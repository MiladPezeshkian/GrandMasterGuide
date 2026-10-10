"use client";

import { Languages, Moon, Sun } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { LANG_NAMES, LANGS } from "@/i18n/site";
import { useI18n } from "./Providers";

export function LangSwitcher() {
  const { lang, setLang, t } = useI18n();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const close = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);
  return (
    <div ref={ref} className="relative">
      <button className="btn btn-quiet" aria-label={t("nav_language")} aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Languages size={18} />
        <span className="hidden text-sm sm:inline">{LANG_NAMES[lang]}</span>
      </button>
      {open && (
        <div className="glass absolute end-0 top-full z-50 mt-2 min-w-36 overflow-hidden rounded-2xl p-1 shadow-[var(--shadow)]">
          {LANGS.map((l) => (
            <button
              key={l}
              onClick={() => {
                setLang(l);
                setOpen(false);
              }}
              className={`block w-full rounded-xl px-4 py-2.5 text-start text-sm transition hover:bg-surface-2 ${l === lang ? "font-bold text-accent" : ""}`}
            >
              {LANG_NAMES[l]}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function ThemeToggle() {
  const { theme, setTheme, t } = useI18n();
  return (
    <button className="btn btn-quiet" aria-label={t("nav_theme")} onClick={() => setTheme(theme === "zorix" ? "sky" : "zorix")}>
      {theme === "zorix" ? <Sun size={18} /> : <Moon size={18} />}
    </button>
  );
}
