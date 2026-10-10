import { sql } from "drizzle-orm";
import { createCode } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";
import { sendCode } from "@/lib/mail";
import { isLang } from "@/i18n/site";

export async function POST(req: Request) {
  const b = await body<{ email?: string; purpose?: string; lang?: string }>(req);
  const email = (b.email ?? "").trim().toLowerCase();
  const purpose = b.purpose === "reset" ? "reset" : "verify";
  const lang = isLang(b.lang) ? b.lang : "fa";
  const d = await db();
  const u = (await d.select().from(schema.users).where(sql`lower(${schema.users.email}) = ${email}`).limit(1))[0];
  // Do not reveal which emails have accounts: answer "sent" either way.
  if (!u || u.banned || (purpose === "verify" && u.verified)) return ok();
  const code = await createCode(u.id, purpose);
  if (code === "too_many") return fail("err_too_many", 429);
  if (!(await sendCode(u.email, u.username, code, purpose, lang))) return fail("err_mail_failed", 502);
  return ok();
}
