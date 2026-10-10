import Link from "next/link";
import { dateTime } from "@/components/admin/format";
import { device, recentLogins } from "@/lib/admin";
import { serverT } from "@/i18n/server";

export default async function AdminLogins({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
  const { t, lang } = await serverT();
  const page = Math.max(1, Number((await searchParams).page) || 1);
  const list = await recentLogins(100, (page - 1) * 100);
  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-black">{t("admin_logins")}</h1>
      <div className="card overflow-x-auto">
        <table className="w-full min-w-[44rem] text-sm">
          <thead className="border-b border-line text-xs text-dim">
            <tr>
              {[t("admin_col_when"), t("admin_col_user"), t("admin_col_status"), t("admin_col_ip"), t("admin_col_device")].map((h) => (
                <th key={h} className="px-4 py-3 text-start font-semibold">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {list.map((l, i) => (
              <tr key={i}>
                <td className="px-4 py-2.5 text-dim">{dateTime(lang, l.at)}</td>
                <td className="ltr px-4 py-2.5 text-start">
                  {l.userId ? (
                    <Link href={`/admin/users/${l.userId}`} className="font-semibold hover:text-accent">
                      @{l.username}
                    </Link>
                  ) : (
                    <span className="text-dim">{l.email}</span>
                  )}
                </td>
                <td className="px-4 py-2.5">{l.success ? <span className="font-semibold text-best">{t("admin_success")}</span> : <span className="font-semibold text-danger">{t("admin_failed")}</span>}</td>
                <td className="ltr px-4 py-2.5 text-start text-xs text-dim">{l.ip}</td>
                <td className="px-4 py-2.5 text-xs text-dim">{device(l.userAgent)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex justify-center gap-2">
        {page > 1 && <Link className="btn btn-ghost" href={`/admin/logins?page=${page - 1}`}>{t("admin_prev")}</Link>}
        {list.length === 100 && <Link className="btn btn-ghost" href={`/admin/logins?page=${page + 1}`}>{t("admin_next")}</Link>}
      </div>
    </div>
  );
}
