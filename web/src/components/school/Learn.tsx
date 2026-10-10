"use client";

import { ArrowLeft, ArrowRight, BookOpen, ChevronLeft, ChevronRight, Lightbulb, Play, RotateCcw, Trophy } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { digits } from "@/i18n/site";
import { useI18n } from "../site/Providers";
import { Loading } from "./Analysis";
import type { Course, LessonState } from "./types";
import { CoachBubble, HelpButton, PageHeader, SchoolBoard, Spinner, Stars, ViewToggle } from "./ui";
import { useChannel, useZorix } from "./ZorixProvider";

const COURSE_ART = ["♟", "♞", "♛", "♝", "♚", "♜", "♔"];

export function LearnHub() {
  const { a, lang } = useI18n();
  const courses = useChannel("courses");
  const profile = useChannel("profile");
  if (!courses?.length) return <Loading />;
  const next = (() => {
    for (const c of courses) for (const ch of c.chapters) for (const l of ch.lessons) if (!profile?.lessonStars?.[l.id]) return { c, l };
    return null;
  })();
  return (
    <div className="mx-auto max-w-6xl px-3 py-5 sm:px-6">
      <PageHeader title={a("learn_title")} subtitle={a("learn_subtitle", courses.reduce((n, c) => n + c.total, 0))} actions={<HelpButton topic="learn" />} />
      {next && (
        <Link href={`/app/learn/${next.c.id}/${next.l.id}`} className="card group relative mb-6 flex items-center gap-4 overflow-hidden p-5 transition hover:border-accent">
          <div className="absolute inset-0 opacity-60" style={{ background: "radial-gradient(circle at 90% 50%, var(--glow), transparent 60%)" }} />
          <div className="relative flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl text-2xl text-white" style={{ background: "var(--grad-accent)" }}>
            <Play size={24} />
          </div>
          <div className="relative min-w-0 flex-1">
            <div className="text-xs font-bold text-accent">{next.c.title}</div>
            <div className="truncate text-lg font-extrabold">{next.l.title}</div>
            <div className="text-xs text-faint">{a("lesson_meta", next.l.exercises, next.l.level)}</div>
          </div>
        </Link>
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {courses.map((c, i) => (
          <Link key={c.id} href={`/app/learn/${c.id}`} className="card group relative overflow-hidden p-5 transition hover:-translate-y-0.5 hover:border-line-strong">
            <div className="absolute -end-4 -top-6 text-[7rem] leading-none text-[color-mix(in_srgb,var(--accent)_14%,transparent)] transition group-hover:scale-110">{COURSE_ART[i % COURSE_ART.length]}</div>
            <div className="relative">
              <h3 className="text-xl font-black">{c.title}</h3>
              <p className="mt-1 line-clamp-2 min-h-12 text-sm leading-6 text-dim">{c.description}</p>
              <div className="mt-4 h-2 overflow-hidden rounded-full bg-surface-3">
                <div className="h-full rounded-full" style={{ width: `${(c.done / Math.max(1, c.total)) * 100}%`, background: "var(--grad-accent)" }} />
              </div>
              <div className="mt-2 flex items-center justify-between text-xs text-dim">
                <span>{a("learn_progress", digits(lang, c.done), digits(lang, c.total))}</span>
                <span className="flex items-center gap-1">
                  <Stars n={1} max={1} size={12} /> {digits(lang, c.stars)}
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function CourseView({ courseId }: { courseId: string }) {
  const { a, lang, dir } = useI18n();
  const courses = useChannel("courses");
  const profile = useChannel("profile");
  if (!courses?.length) return <Loading />;
  const c = courses.find((x) => x.id === courseId);
  if (!c) return <Loading />;
  const Back = dir === "rtl" ? ArrowRight : ArrowLeft;
  let number = 0;
  return (
    <div className="mx-auto max-w-4xl px-3 py-5 sm:px-6">
      <Link href="/app/learn" className="btn btn-quiet -ms-2 mb-2">
        <Back size={16} /> {a("learn_title")}
      </Link>
      <PageHeader title={c.title} subtitle={c.description} actions={<HelpButton topic="learn" />} />
      <div className="space-y-6">
        {c.chapters.map((ch) => (
          <section key={ch.id}>
            <h2 className="mb-3 text-sm font-bold text-accent">{ch.title}</h2>
            <div className="grid gap-2">
              {ch.lessons.map((l) => {
                number++;
                const stars = profile?.lessonStars?.[l.id] ?? 0;
                return (
                  <Link key={l.id} href={`/app/learn/${c.id}/${l.id}`} className="card flex items-center gap-4 p-4 transition hover:border-line-strong">
                    <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-black ${stars ? "bg-best text-[#101014]" : "bg-surface-3"}`}>{digits(lang, number)}</span>
                    <div className="min-w-0 flex-1">
                      <div className="font-bold">{l.title}</div>
                      <div className="text-xs text-faint">{a("lesson_meta", l.exercises, l.level)}</div>
                    </div>
                    <Stars n={stars} />
                  </Link>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

function nextLessonOf(courses: Course[] | undefined, courseId: string, lessonId: string) {
  const c = courses?.find((x) => x.id === courseId);
  const all = c?.chapters.flatMap((ch) => ch.lessons) ?? [];
  const i = all.findIndex((l) => l.id === lessonId);
  return i >= 0 ? all[i + 1] : undefined;
}

export function LessonPlayer({ courseId, lessonId }: { courseId: string; lessonId: string }) {
  const { a } = useI18n();
  const { core } = useZorix();
  const router = useRouter();
  const courses = useChannel("courses");
  const lesson = useChannel("lesson");
  const result = useChannel("lessonResult");
  const [started, setStarted] = useState(false);
  const info = courses?.find((c) => c.id === courseId)?.chapters.flatMap((ch) => ch.lessons).find((l) => l.id === lessonId);

  // Start the session once the content is loaded; close it when leaving.
  useEffect(() => {
    if (!core || !courses?.length) return;
    core.lessonStart(courseId, lessonId);
    setStarted(false);
    return () => core.lessonClose();
  }, [core, courses?.length, courseId, lessonId]);

  if (!core || !courses?.length || !info) return <Loading />;
  const next = nextLessonOf(courses, courseId, lessonId);

  if (result && result.lessonId === lessonId) {
    return <ResultPage stars={result.stars} mistakes={result.mistakes} title={info.title} onNext={next ? () => router.push(`/app/learn/${courseId}/${next.id}`) : undefined} courseId={courseId} />;
  }
  if (!started) return <IntroPage title={info.title} intro={info.intro} courseId={courseId} onStart={() => setStarted(true)} />;
  if (!lesson || lesson.lessonId !== lessonId) return <Loading />;
  return <StepPage s={lesson} courseId={courseId} title={info.title} />;
}

function IntroPage({ title, intro, courseId, onStart }: { title: string; intro: string; courseId: string; onStart: () => void }) {
  const { a, dir } = useI18n();
  const { core } = useZorix();
  useEffect(() => {
    const id = setTimeout(() => core?.speakDisplay(intro), 300);
    return () => clearTimeout(id);
  }, [core, intro]);
  const Back = dir === "rtl" ? ArrowRight : ArrowLeft;
  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <Link href={`/app/learn/${courseId}`} className="btn btn-quiet -ms-2 mb-4">
        <Back size={16} /> {a("lesson_back_to_course")}
      </Link>
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
        <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl text-white" style={{ background: "var(--grad-accent)" }}>
          <BookOpen size={30} />
        </div>
        <h1 className="text-3xl font-black">{title}</h1>
        <div className="mt-6">
          <CoachBubble message={intro} waitForVoice onSpeak={() => core?.speakDisplay(intro)} />
        </div>
        <button className="btn btn-primary mt-8 h-14 w-full text-lg" onClick={onStart}>
          <Play size={20} /> {a("lesson_start")}
        </button>
      </motion.div>
    </div>
  );
}

function StepPage({ s, courseId, title }: { s: LessonState; courseId: string; title: string }) {
  const { a, dir } = useI18n();
  const { core } = useZorix();
  const lastSpeech = useRef<string | null>(null);

  // The coach reads explanation cards and its comments on each solved move (as in the app).
  useEffect(() => {
    if (s.type !== "theory") return;
    const id = setTimeout(() => core?.speakDisplay(s.prompt), 300);
    return () => clearTimeout(id);
  }, [core, s.index, s.type, s.prompt]);
  useEffect(() => {
    if (s.speech && s.speech !== lastSpeech.current) core?.speak(s.speech);
    lastSpeech.current = s.speech;
  }, [core, s.speech]);

  const highlights: Record<string, string> = {};
  s.marks.forEach((m) => (highlights[m] = s.type === "squares" && s.status === "wrong" ? "rgba(255,75,85,0.55)" : "rgba(79,156,255,0.45)"));
  const pos = s.position;
  const boardInteractive = s.interactive && !["squares", "theory", "quiz"].includes(s.type);
  const puzzleLike = ["puzzle", "mate", "best", "line", "goal"].includes(s.type);
  const [Prev, Next] = dir === "rtl" ? [ChevronRight, ArrowLeft] : [ChevronLeft, ArrowRight];
  const Back = dir === "rtl" ? ArrowRight : ArrowLeft;

  return (
    <div className="mx-auto max-w-6xl px-3 py-4 sm:px-6">
      <div className="mb-3 flex items-center gap-2">
        <Link href={`/app/learn/${courseId}`} className="btn btn-quiet -ms-2" aria-label={a("lesson_back_to_course")}>
          <Back size={18} />
        </Link>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-bold">{title}</div>
          <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-3">
            <motion.div className="h-full rounded-full" style={{ background: "var(--grad-accent)" }} animate={{ width: `${((s.index + (s.status === "solved" ? 1 : 0)) / s.count) * 100}%` }} />
          </div>
        </div>
        <ViewToggle />
        <HelpButton topic="lesson" />
      </div>

      <PromptBar s={s} />

      <div className={`mt-4 grid gap-5 ${pos ? "lg:grid-cols-[minmax(0,1fr)_23rem]" : ""}`}>
        {pos && (
          <div className="mx-auto w-full max-w-[min(100%,78dvh)]">
            <SchoolBoard
              fen={pos.fen}
              orientation={s.flipped ? "black" : "white"}
              movable={boardInteractive ? pos.turn : "none"}
              lastMove={s.lastMove}
              check={pos.check}
              stars={s.stars}
              highlights={highlights}
              hint={s.hintSquare}
              arrows={s.arrows.map((m) => ({ from: m.slice(0, 2), to: m.slice(2, 4), color: "rgba(47,210,124,0.85)" }))}
              coordinates={s.type === "squares" ? true : undefined}
              onMove={(f, t) => core?.lessonMove(f, t)}
              onSquare={s.type === "squares" ? (sq) => core?.lessonSquare(sq) : undefined}
              promotion={s.promotion}
              onPromote={(p) => core?.lessonPromote(p ?? "")}
            />
          </div>
        )}
        <div className="space-y-4">
          {s.type === "theory" && <CoachBubble message={s.prompt} waitForVoice onSpeak={() => core?.speakDisplay(s.prompt)} />}
          {s.type === "quiz" && <Quiz s={s} />}
          {s.type !== "theory" && s.type !== "quiz" && s.message && <CoachBubble message={s.message} waitForVoice={!!s.speech} onSpeak={() => (s.speech ? core?.speak(s.speech) : core?.speakDisplay(s.message!))} tone={s.status === "solved" ? "good" : s.status === "wrong" ? "bad" : "default"} />}
          <div className="flex flex-wrap items-center gap-2">
            {s.index > 0 && (
              <button className="btn btn-ghost h-11 w-11 !px-0" aria-label={a("action_back")} onClick={() => core?.lessonPrevious()}>
                <Prev size={18} />
              </button>
            )}
            {puzzleLike && s.status !== "solved" && (
              <>
                <button className="btn btn-ghost flex-1" onClick={() => core?.lessonHint()}>
                  <Lightbulb size={16} /> {a("puzzle_hint")}
                </button>
                <button className="btn btn-ghost flex-1" onClick={() => core?.lessonSolution()}>
                  <Play size={16} /> {a("puzzle_solution")}
                </button>
              </>
            )}
            {["play", "stars", "capture"].includes(s.type) && s.status !== "solved" && (
              <button className="btn btn-ghost flex-1" onClick={() => core?.lessonRetry()}>
                <RotateCcw size={16} /> {a("puzzle_retry")}
              </button>
            )}
            {s.status === "solved" && (
              <button className="btn btn-primary flex-[1.4]" onClick={() => core?.lessonNext()}>
                {a("puzzle_next")} <Next size={18} />
              </button>
            )}
            {s.status === "failed" && (
              <button className="btn btn-primary flex-[1.4]" onClick={() => core?.lessonRetry()}>
                <RotateCcw size={16} /> {a("puzzle_retry")}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/** The task stays on screen; the verdict on the last answer shows under it (as in the app since 2.5). */
function PromptBar({ s }: { s: LessonState }) {
  const { a, lang } = useI18n();
  let text: string | null = null;
  if (s.type === "squares") text = s.target ? a("lesson_find_square", s.target) : a("lesson_squares_done", s.correct, s.squaresCount ?? 0);
  else if (s.type !== "theory" && s.type !== "quiz") text = s.prompt || null;
  const verdict =
    s.status === "wrong" ? { t: a("puzzle_wrong"), c: "var(--danger)" } : s.status === "solved" && s.type !== "theory" ? { t: a("puzzle_solved"), c: "var(--best)" } : s.status === "failed" ? { t: a("lesson_failed"), c: "var(--danger)" } : null;
  let extra: React.ReactNode = null;
  if ((s.type === "stars" || s.type === "capture") && s.par != null) extra = a("lesson_moves_par", digits(lang, s.moves), digits(lang, s.par));
  if (s.type === "squares") extra = a("lesson_round", digits(lang, Math.min(s.round + 1, s.squaresCount ?? 0)), digits(lang, s.squaresCount ?? 0));
  if (s.type === "play" && s.busy) extra = (<span className="flex items-center gap-2"><Spinner size={13} /> {a("zorix_thinking")}</span>);
  if (s.type === "mate" && s.busy) extra = (<span className="flex items-center gap-2"><Spinner size={13} /> {a("lesson_checking")}</span>);
  if (!text && !verdict && !extra) return null;
  return (
    <div className="flex flex-col items-center gap-1.5 text-center">
      {s.type === "squares" && s.prompt && <p className="text-sm text-dim">{s.prompt}</p>}
      <AnimatePresence mode="wait">
        {text && (
          <motion.p key={text} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className={`font-bold leading-8 ${s.type === "squares" && s.target ? "ltr text-3xl" : "text-lg"}`}>
            {text}
          </motion.p>
        )}
      </AnimatePresence>
      <AnimatePresence mode="wait">
        {verdict && (
          <motion.span key={verdict.t + s.round} initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }} className="rounded-full px-3 py-1 text-sm font-bold" style={{ color: verdict.c, background: `color-mix(in srgb, ${verdict.c} 14%, transparent)` }}>
            {verdict.t}
          </motion.span>
        )}
      </AnimatePresence>
      {extra && <div className="text-xs font-semibold text-dim">{extra}</div>}
    </div>
  );
}

function Quiz({ s }: { s: LessonState }) {
  const { core } = useZorix();
  return (
    <div className="space-y-2">
      <p className="text-lg font-bold leading-8">{s.prompt}</p>
      {(s.options ?? []).map((opt, i) => {
        const chosen = s.chosen === i;
        const correct = s.status === "solved" && i === s.answer;
        const wrong = chosen && s.status === "wrong";
        return (
          <button
            key={i}
            disabled={s.status === "solved"}
            onClick={() => core?.lessonOption(i)}
            className={`w-full rounded-2xl border-2 p-4 text-start font-semibold transition ${correct ? "border-best bg-best/15" : wrong ? "border-danger bg-danger/10" : "border-transparent bg-surface-2 hover:border-line-strong"}`}
          >
            {opt}
          </button>
        );
      })}
      {s.message && <CoachBubble message={s.message} tone="good" />}
    </div>
  );
}

function ResultPage({ stars, mistakes, title, onNext, courseId }: { stars: number; mistakes: number; title: string; onNext?: () => void; courseId: string }) {
  const { a, lang } = useI18n();
  return (
    <div className="flex min-h-[70dvh] items-center justify-center px-6">
      <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="w-full max-w-sm text-center">
        <Trophy size={76} className="mx-auto text-[#FFC53D] drop-shadow-[0_0_24px_rgba(255,197,61,0.5)]" />
        <h1 className="mt-4 text-3xl font-black">{a("lesson_done_title")}</h1>
        <p className="mt-1 font-semibold text-dim">{title}</p>
        <div className="mt-5 flex justify-center">
          <Stars n={stars} size={40} />
        </div>
        <p className="mt-3 text-sm text-dim">{mistakes === 0 ? a("lesson_perfect") : a("lesson_mistakes", digits(lang, mistakes))}</p>
        <div className="mt-8 grid gap-2">
          {onNext && (
            <button className="btn btn-primary h-13" onClick={onNext}>
              {a("lesson_next")}
            </button>
          )}
          <Link href={`/app/learn/${courseId}`} className="btn btn-ghost">
            {a("lesson_back_to_course")}
          </Link>
        </div>
      </motion.div>
    </div>
  );
}

export function useCourseTitle(courseId: string) {
  const courses = useChannel("courses");
  return useMemo(() => courses?.find((c) => c.id === courseId)?.title, [courses, courseId]);
}
