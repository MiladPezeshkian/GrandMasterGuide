"use client";

import { Sparkles } from "lucide-react";
import { motion } from "motion/react";
import { Board2D } from "../board/Board2D";
import { COACH_SAMPLES } from "../site/coachSamples";
import { useI18n } from "../site/Providers";

const FEN = "r1bqkbnr/pppp1Qpp/2n5/4p3/4P3/8/PPPP1PPP/RNB1KBNR b KQkq - 0 3";

/** A board with the coach's verdict on a real blunder (Qxf7+?? in three languages). */
export function AuthShowcase() {
  const { lang, a, t } = useI18n();
  const s = COACH_SAMPLES[lang].blunder;
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center gap-6 p-12">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_40%,var(--glow),transparent_60%)]" />
      <motion.div initial={{ opacity: 0, y: 20, rotateX: 18 }} animate={{ opacity: 1, y: 0, rotateX: 8 }} transition={{ duration: 1 }} className="relative w-full max-w-md [perspective:1200px]">
        <div className="[transform:rotateX(8deg)]">
          <Board2D fen={FEN} orientation="white" movable="none" lastMove="h5f7" arrows={[{ from: "e8", to: "f7", color: "rgba(255,75,85,0.85)" }]} />
        </div>
      </motion.div>
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.3 }} className="glass relative w-full max-w-md rounded-3xl p-5 shadow-[var(--shadow)]">
        <div className="flex items-center gap-2 text-sm font-bold">
          <span className="ltr text-lg font-black">{s.san}</span>
          <span className="text-danger">{a("quality_blunder")}</span>
        </div>
        <p className="mt-2 line-clamp-4 text-sm leading-7 text-dim">{s.message}</p>
        <div className="mt-3 inline-flex items-center gap-2 text-xs font-semibold text-best">
          <Sparkles size={14} /> {a("coach_best_was", s.best)}
        </div>
      </motion.div>
      <p className="relative text-sm text-faint">{t("free_forever")}</p>
    </div>
  );
}
