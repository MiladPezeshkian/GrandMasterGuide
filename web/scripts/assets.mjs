// Copies what the site serves from other parts of the repository:
//  - Stockfish 19 (WebAssembly, lite single-threaded build) from the npm package -> public/engine
//  - the app's lessons and puzzles -> public/content/files
//  - the app's piece artwork -> public/pieces
//  - the app's texts (en/fa/ckb strings.xml) -> src/i18n/app.generated.json
// The copies of the app's files are committed too, so a build without ../mobile still works.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const mobile = path.resolve(web, "../mobile");
const res = path.join(mobile, "shared/src/commonMain/composeResources");

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const e of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, e.name), b = path.join(to, e.name);
    if (e.isDirectory()) copyDir(a, b);
    else fs.copyFileSync(a, b);
  }
}

// Stockfish.
const sf = path.join(web, "node_modules/stockfish/bin");
fs.mkdirSync(path.join(web, "public/engine"), { recursive: true });
for (const f of ["stockfish-19-lite-single.js", "stockfish-19-lite-single.wasm"]) {
  fs.copyFileSync(path.join(sf, f), path.join(web, "public/engine", f));
}

if (fs.existsSync(res)) {
  // Lessons and puzzles.
  copyDir(path.join(res, "files"), path.join(web, "public/content/files"));
  // Pieces.
  copyDir(path.join(mobile, "branding/pieces"), path.join(web, "public/pieces"));
  fs.rmSync(path.join(web, "public/pieces/README.md"), { force: true });

  // Texts.
  const decode = (s) =>
    s.replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\n/g, "\n").replace(/\\@/g, "@").replace(/\\\?/g, "?")
      .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, "&");
  const out = {};
  for (const [lang, dir] of [["en", "values"], ["fa", "values-fa"], ["ckb", "values-ckb"]]) {
    const xml = fs.readFileSync(path.join(res, dir, "strings.xml"), "utf8");
    const strings = {};
    for (const m of xml.matchAll(/<string name="([^"]+)">([\s\S]*?)<\/string>/g)) strings[m[1]] = decode(m[2]);
    for (const m of xml.matchAll(/<string-array name="([^"]+)">([\s\S]*?)<\/string-array>/g)) {
      strings[m[1]] = [...m[2].matchAll(/<item>([\s\S]*?)<\/item>/g)].map((i) => decode(i[1]));
    }
    out[lang] = strings;
  }
  fs.mkdirSync(path.join(web, "src/i18n"), { recursive: true });
  fs.writeFileSync(path.join(web, "src/i18n/app.generated.json"), JSON.stringify(out));
}
console.log("assets ready");
