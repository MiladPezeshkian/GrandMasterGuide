import Link from "next/link";
import { currentUser } from "@/lib/auth";
import { serverT } from "@/i18n/server";
import { Logo } from "./Brand";
import { LangSwitcher, ThemeToggle } from "./Controls";
import { SignOutButton } from "./SignOut";

/** Top bar of the public pages. */
export async function Header() {
  const { t } = await serverT();
  const user = await currentUser().catch(() => null);
  return (
    <header className="sticky top-0 z-40 border-b border-[var(--glass-line)] bg-[color-mix(in_srgb,var(--bg)_86%,transparent)] backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-2 px-4 sm:px-6">
        <Logo />
        <div className="flex-1" />
        <LangSwitcher />
        <ThemeToggle />
        {user ? (
          <>
            {user.role === "admin" && (
              <Link href="/admin" className="btn btn-quiet hidden sm:inline-flex">
                {t("nav_admin")}
              </Link>
            )}
            <SignOutButton label={t("nav_logout")} className="hidden sm:inline-flex" />
            <Link href="/app" className="btn btn-primary">
              {t("nav_app")}
            </Link>
          </>
        ) : (
          <>
            <Link href="/login" className="btn btn-quiet">
              {t("nav_login")}
            </Link>
            <Link href="/register" className="btn btn-primary hidden sm:inline-flex">
              {t("nav_register")}
            </Link>
          </>
        )}
      </div>
    </header>
  );
}
