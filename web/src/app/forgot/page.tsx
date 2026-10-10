import { redirect } from "next/navigation";
import { Suspense } from "react";
import { AuthShell } from "@/components/auth/AuthShell";
import { ForgotForm } from "@/components/auth/Forms";
import { currentUser } from "@/lib/auth";

export default async function Page() {
  if (await currentUser().catch(() => null)) redirect("/app");
  return (
    <AuthShell>
      <Suspense>
        <ForgotForm />
      </Suspense>
    </AuthShell>
  );
}
