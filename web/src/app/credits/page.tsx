import type { Metadata } from "next";
import { Footer } from "@/components/site/Footer";
import { Header } from "@/components/site/Header";
import { serverT } from "@/i18n/server";

export const metadata: Metadata = { title: "Credits" };

const CREDITS: { name: string; by: string; license: string; url: string; use: string }[] = [
  { name: "Stockfish 19", by: "The Stockfish developers", license: "GPL-3.0", url: "https://stockfishchess.org", use: "Chess engine" },
  { name: "Stockfish.js (WebAssembly)", by: "Chess.com, LLC (nmrugg)", license: "GPL-3.0", url: "https://github.com/nmrugg/stockfish.js", use: "Engine in the browser" },
  {
    name: "“The King - 3DDecember Day5”",
    by: "Batuhan13",
    license: "CC BY 4.0",
    url: "https://sketchfab.com/3d-models/the-king-3ddecember-day5-a94271eca92f40cea88662184b87d2c2",
    use: "3D king on the home page (textures resized)",
  },
  { name: "cburnett chess pieces", by: "Colin M.L. Burnett", license: "BSD", url: "https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces", use: "2D pieces" },
  { name: "Vazirmatn", by: "Saber Rastikerdar", license: "OFL-1.1", url: "https://github.com/rastikerdar/vazirmatn", use: "Persian and Kurdish text" },
  { name: "Orbitron", by: "Matt McInerney", license: "OFL-1.1", url: "https://fonts.google.com/specimen/Orbitron", use: "Wordmark" },
  { name: "Inter", by: "Rasmus Andersson", license: "OFL-1.1", url: "https://rsms.me/inter/", use: "Latin text" },
  { name: "three.js, React Three Fiber, drei", by: "mrdoob, Poimandres", license: "MIT", url: "https://github.com/pmndrs/react-three-fiber", use: "3D" },
  { name: "Lucide", by: "Lucide contributors", license: "ISC", url: "https://lucide.dev", use: "Icons" },
  { name: "ONNX Runtime Web", by: "Microsoft", license: "MIT", url: "https://onnxruntime.ai", use: "Neural voice in the browser" },
];

export default async function Credits() {
  const { t } = await serverT();
  return (
    <>
      <Header />
      <main className="mx-auto max-w-4xl px-6 py-16">
        <h1 className="text-3xl font-black sm:text-4xl">{t("footer_credits")}</h1>
        <p className="mt-3 text-dim">{t("made_by")}</p>
        <div className="mt-10 grid gap-3">
          {CREDITS.map((c) => (
            <a key={c.name} href={c.url} target="_blank" rel="noopener" className="card ltr flex flex-wrap items-center justify-between gap-3 p-5 text-left transition hover:border-line-strong">
              <div>
                <div className="font-bold">{c.name}</div>
                <div className="text-sm text-dim">
                  {c.by} · {c.use}
                </div>
              </div>
              <span className="chip">{c.license}</span>
            </a>
          ))}
        </div>
        <p className="ltr mt-10 text-left text-sm text-faint">
          Source code (GPL-3.0):{" "}
          <a className="underline" href="https://github.com/MiladPezeshkian/GrandMasterGuide" target="_blank" rel="noopener">
            github.com/MiladPezeshkian/GrandMasterGuide
          </a>
        </p>
      </main>
      <Footer />
    </>
  );
}
