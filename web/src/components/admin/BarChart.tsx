"use client";

import { useState } from "react";
import { digits } from "@/i18n/site";
import { useI18n } from "../site/Providers";

/**
 * One series over the last days: thin bars (4px rounded tops, 2px gaps) in the accent colour,
 * a recessive grid, the peak labelled, and a tooltip with the exact value on hover / tap.
 */
export function BarChart({ title, points, unit }: { title: string; points: { day: string; value: number }[]; unit?: string }) {
  const { lang } = useI18n();
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(1, ...points.map((p) => p.value));
  const nice = max <= 4 ? 4 : Math.ceil(max / 4) * 4;
  const total = points.reduce((s, p) => s + p.value, 0);
  const fmtDay = (d: string) => new Intl.DateTimeFormat(lang === "fa" ? "fa-IR" : lang === "ckb" ? "ar-IQ" : "en-GB", { month: "short", day: "numeric" }).format(new Date(d + "T12:00:00Z"));
  const peak = points.reduce((b, p, i) => (p.value > points[b].value ? i : b), 0);
  const h = 160;
  return (
    <div className="card p-5">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="text-sm font-bold">{title}</h3>
        <span className="tnum text-xs text-dim">
          Σ {digits(lang, total)}
          {unit ? ` ${unit}` : ""}
        </span>
      </div>
      <div className="relative mt-4" dir="ltr" onMouseLeave={() => setHover(null)}>
        <div className="absolute inset-x-0 top-0" style={{ height: h }}>
          {[0, 0.25, 0.5, 0.75, 1].map((f) => (
            <div key={f} className="absolute inset-x-0 border-t border-line/60" style={{ top: h - f * h }}>
              <span className="tnum absolute -top-2 left-0 bg-surface pe-1 text-[0.6rem] text-faint">{digits(lang, Math.round(nice * f))}</span>
            </div>
          ))}
          {hover != null && (
            <div className="pointer-events-none absolute z-10 -translate-x-1/2 rounded-lg bg-text px-2.5 py-1.5 text-center text-xs whitespace-nowrap text-bg shadow-lg" style={{ left: `${((hover + 0.5) / points.length) * 100}%`, top: -8 }}>
              <div className="tnum font-bold">
                {digits(lang, points[hover].value)}
                {unit ? ` ${unit}` : ""}
              </div>
              <div className="opacity-70">{fmtDay(points[hover].day)}</div>
            </div>
          )}
        </div>
        <div className="relative ms-7 flex items-end gap-[2px]" style={{ height: h }}>
          {points.map((p, i) => (
            <div key={p.day} className="relative flex h-full flex-1 cursor-pointer items-end" onMouseEnter={() => setHover(i)} onClick={() => setHover(i)}>
              <div className="w-full rounded-t-[4px] transition-colors" style={{ height: `${(p.value / nice) * 100}%`, minHeight: p.value ? 2 : 0, background: hover === i ? "var(--accent-bright)" : "var(--accent)", opacity: hover == null || hover === i ? 1 : 0.55 }} />
              {i === peak && p.value > 0 && hover == null && <span className="tnum absolute left-1/2 -translate-x-1/2 text-[0.65rem] font-bold text-text" style={{ bottom: `calc(${(p.value / nice) * 100}% + 4px)` }}>{digits(lang, p.value)}</span>}
            </div>
          ))}
        </div>
        <div className="ms-7 mt-1.5 flex justify-between text-[0.62rem] text-faint">
          <span>{fmtDay(points[0].day)}</span>
          <span>{fmtDay(points[points.length - 1].day)}</span>
        </div>
      </div>
    </div>
  );
}
