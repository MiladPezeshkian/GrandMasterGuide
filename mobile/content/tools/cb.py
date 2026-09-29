"""
Curriculum builder core: step constructors that validate every exercise with python-chess
(and Stockfish where a position must be judged), plus random exercise generators.
"""
import itertools, json, random
import chess, chess.engine

RNG = random.Random(2026)
ENGINE = None  # set by build_curriculum.py

def T(en, fa, ckb):
    return {"en": en, "fa": fa, "ckb": ckb}

class ContentError(Exception):
    pass

# ---------------------------------------------------------------- structure

def lesson(lid, title, intro, level, steps):
    if not steps:
        raise ContentError(f"lesson {lid} has no steps")
    return {"id": lid, "title": title, "intro": intro, "level": level, "steps": steps}

def chapter(cid, title, lessons):
    return {"id": cid, "title": title, "lessons": lessons}

def course(cid, title, desc, chapters):
    return {"id": cid, "title": title, "desc": desc, "chapters": chapters}

# ---------------------------------------------------------------- steps

def theory(text, fen=None, arrows=(), marks=()):
    if fen:
        chess.Board(fen)
    return {"type": "theory", "text": text, **({"fen": fen} if fen else {}), "arrows": list(arrows), "marks": list(marks)}

def quiz(question, options, answer, explain=None, fen=None, marks=()):
    assert 0 <= answer < len(options)
    if fen:
        chess.Board(fen)
    d = {"type": "quiz", "prompt": question, "options": options, "answer": answer, "marks": list(marks)}
    if explain:
        d["explain"] = explain
    if fen:
        d["fen"] = fen
    return d

def square(count, prompt, black=False):
    return {"type": "square", "count": count, "prompt": prompt, "black": black}

def goal(fen, g, prompt):
    b = chess.Board(fen)
    assert b.is_valid(), fen
    ok = [m for m in b.legal_moves if satisfies(b, m, g)]
    if not ok:
        raise ContentError(f"goal {g} impossible in {fen}")
    return {"type": "goal", "fen": fen, "goal": g, "prompt": prompt}

def mate(fen, n, prompt):
    b = chess.Board(fen)
    assert b.is_valid(), fen
    if not forces_mate_any(b, n):
        raise ContentError(f"no mate in {n}: {fen}")
    return {"type": "mate", "fen": fen, "n": n, "prompt": prompt}

def puzzle(p, prompt):
    b = chess.Board(p["fen"])
    for u in p["moves"]:
        m = chess.Move.from_uci(u)
        if m not in b.legal_moves:
            raise ContentError(f"illegal puzzle move {u} in {b.fen()}")
        b.push(m)
    d = {"type": "puzzle", "fen": p["fen"], "moves": p["moves"], "prompt": prompt, "rating": p.get("rating", 1000), "themes": p.get("themes", [])}
    if p.get("pre") and p.get("last"):
        pre = chess.Board(p["pre"])
        if chess.Move.from_uci(p["last"]) not in pre.legal_moves:
            raise ContentError("bad pre move")
        d["pre"], d["last"] = p["pre"], p["last"]
    return d

def best(fen, prompt, explain=None, accept=None, margin=30, depth=18):
    b = chess.Board(fen)
    assert b.is_valid(), fen
    if accept is None:
        accept = engine_accept(b, margin, depth)
    for u in accept:
        if chess.Move.from_uci(u) not in b.legal_moves:
            raise ContentError(f"accept move {u} illegal in {fen}")
    d = {"type": "best", "fen": fen, "accept": accept, "prompt": prompt}
    if explain:
        d["explain"] = explain
    return d

def line(san_moves, side, prompt, notes=None):
    b = chess.Board()
    uci = []
    for s in san_moves.split():
        m = b.parse_san(s)
        uci.append(m.uci())
        b.push(m)
    return {"type": "line", "moves": uci, "side": side, "prompt": prompt, "notes": {str(k): v for k, v in (notes or {}).items()}}

def play(fen, g, moves, prompt, check=True):
    b = chess.Board(fen)
    assert b.is_valid(), fen
    if check and ENGINE is not None:
        info = ENGINE.analyse(b, chess.engine.Limit(depth=22, time=10))
        s = info["score"].pov(b.turn)
        if g in ("win", "promote") and not (s.is_mate() and s.mate() > 0 or (s.score() or 0) >= 300):
            raise ContentError(f"play {g} not winning ({s}) {fen}")
        if g == "draw" and (s.is_mate() or abs(s.score()) > 90):
            raise ContentError(f"play draw not drawn ({s}) {fen}")
    return {"type": "play", "fen": fen, "goal": g, "moves": moves, "prompt": prompt}

def stars(fen, star_squares, prompt):
    b = chess.Board(fen)
    par = stars_par(b, [chess.parse_square(s) for s in star_squares])
    if par is None:
        raise ContentError(f"stars unreachable {fen} {star_squares}")
    return {"type": "stars", "fen": fen, "stars": list(star_squares), "par": par, "prompt": prompt}

def capture(fen, prompt):
    b = chess.Board(fen)
    par = capture_par(b)
    if par is None:
        raise ContentError(f"capture impossible {fen}")
    return {"type": "capture", "fen": fen, "par": par, "prompt": prompt}

# ---------------------------------------------------------------- rule checks

VAL = {chess.PAWN: 100, chess.KNIGHT: 300, chess.BISHOP: 310, chess.ROOK: 500, chess.QUEEN: 900, chess.KING: 20000}

def see_gain(board, sq, side):
    victim = board.piece_at(sq)
    if victim is None or victim.color == side:
        return 0
    b = board.copy(stack=False)
    gains = [VAL[victim.piece_type]]
    cur, on_sq = side, None
    while True:
        att = list(b.attackers(cur, sq))
        if not att:
            break
        frm = min(att, key=lambda s: VAL[b.piece_at(s).piece_type])
        mover = b.piece_at(frm)
        if mover.piece_type == chess.KING:
            t = b.copy(stack=False); t.remove_piece_at(frm); t.set_piece_at(sq, mover)
            if t.attackers(not cur, sq):
                break
        if on_sq is not None:
            gains.append(on_sq - gains[-1])
        on_sq = VAL[mover.piece_type]
        b.remove_piece_at(frm); b.set_piece_at(sq, mover)
        cur = not cur
    if on_sq is None:
        return 0
    d = len(gains) - 1
    while d > 0:
        gains[d - 1] = -max(-gains[d - 1], gains[d]); d -= 1
    return gains[0]

def hanging(board, color):
    return {sq for sq, p in board.piece_map().items() if p.color == color and p.piece_type != chess.KING and see_gain(board, sq, not color) > 0}

def satisfies(b, m, g):
    after = b.copy(); after.push(m)
    mover = b.piece_at(m.from_square)
    checkers = set(b.checkers()) if b.is_check() else set()
    if g == "check": return after.is_check()
    if g == "checkmate": return after.is_checkmate()
    if g == "capture_checker": return b.is_check() and m.to_square in checkers
    if g == "block_check": return b.is_check() and mover.piece_type != chess.KING and m.to_square not in checkers
    if g == "king_escape": return b.is_check() and mover.piece_type == chess.KING
    if g == "castle_short": return b.is_castling(m) and chess.square_file(m.to_square) == 6
    if g == "castle_long": return b.is_castling(m) and chess.square_file(m.to_square) == 2
    if g == "promote": return m.promotion is not None
    if g == "promote_knight": return m.promotion == chess.KNIGHT
    if g == "en_passant": return b.is_en_passant(m)
    if g == "win_material":
        if not b.is_capture(m): return False
        victim = b.piece_at(m.to_square)
        v = VAL[victim.piece_type] if victim else 100
        return v - max(0, see_gain(after, m.to_square, not b.turn)) > 0
    if g == "save_piece":
        return bool(hanging(b, b.turn)) and not hanging(after, b.turn)
    raise ValueError(g)

def forces_mate_any(b, n):
    for m in b.legal_moves:
        if forces_mate(b, m, n):
            return True
    return False

def forces_mate(b, m, n):
    b = b.copy(); b.push(m)
    if b.is_checkmate(): return True
    if n <= 1 or b.is_game_over(): return False
    for r in b.legal_moves:
        b.push(r)
        ok = any(forces_mate(b, x, n - 1) for x in b.legal_moves)
        b.pop()
        if not ok: return False
    return True

def engine_accept(b, margin, depth):
    infos = ENGINE.analyse(b, chess.engine.Limit(depth=depth, time=20), multipv=5)
    top = infos[0]["score"].pov(b.turn)
    def cp(s): return 10000 - s.mate() * 10 if s.is_mate() and s.mate() > 0 else (-10000 if s.is_mate() else s.score())
    best_cp = cp(top)
    return [i["pv"][0].uci() for i in infos if "pv" in i and best_cp - cp(i["score"].pov(b.turn)) <= margin]

# ---------------------------------------------------------------- movement puzzles (stars / capture)

def piece_moves(ptype, sq, blocked, color=chess.WHITE, captures_only_on=frozenset()):
    """Squares a lone piece can move to; `blocked` squares are occupied (cannot pass; can capture if in captures_only_on)."""
    f, r = chess.square_file(sq), chess.square_rank(sq)
    out = []
    def add(x, y):
        if 0 <= x < 8 and 0 <= y < 8:
            out.append(chess.square(x, y))
    if ptype == chess.KNIGHT:
        for dx, dy in [(1,2),(2,1),(2,-1),(1,-2),(-1,-2),(-2,-1),(-2,1),(-1,2)]: add(f+dx, r+dy)
        return [s for s in out if s not in blocked or s in captures_only_on]
    if ptype == chess.KING:
        for dx in (-1,0,1):
            for dy in (-1,0,1):
                if dx or dy: add(f+dx, r+dy)
        return [s for s in out if s not in blocked or s in captures_only_on]
    if ptype == chess.PAWN:
        dy = 1 if color == chess.WHITE else -1
        res = []
        one = chess.square(f, r+dy) if 0 <= r+dy < 8 else None
        if one is not None and one not in blocked:
            res.append(one)
            start = 1 if color == chess.WHITE else 6
            two = chess.square(f, r+2*dy) if 0 <= r+2*dy < 8 else None
            if r == start and two is not None and two not in blocked: res.append(two)
        for dx in (-1, 1):
            if 0 <= f+dx < 8 and 0 <= r+dy < 8:
                s = chess.square(f+dx, r+dy)
                if s in captures_only_on: res.append(s)
        return res
    dirs = []
    if ptype in (chess.ROOK, chess.QUEEN): dirs += [(1,0),(-1,0),(0,1),(0,-1)]
    if ptype in (chess.BISHOP, chess.QUEEN): dirs += [(1,1),(1,-1),(-1,1),(-1,-1)]
    for dx, dy in dirs:
        x, y = f+dx, r+dy
        while 0 <= x < 8 and 0 <= y < 8:
            s = chess.square(x, y)
            if s in blocked:
                if s in captures_only_on: out.append(s)
                break
            out.append(s); x += dx; y += dy
    return out

def stars_par(b, star_sqs):
    pieces = [(sq, p) for sq, p in b.piece_map().items() if p.color == b.turn]
    movers = [(sq, p) for sq, p in pieces if p.piece_type != chess.PAWN or True]
    if len(movers) != 1:
        return None
    start, piece = movers[0]
    blocked = {sq for sq in b.piece_map() if sq != start}
    # BFS distances between key squares
    def dist(a):
        from collections import deque
        d = {a: 0}; q = deque([a])
        while q:
            x = q.popleft()
            for y in piece_moves(piece.piece_type, x, blocked, piece.color):
                if y not in d:
                    d[y] = d[x] + 1; q.append(y)
        return d
    keys = [start] + list(star_sqs)
    D = {k: dist(k) for k in keys}
    best = None
    for perm in itertools.permutations(star_sqs):
        cur, tot = start, 0
        ok = True
        for s in perm:
            if s not in D[cur]: ok = False; break
            tot += D[cur][s]; cur = s
        if ok and (best is None or tot < best):
            best = tot
    return best

def capture_par(b):
    from collections import deque
    us = b.turn
    mine = [(sq, p) for sq, p in b.piece_map().items() if p.color == us]
    if len(mine) != 1:
        return None
    start, piece = mine[0]
    targets = frozenset(sq for sq, p in b.piece_map().items() if p.color != us)
    q = deque([(start, targets, 0)]); seen = {(start, targets)}
    while q:
        pos, left, d = q.popleft()
        if not left:
            return d
        for y in piece_moves(piece.piece_type, pos, set(left), piece.color, captures_only_on=left):
            nl = left - {y}
            st = (y, nl)
            if st not in seen:
                seen.add(st); q.append((y, nl, d + 1))
    return None

def random_stars(ptype, n_stars, obstacles=0, rng=RNG, tries=20000):
    for _ in range(tries):
        start = rng.randrange(64)
        if ptype == chess.PAWN and chess.square_rank(start) in (0, 7):
            continue
        b = chess.Board(None)
        b.set_piece_at(start, chess.Piece(ptype, chess.WHITE))
        for _ in range(obstacles):
            s = rng.randrange(64)
            if b.piece_at(s) is None and chess.square_rank(s) not in (0, 7):
                b.set_piece_at(s, chess.Piece(chess.PAWN, chess.WHITE))
        free = [s for s in chess.SQUARES if b.piece_at(s) is None]
        if ptype == chess.BISHOP:
            free = [s for s in free if (chess.square_file(s) + chess.square_rank(s)) % 2 == (chess.square_file(start) + chess.square_rank(start)) % 2]
        if ptype in (chess.KING, chess.KNIGHT):
            free = [s for s in free if chess.square_distance(s, start) <= 3]
        if ptype == chess.PAWN:
            free = [s for s in free if chess.square_file(s) == chess.square_file(start) and chess.square_rank(start) < chess.square_rank(s) < 7]
        if len(free) < n_stars:
            continue
        st = rng.sample(free, n_stars)
        par = stars_par(b, st)
        cap = n_stars * (4 if ptype in (chess.KING, chess.KNIGHT, chess.BISHOP) else 3)
        if par is None or par < n_stars or par > cap:
            continue
        # avoid trivially adjacent stars for sliders: each star needs at least one move
        return b.fen(), [chess.square_name(s) for s in st]
    raise ContentError("no star layout")

def random_capture(ptype, n_targets, rng=RNG, tries=60000):
    for _ in range(tries):
        b = chess.Board(None)
        start = rng.randrange(64)
        if ptype == chess.PAWN and chess.square_rank(start) in (0, 6, 7):
            continue
        b.set_piece_at(start, chess.Piece(ptype, chess.WHITE))
        ok = True
        for _ in range(n_targets):
            s = rng.randrange(64)
            if ptype == chess.BISHOP and (chess.square_file(s) + chess.square_rank(s)) % 2 != (chess.square_file(start) + chess.square_rank(start)) % 2:
                ok = False; break
            if ptype in (chess.KING, chess.KNIGHT) and chess.square_distance(s, start) > 4:
                ok = False; break
            if b.piece_at(s) is not None or chess.square_rank(s) in (0, 7):
                ok = False; break
            b.set_piece_at(s, chess.Piece(rng.choice([chess.PAWN, chess.KNIGHT, chess.BISHOP, chess.ROOK]), chess.BLACK))
        if not ok:
            continue
        par = capture_par(b)
        if par is None or par > n_targets * 3:
            continue
        return b.fen()
    raise ContentError("no capture layout")

def random_position(spec, rng=RNG, tries=20000, cond=None):
    """spec: list of (piece symbol, zone) where zone is a square-name list or None (anywhere)."""
    for _ in range(tries):
        b = chess.Board(None)
        ok = True
        for sym, zone in spec:
            p = chess.Piece.from_symbol(sym)
            cands = [chess.parse_square(z) for z in zone] if zone else list(chess.SQUARES)
            cands = [c for c in cands if b.piece_at(c) is None and not (p.piece_type == chess.PAWN and chess.square_rank(c) in (0, 7))]
            if not cands:
                ok = False; break
            b.set_piece_at(rng.choice(cands), p)
        if not ok:
            continue
        b.turn = chess.WHITE
        b.castling_rights = 0
        if not b.is_valid():
            continue
        if cond is None or cond(b):
            return b.fen()
    raise ContentError("no position for spec")
USED = set()
DATA = {}
