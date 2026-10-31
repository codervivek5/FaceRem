import { Avatar, Card, EmptyState, StatusPill, formatDate, formatTime } from "@/components/ui";
import { apiGet } from "@/lib/api";
import type { AttendanceRecord, Me } from "@/lib/types";

export default async function AttendancePage() {
  const [me, records] = await Promise.all([
    apiGet<Me>("/api/v1/auth/me"),
    apiGet<AttendanceRecord[]>("/api/v1/attendance"),
  ]);

  const byDate = records.reduce<Record<string, AttendanceRecord[]>>((groups, record) => {
    (groups[record.date] ??= []).push(record);
    return groups;
  }, {});
  const dates = Object.keys(byDate).sort().reverse();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-ink-900">Attendance</h1>
        <p className="mt-1 text-sm text-ink-500">
          {me.role === "EMPLOYEE"
            ? "Your own records. Organization-wide attendance is restricted to staff roles."
            : `${records.length} records across ${dates.length} days`}
        </p>
      </div>

      {dates.length === 0 ? (
        <Card>
          <EmptyState title="Nothing recorded yet" body="Attendance appears here after the first scan." />
        </Card>
      ) : (
        <div className="space-y-6">
          {dates.map((date) => (
            <section key={date}>
              <h2 className="mb-2 text-sm font-semibold text-ink-600">{formatDate(date)}</h2>
              <Card>
                <ul className="divide-y divide-ink-200">
                  {byDate[date].map((record) => (
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
              </Card>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
