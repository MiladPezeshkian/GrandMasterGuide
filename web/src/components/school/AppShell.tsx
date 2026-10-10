"use client";

import { BookOpen, Bot, Cpu, Home, Puzzle, Settings, Shield, Users } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { SiteKey } from "@/i18n/site";
import { Logo } from "../site/Brand";
import { LangSwitcher, ThemeToggle } from "../site/Controls";
import { useI18n } from "../site/Providers";
import { SignOutButton } from "../site/SignOut";
import { Onboarding } from "./Onboarding";
import { useChannel } from "./ZorixProvider";

const NAV: { href: string; key: SiteKey | "home"; icon: typeof Home }[] = [
  { href: "/app", key: "home", icon: Home },
  { href: "/app/analysis", key: "tab_analysis", icon: Cpu },
  { href: "/app/play", key: "tab_play", icon: Bot },
  { href: "/app/friends", key: "tab_friends", icon: Users },
  { href: "/app/learn", key: "tab_learn", icon: BookOpen },
  { href: "/app/puzzles", key: "tab_puzzles", icon: Puzzle },
  { href: "/app/settings", key: "tab_settings", icon: Settings },
];

export function AppShell({ user, children }: { user: { username: string; email: string; admin: boolean }; children: React.ReactNode }) {
  const { t } = useI18n();
  const path = usePathname();
  const profile = useChannel("profile");
  const engine = useChannel("board")?.engine;
  const active = (href: string) => (href === "/app" ? path === "/app" : path.startsWith(href));
  const label = (key: SiteKey | "home") => (key === "home" ? t("nav_home") : t(key));

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[17rem_1fr]">
      {/* Sidebar (desktop) */}
      <aside className="sticky top-0 hidden h-dvh flex-col border-e border-line bg-bg-2 p-4 lg:flex">
        <div className="px-2 pb-6 pt-2">
          <Logo href="/app" />
        </div>
        <nav className="flex flex-1 flex-col gap-1">
          {NAV.map(({ href, key, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-[0.95rem] font-semibold transition ${active(href) ? "bg-accent text-on-accent shadow-[0_8px_24px_-10px_var(--glow)]" : "text-dim hover:bg-surface-2 hover:text-text"}`}
            >
              <Icon size={19} /> {label(key)}
            </Link>
          ))}
          {user.admin && (
            <Link href="/admin" className="mt-2 flex items-center gap-3 rounded-xl px-3 py-2.5 text-[0.95rem] font-semibold text-dim hover:bg-surface-2 hover:text-text">
              <Shield size={19} /> {t("nav_admin")}
            </Link>
          )}
        </nav>
        <div className="card mt-4 p-3">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-full text-sm font-black text-white" style={{ background: "var(--grad-accent)" }}>
              {(profile?.name || user.username).slice(0, 1).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-bold">{profile?.name || user.username}</div>
              <div className="ltr truncate text-xs text-faint">@{user.username}</div>
            </div>
            <SignOutButton label={t("nav_logout")} iconOnly />
          </div>
          <div className="mt-2 flex items-center justify-between">
            <LangSwitcher />
            <ThemeToggle />
          </div>
        </div>
        <EngineStatusLine engine={engine} />
      </aside>

      {/* Top bar (phones) */}
      <header className="sticky top-0 z-30 flex h-14 items-center gap-1 border-b border-line bg-[color-mix(in_srgb,var(--bg)_88%,transparent)] px-3 backdrop-blur-xl lg:hidden">
        <Logo href="/app" compact />
        <div className="flex-1" />
        {user.admin && (
          <Link href="/admin" className="btn btn-quiet" aria-label={t("nav_admin")}>
            <Shield size={18} />
          </Link>
        )}
        <LangSwitcher />
        <ThemeToggle />
        <SignOutButton label={t("nav_logout")} iconOnly />
      </header>

      <main className="min-w-0 pb-24 lg:pb-0">{children}</main>

      {/* Bottom bar (phones) */}
      <nav className="fixed inset-x-0 bottom-0 z-30 grid grid-cols-7 border-t border-line bg-[color-mix(in_srgb,var(--bg)_92%,transparent)] pb-[env(safe-area-inset-bottom)] backdrop-blur-xl lg:hidden">
        {NAV.map(({ href, key, icon: Icon }) => (
          <Link key={href} href={href} className={`flex flex-col items-center gap-0.5 py-2 text-[0.62rem] font-semibold ${active(href) ? "text-accent" : "text-faint"}`}>
            <Icon size={20} />
            <span className="max-w-full truncate px-0.5">{label(key)}</span>
          </Link>
        ))}
      </nav>

      {profile && !profile.onboarded && <Onboarding defaultName={user.username} />}
    </div>
  );
}

function EngineStatusLine({ engine }: { engine?: { kind: string; name?: string } }) {
  const { t } = useI18n();
  if (!engine) return <p className="mt-3 px-2 text-xs text-faint">{t("loading_school")}</p>;
  return (
    <p className="mt-3 flex items-center gap-2 px-2 text-xs text-faint">
      <span className={`h-2 w-2 rounded-full ${engine.kind === "ready" ? "bg-best" : engine.kind === "failed" ? "bg-danger" : "pulse-ring bg-warn"}`} />
      {engine.kind === "ready" ? <span className="ltr">{engine.name}</span> : t("loading_engine")}
    </p>
  );
}
