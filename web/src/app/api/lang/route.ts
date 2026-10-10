import { eq } from "drizzle-orm";
import { cookies } from "next/headers";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";
import { isLang } from "@/i18n/site";
import { LANG_COOKIE } from "@/i18n/server";

export async function POST(req: Request) {
  const b = await body<{ lang?: string }>(req);
  if (!isLang(b.lang)) return fail("err_generic");
  (await cookies()).set(LANG_COOKIE, b.lang, { path: "/", maxAge: 365 * 86_400, sameSite: "lax" });
  const u = await currentUser().catch(() => null);
  if (u) await (await db()).update(schema.users).set({ lang: b.lang }).where(eq(schema.users.id, u.id));
  return ok();
}
