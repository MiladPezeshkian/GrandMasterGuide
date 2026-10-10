"use client";

import { Activity, ClipboardCopy, Edit3, FileInput, FlipVertical2, Lightbulb, MoreHorizontal, Play, Plus, Redo2, Square, Undo2, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useI18n } from "../site/Providers";
import { MoveList } from "./MoveList";
import type { BoardState } from "./types";
import { CoachBubble, EvalBar, formatScore, HelpButton, PageHeader, QualityBadge, QualityDot, SchoolBoard, Spinner, useToast, ViewToggle } from "./ui";
import { useChannel, useZorix } from "./ZorixProvider";

const MESSAGE_KEYS: Record<string, string> = {
  illegal_move: "msg_illegal_move",
  game_is_over: "msg_game_over",
  engine_not_ready: "msg_engine_not_ready",
  no_legal_moves: "msg_no_legal_moves",
  suggestion_outdated: "msg_suggestion_outdated",
  fen_invalid: "msg_fen_invalid",
  fen_copied: "msg_fen_copied",
  pgn_copied: "msg_pgn_copied",
  position_loaded: "msg_position_loaded",
  nothing_to_undo: "msg_nothing_to_undo",
  nothing_to_redo: "msg_nothing_to_redo",
};

export function gameOverText(a: (k: string) => string, result: string, reason: string | null): string | null {
  switch (reason) {
    case "checkmate":
      return a(result === "1-0" ? "game_over_checkmate_white" : "game_over_checkmate_black");
    case "stalemate":
      return a("game_over_stalemate");
    case "insufficient_material":
      return a("game_over_insufficient");
    case "fifty_moves":
      return a("game_over_fifty");
    case "threefold_repetition":
      return a("game_over_repetition");
    default:
      return null;
  }
}

export function Analysis() {
  const { a, t } = useI18n();
  const { core } = useZorix();
  const st = useChannel("board");
  const message = useChannel("message");
  const [toast, showToast] = useToast();
  const [menu, setMenu] = useState(false);
  const [fenOpen, setFenOpen] = useState(false);

  const lastMessage = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (message && message !== lastMessage.current) showToast(a(MESSAGE_KEYS[message] ?? message));
    lastMessage.current = message;
  }, [message, a, showToast]);

  if (!core || !st) return <Loading />;
  const g = st.game;
  const flipped = st.flipped;
  const view = st.view;
  const hint = st.hint;
  const thinking = hint.kind === "thinking";
  const sideName = a(g.turn === "white" ? "side_white" : "side_black");
  const arrows: { from: string; to: string; color?: string }[] = [];
  if (st.settings.showArrows) {
    if (hint.kind === "ready" && hint.view.best && hint.view.fen === g.fen) arrows.push({ from: hint.view.best.slice(0, 2), to: hint.view.best.slice(2, 4), color: "rgba(47,210,124,0.9)" });
    else if (st.analysisOn && view && view.fen === g.fen)
      view.lines.slice(0, 3).forEach((l, i) => l.uci[0] && arrows.push({ from: l.uci[0].slice(0, 2), to: l.uci[0].slice(2, 4), color: ["rgba(47,210,124,0.9)", "rgba(79,156,255,0.75)", "rgba(255,176,46,0.7)"][i] }));
  }
  const over = gameOverText(a, g.result, g.reason);

  return (
    <div className="mx-auto max-w-7xl px-3 py-5 sm:px-6">
      <PageHeader
        title={a("tab_analysis")}
        subtitle={<span>{a(g.turn === "white" ? "turn_white" : "turn_black")}</span>}
        actions={
          <>
            <ViewToggle />
            <HelpButton topic="analysis" />
          </>
        }
      />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div>
          <div className="flex gap-2">
            <EvalBar score={view?.fen === g.fen ? view.score : null} flipped={flipped} />
            <div className="min-w-0 flex-1">
              <SchoolBoard
                fen={g.fen}
                orientation={flipped ? "black" : "white"}
                movable={g.over ? "none" : "both"}
                lastMove={g.lastMove}
                check={g.check}
                arrows={arrows}
                onMove={(f, to) => core.boardMove(f, to)}
                promotion={st.promotion}
                onPromote={(p) => core.boardPromote(p ?? "")}
              />
            </div>
          </div>
          {/* Toolbar */}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {thinking ? (
              <button className="btn btn-primary flex-1 sm:flex-none" onClick={() => core.boardStop()}>
                <Square size={16} /> {a("action_stop")}
              </button>
            ) : (
              <button className="btn btn-primary flex-1 sm:flex-none" onClick={() => core.boardBestMove()} disabled={g.over || st.engine.kind !== "ready"}>
                <Lightbulb size={17} /> {a("action_best_move")}
              </button>
            )}
            <IconBtn label={a("action_undo")} onClick={() => core.boardUndo()} disabled={!g.canUndo}>
              <Undo2 size={18} />
            </IconBtn>
            <IconBtn label={a("action_redo")} onClick={() => core.boardRedo()} disabled={!g.canRedo}>
              <Redo2 size={18} />
            </IconBtn>
            <IconBtn label={a("action_flip")} onClick={() => core.boardFlip()}>
              <FlipVertical2 size={18} />
            </IconBtn>
            <IconBtn label={a("action_analysis")} onClick={() => core.boardAnalysis(!st.analysisOn)} active={st.analysisOn}>
              <Activity size={18} />
            </IconBtn>
            <div className="relative">
              <IconBtn label={a("action_more")} onClick={() => setMenu((m) => !m)}>
                <MoreHorizontal size={18} />
              </IconBtn>
              <AnimatePresence>
                {menu && (
                  <motion.div initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="glass absolute end-0 top-full z-30 mt-2 w-56 overflow-hidden rounded-2xl p-1 shadow-[var(--shadow)]" onMouseLeave={() => setMenu(false)}>
                    <MenuItem icon={<Plus size={16} />} label={a("action_new_game")} onClick={() => { core.boardNew(); setMenu(false); }} />
                    <MenuItem icon={<FileInput size={16} />} label={a("action_load_fen")} onClick={() => { setFenOpen(true); setMenu(false); }} />
                    <MenuItem icon={<ClipboardCopy size={16} />} label={a("action_copy_fen")} onClick={() => { void navigator.clipboard.writeText(core.boardFen()); showToast(a("msg_fen_copied")); setMenu(false); }} />
                    <MenuItem icon={<ClipboardCopy size={16} />} label={a("action_copy_pgn")} onClick={() => { void navigator.clipboard.writeText(core.boardPgn()); showToast(a("msg_pgn_copied")); setMenu(false); }} />
                    <Link href="/app/puzzles/build" className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold hover:bg-surface-2">
                      <Edit3 size={16} /> {a("action_build")}
                    </Link>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </div>

        {/* Side panel */}
        <div className="space-y-4">
          {over && <div className="card border-accent/50 p-4 text-center font-bold">{over}</div>}
          <HintPanel st={st} sideName={sideName} />
          <CoachVerdict st={st} />
          {st.analysisOn && <EngineLines st={st} />}
          <MoveList plies={g.plies} all={g.line} current={g.plies.length} onGoTo={(n) => core.boardGoTo(n)} />
          <p className="text-center text-xs text-faint">{t("speed_tip")}</p>
        </div>
      </div>
      {fenOpen && <FenDialog onClose={() => setFenOpen(false)} />}
      {toast}
    </div>
  );
}

function IconBtn({ label, onClick, disabled, active, children }: { label: string; onClick: () => void; disabled?: boolean; active?: boolean; children: React.ReactNode }) {
  return (
    <button className={`btn btn-ghost h-11 w-11 !px-0 ${active ? "!border-accent !text-accent" : ""}`} aria-label={label} title={label} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

function MenuItem({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick: () => void }) {
  return (
    <button className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-start text-sm font-semibold hover:bg-surface-2" onClick={onClick}>
      {icon} {label}
    </button>
  );
}

function HintPanel({ st, sideName }: { st: BoardState; sideName: string }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const hint = st.hint;
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (hint.kind !== "thinking") return;
    const id = setInterval(() => setNow(Date.now()), 100);
    return () => clearInterval(id);
  }, [hint.kind]);
  if (hint.kind === "idle") return null;
  if (hint.kind === "thinking") {
    const p = Math.min(1, (now - hint.startedAt) / hint.budgetMs);
    return (
      <div className="card p-4">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <Spinner /> {a("hint_thinking")}
          {hint.view && <span className="ltr ms-auto text-xs text-faint">{a("hint_depth", hint.view.depth)}</span>}
        </div>
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-3">
          <div className="h-full rounded-full bg-accent transition-[width] duration-100" style={{ width: `${p * 100}%` }} />
        </div>
      </div>
    );
  }
  const v = hint.view;
  const explain = st.hintExplanation?.fen === v.fen ? st.hintExplanation : null;
  return (
    <div className="space-y-2">
      <div className="card flex items-center gap-3 p-4">
        <Lightbulb size={22} className="text-best" />
        <div className="min-w-0 flex-1">
          <div className="text-xs text-dim">{a("hint_best_move", sideName)}</div>
          <div className="ltr text-2xl font-black">
            {v.bestSan ?? "—"} <span className="text-sm font-bold text-dim">{formatScore(v.score)}</span>
          </div>
        </div>
        <button className="btn btn-primary" onClick={() => core?.boardPlaySuggestion()}>
          <Play size={16} /> {a("action_play")}
        </button>
        <button className="btn btn-quiet" aria-label={a("action_dismiss")} onClick={() => core?.boardDismissSuggestion()}>
          <X size={16} />
        </button>
      </div>
      {explain && <CoachBubble message={explain.display} onSpeak={() => core?.boardSpeakHint()} />}
    </div>
  );
}

function CoachVerdict({ st }: { st: BoardState }) {
  const { a } = useI18n();
  const { core } = useZorix();
  if (!st.settings.coachMode || st.game.plies.length === 0) return null;
  const f = st.feedback;
  if (!f && !st.coachBusy) return null;
  if (!f) return <CoachBubble message={null} busy />;
  return (
    <CoachBubble
      message={f.message || null}
      waitForVoice
      onSpeak={() => core?.boardSpeakFeedback()}
      title={a("coach_title")}
      badge={
        <span className="flex items-center gap-2">
          <QualityDot q={f.quality} />
          <span className="ltr font-black">{f.san}</span>
          <QualityBadge q={f.quality} />
          {f.scoreAfter && <span className="ltr rounded-md bg-surface-2 px-1.5 py-0.5 text-xs font-bold text-dim">{formatScore(f.scoreAfter)}</span>}
        </span>
      }
    />
  );
}

function EngineLines({ st }: { st: BoardState }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const v = st.view;
  return (
    <div className="card overflow-hidden">
      <div className="flex items-center justify-between border-b border-line px-4 py-2.5 text-sm font-bold">
        {a("analysis_title")}
        {v && <span className="ltr text-xs font-semibold text-faint">{a("hint_depth", v.depth)}</span>}
      </div>
      {!v || v.fen !== st.game.fen ? (
        <div className="flex items-center gap-2 p-4 text-sm text-dim">
          <Spinner /> {a("analysis_waiting")}
        </div>
      ) : (
        <div className="divide-y divide-line">
          {v.lines.map((l, i) => (
            <button key={l.rank} className="ltr flex w-full items-start gap-3 px-4 py-2.5 text-left text-sm hover:bg-surface-2" onClick={() => core?.boardPlayLine(i)}>
              <span className={`tnum min-w-14 rounded-md px-1.5 py-0.5 text-center text-xs font-black ${(l.score.mate ?? l.score.cp ?? 0) >= 0 ? "bg-[#f4f4f6] text-[#111]" : "bg-[#222] text-white"}`}>{formatScore(l.score)}</span>
              <span className="line-clamp-2 text-dim">{l.text}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function FenDialog({ onClose }: { onClose: () => void }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const [fen, setFen] = useState("");
  const [error, setError] = useState<string | null>(null);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm" onClick={onClose}>
      <div className="card w-full max-w-lg p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-xl font-black">{a("fen_dialog_title")}</h2>
        <textarea className="input ltr mt-4 min-h-24 py-3 font-mono text-sm" placeholder={a("fen_dialog_hint")} value={fen} onChange={(e) => setFen(e.target.value)} />
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button className="btn btn-quiet" onClick={async () => setFen((await navigator.clipboard.readText().catch(() => "")) || fen)}>
            {a("action_paste")}
          </button>
          <button className="btn btn-ghost" onClick={onClose}>
            {a("action_cancel")}
          </button>
          <button
            className="btn btn-primary"
            onClick={() => {
              const e = core?.boardLoadFen(fen.trim());
              if (e == null) return onClose();
              setError(e === "malformed" ? a("fen_error_malformed") : a(`problem_${e}`));
            }}
          >
            {a("action_load")}
          </button>
        </div>
      </div>
    </div>
  );
}

export function Loading() {
  const { t } = useI18n();
  return (
    <div className="flex min-h-[60dvh] flex-col items-center justify-center gap-4">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/emblem.png" alt="" className="h-16 w-auto animate-pulse" />
      <div className="flex items-center gap-2 text-sm text-dim">
        <Spinner /> {t("loading_school")}
      </div>
    </div>
  );
}
