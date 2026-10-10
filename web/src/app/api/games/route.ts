import { sql } from "drizzle-orm";
import { currentUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { newGameId, myGames, START_FEN, TIME_CONTROLS } from "@/lib/games";
import { body, fail, ok } from "@/lib/http";

/** The player's online games and invitations. */
export async function GET() {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  return ok({ games: await myGames(u.id) });
}

/** A new game: waits for whoever opens the link, or for the invited player. */
export async function POST(req: Request) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const b = await body<{ color?: string; minutes?: number; increment?: number; opponent?: string }>(req);
  const color = b.color === "white" || b.color === "black" ? b.color : "random";
  const tc = TIME_CONTROLS.find(([m, i]) => m === Number(b.minutes) && i === Number(b.increment)) ?? [0, 0];
  const d = await db();
  let inviteeId: string | null = null;
  const opponent = (b.opponent ?? "").trim().replace(/^@/, "");
  if (opponent) {
    const o = (await d.select().from(schema.users).where(sql`lower(${schema.users.username}) = ${opponent.toLowerCase()}`).limit(1))[0];
    if (!o || !o.verified || o.banned) return fail("err_not_found", 404);
    if (o.id === u.id) return fail("game_cannot_self");
    inviteeId = o.id;
  }
  const id = newGameId();
  const ms = tc[0] * 60_000;
  await d.insert(schema.games).values({
    id,
    kind: "friend",
    status: "waiting",
    creatorId: u.id,
    inviteeId,
    creatorColor: color,
    fen: START_FEN,
    initialMs: ms,
    incrementMs: tc[1] * 1000,
    whiteMs: ms,
    blackMs: ms,
  });
  return ok({ id });
}
