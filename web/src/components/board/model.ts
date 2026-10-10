/** Everything a board (2D or 3D) shows and reports. Squares are names ("e4"), moves UCI ("e2e4"). */
export type Color = "white" | "black";
export type BoardThemeId = "classic" | "walnut" | "green" | "blue" | "graphite";

export type Arrow = { from: string; to: string; color?: string };

export type BoardProps = {
  fen: string;
  orientation: Color;
  /** Which side may move pieces: a colour, "both" (analysis) or "none". */
  movable: Color | "both" | "none";
  lastMove?: string | null;
  check?: boolean;
  arrows?: Arrow[];
  /** Square -> CSS colour. */
  highlights?: Record<string, string>;
  stars?: string[];
  hint?: string | null;
  showLegal?: boolean;
  coordinates?: boolean;
  theme?: BoardThemeId;
  /** Legal destinations of the piece on a square (the app's rules). */
  legalTargets?: (square: string) => string[];
  onMove?: (from: string, to: string) => void;
  /** Raw square taps (lessons that ask for a square, the puzzle builder). */
  onSquare?: (square: string) => void;
  /** A pawn promotion waiting for the player's choice. */
  promotion?: { from: string; to: string; side: Color | null } | null;
  onPromote?: (piece: "q" | "r" | "b" | "n" | null) => void;
  /** Animate moves. */
  animate?: boolean;
};

export type PieceKind = "p" | "n" | "b" | "r" | "q" | "k";
export type BoardPiece = { id: number; kind: PieceKind; color: Color; square: string };

export const FILES = "abcdefgh";

export function squareName(file: number, rank: number) {
  return FILES[file] + String(rank + 1);
}

export function fileRank(square: string): [number, number] {
  return [FILES.indexOf(square[0]), Number(square[1]) - 1];
}

/** Piece placement of a FEN as square -> piece. */
export function parseFen(fen: string): Map<string, { kind: PieceKind; color: Color }> {
  const out = new Map<string, { kind: PieceKind; color: Color }>();
  const rows = fen.split(" ")[0].split("/");
  rows.forEach((row, i) => {
    let file = 0;
    for (const ch of row) {
      if (/\d/.test(ch)) {
        file += Number(ch);
      } else {
        out.set(squareName(file, 7 - i), { kind: ch.toLowerCase() as PieceKind, color: ch === ch.toUpperCase() ? "white" : "black" });
        file++;
      }
    }
  });
  return out;
}

export function turnOf(fen: string): Color {
  return fen.split(" ")[1] === "b" ? "black" : "white";
}

export function kingSquare(fen: string, color: Color): string | null {
  for (const [sq, p] of parseFen(fen)) if (p.kind === "k" && p.color === color) return sq;
  return null;
}

let nextId = 1;

/**
 * Keeps piece identities across positions, so a moved piece slides instead of reappearing:
 * unchanged squares keep their piece, then each vanished piece is matched to the nearest new
 * square holding the same piece (or, for a promotion, a pawn of the same colour).
 */
export function trackPieces(previous: BoardPiece[], fen: string): BoardPiece[] {
  const board = parseFen(fen);
  const result: BoardPiece[] = [];
  const unmatchedOld: BoardPiece[] = [];
  const taken = new Set<string>();
  for (const p of previous) {
    const now = board.get(p.square);
    if (now && now.kind === p.kind && now.color === p.color && !taken.has(p.square)) {
      result.push(p);
      taken.add(p.square);
    } else {
      unmatchedOld.push(p);
    }
  }
  const fresh = [...board.entries()].filter(([sq]) => !taken.has(sq));
  const dist = (a: string, b: string) => {
    const [fa, ra] = fileRank(a), [fb, rb] = fileRank(b);
    return Math.hypot(fa - fb, ra - rb);
  };
  for (const [sq, piece] of fresh) {
    let best = -1, bestD = Infinity;
    unmatchedOld.forEach((o, i) => {
      const same = o.kind === piece.kind && o.color === piece.color;
      const promoted = o.kind === "p" && piece.kind !== "p" && o.color === piece.color;
      if (!same && !promoted) return;
      const d = dist(o.square, sq) + (same ? 0 : 10);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    });
    if (best >= 0 && bestD < 20) {
      const o = unmatchedOld.splice(best, 1)[0];
      result.push({ id: o.id, kind: piece.kind, color: piece.color, square: sq });
    } else {
      result.push({ id: nextId++, kind: piece.kind, color: piece.color, square: sq });
    }
  }
  return result;
}

export const BOARD_COLORS: Record<BoardThemeId, { light: string; dark: string }> = {
  classic: { light: "#f0d9b5", dark: "#b58863" },
  walnut: { light: "#e8c99b", dark: "#8b5a2b" },
  green: { light: "#eeeed2", dark: "#769656" },
  blue: { light: "#dee3e6", dark: "#8ca2ad" },
  graphite: { light: "#cfd0d6", dark: "#6b6d78" },
};
