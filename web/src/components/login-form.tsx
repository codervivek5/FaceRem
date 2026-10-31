"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Banner, Button, Field } from "@/components/ui";

const DEMO = { email: "hr@northwind.demo", password: "demo12345" };

export default function LoginForm({ sessionExpired }: { sessionExpired: boolean }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [organization, setOrganization] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, organization_slug: organization }),
      });
      const body = await response.json();

      if (!response.ok) {
        setError(body.message ?? "Sign in failed.");
        return;
      }
      // The session lives in httpOnly cookies, so a refresh is what makes it visible.
      router.replace("/dashboard");
      router.refresh();
    } catch {
      setError("Could not reach the attendance service.");
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-8 space-y-4">
      {sessionExpired && <Banner tone="warning">Your session expired. Please sign in again.</Banner>}

      <Field
        label="Organization code"
        hint="Optional — leave empty for a single-tenant account"
        value={organization}
        onChange={(event) => setOrganization(event.target.value)}
        placeholder="northwind"
        autoComplete="organization"
      />
      <Field
        label="Work email"
        type="email"
        required
        value={email}
        onChange={(event) => setEmail(event.target.value)}
        placeholder="you@company.com"
        autoComplete="username"
      />
      <Field
        label="Password"
        type="password"
        required
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        autoComplete="current-password"
      />

      {error && <Banner tone="danger">{error}</Banner>}

      <Button type="submit" className="w-full" disabled={pending}>
        {pending ? "Signing in…" : "Sign in"}
      </Button>

      <button
        type="button"
        onClick={() => {
          setEmail(DEMO.email);
          setPassword(DEMO.password);
          setOrganization("northwind");
        }}
        className="w-full text-center text-sm font-medium text-brand-700 hover:text-brand-800"
      >
        Fill demo HR credentials
      </button>
    </form>
  );
}
