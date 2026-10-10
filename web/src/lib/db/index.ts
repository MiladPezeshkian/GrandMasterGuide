import "server-only";
import type { PgDatabase, PgQueryResultHKT } from "drizzle-orm/pg-core";
import * as schema from "./schema";

/**
 * The database, chosen by DATABASE_URL:
 *  - Neon (Vercel Postgres): the Neon HTTP driver, made for serverless functions;
 *  - any other Postgres: postgres.js over TCP;
 *  - "pglite:<dir>" (local development only): Postgres in WebAssembly, no server needed.
 * Tables are created by the migrations in ./drizzle (see migrate.ts), on first use.
 */
export type Db = PgDatabase<PgQueryResultHKT, typeof schema>;

// Kept on globalThis so development hot reloads reuse the connection (PGlite allows one per directory).
const g = globalThis as unknown as { __zorixDb?: Promise<Db> | null };

async function connect(): Promise<Db> {
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error("DATABASE_URL is not set");
  if (url.startsWith("pglite:")) {
    const { PGlite } = await import("@electric-sql/pglite");
    const { drizzle } = await import("drizzle-orm/pglite");
    const dir = url.slice("pglite:".length);
    if (dir) (await import("node:fs")).mkdirSync(dir, { recursive: true });
    const client = new PGlite(dir || undefined);
    return drizzle(client, { schema }) as unknown as Db;
  }
  if (/\.neon\.tech|neon\.build/.test(url)) {
    const { neon } = await import("@neondatabase/serverless");
    const { drizzle } = await import("drizzle-orm/neon-http");
    return drizzle(neon(url), { schema }) as unknown as Db;
  }
  const { default: postgres } = await import("postgres");
  const { drizzle } = await import("drizzle-orm/postgres-js");
  return drizzle(postgres(url, { max: 5, prepare: false }), { schema }) as unknown as Db;
}

/** The connected database, with the tables created or updated. */
export function db(): Promise<Db> {
  if (!g.__zorixDb) {
    g.__zorixDb = (async () => {
      const d = await connect();
      const { migrate } = await import("./migrate");
      await migrate(d);
      return d;
    })().catch((e) => {
      g.__zorixDb = null;
      throw e;
    });
  }
  return g.__zorixDb;
}

export { schema };
