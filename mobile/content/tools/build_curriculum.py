#!/usr/bin/env python3
"""
Builds the Zorix Chess curriculum and puzzle bank.

  build_curriculum.py STOCKFISH MINED_DIR MATES_JSON [courses...]

Writes mobile/shared/src/commonMain/composeResources/files/learn/<course>.json and files/puzzles.json.
Every exercise is validated with python-chess; engine-judged steps are cached in content/cache.
"""
import glob, json, os, sys, time
import chess, chess.engine
import cb
from rating import rate

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
OUT = os.path.join(ROOT, "shared/src/commonMain/composeResources/files")
CACHE = os.path.join(ROOT, "content/cache/accept.json")

def load_mined(mined_dir):
    seen, out = set(), []
    for path in sorted(glob.glob(os.path.join(mined_dir, "*.jsonl"))):
        for line in open(path):
            try:
                p = json.loads(line)
            except json.JSONDecodeError:
                continue
            key = p["fen"].split(" ")[0] + p["fen"].split(" ")[1]
            if key in seen:
                continue
            seen.add(key)
            # Re-validate the line.
            b = chess.Board(p["fen"])
            ok = True
            for u in p["moves"]:
                m = chess.Move.from_uci(u)
                if m not in b.legal_moves:
                    ok = False; break
                b.push(m)
            if ok:
                p["rating"] = rate(p)  # human-oriented difficulty instead of the miner's search-depth guess
                out.append(p)
    out.sort(key=lambda p: (p["rating"], p["fen"]))
    for i, p in enumerate(out):
        p["id"] = i + 1
    return out

def cached_accept(original):
    cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    def wrapper(b, margin, depth):
        key = f"{b.fen()}|{margin}|{depth}"
        if key not in cache:
            cache[key] = original(b, margin, depth)
            os.makedirs(os.path.dirname(CACHE), exist_ok=True)
            json.dump(cache, open(CACHE, "w"), indent=0, sort_keys=True)
        return cache[key]
    return wrapper

def main():
    engine_path, mined_dir, mates_path = sys.argv[1:4]
    only = set(sys.argv[4:])
    cb.ENGINE = chess.engine.SimpleEngine.popen_uci(engine_path)
    cb.ENGINE.configure({"Threads": 2, "Hash": 128})
    cb.engine_accept = cached_accept(cb.engine_accept)
    puzzles = load_mined(mined_dir)
    cb.DATA = {"mates": json.load(open(mates_path)), "puzzles": puzzles}
    cb.USED = set()
    print(f"{len(puzzles)} mined puzzles")
    os.makedirs(os.path.join(OUT, "learn"), exist_ok=True)
    import course_basics, course_concepts, course_mates
    modules = [course_basics, course_concepts, course_mates]
    for extra in ["course_tactics", "course_endgames", "course_openings", "course_strategy"]:
        try:
            modules.append(__import__(extra))
        except ImportError:
            pass
    index = []
    total = 0
    for mod in modules:
        t = time.time()
        c = mod.build()
        if only and c["id"] not in only:
            continue
        n = sum(len(ch["lessons"]) for ch in c["chapters"])
        total += n
        path = os.path.join(OUT, "learn", f"{c['id']}.json")
        json.dump(c, open(path, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        index.append(c["id"])
        print(f"{c['id']}: {n} lessons, {os.path.getsize(path) // 1024} KB, {time.time() - t:.0f}s")
    # Rated puzzle bank for the Puzzles tab: every verified puzzle (the trainer picks by rating and theme).
    bank = [{k: p[k] for k in ("id", "fen", "moves", "rating", "themes", "pre", "last") if k in p}
            for p in puzzles]
    json.dump(bank, open(os.path.join(OUT, "puzzles.json"), "w"), separators=(",", ":"))
    json.dump({"courses": index}, open(os.path.join(OUT, "learn", "index.json"), "w"))
    print(f"total lessons {total}, puzzle bank {len(bank)}")
    cb.ENGINE.quit()

if __name__ == "__main__":
    main()
