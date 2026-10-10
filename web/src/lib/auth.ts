import "server-only";
import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import { and, eq, gt, sql } from "drizzle-orm";
import { cookies, headers } from "next/headers";
import { db, schema } from "./db";
import type { User } from "./db/schema";

const COOKIE = "zx_session";
const SESSION_DAYS = 30;
const CODE_MINUTES = 15;

export type SessionUser = Pick<User, "id" | "email" | "username" | "role" | "verified" | "lang" | "createdAt">;

function sha256(text: string) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

export function hashPassword(password: string) {
  return bcrypt.hash(password, 11);
}

export function checkPassword(password: string, hash: string) {
  return bcrypt.compare(password, hash);
}

/** Constant-time comparison of two secrets. */
export function sameSecret(a: string, b: string) {
  const x = Buffer.from(sha256(a)), y = Buffer.from(sha256(b));
  return crypto.timingSafeEqual(x, y);
}

export async function requestInfo() {
  const h = await headers();
  const ip = (h.get("x-forwarded-for") ?? "").split(",")[0].trim() || h.get("x-real-ip") || null;
  return { ip, userAgent: h.get("user-agent")?.slice(0, 300) ?? null };
}

// ---------------------------------------------------------------- sessions

export async function createSession(userId: string) {
  const d = await db();
  const token = crypto.randomBytes(32).toString("base64url");
  const { ip, userAgent } = await requestInfo();
  const expiresAt = new Date(Date.now() + SESSION_DAYS * 86_400_000);
  await d.insert(schema.sessions).values({ id: sha256(token), userId, expiresAt, ip, userAgent });
  await d
    .update(schema.users)
    .set({ lastLoginAt: new Date(), lastSeenAt: new Date(), loginCount: sql`${schema.users.loginCount} + 1` })
    .where(eq(schema.users.id, userId));
  (await cookies()).set(COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    expires: expiresAt,
  });
}

/** The signed-in player, or null. Banned players and expired sessions count as signed out. */
export async function currentUser(): Promise<SessionUser | null> {
  const token = (await cookies()).get(COOKIE)?.value;
  if (!token) return null;
  const d = await db();
  const rows = await d
    .select({
      id: schema.users.id,
      email: schema.users.email,
      username: schema.users.username,
      role: schema.users.role,
      verified: schema.users.verified,
      banned: schema.users.banned,
      lang: schema.users.lang,
      createdAt: schema.users.createdAt,
    })
    .from(schema.sessions)
    .innerJoin(schema.users, eq(schema.sessions.userId, schema.users.id))
    .where(and(eq(schema.sessions.id, sha256(token)), gt(schema.sessions.expiresAt, new Date())))
    .limit(1);
  const u = rows[0];
  if (!u || u.banned || !u.verified) return null;
  const { banned: _banned, ...user } = u;
  return user;
}

export async function signOut() {
  const jar = await cookies();
  const token = jar.get(COOKIE)?.value;
  if (token) {
    const d = await db();
    await d.delete(schema.sessions).where(eq(schema.sessions.id, sha256(token)));
  }
  jar.delete(COOKIE);
}

// ---------------------------------------------------------------- email codes

export type CodePurpose = "verify" | "reset";

/** A new 6-digit code for [userId] (older codes for the same purpose stop working). */
export async function createCode(userId: string, purpose: CodePurpose): Promise<string | "too_many"> {
  const d = await db();
  const hourAgo = new Date(Date.now() - 3_600_000);
  const recent = await d
    .select({ n: sql<number>`count(*)::int` })
    .from(schema.codes)
    .where(and(eq(schema.codes.userId, userId), eq(schema.codes.purpose, purpose), gt(schema.codes.createdAt, hourAgo)));
  if ((recent[0]?.n ?? 0) >= 6) return "too_many";
  const code = crypto.randomInt(0, 1_000_000).toString().padStart(6, "0");
  // Only the newest code is checked (useCode), so earlier ones stop working; they stay for the rate limit.
  await d.insert(schema.codes).values({
    userId,
    purpose,
    codeHash: sha256(`${userId}:${purpose}:${code}`),
    expiresAt: new Date(Date.now() + CODE_MINUTES * 60_000),
  });
  return code;
}

/** Checks a code; each code allows 5 tries and is used up when it matches. */
export async function useCode(userId: string, purpose: CodePurpose, code: string): Promise<"ok" | "wrong" | "expired"> {
  const d = await db();
  const rows = await d
    .select()
    .from(schema.codes)
    .where(and(eq(schema.codes.userId, userId), eq(schema.codes.purpose, purpose)))
    .orderBy(sql`${schema.codes.createdAt} desc`)
    .limit(1);
  const c = rows[0];
  if (!c || c.expiresAt < new Date() || c.attempts >= 5) return "expired";
  if (c.codeHash !== sha256(`${userId}:${purpose}:${code.trim()}`)) {
    await d.update(schema.codes).set({ attempts: c.attempts + 1 }).where(eq(schema.codes.id, c.id));
    return "wrong";
  }
  await d.delete(schema.codes).where(and(eq(schema.codes.userId, userId), eq(schema.codes.purpose, purpose)));
  return "ok";
}

// ---------------------------------------------------------------- sign-in attempts

export async function recordLogin(email: string, userId: string | null, success: boolean) {
  const d = await db();
  const { ip, userAgent } = await requestInfo();
  await d.insert(schema.logins).values({ email: email.toLowerCase(), userId, success, ip, userAgent });
}

/** Too many failed sign-ins for this email or address in the last 15 minutes. */
export async function loginBlocked(email: string) {
  const d = await db();
  const { ip } = await requestInfo();
  const since = new Date(Date.now() - 15 * 60_000);
  const rows = await d
    .select({ n: sql<number>`count(*)::int` })
    .from(schema.logins)
    .where(
      and(
        eq(schema.logins.success, false),
        gt(schema.logins.at, since),
        ip ? sql`(${schema.logins.email} = ${email.toLowerCase()} OR ${schema.logins.ip} = ${ip})` : eq(schema.logins.email, email.toLowerCase()),
      ),
    );
  return (rows[0]?.n ?? 0) >= 10;
}

// ---------------------------------------------------------------- the administrator

/** The admin account comes from ADMIN_EMAIL / ADMIN_PASSWORD (never stored in the code). */
export function isAdminLogin(email: string, password: string) {
  const adminEmail = process.env.ADMIN_EMAIL?.trim().toLowerCase();
  const adminPassword = process.env.ADMIN_PASSWORD;
  if (!adminEmail || !adminPassword) return false;
  return email.trim().toLowerCase() === adminEmail && sameSecret(password, adminPassword);
}

export function isAdminEmail(email: string) {
  return !!process.env.ADMIN_EMAIL && email.trim().toLowerCase() === process.env.ADMIN_EMAIL.trim().toLowerCase();
}

/** Creates or updates the admin user (verified, role admin) and returns its id. */
export async function ensureAdmin(email: string, password: string): Promise<string> {
  const d = await db();
  const existing = await d.select().from(schema.users).where(sql`lower(${schema.users.email}) = ${email.toLowerCase()}`).limit(1);
  const passwordHash = await hashPassword(password);
  if (existing[0]) {
    await d.update(schema.users).set({ role: "admin", verified: true, banned: false, passwordHash }).where(eq(schema.users.id, existing[0].id));
    return existing[0].id;
  }
  let username = "Zorix";
  const taken = await d.select({ id: schema.users.id }).from(schema.users).where(sql`lower(${schema.users.username}) = 'zorix'`).limit(1);
  if (taken[0]) username = `admin${crypto.randomInt(1000, 9999)}`;
  const inserted = await d
    .insert(schema.users)
    .values({ email: email.toLowerCase(), username, passwordHash, verified: true, role: "admin" })
    .returning({ id: schema.users.id });
  return inserted[0].id;
}
