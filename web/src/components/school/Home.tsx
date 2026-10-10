"use client";

import { BookOpen, Bot, Cpu, Puzzle, Trophy, Users } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";
import { digits } from "@/i18n/site";
import { useI18n } from "../site/Providers";
import { useChannel } from "./ZorixProvider";

/** The school's front page: where to continue, the player's numbers, and every section. */
export function Home({ username }: { username: string }) {
  const { a, t, lang } = useI18n();
  const profile = useChannel("profile");
  const courses = useChannel("courses");
  const play = useChannel("play");
  const next = (() => {
    for (const c of courses ?? []) for (const ch of c.chapters) for (const l of ch.lessons) if (!profile?.lessonStars?.[l.id]) return { c, l };
    return null;
  })();
  const name = profile?.name || username;
  const tiles = [
    { href: "/app/analysis", icon: Cpu, title: t("tab_analysis"), text: a("help_analysis_title") },
    { href: "/app/play", icon: Bot, title: a("play_title"), text: a("play_subtitle_plain") },
    { href: "/app/friends", icon: Users, title: t("friends_title"), text: t("friends_sub") },
    { href: "/app/learn", icon: BookOpen, title: a("learn_title"), text: a("learn_subtitle", 322) },
    { href: "/app/puzzles", icon: Puzzle, title: a("tab_puzzles"), text: a("help_puzzles_title") },
  ];
  return (
    <div className="mx-auto max-w-6xl px-3 py-6 sm:px-6">
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="card relative overflow-hidden p-6 sm:p-8">
        <div className="aurora absolute inset-0 opacity-70" />
        <div className="relative">
          <p className="text-sm font-bold text-accent">{t("brand_name")}</p>
          <h1 className="mt-1 text-3xl font-black sm:text-4xl">{name}</h1>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[
              [digits(lang, profile?.lessonsCompleted ?? 0), t("stat_lessons")],
              [digits(lang, profile?.puzzlesSolved ?? 0), a("tab_puzzles")],
              [digits(lang, profile?.gamesPlayed ?? 0), t("admin_col_games")],
              [digits(lang, profile?.xp ?? 0), "XP"],
            ].map(([v, l]) => (
              <div key={l} className="glass rounded-2xl p-4">
                <div className="tnum text-2xl font-black">{v}</div>
                <div className="text-xs text-dim">{l}</div>
              </div>
            ))}
          </div>
          <div className="mt-6 flex flex-wrap gap-3">
            {play?.active && (
              <Link href="/app/play" className="btn btn-primary">
                <Trophy size={17} /> {a("play_resume")}
              </Link>
            )}
            {next && (
              <Link href={`/app/learn/${next.c.id}/${next.l.id}`} className={`btn ${play?.active ? "btn-ghost" : "btn-primary"}`}>
                <BookOpen size={17} /> {next.l.title}
              </Link>
            )}
          </div>
        </div>
      </motion.div>
      <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tiles.map(({ href, icon: Icon, title, text }, i) => (
          <motion.div key={href} initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 * i }}>
            <Link href={href} className="card group flex h-full items-start gap-4 p-5 transition hover:-translate-y-0.5 hover:border-line-strong">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl text-white transition group-hover:scale-105" style={{ background: "var(--grad-accent)" }}>
                <Icon size={22} />
              </div>
              <div>
                <h3 className="text-lg font-extrabold">{title}</h3>
                <p className="mt-1 text-sm leading-6 text-dim">{text}</p>
              </div>
            </Link>
          </motion.div>
        ))}
      </div>
      <p className="mt-6 text-center text-xs text-faint">{t("speed_tip")}</p>
    </div>
  );
}
