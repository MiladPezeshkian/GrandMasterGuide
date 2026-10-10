/** An online game as the server describes it (see lib/games.ts gameView). */
export type OnlineGame = {
  id: string;
  status: "waiting" | "active" | "finished" | "aborted" | "declined";
  moves: string[];
  fen: string;
  ply: number;
  result: string | null;
  reason: string | null;
  white: string | null;
  black: string | null;
  creator: string | null;
  invitee: string | null;
  creatorColor: string;
  you: "white" | "black" | null;
  isCreator: boolean;
  isInvitee: boolean;
  initialMs: number;
  incrementMs: number;
  clocks: { white: number; black: number };
  clockRunning: boolean;
  drawOffer: "white" | "black" | null;
  rematchId: string | null;
  updatedAt: number;
  serverTime: number;
};
