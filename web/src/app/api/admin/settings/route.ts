import { currentUser } from "@/lib/auth";
import { body, fail, ok } from "@/lib/http";
import { setSiteSetting } from "@/lib/settings";

export async function POST(req: Request) {
  const admin = await currentUser();
  if (!admin || admin.role !== "admin") return fail("err_generic", 403);
  const b = await body<{ announcement?: string; registrationOpen?: boolean }>(req);
  if (typeof b.announcement === "string") await setSiteSetting("announcement", b.announcement.slice(0, 300));
  if (typeof b.registrationOpen === "boolean") await setSiteSetting("registrationOpen", String(b.registrationOpen));
  return ok();
}
