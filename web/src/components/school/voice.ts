/**
 * The coach's voice in the browser: the system voices of the Web Speech API (Edge and Android have
 * natural Persian and English voices). The text is spoken sentence by sentence (long utterances are
 * cut off by some browsers); [onStart] fires when the first sentence starts, [onDone] after the last.
 */
export type Voice = {
  supports: (lang: string) => boolean;
  speak: (text: string, lang: string, onStart: () => void, onDone: () => void) => void;
  stop: () => void;
};

const LOCALES: Record<string, string[]> = {
  en: ["en-US", "en-GB", "en"],
  fa: ["fa-IR", "fa"],
  ckb: ["ckb-IQ", "ckb", "ku-IQ", "ku"],
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
    // Very long sentences are split at commas.
    if (p.length > 220) out.push(...p.split(/(?<=[،,;؛])\s*/).filter(Boolean));
    else out.push(p);
  }
  return out;
}

export function createVoice(): Voice {
  const synth = typeof window !== "undefined" ? window.speechSynthesis : undefined;
  let voices: SpeechSynthesisVoice[] = synth?.getVoices() ?? [];
  synth?.addEventListener?.("voiceschanged", () => {
    voices = synth.getVoices();
  });
  let generation = 0;

  return {
    supports(lang) {
      if (!synth) return false;
      if (!voices.length) voices = synth.getVoices();
      return bestVoice(voices, lang) != null;
    },
    speak(text, lang, onStart, onDone) {
      if (!synth) return onDone();
      const voice = bestVoice(voices, lang);
      if (!voice) return onDone();
      synth.cancel();
      const gen = ++generation;
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
        const end = () => {
          if (gen === generation && i === parts.length - 1) onDone();
        };
        u.onend = end;
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
    },
  };
}
