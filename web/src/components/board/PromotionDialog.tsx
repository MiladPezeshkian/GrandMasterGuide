"use client";

import { pieceSrc } from "./Board2D";

/** Choice of the new piece for a promoted pawn (used over the 3D board). */
export function PromotionDialog({ color, onPick }: { color: "white" | "black"; onPick: (p: "q" | "r" | "b" | "n" | null) => void }) {
  return (
    <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/50 backdrop-blur-[2px]" onClick={() => onPick(null)}>
      <div className="glass flex gap-2 rounded-2xl p-3 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        {(["q", "r", "b", "n"] as const).map((k) => (
          <button key={k} className="h-16 w-16 rounded-xl bg-surface-2 p-1.5 transition hover:scale-105 hover:bg-surface-3 sm:h-20 sm:w-20" onClick={() => onPick(k)}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={pieceSrc(color, k)} alt={k} className="h-full w-full" />
          </button>
        ))}
      </div>
    </div>
  );
}
