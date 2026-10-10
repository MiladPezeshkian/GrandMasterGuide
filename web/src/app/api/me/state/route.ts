import { sql } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";

/** The player's chess-school progress (the app's key-value store), kept with the account. */
export async function GET() {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const d = await db();
  const row = (await d.select().from(schema.userState).where(sql`${schema.userState.userId} = ${u.id}`).limit(1))[0];
  return ok({ data: row?.data ?? {} });
}

export async function PUT(req: Request) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const b = await body<{ data?: Record<string, unknown> }>(req);
  const data: Record<string, string> = {};
  for (const [k, v] of Object.entries(b.data ?? {})) {
    if (typeof v === "string" && k.length <= 100) data[k] = v;
  }
  if (JSON.stringify(data).length > 1_000_000) return fail("err_generic", 413);
  const d = await db();
  await d
    .insert(schema.userState)
    .values({ userId: u.id, data })
    .onConflictDoUpdate({ target: schema.userState.userId, set: { data, updatedAt: new Date() } });
  return ok();
}
