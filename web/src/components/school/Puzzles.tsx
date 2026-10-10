"use client";

import { ArrowRight, Edit3, Eraser, Lightbulb, Play, RefreshCw, RotateCcw, SkipForward, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { digits } from "@/i18n/site";
import { pieceSrc } from "../board/Board2D";
import { parseFen, type PieceKind } from "../board/model";
import { useI18n } from "../site/Providers";
import { Loading } from "./Analysis";
import { CoachBubble, HelpButton, PageHeader, SchoolBoard, ViewToggle } from "./ui";
import { useChannel, useZorix } from "./ZorixProvider";

/** Puzzle theme id -> the app's text key (same mapping as the app's themeLabel). */
const THEME_KEY: Record<string, string> = {
  mate: "theme_mate",
  mateIn1: "puzzle_mate1",
  mateIn2: "puzzle_mate2",
  fork: "theme_fork",
  pin: "theme_pin",
  skewer: "theme_skewer",
  discoveredAttack: "theme_discovered",
  doubleCheck: "theme_double_check",
  hangingPiece: "theme_hanging",
  capturingDefender: "theme_defender",
  deflection: "theme_deflection",
  attraction: "theme_attraction",
  sacrifice: "theme_sacrifice",
  promotion: "theme_promotion",
  quietMove: "theme_quiet",
  endgame: "theme_endgame",
  opening: "theme_opening",
};

export function Puzzles() {
  const { a, lang } = useI18n();
  const { core } = useZorix();
  const st = useChannel("puzzles");
  const profile = useChannel("profile");

  useEffect(() => {
    if (core && st?.loaded && !st.puzzle) core.puzzleStart("rated", null);
  }, [core, st?.loaded, st?.puzzle]);

  if (!core || !st) return <Loading />;
  const p = st.puzzle;
  const pos = st.position;
  const sideText = a(!st.flipped ? "puzzle_white_to_move" : "puzzle_black_to_move");
  const verdict =
    st.outcome === "correct_step"
      ? { t: a("puzzle_correct"), c: "var(--best)" }
      : st.outcome === "wrong"
        ? { t: a("puzzle_wrong"), c: "var(--danger)" }
        : st.outcome === "solved"
          ? { t: a("puzzle_solved"), c: "var(--best)" }
          : st.outcome === "failed"
            ? { t: a("puzzle_streak_over", digits(lang, st.streak)), c: "var(--danger)" }
            : null;

  return (
    <div className="mx-auto max-w-6xl px-3 py-5 sm:px-6">
      <PageHeader
        title={a("tab_puzzles")}
        subtitle={st.mode === "streak" ? a("puzzle_streak_now", digits(lang, st.streak)) : a("puzzle_solved_line", digits(lang, profile?.puzzlesSolved ?? 0))}
        actions={
          <>
            <Link href="/app/puzzles/build" className="btn btn-ghost">
              <Edit3 size={16} /> <span className="hidden sm:inline">{a("action_build")}</span>
            </Link>
            <ViewToggle />
            <HelpButton topic="puzzles" />
          </>
        }
      />
      <div className="scrollbar-thin -mx-3 mb-4 flex gap-2 overflow-x-auto px-3 pb-1">
        <button className="chip shrink-0" aria-pressed={st.mode === "rated" && !st.theme} onClick={() => core.puzzleStart("rated", null)}>
          {a("puzzle_mode_rated")}
        </button>
        <button className="chip shrink-0" aria-pressed={st.mode === "streak"} onClick={() => core.puzzleStart("streak", null)}>
          {a("puzzle_mode_streak")}
        </button>
        {st.themes.map((th) => (
          <button key={th} className="chip shrink-0" aria-pressed={st.mode === "rated" && st.theme === th} onClick={() => core.puzzleStart("rated", th)}>
            {THEME_KEY[th] ? a(THEME_KEY[th]) : th}
          </button>
        ))}
      </div>
      {!p || !pos ? (
        <Loading />
      ) : (
        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_23rem]">
          <div>
            <div className="mb-3 flex flex-col items-center gap-1.5 text-center">
              <p className="text-lg font-bold">{sideText}</p>
              {verdict && (
                <span className="rounded-full px-3 py-1 text-sm font-bold" style={{ color: verdict.c, background: `color-mix(in srgb, ${verdict.c} 14%, transparent)` }}>
                  {verdict.t}
                </span>
              )}
            </div>
            <SchoolBoard
              fen={pos.fen}
              orientation={st.flipped ? "black" : "white"}
              movable={st.interactive ? pos.turn : "none"}
              lastMove={st.lastMove}
              check={pos.check}
              hint={st.hintSquare}
              onMove={(f, t) => core.puzzleMove(f, t)}
              promotion={st.promotion}
              onPromote={(pc) => core.puzzlePromote(pc ?? "")}
            />
          </div>
          <div className="space-y-4">
            <div className="card flex items-center justify-between p-4">
              <div>
                <div className="text-xs text-dim">#{digits(lang, p.id)}</div>
                <div className="tnum text-2xl font-black">{digits(lang, p.rating)}</div>
              </div>
              {st.ratingChange != null && (
                <span className={`tnum rounded-full px-3 py-1 text-sm font-black ${st.ratingChange >= 0 ? "bg-best/15 text-best" : "bg-danger/15 text-danger"}`}>
                  {st.ratingChange >= 0 ? "+" : ""}
                  {digits(lang, st.ratingChange)}
                </span>
              )}
            </div>
            {st.outcome !== "solving" && p.themes.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {p.themes.filter((th) => THEME_KEY[th]).slice(0, 4).map((th) => (
                  <span key={th} className="chip">
                    {a(THEME_KEY[th])}
                  </span>
                ))}
              </div>
            )}
            {st.explanation && <CoachBubble message={st.explanation} onSpeak={() => core.speak(st.explanation!)} tone={st.outcome === "solved" ? "good" : "default"} />}
            <div className="grid grid-cols-3 gap-2">
              {st.outcome === "solved" || st.outcome === "failed" ? (
                st.outcome === "failed" ? (
                  <button className="btn btn-primary col-span-3" onClick={() => core.puzzleStart("streak", null)}>
                    <RotateCcw size={16} /> {a("puzzle_try_again")}
                  </button>
                ) : (
                  <button className="btn btn-primary col-span-3" onClick={() => core.puzzleNext()}>
                    {a("puzzle_next")} <ArrowRight size={16} className="rtl:rotate-180" />
                  </button>
                )
              ) : (
                <>
                  <button className="btn btn-ghost" onClick={() => core.puzzleHint()}>
                    <Lightbulb size={16} /> {a("puzzle_hint")}
                  </button>
                  <button className="btn btn-ghost" onClick={() => core.puzzleSolution()}>
                    <Play size={16} /> {a("puzzle_solution")}
                  </button>
                  {st.mode === "rated" && (
                    <button className="btn btn-ghost" onClick={() => core.puzzleNext()}>
                      <SkipForward size={16} /> {a("puzzle_skip")}
                    </button>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- puzzle builder

const START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
const KINGS_ONLY = "4k3/8/8/8/8/8/8/4K3";
type Tool = { kind: PieceKind; color: "white" | "black" } | null;

function toFenBoard(board: Map<string, { kind: PieceKind; color: "white" | "black" }>) {
  const rows: string[] = [];
  for (let r = 7; r >= 0; r--) {
    let row = "", empty = 0;
    for (let f = 0; f < 8; f++) {
      const p = board.get("abcdefgh"[f] + (r + 1));
      if (!p) empty++;
      else {
        if (empty) row += empty;
        empty = 0;
        row += p.color === "white" ? p.kind.toUpperCase() : p.kind;
      }
    }
    if (empty) row += empty;
    rows.push(row);
  }
  return rows.join("/");
}

/**
 * "Build a puzzle": both kings stay on the board (they can be moved, never removed), any other pieces
 * anywhere up to 32 in total; then the position goes to the analysis board. Same rules as the app.
 */
export function Builder() {
  const { a, lang } = useI18n();
  const { core } = useZorix();
  const router = useRouter();
  const boardState = useChannel("board");
  const [placement, setPlacement] = useState(() => {
    const fen = boardState?.game.fen.split(" ")[0];
    const b = fen ? parseFen(fen) : null;
    const kings = b ? [...b.values()].filter((p) => p.kind === "k") : [];
    return fen && kings.length === 2 && kings[0].color !== kings[1].color ? fen : KINGS_ONLY;
  });
  const [side, setSide] = useState<"w" | "b">("w");
  const [castling, setCastling] = useState("");
  const [tool, setTool] = useState<Tool>({ kind: "q", color: "white" });
  const [notice, setNotice] = useState<string | null>(null);
  const board = useMemo(() => parseFen(placement), [placement]);
  const count = board.size;
  const fen = `${placement} ${side} ${castling || "-"} - 0 1`;
  const problem = core?.positionProblem(fen) ?? null;

  const tap = (sq: string) => {
    setNotice(null);
    const next = new Map(board);
    const current = next.get(sq);
    const total = next.size;
    if (!tool) {
      if (current?.kind === "k") return setNotice(a("builder_king_stays"));
      next.delete(sq);
    } else if (tool.kind === "k") {
      if (current?.kind === "k") return;
      for (const [s, p] of next) if (p.kind === "k" && p.color === tool.color) next.delete(s);
      next.set(sq, tool);
    } else if (current?.kind === "k") {
      return setNotice(a("builder_king_stays"));
    } else if (current && current.kind === tool.kind && current.color === tool.color) {
      next.delete(sq);
    } else if (tool.kind === "p" && (sq[1] === "1" || sq[1] === "8")) {
      return setNotice(a("builder_pawn_rank"));
    } else if (!current && total >= 32) {
      return setNotice(a("builder_full"));
    } else {
      next.set(sq, tool);
    }
    setPlacement(toFenBoard(next));
  };

  const status = notice ?? (count > 32 ? a("builder_full") : problem === "opponent_in_check" ? a("builder_check") : problem ? a(`problem_${problem}`) : a("builder_ready"));
  const ok = !notice && !problem && count <= 32;
  const castleFlags: [string, string][] = [
    ["K", a("editor_white_oo")],
    ["Q", a("editor_white_ooo")],
    ["k", a("editor_black_oo")],
    ["q", a("editor_black_ooo")],
  ];

  return (
    <div className="mx-auto max-w-6xl px-3 py-5 sm:px-6">
      <PageHeader title={a("builder_title")} subtitle={a("builder_count", digits(lang, count), digits(lang, 32))} actions={<><ViewToggle /><HelpButton topic="builder" /></>} />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <SchoolBoard fen={fen} orientation="white" movable="none" onSquare={tap} />
        <div className="space-y-4">
          <div className={`rounded-xl px-4 py-3 text-sm font-semibold ${ok ? "bg-best/12 text-best" : "bg-danger/12 text-danger"}`}>{status}</div>
          <div className="card space-y-2 p-3" dir="ltr">
            {(["white", "black"] as const).map((color) => (
              <div key={color} className="grid grid-cols-6 gap-1.5">
                {(["q", "r", "b", "n", "p", "k"] as const).map((kind) => {
                  const sel = tool?.kind === kind && tool.color === color;
                  return (
                    <button key={kind} onClick={() => setTool({ kind, color })} className={`aspect-square rounded-xl p-1 transition ${sel ? "bg-accent/25 ring-2 ring-accent" : "bg-surface-2 hover:bg-surface-3"} ${kind === "k" ? "border border-dashed border-line-strong" : ""}`} aria-label={kind === "k" ? a("builder_move_king") : kind}>
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={pieceSrc(color, kind)} alt="" className="h-full w-full" />
                    </button>
                  );
                })}
              </div>
            ))}
            <button onClick={() => setTool(null)} className={`flex w-full items-center justify-center gap-2 rounded-xl py-2 text-sm font-semibold ${tool === null ? "bg-accent/25 ring-2 ring-accent" : "bg-surface-2 hover:bg-surface-3"}`}>
              <Eraser size={16} /> {a("editor_eraser")}
            </button>
          </div>
          <p className="text-xs leading-5 text-dim">{a(tool === null ? "builder_tool_eraser" : tool.kind === "k" ? "builder_tool_king" : "builder_tool_piece")}</p>
          <div className="card space-y-3 p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold">{a("editor_side_to_move")}</span>
              <div className="flex gap-1">
                {(["w", "b"] as const).map((s) => (
                  <button key={s} className="chip" aria-pressed={side === s} onClick={() => { setSide(s); setNotice(null); }}>
                    {a(s === "w" ? "side_white" : "side_black")}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <span className="text-sm font-semibold">{a("editor_castling")}</span>
              <div className="mt-2 grid grid-cols-2 gap-2">
                {castleFlags.map(([flag, label]) => (
                  <label key={flag} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" className="accent-[var(--accent)]" checked={castling.includes(flag)} onChange={(e) => setCastling((c) => ("KQkq".split("").filter((x) => (x === flag ? e.target.checked : c.includes(x))).join("")))} />
                    <span className="ltr">{label}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <button className="btn btn-ghost" onClick={() => { setPlacement(KINGS_ONLY); setCastling(""); setNotice(null); }}>
              <Trash2 size={16} /> {a("editor_clear")}
            </button>
            <button className="btn btn-ghost" onClick={() => { setPlacement(START); setSide("w"); setCastling("KQkq"); setNotice(null); }}>
              <RefreshCw size={16} /> {a("editor_start")}
            </button>
          </div>
          <button
            className="btn btn-primary h-13 w-full"
            onClick={() => {
              if (!ok) return;
              if (core?.boardSetPosition(fen)) router.push("/app/analysis");
            }}
            disabled={!ok}
          >
            {a("builder_analyze")}
          </button>
        </div>
      </div>
    </div>
  );
}
