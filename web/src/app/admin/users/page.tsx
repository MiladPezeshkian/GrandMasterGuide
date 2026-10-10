import Link from "next/link";
import { ago, date, duration } from "@/components/admin/format";
import { UserActions } from "@/components/admin/UserActions";
import { listUsers, PAGE_SIZE } from "@/lib/admin";
import { serverT } from "@/i18n/server";
import { digits } from "@/i18n/site";

export default async function AdminUsers({ searchParams }: { searchParams: Promise<{ q?: string; page?: string; sort?: string }> }) {
  const { t, lang } = await serverT();
  const sp = await searchParams;
  const q = sp.q ?? "";
  const page = Math.max(1, Number(sp.page) || 1);
  const sort = sp.sort ?? "joined";
  const { users, total } = await listUsers(q, page, sort);
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const link = (p: number, s = sort) => `/admin/users?${new URLSearchParams({ q, page: String(p), sort: s })}`;
  const sorts: [string, string][] = [
    ["joined", t("admin_col_joined")],
    ["seen", t("admin_col_last_seen")],
    ["time", t("admin_col_time")],
    ["games", t("admin_col_games")],
  ];
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end gap-3">
        <h1 className="flex-1 text-2xl font-black">
          {t("admin_users")} <span className="tnum text-base font-semibold text-dim">({digits(lang, total)})</span>
        </h1>
        <form className="flex gap-2" action="/admin/users">
          <input name="q" defaultValue={q} placeholder={t("admin_search")} className="input h-10 min-h-0 w-64" />
          <input type="hidden" name="sort" value={sort} />
        </form>
      </div>
      <div className="flex flex-wrap gap-2">
        {sorts.map(([key, label]) => (
          <Link key={key} href={link(1, key)} className="chip" aria-pressed={sort === key}>
            {label}
          </Link>
        ))}
      </div>
      <div className="card overflow-x-auto">
        <table className="w-full min-w-[56rem] text-sm">
          <thead className="border-b border-line text-start text-xs text-dim">
            <tr>
              {[t("admin_col_user"), t("admin_col_status"), t("admin_col_joined"), t("admin_col_last_seen"), t("admin_col_time"), t("admin_col_logins"), t("admin_col_games"), ""].map((h, i) => (
                <th key={i} className="px-4 py-3 text-start font-semibold">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {users.map((u) => {
              const online = u.lastSeenAt && Date.now() - new Date(u.lastSeenAt).getTime() < 120_000;
              return (
                <tr key={u.id} className="hover:bg-surface-2/60">
                  <td className="px-4 py-3">
                    <Link href={`/admin/users/${u.id}`} className="block">
                      <span className="ltr font-bold hover:text-accent">@{u.username}</span>
                      <span className="ltr block text-xs text-faint">{u.email}</span>
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    {u.role === "admin" ? (
                      <span className="chip !text-accent">ADMIN</span>
                    ) : u.banned ? (
                      <span className="chip !text-danger">{t("admin_banned")}</span>
                    ) : !u.verified ? (
                      <span className="chip !text-warn">{t("admin_unverified")}</span>
                    ) : (
                      <span className="chip">
                        <span className={`h-2 w-2 rounded-full ${online ? "bg-best" : "bg-line-strong"}`} /> {online ? t("admin_online_now") : t("admin_active")}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-dim">{date(lang, u.createdAt)}</td>
                  <td className="px-4 py-3 text-dim">{u.lastSeenAt ? ago(lang, u.lastSeenAt) : t("admin_never")}</td>
                  <td className="tnum px-4 py-3">{duration(lang, u.secondsOnline)}</td>
                  <td className="tnum px-4 py-3">{digits(lang, u.loginCount)}</td>
                  <td className="tnum px-4 py-3">{digits(lang, u.games)}</td>
                  <td className="px-4 py-3">{u.role !== "admin" && <UserActions id={u.id} banned={u.banned} verified={u.verified} compact />}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {pages > 1 && (
        <div className="flex items-center justify-center gap-2 text-sm">
          {page > 1 && <Link href={link(page - 1)} className="btn btn-ghost">{t("admin_prev")}</Link>}
          <span className="text-dim">{t("admin_page", { p: `${digits(lang, page)} / ${digits(lang, pages)}` })}</span>
          {page < pages && <Link href={link(page + 1)} className="btn btn-ghost">{t("admin_next")}</Link>}
        </div>
      )}
    </div>
  );
}
