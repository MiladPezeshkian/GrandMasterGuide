import { digits, type Lang } from "@/i18n/site";

const LOCALE: Record<Lang, string> = { fa: "fa-IR", ckb: "ar-IQ", en: "en-GB" };

/** "3 h 12 min" (localized digits). */
export function duration(lang: Lang, seconds: number): string {
  const h = Math.floor(seconds / 3600), m = Math.floor((seconds % 3600) / 60);
  const unitH = lang === "en" ? "h" : lang === "fa" ? "ساعت" : "کاتژمێر";
  const unitM = lang === "en" ? "min" : lang === "fa" ? "دقیقه" : "خولەک";
  if (h === 0) return `${digits(lang, m)} ${unitM}`;
  return `${digits(lang, h)} ${unitH} ${digits(lang, m)} ${unitM}`;
}

export function dateTime(lang: Lang, iso: string | null): string {
  if (!iso) return "—";
  return new Intl.DateTimeFormat(LOCALE[lang], { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
}

export function date(lang: Lang, iso: string): string {
  return new Intl.DateTimeFormat(LOCALE[lang], { dateStyle: "medium" }).format(new Date(iso));
}

/** "5 minutes ago". */
export function ago(lang: Lang, iso: string | null): string {
  if (!iso) return "—";
  const s = (new Date(iso).getTime() - Date.now()) / 1000;
  const rtf = new Intl.RelativeTimeFormat(lang === "ckb" ? "fa" : lang, { numeric: "auto" });
  const abs = Math.abs(s);
  if (abs < 60) return rtf.format(Math.round(s), "second");
  if (abs < 3600) return rtf.format(Math.round(s / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(s / 3600), "hour");
  if (abs < 86400 * 30) return rtf.format(Math.round(s / 86400), "day");
  return date(lang, iso);
}
