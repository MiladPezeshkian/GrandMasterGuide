import { Home } from "@/components/school/Home";
import { currentUser } from "@/lib/auth";

export default async function Page() {
  const user = await currentUser();
  return <Home username={user?.username ?? ""} />;
}
