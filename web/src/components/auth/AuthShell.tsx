import Link from "next/link";
import { Logo } from "../site/Brand";
import { LangSwitcher, ThemeToggle } from "../site/Controls";
import { AuthShowcase } from "./AuthShowcase";

/** Layout of the account pages: the form on one side, a living board on the other. */
export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <div className="aurora pointer-events-none absolute inset-0 -z-10 opacity-70" />
      <div className="flex flex-col px-5 py-5 sm:px-10">
        <div className="flex items-center gap-1">
          <Logo />
          <div className="flex-1" />
          <LangSwitcher />
          <ThemeToggle />
        </div>
        <div className="flex flex-1 items-center justify-center py-10">
          <div className="w-full max-w-md">{children}</div>
        </div>
        <p className="text-center text-xs text-faint">
          <Link href="/" className="hover:text-dim">
            GrandMaster Guide · Zorix
          </Link>
        </p>
      </div>
      <div className="relative hidden overflow-hidden border-s border-line bg-bg-2 lg:block">
        <AuthShowcase />
      </div>
    </div>
  );
}
