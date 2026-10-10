import { OnlineGameView } from "@/components/game/OnlineGameView";

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <OnlineGameView id={id} />;
}
