import { eq, sql } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { fail, ok } from "@/lib/http";

/**
 * Sent about every minute while the site is open and visible. Each heartbeat adds the time since the
 * previous one (from any tab) to the player's time on the site; after a gap of more than two minutes
 * the player was away, and nothing is added.
 */
export async function POST() {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const d = await db();
  const row = (await d.select({ last: schema.users.lastSeenAt }).from(schema.users).where(eq(schema.users.id, u.id)).limit(1))[0];
  const now = new Date();
  const elapsed = row?.last ? Math.round((now.getTime() - row.last.getTime()) / 1000) : 0;
  const added = elapsed > 0 && elapsed <= 120 ? elapsed : 0;
  await d
    .update(schema.users)
    .set({ lastSeenAt: now, secondsOnline: sql`${schema.users.secondsOnline} + ${added}` })
    .where(eq(schema.users.id, u.id));
  if (added > 0) {
    const day = now.toISOString().slice(0, 10);
    await d
      .insert(schema.activity)
      .values({ userId: u.id, day, seconds: added })
      .onConflictDoUpdate({ target: [schema.activity.userId, schema.activity.day], set: { seconds: sql`${schema.activity.seconds} + ${added}` } });
  }
  return ok();
}
