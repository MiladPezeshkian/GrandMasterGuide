"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useMemo, useState } from "react";
import { appArray, appText } from "@/i18n/app";
import { RTL, siteText, type Lang, type SiteKey } from "@/i18n/site";

type Theme = "zorix" | "sky";

type Ctx = {
  lang: Lang;
  dir: "rtl" | "ltr";
  theme: Theme;
  setLang: (lang: Lang) => void;
  setTheme: (theme: Theme) => void;
  /** Website text. */
  t: (key: SiteKey, vars?: Record<string, string | number>) => string;
  /** The app's (chess school's) text. */
  a: (key: string, ...args: (string | number)[]) => string;
  aa: (key: string) => string[];
};

const I18n = createContext<Ctx | null>(null);

export function Providers({ lang: initialLang, theme: initialTheme, children }: { lang: Lang; theme: Theme; children: React.ReactNode }) {
  const [lang, setLangState] = useState(initialLang);
  const [theme, setThemeState] = useState<Theme>(initialTheme);
  const router = useRouter();

  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    document.documentElement.lang = l;
    document.documentElement.dir = RTL.includes(l) ? "rtl" : "ltr";
    // Server-rendered parts follow once the choice is saved.
    void fetch("/api/lang", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ lang: l }) }).then(() => router.refresh());
  }, [router]);

  const setTheme = useCallback((t: Theme) => {
    setThemeState(t);
    document.documentElement.dataset.theme = t;
    document.cookie = `zx_theme=${t}; path=/; max-age=${365 * 86400}; samesite=lax`;
  }, []);

  const value = useMemo<Ctx>(
    () => ({
      lang,
      dir: RTL.includes(lang) ? "rtl" : "ltr",
      theme,
      setLang,
      setTheme,
      t: (key, vars) => siteText(lang, key, vars),
      a: (key, ...args) => appText(lang, key, ...args),
      aa: (key) => appArray(lang, key),
    }),
    [lang, theme, setLang, setTheme],
  );
  return <I18n.Provider value={value}>{children}</I18n.Provider>;
}

export function useI18n(): Ctx {
  const c = useContext(I18n);
  if (!c) throw new Error("useI18n outside Providers");
  return c;
}
