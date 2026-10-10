import { sql } from "drizzle-orm";
import { checkPassword, createCode, createSession, ensureAdmin, isAdminEmail, isAdminLogin, loginBlocked, recordLogin } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";
import { sendCode } from "@/lib/mail";
import { isLang } from "@/i18n/site";

export async function POST(req: Request) {
  const b = await body<{ login?: string; password?: string; lang?: string }>(req);
  const login = (b.login ?? "").trim();
  const password = b.password ?? "";
  const lang = isLang(b.lang) ? b.lang : "fa";
  if (!login || !password) return fail("err_wrong_credentials");
  if (await loginBlocked(login)) return fail("err_too_many", 429);

  // The administrator: credentials from the environment, never from the database.
  if (isAdminEmail(login)) {
    if (!isAdminLogin(login, password)) {
      await recordLogin(login, null, false);
      return fail("err_wrong_credentials", 401);
    }
    const id = await ensureAdmin(login, password);
    await recordLogin(login, id, true);
    await createSession(id);
    return ok({ redirect: "/admin" });
  }

  const d = await db();
  const key = login.toLowerCase();
  const u = (
    await d.select().from(schema.users).where(sql`lower(${schema.users.email}) = ${key} OR lower(${schema.users.username}) = ${key}`).limit(1)
  )[0];
  if (!u || !(await checkPassword(password, u.passwordHash))) {
    await recordLogin(login, u?.id ?? null, false);
    return fail("err_wrong_credentials", 401);
  }
  if (u.banned) {
    await recordLogin(login, u.id, false);
    return fail("err_banned", 403);
  }
  if (!u.verified) {
    const code = await createCode(u.id, "verify");
    if (code !== "too_many") await sendCode(u.email, u.username, code, "verify", lang);
    return ok({ redirect: `/verify?email=${encodeURIComponent(u.email)}` });
  }
  await recordLogin(login, u.id, true);
  await createSession(u.id);
  return ok({ redirect: u.role === "admin" ? "/admin" : "/app" });
}
