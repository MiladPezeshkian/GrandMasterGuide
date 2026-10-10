import Image from "next/image";
import Link from "next/link";
import { serverT } from "@/i18n/server";

export async function Footer() {
  const { t } = await serverT();
  return (
    <footer className="border-t border-line bg-bg-2">
      <div className="mx-auto grid max-w-7xl gap-8 px-6 py-12 md:grid-cols-[1.4fr_1fr]">
        <div className="flex items-start gap-4">
          <Image src="/brand/emblem.png" alt="Zorix" width={92} height={56} className="h-14 w-auto" />
          <div>
            <p className="ltr font-brand text-sm font-black tracking-[0.2em]">
              <span className="text-gradient-accent">ZORIX</span> <span className="text-gradient-silver">CHESS</span>
            </p>
            <p className="mt-2 text-sm font-semibold text-text">{t("made_by")}</p>
            <p className="mt-1 text-sm text-dim">{t("free_forever")}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-start gap-x-6 gap-y-3 text-sm text-dim md:justify-end">
          <Link href="/credits" className="hover:text-text">
            {t("footer_credits")}
          </Link>
          <a href="https://github.com/MiladPezeshkian/GrandMasterGuide/releases" className="hover:text-text" rel="noopener" target="_blank">
            {t("footer_app")}
          </a>
        </div>
      </div>
      <div className="border-t border-line py-5 text-center text-xs text-faint">{t("footer_rights", { year: new Date().getFullYear() })}</div>
    </footer>
  );
}
