import { BarChart } from "@/components/admin/BarChart";
import { StatTile } from "@/components/admin/AdminShell";
import { duration } from "@/components/admin/format";
import { overview } from "@/lib/admin";
import { serverT } from "@/i18n/server";
import { digits } from "@/i18n/site";

export default async function AdminOverview() {
  const { t, lang } = await serverT();
  const o = await overview();
  const d = (v: number) => digits(lang, v.toLocaleString("en-US"));
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-black sm:text-3xl">{t("admin_title")}</h1>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-6">
        <StatTile label={t("admin_total_users")} value={d(o.users.total)} accent />
        <StatTile label={t("admin_verified")} value={d(o.users.verified)} />
        <StatTile label={t("admin_online_now")} value={d(o.users.online)} accent />
        <StatTile label={t("admin_active_today")} value={d(o.active.today)} />
        <StatTile label={t("admin_active_week")} value={d(o.active.week)} />
        <StatTile label={t("admin_new_today")} value={d(o.users.newToday)} />
        <StatTile label={t("admin_games_total")} value={d(o.games.total)} />
        <StatTile label={t("admin_games_online")} value={d(o.games.online)} />
        <StatTile label={t("admin_games_bot")} value={d(o.games.bot)} />
        <StatTile label={t("admin_games_live")} value={d(o.games.live)} accent />
        <StatTile label={t("admin_time_total")} value={duration(lang, o.users.seconds)} />
        <StatTile label={t("admin_avg_time")} value={duration(lang, o.users.avgSeconds)} />
      </div>
      <div className="grid gap-4 xl:grid-cols-3">
        <BarChart title={t("admin_signups_30")} points={o.signups} />
        <BarChart title={t("admin_activity_30")} points={o.activeDays} />
        <BarChart title={t("admin_games_30")} points={o.gameDays} />
      </div>
    </div>
  );
}
