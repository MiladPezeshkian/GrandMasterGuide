import { SettingsForm } from "@/components/admin/UserActions";
import { siteSettings } from "@/lib/settings";
import { serverT } from "@/i18n/server";

export default async function AdminSettings() {
  const { t } = await serverT();
  const s = await siteSettings();
  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-black">{t("admin_settings")}</h1>
      <SettingsForm announcement={s.announcement} registrationOpen={s.registrationOpen} />
    </div>
  );
}
