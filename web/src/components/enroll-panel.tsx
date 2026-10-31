"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";

import { Banner, Button, Card, SectionTitle } from "@/components/ui";

export default function EnrollPanel({
  employeeId,
  enrolled,
}: {
  employeeId: string;
  enrolled: boolean;
}) {
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [consent, setConsent] = useState(false);
  const [message, setMessage] = useState<{ tone: "info" | "danger"; text: string } | null>(null);
  const [pending, setPending] = useState(false);

  function choose(event: React.ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null;
    setFile(selected);
    setPreview(selected ? URL.createObjectURL(selected) : null);
    setMessage(null);
  }

  async function enroll() {
    if (!file) return;
    setPending(true);
    setMessage(null);

    const body = new FormData();
    body.append("image", file);
    body.append("consent", String(consent));

    const response = await fetch(`/api/proxy/api/v1/employees/${employeeId}/face/enroll`, {
      method: "POST",
      body,
    });
    const payload = await response.json();
    setPending(false);

    if (!response.ok || !payload.success) {
      setMessage({ tone: "danger", text: payload?.error?.message ?? "Enrollment failed." });
      return;
    }
    setMessage({ tone: "info", text: "Face profile created." });
    setFile(null);
    setPreview(null);
    if (fileRef.current) fileRef.current.value = "";
    router.refresh();
  }

  async function revoke() {
    setPending(true);
    setMessage(null);
    const response = await fetch(`/api/proxy/api/v1/employees/${employeeId}/face`, {
      method: "DELETE",
    });
    const payload = await response.json();
    setPending(false);

    if (!response.ok || !payload.success) {
      setMessage({ tone: "danger", text: payload?.error?.message ?? "Could not remove the profile." });
      return;
    }
    setMessage({ tone: "info", text: "Face profile removed. Attendance history was kept." });
    router.refresh();
  }

  return (
    <Card className="p-6">
      <SectionTitle>Face profile</SectionTitle>

      {enrolled ? (
        <div className="space-y-4">
          <p className="text-sm text-ink-600">
            A face profile is active. Re-enrolling replaces the stored embedding; removing it stops
            recognition but never deletes attendance history.
          </p>
          <Button variant="danger" onClick={revoke} disabled={pending}>
            {pending ? "Working…" : "Remove face profile"}
          </Button>
        </div>
      ) : (
        <p className="mb-4 text-sm text-ink-600">
          Upload a clear, front-facing photo. The server extracts a mathematical embedding — the
          photograph itself is not stored.
        </p>
      )}

      <div className="mt-4 space-y-4 border-t border-ink-200 pt-4">
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={choose}
          className="block w-full text-sm text-ink-600 file:mr-3 file:rounded-lg file:border-0 file:bg-ink-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-ink-700 hover:file:bg-ink-200"
        />

        {preview && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={preview}
            alt="Selected face preview"
            className="size-32 rounded-xl border border-ink-200 object-cover"
          />
        )}

        <label className="flex items-start gap-2.5 text-sm text-ink-700">
          <input
            type="checkbox"
            checked={consent}
            onChange={(event) => setConsent(event.target.checked)}
            className="mt-0.5 size-4"
          />
          <span>
            The employee has given informed consent to face enrollment. Consent and its timestamp
            are written to the audit log.
          </span>
        </label>

        {message && (
          <Banner tone={message.tone === "danger" ? "danger" : "info"}>{message.text}</Banner>
        )}

        <Button onClick={enroll} disabled={!file || !consent || pending}>
          {pending ? "Uploading…" : enrolled ? "Re-enroll face" : "Enroll face"}
        </Button>
      </div>
    </Card>
  );
}
