import Image from "next/image";
import Link from "next/link";

/** The Zorix "ZC" emblem with the GRANDMASTER GUIDE wordmark (red / chrome, as in the app). */
export function Logo({ compact = false, href = "/" }: { compact?: boolean; href?: string }) {
  return (
    <Link href={href} className="group flex items-center gap-2.5" aria-label="GrandMaster Guide — Zorix">
      <Image src="/brand/emblem.png" alt="" width={46} height={28} priority className="h-7 w-auto drop-shadow-[0_0_12px_var(--glow)] transition-transform group-hover:scale-105" />
      {!compact && (
        <span className="ltr flex flex-col leading-none">
          <span className="font-brand text-[0.95rem] font-black tracking-[0.18em]">
            <span className="text-gradient-accent">GRANDMASTER</span> <span className="text-gradient-silver">GUIDE</span>
          </span>
          <span className="mt-1 font-brand text-[0.55rem] font-bold tracking-[0.42em] text-faint">BY ZORIX</span>
        </span>
      )}
    </Link>
  );
}
