import { sql } from "drizzle-orm";
import {
  boolean,
  date,
  index,
  integer,
  jsonb,
  pgTable,
  primaryKey,
  serial,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core";

/** Players and the one administrator (role "admin"). */
export const users = pgTable(
  "users",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    email: text("email").notNull(),
    username: text("username").notNull(),
    passwordHash: text("password_hash").notNull(),
    verified: boolean("verified").notNull().default(false),
    role: text("role").notNull().default("user"),
    banned: boolean("banned").notNull().default(false),
    lang: text("lang"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }),
    lastLoginAt: timestamp("last_login_at", { withTimezone: true }),
    loginCount: integer("login_count").notNull().default(0),
    /** Total time with the site open (seconds), from the page's heartbeats. */
    secondsOnline: integer("seconds_online").notNull().default(0),
  },
  (t) => [
    uniqueIndex("users_email_idx").on(sql`lower(${t.email})`),
    uniqueIndex("users_username_idx").on(sql`lower(${t.username})`),
  ],
);

/** One-time codes sent by email: account verification and password reset. */
export const codes = pgTable(
  "codes",
  {
    id: serial("id").primaryKey(),
    userId: uuid("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    purpose: text("purpose").notNull(),
    codeHash: text("code_hash").notNull(),
    attempts: integer("attempts").notNull().default(0),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index("codes_user_idx").on(t.userId, t.purpose)],
);

/** Signed-in browsers. The cookie holds a random token; only its SHA-256 is stored. */
export const sessions = pgTable(
  "sessions",
  {
    id: text("id").primaryKey(),
    userId: uuid("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull().defaultNow(),
    ip: text("ip"),
    userAgent: text("user_agent"),
  },
  (t) => [index("sessions_user_idx").on(t.userId)],
);

/** Every sign-in attempt (for the admin panel and rate limits). */
export const logins = pgTable(
  "logins",
  {
    id: serial("id").primaryKey(),
    userId: uuid("user_id").references(() => users.id, { onDelete: "set null" }),
    email: text("email").notNull(),
    success: boolean("success").notNull(),
    ip: text("ip"),
    userAgent: text("user_agent"),
    at: timestamp("at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index("logins_at_idx").on(t.at), index("logins_user_idx").on(t.userId)],
);

/** Time on the site per player and day (seconds). */
export const activity = pgTable(
  "activity",
  {
    userId: uuid("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    day: date("day").notNull(),
    seconds: integer("seconds").notNull().default(0),
  },
  (t) => [primaryKey({ columns: [t.userId, t.day] }), index("activity_day_idx").on(t.day)],
);

/** The player's progress in the chess school (the app's key-value store), so it follows the account. */
export const userState = pgTable("user_state", {
  userId: uuid("user_id").primaryKey().references(() => users.id, { onDelete: "cascade" }),
  data: jsonb("data").$type<Record<string, string>>().notNull().default({}),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

/**
 * Games: online games between two players (kind "friend") and finished games against the Zorix bots
 * (kind "bot", recorded for statistics). Moves are UCI, space separated.
 */
export const games = pgTable(
  "games",
  {
    id: text("id").primaryKey(),
    kind: text("kind").notNull(),
    status: text("status").notNull(),
    creatorId: uuid("creator_id").references(() => users.id, { onDelete: "set null" }),
    inviteeId: uuid("invitee_id").references(() => users.id, { onDelete: "set null" }),
    whiteId: uuid("white_id").references(() => users.id, { onDelete: "set null" }),
    blackId: uuid("black_id").references(() => users.id, { onDelete: "set null" }),
    /** Colour the creator asked for: "white", "black" or "random". */
    creatorColor: text("creator_color").notNull().default("random"),
    botLevel: integer("bot_level"),
    moves: text("moves").notNull().default(""),
    fen: text("fen").notNull(),
    ply: integer("ply").notNull().default(0),
    result: text("result"),
    reason: text("reason"),
    /** Clock: 0 = no clock. */
    initialMs: integer("initial_ms").notNull().default(0),
    incrementMs: integer("increment_ms").notNull().default(0),
    whiteMs: integer("white_ms").notNull().default(0),
    blackMs: integer("black_ms").notNull().default(0),
    turnStartedAt: timestamp("turn_started_at", { withTimezone: true }),
    drawOffer: text("draw_offer"),
    rematchId: text("rematch_id"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
    finishedAt: timestamp("finished_at", { withTimezone: true }),
  },
  (t) => [
    index("games_white_idx").on(t.whiteId),
    index("games_black_idx").on(t.blackId),
    index("games_invitee_idx").on(t.inviteeId, t.status),
    index("games_created_idx").on(t.createdAt),
  ],
);

/** Site-wide switches set in the admin panel (announcement, registration open...). */
export const siteSettings = pgTable("site_settings", {
  key: text("key").primaryKey(),
  value: text("value").notNull(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

export type User = typeof users.$inferSelect;
export type Game = typeof games.$inferSelect;
