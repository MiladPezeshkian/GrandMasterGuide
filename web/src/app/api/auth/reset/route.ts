import { eq, sql } from "drizzle-orm";
import { createSession, hashPassword, useCode } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";

export async function POST(req: Request) {
  const b = await body<{ email?: string; code?: string; password?: string }>(req);
  const email = (b.email ?? "").trim().toLowerCase();
  const code = (b.code ?? "").replace(/\D/g, "");
  const password = b.password ?? "";
  if (password.length < 8 || password.length > 200) return fail("err_password_short");
  const d = await db();
  const u = (await d.select().from(schema.users).where(sql`lower(${schema.users.email}) = ${email}`).limit(1))[0];
  if (!u || u.banned || u.role === "admin") return fail("err_code_wrong");
  const r = await useCode(u.id, "reset", code);
  if (r === "wrong") return fail("err_code_wrong");
  if (r === "expired") return fail("err_code_expired");
  // A reset proves the email too; every other session ends.
  await d.update(schema.users).set({ passwordHash: await hashPassword(password), verified: true }).where(eq(schema.users.id, u.id));
  await d.delete(schema.sessions).where(eq(schema.sessions.userId, u.id));
  await createSession(u.id);
  return ok({ redirect: "/app" });
}
