import { currentUser } from "@/lib/auth";
import { gameView, loadGame } from "@/lib/games";
import { fail, ok } from "@/lib/http";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  const u = await currentUser();
  if (!u) return fail("err_generic", 401);
  const g = await loadGame((await params).id);
  if (!g) return fail("game_not_found", 404);
  return ok({ game: await gameView(g, u.id) });
}
