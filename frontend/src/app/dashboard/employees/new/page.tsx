import { redirect } from "next/navigation";

import NewEmployeeForm from "@/components/new-employee-form";
import { apiGet } from "@/lib/api";
import type { Me } from "@/lib/types";
import { canManagePeople } from "@/lib/types";

export default async function NewEmployeePage() {
  const me = await apiGet<Me>("/api/v1/auth/me");
  // Defence in depth: the server rejects this too, but there is no point rendering the form.
  if (!canManagePeople(me.role)) redirect("/dashboard/employees");

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold tracking-tight text-ink-900">Add employee</h1>
      <p className="mt-1 text-sm text-ink-500">
        Face enrollment happens after saving, with consent recorded.
      </p>
      <NewEmployeeForm />
    </div>
  );
}
