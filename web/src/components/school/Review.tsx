"use client";

import { ArrowLeft, RotateCcw, Target } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo } from "react";
import { digits } from "@/i18n/site";
import { useI18n } from "../site/Providers";
import { Loading } from "./Analysis";
import { MoveList } from "./MoveList";
import type { Quality, ReviewState } from "./types";
import { CoachBubble, formatScore, HelpButton, PageHeader, QUALITY_COLOR, QualityBadge, QualityDot, SchoolBoard, ViewToggle } from "./ui";
import { useChannel, useZorix } from "./ZorixProvider";

const ORDER: Quality[] = ["brilliant", "great", "best", "excellent", "good", "book", "inaccuracy", "mistake", "miss", "blunder"];

export function Review() {
  const { a, t } = useI18n();
  const { core } = useZorix();
  const st = useChannel("review");
  if (!core || !st) return <Loading />;
  if (!st.game) {
    return (
      <div className="mx-auto max-w-xl px-6 py-24 text-center">
        <h1 className="text-2xl font-black">{a("review_title")}</h1>
        <p className="mt-3 text-dim">{a("result_review_hint")}</p>
        <Link href="/app/play" className="btn btn-primary mt-6">
          {t("tab_play")}
        </Link>
      </div>
    );
  }
  return <ReviewView st={st} />;
}

function ReviewView({ st }: { st: ReviewState }) {
  const { a, lang, dir } = useI18n();
  const { core } = useZorix();
  const g = st.game!;
  const r = st.review;
  const ply = st.ply;
  const fen = ply === 0 ? g.startFen : g.plies[ply - 1].fen;
  const current = r && ply > 0 ? r.moves[ply - 1] : null;
  const plies = useMemo(() => g.plies.map((p, i) => ({ ...p, quality: r?.moves[i]?.quality ?? null })), [g.plies, r]);
  const player = st.player ?? "white";

  // Arrow keys step through the game.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (st.retry) return;
      const back = dir === "rtl" ? "ArrowRight" : "ArrowLeft";
      const fwd = dir === "rtl" ? "ArrowLeft" : "ArrowRight";
      if (e.key === back) core?.reviewGoTo(ply - 1);
      if (e.key === fwd) core?.reviewGoTo(ply + 1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [core, ply, dir, st.retry]);

  if (st.retry) return <RetryView st={st} />;

  return (
    <div className="mx-auto max-w-7xl px-3 py-5 sm:px-6">
      <PageHeader title={a("review_title")} subtitle={r?.opening ?? undefined} actions={<><ViewToggle /><HelpButton topic="review" /></>} />
      {!r && (
        <div className="card mb-5 p-5">
          <div className="text-sm font-semibold">{a("review_analysing")}</div>
          <div className="mt-3 h-2 overflow-hidden rounded-full bg-surface-3">
            <div className="h-full rounded-full transition-[width] duration-300" style={{ width: `${Math.round(st.progress * 100)}%`, background: "var(--grad-accent)" }} />
          </div>
          <div className="tnum mt-2 text-xs text-faint">{digits(lang, Math.round(st.progress * 100))}%</div>
        </div>
      )}
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_25rem]">
        <div>
          <SchoolBoard fen={fen} orientation={player} movable="none" lastMove={ply > 0 ? g.plies[ply - 1].uci : null} arrows={current?.best && current.best !== current.uci ? [{ from: current.best.slice(0, 2), to: current.best.slice(2, 4) }] : []} />
          {r && <EvalGraph curve={r.evalCurve} ply={ply} onPick={(p) => core?.reviewGoTo(p)} />}
        </div>
        <div className="space-y-4">
          {r && (
            <div className="grid grid-cols-2 gap-3">
              {(["white", "black"] as const).map((side) => (
                <div key={side} className={`card p-4 ${side === player ? "!border-accent/60" : ""}`}>
                  <div className="flex items-center gap-2 text-xs font-semibold text-dim">
                    <span className={`h-3 w-3 rounded-full border border-line-strong ${side === "white" ? "bg-[#f4f4f6]" : "bg-[#16161a]"}`} /> {a(side === "white" ? "side_white" : "side_black")}
                  </div>
                  <div className="tnum mt-1 text-3xl font-black">{r.accuracy[side] != null ? digits(lang, r.accuracy[side]!.toFixed(1)) : "—"}</div>
                  <div className="text-xs text-faint">{a("review_accuracy")}</div>
                </div>
              ))}
            </div>
          )}
          {r && (
            <div className="card divide-y divide-line">
              {ORDER.filter((q) => (r.counts.white[q] ?? 0) + (r.counts.black[q] ?? 0) > 0).map((q) => (
                <div key={q} className="grid grid-cols-[2.5rem_1fr_2.5rem] items-center gap-2 px-4 py-2 text-sm">
                  <span className="tnum text-center font-bold">{digits(lang, r.counts.white[q] ?? 0)}</span>
                  <span className="flex items-center justify-center gap-2 font-semibold" style={{ color: QUALITY_COLOR[q] }}>
                    <QualityDot q={q} size={18} /> {a(`quality_${q}`)}
                  </span>
                  <span className="tnum text-center font-bold">{digits(lang, r.counts.black[q] ?? 0)}</span>
                </div>
              ))}
            </div>
          )}
          {current && (
            <CoachBubble
              message={current.message}
              onSpeak={() => core?.speak(current.speech)}
              badge={
                <>
                  <span className="ltr font-black">{current.san}</span>
                  <QualityBadge q={current.quality} />
                  <span className="ltr text-xs text-dim">{formatScore(current.scoreAfter)}</span>
                </>
              }
            />
          )}
          {r && r.keyMoments[player].length > 0 && (
            <div className="card p-4">
              <div className="mb-3 flex items-center gap-2 text-sm font-bold">
                <Target size={16} className="text-accent" /> {a("review_key_moments")}
              </div>
              <div className="flex flex-wrap gap-2">
                {r.keyMoments[player].map((i) => {
                  const m = r.moves[i];
                  return (
                    <div key={i} className="flex items-center gap-1 rounded-xl border border-line bg-surface-2 p-1">
                      <button className="ltr flex items-center gap-1.5 rounded-lg px-2 py-1 text-sm font-bold hover:bg-surface-3" onClick={() => core?.reviewGoTo(i + 1)}>
                        <QualityDot q={m.quality} size={18} /> {Math.floor(i / 2) + 1}
                        {i % 2 ? "…" : "."} {m.san}
                      </button>
                      <button className="rounded-lg px-2 py-1 text-xs font-bold text-accent hover:bg-surface-3" onClick={() => core?.reviewRetry(i)}>
                        <RotateCcw size={14} className="inline" /> {a("review_retry")}
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          <MoveList plies={plies} current={ply} onGoTo={(n) => core?.reviewGoTo(n)} />
        </div>
      </div>
    </div>
  );
}

function EvalGraph({ curve, ply, onPick }: { curve: number[]; ply: number; onPick: (ply: number) => void }) {
  if (curve.length < 2) return null;
  const w = 600, h = 120;
  const x = (i: number) => (i / (curve.length - 1)) * w;
  const y = (v: number) => h / 2 - (Math.max(-10, Math.min(10, v)) / 10) * (h / 2 - 4);
  const pts = curve.map((v, i) => `${x(i)},${y(v)}`).join(" ");
  return (
    <svg style={{ direction: "ltr" }} viewBox={`0 0 ${w} ${h}`} className="mt-3 h-28 w-full cursor-pointer overflow-visible rounded-xl bg-[#2a2a31]" preserveAspectRatio="none" onClick={(e) => {
      const box = (e.currentTarget as SVGSVGElement).getBoundingClientRect();
      onPick(Math.round(((e.clientX - box.left) / box.width) * (curve.length - 1)));
    }}>
      <polygon points={`0,${h} ${pts} ${w},${h}`} fill="#f4f4f6" />
      <line x1={0} x2={w} y1={h / 2} y2={h / 2} stroke="#888" strokeWidth={0.6} strokeDasharray="4 4" />
      <line x1={x(ply)} x2={x(ply)} y1={0} y2={h} stroke="var(--accent)" strokeWidth={2} />
    </svg>
  );
}

function RetryView({ st }: { st: ReviewState }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const r = st.retry!;
  const moment = st.review?.moves[r.index];
  return (
    <div className="mx-auto max-w-6xl px-3 py-5 sm:px-6">
      <PageHeader
        title={a("review_retry")}
        subtitle={a("review_retry_prompt")}
        actions={
          <button className="btn btn-ghost" onClick={() => core?.reviewEndRetry()}>
            <ArrowLeft size={16} /> {a("review_back_to_game")}
          </button>
        }
      />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <SchoolBoard
          fen={r.fen}
          orientation={r.turn}
          movable={r.checking || r.solved ? "none" : r.turn}
          onMove={(f, t) => {
            const promo = core?.isPromotion(r.fen, f, t) ? "q" : "";
            core?.reviewRetryMove(f + t + promo);
          }}
        />
        <div className="space-y-4">
          {moment && (
            <div className="card flex items-center gap-2 p-4 text-sm">
              <QualityDot q={moment.quality} /> <span className="ltr font-bold">{moment.san}</span> <QualityBadge q={moment.quality} />
            </div>
          )}
          <CoachBubble message={r.message} busy={r.checking} tone={r.solved === true ? "good" : r.solved === false ? "bad" : "default"} />
          {r.solved === false && (
            <button className="btn btn-ghost w-full" onClick={() => core?.reviewRetry(r.index)}>
              <RotateCcw size={16} /> {a("puzzle_retry")}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
