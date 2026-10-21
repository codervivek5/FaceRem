import Nav from "@/components/nav";
import { apiGet } from "@/lib/api";
import type { Me } from "@/lib/types";

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  // Loading the principal here also validates the session for every nested page.
  const me = await apiGet<Me>("/api/v1/auth/me");

  return (
    <div className="min-h-dvh">
      <Nav me={me} />
      <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
    </div>
  );
}
