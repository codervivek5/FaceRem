"use client";

import Link from "next/link";
import { useMemo, useState } from "react";

import { Avatar, Card, EmptyState, EnrollmentPill, cx } from "@/components/ui";
import type { Employee } from "@/lib/types";

type Filter = "all" | "enrolled" | "pending";

const filters: Array<{ id: Filter; label: string }> = [
  { id: "all", label: "All" },
  { id: "enrolled", label: "Enrolled" },
  { id: "pending", label: "Needs enrollment" },
];

export default function EmployeeFilters({ employees }: { employees: Employee[] }) {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("all");

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return employees.filter((employee) => {
      if (filter === "enrolled" && !employee.face_enrolled) return false;
      if (filter === "pending" && employee.face_enrolled) return false;
      if (!needle) return true;
      return (
        employee.full_name.toLowerCase().includes(needle) ||
        employee.employee_code.toLowerCase().includes(needle) ||
        employee.department.toLowerCase().includes(needle)
      );
    });
  }, [employees, query, filter]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search name, code or department"
          aria-label="Search employees"
          className="h-11 min-w-64 flex-1 rounded-xl border border-ink-300 bg-white px-3.5 text-sm placeholder:text-ink-400 focus:border-brand-600 focus:outline-none"
        />
        <div className="flex gap-1.5">
          {filters.map((item) => (
            <button
              key={item.id}
              onClick={() => setFilter(item.id)}
              aria-pressed={filter === item.id}
              className={cx(
                "h-11 rounded-xl border px-3.5 text-sm font-medium transition-colors",
                filter === item.id
                  ? "border-brand-600 bg-brand-50 text-brand-700"
                  : "border-ink-300 bg-white text-ink-600 hover:bg-ink-50",
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <Card>
        {visible.length === 0 ? (
          <EmptyState title="No matches" body="Adjust the search or filter to see more people." />
        ) : (
          <ul className="divide-y divide-ink-200">
            {visible.map((employee) => (
              <li key={employee.id}>
                <Link
                  href={`/dashboard/employees/${employee.id}`}
                  className="flex items-center gap-3 px-5 py-3.5 hover:bg-ink-50"
                >
                  <Avatar name={employee.full_name} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink-900">
                      {employee.full_name}
                    </p>
                    <p className="truncate text-xs text-ink-500">
                      {employee.employee_code} · {employee.department}
                    </p>
                  </div>
                  <EnrollmentPill enrolled={employee.face_enrolled} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
