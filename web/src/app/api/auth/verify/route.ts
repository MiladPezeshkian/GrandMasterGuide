import { eq, sql } from "drizzle-orm";
import { createSession, useCode } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";

export async function POST(req: Request) {
  const b = await body<{ email?: string; code?: string }>(req);
  const email = (b.email ?? "").trim().toLowerCase();
  const code = (b.code ?? "").replace(/\D/g, "");
  const d = await db();
  const u = (await d.select().from(schema.users).where(sql`lower(${schema.users.email}) = ${email}`).limit(1))[0];
  if (!u) return fail("err_not_found", 404);
  if (u.banned) return fail("err_banned", 403);
  const r = await useCode(u.id, "verify", code);
  if (r === "wrong") return fail("err_code_wrong");
  if (r === "expired") return fail("err_code_expired");
  await d.update(schema.users).set({ verified: true }).where(eq(schema.users.id, u.id));
  await createSession(u.id);
  return ok({ redirect: "/app" });
}
