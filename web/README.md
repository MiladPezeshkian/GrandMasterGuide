# GrandMaster Guide — website

The chess school of the GrandMaster Guide app on the web, by Zorix. Designed and built by Milad Pezeshkian.

- **3D board** (or flat 2D, per player), Zorix (dark) and Sky (light) styles, Persian / Sorani Kurdish / English.
- **The app's own chess logic in the browser:** the rules, the coach and its explanations, the 322 lessons, the
  rated puzzles, the 20 Zorix levels and game review are the app's Kotlin code (`../mobile/shared`) compiled to
  JavaScript (`core/` → `src/core/`). **Stockfish 19** (WebAssembly) runs in a Web Worker. The site therefore
  rates and explains moves exactly like the app.
- **The coach's voice:** Sorani Kurdish is the app's own neural voice **Vekol**, run in the browser with ONNX Runtime
  Web (`public/voices/`, 39 MB, downloaded once and kept in the browser's cache). Persian and English use the
  browser's voices (Microsoft Edge and Android have natural ones). As in the app, "Zorix is thinking…" shows until
  the voice is ready, then the text and the voice come together.
- **Accounts:** sign-up with an emailed 6-digit code, sign-in by email or username, password reset by code.
  Everything is free once signed in; progress is saved to the account.
- **Play with friends:** a game link (`/g/<id>`) or an invitation by username; clocks, draw offers, resign, abort,
  rematch, and "Analyse with the coach" afterwards.
- **Admin panel** (`/admin`, one administrator from the environment): players, sign-ins, time on the site,
  games, 30-day charts, blocking/deleting players, an announcement banner, closing sign-ups.

## Deploy on Vercel

1. **Import the repository** in Vercel (Add New → Project → `MiladPezeshkian/GrandMasterGuide`).
   - *Root Directory:* `web` · *Framework:* Next.js (detected) · *Project name:* `zorixchess` → `https://zorixchess.vercel.app`.
2. **Database:** in the project, Storage → Create → **Neon (Postgres)** → connect it to the project. This sets
   `DATABASE_URL`. The tables are created automatically on the first request.
3. **Environment variables** (Settings → Environment Variables):

   | Name | Value |
   |---|---|
   | `ADMIN_EMAIL` | the administrator's email |
   | `ADMIN_PASSWORD` | the administrator's password (never put it in the code) |
   | `SMTP_HOST` | `smtp.gmail.com` |
   | `SMTP_PORT` | `465` |
   | `SMTP_USER` | the Gmail address that sends the codes |
   | `SMTP_PASS` | a Gmail **App Password** (Google Account → Security → 2-Step Verification → App passwords) |
   | `MAIL_FROM` | e.g. `Zorix Chess <you@gmail.com>` |
   | `NEXT_PUBLIC_SITE_URL` | `https://zorixchess.vercel.app` (or your domain) |

4. **Deploy** (or redeploy after adding the variables). Signing in with `ADMIN_EMAIL` / `ADMIN_PASSWORD` opens `/admin`.

Any other Postgres works too (`DATABASE_URL=postgres://…`, used through postgres.js).

## Develop

```bash
cd web
npm install
cp .env.example .env.local      # DATABASE_URL=pglite:./.data/pglite runs Postgres in-process, no server needed
npm run dev                     # http://localhost:3000 — without SMTP, codes are printed in the terminal
npm run typecheck && npm run build
```

After changing the app's chess logic (`mobile/shared/src/commonMain`), rebuild the JavaScript core and commit it:

```bash
gradle -p web/core build        # writes web/src/core/zorix-core.mjs
```

After changing `src/lib/db/schema.ts`: `npm run db:generate` (writes a migration and embeds it).

## Credits

Stockfish 19 (GPL-3.0) and Stockfish.js (GPL-3.0); "The King - 3DDecember Day5" by Batuhan13 (CC BY 4.0);
cburnett pieces (BSD); Vazirmatn, Orbitron, Inter (OFL); three.js, React Three Fiber, drei (MIT); Lucide (ISC);
ONNX Runtime Web (MIT). The Kurdish voice "Vekol" is by Darvan Shvan (Revge), CC BY-NC 4.0, used with the
author's permission in this free school (see `public/voices/ckb/NOTICE.md`).
See `/credits` on the site. The site is free software under the GPL-3.0.
