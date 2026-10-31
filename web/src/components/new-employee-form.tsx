"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Banner, Button, Card, Field } from "@/components/ui";

const initial = {
  full_name: "",
  employee_code: "",
  email: "",
  department: "General",
  job_title: "",
};

export default function NewEmployeeForm() {
  const router = useRouter();
  const [form, setForm] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function update(field: keyof typeof initial) {
    return (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((current) => ({ ...current, [field]: event.target.value }));
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);

    const response = await fetch("/api/proxy/api/v1/employees", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(form),
    });
    const body = await response.json();

    if (!response.ok || !body.success) {
      setError(body?.error?.message ?? "The employee could not be created.");
      setPending(false);
      return;
    }

    router.push(`/dashboard/employees/${body.data.id}`);
    router.refresh();
  }

  return (
    <Card className="mt-6 p-6">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Field label="Full name" required value={form.full_name} onChange={update("full_name")} />
        <Field
          label="Work email"
          type="email"
          required
          value={form.email}
          onChange={update("email")}
        />
        <Field
          label="Employee code"
          required
          hint="Unique inside your organization"
          value={form.employee_code}
          onChange={update("employee_code")}
        />
        <Field label="Department" value={form.department} onChange={update("department")} />
        <Field label="Job title" value={form.job_title} onChange={update("job_title")} />

        {error && <Banner tone="danger">{error}</Banner>}

        <div className="flex gap-3 pt-1">
          <Button type="submit" disabled={pending}>
            {pending ? "Saving…" : "Save employee"}
          </Button>
          <Button type="button" variant="secondary" onClick={() => router.back()}>
            Cancel
          </Button>
        </div>
      </form>
    </Card>
  );
}
