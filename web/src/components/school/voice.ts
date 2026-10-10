/**
 * The coach's voice in the browser.
 *  - Kurdish (Sorani): the app's own neural voice "Vekol", run in a Web Worker with ONNX Runtime
 *    (public/voices/kurdish-worker.mjs); downloaded once (39 MB) and kept in the browser's cache.
 *  - Persian and English: the system voices of the Web Speech API (Edge and Android have natural ones).
 * [onStart] fires when the voice actually starts, [onDone] after the last sentence (or on failure), so
 * the coach can show "Zorix is thinking…" until the voice is ready, as in the app.
 */
export type Voice = {
  supports: (lang: string) => boolean;
  speak: (text: string, lang: string, onStart: () => void, onDone: () => void) => void;
  stop: () => void;
  prepare: (lang: string) => void;
};

const LOCALES: Record<string, string[]> = {
  en: ["en-US", "en-GB", "en"],
  fa: ["fa-IR", "fa"],
};

function bestVoice(voices: SpeechSynthesisVoice[], lang: string): SpeechSynthesisVoice | null {
  const prefixes = LOCALES[lang] ?? [lang];
  const matching = voices.filter((v) => prefixes.some((p) => v.lang.toLowerCase().startsWith(p.toLowerCase())));
  if (!matching.length) return null;
  // Neural voices first ("Online (Natural)" on Edge, Google voices on Chrome).
  const score = (v: SpeechSynthesisVoice) =>
    (/natural|neural|online/i.test(v.name) ? 4 : 0) + (/google/i.test(v.name) ? 2 : 0) + (v.lang.toLowerCase() === prefixes[0].toLowerCase() ? 1 : 0) + (v.localService ? 0 : 1);
  return matching.sort((a, b) => score(b) - score(a))[0];
}

function sentences(text: string): string[] {
  const parts = text.match(/[^.!?؟。\n]+[.!?؟。]*|\n+/g) ?? [text];
  const out: string[] = [];
  for (const p of parts.map((s) => s.trim()).filter(Boolean)) {
    if (p.length > 220) out.push(...p.split(/(?<=[،,;؛])\s*/).filter(Boolean));
    else out.push(p);
  }
  return out;
}

/** Pause between two sentences, as a speaker takes a breath (as in the app). */
const BREATH = 0.16;

export function createVoice(kurdishPieces: (text: string, letters: string) => string[]): Voice {
  const synth = typeof window !== "undefined" ? window.speechSynthesis : undefined;
  let voices: SpeechSynthesisVoice[] = synth?.getVoices() ?? [];
  synth?.addEventListener?.("voiceschanged", () => {
    voices = synth.getVoices();
  });
  let generation = 0;

  // ------------------------------------------------------------ Kurdish (Vekol in a worker)
  const canKurdish = typeof window !== "undefined" && typeof WebAssembly !== "undefined" && typeof Worker !== "undefined";
  let worker: Worker | null = null;
  let letters: string | null = null;
  let audio: AudioContext | null = null;
  let sources: AudioBufferSourceNode[] = [];
  let timers: ReturnType<typeof setTimeout>[] = [];
  let request: { id: number; onStart: () => void; onDone: () => void; nextAt: number; started: boolean; finished: boolean; lastEnd: number } | null = null;

  const ctx = () => {
    if (!audio) audio = new AudioContext();
    if (audio.state === "suspended") void audio.resume();
    return audio;
  };
  // Browsers start audio only after a user gesture: unlock it on the first tap or key.
  if (typeof window !== "undefined") {
    const unlock = () => {
      ctx();
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
    };
    window.addEventListener("pointerdown", unlock);
    window.addEventListener("keydown", unlock);
  }

  const kurdishWorker = () => {
    if (!worker) {
      worker = new Worker("/voices/kurdish-worker.mjs", { type: "module" });
      worker.onmessage = (e) => {
        const m = e.data as { type: string; id?: number; audio?: Float32Array };
        const r = request;
        if (!r || m.id !== r.id) return;
        if (m.type === "audio" && m.audio) {
          const c = ctx();
          const buffer = c.createBuffer(1, m.audio.length, 22050);
          buffer.copyToChannel(m.audio as Float32Array<ArrayBuffer>, 0);
          const src = c.createBufferSource();
          src.buffer = buffer;
          src.connect(c.destination);
          const at = Math.max(c.currentTime + 0.05, r.nextAt);
          src.start(at);
          sources.push(src);
          r.nextAt = at + buffer.duration + BREATH;
          r.lastEnd = at + buffer.duration;
          if (!r.started) {
            r.started = true;
            timers.push(setTimeout(() => request === r && r.onStart(), Math.max(0, (at - c.currentTime) * 1000)));
          }
        } else if (m.type === "done") {
          r.finished = true;
          const c = ctx();
          const wait = Math.max(0, (r.lastEnd - c.currentTime) * 1000);
          timers.push(
            setTimeout(() => {
              if (request === r) {
                request = null;
                r.onDone();
              }
            }, wait),
          );
        }
      };
    }
    return worker;
  };

  const loadLetters = async () => {
    if (letters != null) return letters;
    const map = (await fetch("/voices/ckb/tokens.json").then((r) => r.json())) as Record<string, number>;
    letters = Object.keys(map).filter((k) => k !== " " && k !== "\n").join("");
    return letters;
  };

  const stopKurdish = () => {
    timers.forEach(clearTimeout);
    timers = [];
    sources.forEach((s) => {
      try {
        s.stop();
      } catch {}
    });
    sources = [];
    request = null;
    worker?.postMessage({ type: "stop" });
  };

  return {
    supports(lang) {
      if (lang === "ckb") return canKurdish;
      if (!synth) return false;
      if (!voices.length) voices = synth.getVoices();
      return bestVoice(voices, lang) != null;
    },
    prepare(lang) {
      if (lang === "ckb" && canKurdish) {
        kurdishWorker().postMessage({ type: "load" });
        void loadLetters().catch(() => undefined);
      }
    },
    speak(text, lang, onStart, onDone) {
      generation++;
      synth?.cancel();
      stopKurdish();
      if (lang === "ckb") {
        if (!canKurdish) return onDone();
        const id = generation;
        void loadLetters()
          .then((l) => {
            if (id !== generation) return;
            const pieces = kurdishPieces(text, l);
            if (!pieces.length) return onDone();
            request = { id, onStart, onDone, nextAt: 0, started: false, finished: false, lastEnd: 0 };
            kurdishWorker().postMessage({ type: "speak", id, pieces });
          })
          .catch(() => onDone());
        return;
      }
      if (!synth) return onDone();
      const voice = bestVoice(voices, lang);
      if (!voice) return onDone();
      const gen = generation;
      const parts = sentences(text.replace(/[⁦-⁩]/g, ""));
      let started = false;
      parts.forEach((part, i) => {
        const u = new SpeechSynthesisUtterance(part);
        u.voice = voice;
        u.lang = voice.lang;
        u.rate = lang === "en" ? 1.0 : 0.95;
        u.onstart = () => {
          if (gen === generation && !started) {
            started = true;
            onStart();
          }
        };
        u.onend = () => {
          if (gen === generation && i === parts.length - 1) onDone();
        };
        u.onerror = () => {
          if (gen === generation) onDone();
        };
        synth.speak(u);
      });
      if (!parts.length) onDone();
    },
    stop() {
      generation++;
      synth?.cancel();
      stopKurdish();
    },
  };
}
