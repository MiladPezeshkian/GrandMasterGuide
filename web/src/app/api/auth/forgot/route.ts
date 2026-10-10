import { sql } from "drizzle-orm";
import { createCode, isAdminEmail } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, EMAIL_RE, fail, ok } from "@/lib/http";
import { sendCode } from "@/lib/mail";
import { isLang } from "@/i18n/site";

export async function POST(req: Request) {
  const b = await body<{ email?: string; lang?: string }>(req);
  const email = (b.email ?? "").trim().toLowerCase();
  const lang = isLang(b.lang) ? b.lang : "fa";
  if (!EMAIL_RE.test(email)) return fail("err_invalid_email");
  // The admin password lives in the environment; it cannot be reset here.
  if (isAdminEmail(email)) return ok({ email });
  const d = await db();
  const u = (await d.select().from(schema.users).where(sql`lower(${schema.users.email}) = ${email}`).limit(1))[0];
  if (!u || u.banned) return ok({ email });
  const code = await createCode(u.id, "reset");
  if (code === "too_many") return fail("err_too_many", 429);
  if (!(await sendCode(u.email, u.username, code, "reset", lang))) return fail("err_mail_failed", 502);
  return ok({ email });
}
