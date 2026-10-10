import "server-only";
import { cookies, headers } from "next/headers";
import { isLang, RTL, siteText, type Lang, type SiteKey } from "./site";

export const LANG_COOKIE = "zx_lang";

/** The visitor's language: their choice (cookie), else the browser's, else Persian. */
export async function getLang(): Promise<Lang> {
  const chosen = (await cookies()).get(LANG_COOKIE)?.value;
  if (isLang(chosen)) return chosen;
  const accept = ((await headers()).get("accept-language") ?? "").toLowerCase();
  for (const part of accept.split(",")) {
    const code = part.trim().split(";")[0];
    if (code.startsWith("fa")) return "fa";
    if (code.startsWith("ckb") || code.startsWith("ku")) return "ckb";
    if (code.startsWith("en")) return "en";
  }
  return "fa";
}

export async function serverT() {
  const lang = await getLang();
  return {
    lang,
    dir: RTL.includes(lang) ? ("rtl" as const) : ("ltr" as const),
    t: (key: SiteKey, vars?: Record<string, string | number>) => siteText(lang, key, vars),
  };
}
