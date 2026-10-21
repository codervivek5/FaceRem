# FaceAttend web

The desktop console for the FaceAttend platform, and the public landing page. Next.js 15 App
Router, React 19, Tailwind v4, TypeScript. It consumes the FastAPI service in [`../backend`](../backend).

```bash
cd web
npm install
cp .env.example .env.local        # point API_BASE_URL at the backend
npm run dev                       # http://localhost:3000
```

The backend must be running:

```bash
cd ../backend && source .venv/bin/activate && uvicorn app.main:app --port 8000
```

Sign in with any seeded account, password `demo12345` — `hr@northwind.demo` for the fullest view,
`admin@northwind.demo` to also see the audit log, `employee@northwind.demo` to see the restricted
view a regular employee gets.

## Pages

| Route | Who | What |
| --- | --- | --- |
| `/` | public | Landing page |
| `/login` | public | Sign in |
| `/dashboard` | any signed-in user | Today's totals and activity |
| `/dashboard/employees` | staff + managers | Searchable roster with enrollment status |
| `/dashboard/employees/new` | ORG_ADMIN, HR_ADMIN | Create an employee |
| `/dashboard/employees/[id]` | staff + managers | Detail, face enrollment and revocation, history |
| `/dashboard/scan` | ORG_ADMIN, HR_ADMIN, device | Webcam capture → server recognition, with a continuous kiosk loop |
| `/dashboard/attendance` | all (employees see only their own) | Records grouped by date |
| `/dashboard/audit` | ORG_ADMIN | Biometric and auth audit trail |

## Why the browser never sees a token

The API returns JWTs, but a token in `localStorage` is one XSS away from being exfiltrated — and
this product's tokens unlock biometric data. So this app is a thin BFF:

```text
browser ──> Next.js route handler ──> FastAPI
         (httpOnly cookie)        (Authorization: Bearer …)
```

- `POST /api/auth/login` calls the API and puts both tokens in **httpOnly, SameSite=Lax** cookies.
  The browser receives `{ok: true}` and nothing else.
- Server Components read the cookie and call the API directly (`lib/api.ts`), so most pages need
  no client-side fetching at all.
- The few calls that must start in the browser — creating an employee, uploading an enrollment
  frame, running a scan, revoking a profile — go through `/api/proxy/[...path]`, which attaches
  the token server-side. That proxy is a **per-method allowlist**, not an open relay.
- Set `COOKIE_SECURE=1` when serving over HTTPS.

`middleware.ts` bounces anonymous visitors off `/dashboard` before rendering. That is convenience,
not security: every authorization decision is still made by the API.

## Roles in the UI

Navigation and buttons are hidden for roles that cannot use them, and pages like
`/dashboard/scan` and `/dashboard/audit` redirect. This is purely to avoid dead ends — the server
rejects the request regardless, and `/dashboard` deliberately degrades rather than erroring when a
`MANAGER` may read attendance but not organization totals.

## Known gaps

- **The web scanner has no liveness detection.** A held-up photo would pass it. It sends
  `liveness_score=1.0` and labels itself `web-console` so those scans are identifiable in the
  audit log. Real kiosks should use the Android client, where the on-device SDK produces a score
  the server enforces. Treat this page as an operator tool, not a turnstile.
- **The web kiosk loop captures on a timer**, because a browser has no face detector to wait for.
  The Android scanner waits for a stable face instead, which is why it is the better gate device.
  Repeated captures of the same person are harmless: the server answers `ALREADY_PRESENT` and
  writes nothing.
- Access tokens are not refreshed automatically; when one expires the user is sent back to
  `/login`. Wiring `/api/v1/auth/refresh` into the route handler is the next step.
- No pagination on employees, attendance or the audit log.
- No optimistic UI — mutations call `router.refresh()` and re-render from the server.
