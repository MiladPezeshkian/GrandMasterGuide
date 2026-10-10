#!/usr/bin/env python3
"""
Zorix Chess puzzle miner.

Plays engine-vs-engine games at human-like strengths, finds the moments where one side
blunders, and turns them into puzzles whose every solving move is the *only* good move
(verified with a deep multi-PV search). Each puzzle gets an estimated rating and theme tags.

Usage: mine_puzzles.py STOCKFISH OUT.jsonl [--seed N] [--games N]
Output: one JSON object per line:
  {"fen": solver to move, "pre": FEN before the opponent's blunder, "last": the blunder (UCI),
   "moves": solution line (solver, reply, solver, ...), "rating": int, "themes": [...]}
"""
import argparse, json, math, random, sys, time
import chess, chess.engine

VALUES = {chess.PAWN: 100, chess.KNIGHT: 300, chess.BISHOP: 310, chess.ROOK: 500, chess.QUEEN: 900, chess.KING: 0}

def win(score: chess.engine.PovScore, pov) -> float:
    s = score.pov(pov)
    if s.is_mate():
        return 1.0 if s.mate() > 0 else 0.0
    return 1.0 / (1.0 + math.exp(-0.00368208 * s.score()))

def material(board, side):
    m = 0
    for pt, v in VALUES.items():
        m += v * (len(board.pieces(pt, side)) - len(board.pieces(pt, not side)))
    return m

def see_capture_gain(board, square, side):
    """Static exchange: what `side` gains by capturing on `square` (0 if no capture)."""
    victim = board.piece_at(square)
    if victim is None or victim.color == side:
        return 0
    b = board.copy(stack=False)
    gains = [VALUES[victim.piece_type] if victim.piece_type != chess.KING else 20000]
    cur = side
    on_sq = None
    while True:
        attackers = [s for s in b.attackers(cur, square)]
        if not attackers:
            break
        frm = min(attackers, key=lambda s: VALUES[b.piece_at(s).piece_type] if b.piece_at(s).piece_type != chess.KING else 50000)
        mover = b.piece_at(frm)
        if mover.piece_type == chess.KING and b.attackers(not cur, square):
            # remove captured piece first to test: king can't capture into attack
            tmp = b.copy(stack=False); tmp.remove_piece_at(frm); tmp.set_piece_at(square, mover)
            if tmp.attackers(not cur, square):
                break
        if on_sq is not None:
            gains.append(on_sq - gains[-1])
        on_sq = VALUES[mover.piece_type] if mover.piece_type != chess.KING else 20000
        b.remove_piece_at(frm)
        b.set_piece_at(square, mover)
        cur = not cur
        if len(gains) > 30:
            break
    if on_sq is None:
        return 0
    d = len(gains) - 1
    while d > 0:
        gains[d - 1] = -max(-gains[d - 1], gains[d])
        d -= 1
    return gains[0]

class Miner:
    def __init__(self, path, seed):
        self.rng = random.Random(seed)
        self.blunder_rate = 0.0
        self.keep_trivial = 0.15
        self.hard = False
        self.eng = chess.engine.SimpleEngine.popen_uci(path)
        self.eng.configure({"Threads": 1, "Hash": 64})

    def close(self):
        self.eng.quit()

    # ------------------------------------------------------------------ game generation
    def weak_move(self, board, elo):
        # Now and then a plausible mistake: a move a club player would consider that loses material.
        if board.ply() >= 10 and self.rng.random() < self.blunder_rate:
            self.eng.configure({"UCI_LimitStrength": False})
            infos = self.eng.analyse(board, chess.engine.Limit(depth=6), multipv=8)
            scored = [(i["pv"][0], i["score"].pov(board.turn).score(mate_score=10000)) for i in infos if "pv" in i]
            if scored:
                best = scored[0][1]
                bad = [mv for mv, sc in scored if best - 700 <= sc <= best - 150]
                if bad:
                    return self.rng.choice(bad)
        self.eng.configure({"UCI_LimitStrength": True, "UCI_Elo": max(1320, elo)})
        r = self.eng.play(board, chess.engine.Limit(nodes=self.rng.randint(8000, 30000)))
        return r.move

    def opening(self, board):
        plies = self.rng.randint(2, 7)
        self.eng.configure({"UCI_LimitStrength": False})
        for _ in range(plies):
            infos = self.eng.analyse(board, chess.engine.Limit(depth=8), multipv=5)
            cands = [(i["pv"][0], i["score"].pov(board.turn).score(mate_score=10000)) for i in infos if "pv" in i]
            best = max(c[1] for c in cands)
            cands = [c for c in cands if c[1] >= best - 60]
            board.push(self.rng.choice(cands)[0])

    def game(self):
        board = chess.Board()
        self.opening(board)
        elo_w = self.rng.choice([1500, 1700, 1900, 2100, 2300, 2500])
        elo_b = max(1320, min(2600, elo_w + self.rng.choice([-200, -100, 0, 100, 200])))
        while not board.is_game_over(claim_draw=True) and board.ply() < 170:
            board.push(self.weak_move(board, elo_w if board.turn else elo_b))
        return board

    # ------------------------------------------------------------------ analysis
    def quick(self, board):
        self.eng.configure({"UCI_LimitStrength": False})
        return self.eng.analyse(board, chess.engine.Limit(nodes=40000))["score"]

    def deep(self, board, nodes=1_200_000, multipv=2):
        self.eng.configure({"UCI_LimitStrength": False})
        return self.eng.analyse(board, chess.engine.Limit(nodes=nodes), multipv=multipv)

    def injected(self, final):
        """Positions from the game where a plausible mistake is injected; yields (pre, mistake, fen)."""
        moves = list(final.move_stack)
        board = chess.Board()
        out = []
        next_sample = self.rng.randint(8, 12)
        for i, mv in enumerate(moves):
            board.push(mv)
            if board.is_game_over():
                break
            if board.ply() < next_sample:
                continue
            next_sample = board.ply() + self.rng.randint(2, 4)
            self.eng.configure({"UCI_LimitStrength": False})
            infos = self.eng.analyse(board, chess.engine.Limit(depth=9), multipv=12)
            scored = [(i["pv"][0], i["score"].pov(board.turn).score(mate_score=10000)) for i in infos if "pv" in i]
            if len(scored) < 3:
                continue
            best = scored[0][1]
            if abs(best) > 250:
                continue  # already decided positions make poor puzzles
            lo, hi = (best - 450, best - 160) if self.hard else (best - 900, best - 220)
            bad = [m for m, sc in scored[1:] if lo <= sc <= hi]
            # Mistakes that allow a forced mate make the best puzzles: always keep them.
            mating = [i["pv"][0] for i in infos if "pv" in i and i["score"].pov(board.turn).is_mate()
                      and i["score"].pov(board.turn).mate() < 0 and -i["score"].pov(board.turn).mate() <= 5]
            self.rng.shuffle(bad)
            for m in mating[:3] + bad[:3]:
                b2 = board.copy()
                b2.push(m)
                if b2.is_game_over():
                    continue
                out.append((board.fen(), m.uci(), b2.fen()))
        return out

    def candidates(self, final):
        moves = list(final.move_stack)
        board = chess.Board()
        prev = None
        out = []
        for i, mv in enumerate(moves):
            board.push(mv)
            if board.is_game_over():
                break
            sc = self.quick(board)
            if prev is not None and board.ply() >= 8:
                w_now = win(sc, board.turn)
                w_before = 1.0 - win(prev, not board.turn) if False else win(prev, board.turn)
                if w_now - w_before >= 0.25 and w_now >= 0.70 and w_before <= 0.6:
                    pre = board.copy()
                    pre.pop()
                    out.append((pre.fen(), mv.uci(), board.fen()))
            prev = sc
        return out

    def build(self, fen):
        board = chess.Board(fen)
        solver = board.turn
        pre = self.deep(board, nodes=150_000, multipv=2)
        if len(pre) < 2 or "pv" not in pre[0]:
            return None
        if win(pre[0]["score"], solver) < 0.72 or win(pre[0]["score"], solver) - win(pre[1]["score"], solver) < 0.22:
            return None
        start_mat = material(board, solver)
        line = []
        mate_line = False
        for step in range(4):
            infos = self.deep(board)
            if not infos or "pv" not in infos[0]:
                break
            best = infos[0]
            bs = best["score"].pov(solver)
            second = infos[1] if len(infos) > 1 else None
            if step == 0:
                if not (bs.is_mate() and bs.mate() > 0) and (bs.score() is None or bs.score() < 220):
                    return None
                if second is None:
                    return None
                # Simply taking a piece that was left hanging is too easy to be a puzzle most of the time.
                first = best["pv"][0]
                victim = board.piece_at(first.to_square)
                if victim is not None and not bs.is_mate():
                    free = see_capture_gain(board, first.to_square, solver) >= VALUES[victim.piece_type]
                    if free and (self.hard or self.rng.random() > self.keep_trivial):
                        return None
            if bs.is_mate() and bs.mate() > 0:
                mate_line = True
                if bs.mate() == 1:
                    # Last move: any mate is accepted by the app, take the engine's.
                    line.append(best["pv"][0]); board.push(best["pv"][0])
                    break
                if second is not None and second["score"].pov(solver).is_mate() and second["score"].pov(solver).mate() > 0:
                    return None if step == 0 else self.finish(line, board, fen, solver, start_mat, mate_line, trimmed=True)
            else:
                if mate_line:
                    return None
                if second is not None and win(best["score"], solver) - win(second["score"], solver) < 0.28:
                    break
            mv = best["pv"][0]
            line.append(mv)
            board.push(mv)
            if board.is_game_over():
                break
            reply = self.deep(board, nodes=600_000, multipv=1)[0]
            if "pv" not in reply:
                break
            line.append(reply["pv"][0])
            board.push(reply["pv"][0])
        return self.finish(line, board, fen, solver, start_mat, mate_line)

    def finish(self, line, board, fen, solver, start_mat, mate_line, trimmed=False):
        # The line must end with a solver move.
        if len(line) % 2 == 0 and line:
            line = line[:-1]
        if not line:
            return None
        if mate_line:
            b = chess.Board(fen)
            for m in line:
                b.push(m)
            return (line, True) if b.is_checkmate() else None
        # Advantage puzzle: after the opponent's best answer the solver must have won material
        # (or keep a crushing, engine-confirmed advantage).
        while line:
            b = chess.Board(fen)
            for m in line:
                b.push(m)
            if b.is_checkmate():
                return line, True
            info = self.deep(b, nodes=800_000, multipv=1)[0]
            if "pv" not in info:
                return None
            ev = info["score"].pov(solver)
            b.push(info["pv"][0])
            gain = material(b, solver) - start_mat
            winning = ev.is_mate() and ev.mate() > 0 or (ev.score() is not None and ev.score() >= 200)
            if winning and gain >= 150:
                return line, False
            line = line[:-2]
        return None

    def rating(self, fen, line, mate):
        board = chess.Board(fen)
        sol = line[0]
        found = 30
        self.eng.configure({"UCI_LimitStrength": False})
        for d in range(1, 16):
            info = self.eng.analyse(board, chess.engine.Limit(depth=d), game=object())
            if info.get("pv") and info["pv"][0] == sol:
                found = d
                break
        n = (len(line) + 1) // 2
        r = 450 + 95 * found + 140 * (n - 1)
        if not board.is_capture(sol) and not board.gives_check(sol) and not sol.promotion:
            r += 170
        if board.is_capture(sol) and see_capture_gain(board, sol.to_square, board.turn) >= VALUES[board.piece_at(sol.to_square).piece_type] if board.piece_at(sol.to_square) else False:
            r -= 120
        if mate and n == 1:
            r -= 80
        return max(400, min(2800, int(r)))

    # ------------------------------------------------------------------ themes
    def themes(self, fen, line, mate):
        board = chess.Board(fen)
        solver = board.turn
        t = set()
        n = (len(line) + 1) // 2
        if mate:
            t.add("mate"); t.add(f"mateIn{n}")
        t.add("oneMove" if n == 1 else "short" if n == 2 else "long" if n == 3 else "veryLong")
        first = line[0]
        if not board.is_capture(first) and not board.gives_check(first) and not first.promotion:
            t.add("quietMove")
        b = board.copy()
        solver_moves = []
        for i, mv in enumerate(line):
            mover_side = b.turn
            if mover_side == solver:
                t |= self.move_motifs(b, mv, i == 0)
                solver_moves.append((b.copy(), mv))
            b.push(mv)
        # Removing the defender: a capture of a piece that guarded something we take later.
        for k, (pos, mv) in enumerate(solver_moves[:-1]):
            if not pos.is_capture(mv):
                continue
            guarded = [q for q in pos.attacks(mv.to_square) if pos.piece_at(q) and pos.piece_at(q).color != solver]
            later = {m.to_square for _, m in solver_moves[k + 1:] if _.is_capture(m)}
            if any(q in later for q in guarded):
                t.add("capturingDefender")
        # Attraction and deflection after a sacrifice that is taken.
        if len(line) >= 3:
            b0 = board.copy()
            s1 = line[0]; r1 = line[1]
            b1 = b0.copy(); b1.push(s1)
            if r1.to_square == s1.to_square and b1.is_capture(r1):
                t.add("attraction")
            else:
                mover = b1.piece_at(r1.from_square)
                if mover is not None:
                    guarded_before = set(b1.attacks(r1.from_square))
                    if line[2].to_square in guarded_before and r1.from_square != line[2].to_square:
                        t.add("deflection")
        if mate:
            t |= self.mate_pattern(b)
        # Material swing on first move: sacrifice
        if board.is_capture(first) or True:
            b1 = board.copy(); b1.push(first)
            gain_now = (VALUES[board.piece_at(first.to_square).piece_type] if board.piece_at(first.to_square) else 0)
            lost = see_capture_gain(b1, first.to_square, not solver)
            if lost - gain_now >= 200 and n >= 2:
                t.add("sacrifice")
        if n == 1 and board.is_capture(first) and board.piece_at(first.to_square):
            v = VALUES[board.piece_at(first.to_square).piece_type]
            if see_capture_gain(board, first.to_square, solver) >= v:
                t.add("hangingPiece")
        t |= self.phase(board)
        return sorted(t)

    def move_motifs(self, b, mv, first):
        t = set()
        us = b.turn
        them = not us
        piece = b.piece_at(mv.from_square)
        if b.is_en_passant(mv): t.add("enPassant")
        if b.is_castling(mv): t.add("castling")
        if mv.promotion:
            t.add("promotion")
            if mv.promotion != chess.QUEEN: t.add("underPromotion")
        before_attacks = {s: set(b.attacks(s)) for s in chess.SQUARES if b.piece_at(s) and b.piece_at(s).color == us}
        a = b.copy(); a.push(mv)
        if a.is_check():
            checkers = list(a.checkers())
            if len(checkers) >= 2: t.add("doubleCheck")
            elif mv.to_square not in checkers: t.add("discoveredCheck")
        to = mv.to_square
        moved = a.piece_at(to)
        mover_safe = see_capture_gain(a, to, them) <= 0
        # fork
        targets = []
        for s in a.attacks(to):
            p = a.piece_at(s)
            if p and p.color == them:
                if p.piece_type == chess.KING or VALUES[p.piece_type] > VALUES[moved.piece_type] or see_capture_gain(a, s, us) > 0:
                    targets.append(s)
        if len(targets) >= 2 and mover_safe:
            t.add("fork")
        # pins and skewers from the moved piece
        if moved.piece_type in (chess.BISHOP, chess.ROOK, chess.QUEEN) and mover_safe:
            for s in a.attacks(to):
                p = a.piece_at(s)
                if not p or p.color != them:
                    continue
                ray = chess.ray(to, s)
                between_after = [q for q in chess.SquareSet(ray) if q != to and q != s and
                                 chess.square_distance(to, q) > chess.square_distance(to, s) and
                                 (chess.square_file(q) - chess.square_file(to)) * (chess.square_file(s) - chess.square_file(to)) >= 0 and
                                 (chess.square_rank(q) - chess.square_rank(to)) * (chess.square_rank(s) - chess.square_rank(to)) >= 0]
                between_after.sort(key=lambda q: chess.square_distance(s, q))
                behind = next((q for q in between_after if a.piece_at(q)), None)
                if behind is None or not chess.SquareSet(chess.between(to, behind)) & chess.SquareSet([s]):
                    continue
                bp = a.piece_at(behind)
                if bp.color != them:
                    continue
                fv = VALUES[p.piece_type] if p.piece_type != chess.KING else 20000
                bv = VALUES[bp.piece_type] if bp.piece_type != chess.KING else 20000
                if bv > fv and p.piece_type != chess.KING:
                    t.add("pin")
                elif fv > bv and fv > VALUES[moved.piece_type] and bp.piece_type != chess.PAWN:
                    t.add("skewer")
        # discovered attack
        for s, before in before_attacks.items():
            if s == mv.from_square:
                continue
            p = a.piece_at(s)
            if not p or p.piece_type not in (chess.BISHOP, chess.ROOK, chess.QUEEN):
                continue
            for q in set(a.attacks(s)) - before:
                tp = a.piece_at(q)
                if tp and tp.color == them and tp.piece_type != chess.KING and (VALUES[tp.piece_type] >= VALUES[p.piece_type] or see_capture_gain(a, q, us) > 0):
                    t.add("discoveredAttack")
        # capturing the defender
        if b.is_capture(mv) and not first:
            pass
        return t

    def mate_pattern(self, b):
        t = set()
        if not b.is_checkmate():
            return t
        king = b.king(b.turn)
        checkers = list(b.checkers())
        rank = chess.square_rank(king)
        if len(checkers) == 1:
            c = b.piece_at(checkers[0])
            if c.piece_type in (chess.ROOK, chess.QUEEN) and rank in (0, 7) and chess.square_rank(checkers[0]) == rank:
                fwd = 1 if rank == 0 else -1
                blocked = all(
                    (b.piece_at(chess.square(f, rank + fwd)) is not None and b.piece_at(chess.square(f, rank + fwd)).color == b.turn)
                    for f in range(max(0, chess.square_file(king) - 1), min(7, chess.square_file(king) + 1) + 1)
                )
                if blocked:
                    t.add("backRankMate")
            if c.piece_type == chess.KNIGHT:
                nb = [s for s in chess.SquareSet(chess.BB_KING_ATTACKS[king])]
                if all(b.piece_at(s) is not None and b.piece_at(s).color == b.turn for s in nb):
                    t.add("smotheredMate")
        return t

    def phase(self, board):
        t = set()
        minors_majors = [len(board.pieces(p, c)) for p in (chess.KNIGHT, chess.BISHOP, chess.ROOK, chess.QUEEN) for c in (True, False)]
        total = sum(VALUES[p] * (len(board.pieces(p, True)) + len(board.pieces(p, False))) for p in (chess.KNIGHT, chess.BISHOP, chess.ROOK, chess.QUEEN))
        if board.fullmove_number <= 12 and total >= 5000:
            t.add("opening")
        elif total <= 2600:
            t.add("endgame")
            kinds = {p for p in (chess.KNIGHT, chess.BISHOP, chess.ROOK, chess.QUEEN) if board.pieces(p, True) or board.pieces(p, False)}
            if not kinds: t.add("pawnEndgame")
            elif kinds == {chess.ROOK}: t.add("rookEndgame")
            elif kinds == {chess.QUEEN}: t.add("queenEndgame")
            elif kinds == {chess.BISHOP}: t.add("bishopEndgame")
            elif kinds == {chess.KNIGHT}: t.add("knightEndgame")
        else:
            t.add("middlegame")
        return t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("engine"); ap.add_argument("out")
    ap.add_argument("--seed", type=int, default=1); ap.add_argument("--games", type=int, default=100000)
    ap.add_argument("--hard", action="store_true", help="prefer subtle mistakes and deeper solutions")
    args = ap.parse_args()
    m = Miner(args.engine, args.seed)
    m.hard = args.hard
    seen = set()
    found = 0
    t0 = time.time()
    with open(args.out, "a") as f:
        for g in range(args.games):
            try:
                final = m.game()
                for pre, last, fen in m.injected(final):
                    key = fen.split(" ")[0]
                    if key in seen:
                        continue
                    seen.add(key)
                    res = m.build(fen)
                    if not res:
                        continue
                    line, mate = res
                    if m.hard and len(line) == 1 and not mate:
                        # one-move puzzles only when the engine needs some depth to see them
                        probe = m.eng.analyse(chess.Board(fen), chess.engine.Limit(depth=3))
                        if probe.get("pv") and probe["pv"][0] == line[0]:
                            continue
                    rec = {
                        "fen": fen, "pre": pre, "last": last,
                        "moves": [x.uci() for x in line],
                        "rating": m.rating(fen, line, mate),
                        "themes": m.themes(fen, line, mate),
                    }
                    f.write(json.dumps(rec) + "\n"); f.flush()
                    found += 1
            except (chess.engine.EngineError, chess.engine.EngineTerminatedError) as e:
                print("engine error", e, file=sys.stderr)
                m.close(); m = Miner(args.engine, args.seed + g + 1000)
            if g % 5 == 0:
                print(f"games={g+1} puzzles={found} rate={found / max(1e-9, time.time() - t0) * 3600:.0f}/h", flush=True)
    m.close()

if __name__ == "__main__":
    main()
