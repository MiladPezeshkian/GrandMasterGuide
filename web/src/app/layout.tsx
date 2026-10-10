import type { Metadata, Viewport } from "next";
import { Inter, Orbitron, Vazirmatn } from "next/font/google";
import { cookies } from "next/headers";
import { Providers } from "@/components/site/Providers";
import { getLang } from "@/i18n/server";
import { RTL } from "@/i18n/site";
import { siteSettings } from "@/lib/settings";
import "./globals.css";

const vazirmatn = Vazirmatn({ subsets: ["arabic", "latin"], variable: "--font-body", display: "swap" });
const inter = Inter({ subsets: ["latin"], variable: "--font-latin", display: "swap" });
const orbitron = Orbitron({ subsets: ["latin"], weight: ["700", "900"], variable: "--font-orbitron", display: "swap" });

const site = process.env.NEXT_PUBLIC_SITE_URL ?? "https://zorixchess.vercel.app";

export const metadata: Metadata = {
  metadataBase: new URL(site),
  title: { default: "GrandMaster Guide — Zorix Chess", template: "%s · GrandMaster Guide" },
  description:
    "A complete chess school with a coach that explains every move: 322 lessons, rated puzzles, 20 Zorix levels, Stockfish 19 analysis and live games with friends. Free, in Persian, Kurdish and English.",
  applicationName: "GrandMaster Guide",
  authors: [{ name: "Milad Pezeshkian" }],
  icons: { icon: "/brand/icon-512.png", apple: "/brand/icon-512.png" },
  openGraph: {
    title: "GrandMaster Guide — Zorix Chess",
    description: "Every move, explained. A free 3D chess school with a personal coach.",
    images: ["/brand/logo-dark.png"],
    type: "website",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#07070a" },
    { media: "(prefers-color-scheme: light)", color: "#f3f8fd" },
  ],
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const lang = await getLang();
  const theme = (await cookies()).get("zx_theme")?.value === "sky" ? "sky" : "zorix";
  const { announcement } = await siteSettings();
  return (
    <html lang={lang} dir={RTL.includes(lang) ? "rtl" : "ltr"} data-theme={theme} className={`${vazirmatn.variable} ${inter.variable} ${orbitron.variable}`}>
      <body>
        <Providers lang={lang} theme={theme}>
          {announcement ? (
            <div className="relative z-50 bg-accent px-4 py-2 text-center text-sm font-semibold text-on-accent">{announcement}</div>
          ) : null}
          {children}
        </Providers>
      </body>
    </html>
  );
}
