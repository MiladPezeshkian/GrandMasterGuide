"use client";

import { motion } from "motion/react";
import { useState } from "react";
import { useI18n } from "../site/Providers";
import { useZorix } from "./ZorixProvider";

const LEVELS = ["new", "rules", "casual", "club", "strong"] as const;

/** First visit: the name the coach uses and how well the player plays (as in the app). */
export function Onboarding({ defaultName }: { defaultName: string }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const [name, setName] = useState(defaultName);
  const [step, setStep] = useState(0);
  const [level, setLevel] = useState<(typeof LEVELS)[number]>("rules");
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <motion.div initial={{ opacity: 0, scale: 0.94, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} className="card w-full max-w-lg p-7 shadow-[var(--shadow)]">
        {step === 0 ? (
          <>
            <h2 className="text-2xl font-black">{a("onboard_welcome")}</h2>
            <p className="mt-2 text-dim">{a("onboard_name_body")}</p>
            <input className="input mt-6" value={name} maxLength={24} placeholder={a("onboard_name_hint")} onChange={(e) => setName(e.target.value)} />
            <button className="btn btn-primary mt-6 w-full" disabled={!name.trim()} onClick={() => setStep(1)}>
              {a("onboard_next")}
            </button>
          </>
        ) : (
          <>
            <h2 className="text-2xl font-black">{a("onboard_level_title", name.trim())}</h2>
            <p className="mt-2 text-dim">{a("onboard_level_body")}</p>
            <div className="mt-5 grid gap-2">
              {LEVELS.map((l) => (
                <button key={l} onClick={() => setLevel(l)} className={`rounded-2xl border p-4 text-start transition ${level === l ? "border-accent bg-[color-mix(in_srgb,var(--accent)_14%,transparent)]" : "border-line bg-surface-2 hover:border-line-strong"}`}>
                  <div className="font-bold">{a(`exp_${l}`)}</div>
                  <div className="text-sm text-dim">{a(`exp_${l}_body`)}</div>
                </button>
              ))}
            </div>
            <button className="btn btn-primary mt-6 w-full" disabled={!core} onClick={() => core?.completeOnboarding(name.trim(), level)}>
              {a("onboard_finish")}
            </button>
          </>
        )}
      </motion.div>
    </div>
  );
}
