"use client";

import { Check, Clock, Copy, Swords, UserPlus, X } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { digits, type SiteKey } from "@/i18n/site";
import { PageHeader, Spinner } from "../school/ui";
import { useI18n } from "../site/Providers";
import type { OnlineGame } from "./types";

export const TIME_CONTROLS: [number, number][] = [
  [0, 0],
  [3, 2],
  [5, 0],
  [10, 0],
  [15, 10],
  [30, 0],
];

export function timeLabel(t: (k: SiteKey, v?: Record<string, string | number>) => string, lang: Parameters<typeof digits>[0], minutes: number, inc: number) {
  if (!minutes) return t("time_none");
  return inc ? t("time_min_inc", { m: digits(lang, minutes), i: digits(lang, inc) }) : t("time_min", { m: digits(lang, minutes) });
}

export function Friends() {
  const { t, lang } = useI18n();
  const router = useRouter();
  const [games, setGames] = useState<OnlineGame[] | null>(null);
  const [color, setColor] = useState<"white" | "black" | "random">("random");
  const [tc, setTc] = useState<[number, number]>([10, 0]);
  const [opponent, setOpponent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const r = await fetch("/api/games").then((x) => x.json()).catch(() => null);
    if (r?.ok) setGames(r.games);
  }, []);
  useEffect(() => {
    void load();
    const id = setInterval(() => document.visibilityState === "visible" && load(), 8000);
    return () => clearInterval(id);
  }, [load]);

  async function create() {
    setBusy(true);
    setError(null);
    const r = await fetch("/api/games", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ color, minutes: tc[0], increment: tc[1], opponent }) })
      .then((x) => x.json())
      .catch(() => ({ ok: false, error: "err_generic" }));
    setBusy(false);
    if (!r.ok) return setError(t(r.error === "err_not_found" ? "err_not_found" : (r.error as SiteKey) ?? "err_generic"));
    router.push(`/app/game/${r.id}`);
  }

  async function act(id: string, action: string) {
    await fetch(`/api/games/${id}/${action === "join" ? "join" : "action"}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action }) });
    if (action === "join") router.push(`/app/game/${id}`);
    else void load();
  }

  const invitations = games?.filter((g) => g.status === "waiting" && g.isInvitee) ?? [];
  const mine = games?.filter((g) => !(g.status === "waiting" && g.isInvitee)) ?? [];

  return (
    <div className="mx-auto max-w-5xl px-3 py-5 sm:px-6">
      <PageHeader title={t("friends_title")} subtitle={t("friends_sub")} />
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <section className="card relative overflow-hidden p-5">
          <div className="aurora absolute inset-0 opacity-40" />
          <div className="relative space-y-5">
            <h2 className="flex items-center gap-2 text-lg font-black">
              <Swords size={20} className="text-accent" /> {t("friends_new")}
            </h2>
            <div>
              <div className="mb-2 text-sm font-semibold">{t("friends_color")}</div>
              <div className="flex gap-2">
                {(["white", "random", "black"] as const).map((c) => (
                  <button key={c} className="chip" aria-pressed={color === c} onClick={() => setColor(c)}>
                    {c !== "random" && <span className={`h-3.5 w-3.5 rounded-full border border-line-strong ${c === "white" ? "bg-[#f4f4f6]" : "bg-[#16161a]"}`} />}
                    {t(c === "white" ? "color_white" : c === "black" ? "color_black" : "color_random")}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <div className="mb-2 flex items-center gap-2 text-sm font-semibold">
                <Clock size={15} /> {t("friends_time")}
              </div>
              <div className="flex flex-wrap gap-2">
                {TIME_CONTROLS.map(([m, i]) => (
                  <button key={`${m}+${i}`} className="chip" aria-pressed={tc[0] === m && tc[1] === i} onClick={() => setTc([m, i])}>
                    {timeLabel(t, lang, m, i)}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <div className="mb-2 flex items-center gap-2 text-sm font-semibold">
                <UserPlus size={15} /> {t("friends_opponent")}
              </div>
              <input className="input ltr" placeholder="@username" value={opponent} onChange={(e) => setOpponent(e.target.value.replace(/\s/g, ""))} />
            </div>
            {error && <p className="text-sm font-semibold text-danger">{error}</p>}
            <button className="btn btn-primary h-13 w-full" onClick={create} disabled={busy}>
              {busy && <Spinner />} {t("friends_create")}
            </button>
          </div>
        </section>

        <div className="space-y-5">
          <section>
            <h2 className="mb-3 text-sm font-bold text-accent">{t("friends_invitations")}</h2>
            {invitations.length === 0 ? (
              <p className="card p-4 text-sm text-dim">{t("friends_no_invitations")}</p>
            ) : (
              <div className="space-y-2">
                {invitations.map((g) => (
                  <div key={g.id} className="card flex items-center gap-3 p-4">
                    <div className="min-w-0 flex-1">
                      <div className="font-bold">{t("friends_invites_you", { name: g.creator ?? "?" })}</div>
                      <div className="text-xs text-dim">{timeLabel(t, lang, g.initialMs / 60000, g.incrementMs / 1000)}</div>
                    </div>
                    <button className="btn btn-primary" onClick={() => act(g.id, "join")}>
                      <Check size={16} /> {t("friends_accept")}
                    </button>
                    <button className="btn btn-ghost" onClick={() => act(g.id, "decline")}>
                      <X size={16} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </section>
          <section>
            <h2 className="mb-3 text-sm font-bold text-accent">{t("friends_games")}</h2>
            {games === null ? (
              <div className="skeleton h-20 rounded-2xl" />
            ) : mine.length === 0 ? (
              <p className="card p-4 text-sm text-dim">{t("friends_no_games")}</p>
            ) : (
              <div className="space-y-2">
                {mine.map((g) => (
                  <GameRow key={g.id} g={g} />
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

function GameRow({ g }: { g: OnlineGame }) {
  const { t, lang } = useI18n();
  const opponent = g.you === "white" ? g.black : g.you === "black" ? g.white : g.invitee;
  const yourTurn = g.status === "active" && g.you === (g.ply % 2 === 0 ? "white" : "black");
  const status =
    g.status === "active" ? t("status_active") : g.status === "waiting" ? t("status_waiting") : g.status === "finished" ? t("status_finished") : g.status === "declined" ? t("status_declined") : t("status_aborted");
  let result = "";
  if (g.status === "finished") result = g.result === "1/2-1/2" ? t("game_draw") : (g.result === "1-0") === (g.you === "white") ? t("game_you_won") : t("game_you_lost");
  return (
    <Link href={`/app/game/${g.id}`} className={`card flex items-center gap-3 p-4 transition hover:border-line-strong ${yourTurn ? "!border-accent" : ""}`}>
      <span className={`h-3 w-3 shrink-0 rounded-full ${g.status === "active" ? "pulse-ring bg-best" : g.status === "waiting" ? "bg-warn" : "bg-line-strong"}`} />
      <div className="min-w-0 flex-1">
        <div className="truncate font-bold">{opponent ? t("friends_vs", { name: opponent }) : t("friends_waiting")}</div>
        <div className="text-xs text-dim">
          {status} · {timeLabel(t, lang, g.initialMs / 60000, g.incrementMs / 1000)}
          {result && ` · ${result}`}
        </div>
      </div>
      {yourTurn && <span className="rounded-full bg-accent px-2.5 py-1 text-xs font-bold text-on-accent">{t("game_your_turn")}</span>}
      {g.status === "waiting" && g.isCreator && <Copy size={16} className="text-faint" />}
    </Link>
  );
}
