/** The plain objects the core (web/core, ZorixCore) publishes on each channel. */
export type Channel = "board" | "play" | "review" | "puzzles" | "profile" | "courses" | "lesson" | "lessonResult" | "speech" | "message" | "contentError";

export type Color = "white" | "black";
export type Score = { cp: number | null; mate: number | null };
export type Quality = "brilliant" | "great" | "best" | "excellent" | "good" | "book" | "inaccuracy" | "mistake" | "miss" | "blunder";

export type Ply = { uci: string; san: string; fen: string; side: Color; quality: Quality | null };

export type GameState = {
  startFen: string;
  fen: string;
  turn: Color;
  check: boolean;
  plies: Ply[];
  /** The whole line, including moves undone (redo). */
  line: Ply[];
  redo: number;
  lastMove: string | null;
  canUndo: boolean;
  canRedo: boolean;
  result: string;
  over: boolean;
  reason: string | null;
  pgn: string;
};

export type EngineLine = { rank: number; depth: number; score: Score; uci: string[]; san: string[]; text: string };
export type EngineView = {
  fen: string;
  lines: EngineLine[];
  depth: number;
  nodes: number;
  nps: number;
  timeMs: number;
  best: string | null;
  bestSan: string | null;
  finished: boolean;
  score: Score | null;
};

export type EngineStatus =
  | { kind: "preparing"; progress: number }
  | { kind: "starting" }
  | { kind: "ready"; name: string; build: string }
  | { kind: "failed"; message: string };

export type Hint =
  | { kind: "idle" }
  | { kind: "thinking"; fen: string; startedAt: number; budgetMs: number; view: EngineView | null }
  | { kind: "ready"; view: EngineView };

export type Feedback = {
  san: string;
  quality: Quality;
  bestSan: string | null;
  scoreBefore: Score | null;
  scoreAfter: Score | null;
  message: string;
  speech: string;
  best: string | null;
  expectedSan: string | null;
};

export type Promotion = { from: string; to: string; side: Color | null } | null;

export type Settings = {
  thinkTimeMs: number;
  threads: number;
  hashMb: number;
  analysisLines: number;
  boardTheme: "classic" | "walnut" | "green" | "blue" | "graphite";
  showCoordinates: boolean;
  showLegalMoves: boolean;
  showArrows: boolean;
  animateMoves: boolean;
  haptics: boolean;
  coachMode: boolean;
  language: string | null;
  voice: boolean;
  explainBotMoves: boolean;
  appTheme: "zorix" | "sky";
};

export type BoardState = {
  game: GameState;
  flipped: boolean;
  promotion: Promotion;
  engine: EngineStatus;
  hint: Hint;
  analysisOn: boolean;
  view: EngineView | null;
  settings: Settings;
  feedback: Feedback | null;
  coachBusy: boolean;
  hintExplanation: { fen: string; display: string; speech: string } | null;
  moveCounter: number;
};

export type CoachMessage = { quality: Quality | null; display: string; speech: string };
export type Bot = { level: number; elo: number; tier: string };

export type PlayState = {
  active: boolean;
  bot: Bot;
  userSide: Color;
  game: GameState;
  promotion: Promotion;
  botThinking: boolean;
  botMessage: CoachMessage | null;
  feedback: Feedback | null;
  coachBusy: boolean;
  hint: CoachMessage | null;
  hintBusy: boolean;
  assists: number;
  result: { outcome: "win" | "draw" | "loss"; reason: string | null; resigned: boolean; drawAgreed: boolean; ratingBefore: number; ratingAfter: number } | null;
  drawDeclined: boolean;
  moveCounter: number;
  userToMove: boolean;
};

export type ReviewedMove = {
  index: number;
  side: Color;
  san: string;
  uci: string;
  quality: Quality;
  scoreBefore: Score;
  scoreAfter: Score;
  bestSan: string | null;
  best: string | null;
  accuracy: number;
  message: string;
  speech: string;
};

export type ReviewState = {
  game: GameState | null;
  player: Color | null;
  progress: number;
  review: {
    moves: ReviewedMove[];
    accuracy: { white: number | null; black: number | null };
    counts: { white: Record<string, number>; black: Record<string, number> };
    evalCurve: number[];
    opening: string | null;
    keyMoments: { white: number[]; black: number[] };
  } | null;
  ply: number;
  retry: { index: number; fen: string; turn: Color; checking: boolean; solved: boolean | null; message: string | null; tried: string | null } | null;
};

export type Position = { fen: string; turn: Color; check: boolean };

export type PuzzleState = {
  puzzle: { id: number; rating: number; themes: string[] } | null;
  position: Position | null;
  lastMove: string | null;
  flipped: boolean;
  outcome: "solving" | "correct_step" | "wrong" | "solved" | "failed";
  interactive: boolean;
  hintSquare: string | null;
  explanation: string | null;
  ratingChange: number | null;
  mode: "rated" | "streak";
  streak: number;
  theme: string | null;
  promotion: Promotion;
  loaded: boolean;
  themes: string[];
};

export type ProfileState = {
  name: string;
  onboarded: boolean;
  experience: string;
  rating: number;
  ratedGames: number;
  peakRating: number;
  wins: number;
  draws: number;
  losses: number;
  levelStars: Record<string, number>;
  puzzleRating: number;
  puzzlesSolved: number;
  puzzlesTried: number;
  puzzleStreak: number;
  bestPuzzleStreak: number;
  lessonStars: Record<string, number>;
  xp: number;
  gamesPlayed: number;
  lessonsCompleted: number;
};

export type LessonInfo = { id: string; title: string; intro: string; level: number; exercises: number; steps: number };
export type Course = {
  id: string;
  title: string;
  description: string;
  chapters: { id: string; title: string; lessons: LessonInfo[] }[];
  done: number;
  total: number;
  stars: number;
};

export type LessonState = {
  lessonId: string;
  lessonTitle: string;
  index: number;
  count: number;
  type: "theory" | "stars" | "capture" | "puzzle" | "mate" | "goal" | "quiz" | "squares" | "best" | "line" | "play";
  prompt: string;
  par: number | null;
  squaresCount: number | null;
  mateIn: number | null;
  goal: string | null;
  playGoal: string | null;
  options: string[] | null;
  answer: number | null;
  position: Position | null;
  flipped: boolean;
  lastMove: string | null;
  stars: string[];
  marks: string[];
  arrows: string[];
  hintSquare: string | null;
  interactive: boolean;
  status: "active" | "wrong" | "solved" | "failed";
  message: string | null;
  speech: string | null;
  moves: number;
  busy: boolean;
  target: string | null;
  round: number;
  correct: number;
  chosen: number | null;
  promotion: Promotion;
};

export type LessonResult = { lessonId: string; mistakes: number; stars: number };
