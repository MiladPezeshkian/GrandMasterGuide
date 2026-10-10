"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { BOARD_COLORS, fileRank, FILES, kingSquare, squareName, trackPieces, turnOf, type BoardPiece, type BoardProps } from "./model";
import { useInteraction } from "./useInteraction";

const PIECE_FILE: Record<string, string> = { p: "P", n: "N", b: "B", r: "R", q: "Q", k: "K" };

export function pieceSrc(color: "white" | "black", kind: string) {
  return `/pieces/${color === "white" ? "w" : "b"}${PIECE_FILE[kind]}.svg`;
}

/** The flat board: tap or drag pieces; arrows, highlights and the promotion choice on top. */
export function Board2D(props: BoardProps) {
  const { fen, orientation, lastMove, check, arrows = [], highlights = {}, stars = [], hint, showLegal = true, coordinates = true, theme = "classic", animate = true } = props;
  const { selected, targets, tap, drop, canPick, board } = useInteraction(props);
  const [pieces, setPieces] = useState<BoardPiece[]>([]);
  const ref = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<{ from: string; x: number; y: number; id: number } | null>(null);
  const colors = BOARD_COLORS[theme];
  const flipped = orientation === "black";

  useEffect(() => setPieces((prev) => trackPieces(prev, fen)), [fen]);

  const pos = (square: string) => {
    const [f, r] = fileRank(square);
    return flipped ? { x: 7 - f, y: r } : { x: f, y: 7 - r };
  };
  const squareAt = (clientX: number, clientY: number): string | null => {
    const el = ref.current;
    if (!el) return null;
    const box = el.getBoundingClientRect();
    const cx = Math.floor(((clientX - box.left) / box.width) * 8);
    const cy = Math.floor(((clientY - box.top) / box.height) * 8);
    if (cx < 0 || cx > 7 || cy < 0 || cy > 7) return null;
    return flipped ? squareName(7 - cx, cy) : squareName(cx, 7 - cy);
  };

  const checkSquare = check ? kingSquare(fen, turnOf(fen)) : null;
  const last = lastMove ? [lastMove.slice(0, 2), lastMove.slice(2, 4)] : [];

  const squares = useMemo(() => {
    const out: string[] = [];
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) out.push(flipped ? squareName(7 - x, y) : squareName(x, 7 - y));
    return out;
  }, [flipped]);

  return (
    <div
      ref={ref}
      dir="ltr"
      className="relative aspect-square w-full touch-none select-none overflow-hidden rounded-xl shadow-[var(--shadow)]"
      onPointerMove={(e) => {
        if (drag) setDrag({ ...drag, x: e.clientX, y: e.clientY });
      }}
      onPointerUp={(e) => {
        if (!drag) return;
        const to = squareAt(e.clientX, e.clientY);
        const from = drag.from;
        setDrag(null);
        if (to === from) tap(from);
        else drop(from, to);
      }}
      onPointerCancel={() => setDrag(null)}
    >
      {/* Squares */}
      <div className="absolute inset-0 grid grid-cols-8 grid-rows-8">
        {squares.map((sq) => {
          const [f, r] = fileRank(sq);
          const light = (f + r) % 2 === 1;
          const p = pos(sq);
          let overlay: string | null = null;
          if (last.includes(sq)) overlay = "rgba(255, 213, 79, 0.42)";
          if (highlights[sq]) overlay = highlights[sq];
          if (hint === sq) overlay = "rgba(47, 210, 124, 0.55)";
          if (selected === sq) overlay = "rgba(20, 160, 255, 0.45)";
          return (
            <div
              key={sq}
              className="relative"
              style={{ background: light ? colors.light : colors.dark }}
              onPointerDown={(e) => {
                if (e.button !== 0) return;
                if (canPick(sq) && !props.onSquare) {
                  ref.current?.setPointerCapture(e.pointerId);
                  const piece = pieces.find((x) => x.square === sq);
                  setDrag({ from: sq, x: e.clientX, y: e.clientY, id: piece?.id ?? -1 });
                } else {
                  tap(sq);
                }
              }}
            >
              {overlay && <div className="absolute inset-0" style={{ background: overlay }} />}
              {checkSquare === sq && <div className="absolute inset-0" style={{ background: "radial-gradient(circle, rgba(255,40,40,0.85) 0%, rgba(255,40,40,0.35) 45%, transparent 72%)" }} />}
              {coordinates && p.x === 0 && (
                <span className="absolute top-0.5 text-[0.62rem] font-bold leading-none" style={{ color: light ? colors.dark : colors.light, left: 3 }}>
                  {r + 1}
                </span>
              )}
              {coordinates && p.y === 7 && (
                <span className="absolute bottom-0.5 text-[0.62rem] font-bold leading-none" style={{ color: light ? colors.dark : colors.light, right: 3 }}>
                  {FILES[f]}
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Stars (lessons) */}
      {stars.map((sq) => {
        const p = pos(sq);
        return (
          <div key={`star-${sq}`} className="pointer-events-none absolute flex items-center justify-center text-[min(5vw,2.2rem)]" style={{ left: `${p.x * 12.5}%`, top: `${p.y * 12.5}%`, width: "12.5%", height: "12.5%" }}>
            <span className="drop-shadow-[0_2px_6px_rgba(0,0,0,0.5)]">⭐</span>
          </div>
        );
      })}

      {/* Pieces */}
      {pieces.map((pc) => {
        const p = pos(pc.square);
        const dragging = drag && drag.id === pc.id;
        let style: React.CSSProperties = {
          left: `${p.x * 12.5}%`,
          top: `${p.y * 12.5}%`,
          transition: animate && !dragging ? "left 0.2s ease, top 0.2s ease" : "none",
        };
        if (dragging && ref.current) {
          const box = ref.current.getBoundingClientRect();
          const size = box.width / 8;
          style = { left: drag.x - box.left - size / 2, top: drag.y - box.top - size / 2, zIndex: 30, transition: "none", transform: "scale(1.12)" };
        }
        return (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            key={pc.id}
            src={pieceSrc(pc.color, pc.kind)}
            alt=""
            draggable={false}
            className="pointer-events-none absolute z-10 h-[12.5%] w-[12.5%] p-[0.6%] drop-shadow-[0_3px_3px_rgba(0,0,0,0.35)]"
            style={style}
          />
        );
      })}

      {/* Legal move dots */}
      {showLegal &&
        targets.map((sq) => {
          const p = pos(sq);
          const occupied = board.has(sq);
          return (
            <div key={`t-${sq}`} className="pointer-events-none absolute z-20 flex items-center justify-center" style={{ left: `${p.x * 12.5}%`, top: `${p.y * 12.5}%`, width: "12.5%", height: "12.5%" }}>
              {occupied ? (
                <div className="h-[92%] w-[92%] rounded-full border-[0.35rem] border-black/25" />
              ) : (
                <div className="h-[30%] w-[30%] rounded-full bg-black/25" />
              )}
            </div>
          );
        })}

      {/* Arrows */}
      {arrows.length > 0 && (
        <svg className="pointer-events-none absolute inset-0 z-20 h-full w-full" viewBox="0 0 8 8">
          {arrows.map((a, i) => {
            const f = pos(a.from), t = pos(a.to);
            const x1 = f.x + 0.5, y1 = f.y + 0.5, x2 = t.x + 0.5, y2 = t.y + 0.5;
            const len = Math.hypot(x2 - x1, y2 - y1);
            const ux = (x2 - x1) / len, uy = (y2 - y1) / len;
            const head = 0.42;
            const ex = x2 - ux * head, ey = y2 - uy * head;
            const color = a.color ?? "rgba(47,210,124,0.85)";
            return (
              <g key={i} opacity={0.9}>
                <line x1={x1} y1={y1} x2={ex} y2={ey} stroke={color} strokeWidth={0.17} strokeLinecap="round" />
                <polygon points={`${x2},${y2} ${ex - uy * 0.27},${ey + ux * 0.27} ${ex + uy * 0.27},${ey - ux * 0.27}`} fill={color} />
              </g>
            );
          })}
        </svg>
      )}

      <Promotion {...props} pos={pos} />
    </div>
  );
}

function Promotion({ promotion, onPromote, pos }: BoardProps & { pos: (sq: string) => { x: number; y: number } }) {
  if (!promotion) return null;
  const p = pos(promotion.to);
  const down = p.y === 0;
  const color = promotion.side ?? (promotion.to[1] === "8" ? "white" : "black");
  return (
    <div className="absolute inset-0 z-40 bg-black/45" onPointerDown={(e) => { e.stopPropagation(); onPromote?.(null); }}>
      <div
        className="absolute flex flex-col overflow-hidden rounded-lg bg-surface shadow-2xl ring-2 ring-accent"
        style={{ left: `${p.x * 12.5}%`, width: "12.5%", ...(down ? { top: 0 } : { bottom: 0 }), flexDirection: down ? "column" : "column-reverse" }}
        onPointerDown={(e) => e.stopPropagation()}
      >
        {(["q", "n", "r", "b"] as const).map((k) => (
          <button key={k} className="aspect-square w-full p-1 hover:bg-surface-3" onClick={() => onPromote?.(k)}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={pieceSrc(color, k)} alt={k} className="h-full w-full" />
          </button>
        ))}
      </div>
    </div>
  );
}
