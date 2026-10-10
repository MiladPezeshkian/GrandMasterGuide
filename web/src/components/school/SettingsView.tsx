"use client";

import Link from "next/link";
import { useState } from "react";
import { LANG_NAMES, LANGS } from "@/i18n/site";
import { BOARD_COLORS, type BoardThemeId } from "../board/model";
import { useI18n } from "../site/Providers";
import { Loading } from "./Analysis";
import { HelpButton, PageHeader, ViewToggle } from "./ui";
import { useChannel, useZorix } from "./ZorixProvider";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="card p-5">
      <h2 className="mb-4 text-sm font-bold text-accent">{title}</h2>
      <div className="space-y-4">{children}</div>
    </section>
  );
}

function Toggle({ label, hint, checked, onChange }: { label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-start gap-4">
      <div className="min-w-0 flex-1">
        <div className="font-semibold">{label}</div>
        {hint && <div className="mt-0.5 text-xs leading-5 text-dim">{hint}</div>}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative mt-0.5 h-7 w-12 shrink-0 rounded-full transition ${checked ? "bg-accent" : "bg-surface-3"}`}
      >
        <span className={`absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition-all ${checked ? "start-[1.4rem]" : "start-0.5"}`} />
      </button>
    </label>
  );
}

export function SettingsView({ account }: { account: { email: string; username: string } }) {
  const { a, t, lang, setLang, theme, setTheme } = useI18n();
  const { core } = useZorix();
  const st = useChannel("board");
  const profile = useChannel("profile");
  const [name, setName] = useState<string | null>(null);
  if (!core || !st) return <Loading />;
  const s = st.settings;
  const set = (key: string, value: string | boolean | number) => core.setSetting(key, String(value));

  return (
    <div className="mx-auto max-w-3xl px-3 py-5 sm:px-6">
      <PageHeader title={a("settings_title")} actions={<HelpButton topic="settings" />} />
      <div className="space-y-4">
        <Section title={a("settings_profile")}>
          <div className="flex gap-2">
            <input className="input" maxLength={24} value={name ?? profile?.name ?? ""} onChange={(e) => setName(e.target.value)} placeholder={a("onboard_name_hint")} />
            <button className="btn btn-primary" disabled={!name?.trim()} onClick={() => { core.rename(name!.trim()); setName(null); }}>
              {t("admin_save")}
            </button>
          </div>
          <div className="grid gap-1 text-sm text-dim">
            <span className="ltr">{account.email}</span>
            <span className="ltr">@{account.username}</span>
          </div>
        </Section>

        <Section title={a("settings_appearance")}>
          <div>
            <div className="mb-2 font-semibold">{a("settings_language")}</div>
            <div className="flex flex-wrap gap-2">
              {LANGS.map((l) => (
                <button key={l} className="chip" aria-pressed={lang === l} onClick={() => setLang(l)}>
                  {LANG_NAMES[l]}
                </button>
              ))}
            </div>
          </div>
          <div>
            <div className="mb-2 font-semibold">{t("nav_theme")}</div>
            <div className="flex flex-wrap gap-2">
              {(["zorix", "sky"] as const).map((th) => (
                <button key={th} className="chip" aria-pressed={theme === th} onClick={() => { setTheme(th); set("appTheme", th); }}>
                  {a(`theme_${th}`)}
                </button>
              ))}
            </div>
          </div>
        </Section>

        <Section title={a("settings_board")}>
          <div className="flex items-center justify-between">
            <span className="font-semibold">{t("board_view")}</span>
            <ViewToggle />
          </div>
          <div>
            <div className="mb-2 font-semibold">{a("settings_theme")}</div>
            <div className="grid grid-cols-5 gap-2">
              {(Object.keys(BOARD_COLORS) as BoardThemeId[]).map((id) => (
                <button key={id} onClick={() => set("boardTheme", id)} className={`overflow-hidden rounded-xl border-2 transition ${s.boardTheme === id ? "border-accent" : "border-transparent"}`} aria-label={a(`theme_${id}`)}>
                  <div className="grid aspect-square grid-cols-2 grid-rows-2">
                    {[0, 1, 2, 3].map((i) => (
                      <div key={i} style={{ background: (i === 0 || i === 3) ? BOARD_COLORS[id].light : BOARD_COLORS[id].dark }} />
                    ))}
                  </div>
                  <div className="truncate bg-surface-2 px-1 py-1 text-[0.65rem] font-semibold">{a(`theme_${id}`)}</div>
                </button>
              ))}
            </div>
          </div>
          <Toggle label={a("settings_coordinates")} checked={s.showCoordinates} onChange={(v) => set("showCoordinates", v)} />
          <Toggle label={a("settings_legal_moves")} checked={s.showLegalMoves} onChange={(v) => set("showLegalMoves", v)} />
          <Toggle label={a("settings_arrows")} checked={s.showArrows} onChange={(v) => set("showArrows", v)} />
          <Toggle label={a("settings_animations")} checked={s.animateMoves} onChange={(v) => set("animateMoves", v)} />
        </Section>

        <Section title={a("settings_learning")}>
          <Toggle label={a("settings_coach")} hint={a("settings_coach_hint")} checked={s.coachMode} onChange={(v) => set("coachMode", v)} />
          <Toggle label={a("settings_voice")} hint={`${a("settings_voice_hint")} ${t("voice_browser_note")}`} checked={s.voice} onChange={(v) => set("voice", v)} />
          <Toggle label={a("settings_explain_bot")} hint={a("settings_explain_bot_hint")} checked={s.explainBotMoves} onChange={(v) => set("explainBotMoves", v)} />
        </Section>

        <Section title={a("settings_engine")}>
          <div>
            <div className="flex items-center justify-between font-semibold">
              {a("settings_think_time")}
              <span className="ltr tnum text-accent">{a("think_time_value", (s.thinkTimeMs / 1000).toFixed(1))}</span>
            </div>
            <input type="range" min={500} max={30000} step={500} value={s.thinkTimeMs} onChange={(e) => set("thinkTimeMs", e.target.value)} className="mt-3 w-full accent-[var(--accent)]" dir="ltr" />
            <p className="mt-1 text-xs leading-5 text-dim">{a("settings_think_time_hint")}</p>
          </div>
          <div>
            <div className="mb-2 font-semibold">{a("settings_lines")}</div>
            <div className="flex gap-2">
              {[1, 2, 3].map((n) => (
                <button key={n} className="chip" aria-pressed={s.analysisLines === n} onClick={() => set("analysisLines", n)}>
                  {n}
                </button>
              ))}
            </div>
            <p className="mt-1 text-xs leading-5 text-dim">{a("settings_lines_hint")}</p>
          </div>
          <div>
            <div className="mb-2 font-semibold">{a("settings_hash")}</div>
            <div className="flex flex-wrap gap-2">
              {[16, 32, 64, 128, 256].map((n) => (
                <button key={n} className="chip ltr" aria-pressed={s.hashMb === n} onClick={() => set("hashMb", n)}>
                  {a("mb_value", n)}
                </button>
              ))}
            </div>
            <p className="mt-1 text-xs leading-5 text-dim">{a("settings_hash_hint")}</p>
          </div>
          {st.engine.kind === "ready" && <p className="ltr text-xs text-faint">{st.engine.name}</p>}
        </Section>

        <Section title={a("action_about")}>
          <p className="text-sm font-semibold">{a("about_author")}</p>
          <p className="text-sm leading-7 text-dim">{t("made_by")}</p>
          <Link href="/credits" className="text-sm font-bold text-accent hover:underline">
            {t("footer_credits")}
          </Link>
        </Section>
      </div>
    </div>
  );
}
