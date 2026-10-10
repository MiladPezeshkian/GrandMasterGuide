"use client";

import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useRef } from "react";
import { useI18n } from "../site/Providers";
import type { Ply } from "./types";
import { QUALITY_COLOR } from "./ui";

/** Moves in pairs ("1. e4 e5"), coloured by the coach's verdicts; tap one to go back to it. */
export function MoveList({ plies, current, redo = 0, onGoTo, all }: { plies: Ply[]; current: number; redo?: number; onGoTo: (ply: number) => void; all?: Ply[] }) {
  const { a, dir } = useI18n();
  const line = all ?? plies;
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.querySelector("[data-current='true']")?.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [current]);
  const total = line.length;
  const rows: { n: number; w?: [Ply, number]; b?: [Ply, number] }[] = [];
  line.forEach((p, i) => {
    const moveNo = Math.floor(i / 2) + 1;
    if (i % 2 === 0) rows.push({ n: moveNo, w: [p, i] });
    else if (rows.length) rows[rows.length - 1].b = [p, i];
    else rows.push({ n: moveNo, b: [p, i] });
  });
  const [Prev, Next] = dir === "rtl" ? [ChevronRight, ChevronLeft] : [ChevronLeft, ChevronRight];
  const [First, Last] = dir === "rtl" ? [ChevronLast, ChevronFirst] : [ChevronFirst, ChevronLast];
  const cell = (entry?: [Ply, number]) => {
    if (!entry) return <span />;
    const [p, i] = entry;
    const isCurrent = i === current - 1;
    const future = i >= current;
    return (
      <button
        data-current={isCurrent}
        onClick={() => onGoTo(i + 1)}
        className={`ltr flex items-center gap-1.5 rounded-lg px-2 py-1 text-start text-sm font-semibold transition ${isCurrent ? "bg-accent text-on-accent" : future ? "text-faint hover:bg-surface-2" : "hover:bg-surface-2"}`}
      >
        {p.quality && <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: QUALITY_COLOR[p.quality] }} />}
        {p.san}
      </button>
    );
  };
  return (
    <div className="card overflow-hidden">
      <div ref={ref} className="scrollbar-thin max-h-56 overflow-y-auto p-2" dir="ltr">
        {total === 0 ? (
          <p className="p-2 text-sm text-dim" dir={dir}>
            {a("moves_empty")}
          </p>
        ) : (
          <div className="grid grid-cols-[2.2rem_1fr_1fr] gap-x-1 gap-y-0.5">
            {rows.map((r) => (
              <div key={r.n} className="contents">
                <span className="tnum self-center text-xs text-faint">{r.n}.</span>
                {cell(r.w)}
                {cell(r.b)}
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="flex border-t border-line" dir="ltr">
        {[
          [First, 0, a("action_first")],
          [Prev, Math.max(0, current - 1), a("action_previous")],
          [Next, Math.min(total, current + 1), a("action_next")],
          [Last, total, a("action_last")],
        ].map(([Icon, target, label], i) => {
          const I = Icon as typeof ChevronLeft;
          return (
            <button key={i} className="flex flex-1 items-center justify-center py-2.5 text-dim transition hover:bg-surface-2 hover:text-text disabled:opacity-30" disabled={target === current} aria-label={label as string} onClick={() => onGoTo(target as number)}>
              <I size={18} />
            </button>
          );
        })}
      </div>
      {redo > 0 && <span className="sr-only">{redo}</span>}
    </div>
  );
}
