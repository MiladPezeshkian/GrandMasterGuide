import "server-only";
import { NextResponse } from "next/server";
import type { SiteKey } from "@/i18n/site";

export function ok(data: Record<string, unknown> = {}) {
  return NextResponse.json({ ok: true, ...data });
}

/** An error the page shows in the visitor's language ([error] is a site text key). */
export function fail(error: SiteKey, status = 400, data: Record<string, unknown> = {}) {
  return NextResponse.json({ ok: false, error, ...data }, { status });
}

export async function body<T = Record<string, unknown>>(req: Request): Promise<T> {
  try {
    return (await req.json()) as T;
  } catch {
    return {} as T;
  }
}

export const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;
export const USERNAME_RE = /^[A-Za-z0-9_]{3,20}$/;
