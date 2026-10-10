import { dateTime } from "./format";
import type { AdminGame } from "@/lib/admin";
import { serverT } from "@/i18n/server";
import { digits, type SiteKey } from "@/i18n/site";

export async function GamesTable({ games }: { games: AdminGame[] }) {
  const { t, lang } = await serverT();
  const status: Record<string, SiteKey> = { active: "status_active", waiting: "status_waiting", finished: "status_finished", aborted: "status_aborted", declined: "status_declined" };
  return (
    <table className="w-full min-w-[40rem] text-sm">
      <thead className="border-b border-line text-xs text-dim">
        <tr>
          {[t("admin_col_players"), t("admin_col_status"), t("admin_col_result"), t("admin_col_moves"), t("admin_col_when")].map((h) => (
            <th key={h} className="px-4 py-2.5 text-start font-semibold">
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {games.map((g) => (
          <tr key={g.id}>
            <td className="ltr px-4 py-2.5 text-start">
              <span className="font-semibold">{g.white ?? (g.botLevel ? `Zorix ${g.botLevel}` : "—")}</span>
              <span className="text-faint"> vs </span>
              <span className="font-semibold">{g.black ?? (g.botLevel ? `Zorix ${g.botLevel}` : "—")}</span>
            </td>
            <td className="px-4 py-2.5">
              <span className="chip">{g.kind === "bot" ? "Zorix" : t(status[g.status] ?? "status_finished")}</span>
            </td>
            <td className="ltr px-4 py-2.5 font-bold">{g.result ?? "—"}</td>
            <td className="tnum px-4 py-2.5">{digits(lang, Math.ceil(g.ply / 2))}</td>
            <td className="px-4 py-2.5 text-dim">{dateTime(lang, g.createdAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
