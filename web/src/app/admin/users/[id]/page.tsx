import Link from "next/link";
import { notFound } from "next/navigation";
import { BarChart } from "@/components/admin/BarChart";
import { StatTile } from "@/components/admin/AdminShell";
import { ago, dateTime, duration } from "@/components/admin/format";
import { GamesTable } from "@/components/admin/GamesTable";
import { UserActions } from "@/components/admin/UserActions";
import { device, userDetail } from "@/lib/admin";
import { serverT } from "@/i18n/server";
import { digits } from "@/i18n/site";

export default async function AdminUser({ params }: { params: Promise<{ id: string }> }) {
  const { t, lang } = await serverT();
  const d = await userDetail((await params).id);
  if (!d) notFound();
  const u = d.user, p = d.progress;
  const n = (v: number) => digits(lang, v);
  return (
    <div className="space-y-6">
      <Link href="/admin/users" className="btn btn-quiet -ms-2">
        {t("admin_back")}
      </Link>
      <div className="card flex flex-wrap items-center gap-4 p-5">
        <div className="flex h-14 w-14 items-center justify-center rounded-full text-xl font-black text-white" style={{ background: "var(--grad-accent)" }}>
          {u.username.slice(0, 1).toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="ltr text-2xl font-black">@{u.username}</h1>
          <p className="ltr text-sm text-dim">{u.email}</p>
          <p className="mt-1 text-xs text-faint">
            {t("admin_col_joined")}: {dateTime(lang, u.createdAt)} · {t("admin_col_last_seen")}: {u.lastSeenAt ? ago(lang, u.lastSeenAt) : t("admin_never")}
          </p>
        </div>
        {u.role !== "admin" && <UserActions id={u.id} banned={u.banned} verified={u.verified} />}
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-6">
        <StatTile label={t("admin_col_time")} value={duration(lang, u.secondsOnline)} accent />
        <StatTile label={t("admin_col_logins")} value={n(u.loginCount)} />
        <StatTile label={t("stat_lessons")} value={n(p.lessons)} />
        <StatTile label={t("tab_puzzles")} value={n(p.puzzlesSolved)} />
        <StatTile label={t("admin_games_bot")} value={`${n(p.wins)} / ${n(p.draws)} / ${n(p.losses)}`} />
        <StatTile label="XP" value={n(p.xp)} />
      </div>
      <BarChart title={t("admin_daily_time")} points={d.days} unit={lang === "en" ? "min" : lang === "fa" ? "دقیقه" : "خولەک"} />
      <div className="grid gap-4 xl:grid-cols-2">
        <section className="card overflow-x-auto">
          <h2 className="border-b border-line px-4 py-3 text-sm font-bold">{t("admin_recent_logins")}</h2>
          <table className="w-full text-sm">
            <tbody className="divide-y divide-line">
              {d.logins.map((l, i) => (
                <tr key={i}>
                  <td className="px-4 py-2.5 text-dim">{dateTime(lang, l.at)}</td>
                  <td className="px-4 py-2.5">{l.success ? <span className="text-best">{t("admin_success")}</span> : <span className="text-danger">{t("admin_failed")}</span>}</td>
                  <td className="ltr px-4 py-2.5 text-xs text-dim">{l.ip}</td>
                  <td className="px-4 py-2.5 text-xs text-dim">{device(l.userAgent)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        <section className="card overflow-x-auto">
          <h2 className="border-b border-line px-4 py-3 text-sm font-bold">{t("admin_recent_games")}</h2>
          <GamesTable games={d.games} />
        </section>
      </div>
    </div>
  );
}
