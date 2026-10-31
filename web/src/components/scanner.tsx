"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { Avatar, Banner, Button, Card, StatusPill, cx, formatTime } from "@/components/ui";
import { EVENT_LABELS, type RecognitionResult } from "@/lib/types";

type Phase = "idle" | "starting" | "ready" | "scanning" | "result" | "error";

/** How long a result stays up before an unattended kiosk arms itself for the next person. */
const HOLD_MILLIS = { success: 2600, repeat: 2000, failure: 3600 } as const;
/** Gap between automatic captures while waiting for someone to step up. */
const POLL_MILLIS = 1500;

export default function Scanner() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const busyRef = useRef(false);

  const [phase, setPhase] = useState<Phase>("idle");
  const [kiosk, setKiosk] = useState(true);
  const [result, setResult] = useState<RecognitionResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [scanCount, setScanCount] = useState(0);

  const clearTimer = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = null;
  }, []);

  const stop = useCallback(() => {
    clearTimer();
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, [clearTimer]);

  // Release the camera when the operator navigates away.
  useEffect(() => stop, [stop]);

  async function start() {
    setPhase("starting");
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user", width: 640, height: 480 },
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setPhase("ready");
    } catch {
      setError("Camera access was blocked. Allow it in the browser, or use the Android client.");
      setPhase("error");
    }
  }

  const capture = useCallback(async () => {
    const video = videoRef.current;
    if (!video || busyRef.current) return;
    busyRef.current = true;

    setPhase("scanning");
    setError(null);

    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    canvas.getContext("2d")?.drawImage(video, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, "image/jpeg", 0.92),
    );
    if (!blob) {
      busyRef.current = false;
      setError("Could not capture a frame.");
      setPhase("ready");
      return;
    }

    const body = new FormData();
    body.append("image", new File([blob], "scan.jpg", { type: "image/jpeg" }));
    // The browser has no liveness SDK; the server records this as an unattested scan.
    body.append("liveness_score", "1.0");
    body.append("device_label", "web-console");

    const response = await fetch("/api/proxy/api/v1/attendance/recognize", { method: "POST", body });
    const payload = await response.json();
    busyRef.current = false;

    if (!response.ok || !payload.success) {
      setResult(null);
      setError(payload?.error?.message ?? "Recognition failed.");
      setPhase("result");
      return;
    }

    setResult(payload.data as RecognitionResult);
    setScanCount((count) => count + 1);
    setPhase("result");
  }, []);

  // The kiosk loop. A browser has no face detector, so instead of waiting for a stable face it
  // captures on a slow timer — good enough for an operator tool, and the server's
  // ALREADY_PRESENT reply makes repeated captures of the same person harmless.
  useEffect(() => {
    if (!kiosk) return;
    clearTimer();

    if (phase === "ready") {
      timerRef.current = setTimeout(capture, POLL_MILLIS);
    } else if (phase === "result") {
      const hold = error
        ? HOLD_MILLIS.failure
        : result?.event === "ALREADY_PRESENT"
          ? HOLD_MILLIS.repeat
          : HOLD_MILLIS.success;
      timerRef.current = setTimeout(() => {
        setResult(null);
        setError(null);
        setPhase("ready");
      }, hold);
    }

    return clearTimer;
  }, [kiosk, phase, result, error, capture, clearTimer]);

  const isRepeat = result?.event === "ALREADY_PRESENT";
  const running = phase !== "idle" && phase !== "error";

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-sm font-medium text-ink-700">
          <input
            type="checkbox"
            checked={kiosk}
            onChange={(event) => setKiosk(event.target.checked)}
            className="size-4"
          />
          Kiosk mode — scan continuously, no clicking
        </label>
        {scanCount > 0 && <span className="text-sm text-ink-500">{scanCount} scanned</span>}
      </div>

      <Card className="overflow-hidden p-0">
        <div className="relative aspect-[4/3] bg-ink-900">
          <video
            ref={videoRef}
            playsInline
            muted
            className="size-full object-cover"
            // Mirror the preview so it behaves like a mirror for the person in front of it.
            style={{ transform: "scaleX(-1)" }}
          />
          {phase === "idle" && (
            <div className="absolute inset-0 grid place-items-center text-sm text-white/70">
              Camera is off
            </div>
          )}
          {running && (
            <div className="pointer-events-none absolute inset-0 grid place-items-center">
              <div
                className={cx(
                  "h-[70%] w-[52%] rounded-[50%] border-4 transition-colors",
                  phase === "scanning" ? "border-white/70" : "border-brand-400/80",
                )}
              />
            </div>
          )}
          {phase === "scanning" && (
            <div className="absolute inset-0 grid place-items-center bg-black/50 text-sm font-medium text-white">
              Matching face…
            </div>
          )}
          {phase === "result" && (result || error) && (
            <div className="absolute inset-0 grid place-items-center bg-black/70 p-6">
              {result ? (
                <div className="w-full max-w-sm rounded-2xl bg-white p-6 text-center">
                  <Avatar name={result.full_name} className="mx-auto size-14 text-base" />
                  <p className="mt-3 text-xl font-bold text-ink-900">{result.full_name}</p>
                  <p className="text-sm text-ink-500">
                    {result.employee_code} · {result.department}
                  </p>
                  <p
                    className={cx(
                      "mt-4 text-lg font-bold",
                      isRepeat ? "text-info-fg" : "text-success",
                    )}
                  >
                    {EVENT_LABELS[result.event]}
                  </p>
                  <p className="mt-0.5 text-sm text-ink-600">
                    {isRepeat
                      ? result.check_out_at
                        ? `In ${formatTime(result.check_in_at)} · out ${formatTime(result.check_out_at)}`
                        : `In since ${formatTime(result.check_in_at)}`
                      : `at ${formatTime(result.recorded_at)}`}
                  </p>
                  <div className="mt-3 flex justify-center">
                    <StatusPill status={result.status} />
                  </div>
                  <p className="mt-3 text-xs text-ink-400">
                    Match {(result.match_confidence * 100).toFixed(1)}%
                  </p>
                </div>
              ) : (
                <div className="w-full max-w-sm rounded-2xl bg-white p-6 text-center">
                  <p className="text-lg font-bold text-danger-fg">Not recognized</p>
                  <p className="mt-1 text-sm text-ink-600">{error}</p>
                </div>
              )}
            </div>
          )}
        </div>
      </Card>

      {phase === "error" && error && <Banner tone="danger">{error}</Banner>}

      <div className="flex flex-wrap gap-3">
        {!running ? (
          <Button onClick={start}>Start camera</Button>
        ) : (
          <>
            {!kiosk && (
              <Button onClick={capture} disabled={phase === "scanning" || phase === "starting"}>
                {phase === "scanning" ? "Scanning…" : "Capture and identify"}
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={() => {
                stop();
                setPhase("idle");
                setResult(null);
                setError(null);
              }}
            >
              Stop camera
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
