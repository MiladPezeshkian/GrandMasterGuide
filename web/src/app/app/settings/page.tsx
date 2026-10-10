import { SettingsView } from "@/components/school/SettingsView";
import { currentUser } from "@/lib/auth";

export default async function Page() {
  const user = await currentUser();
  return <SettingsView account={{ email: user?.email ?? "", username: user?.username ?? "" }} />;
}
