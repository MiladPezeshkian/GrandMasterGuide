import strings from "./app.generated.json";
import type { Lang } from "./site";

type Strings = Record<string, string | string[]>;
const ALL = strings as unknown as Record<Lang, Strings>;

/**
 * A text of the app (the chess school) in [lang], with Android-style arguments: "%1$s", "%2$d", "%%".
 */
export function appText(lang: Lang, key: string, ...args: (string | number)[]): string {
  const raw = ALL[lang]?.[key] ?? ALL.en[key];
  if (typeof raw !== "string") return key;
  return raw
    .replace(/%(\d+)\$[sd]/g, (_, n) => String(args[Number(n) - 1] ?? ""))
    .replace(/%[sd]/g, () => String(args.shift() ?? ""))
    .replace(/%%/g, "%");
}

/** A string array of the app (help pages: "Title|Text" items). */
export function appArray(lang: Lang, key: string): string[] {
  const raw = ALL[lang]?.[key] ?? ALL.en[key];
  return Array.isArray(raw) ? raw : [];
}
