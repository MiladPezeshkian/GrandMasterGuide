"use client";

import dynamic from "next/dynamic";
import { useI18n } from "../site/Providers";
import { Board2D } from "./Board2D";
import type { BoardProps } from "./model";

const Board3D = dynamic(() => import("./Board3D").then((m) => m.Board3D), {
  ssr: false,
  loading: () => <div className="skeleton aspect-square w-full rounded-2xl" />,
});

/** The board in the player's chosen view (3D or flat). */
export function Board(props: BoardProps & { view: "3d" | "2d" }) {
  const { theme } = useI18n();
  return props.view === "3d" ? <Board3D {...props} appTheme={theme} /> : <Board2D {...props} />;
}
