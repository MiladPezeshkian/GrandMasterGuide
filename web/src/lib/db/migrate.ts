import "server-only";
import { sql } from "drizzle-orm";
import type { Db } from "./index";
import { MIGRATIONS } from "./migrations.generated";

/** Applies the migrations that have not run yet (each once, recorded in zorix_migrations). */
export async function migrate(db: Db) {
  await db.execute(sql`CREATE TABLE IF NOT EXISTS zorix_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())`);
  const done = await db.execute(sql`SELECT name FROM zorix_migrations`);
  const rows = (Array.isArray(done) ? done : (done as { rows: { name: string }[] }).rows) as { name: string }[];
  const applied = new Set(rows.map((r) => r.name));
  for (const m of MIGRATIONS) {
    if (applied.has(m.name)) continue;
    for (const statement of m.statements) await db.execute(sql.raw(statement));
    // Two cold starts may race: the second insert is ignored, its statements fail harmlessly before it.
    await db.execute(sql`INSERT INTO zorix_migrations (name) VALUES (${m.name}) ON CONFLICT DO NOTHING`);
  }
}
