import { sql } from "drizzle-orm";
import { createCode, hashPassword, isAdminEmail } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, EMAIL_RE, fail, ok, USERNAME_RE } from "@/lib/http";
import { sendCode } from "@/lib/mail";
import { siteSettings } from "@/lib/settings";
import { isLang } from "@/i18n/site";

export async function POST(req: Request) {
  const b = await body<{ email?: string; username?: string; password?: string; lang?: string }>(req);
  const email = (b.email ?? "").trim().toLowerCase();
  const username = (b.username ?? "").trim();
  const password = b.password ?? "";
  const lang = isLang(b.lang) ? b.lang : "fa";
  if (!EMAIL_RE.test(email)) return fail("err_invalid_email");
  if (!USERNAME_RE.test(username)) return fail("err_username");
  if (password.length < 8 || password.length > 200) return fail("err_password_short");
  if (isAdminEmail(email)) return fail("err_email_taken");
  if (!(await siteSettings()).registrationOpen) return fail("err_registration_closed", 403);

  const d = await db();
  const same = await d
    .select({ id: schema.users.id, email: schema.users.email, username: schema.users.username, verified: schema.users.verified })
    .from(schema.users)
    .where(sql`lower(${schema.users.email}) = ${email} OR lower(${schema.users.username}) = ${username.toLowerCase()}`);
  const byEmail = same.find((u) => u.email.toLowerCase() === email);
  const byName = same.find((u) => u.username.toLowerCase() === username.toLowerCase());
  if (byEmail?.verified) return fail("err_email_taken");
  if (byName && byName.id !== byEmail?.id) return fail("err_username_taken");

  const passwordHash = await hashPassword(password);
  let userId: string;
  if (byEmail) {
    // An unconfirmed sign-up with this email: start it again with the new details.
    await d.update(schema.users).set({ username, passwordHash, lang }).where(sql`${schema.users.id} = ${byEmail.id}`);
    userId = byEmail.id;
  } else {
    const inserted = await d.insert(schema.users).values({ email, username, passwordHash, lang }).returning({ id: schema.users.id });
    userId = inserted[0].id;
  }
  const code = await createCode(userId, "verify");
  if (code === "too_many") return fail("err_too_many", 429);
  if (!(await sendCode(email, username, code, "verify", lang))) return fail("err_mail_failed", 502);
  return ok({ email });
}
