import "server-only";
import nodemailer, { type Transporter } from "nodemailer";
import { siteText, RTL, type Lang } from "@/i18n/site";

let transport: Transporter | null = null;

function mailer() {
  if (transport) return transport;
  const host = process.env.SMTP_HOST;
  if (!host || !process.env.SMTP_USER || !process.env.SMTP_PASS) return null;
  const port = Number(process.env.SMTP_PORT ?? 465);
  transport = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS },
  });
  return transport;
}

function escape(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

/** Sends a verification or reset code. Without SMTP settings (local development) the code is logged. */
export async function sendCode(to: string, name: string, code: string, purpose: "verify" | "reset", lang: Lang): Promise<boolean> {
  const t = (k: Parameters<typeof siteText>[1], v?: Record<string, string>) => siteText(lang, k, v);
  const subject = t(purpose === "verify" ? "mail_verify_subject" : "mail_reset_subject", { code });
  const title = t(purpose === "verify" ? "mail_verify_title" : "mail_reset_title");
  const body = t(purpose === "verify" ? "mail_verify_body" : "mail_reset_body", { name });
  const dir = RTL.includes(lang) ? "rtl" : "ltr";
  const site = process.env.NEXT_PUBLIC_SITE_URL ?? "";
  const html = `<!doctype html><html dir="${dir}"><body style="margin:0;background:#0b0b0e;padding:32px 12px;font-family:Tahoma,Segoe UI,Arial,sans-serif">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">
<table role="presentation" width="100%" style="max-width:480px;background:#15151b;border-radius:20px;border:1px solid #2a2a33;overflow:hidden" cellpadding="0" cellspacing="0">
<tr><td style="height:4px;background:linear-gradient(90deg,#ff4b55,#e3202b,#7a000a)"></td></tr>
<tr><td style="padding:28px 28px 8px;text-align:center">
${site ? `<img src="${site}/brand/emblem.png" width="96" alt="Zorix" style="display:inline-block">` : ""}
<div style="font:700 13px/1.4 Arial,sans-serif;letter-spacing:4px;color:#e3202b;margin-top:10px">GRANDMASTER GUIDE</div>
<h1 style="color:#f1f1f4;font-size:22px;margin:18px 0 8px">${escape(title)}</h1>
<p style="color:#a7a8b3;font-size:15px;line-height:1.7;margin:0 0 20px">${escape(body)}</p>
<div dir="ltr" style="display:inline-block;background:#0e0e11;border:1px solid #3a3a45;border-radius:14px;padding:14px 22px;font:700 34px/1 Consolas,Menlo,monospace;letter-spacing:10px;color:#fff">${code}</div>
<p style="color:#7c7c86;font-size:12px;line-height:1.7;margin:22px 0 6px">${escape(t("mail_expires"))}</p>
</td></tr>
<tr><td style="padding:14px;text-align:center;color:#55555f;font-size:11px;border-top:1px solid #23232b">Zorix · ${escape(t("made_by"))}</td></tr>
</table></td></tr></table></body></html>`;
  const m = mailer();
  if (!m) {
    if (process.env.NODE_ENV !== "production") {
      console.log(`[mail] ${purpose} code for ${to}: ${code}`);
      return true;
    }
    console.error("SMTP is not configured: set SMTP_HOST, SMTP_USER and SMTP_PASS");
    return false;
  }
  try {
    await m.sendMail({
      from: process.env.MAIL_FROM ?? process.env.SMTP_USER,
      to,
      subject,
      text: `${title}\n\n${body}\n\n${code}\n\n${t("mail_expires")}`,
      html,
    });
    return true;
  } catch (e) {
    console.error("sending mail failed", e);
    return false;
  }
}
