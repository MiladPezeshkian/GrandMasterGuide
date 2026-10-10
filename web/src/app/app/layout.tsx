import { eq } from "drizzle-orm";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { AppShell } from "@/components/school/AppShell";
import { ZorixProvider } from "@/components/school/ZorixProvider";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";

export const metadata = { title: "School" };

export default async function SchoolLayout({ children }: { children: React.ReactNode }) {
  const user = await currentUser();
  if (!user) {
    const path = (await headers()).get("x-invoke-path") ?? "/app";
    redirect(`/login?next=${encodeURIComponent(path)}`);
  }
  const d = await db();
  const row = (await d.select().from(schema.userState).where(eq(schema.userState.userId, user.id)).limit(1))[0];
  return (
    <ZorixProvider initialState={row?.data ?? {}} userId={user.id}>
      <AppShell user={{ username: user.username, email: user.email, admin: user.role === "admin" }}>{children}</AppShell>
    </ZorixProvider>
  );
}
