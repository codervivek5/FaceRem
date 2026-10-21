import { redirect } from "next/navigation";

import LoginForm from "@/components/login-form";
import { Wordmark } from "@/components/ui";
import { getAccessToken } from "@/lib/session";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ expired?: string }>;
}) {
  const { expired } = await searchParams;
  if (!expired && (await getAccessToken())) redirect("/dashboard");

  return (
    <main className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <Wordmark className="mb-10" />
          <h1 className="text-2xl font-bold tracking-tight text-ink-900">Welcome back</h1>
          <p className="mt-1.5 text-sm text-ink-600">
            Sign in to your organization&apos;s attendance workspace.
          </p>
          <LoginForm sessionExpired={Boolean(expired)} />
        </div>
      </div>

      <aside className="hidden flex-col justify-between bg-gradient-to-br from-brand-700 to-violet-700 p-12 text-white lg:flex">
        <p className="text-sm font-medium text-white/70">FaceAttend console</p>
        <div>
          <p className="text-3xl font-bold leading-tight">
            Enroll once.
            <br />
            Check in with a glance.
          </p>
          <p className="mt-4 max-w-sm text-white/80">
            Recognition runs on your organization&apos;s server, scoped to your tenant, with consent
            and an audit trail on every biometric action.
          </p>
        </div>
        <p className="text-xs text-white/60">Secure · Private · Multi-tenant</p>
      </aside>
    </main>
  );
}
