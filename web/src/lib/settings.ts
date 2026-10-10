import "server-only";
import { db, schema } from "./db";

export type SiteSettings = { announcement: string; registrationOpen: boolean };

export async function siteSettings(): Promise<SiteSettings> {
  try {
    const d = await db();
    const rows = await d.select().from(schema.siteSettings);
    const map = new Map(rows.map((r) => [r.key, r.value]));
    return { announcement: map.get("announcement") ?? "", registrationOpen: map.get("registrationOpen") !== "false" };
  } catch {
    return { announcement: "", registrationOpen: true };
  }
}

export async function setSiteSetting(key: keyof SiteSettings, value: string) {
  const d = await db();
  await d
    .insert(schema.siteSettings)
    .values({ key, value })
    .onConflictDoUpdate({ target: schema.siteSettings.key, set: { value, updatedAt: new Date() } });
}
