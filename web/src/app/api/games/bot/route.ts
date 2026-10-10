import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { newGameId, replay } from "@/lib/games";
import { body, fail, ok } from "@/lib/http";

/** Records a finished game against a Zorix level (for the player's history and the admin's statistics). */
export async function POST(req: Request) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const b = await body<{ level?: number; side?: string; result?: string; reason?: string; moves?: string[] }>(req);
  const level = Math.max(1, Math.min(20, Math.round(Number(b.level) || 1)));
  const moves = (Array.isArray(b.moves) ? b.moves : []).filter((m) => typeof m === "string" && /^[a-h][1-8][a-h][1-8][qrbn]?$/.test(m)).slice(0, 600);
  const chess = replay(moves);
  const white = b.side === "black" ? null : u.id;
  const result = ["1-0", "0-1", "1/2-1/2"].includes(String(b.result)) ? String(b.result) : null;
  const d = await db();
  await d.insert(schema.games).values({
    id: newGameId(),
    kind: "bot",
    status: "finished",
    creatorId: u.id,
    whiteId: white,
    blackId: white ? null : u.id,
    creatorColor: b.side === "black" ? "black" : "white",
    botLevel: level,
    moves: chess ? moves.join(" ") : "",
    fen: chess?.fen() ?? "",
    ply: chess ? moves.length : 0,
    result,
    reason: typeof b.reason === "string" ? b.reason.slice(0, 40) : null,
    finishedAt: new Date(),
  });
  return ok();
}
