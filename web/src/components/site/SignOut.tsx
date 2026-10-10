"use client";

import { LogOut } from "lucide-react";

export function SignOutButton({ label, className = "", iconOnly = false }: { label: string; className?: string; iconOnly?: boolean }) {
  return (
    <button
      className={`btn btn-quiet ${className}`}
      aria-label={label}
      onClick={async () => {
        await fetch("/api/auth/logout", { method: "POST" });
        window.location.href = "/";
      }}
    >
      <LogOut size={17} />
      {!iconOnly && <span className="text-sm">{label}</span>}
    </button>
  );
}
