import EmployeeFilters from "@/components/employee-filters";
import { ButtonLink, Card, EmptyState } from "@/components/ui";
import { apiGet } from "@/lib/api";
import type { Employee, Me } from "@/lib/types";
import { canManagePeople } from "@/lib/types";

export default async function EmployeesPage() {
  const [me, employees] = await Promise.all([
    apiGet<Me>("/api/v1/auth/me"),
    apiGet<Employee[]>("/api/v1/employees"),
  ]);

  const pending = employees.filter((employee) => !employee.face_enrolled).length;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-ink-900">People</h1>
          <p className="mt-1 text-sm text-ink-500">
            {employees.length} employees · {pending} awaiting face enrollment
          </p>
        </div>
        {canManagePeople(me.role) && (
          <ButtonLink href="/dashboard/employees/new">Add employee</ButtonLink>
        )}
      </div>

      {employees.length === 0 ? (
        <Card>
          <EmptyState
            title="No employees yet"
            body="Add your first employee to start enrolling face profiles."
          />
        </Card>
      ) : (
        <EmployeeFilters employees={employees} />
      )}
    </div>
  );
}
