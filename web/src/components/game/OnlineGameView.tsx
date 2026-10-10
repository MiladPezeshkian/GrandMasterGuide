"use client";

import { Chess } from "chess.js";
import { Brain, Check, Copy, Flag, Handshake, Plus, RotateCcw, Share2, Swords, WifiOff, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { digits, type SiteKey } from "@/i18n/site";
import { MoveList } from "../school/MoveList";
import type { Ply } from "../school/types";
import { SchoolBoard, Spinner, ViewToggle } from "../school/ui";
import { useZorix } from "../school/ZorixProvider";
import { useI18n } from "../site/Providers";
import { timeLabel } from "./Friends";
import type { OnlineGame } from "./types";

const START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

function replay(moves: string[]) {
  const chess = new Chess(START);
  const plies: Ply[] = [];
  for (const m of moves) {
    try {
      const side = chess.turn() === "w" ? "white" : "black";
      const r = chess.move({ from: m.slice(0, 2), to: m.slice(2, 4), promotion: m[4] });
      plies.push({ uci: m, san: r.san, fen: chess.fen(), side, quality: null });
    } catch {
      break;
    }
  }
  return { chess, plies };
}

function formatClock(ms: number, lang: Parameters<typeof digits>[0]) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const m = Math.floor(total / 60), s = total % 60;
  const text = ms < 10_000 && ms > 0 ? (ms / 1000).toFixed(1) : `${m}:${String(s).padStart(2, "0")}`;
  return digits(lang, text);
}

export function OnlineGameView({ id }: { id: string }) {
  const { t, a, lang } = useI18n();
  const { core } = useZorix();
  const router = useRouter();
  const [game, setGame] = useState<OnlineGame | null>(null);
  const [error, setError] = useState<SiteKey | null>(null);
  const [offline, setOffline] = useState(false);
  const [pending, setPending] = useState<string | null>(null);
  const [promotion, setPromotion] = useState<{ from: string; to: string } | null>(null);
  const [view, setView] = useState<number | null>(null);
  const [confirmResign, setConfirmResign] = useState(false);
  const [received, setReceived] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const busy = useRef(false);

  const apply = useCallback((g: OnlineGame) => {
    setGame((old) => (old && old.ply > g.ply && old.status === g.status ? old : g));
    setReceived(Date.now());
  }, []);

  const load = useCallback(async () => {
    try {
      const r = await fetch(`/api/games/${id}`, { cache: "no-store" });
      const j = await r.json();
      setOffline(false);
      if (!j.ok) return setError(j.error ?? "game_not_found");
      if (!busy.current) apply(j.game);
    } catch {
      setOffline(true);
    }
  }, [id, apply]);

  // Live updates: every second while the game runs and the page is visible.
  useEffect(() => {
    void load();
    let timer: ReturnType<typeof setTimeout>;
    const loop = () => {
      const live = game?.status === "active" || game?.status === "waiting" || !game;
      const delay = document.visibilityState === "visible" ? (live ? 1000 : 8000) : 6000;
      timer = setTimeout(async () => {
        await load();
        loop();
      }, delay);
    };
    loop();
    return () => clearTimeout(timer);
  }, [load, game?.status]);

  useEffect(() => {
    if (!game?.clockRunning) return;
    const id = setInterval(() => setNow(Date.now()), 200);
    return () => clearInterval(id);
  }, [game?.clockRunning]);

  const moves = useMemo(() => [...(game?.moves ?? []), ...(pending ? [pending] : [])], [game?.moves, pending]);
  const { chess, plies } = useMemo(() => replay(moves), [moves]);
  const fen = chess.fen();
  useEffect(() => setView(null), [moves.length]);

  if (error) {
    return (
      <div className="mx-auto max-w-md px-6 py-24 text-center">
        <p className="text-lg font-bold">{t(error)}</p>
        <Link href="/app/friends" className="btn btn-primary mt-6">
          {t("friends_title")}
        </Link>
      </div>
    );
  }
  if (!game) return <div className="flex min-h-[60dvh] items-center justify-center"><Spinner size={28} /></div>;

  const you = game.you;
  const orientation = you ?? "white";
  const turn = chess.turn() === "w" ? "white" : "black";
  const yourTurn = game.status === "active" && you === turn && !pending;
  const shownFen = view != null ? (view === 0 ? START : plies[view - 1]?.fen ?? fen) : fen;
  const shownLast = view != null ? (view > 0 ? plies[view - 1]?.uci : null) : (plies[plies.length - 1]?.uci ?? null);

  const clocks = (() => {
    const c = { ...game.clocks };
    if (game.clockRunning) {
      const spent = now - received;
      const mover = game.ply % 2 === 0 ? "white" : "black";
      c[mover] = Math.max(0, c[mover] - spent);
    }
    return c;
  })();

  async function send(uci: string) {
    if (!game) return;
    busy.current = true;
    setPending(uci);
    try {
      const r = await fetch(`/api/games/${id}/move`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ uci, ply: game.ply }) });
      const j = await r.json();
      if (j.game) apply(j.game);
    } finally {
      setPending(null);
      busy.current = false;
    }
  }

  function onMove(from: string, to: string) {
    const piece = chess.get(from as never);
    if (piece?.type === "p" && (to[1] === "8" || to[1] === "1")) return setPromotion({ from, to });
    void send(from + to);
  }

  async function action(name: string) {
    const r = await fetch(`/api/games/${id}/action`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action: name }) }).then((x) => x.json());
    if (r.id) router.push(`/app/game/${r.id}`);
    else if (r.game) apply(r.game);
  }

  async function join() {
    const r = await fetch(`/api/games/${id}/join`, { method: "POST" }).then((x) => x.json());
    if (r.ok) apply(r.game);
    else setError(r.error ?? "err_generic");
  }

  const link = typeof window !== "undefined" ? `${window.location.origin}/g/${id}` : `/g/${id}`;
  const opponentName = you === "white" ? game.black : you === "black" ? game.white : null;
  const top = { name: orientation === "white" ? game.black : game.white, color: orientation === "white" ? "black" : "white" } as const;
  const bottom = { name: orientation === "white" ? game.white : game.black, color: orientation } as const;

  let banner: React.ReactNode = null;
  if (game.status === "finished" || game.status === "aborted") {
    const res = game.result;
    const title =
      game.status === "aborted" ? t("reason_aborted") : res === "1/2-1/2" ? t("game_draw") : you ? ((res === "1-0") === (you === "white") ? t("game_you_won") : t("game_you_lost")) : res === "1-0" ? t("game_white_won") : t("game_black_won");
    const reason = game.reason && game.status !== "aborted" ? t(`reason_${game.reason}` as SiteKey) : "";
    banner = (
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="card relative overflow-hidden p-5 text-center">
        <div className="aurora absolute inset-0 opacity-50" />
        <div className="relative">
          <div className="text-2xl font-black">{title}</div>
          {reason && <div className="mt-1 text-sm text-dim">{reason}</div>}
          <div className="mt-4 grid gap-2">
            {game.moves.length > 0 && (
              <button
                className="btn btn-primary"
                onClick={() => {
                  core?.reviewGame(START, game.moves, you ?? "");
                  router.push("/app/review");
                }}
              >
                <Brain size={17} /> {t("game_analyze")}
              </button>
            )}
            <div className="grid grid-cols-2 gap-2">
              {you && (
                <button className="btn btn-ghost" onClick={() => (game.rematchId ? router.push(`/app/game/${game.rematchId}`) : action("rematch"))}>
                  <RotateCcw size={16} /> {t("game_rematch")}
                </button>
              )}
              <Link href="/app/friends" className="btn btn-ghost">
                <Plus size={16} /> {t("game_new")}
              </Link>
            </div>
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-3 py-5 sm:px-6">
      <div className="mb-4 flex items-center gap-2">
        <Swords size={22} className="text-accent" />
        <h1 className="flex-1 truncate text-xl font-black sm:text-2xl">{opponentName ? t("friends_vs", { name: opponentName }) : t("friends_title")}</h1>
        <span className="chip">{timeLabel(t, lang, game.initialMs / 60000, game.incrementMs / 1000)}</span>
        <ViewToggle />
      </div>
      {offline && (
        <div className="mb-3 flex items-center gap-2 rounded-xl bg-warn/15 px-4 py-2 text-sm font-semibold text-warn">
          <WifiOff size={16} /> {t("game_connection")}
        </div>
      )}
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_23rem]">
        <div>
          <PlayerLine name={top.name} color={top.color} ms={game.initialMs ? clocks[top.color] : null} active={game.status === "active" && turn === top.color} lang={lang} you={you === top.color} />
          <div className="my-2">
            <SchoolBoard
              fen={shownFen}
              orientation={orientation}
              movable={yourTurn && view == null ? (you as "white" | "black") : "none"}
              lastMove={shownLast}
              check={view == null && chess.inCheck()}
              legalTargets={(sq) => chess.moves({ square: sq as never, verbose: true }).map((m) => m.to)}
              onMove={onMove}
              promotion={promotion ? { ...promotion, side: you } : null}
              onPromote={(p) => {
                const pr = promotion;
                setPromotion(null);
                if (p && pr) void send(pr.from + pr.to + p);
              }}
            />
          </div>
          <PlayerLine name={bottom.name} color={bottom.color} ms={game.initialMs ? clocks[bottom.color] : null} active={game.status === "active" && turn === bottom.color} lang={lang} you={you === bottom.color} />
        </div>

        <div className="space-y-4">
          {game.status === "waiting" && game.isCreator && (
            <div className="card space-y-4 p-5">
              <div>
                <h2 className="text-lg font-black">{t("game_invite_title")}</h2>
                <p className="mt-1 text-sm leading-6 text-dim">{game.invitee ? t("friends_waiting_for", { name: game.invitee }) : t("game_invite_sub")}</p>
              </div>
              <div className="ltr flex items-center gap-2 rounded-xl border border-line bg-surface-2 p-2 ps-3 text-sm">
                <span className="min-w-0 flex-1 truncate">{link}</span>
                <CopyButton text={link} />
              </div>
              {typeof navigator !== "undefined" && "share" in navigator && (
                <button className="btn btn-ghost w-full" onClick={() => navigator.share({ title: "GrandMaster Guide", text: t("game_share_text"), url: link }).catch(() => undefined)}>
                  <Share2 size={16} /> {t("game_share")}
                </button>
              )}
              <div className="flex items-center gap-2 text-sm text-dim">
                <Spinner /> {t("game_waiting")}
              </div>
              <button className="btn btn-quiet w-full" onClick={() => action("abort")}>
                <X size={16} /> {t("game_abort")}
              </button>
            </div>
          )}
          {game.status === "waiting" && !game.isCreator && (
            <div className="card space-y-4 p-5 text-center">
              <h2 className="text-lg font-black">{t("game_join_title", { name: game.creator ?? "" })}</h2>
              <button className="btn btn-primary w-full" onClick={join}>
                <Check size={17} /> {t("game_join_btn")}
              </button>
            </div>
          )}
          {game.status === "active" && (
            <div className="card p-4">
              <div className="text-center text-lg font-black">{you ? (yourTurn ? t("game_your_turn") : t("game_their_turn")) : a(turn === "white" ? "turn_white" : "turn_black")}</div>
              {game.drawOffer && you && game.drawOffer !== you && (
                <div className="mt-3 rounded-xl bg-surface-2 p-3 text-center">
                  <div className="text-sm font-semibold">{t("game_draw_offer_received")}</div>
                  <div className="mt-2 grid grid-cols-2 gap-2">
                    <button className="btn btn-primary" onClick={() => action("draw")}>
                      {t("game_accept_draw")}
                    </button>
                    <button className="btn btn-ghost" onClick={() => action("decline-draw")}>
                      {t("game_decline_draw")}
                    </button>
                  </div>
                </div>
              )}
              {you && (
                <div className="mt-3 grid grid-cols-2 gap-2">
                  {game.ply < 2 ? (
                    <button className="btn btn-ghost col-span-2" onClick={() => action("abort")}>
                      <X size={16} /> {t("game_abort")}
                    </button>
                  ) : (
                    <>
                      <button className="btn btn-ghost" onClick={() => action("draw")} disabled={game.drawOffer === you}>
                        <Handshake size={16} /> {game.drawOffer === you ? t("game_draw_offered") : t("game_offer_draw")}
                      </button>
                      <button className="btn btn-ghost !text-danger" onClick={() => setConfirmResign(true)}>
                        <Flag size={16} /> {t("game_resign")}
                      </button>
                    </>
                  )}
                </div>
              )}
            </div>
          )}
          {banner}
          <MoveList plies={plies} current={view ?? plies.length} onGoTo={(n) => setView(n >= plies.length ? null : n)} />
        </div>
      </div>

      <AnimatePresence>
        {confirmResign && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4" onClick={() => setConfirmResign(false)}>
            <div className="card w-full max-w-sm p-6" onClick={(e) => e.stopPropagation()}>
              <h2 className="text-xl font-black">{t("game_resign_confirm")}</h2>
              <div className="mt-6 flex justify-end gap-2">
                <button className="btn btn-ghost" onClick={() => setConfirmResign(false)}>
                  {a("action_cancel")}
                </button>
                <button className="btn btn-primary" onClick={() => { void action("resign"); setConfirmResign(false); }}>
                  {t("game_resign")}
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function PlayerLine({ name, color, ms, active, lang, you }: { name: string | null; color: "white" | "black"; ms: number | null; active: boolean; lang: Parameters<typeof digits>[0]; you: boolean }) {
  const { t } = useI18n();
  const low = ms != null && ms < 20_000;
  return (
    <div className="flex items-center gap-3 px-1">
      <span className={`h-7 w-7 rounded-full border border-line-strong ${color === "white" ? "bg-[#f4f4f6]" : "bg-[#16161a]"}`} />
      <span className="truncate font-bold">{name ?? "…"}</span>
      {you && <span className="text-xs text-faint">({t("game_you")})</span>}
      <span className="flex-1" />
      {ms != null && (
        <span className={`tnum ltr rounded-xl px-3 py-1.5 text-lg font-black transition ${active ? (low ? "bg-danger text-white" : "bg-accent text-on-accent") : "bg-surface-2 text-dim"}`}>{formatClock(ms, lang)}</span>
      )}
    </div>
  );
}

function CopyButton({ text }: { text: string }) {
  const { t } = useI18n();
  const [done, setDone] = useState(false);
  return (
    <button
      className="btn btn-primary h-9 min-h-0 px-3 text-sm"
      onClick={async () => {
        await navigator.clipboard.writeText(text).catch(() => undefined);
        setDone(true);
        setTimeout(() => setDone(false), 1800);
      }}
    >
      {done ? <Check size={15} /> : <Copy size={15} />} {done ? t("game_copied") : t("game_copy")}
    </button>
  );
}
