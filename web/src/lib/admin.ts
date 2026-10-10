import "server-only";
import { redirect } from "next/navigation";
import { sql, type SQL } from "drizzle-orm";
import { currentUser } from "./auth";
import { db } from "./db";

/** The signed-in administrator, or a redirect to the sign-in page. */
export async function requireAdmin() {
  const u = await currentUser();
  if (!u) redirect("/login?next=/admin");
  if (u.role !== "admin") redirect("/app");
  return u;
}

type Row = Record<string, unknown>;

async function rows<T = Row>(q: SQL): Promise<T[]> {
  const d = await db();
  const r = await d.execute(q);
  return (Array.isArray(r) ? r : (r as { rows: T[] }).rows) as T[];
}

const n = (v: unknown) => Number(v ?? 0);

export type DayPoint = { day: string; value: number };

export async function overview() {
  const [u] = await rows(sql`
    SELECT count(*) AS total,
           count(*) FILTER (WHERE verified) AS verified,
           count(*) FILTER (WHERE last_seen_at > now() - interval '2 minutes') AS online,
           count(*) FILTER (WHERE created_at >= date_trunc('day', now())) AS new_today,
           coalesce(sum(seconds_online), 0) AS seconds,
           count(*) FILTER (WHERE seconds_online > 0) AS with_time
    FROM users WHERE role <> 'admin'`);
  const [a] = await rows(sql`
    SELECT count(DISTINCT user_id) FILTER (WHERE day = (now() AT TIME ZONE 'UTC')::date) AS today,
           count(DISTINCT user_id) FILTER (WHERE day > (now() AT TIME ZONE 'UTC')::date - 7) AS week
    FROM activity`);
  const [g] = await rows(sql`
    SELECT count(*) AS total,
           count(*) FILTER (WHERE kind = 'friend') AS online,
           count(*) FILTER (WHERE kind = 'bot') AS bot,
           count(*) FILTER (WHERE kind = 'friend' AND status = 'active') AS live
    FROM games WHERE NOT (kind = 'friend' AND status IN ('aborted', 'declined'))`);
  const series = async (q: SQL) =>
    (await rows<{ day: string; value: unknown }>(q)).map((r) => ({ day: String(r.day).slice(0, 10), value: n(r.value) }));
  const days = sql`generate_series((now() AT TIME ZONE 'UTC')::date - 29, (now() AT TIME ZONE 'UTC')::date, interval '1 day') AS d(day)`;
  const signups = await series(sql`SELECT d.day::date::text AS day, count(u.id) AS value FROM ${days} LEFT JOIN users u ON (u.created_at AT TIME ZONE 'UTC')::date = d.day::date AND u.role <> 'admin' GROUP BY d.day ORDER BY d.day`);
  const active = await series(sql`SELECT d.day::date::text AS day, count(DISTINCT a.user_id) AS value FROM ${days} LEFT JOIN activity a ON a.day = d.day::date GROUP BY d.day ORDER BY d.day`);
  const games = await series(sql`SELECT d.day::date::text AS day, count(g.id) AS value FROM ${days} LEFT JOIN games g ON (g.created_at AT TIME ZONE 'UTC')::date = d.day::date AND NOT (g.kind = 'friend' AND g.status IN ('aborted', 'declined')) GROUP BY d.day ORDER BY d.day`);
  return {
    users: { total: n(u.total), verified: n(u.verified), online: n(u.online), newToday: n(u.new_today), seconds: n(u.seconds), avgSeconds: n(u.with_time) ? n(u.seconds) / n(u.with_time) : 0 },
    active: { today: n(a.today), week: n(a.week) },
    games: { total: n(g.total), online: n(g.online), bot: n(g.bot), live: n(g.live) },
    signups,
    activeDays: active,
    gameDays: games,
  };
}

export type AdminUser = {
  id: string;
  email: string;
  username: string;
  verified: boolean;
  banned: boolean;
  role: string;
  createdAt: string;
  lastSeenAt: string | null;
  secondsOnline: number;
  loginCount: number;
  games: number;
};

export const PAGE_SIZE = 25;

export async function listUsers(q: string, page: number, sort: string) {
  const like = `%${q.trim().toLowerCase()}%`;
  const order =
    sort === "time" ? sql`seconds_online DESC` : sort === "seen" ? sql`last_seen_at DESC NULLS LAST` : sort === "games" ? sql`games DESC` : sql`created_at DESC`;
  const list = await rows(sql`
    SELECT u.id, u.email, u.username, u.verified, u.banned, u.role, u.created_at, u.last_seen_at, u.seconds_online, u.login_count,
           (SELECT count(*) FROM games g WHERE (g.white_id = u.id OR g.black_id = u.id) AND NOT (g.kind = 'friend' AND g.status IN ('aborted', 'declined'))) AS games
    FROM users u
    WHERE (${q.trim() === ""} OR lower(u.email) LIKE ${like} OR lower(u.username) LIKE ${like})
    ORDER BY ${order}
    LIMIT ${PAGE_SIZE} OFFSET ${Math.max(0, page - 1) * PAGE_SIZE}`);
  const [c] = await rows(sql`SELECT count(*) AS n FROM users u WHERE (${q.trim() === ""} OR lower(u.email) LIKE ${like} OR lower(u.username) LIKE ${like})`);
  return {
    total: n(c.n),
    users: list.map(
      (r): AdminUser => ({
        id: String(r.id),
        email: String(r.email),
        username: String(r.username),
        verified: !!r.verified,
        banned: !!r.banned,
        role: String(r.role),
        createdAt: new Date(r.created_at as string).toISOString(),
        lastSeenAt: r.last_seen_at ? new Date(r.last_seen_at as string).toISOString() : null,
        secondsOnline: n(r.seconds_online),
        loginCount: n(r.login_count),
        games: n(r.games),
      }),
    ),
  };
}

export async function userDetail(id: string) {
  if (!/^[0-9a-f-]{36}$/i.test(id)) return null;
  const [u] = await rows(sql`SELECT * FROM users WHERE id = ${id}`);
  if (!u) return null;
  const [st] = await rows<{ data: Record<string, string> | string }>(sql`SELECT data FROM user_state WHERE user_id = ${id}`);
  const data: Record<string, string> = st ? (typeof st.data === "string" ? JSON.parse(st.data) : st.data) : {};
  const days = await rows<{ day: string; value: unknown }>(sql`
    SELECT d.day::date::text AS day, coalesce(a.seconds, 0) AS value
    FROM generate_series((now() AT TIME ZONE 'UTC')::date - 29, (now() AT TIME ZONE 'UTC')::date, interval '1 day') AS d(day)
    LEFT JOIN activity a ON a.day = d.day::date AND a.user_id = ${id} ORDER BY d.day`);
  const logins = await rows(sql`SELECT at, success, ip, user_agent FROM logins WHERE user_id = ${id} ORDER BY at DESC LIMIT 20`);
  const sessions = await rows(sql`SELECT count(*) AS n FROM sessions WHERE user_id = ${id} AND expires_at > now()`);
  const games = await recentGames(sql`(g.white_id = ${id} OR g.black_id = ${id} OR g.creator_id = ${id})`, 20);
  const profileCount = (key: string) => (data[`profile.${key}`] ?? "").split(",").filter(Boolean).length;
  return {
    user: {
      id: String(u.id),
      email: String(u.email),
      username: String(u.username),
      verified: !!u.verified,
      banned: !!u.banned,
      role: String(u.role),
      lang: (u.lang as string | null) ?? null,
      createdAt: new Date(u.created_at as string).toISOString(),
      lastSeenAt: u.last_seen_at ? new Date(u.last_seen_at as string).toISOString() : null,
      lastLoginAt: u.last_login_at ? new Date(u.last_login_at as string).toISOString() : null,
      secondsOnline: n(u.seconds_online),
      loginCount: n(u.login_count),
      activeSessions: n(sessions[0]?.n),
    },
    progress: {
      name: data["profile.name"] ?? "",
      xp: n(data["profile.xp"]),
      rating: n(data["profile.rating"]),
      lessons: profileCount("lessonStars"),
      puzzlesSolved: n(data["profile.puzzlesSolved"]),
      puzzleRating: n(data["profile.puzzleRating"]),
      wins: n(data["profile.wins"]),
      draws: n(data["profile.draws"]),
      losses: n(data["profile.losses"]),
      levels: profileCount("levelStars"),
    },
    days: days.map((r) => ({ day: String(r.day).slice(0, 10), value: Math.round(n(r.value) / 60) })),
    logins: logins.map((r) => ({ at: new Date(r.at as string).toISOString(), success: !!r.success, ip: (r.ip as string) ?? "", userAgent: (r.user_agent as string) ?? "" })),
    games,
  };
}

export type AdminGame = {
  id: string;
  kind: string;
  status: string;
  white: string | null;
  black: string | null;
  botLevel: number | null;
  result: string | null;
  reason: string | null;
  ply: number;
  createdAt: string;
};

export async function recentGames(where: SQL = sql`true`, limit = 50, offset = 0): Promise<AdminGame[]> {
  const list = await rows(sql`
    SELECT g.id, g.kind, g.status, g.bot_level, g.result, g.reason, g.ply, g.created_at, w.username AS white, b.username AS black
    FROM games g LEFT JOIN users w ON w.id = g.white_id LEFT JOIN users b ON b.id = g.black_id
    WHERE ${where}
    ORDER BY g.created_at DESC LIMIT ${limit} OFFSET ${offset}`);
  return list.map((r) => ({
    id: String(r.id),
    kind: String(r.kind),
    status: String(r.status),
    white: (r.white as string) ?? null,
    black: (r.black as string) ?? null,
    botLevel: r.bot_level == null ? null : n(r.bot_level),
    result: (r.result as string) ?? null,
    reason: (r.reason as string) ?? null,
    ply: n(r.ply),
    createdAt: new Date(r.created_at as string).toISOString(),
  }));
}

export async function recentLogins(limit = 100, offset = 0) {
  const list = await rows(sql`
    SELECT l.at, l.success, l.ip, l.user_agent, l.email, u.username, u.id AS user_id
    FROM logins l LEFT JOIN users u ON u.id = l.user_id
    ORDER BY l.at DESC LIMIT ${limit} OFFSET ${offset}`);
  return list.map((r) => ({
    at: new Date(r.at as string).toISOString(),
    success: !!r.success,
    ip: (r.ip as string) ?? "",
    userAgent: (r.user_agent as string) ?? "",
    email: String(r.email),
    username: (r.username as string) ?? null,
    userId: (r.user_id as string) ?? null,
  }));
}

/** "Chrome · Windows" from a user agent. */
export function device(ua: string): string {
  const browser = /Edg\//.test(ua) ? "Edge" : /OPR\//.test(ua) ? "Opera" : /Firefox\//.test(ua) ? "Firefox" : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : ua ? "Browser" : "—";
  const os = /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS" : /Windows/.test(ua) ? "Windows" : /Mac OS X/.test(ua) ? "macOS" : /Linux/.test(ua) ? "Linux" : "";
  return os ? `${browser} · ${os}` : browser;
}
