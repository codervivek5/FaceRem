import Link from "next/link";

import {
  Avatar,
  Banner,
  ButtonLink,
  Card,
  EmptyState,
  SectionTitle,
  StatCard,
  StatusPill,
  formatTime,
} from "@/components/ui";
import { ApiError, apiGet } from "@/lib/api";
import type { AttendanceRecord, Dashboard, Me } from "@/lib/types";
import { canRunScans } from "@/lib/types";

export default async function OverviewPage() {
  const me = await apiGet<Me>("/api/v1/auth/me");

  // A MANAGER can read the dashboard; an EMPLOYEE cannot. Rather than hiding the page, show
  // the part of it they are allowed to see.
  let summary: Dashboard | null = null;
  try {
    summary = await apiGet<Dashboard>("/api/v1/dashboard");
  } catch (error) {
    if (!(error instanceof ApiError) || error.code !== "FORBIDDEN") throw error;
  }

  const today = new Date().toISOString().slice(0, 10);
  const records = await apiGet<AttendanceRecord[]>(`/api/v1/attendance?on=${today}`);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-ink-900">
            {summary?.organization_name ?? me.organization_name ?? "Your workspace"}
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            {new Date().toLocaleDateString([], {
              weekday: "long",
              day: "numeric",
              month: "long",
              year: "numeric",
            })}
          </p>
        </div>
        {canRunScans(me.role) && <ButtonLink href="/dashboard/scan">Open scanner</ButtonLink>}
      </div>

      {summary ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Present today"
            value={summary.present}
            caption={`of ${summary.total_employees} employees`}
            tone="success"
          />
          <StatCard label="Late arrivals" value={summary.late} tone="warning" />
          <StatCard label="Not checked in" value={summary.not_checked_in} tone="danger" />
          <StatCard
            label="Awaiting enrollment"
            value={summary.pending_enrollments}
            caption="face profile missing"
          />
        </div>
      ) : (
        <Banner tone="info">
          Your role can see your own attendance, but not organization-wide totals.
        </Banner>
      )}

      <section>
        <SectionTitle
          action={
            <Link
              href="/dashboard/attendance"
              className="text-sm font-medium text-brand-700 hover:text-brand-800"
            >
              See all
            </Link>
          }
        >
          Today&apos;s activity
        </SectionTitle>

        <Card>
          {records.length === 0 ? (
            <EmptyState
              title="No check-ins yet today"
              body="Records appear here as soon as someone is recognized by the scanner."
            />
          ) : (
            <ul className="divide-y divide-ink-200">
              {records.map((record) => (
                <li key={record.id} className="flex items-center gap-3 px-5 py-3.5">
                  <Avatar name={record.employee_name} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink-900">
                      {record.employee_name}
                    </p>
                    <p className="truncate text-xs text-ink-500">
                      in {formatTime(record.check_in_at)} · out {formatTime(record.check_out_at)}
                      {record.late_minutes > 0 && ` · ${record.late_minutes}m late`}
                    </p>
                  </div>
                  <StatusPill status={record.status} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </section>
    </div>
  );
}
