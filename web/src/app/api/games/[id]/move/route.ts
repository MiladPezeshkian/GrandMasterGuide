import { and, eq } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { gameView, liveClocks, loadGame, outcome, replay, sideOf } from "@/lib/games";
import { body, fail, ok } from "@/lib/http";

/** Plays a move. [ply] is the number of moves the player saw, so a stale page cannot move twice. */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const b = await body<{ uci?: string; ply?: number }>(req);
  const g = await loadGame((await params).id);
  if (!g) return fail("game_not_found", 404);
  const side = sideOf(g, u.id);
  const uci = String(b.uci ?? "");
  if (!side || g.status !== "active" || !/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(uci)) return fail("err_generic", 409, { game: await gameView(g, u.id) });
  const toMove = g.ply % 2 === 0 ? "white" : "black";
  if (toMove !== side || Number(b.ply) !== g.ply) return fail("err_generic", 409, { game: await gameView(g, u.id) });

  const moves = g.moves ? g.moves.split(" ") : [];
  const chess = replay([...moves, uci]);
  if (!chess) return fail("err_generic", 422, { game: await gameView(g, u.id) });

  const now = new Date();
  let { white, black } = { white: g.whiteMs, black: g.blackMs };
  if (g.initialMs > 0 && g.ply >= 2) {
    const clocks = liveClocks(g, now.getTime());
    white = clocks.white;
    black = clocks.black;
    if (side === "white") white += g.incrementMs;
    else black += g.incrementMs;
  }
  const end = outcome(chess);
  const d = await db();
  const updated = await d
    .update(schema.games)
    .set({
      moves: [...moves, uci].join(" "),
      fen: chess.fen(),
      ply: g.ply + 1,
      whiteMs: white,
      blackMs: black,
      turnStartedAt: now,
      updatedAt: now,
      // A move declines a draw offer by the opponent; the mover's own offer stands.
      drawOffer: g.drawOffer === side ? g.drawOffer : null,
      ...(end ? { status: "finished", result: end.result, reason: end.reason, finishedAt: now, drawOffer: null } : {}),
    })
    .where(and(eq(schema.games.id, g.id), eq(schema.games.ply, g.ply), eq(schema.games.status, "active")))
    .returning();
  if (!updated[0]) {
    const fresh = await loadGame(g.id);
    return fail("err_generic", 409, { game: fresh ? await gameView(fresh, u.id) : null });
  }
  return ok({ game: await gameView(updated[0], u.id) });
}
