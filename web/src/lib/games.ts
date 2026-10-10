import "server-only";
import crypto from "node:crypto";
import { Chess } from "chess.js";
import { and, eq, inArray, or, sql } from "drizzle-orm";
import { db, schema } from "./db";
import type { Game } from "./db/schema";

export const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

/** Time controls offered for online games: minutes + increment seconds (0 = no clock). */
export const TIME_CONTROLS: [number, number][] = [
  [0, 0],
  [3, 2],
  [5, 0],
  [10, 0],
  [15, 10],
  [30, 0],
];

export function newGameId() {
  // 10 characters from an unambiguous alphabet: short links, practically unguessable.
  const alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
  const bytes = crypto.randomBytes(10);
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

export type Side = "white" | "black";

/** Clocks as they are right now (the side to move has been thinking since turnStartedAt). */
export function liveClocks(g: Game, now = Date.now()) {
  let white = g.whiteMs, black = g.blackMs;
  if (g.status === "active" && g.initialMs > 0 && g.turnStartedAt && g.ply >= 2) {
    const spent = now - g.turnStartedAt.getTime();
    if (g.ply % 2 === 0) white -= spent;
    else black -= spent;
  }
  return { white: Math.max(0, white), black: Math.max(0, black) };
}

export function sideOf(g: Game, userId: string): Side | null {
  if (g.whiteId === userId) return "white";
  if (g.blackId === userId) return "black";
  return null;
}

/** Replays the moves; null when one of them is not legal. */
export function replay(moves: string[]): Chess | null {
  const chess = new Chess(START_FEN);
  for (const m of moves) {
    try {
      chess.move({ from: m.slice(0, 2), to: m.slice(2, 4), promotion: m.length > 4 ? m[4] : undefined });
    } catch {
      return null;
    }
  }
  return chess;
}

/** How the position ends the game, if it does. */
export function outcome(chess: Chess): { result: string; reason: string } | null {
  if (chess.isCheckmate()) return { result: chess.turn() === "w" ? "0-1" : "1-0", reason: "checkmate" };
  if (chess.isStalemate()) return { result: "1/2-1/2", reason: "stalemate" };
  if (chess.isInsufficientMaterial()) return { result: "1/2-1/2", reason: "insufficient" };
  if (chess.isThreefoldRepetition()) return { result: "1/2-1/2", reason: "repetition" };
  if (chess.isDrawByFiftyMoves()) return { result: "1/2-1/2", reason: "fifty" };
  return null;
}

/** Ends a game whose clock ran out (checked whenever the game is read). */
export async function settleTimeout(g: Game): Promise<Game> {
  if (g.status !== "active" || g.initialMs === 0 || g.ply < 2) return g;
  const clocks = liveClocks(g);
  const toMove: Side = g.ply % 2 === 0 ? "white" : "black";
  if (clocks[toMove] > 0) return g;
  const d = await db();
  const updated = await d
    .update(schema.games)
    .set({
      status: "finished",
      result: toMove === "white" ? "0-1" : "1-0",
      reason: "timeout",
      whiteMs: clocks.white,
      blackMs: clocks.black,
      finishedAt: new Date(),
      updatedAt: new Date(),
      drawOffer: null,
    })
    .where(and(eq(schema.games.id, g.id), eq(schema.games.status, "active"), eq(schema.games.ply, g.ply)))
    .returning();
  return updated[0] ?? g;
}

export async function loadGame(id: string): Promise<Game | null> {
  const d = await db();
  const g = (await d.select().from(schema.games).where(eq(schema.games.id, id)).limit(1))[0];
  return g ? settleTimeout(g) : null;
}

async function usernames(ids: (string | null)[]): Promise<Map<string, string>> {
  const list = [...new Set(ids.filter((x): x is string => !!x))];
  if (!list.length) return new Map();
  const d = await db();
  const people = await d.select({ id: schema.users.id, username: schema.users.username }).from(schema.users).where(inArray(schema.users.id, list));
  return new Map(people.map((p) => [p.id, p.username]));
}

function viewOf(g: Game, viewerId: string | null, names: Map<string, string>) {
  const name = (id: string | null) => (id ? (names.get(id) ?? null) : null);
  return {
    id: g.id,
    status: g.status,
    moves: g.moves ? g.moves.split(" ") : [],
    fen: g.fen,
    ply: g.ply,
    result: g.result,
    reason: g.reason,
    white: name(g.whiteId),
    black: name(g.blackId),
    creator: name(g.creatorId),
    invitee: name(g.inviteeId),
    creatorColor: g.creatorColor,
    you: viewerId ? sideOf(g, viewerId) : null,
    isCreator: viewerId === g.creatorId,
    isInvitee: !!viewerId && viewerId === g.inviteeId,
    initialMs: g.initialMs,
    incrementMs: g.incrementMs,
    clocks: liveClocks(g),
    clockRunning: g.status === "active" && g.initialMs > 0 && g.ply >= 2,
    drawOffer: g.drawOffer,
    rematchId: g.rematchId,
    updatedAt: g.updatedAt.getTime(),
    serverTime: Date.now(),
  };
}

/** Public view of a game for its page (the players' usernames and the clocks right now). */
export async function gameView(g: Game, viewerId: string | null) {
  return viewOf(g, viewerId, await usernames([g.whiteId, g.blackId, g.creatorId, g.inviteeId]));
}

export type GameView = ReturnType<typeof viewOf>;

/** The player's games: invitations to them, their waiting and running games, and recent finished ones. */
export async function myGames(userId: string) {
  const d = await db();
  const rows = await d
    .select()
    .from(schema.games)
    .where(
      and(
        eq(schema.games.kind, "friend"),
        or(eq(schema.games.whiteId, userId), eq(schema.games.blackId, userId), eq(schema.games.creatorId, userId), eq(schema.games.inviteeId, userId)),
      ),
    )
    .orderBy(sql`${schema.games.updatedAt} desc`)
    .limit(40);
  const settled = await Promise.all(rows.map(settleTimeout));
  const names = await usernames(settled.flatMap((g) => [g.whiteId, g.blackId, g.creatorId, g.inviteeId]));
  return settled.map((g) => viewOf(g, userId, names));
}
