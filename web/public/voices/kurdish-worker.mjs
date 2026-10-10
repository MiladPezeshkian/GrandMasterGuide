// The Kurdish coach voice in the browser: "Vekol" (Darvan Shvan / Revge, CC BY-NC 4.0, see ckb/NOTICE.md),
// a Piper VITS model that reads Sorani letters directly, run with ONNX Runtime Web. Same settings as the
// app: noise 0.667, speed 0.95, rhythm 0.35, and a second render when the audio is too long for its text
// (a rare babbling tail).
import * as ort from "/ort/ort.wasm.min.mjs";

ort.env.wasm.wasmPaths = "/ort/";
ort.env.wasm.numThreads = self.crossOriginIsolated ? Math.min(4, navigator.hardwareConcurrency || 2) : 1;

const MODEL = "/voices/ckb/model.onnx";
const TOKENS = "/voices/ckb/tokens.json";
const CACHE = "zx-voices-v1";
const SPEED = 0.95;
const SECONDS_PER_LETTER = 0.075;

let session = null;
let tokens = null;
let loading = null;
let generation = 0;

/** A file from the browser's cache, downloaded once. */
async function cached(url) {
  try {
    const cache = await caches.open(CACHE);
    let r = await cache.match(url);
    if (!r) {
      r = await fetch(url);
      if (!r.ok) throw new Error(`${url}: ${r.status}`);
      await cache.put(url, r.clone());
    }
    return r;
  } catch {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`${url}: ${r.status}`);
    return r;
  }
}

function load() {
  if (!loading) {
    loading = (async () => {
      tokens = await (await cached(TOKENS)).json();
      const model = await (await cached(MODEL)).arrayBuffer();
      session = await ort.InferenceSession.create(model, { executionProviders: ["wasm"], graphOptimizationLevel: "all" });
      postMessage({ type: "ready" });
    })().catch((e) => {
      loading = null;
      postMessage({ type: "error", message: String(e) });
      throw e;
    });
  }
  return loading;
}

async function synth(text) {
  // Piper input: ^ _ letter _ letter _ … $
  const ids = [1, 0];
  for (const ch of text) {
    const id = tokens[ch];
    if (id != null) ids.push(id, 0);
  }
  ids.push(2);
  const feeds = {
    input: new ort.Tensor("int64", BigInt64Array.from(ids, BigInt), [1, ids.length]),
    input_lengths: new ort.Tensor("int64", BigInt64Array.from([BigInt(ids.length)]), [1]),
    scales: new ort.Tensor("float32", Float32Array.from([0.667, 1 / SPEED, 0.35]), [3]),
    sid: new ort.Tensor("int64", BigInt64Array.from([0n]), [1]),
  };
  const r = await session.run(feeds);
  return new Float32Array(r.output.data);
}

onmessage = async (e) => {
  const m = e.data;
  if (m.type === "load") {
    load().catch(() => {});
  } else if (m.type === "stop") {
    generation++;
  } else if (m.type === "speak") {
    const gen = ++generation;
    try {
      await load();
    } catch {
      postMessage({ type: "done", id: m.id });
      return;
    }
    for (const piece of m.pieces) {
      if (gen !== generation) return;
      let audio = await synth(piece);
      const limit = (piece.length * SECONDS_PER_LETTER) / SPEED + 1.2;
      if (audio.length / 22050 > limit && gen === generation) {
        const again = await synth(piece);
        if (again.length < audio.length) audio = again;
      }
      if (gen !== generation) return;
      postMessage({ type: "audio", id: m.id, audio }, [audio.buffer]);
    }
    postMessage({ type: "done", id: m.id });
  }
};
