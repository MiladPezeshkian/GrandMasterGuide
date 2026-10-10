import { eq } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { body, fail, ok } from "@/lib/http";

/** Admin actions on a player: ban, unban, verify, signout (everywhere), delete. */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const admin = await currentUser();
  if (!admin || admin.role !== "admin") return fail("err_generic", 403);
  const { id } = await params;
  if (id === admin.id) return fail("err_generic", 400);
  const { action } = await body<{ action?: string }>(req);
  const d = await db();
  switch (action) {
    case "ban":
      await d.update(schema.users).set({ banned: true }).where(eq(schema.users.id, id));
      await d.delete(schema.sessions).where(eq(schema.sessions.userId, id));
      break;
    case "unban":
      await d.update(schema.users).set({ banned: false }).where(eq(schema.users.id, id));
      break;
    case "verify":
      await d.update(schema.users).set({ verified: true }).where(eq(schema.users.id, id));
      break;
    case "signout":
      await d.delete(schema.sessions).where(eq(schema.sessions.userId, id));
      break;
    case "delete":
      await d.delete(schema.users).where(eq(schema.users.id, id));
      break;
    default:
      return fail("err_generic");
  }
  return ok();
}
