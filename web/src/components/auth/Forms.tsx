"use client";

import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import type { SiteKey } from "@/i18n/site";
import { useI18n } from "../site/Providers";
import { CodeInput, Field, FormError, FormOk, PasswordInput, post } from "./fields";

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;

/** A same-site path to return to after signing in (an invite link), or null. */
function useNext(): string | null {
  const next = useSearchParams().get("next");
  return next && next.startsWith("/") && !next.startsWith("//") ? next : null;
}

const USERNAME_RE = /^[A-Za-z0-9_]{3,20}$/;

function Title({ title, sub }: { title: string; sub: string }) {
  return (
    <div className="mb-8">
      <h1 className="text-3xl font-black tracking-tight sm:text-4xl">{title}</h1>
      <p className="mt-2 leading-7 text-dim">{sub}</p>
    </div>
  );
}

function Submit({ busy, label }: { busy: boolean; label: string }) {
  return (
    <button type="submit" className="btn btn-primary h-13 w-full text-base" disabled={busy}>
      {busy && <Loader2 size={18} className="animate-spin" />} {label}
    </button>
  );
}

export function RegisterForm() {
  const { t, lang } = useI18n();
  const router = useRouter();
  const next = useNext();
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!EMAIL_RE.test(email.trim())) return setError(t("err_invalid_email"));
    if (!USERNAME_RE.test(username.trim())) return setError(t("err_username"));
    if (password.length < 8) return setError(t("err_password_short"));
    if (password !== confirm) return setError(t("err_password_mismatch"));
    setBusy(true);
    const r = await post("/api/auth/register", { email, username, password, lang });
    setBusy(false);
    if (!r.ok) return setError(t((r.error ?? "err_generic") as SiteKey));
    router.push(`/verify?email=${encodeURIComponent(email.trim().toLowerCase())}${next ? `&next=${encodeURIComponent(next)}` : ""}`);
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Title title={t("auth_register_title")} sub={t("auth_register_sub")} />
      <Field label={t("auth_email")}>
        <input className="input" dir="ltr" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </Field>
      <Field label={t("auth_username")} hint={t("auth_username_hint")}>
        <input className="input" dir="ltr" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value.replace(/\s/g, ""))} maxLength={20} required />
      </Field>
      <Field label={t("auth_password")} hint={t("auth_password_hint")}>
        <PasswordInput autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </Field>
      <Field label={t("auth_password_confirm")}>
        <PasswordInput autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
      </Field>
      <FormError text={error} />
      <Submit busy={busy} label={t("auth_register_btn")} />
      <p className="pt-2 text-center text-sm text-dim">
        {t("auth_have_account")}{" "}
        <Link href={next ? `/login?next=${encodeURIComponent(next)}` : "/login"} className="font-bold text-accent hover:underline">
          {t("nav_login")}
        </Link>
      </p>
    </form>
  );
}

export function LoginForm() {
  const { t, lang } = useI18n();
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const next = useNext();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!login.trim() || !password) return setError(t("err_wrong_credentials"));
    setBusy(true);
    const r = await post("/api/auth/login", { login, password, lang });
    if (!r.ok) {
      setBusy(false);
      return setError(t((r.error ?? "err_generic") as SiteKey));
    }
    const target = r.redirect === "/app" && next ? next : r.redirect?.startsWith("/verify") && next ? `${r.redirect}&next=${encodeURIComponent(next)}` : (r.redirect ?? "/app");
    window.location.href = target;
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Title title={t("auth_login_title")} sub={t("auth_login_sub")} />
      <Field label={t("auth_email_or_username")}>
        <input className="input" dir="ltr" autoComplete="username" value={login} onChange={(e) => setLogin(e.target.value)} required />
      </Field>
      <Field label={t("auth_password")}>
        <PasswordInput autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </Field>
      <div className="text-end">
        <Link href="/forgot" className="text-sm font-semibold text-dim hover:text-accent">
          {t("auth_forgot")}
        </Link>
      </div>
      <FormError text={error} />
      <Submit busy={busy} label={t("auth_login_btn")} />
      <p className="pt-2 text-center text-sm text-dim">
        {t("auth_no_account")}{" "}
        <Link href={next ? `/register?next=${encodeURIComponent(next)}` : "/register"} className="font-bold text-accent hover:underline">
          {t("nav_register")}
        </Link>
      </p>
    </form>
  );
}

function useCountdown(seconds: number) {
  const [left, setLeft] = useState(seconds);
  useEffect(() => {
    if (left <= 0) return;
    const id = setTimeout(() => setLeft((l) => l - 1), 1000);
    return () => clearTimeout(id);
  }, [left]);
  return [left, () => setLeft(seconds)] as const;
}

export function VerifyForm() {
  const { t, lang } = useI18n();
  const email = useSearchParams().get("email") ?? "";
  const next = useNext();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [okText, setOk] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [left, restart] = useCountdown(45);

  async function submit(e?: React.FormEvent) {
    e?.preventDefault();
    if (code.length !== 6) return;
    setError(null);
    setBusy(true);
    const r = await post("/api/auth/verify", { email, code });
    if (!r.ok) {
      setBusy(false);
      return setError(t((r.error ?? "err_generic") as SiteKey));
    }
    window.location.href = next ?? r.redirect ?? "/app";
  }

  useEffect(() => {
    if (code.length === 6) void submit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code]);

  return (
    <form onSubmit={submit} className="space-y-5">
      <Title title={t("auth_verify_title")} sub={t("auth_verify_sub", { email })} />
      <CodeInput value={code} onChange={setCode} />
      <p className="text-xs text-faint">{t("auth_spam_note")}</p>
      <FormError text={error} />
      <FormOk text={okText} />
      <Submit busy={busy} label={t("auth_verify_btn")} />
      <button
        type="button"
        className="btn btn-quiet w-full"
        disabled={left > 0}
        onClick={async () => {
          setError(null);
          const r = await post("/api/auth/resend", { email, purpose: "verify", lang });
          if (r.ok) {
            setOk(t("ok_code_sent"));
            restart();
          } else setError(t((r.error ?? "err_generic") as SiteKey));
        }}
      >
        {left > 0 ? t("auth_resend_in", { s: left }) : t("auth_resend")}
      </button>
    </form>
  );
}

export function ForgotForm() {
  const { t, lang } = useI18n();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!EMAIL_RE.test(email.trim())) return setError(t("err_invalid_email"));
    setBusy(true);
    const r = await post("/api/auth/forgot", { email, lang });
    setBusy(false);
    if (!r.ok) return setError(t((r.error ?? "err_generic") as SiteKey));
    router.push(`/reset?email=${encodeURIComponent(email.trim().toLowerCase())}`);
  }
  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Title title={t("auth_forgot_title")} sub={t("auth_forgot_sub")} />
      <Field label={t("auth_email")}>
        <input className="input" dir="ltr" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </Field>
      <FormError text={error} />
      <Submit busy={busy} label={t("auth_send_code")} />
      <p className="pt-2 text-center text-sm">
        <Link href="/login" className="font-semibold text-dim hover:text-accent">
          {t("auth_back_login")}
        </Link>
      </p>
    </form>
  );
}

export function ResetForm() {
  const { t, lang } = useI18n();
  const email = useSearchParams().get("email") ?? "";
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [okText, setOk] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [left, restart] = useCountdown(45);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (code.length !== 6) return setError(t("err_code_wrong"));
    if (password.length < 8) return setError(t("err_password_short"));
    if (password !== confirm) return setError(t("err_password_mismatch"));
    setBusy(true);
    const r = await post("/api/auth/reset", { email, code, password });
    if (!r.ok) {
      setBusy(false);
      return setError(t((r.error ?? "err_generic") as SiteKey));
    }
    window.location.href = r.redirect ?? "/app";
  }
  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Title title={t("auth_reset_title")} sub={t("auth_reset_sub", { email })} />
      <CodeInput value={code} onChange={setCode} />
      <Field label={t("auth_new_password")} hint={t("auth_password_hint")}>
        <PasswordInput autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </Field>
      <Field label={t("auth_password_confirm")}>
        <PasswordInput autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
      </Field>
      <FormError text={error} />
      <FormOk text={okText} />
      <Submit busy={busy} label={t("auth_save_password")} />
      <button
        type="button"
        className="btn btn-quiet w-full"
        disabled={left > 0}
        onClick={async () => {
          const r = await post("/api/auth/resend", { email, purpose: "reset", lang });
          if (r.ok) {
            setOk(t("ok_code_sent"));
            restart();
          } else setError(t((r.error ?? "err_generic") as SiteKey));
        }}
      >
        {left > 0 ? t("auth_resend_in", { s: left }) : t("auth_resend")}
      </button>
    </form>
  );
}
