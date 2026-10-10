import Link from "next/link";
import { sql } from "drizzle-orm";
import { GamesTable } from "@/components/admin/GamesTable";
import { recentGames } from "@/lib/admin";
import { serverT } from "@/i18n/server";

export default async function AdminGames({ searchParams }: { searchParams: Promise<{ kind?: string; page?: string }> }) {
  const { t } = await serverT();
  const sp = await searchParams;
  const kind = sp.kind === "bot" || sp.kind === "friend" ? sp.kind : "";
  const page = Math.max(1, Number(sp.page) || 1);
  const games = await recentGames(kind ? sql`g.kind = ${kind}` : sql`true`, 50, (page - 1) * 50);
  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-black">{t("admin_games")}</h1>
      <div className="flex gap-2">
        {[["", t("admin_all")], ["friend", t("admin_games_online")], ["bot", t("admin_games_bot")]].map(([k, label]) => (
          <Link key={k} href={k ? `/admin/games?kind=${k}` : "/admin/games"} className="chip" aria-pressed={kind === k}>
            {label}
          </Link>
        ))}
      </div>
      <div className="card overflow-x-auto">
        <GamesTable games={games} />
      </div>
      <div className="flex justify-center gap-2">
        {page > 1 && <Link className="btn btn-ghost" href={`/admin/games?kind=${kind}&page=${page - 1}`}>{t("admin_prev")}</Link>}
        {games.length === 50 && <Link className="btn btn-ghost" href={`/admin/games?kind=${kind}&page=${page + 1}`}>{t("admin_next")}</Link>}
      </div>
    </div>
  );
}
