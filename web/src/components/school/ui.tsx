"use client";

import { Box, HelpCircle, Loader2, Square, Star, Volume2, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { Board } from "../board/Board";
import type { BoardProps } from "../board/model";
import { useI18n } from "../site/Providers";
import type { Quality, Score } from "./types";
import { useChannel, usePreference, useZorix } from "./ZorixProvider";

export const QUALITY_COLOR: Record<Quality, string> = {
  brilliant: "#1BC6C0",
  great: "#5B9BD5",
  best: "#2FD27C",
  excellent: "#8FD14F",
  good: "#A7C48F",
  book: "#C49A6C",
  inaccuracy: "#F5C542",
  mistake: "#FF9F45",
  miss: "#FF7A6B",
  blunder: "#FF4B55",
};

export const QUALITY_SYMBOL: Record<Quality, string> = {
  brilliant: "!!",
  great: "!",
  best: "★",
  excellent: "✓",
  good: "✓",
  book: "📖",
  inaccuracy: "?!",
  mistake: "?",
  miss: "✗",
  blunder: "??",
};

export function QualityDot({ q, size = 22 }: { q: Quality; size?: number }) {
  return (
    <span className="inline-flex shrink-0 items-center justify-center rounded-full text-[0.7rem] font-black text-[#101014]" style={{ background: QUALITY_COLOR[q], width: size, height: size }}>
      {QUALITY_SYMBOL[q]}
    </span>
  );
}

export function QualityBadge({ q }: { q: Quality }) {
  const { a } = useI18n();
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-bold text-[#101014]" style={{ background: QUALITY_COLOR[q] }}>
      {QUALITY_SYMBOL[q]} {a(`quality_${q}`)}
    </span>
  );
}

/** "+1.25", "-0.40", "M3" — from White's point of view. */
export function formatScore(s: Score | null | undefined): string {
  if (!s) return "0.00";
  if (s.mate != null) return `${s.mate > 0 ? "" : "-"}M${Math.abs(s.mate)}`;
  const v = (s.cp ?? 0) / 100;
  return `${v > 0 ? "+" : ""}${v.toFixed(2)}`;
}

/** White's share of the bar (0..1). */
export function scoreShare(s: Score | null | undefined): number {
  if (!s) return 0.5;
  if (s.mate != null) return s.mate > 0 ? 1 : 0;
  const cp = s.cp ?? 0;
  return 1 / (1 + Math.exp(-cp / 260));
}

export function EvalBar({ score, flipped }: { score: Score | null | undefined; flipped: boolean }) {
  const share = scoreShare(score);
  const white = flipped ? 1 - share : share;
  return (
    <div className="relative w-3 overflow-hidden rounded-full bg-[#2a2a31] sm:w-4" title={formatScore(score)}>
      <motion.div className="absolute inset-x-0 bg-[#f4f4f6]" style={flipped ? { top: 0 } : { bottom: 0 }} animate={{ height: `${white * 100}%` }} transition={{ type: "spring", stiffness: 120, damping: 22 }} />
    </div>
  );
}

export function Stars({ n, max = 3, size = 14 }: { n: number; max?: number; size?: number }) {
  return (
    <span className="inline-flex gap-0.5">
      {Array.from({ length: max }, (_, i) => (
        <Star key={i} size={size} className={i < n ? "fill-[#FFC53D] text-[#FFC53D]" : "text-line-strong"} />
      ))}
    </span>
  );
}

export function Spinner({ size = 16 }: { size?: number }) {
  return <Loader2 size={size} className="animate-spin text-accent" />;
}

/**
 * Whether a coach text may show yet: with the voice on, a new explanation waits until the voice
 * starts reading it (as in the app), showing "Zorix is thinking…" meanwhile.
 */
export function useCoachReveal(message: string | null | undefined, waitForVoice: boolean): boolean {
  const { core } = useZorix();
  const speech = useChannel("speech");
  const speechRef = useRef(speech);
  speechRef.current = speech;
  const voiceOn = waitForVoice && !!core?.voiceReady();
  const [revealed, setRevealed] = useState<{ msg: string | null | undefined; ok: boolean }>({ msg: message, ok: !voiceOn || !message });

  useEffect(() => {
    if (!voiceOn || !message) {
      setRevealed({ msg: message, ok: true });
      return;
    }
    setRevealed({ msg: message, ok: false });
    let cancelled = false;
    const started = Date.now();
    let asked = false;
    const tick = () => {
      if (cancelled) return;
      const s = speechRef.current;
      if (s === "preparing") asked = true;
      const waited = Date.now() - started;
      if ((asked && s !== "preparing") || (!asked && waited > 1200) || waited > 21000) {
        setRevealed({ msg: message, ok: true });
        return;
      }
      setTimeout(tick, 80);
    };
    tick();
    return () => {
      cancelled = true;
    };
  }, [message, voiceOn]);

  return revealed.msg === message ? revealed.ok : !voiceOn || !message;
}

export function CoachBubble({
  message,
  title,
  badge,
  busy,
  onSpeak,
  waitForVoice = false,
  tone = "default",
}: {
  message: string | null | undefined;
  title?: string;
  badge?: React.ReactNode;
  busy?: boolean;
  onSpeak?: () => void;
  waitForVoice?: boolean;
  tone?: "default" | "good" | "bad";
}) {
  const { a } = useI18n();
  const speech = useChannel("speech");
  const revealed = useCoachReveal(message, waitForVoice);
  const thinking = (busy && !message) || (!!message && !revealed);
  return (
    <div className={`card relative overflow-hidden p-4 ${tone === "good" ? "border-best/40" : tone === "bad" ? "border-danger/40" : ""}`}>
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#0b0b0e] ring-2 ring-accent/60">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/emblem.png" alt="" className="h-5 w-auto" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-bold text-accent-bright">{title ?? a("coach_name")}</span>
            {badge}
            <span className="flex-1" />
            {onSpeak && message && revealed && (
              <button className="rounded-full p-1.5 text-faint hover:bg-surface-2 hover:text-text" aria-label={a("action_listen")} onClick={onSpeak}>
                {speech === "speaking" ? <Volume2 size={18} className="animate-pulse text-accent" /> : <Volume2 size={18} />}
              </button>
            )}
          </div>
          <AnimatePresence mode="wait" initial={false}>
            {thinking ? (
              <motion.div key="t" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="mt-2 flex items-center gap-2 text-sm text-dim">
                <Spinner /> {busy && !message ? a("coach_evaluating") : a("zorix_thinking")}
              </motion.div>
            ) : message ? (
              <motion.p key={message} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="mt-1.5 text-[0.95rem] leading-7">
                {message}
              </motion.p>
            ) : null}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

/** Help ("?") for a screen: the app's own help pages ("Title|Text" items). */
export function HelpButton({ topic }: { topic: string }) {
  const { a, aa } = useI18n();
  const [open, setOpen] = useState(false);
  const items = aa(`help_${topic}`).map((s) => s.split("|"));
  return (
    <>
      <button className="btn btn-quiet" aria-label={a("action_help")} onClick={() => setOpen(true)}>
        <HelpCircle size={19} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-50 flex items-end justify-center bg-black/55 p-3 backdrop-blur-sm sm:items-center" onClick={() => setOpen(false)}>
            <motion.div initial={{ y: 30 }} animate={{ y: 0 }} exit={{ y: 30 }} className="card scrollbar-thin max-h-[85dvh] w-full max-w-xl overflow-y-auto p-6" onClick={(e) => e.stopPropagation()}>
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-black">{a(`help_${topic}_title`)}</h2>
                <button className="btn btn-quiet" onClick={() => setOpen(false)} aria-label={a("action_close")}>
                  <X size={18} />
                </button>
              </div>
              <div className="mt-4 space-y-4">
                {items.map(([title, text], i) => (
                  <div key={i}>
                    <h3 className="font-bold text-accent-bright">{title}</h3>
                    <p className="mt-1 leading-7 text-dim">{text}</p>
                  </div>
                ))}
              </div>
              <button className="btn btn-primary mt-6 w-full" onClick={() => setOpen(false)}>
                {a("help_got_it")}
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-center gap-3">
      <div className="min-w-0 flex-1">
        <h1 className="text-2xl font-black tracking-tight sm:text-3xl">{title}</h1>
        {subtitle && <div className="mt-1 text-sm text-dim">{subtitle}</div>}
      </div>
      {actions && <div className="flex items-center gap-1">{actions}</div>}
    </div>
  );
}

/** 3D / 2D switch, saved with the account. */
export function ViewToggle() {
  const { t } = useI18n();
  const [view, setView] = usePreference("boardView", "3d");
  return (
    <div className="flex rounded-xl border border-line bg-surface-2 p-0.5" role="group" aria-label={t("board_view")}>
      {(["3d", "2d"] as const).map((v) => (
        <button key={v} className={`flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-bold transition ${view === v ? "bg-accent text-on-accent" : "text-dim hover:text-text"}`} onClick={() => setView(v)}>
          {v === "3d" ? <Box size={14} /> : <Square size={14} />} {t(v === "3d" ? "view_3d" : "view_2d")}
        </button>
      ))}
    </div>
  );
}

/** The board with the player's view (3D/2D), board colours and display settings. */
export function SchoolBoard(props: Omit<BoardProps, "theme" | "coordinates" | "showLegal" | "animate"> & { coordinates?: boolean }) {
  const settings = useChannel("board")?.settings;
  const [view] = usePreference("boardView", "3d");
  const { core } = useZorix();
  return (
    <div className="mx-auto w-full" style={{ maxWidth: "min(100%, calc(100dvh - 10.5rem))" }}>
    <Board
      {...props}
      view={view === "2d" ? "2d" : "3d"}
      theme={settings?.boardTheme ?? "classic"}
      coordinates={props.coordinates ?? settings?.showCoordinates ?? true}
      showLegal={settings?.showLegalMoves ?? true}
      animate={settings?.animateMoves ?? true}
      legalTargets={props.legalTargets ?? (core ? (sq: string) => core.legalTargets(props.fen, sq) : undefined)}
    />
    </div>
  );
}

export function useToast() {
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => {
    if (!msg) return;
    const id = setTimeout(() => setMsg(null), 2600);
    return () => clearTimeout(id);
  }, [msg]);
  const node = (
    <AnimatePresence>
      {msg && (
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 20 }} className="fixed bottom-24 left-1/2 z-50 -translate-x-1/2 rounded-full bg-text px-5 py-2.5 text-sm font-semibold text-bg shadow-xl lg:bottom-8">
          {msg}
        </motion.div>
      )}
    </AnimatePresence>
  );
  return [node, setMsg] as const;
}
