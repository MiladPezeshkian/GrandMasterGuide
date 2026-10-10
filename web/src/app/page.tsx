import { Footer } from "@/components/site/Footer";
import { Header } from "@/components/site/Header";
import { Landing } from "@/components/site/Landing";
import { currentUser } from "@/lib/auth";

export default async function Home() {
  const user = await currentUser().catch(() => null);
  return (
    <>
      <Header />
      <Landing signedIn={!!user} />
      <Footer />
    </>
  );
}
