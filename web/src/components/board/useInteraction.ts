"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { parseFen, turnOf, type BoardProps } from "./model";

/**
 * Tap-to-move shared by the 2D and 3D boards: tap a piece of the side that may move, then a
 * highlighted square. With [onSquare] every tap is reported instead (lessons, puzzle builder).
 */
export function useInteraction(props: BoardProps) {
  const { fen, movable, legalTargets, onMove, onSquare } = props;
  const [selected, setSelected] = useState<string | null>(null);
  const board = useMemo(() => parseFen(fen), [fen]);
  const turn = turnOf(fen);

  // A new position clears the selection.
  useEffect(() => setSelected(null), [fen]);

  const canPick = useCallback(
    (square: string) => {
      const p = board.get(square);
      if (!p || movable === "none") return false;
      if (p.color !== turn) return false;
      return movable === "both" || movable === p.color;
    },
    [board, movable, turn],
  );

  const targets = useMemo(() => (selected && legalTargets ? legalTargets(selected) : []), [selected, legalTargets]);

  const tap = useCallback(
    (square: string) => {
      if (onSquare) {
        onSquare(square);
        return;
      }
      if (selected && selected !== square && (targets.includes(square) || !legalTargets)) {
        onMove?.(selected, square);
        setSelected(null);
        return;
      }
      if (selected === square) {
        setSelected(null);
        return;
      }
      setSelected(canPick(square) ? square : null);
    },
    [onSquare, selected, targets, legalTargets, onMove, canPick],
  );

  /** A drag from [from] dropped on [to]. */
  const drop = useCallback(
    (from: string, to: string | null) => {
      if (!to || to === from) {
        setSelected(canPick(from) ? from : null);
        return;
      }
      const ok = legalTargets ? legalTargets(from).includes(to) : true;
      if (ok) onMove?.(from, to);
      setSelected(null);
    },
    [canPick, legalTargets, onMove],
  );

  return { selected, targets, tap, drop, canPick, board, turn };
}
