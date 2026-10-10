import { redirect } from "next/navigation";
import { currentUser } from "@/lib/auth";

/** The short invite link: signed-in players go straight to the game, others sign in (or up) first. */
export default async function Invite({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const target = `/app/game/${encodeURIComponent(id)}`;
  if (await currentUser().catch(() => null)) redirect(target);
  redirect(`/login?next=${encodeURIComponent(target)}`);
}
