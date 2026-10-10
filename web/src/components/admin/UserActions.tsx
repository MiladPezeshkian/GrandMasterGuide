"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useI18n } from "../site/Providers";

export function UserActions({ id, banned, verified, compact = false }: { id: string; banned: boolean; verified: boolean; compact?: boolean }) {
  const { t } = useI18n();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const run = async (action: string) => {
    if (action === "delete" && !confirm(t("admin_delete_confirm"))) return;
    setBusy(true);
    await fetch(`/api/admin/users/${id}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action }) });
    setBusy(false);
    if (action === "delete") router.push("/admin/users");
    router.refresh();
  };
  const cls = compact ? "btn btn-quiet h-8 min-h-0 px-2 text-xs" : "btn btn-ghost";
  return (
    <div className="flex flex-wrap gap-1.5">
      {!verified && (
        <button className={cls} disabled={busy} onClick={() => run("verify")}>
          {t("admin_verify")}
        </button>
      )}
      <button className={cls} disabled={busy} onClick={() => run(banned ? "unban" : "ban")}>
        {banned ? t("admin_unban") : t("admin_ban")}
      </button>
      {!compact && (
        <button className={cls} disabled={busy} onClick={() => run("signout")}>
          {t("admin_sign_out_all")}
        </button>
      )}
      <button className={`${cls} !text-danger`} disabled={busy} onClick={() => run("delete")}>
        {t("admin_delete")}
      </button>
    </div>
  );
}

export function SettingsForm({ announcement, registrationOpen }: { announcement: string; registrationOpen: boolean }) {
  const { t } = useI18n();
  const router = useRouter();
  const [text, setText] = useState(announcement);
  const [open, setOpen] = useState(registrationOpen);
  const [saved, setSaved] = useState(false);
  return (
    <div className="card max-w-2xl space-y-6 p-6">
      <label className="block">
        <span className="font-semibold">{t("admin_announcement")}</span>
        <span className="mt-1 block text-xs text-dim">{t("admin_announcement_hint")}</span>
        <textarea className="input mt-3 min-h-24 py-3" maxLength={300} value={text} onChange={(e) => setText(e.target.value)} />
      </label>
      <label className="flex items-center gap-3">
        <input type="checkbox" className="h-5 w-5 accent-[var(--accent)]" checked={open} onChange={(e) => setOpen(e.target.checked)} />
        <span className="font-semibold">{t("admin_registration")}</span>
      </label>
      <div className="flex items-center gap-3">
        <button
          className="btn btn-primary"
          onClick={async () => {
            await fetch("/api/admin/settings", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ announcement: text, registrationOpen: open }) });
            setSaved(true);
            router.refresh();
            setTimeout(() => setSaved(false), 2000);
          }}
        >
          {t("admin_save")}
        </button>
        {saved && <span className="text-sm font-semibold text-best">{t("admin_saved")}</span>}
      </div>
    </div>
  );
}
