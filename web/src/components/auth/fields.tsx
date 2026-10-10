"use client";

import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import { useI18n } from "../site/Providers";

export function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-semibold">{label}</span>
      {children}
      {hint && <span className="mt-1.5 block text-xs text-faint">{hint}</span>}
    </label>
  );
}

export function PasswordInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  const [show, setShow] = useState(false);
  const { t } = useI18n();
  return (
    <div className="relative">
      <input {...props} type={show ? "text" : "password"} className="input pe-12" dir="ltr" />
      <button type="button" className="absolute inset-y-0 end-0 flex w-12 items-center justify-center text-faint hover:text-text" aria-label={show ? t("auth_hide") : t("auth_show")} onClick={() => setShow((s) => !s)}>
        {show ? <EyeOff size={18} /> : <Eye size={18} />}
      </button>
    </div>
  );
}

/** The six-digit code, one box per digit (paste and typing both work). */
export function CodeInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const digits = value.padEnd(6, " ").slice(0, 6).split("");
  return (
    <div dir="ltr" className="relative flex justify-between gap-2">
      {digits.map((d, i) => (
        <div key={i} className={`flex h-14 flex-1 items-center justify-center rounded-xl border text-2xl font-black ${i === value.length ? "border-accent shadow-[0_0_0_4px_var(--glow)]" : "border-line"} bg-surface-2`}>
          {d.trim()}
        </div>
      ))}
      <input
        autoFocus
        inputMode="numeric"
        autoComplete="one-time-code"
        aria-label="code"
        className="absolute inset-0 opacity-0"
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/[^0-9۰-۹٠-٩]/g, "").replace(/[۰-۹]/g, (c) => String("۰۱۲۳۴۵۶۷۸۹".indexOf(c))).replace(/[٠-٩]/g, (c) => String("٠١٢٣٤٥٦٧٨٩".indexOf(c))).slice(0, 6))}
      />
    </div>
  );
}

export function FormError({ text }: { text: string | null }) {
  if (!text) return null;
  return <div className="rounded-xl border border-danger/40 bg-danger/10 px-4 py-3 text-sm font-medium text-danger">{text}</div>;
}

export function FormOk({ text }: { text: string | null }) {
  if (!text) return null;
  return <div className="rounded-xl border border-best/40 bg-best/10 px-4 py-3 text-sm font-medium text-best">{text}</div>;
}

/** POSTs JSON to an auth endpoint. */
export async function post(url: string, data: unknown): Promise<{ ok: boolean; error?: string; redirect?: string; email?: string }> {
  try {
    const r = await fetch(url, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(data) });
    return await r.json();
  } catch {
    return { ok: false, error: "err_generic" };
  }
}
