"use client";

import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Bot,
  Brain,
  Cpu,
  Globe2,
  Puzzle,
  Sparkles,
  UserPlus,
  Users,
  Volume2,
} from "lucide-react";
import { motion, useInView } from "motion/react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { digits, type SiteKey } from "@/i18n/site";
import { COACH_SAMPLES } from "./coachSamples";
import { useI18n } from "./Providers";

const HeroScene = dynamic(() => import("../three/HeroScene"), { ssr: false });

const QUALITY_COLOR: Record<string, string> = { blunder: "#ff4b55", book: "#c49a6c", best: "#2fd27c", good: "#a7c48f" };

const reveal = {
  initial: { opacity: 0, y: 28 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: "-80px" },
  transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1] as const },
};

export function Landing({ signedIn }: { signedIn: boolean }) {
  const { t, dir } = useI18n();
  const Forward = dir === "rtl" ? ArrowLeft : ArrowRight;
  const start = signedIn ? "/app" : "/register";
  return (
    <main>
      <Hero start={start} signedIn={signedIn} Forward={Forward} />
      <Stats />
      <CoachShowcase />
      <Features />
      <Levels />
      <How />
      <Languages />
      <section className="relative overflow-hidden px-6 py-24">
        <div className="aurora absolute inset-0 opacity-80" />
        <motion.div {...reveal} className="relative mx-auto max-w-3xl text-center">
          <h2 className="text-3xl font-black tracking-tight sm:text-5xl">{t("cta_title")}</h2>
          <p className="mt-4 text-lg text-dim">{t("cta_sub")}</p>
          <Link href={start} className="btn btn-primary mt-8 h-14 px-8 text-lg">
            {signedIn ? t("nav_app") : t("hero_cta")} <Forward size={20} />
          </Link>
        </motion.div>
      </section>
    </main>
  );
}

function Hero({ start, signedIn, Forward }: { start: string; signedIn: boolean; Forward: typeof ArrowRight }) {
  const { t, theme, dir } = useI18n();
  const wide = useMediaQuery("(min-width: 1024px)");
  return (
    <section className="relative isolate overflow-hidden">
      <div className="aurora absolute inset-0 -z-20" />
      <div className="grid-lines absolute inset-0 -z-20 opacity-60" />
      {wide && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 1.4, delay: 0.1 }} className="absolute inset-0 -z-10">
          <HeroScene theme={theme} side={dir === "rtl" ? "left" : "right"} />
        </motion.div>
      )}
      <div className="mx-auto flex min-h-[calc(100dvh-4rem)] max-w-7xl flex-col justify-center px-6 pb-10 pt-8 lg:pt-0">
        {wide === false && (
          <div className="relative -mx-6 mb-2 h-[46vh] min-h-[320px]">
            <HeroScene theme={theme} side="center" />
          </div>
        )}
        <div className="relative z-10 max-w-xl lg:max-w-[38rem]">
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }} className="glass inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-xs font-semibold text-dim sm:text-sm">
            <Sparkles size={15} className="text-accent" /> {t("hero_kicker")}
          </motion.div>
          <motion.h1 initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.1, ease: [0.22, 1, 0.36, 1] }} className="mt-6 text-5xl font-black leading-[1.08] tracking-tight sm:text-7xl">
            {t("hero_title_1")}
            <br />
            <span className="text-gradient-accent">{t("hero_title_2")}</span>
          </motion.h1>
          <motion.p initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.2 }} className="mt-6 text-base leading-8 text-dim sm:text-lg">
            {t("hero_sub")}
          </motion.p>
          <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.3 }} className="mt-9 flex flex-wrap gap-3">
            <Link href={start} className="btn btn-primary h-14 px-7 text-base">
              {signedIn ? t("nav_app") : t("hero_cta")} <Forward size={19} />
            </Link>
            {!signedIn && (
              <Link href="/login" className="btn btn-ghost h-14 px-7 text-base">
                {t("hero_cta_secondary")}
              </Link>
            )}
          </motion.div>
          <p className="mt-5 text-sm text-faint">{t("hero_note")}</p>
        </div>
      </div>
    </section>
  );
}

/** Media query state; undefined until known on the client (so nothing heavy renders twice). */
function useMediaQuery(query: string): boolean | undefined {
  const [match, setMatch] = useState<boolean | undefined>(undefined);
  useEffect(() => {
    const m = window.matchMedia(query);
    setMatch(m.matches);
    const on = () => setMatch(m.matches);
    m.addEventListener("change", on);
    return () => m.removeEventListener("change", on);
  }, [query]);
  return match;
}

function Counter({ to, suffix = "" }: { to: number; suffix?: string }) {
  const { lang } = useI18n();
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true });
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!inView) return;
    const start = performance.now();
    let raf = 0;
    const step = (now: number) => {
      const p = Math.min(1, (now - start) / 1400);
      setN(Math.round(to * (1 - Math.pow(1 - p, 3))));
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [inView, to]);
  return (
    <span ref={ref} className="tnum">
      {digits(lang, n.toLocaleString("en-US"))}
      {suffix}
    </span>
  );
}

function Stats() {
  const { t } = useI18n();
  const items: [number, SiteKey, string][] = [
    [322, "stat_lessons", ""],
    [20, "stat_levels", ""],
    [700, "stat_puzzles", "+"],
    [3, "stat_languages", ""],
  ];
  return (
    <section className="border-y border-line bg-bg-2">
      <div className="mx-auto grid max-w-7xl grid-cols-2 gap-y-8 px-6 py-10 md:grid-cols-4">
        {items.map(([n, key, suffix]) => (
          <motion.div key={key} {...reveal} className="text-center">
            <div className="text-4xl font-black text-gradient-accent sm:text-5xl">
              <Counter to={n} suffix={suffix} />
            </div>
            <div className="mt-2 text-sm font-semibold text-dim">{t(key)}</div>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

function CoachShowcase() {
  const { t, a, lang } = useI18n();
  const s = COACH_SAMPLES[lang];
  const [which, setWhich] = useState<"blunder" | "good">("blunder");
  const sample = s[which];
  return (
    <section className="mx-auto grid max-w-7xl items-center gap-12 px-6 py-24 lg:grid-cols-2">
      <motion.div {...reveal}>
        <div className="inline-flex items-center gap-2 rounded-full bg-surface-2 px-3 py-1 text-xs font-bold text-accent">
          <Brain size={14} /> {t("f_coach_t")}
        </div>
        <h2 className="mt-4 text-3xl font-black tracking-tight sm:text-5xl">{t("features_title")}</h2>
        <p className="mt-5 text-lg leading-8 text-dim">{t("f_coach_d")}</p>
        <div className="mt-6 flex gap-2">
          {(["blunder", "good"] as const).map((k) => (
            <button key={k} className="chip" aria-pressed={which === k} onClick={() => setWhich(k)}>
              <span className="ltr font-bold">{s[k].san}</span>
            </button>
          ))}
        </div>
      </motion.div>
      <motion.div {...reveal} className="relative">
        <div className="absolute -inset-6 rounded-[2rem] bg-[radial-gradient(circle_at_30%_20%,var(--glow),transparent_60%)] blur-xl" />
        <div className="glass relative rounded-[1.6rem] p-6 shadow-[var(--shadow)]">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-lg font-black text-[#101014]" style={{ background: QUALITY_COLOR[sample.quality] ?? "#a7c48f" }}>
              {sample.quality === "blunder" ? "??" : sample.quality === "book" ? "📖" : "!"}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 text-xs font-semibold text-dim">
                <Volume2 size={14} className="text-accent" /> {a("coach_title")}
              </div>
              <div className="mt-1 flex flex-wrap items-baseline gap-x-3">
                <span className="ltr text-2xl font-black">{sample.san}</span>
                <span className="text-lg font-bold" style={{ color: QUALITY_COLOR[sample.quality] }}>
                  {a(`quality_${sample.quality}`)}
                </span>
              </div>
              <motion.p key={`${lang}-${which}`} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.5 }} className="mt-3 text-[0.97rem] leading-8">
                {sample.message.slice(0, 330)}
                {sample.message.length > 330 ? "…" : ""}
              </motion.p>
              {"best" in sample && sample.best && (
                <div className="mt-4 inline-flex items-center gap-2 rounded-xl bg-surface-2 px-3 py-1.5 text-sm font-bold text-best">
                  <Sparkles size={15} /> {a("coach_best_was", sample.best)}
                </div>
              )}
            </div>
          </div>
        </div>
      </motion.div>
    </section>
  );
}

function Features() {
  const { t } = useI18n();
  const items: [typeof Brain, SiteKey, SiteKey][] = [
    [Brain, "f_coach_t", "f_coach_d"],
    [Bot, "f_bots_t", "f_bots_d"],
    [BookOpen, "f_lessons_t", "f_lessons_d"],
    [Puzzle, "f_puzzles_t", "f_puzzles_d"],
    [Users, "f_friends_t", "f_friends_d"],
    [Cpu, "f_analysis_t", "f_analysis_d"],
  ];
  return (
    <section className="relative px-6 py-24">
      <div className="mx-auto max-w-7xl">
        <motion.div {...reveal} className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-bold text-accent">{t("brand_name")}</p>
          <h2 className="mt-3 text-3xl font-black tracking-tight sm:text-5xl">{t("features_sub")}</h2>
        </motion.div>
        <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {items.map(([Icon, title, desc], i) => (
            <motion.div
              key={title}
              {...reveal}
              transition={{ ...reveal.transition, delay: i * 0.06 }}
              className="group card relative overflow-hidden p-7 transition hover:-translate-y-1 hover:border-line-strong"
            >
              <div className="absolute -end-10 -top-10 h-32 w-32 rounded-full bg-[radial-gradient(circle,var(--glow),transparent_70%)] opacity-0 transition group-hover:opacity-100" />
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl" style={{ background: "var(--grad-accent)" }}>
                <Icon size={22} className="text-white" />
              </div>
              <h3 className="mt-5 text-xl font-extrabold">{t(title)}</h3>
              <p className="mt-2 leading-7 text-dim">{t(desc)}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}

const ELOS = [250, 400, 550, 700, 850, 1000, 1150, 1300, 1450, 1600, 1750, 1900, 2050, 2200, 2350, 2500, 2650, 2800, 3000, 3200];
const TIER_COLORS = ["#6CCB5F", "#3FB5A6", "#4F9CFF", "#8C6CFF", "#FF9F45", "#FF6B6B", "#E3202B", "#FFC53D"];
function tierIndex(elo: number) {
  return elo < 700 ? 0 : elo < 1150 ? 1 : elo < 1600 ? 2 : elo < 2050 ? 3 : elo < 2500 ? 4 : elo < 2800 ? 5 : elo < 3100 ? 6 : 7;
}

function Levels() {
  const { t, lang } = useI18n();
  return (
    <section className="overflow-hidden border-y border-line bg-bg-2 py-20">
      <motion.div {...reveal} className="mx-auto max-w-3xl px-6 text-center">
        <h2 className="text-3xl font-black tracking-tight sm:text-4xl">{t("f_bots_t")}</h2>
        <p className="mt-4 text-dim">{t("f_bots_d")}</p>
      </motion.div>
      <div className="relative mt-12">
        <div dir="ltr" className="flex w-max animate-[marquee_40s_linear_infinite] gap-4 px-4 hover:[animation-play-state:paused]">
          {[...ELOS, ...ELOS].map((elo, i) => {
            const c = TIER_COLORS[tierIndex(elo)];
            return (
              <div key={i} className="card flex w-40 shrink-0 flex-col items-center gap-2 p-5">
                <div className="flex h-12 w-12 items-center justify-center rounded-full text-lg font-black text-[#101014]" style={{ background: c }}>
                  {(i % 20) + 1}
                </div>
                <div className="ltr font-bold">Zorix {(i % 20) + 1}</div>
                <div className="tnum text-sm text-dim">{digits(lang, elo)}</div>
              </div>
            );
          })}
        </div>
      </div>
      <style>{`@keyframes marquee { from { transform: translateX(0) } to { transform: translateX(-50%) } }`}</style>
    </section>
  );
}

function How() {
  const { t, lang } = useI18n();
  const steps: [typeof UserPlus, SiteKey, SiteKey][] = [
    [UserPlus, "how_1_t", "how_1_d"],
    [BookOpen, "how_2_t", "how_2_d"],
    [Users, "how_3_t", "how_3_d"],
  ];
  return (
    <section className="px-6 py-24">
      <div className="mx-auto max-w-6xl">
        <motion.h2 {...reveal} className="text-center text-3xl font-black tracking-tight sm:text-5xl">
          {t("how_title")}
        </motion.h2>
        <div className="mt-14 grid gap-6 md:grid-cols-3">
          {steps.map(([Icon, title, desc], i) => (
            <motion.div key={title} {...reveal} transition={{ ...reveal.transition, delay: i * 0.1 }} className="relative">
              <div className="font-brand text-6xl font-black text-gradient-accent opacity-90">{digits(lang, `0${i + 1}`)}</div>
              <div className="mt-4 flex items-center gap-3">
                <Icon size={22} className="text-accent" />
                <h3 className="text-xl font-extrabold">{t(title)}</h3>
              </div>
              <p className="mt-3 leading-7 text-dim">{t(desc)}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Languages() {
  const { t } = useI18n();
  const samples = [
    ["فارسی", "سارا، این یک اشتباه فاحش بود."],
    ["کوردی", "ئۆی سارا، هەڵەیەکی گەورە!"],
    ["English", "Oh no, Sara — a blunder."],
  ];
  return (
    <section className="px-6 pb-24">
      <div className="card relative mx-auto max-w-6xl overflow-hidden p-8 sm:p-12">
        <div className="aurora absolute inset-0 opacity-60" />
        <div className="relative grid items-center gap-10 md:grid-cols-[1fr_1.2fr]">
          <motion.div {...reveal}>
            <Globe2 size={34} className="text-accent" />
            <h2 className="mt-4 text-3xl font-black tracking-tight sm:text-4xl">{t("langs_title")}</h2>
            <p className="mt-4 leading-8 text-dim">{t("langs_sub")}</p>
          </motion.div>
          <div className="grid gap-3">
            {samples.map(([name, text], i) => (
              <motion.div key={name} {...reveal} transition={{ ...reveal.transition, delay: i * 0.1 }} className="glass flex items-center justify-between gap-4 rounded-2xl px-5 py-4">
                <span className="text-sm font-bold text-accent">{name}</span>
                <span className="text-base font-semibold" dir={name === "English" ? "ltr" : "rtl"}>
                  {text}
                </span>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
