"""Human-oriented difficulty estimate for mined puzzles (the miner's depth-based guess is too engine-centric)."""
import chess

WEIGHTS = {
    "fork": 60, "pin": 90, "skewer": 90, "discoveredAttack": 140, "discoveredCheck": 120, "doubleCheck": 160,
    "deflection": 220, "attraction": 200, "capturingDefender": 160, "sacrifice": 250, "quietMove": 260,
    "promotion": 60, "underPromotion": 250, "enPassant": 80, "hangingPiece": -150,
    "backRankMate": 40, "smotheredMate": 150,
}

def rate(p):
    b = chess.Board(p["fen"])
    n = (len(p["moves"]) + 1) // 2
    themes = set(p["themes"])
    first = chess.Move.from_uci(p["moves"][0])
    r = 650
    if "mate" in themes:
        r = {1: 550, 2: 1150, 3: 1550, 4: 1850}.get(n, 2000)
    else:
        r += 280 * (n - 1)
    for t, w in WEIGHTS.items():
        if t in themes:
            r += w
    if b.gives_check(first):
        r -= 80
    if b.is_capture(first):
        r -= 60
    r += max(0, b.legal_moves.count() - 25) * 4
    # the engine's search depth to find the move still says something
    r += (p.get("rating", 500) - 450) // 3
    return int(max(400, min(2700, r)))
