"use client";

import { ArrowLeft, Gamepad2, LayoutDashboard, LogIn, Settings2, Users } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { SiteKey } from "@/i18n/site";
import { Logo } from "../site/Brand";
import { LangSwitcher, ThemeToggle } from "../site/Controls";
import { useI18n } from "../site/Providers";
import { SignOutButton } from "../site/SignOut";

const NAV: { href: string; key: SiteKey; icon: typeof Users }[] = [
  { href: "/admin", key: "admin_overview", icon: LayoutDashboard },
  { href: "/admin/users", key: "admin_users", icon: Users },
  { href: "/admin/games", key: "admin_games", icon: Gamepad2 },
  { href: "/admin/logins", key: "admin_logins", icon: LogIn },
  { href: "/admin/settings", key: "admin_settings", icon: Settings2 },
];

export function AdminShell({ email, children }: { email: string; children: React.ReactNode }) {
  const { t } = useI18n();
  const path = usePathname();
  const active = (href: string) => (href === "/admin" ? path === "/admin" : path.startsWith(href));
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr]">
      <aside className="border-b border-line bg-bg-2 p-4 lg:sticky lg:top-0 lg:h-dvh lg:border-b-0 lg:border-e">
        <div className="flex items-center gap-2 px-1 pb-4">
          <Logo href="/admin" compact />
          <span className="rounded-full bg-accent px-2 py-0.5 text-[0.65rem] font-black text-on-accent">ADMIN</span>
          <div className="flex-1" />
          <div className="lg:hidden">
            <SignOutButton label={t("nav_logout")} iconOnly />
          </div>
        </div>
        <nav className="scrollbar-thin flex gap-1 overflow-x-auto lg:flex-col">
          {NAV.map(({ href, key, icon: Icon }) => (
            <Link key={href} href={href} className={`flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition ${active(href) ? "bg-accent text-on-accent" : "text-dim hover:bg-surface-2 hover:text-text"}`}>
              <Icon size={18} /> {t(key)}
            </Link>
          ))}
          <Link href="/app" className="flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold text-dim hover:bg-surface-2 hover:text-text lg:mt-4">
            <ArrowLeft size={18} className="rtl:rotate-180" /> {t("nav_app")}
          </Link>
        </nav>
        <div className="mt-4 hidden lg:block">
          <p className="ltr truncate px-1 text-xs text-faint">{email}</p>
          <div className="mt-2 flex items-center justify-between">
            <LangSwitcher />
            <ThemeToggle />
            <SignOutButton label={t("nav_logout")} iconOnly />
          </div>
        </div>
      </aside>
      <main className="min-w-0 px-4 py-6 sm:px-8">{children}</main>
    </div>
  );
}

export function StatTile({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className={`card p-4 ${accent ? "!border-accent/60" : ""}`}>
      <div className="text-xs font-semibold text-dim">{label}</div>
      <div className={`tnum mt-1 text-2xl font-black ${accent ? "text-accent" : ""}`}>{value}</div>
    </div>
  );
}
