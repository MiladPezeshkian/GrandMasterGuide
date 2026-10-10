"use client";

import { Flag, Handshake, Lightbulb, Lock, Play as PlayIcon, RotateCcw, Shuffle, Trophy, Undo2, Volume2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { digits } from "@/i18n/site";
import { useI18n } from "../site/Providers";
import { Loading } from "./Analysis";
import { MoveList } from "./MoveList";
import type { Bot, Color, PlayState } from "./types";
import { CoachBubble, HelpButton, PageHeader, QualityBadge, SchoolBoard, Spinner, Stars, ViewToggle } from "./ui";
import { useChannel, useZorix } from "./ZorixProvider";

export const TIER_COLOR: Record<string, string> = {
  beginner: "#6CCB5F",
  novice: "#3FB5A6",
  intermediate: "#4F9CFF",
  advanced: "#8C6CFF",
  expert: "#FF9F45",
  master: "#FF6B6B",
  grandmaster: "#E3202B",
  zorix: "#FFC53D",
};

export function PlayHub() {
  const { core } = useZorix();
  const play = useChannel("play");
  if (!core || !play) return <Loading />;
  return play.active ? <GameView st={play} /> : <LevelPicker />;
}

function LevelPicker() {
  const { a, lang } = useI18n();
  const { core } = useZorix();
  const profile = useChannel("profile");
  const bots = useMemo(() => (core?.bots() ?? []) as Bot[], [core]);
  const recommended = core?.recommendedLevel() ?? 1;
  const [chosen, setChosen] = useState<Bot | null>(null);
  return (
    <div className="mx-auto max-w-6xl px-3 py-5 sm:px-6">
      <PageHeader title={a("play_title")} subtitle={a("play_subtitle_plain")} actions={<HelpButton topic="play" />} />
      <p className="mb-5 text-sm leading-7 text-dim">{a("play_levels_hint")}</p>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {bots.map((b) => {
          const color = TIER_COLOR[b.tier] ?? "#888";
          const stars = profile?.levelStars?.[String(b.level)] ?? 0;
          const rec = b.level === recommended;
          return (
            <motion.button
              key={b.level}
              whileHover={{ y: -3 }}
              onClick={() => setChosen(b)}
              className={`card relative flex flex-col items-start gap-2 p-4 text-start transition hover:border-line-strong ${rec ? "!border-accent shadow-[0_0_0_1px_var(--accent)]" : ""}`}
            >
              <div className="flex w-full items-center justify-between">
                <span className="flex h-11 w-11 items-center justify-center rounded-full text-lg font-black text-[#101014]" style={{ background: color }}>
                  {digits(lang, b.level)}
                </span>
                <Stars n={stars} />
              </div>
              <div className="ltr font-extrabold">{a("bot_name", b.level)}</div>
              <div className="flex w-full items-center justify-between text-xs">
                <span style={{ color }} className="font-bold">
                  {a(`tier_${b.tier}`)}
                </span>
                <span className="tnum text-faint">{digits(lang, b.elo)}</span>
              </div>
              {rec && <span className="absolute -top-2.5 start-3 rounded-full bg-accent px-2 py-0.5 text-[0.65rem] font-bold text-on-accent">{a("play_recommended")}</span>}
            </motion.button>
          );
        })}
      </div>
      <AnimatePresence>{chosen && <ColorDialog bot={chosen} onClose={() => setChosen(null)} />}</AnimatePresence>
    </div>
  );
}

function ColorDialog({ bot, onClose }: { bot: Bot; onClose: () => void }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const start = (side: string) => {
    core?.playStart(bot.level, side);
    onClose();
  };
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm" onClick={onClose}>
      <motion.div initial={{ scale: 0.95 }} animate={{ scale: 1 }} className="card w-full max-w-sm p-6 text-center" onClick={(e) => e.stopPropagation()}>
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full text-2xl font-black text-[#101014]" style={{ background: TIER_COLOR[bot.tier] }}>
          {bot.level}
        </div>
        <h2 className="ltr mt-3 text-xl font-black">{a("bot_name", bot.level)}</h2>
        <p className="text-sm text-dim">
          {a(`tier_${bot.tier}`)} · <span className="tnum">{bot.elo}</span>
        </p>
        <p className="mt-5 text-sm font-semibold">{a("play_choose_color")}</p>
        <div className="mt-3 grid grid-cols-3 gap-2">
          {[
            ["white", <span key="w" className="h-8 w-8 rounded-full border border-line-strong bg-[#f4f4f6]" />, a("side_white")],
            ["random", <Shuffle key="r" size={26} />, a("play_random")],
            ["black", <span key="b" className="h-8 w-8 rounded-full border border-line-strong bg-[#16161a]" />, a("side_black")],
          ].map(([side, icon, label]) => (
            <button key={side as string} className="flex flex-col items-center gap-2 rounded-2xl border border-line bg-surface-2 p-3 text-sm font-semibold hover:border-accent" onClick={() => start(side as string)}>
              {icon}
              {label}
            </button>
          ))}
        </div>
      </motion.div>
    </motion.div>
  );
}

function GameView({ st }: { st: PlayState }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const router = useRouter();
  const g = st.game;
  const [confirmResign, setConfirmResign] = useState(false);
  const [hideResult, setHideResult] = useState(false);
  const recorded = useRef<string | null>(null);
  const profile = useChannel("profile");
  useEffect(() => setHideResult(false), [st.result]);

  // A finished game is recorded with the account (history and the admin's statistics).
  useEffect(() => {
    if (!st.result) return;
    const key = `${st.bot.level}:${g.plies.length}:${g.pgn.length}`;
    if (recorded.current === key) return;
    recorded.current = key;
    const winnerWhite = st.result.outcome === "win" ? st.userSide === "white" : st.result.outcome === "loss" ? st.userSide === "black" : null;
    void fetch("/api/games/bot", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        level: st.bot.level,
        side: st.userSide,
        result: winnerWhite == null ? "1/2-1/2" : winnerWhite ? "1-0" : "0-1",
        reason: st.result.resigned ? "resign" : st.result.drawAgreed ? "agreement" : (st.result.reason ?? ""),
        moves: g.plies.map((p) => p.uci),
      }),
    });
  }, [st.result, st.bot.level, st.userSide, g]);

  const lastFeedback = st.feedback;
  let coach: React.ReactNode = null;
  if (st.hint) coach = <CoachBubble message={st.hint.display} waitForVoice onSpeak={() => core?.playSpeakAgain()} badge={<span className="rounded-full bg-best px-2 py-0.5 text-xs font-bold text-[#101014]">{a("action_hint")}</span>} />;
  else if (st.coachBusy) coach = <CoachBubble message={null} busy />;
  else if (lastFeedback && (!st.botMessage || !st.userToMove))
    coach = <CoachBubble message={lastFeedback.message || null} waitForVoice onSpeak={() => core?.playSpeakAgain()} badge={<><span className="ltr font-black">{lastFeedback.san}</span><QualityBadge q={lastFeedback.quality} /></>} />;
  else if (st.botMessage) coach = <CoachBubble message={st.botMessage.display} title="Zorix" waitForVoice onSpeak={() => core?.playSpeakAgain()} />;

  const opponentColor: Color = st.userSide === "white" ? "black" : "white";
  return (
    <div className="mx-auto max-w-7xl px-3 py-5 sm:px-6">
      <PageHeader
        title={a("bot_name", st.bot.level)}
        subtitle={
          <span className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: TIER_COLOR[st.bot.tier] }} />
            {a(`tier_${st.bot.tier}`)} · <span className="tnum">{st.bot.elo}</span>
          </span>
        }
        actions={
          <>
            <ViewToggle />
            <HelpButton topic="game" />
          </>
        }
      />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div>
          <PlayerBar name={a("bot_name", st.bot.level)} color={opponentColor} thinking={st.botThinking} />
          <div className="my-2">
            <SchoolBoard
              fen={g.fen}
              orientation={st.userSide}
              movable={st.userToMove ? st.userSide : "none"}
              lastMove={g.lastMove}
              check={g.check}
              onMove={(f, t) => core?.playMove(f, t)}
              promotion={st.promotion}
              onPromote={(p) => core?.playPromote(p ?? "")}
            />
          </div>
          <PlayerBar name={profile?.name || a("default_player")} color={st.userSide} thinking={false} you />
          <div className="mt-3 flex flex-wrap gap-2">
            <button className="btn btn-primary flex-1" onClick={() => core?.playHint()} disabled={!st.userToMove || st.hintBusy}>
              {st.hintBusy ? <Spinner /> : <Lightbulb size={17} />} {a("action_hint")}
            </button>
            <button className="btn btn-ghost" onClick={() => core?.playTakeback()} disabled={g.plies.length < 2 || !!st.result}>
              <Undo2 size={17} /> {a("action_takeback")}
            </button>
            <button className="btn btn-ghost" onClick={() => core?.playOfferDraw()} disabled={!!st.result}>
              <Handshake size={17} /> {a("action_draw")}
            </button>
            <button className="btn btn-ghost !text-danger" onClick={() => setConfirmResign(true)} disabled={!!st.result}>
              <Flag size={17} /> {a("action_resign")}
            </button>
          </div>
        </div>
        <div className="space-y-4">
          {coach}
          {st.drawDeclined && <p className="text-sm text-dim">{a("play_draw_declined")}</p>}
          <MoveList plies={g.plies} current={g.plies.length} onGoTo={() => undefined} />
          <button className="btn btn-quiet w-full" onClick={() => core?.playClose()}>
            <RotateCcw size={16} /> {a("play_title")}
          </button>
        </div>
      </div>

      <AnimatePresence>
        {confirmResign && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4" onClick={() => setConfirmResign(false)}>
            <div className="card w-full max-w-sm p-6" onClick={(e) => e.stopPropagation()}>
              <h2 className="text-xl font-black">{a("resign_title")}</h2>
              <p className="mt-2 text-dim">{a("resign_body")}</p>
              <div className="mt-6 flex justify-end gap-2">
                <button className="btn btn-ghost" onClick={() => setConfirmResign(false)}>
                  {a("action_cancel")}
                </button>
                <button className="btn btn-primary" onClick={() => { core?.playResign(); setConfirmResign(false); }}>
                  {a("action_resign")}
                </button>
              </div>
            </div>
          </motion.div>
        )}
        {st.result && !hideResult && <ResultDialog st={st} onHide={() => setHideResult(true)} onReview={() => { core?.reviewPlayGame(); router.push("/app/review"); }} />}
      </AnimatePresence>
    </div>
  );
}

function PlayerBar({ name, color, thinking, you }: { name: string; color: Color; thinking: boolean; you?: boolean }) {
  const { a } = useI18n();
  return (
    <div className="flex items-center gap-3 rounded-xl px-1 py-1">
      <span className={`h-7 w-7 rounded-full border border-line-strong ${color === "white" ? "bg-[#f4f4f6]" : "bg-[#16161a]"}`} />
      <span className="font-bold">{name}</span>
      {you && <span className="text-xs text-faint">({a(color === "white" ? "side_white" : "side_black")})</span>}
      {thinking && (
        <span className="ms-auto flex items-center gap-2 text-sm text-dim">
          <Spinner /> {a("zorix_thinking")}
        </span>
      )}
    </div>
  );
}

function ResultDialog({ st, onReview, onHide }: { st: PlayState; onReview: () => void; onHide: () => void }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const r = st.result!;
  const title = r.outcome === "win" ? a("result_win") : r.outcome === "draw" ? a("result_draw") : a("result_loss");
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <motion.div initial={{ scale: 0.9, y: 20 }} animate={{ scale: 1, y: 0 }} className="card w-full max-w-sm p-7 text-center shadow-[var(--shadow)]">
        <Trophy size={56} className={`mx-auto ${r.outcome === "win" ? "text-[#FFC53D]" : r.outcome === "draw" ? "text-silver" : "text-faint"}`} />
        <h2 className="mt-3 text-3xl font-black">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-dim">{a("result_review_hint")}</p>
        <div className="mt-6 grid gap-2">
          <button className="btn btn-primary" onClick={onReview}>
            <Volume2 size={17} /> {a("action_review")}
          </button>
          <div className="grid grid-cols-2 gap-2">
            <button className="btn btn-ghost" onClick={() => core?.playRematch()}>
              <RotateCcw size={16} /> {a("action_rematch")}
            </button>
            {st.bot.level < 20 ? (
              <button className="btn btn-ghost" onClick={() => core?.playStart(st.bot.level + 1, st.userSide)}>
                <PlayIcon size={16} /> {a("action_next_level")}
              </button>
            ) : (
              <button className="btn btn-ghost" onClick={() => core?.playClose()}>
                <Lock size={16} /> {a("action_close")}
              </button>
            )}
          </div>
          <button className="btn btn-quiet" onClick={onHide}>
            {a("action_close")}
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
