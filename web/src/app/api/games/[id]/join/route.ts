import { and, eq } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { gameView, loadGame } from "@/lib/games";
import { fail, ok } from "@/lib/http";

export async function POST(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const g = await loadGame((await params).id);
  if (!g) return fail("game_not_found", 404);
  if (g.whiteId === u.id || g.blackId === u.id) return ok({ game: await gameView(g, u.id) });
  if (g.status !== "waiting") return fail("game_full", 409);
  if (g.creatorId === u.id) return fail("game_cannot_self");
  if (g.inviteeId && g.inviteeId !== u.id) return fail("game_not_invited", 403);
  const creatorWhite = g.creatorColor === "white" || (g.creatorColor === "random" && Math.random() < 0.5);
  const d = await db();
  const updated = await d
    .update(schema.games)
    .set({
      status: "active",
      inviteeId: u.id,
      whiteId: creatorWhite ? g.creatorId : u.id,
      blackId: creatorWhite ? u.id : g.creatorId,
      turnStartedAt: new Date(),
      updatedAt: new Date(),
    })
    .where(and(eq(schema.games.id, g.id), eq(schema.games.status, "waiting")))
    .returning();
  if (!updated[0]) return fail("game_full", 409);
  return ok({ game: await gameView(updated[0], u.id) });
}
