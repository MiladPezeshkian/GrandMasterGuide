"use client";

import { usePathname } from "next/navigation";
import { createContext, useContext, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import type { ZorixCore } from "@/core/zorix-core.mjs";
import { useI18n } from "../site/Providers";
import { createVoice, type Voice } from "./voice";
import type { BoardState, Channel, Course, LessonResult, LessonState, PlayState, ProfileState, PuzzleState, ReviewState } from "./types";

type States = {
  board?: BoardState;
  play?: PlayState;
  review?: ReviewState;
  puzzles?: PuzzleState;
  profile?: ProfileState;
  courses?: Course[];
  lesson?: LessonState | null;
  lessonResult?: LessonResult | null;
  speech?: "idle" | "preparing" | "speaking";
  message?: string;
  contentError?: string | null;
};

/** Keeps the latest state of every channel and tells subscribed components when it changes. */
class Store {
  states: States = {};
  private listeners = new Set<() => void>();
  set(channel: Channel, value: unknown) {
    this.states = { ...this.states, [channel]: value };
    this.listeners.forEach((l) => l());
  }
  subscribe = (l: () => void) => {
    this.listeners.add(l);
    return () => this.listeners.delete(l);
  };
}

type Ctx = {
  core: ZorixCore | null;
  store: Store;
  ready: boolean;
  storage: AccountStorage;
  voice: Voice;
};

const ZorixCtx = createContext<Ctx | null>(null);

/**
 * The player's saved progress (the app's key-value store): kept in memory, cached in the browser and
 * saved to the account a moment after each change, so it follows the player to any device.
 */
class AccountStorage {
  private map: Record<string, string>;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private dirty = false;
  constructor(initial: Record<string, string>, private userId: string) {
    let cached: Record<string, string> = {};
    try {
      cached = JSON.parse(localStorage.getItem(`zx.state.${userId}`) ?? "{}");
    } catch {}
    // The server copy wins; the browser cache only fills in what never reached it.
    this.map = { ...cached, ...initial };
  }
  getItem(key: string): string | null {
    return this.map[key] ?? null;
  }
  setItem(key: string, value: string | null) {
    if (value == null) delete this.map[key];
    else this.map[key] = value;
    try {
      localStorage.setItem(`zx.state.${this.userId}`, JSON.stringify(this.map));
    } catch {}
    this.dirty = true;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(), 2500);
  }
  flush(beacon = false) {
    if (!this.dirty) return;
    this.dirty = false;
    const body = JSON.stringify({ data: this.map });
    if (beacon && navigator.sendBeacon) navigator.sendBeacon("/api/me/state", new Blob([body], { type: "application/json" }));
    else void fetch("/api/me/state", { method: "PUT", headers: { "content-type": "application/json" }, body, keepalive: true });
  }
}

export function ZorixProvider({ initialState, userId, children }: { initialState: Record<string, string>; userId: string; children: React.ReactNode }) {
  const { lang } = useI18n();
  const store = useMemo(() => new Store(), []);
  const [core, setCore] = useState<ZorixCore | null>(null);
  const storageRef = useRef<AccountStorage | null>(null);
  const voiceRef = useRef<Voice | null>(null);
  const coreRef = useRef<ZorixCore | null>(null);
  if (typeof window !== "undefined" && !storageRef.current) storageRef.current = new AccountStorage(initialState, userId);
  // Kurdish text is cut into the voice's pieces by the app's own rules (KurdishVoice), in the core.
  if (typeof window !== "undefined" && !voiceRef.current) voiceRef.current = createVoice((text, letters) => coreRef.current?.kurdishPieces(text, letters) ?? []);

  // Create the core once: the app's logic with Stockfish in a Web Worker.
  useEffect(() => {
    let disposed = false;
    let instance: ZorixCore | null = null;
    (async () => {
      const mod = await import("@/core/zorix-core.mjs");
      if (disposed) return;
      const voice = voiceRef.current!;
      instance = new mod.ZorixCore(
        (onLine) => {
          const worker = new Worker("/engine/stockfish-19-lite-single.js");
          worker.onmessage = (e) => onLine(typeof e.data === "string" ? e.data : String(e.data));
          worker.onerror = () => onLine(null);
          return { send: (line: string) => worker.postMessage(line), close: () => worker.terminate() };
        },
        storageRef.current!,
        {
          supports: (l: string) => voice.supports(l),
          speak: (text: string, l: string, token: number) => voice.speak(text, l, () => instance?.voiceStarted(token), () => instance?.voiceDone(token)),
          stop: () => voice.stop(),
          prepare: (l: string) => voice.prepare(l),
        },
        (path: string) => fetch(`/content/${path}`).then((r) => {
          if (!r.ok) throw new Error(`${path}: ${r.status}`);
          return r.text();
        }),
        Math.max(1, Math.min(4, (navigator.hardwareConcurrency || 2) - 1)),
      );
      instance.subscribe((channel: string, state: unknown) => store.set(channel as Channel, state));
      coreRef.current = instance;
      setCore(instance);
    })();
    return () => {
      disposed = true;
      if (coreRef.current === instance) coreRef.current = null;
      instance?.close();
    };
  }, [store]);

  useEffect(() => {
    core?.setLanguage(lang);
  }, [core, lang]);

  // Only the visible screen uses the engine in the background or talks.
  const pathname = usePathname();
  useEffect(() => {
    if (!core) return;
    const screen = pathname.startsWith("/app/analysis") ? "analysis" : pathname.startsWith("/app/play") ? "play" : "other";
    core.setScreen(screen);
  }, [core, pathname]);

  // Background tab: pause like the app does; time on the site counts only while the page is visible.
  useEffect(() => {
    const beat = () => {
      if (document.visibilityState === "visible") void fetch("/api/me/heartbeat", { method: "POST" });
    };
    beat();
    const id = setInterval(beat, 60_000);
    const onVis = () => {
      if (document.visibilityState === "hidden") {
        core?.onBackground();
        storageRef.current?.flush(true);
      } else {
        core?.onForeground();
        beat();
      }
    };
    const onHide = () => storageRef.current?.flush(true);
    document.addEventListener("visibilitychange", onVis);
    window.addEventListener("pagehide", onHide);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", onVis);
      window.removeEventListener("pagehide", onHide);
    };
  }, [core]);

  const value = useMemo<Ctx>(() => ({ core, store, ready: !!core, storage: storageRef.current!, voice: voiceRef.current! }), [core, store]);
  return <ZorixCtx.Provider value={value}>{children}</ZorixCtx.Provider>;
}

export function useZorix() {
  const c = useContext(ZorixCtx);
  if (!c) throw new Error("useZorix outside ZorixProvider");
  return c;
}

/** The latest state of one part of the app. */
export function useChannel<K extends keyof States>(channel: K): States[K] {
  const { store } = useZorix();
  return useSyncExternalStore(
    store.subscribe,
    () => store.states[channel],
    () => undefined,
  );
}

const prefListeners = new Set<() => void>();

/** A per-account preference of the website (board view...), saved with the progress; shared by all components. */
export function usePreference(key: string, fallback: string): [string, (v: string) => void] {
  const { storage } = useZorix();
  const value = useSyncExternalStore(
    (l) => {
      prefListeners.add(l);
      return () => prefListeners.delete(l);
    },
    () => storage?.getItem(`web.${key}`) ?? fallback,
    () => fallback,
  );
  return [
    value,
    (v: string) => {
      storage?.setItem(`web.${key}`, v);
      prefListeners.forEach((l) => l());
    },
  ];
}
