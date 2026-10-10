import { and, eq } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { gameView, liveClocks, loadGame, newGameId, sideOf, START_FEN } from "@/lib/games";
import { body, fail, ok } from "@/lib/http";

/** resign, draw (offer or accept), decline-draw, abort, decline (an invitation), rematch. */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const { action } = await body<{ action?: string }>(req);
  const g = await loadGame((await params).id);
  if (!g) return fail("game_not_found", 404);
  const d = await db();
  const side = sideOf(g, u.id);
  const now = new Date();
  const finish = (result: string, reason: string) => ({
    status: "finished" as const,
    result,
    reason,
    finishedAt: now,
    updatedAt: now,
    drawOffer: null,
    ...(() => {
      const c = liveClocks(g, now.getTime());
      return { whiteMs: c.white, blackMs: c.black };
    })(),
  });
  const save = async (values: Partial<typeof schema.games.$inferInsert>, status = g.status) => {
    const r = await d.update(schema.games).set(values).where(and(eq(schema.games.id, g.id), eq(schema.games.status, status), eq(schema.games.ply, g.ply))).returning();
    return r[0] ?? (await loadGame(g.id));
  };

  let result = g;
  switch (action) {
    case "resign":
      if (!side || g.status !== "active") break;
      result = (await save(finish(side === "white" ? "0-1" : "1-0", "resign"))) ?? g;
      break;
    case "draw":
      if (!side || g.status !== "active") break;
      if (g.drawOffer && g.drawOffer !== side) result = (await save(finish("1/2-1/2", "agreement"))) ?? g;
      else result = (await save({ drawOffer: side, updatedAt: now })) ?? g;
      break;
    case "decline-draw":
      if (!side || g.status !== "active" || !g.drawOffer || g.drawOffer === side) break;
      result = (await save({ drawOffer: null, updatedAt: now })) ?? g;
      break;
    case "abort":
      // Before both players have moved, either may abort; a waiting game can be withdrawn by its creator.
      if (g.status === "waiting" && g.creatorId === u.id) result = (await save({ status: "aborted", updatedAt: now, finishedAt: now })) ?? g;
      else if (side && g.status === "active" && g.ply < 2) result = (await save({ status: "aborted", reason: "aborted", updatedAt: now, finishedAt: now })) ?? g;
      break;
    case "decline":
      if (g.status === "waiting" && g.inviteeId === u.id) result = (await save({ status: "declined", updatedAt: now, finishedAt: now })) ?? g;
      break;
    case "rematch": {
      if (!side || (g.status !== "finished" && g.status !== "aborted")) break;
      if (g.rematchId) return ok({ id: g.rematchId });
      const id = newGameId();
      const opponent = side === "white" ? g.blackId : g.whiteId;
      await d.insert(schema.games).values({
        id,
        kind: "friend",
        status: "waiting",
        creatorId: u.id,
        inviteeId: opponent,
        // Colours swap in a rematch.
        creatorColor: side === "white" ? "black" : "white",
        fen: START_FEN,
        initialMs: g.initialMs,
        incrementMs: g.incrementMs,
        whiteMs: g.initialMs,
        blackMs: g.initialMs,
      });
      await d.update(schema.games).set({ rematchId: id }).where(eq(schema.games.id, g.id));
      return ok({ id });
    }
    default:
      return fail("err_generic");
  }
  return ok({ game: await gameView(result, u.id) });
}
