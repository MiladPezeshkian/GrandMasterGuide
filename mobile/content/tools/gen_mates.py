#!/usr/bin/env python3
"""
Generates checkmate-pattern exercises (back rank, smothered, Anastasia, Arabian, Boden, Damiano,
epaulette, dovetail, hook, Opera, corridor, queen & bishop, queen & knight, two rooks, pawn mate...).

Random but natural-looking attacking positions are created around a castled or cornered king;
every mate-in-1 found is classified by exact pattern detectors on the final position.
Mate-in-2 versions are found with Stockfish and verified to have a unique first move.

Output: JSON {pattern: [{"fen":..., "moves":[...], "n":1|2}]}
"""
import json, random, sys, collections
import chess, chess.engine

def neighbors(sq):
    return [s for s in chess.SquareSet(chess.BB_KING_ATTACKS[sq])]

def defended_by(board, sq, color, types):
    return [a for a in board.attackers(color, sq) if board.piece_type_at(a) in types]

def classify(board, move):
    """Patterns of the checkmate produced by `move` (board is the position before the move)."""
    b = board.copy(); b.push(move)
    if not b.is_checkmate():
        return set()
    loser = b.turn
    winner = not loser
    k = b.king(loser)
    kf, kr = chess.square_file(k), chess.square_rank(k)
    checkers = list(b.checkers())
    out = set()
    nb = neighbors(k)
    own_adjacent = [s for s in nb if b.piece_at(s) and b.piece_at(s).color == loser]
    back = 0 if loser == chess.WHITE else 7
    edge_rank = kr in (0, 7)
    edge_file = kf in (0, 7)
    if len(checkers) == 2:
        out.add("doubleCheckMate")
    if len(checkers) != 1:
        return out
    c = checkers[0]
    ct = b.piece_type_at(c)
    cf, cr = chess.square_file(c), chess.square_rank(c)
    adjacent = c in nb
    fwd = 1 if kr == back and back == 0 else -1
    # --- back rank / corridor
    if ct in (chess.ROOK, chess.QUEEN) and kr == back and cr == kr and not adjacent:
        front = [chess.square(f, kr + (1 if back == 0 else -1)) for f in range(max(0, kf - 1), min(7, kf + 1) + 1)]
        blocked = sum(1 for s in front if b.piece_at(s) and b.piece_at(s).color == loser)
        if blocked >= 2:
            out.add("backRank")
    if ct in (chess.ROOK, chess.QUEEN) and edge_file and cf == kf and not adjacent:
        side = [chess.square(kf + (1 if kf == 0 else -1), r) for r in range(max(0, kr - 1), min(7, kr + 1) + 1)]
        if sum(1 for s in side if b.piece_at(s) and b.piece_at(s).color == loser) >= 2:
            out.add("corridor")
    # --- smothered
    if ct == chess.KNIGHT and all(b.piece_at(s) and b.piece_at(s).color == loser for s in nb):
        out.add("smothered")
    # --- Arabian: rook next to cornered king, protected by a knight that also covers the other flight square
    if ct == chess.ROOK and adjacent and edge_rank and edge_file:
        if defended_by(b, c, winner, [chess.KNIGHT]):
            out.add("arabian")
    # --- Anastasia: king on edge file, rook/queen mates along it, own pawn next to king, knight covers escape
    if ct in (chess.ROOK, chess.QUEEN) and edge_file and cf == kf:
        pawn_side = [s for s in nb if chess.square_file(s) != kf and b.piece_type_at(s) == chess.PAWN and b.color_at(s) == loser]
        knight_cover = any(b.piece_type_at(a) == chess.KNIGHT for s in nb for a in b.attackers(winner, s))
        if pawn_side and knight_cover and not edge_rank:
            out.add("anastasia")
    # --- hook: rook adjacent, protected by knight, knight protected by pawn
    if ct == chess.ROOK and adjacent:
        for n in defended_by(b, c, winner, [chess.KNIGHT]):
            if defended_by(b, n, winner, [chess.PAWN]):
                out.add("hook")
    # --- Opera: rook adjacent on the back rank protected by a bishop
    if ct == chess.ROOK and adjacent and cr == kr == back and defended_by(b, c, winner, [chess.BISHOP]):
        out.add("opera")
    # --- queen mates next to the king with support
    if ct == chess.QUEEN and adjacent:
        if defended_by(b, c, winner, [chess.PAWN]):
            out.add("queenPawn")
        if defended_by(b, c, winner, [chess.BISHOP]):
            out.add("queenBishop")
        if defended_by(b, c, winner, [chess.KNIGHT]):
            out.add("queenKnight")
        # Dovetail: queen diagonally adjacent, the two squares behind the king (away from the queen) blocked
        if abs(cf - kf) == 1 and abs(cr - kr) == 1:
            bx, by = kf - (cf - kf), kr - (cr - kr)
            behind = [(bx, kr), (kf, by)]
            if all(0 <= x < 8 and 0 <= y < 8 and b.piece_at(chess.square(x, y)) and b.color_at(chess.square(x, y)) == loser for x, y in behind):
                out.add("dovetail")
    # --- Epaulette: queen two squares in front of the king on its file, both sides of the king blocked by own pieces
    if ct == chess.QUEEN and cf == kf and abs(cr - kr) == 2:
        sides = [chess.square(kf - 1, kr) if kf > 0 else None, chess.square(kf + 1, kr) if kf < 7 else None]
        if all(s is not None and b.piece_at(s) and b.color_at(s) == loser and b.piece_type_at(s) != chess.PAWN for s in sides):
            out.add("epaulette")
    # --- Boden: bishop mates, a second bishop covers a flight square, king hemmed by its own pieces
    if ct == chess.BISHOP:
        other = [s for s in b.pieces(chess.BISHOP, winner) if s != c]
        if any(any(q in chess.SquareSet(b.attacks(o)) for q in nb) for o in other) and len(own_adjacent) >= 2:
            out.add("boden")
    # --- two rooks / rook + queen ladder on the edge
    if ct in (chess.ROOK, chess.QUEEN) and edge_rank and cr == kr and not adjacent:
        heavy = [s for s in b.pieces(chess.ROOK, winner) | b.pieces(chess.QUEEN, winner) if s != c]
        if any(chess.square_rank(s) == kr + (1 if kr == 0 else -1) for s in heavy):
            out.add("ladder")
    if ct == chess.PAWN:
        out.add("pawnMate")
    if ct == chess.BISHOP and not out:
        out.add("bishopMate")
    if ct == chess.KNIGHT and "smothered" not in out:
        out.add("knightMate")
    return out


PIECES_ATT = [chess.QUEEN, chess.ROOK, chess.ROOK, chess.BISHOP, chess.BISHOP, chess.KNIGHT, chess.KNIGHT]

def random_attack_position(rng):
    """Black king castled or cornered with a pawn shield; White attacking pieces nearby."""
    b = chess.Board(None)
    side = rng.choice(["k", "q"])  # kingside or queenside castle
    if side == "k":
        king = rng.choice([chess.G8, chess.H8, chess.G8, chess.F8, chess.H7, chess.G7])
        shield_files = [5, 6, 7]
    else:
        king = rng.choice([chess.C8, chess.B8, chess.A8, chess.B7])
        shield_files = [0, 1, 2]
    b.set_piece_at(king, chess.Piece(chess.KING, chess.BLACK))
    for f in shield_files:
        if rng.random() < 0.85:
            r = rng.choice([6, 6, 6, 5])
            sq = chess.square(f, r)
            if b.piece_at(sq) is None:
                b.set_piece_at(sq, chess.Piece(chess.PAWN, chess.BLACK))
    # Black defenders / blockers near the king
    for _ in range(rng.randint(1, 4)):
        pt = rng.choice([chess.ROOK, chess.ROOK, chess.KNIGHT, chess.BISHOP, chess.QUEEN, chess.PAWN])
        f = rng.choice(shield_files + [3, 4] if side == "k" else shield_files + [3, 4])
        r = rng.choice([7, 7, 6, 5])
        sq = chess.square(f, r)
        if b.piece_at(sq) is None and not (pt == chess.PAWN and r == 7):
            b.set_piece_at(sq, chess.Piece(pt, chess.BLACK))
    # Black pieces elsewhere
    for _ in range(rng.randint(0, 4)):
        pt = rng.choice([chess.PAWN, chess.PAWN, chess.ROOK, chess.KNIGHT, chess.BISHOP, chess.QUEEN])
        sq = rng.randrange(64)
        if b.piece_at(sq) is None and not (pt == chess.PAWN and chess.square_rank(sq) in (0, 7)):
            b.set_piece_at(sq, chess.Piece(pt, chess.BLACK))
    # White king safe at home
    wk = rng.choice([chess.G1, chess.H1, chess.B1, chess.C1, chess.G2])
    if b.piece_at(wk) is None:
        b.set_piece_at(wk, chess.Piece(chess.KING, chess.WHITE))
    else:
        return None
    for f in ([5, 6, 7] if chess.square_file(wk) >= 5 else [0, 1, 2]):
        if rng.random() < 0.7:
            sq = chess.square(f, rng.choice([1, 1, 2]))
            if b.piece_at(sq) is None:
                b.set_piece_at(sq, chess.Piece(chess.PAWN, chess.WHITE))
    # White attackers, closer to the black king
    for _ in range(rng.randint(2, 4)):
        pt = rng.choice(PIECES_ATT + [chess.PAWN])
        kf, kr = chess.square_file(king), chess.square_rank(king)
        for _ in range(20):
            f = min(7, max(0, kf + rng.randint(-4, 4)))
            r = min(7, max(0, kr - rng.randint(0, 6)))
            sq = chess.square(f, r)
            if b.piece_at(sq) is None and not (pt == chess.PAWN and r in (0, 7)):
                b.set_piece_at(sq, chess.Piece(pt, chess.WHITE))
                break
    b.turn = chess.WHITE
    if not b.is_valid() or b.is_check():
        return None
    # Keep material roughly sane: White not more than a rook + piece ahead.
    val = {chess.PAWN: 1, chess.KNIGHT: 3, chess.BISHOP: 3, chess.ROOK: 5, chess.QUEEN: 9, chess.KING: 0}
    mat = sum(val[p.piece_type] * (1 if p.color else -1) for p in b.piece_map().values())
    if abs(mat) > 9:
        return None
    return b

def transform(b, rng):
    """Random mirror (a<->h) and colour flip for variety."""
    if rng.random() < 0.5:
        b = b.transform(chess.flip_horizontal)
    if rng.random() < 0.4:
        b = b.mirror()
    return b

def main():
    seed = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    target = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    out_path = sys.argv[3] if len(sys.argv) > 3 else "mates.json"
    engine_path = sys.argv[4] if len(sys.argv) > 4 else None
    rng = random.Random(seed)
    found = collections.defaultdict(list)
    seen = set()
    tries = 0
    while tries < 400000:
        tries += 1
        b = random_attack_position(rng)
        if b is None:
            continue
        mates = []
        for m in b.legal_moves:
            b.push(m)
            if b.is_checkmate():
                mates.append(m)
            b.pop()
        if len(mates) != 1:
            continue
        pats = classify(b, mates[0])
        for p in pats:
            if len(found[p]) >= target:
                continue
            t = transform(b.copy(), rng)
            # re-find the mate after the transform
            mm = [m for m in t.legal_moves if (t.push(m) or True) and (t.is_checkmate(), t.pop())[0]]
            if len(mm) != 1:
                continue
            key = t.board_fen()
            if key in seen:
                continue
            seen.add(key)
            found[p].append({"fen": t.fen(), "moves": [mm[0].uci()], "n": 1})
        if tries % 20000 == 0:
            print(tries, {k: len(v) for k, v in found.items()}, flush=True)
        if all(len(found[p]) >= target for p in ["backRank", "smothered", "arabian", "anastasia", "hook", "opera", "queenPawn", "queenBishop", "queenKnight", "dovetail", "epaulette", "boden", "ladder", "corridor", "pawnMate"]):
            break
    # Mate in 2 with Stockfish: the first move must be the only one that mates in 2.
    if engine_path:
        eng = chess.engine.SimpleEngine.popen_uci(engine_path)
        two = collections.defaultdict(list)
        tries2 = 0
        while tries2 < 60000 and sum(len(v) for v in two.values()) < target * 8:
            tries2 += 1
            b = random_attack_position(rng)
            if b is None:
                continue
            # quick filter: no mate in one
            if any((b.push(m) or True) and (b.is_checkmate(), b.pop())[0] for m in list(b.legal_moves)):
                continue
            info = eng.analyse(b, chess.engine.Limit(depth=8), multipv=2)
            s0 = info[0]["score"].white()
            if not (s0.is_mate() and s0.mate() == 2):
                continue
            if len(info) > 1:
                s1 = info[1]["score"].white()
                if s1.is_mate() and 0 < s1.mate() <= 2:
                    continue
            pv = info[0]["pv"][:3]
            if len(pv) < 3:
                continue
            bb = b.copy(); bb.push(pv[0]); bb.push(pv[1])
            pats = classify(bb, pv[2])
            for p in pats:
                if len(two[p]) >= target:
                    continue
                key = b.board_fen()
                if key in seen:
                    continue
                seen.add(key)
                two[p].append({"fen": b.fen(), "moves": [m.uci() for m in pv], "n": 2})
            if tries2 % 2000 == 0:
                print("m2", tries2, {k: len(v) for k, v in two.items()}, flush=True)
        eng.quit()
        for k, v in two.items():
            found[k + "2"] = v
    json.dump(found, open(out_path, "w"), indent=0)
    print("done", {k: len(v) for k, v in found.items()})

if __name__ == "__main__":
    main()
