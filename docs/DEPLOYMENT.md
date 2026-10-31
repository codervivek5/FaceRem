# Deployment

What the system is made of, what the database looks like, and what has to change before
any of it faces real users.

## The three pieces

```text
Android app  (app/)      Kotlin · Compose · CameraX · ML Kit
     |                   Detects a face on device, uploads one frame.
     v
FastAPI      (backend/)  Python · SQLAlchemy · Alembic · InsightFace
     |                   Decides identity. The only component that may.
     v
PostgreSQL               Tenant data, face embeddings, attendance, audit trail.

Web console  (web/)      Next.js · talks to the same API over httpOnly cookies
```

Only the backend needs deploying for the app to work. The web console is optional and can
go on any Node host; the Android app ships as an APK.

## Database

Eleven tables. Every tenant-owned row carries `organization_id`, and every query filters on
it — that column is the tenant boundary, not a convenience.

```text
organizations ─┬─ subscriptions      (1:1)  plan, seat_limit, status
               ├─ users              (n)    login + role  ──┐
               ├─ departments        (n)                    │
               ├─ branches           (n)                    │
               ├─ shifts             (n)                    │
               ├─ employees          (n) ──┬─ department_id │
               │                           ├─ branch_id     │
               │                           └─ shift_id      │
               ├─ face_profiles      (n) ── employee_id     │
               └─ attendance_records (n) ── employee_id     │
                                                            │
refresh_tokens ──────────────────────── user_id ────────────┘
audit_logs      (no FKs — append-only, survives deletions)
```

Constraints worth knowing about:

| Table | Rule | Why |
| --- | --- | --- |
| `organizations` | `slug` unique | tenant handle used at sign-in |
| `users` | unique `(organization_id, email)` | same email may exist in two tenants |
| `employees` | unique `(organization_id, employee_code)` | codes only need to be unique per tenant |
| `attendance_records` | unique `(organization_id, employee_id, date)` | one row per person per day |
| `subscriptions` | `organization_id` unique | one plan per tenant |
| `face_profiles` | soft-deleted via `revoked_at` | removing biometrics must not delete attendance |

`audit_logs` deliberately has no foreign keys: it has to outlive the rows it describes.

### Face embeddings

`face_profiles.embedding` is JSON today and searched in Python. That is fine to a few
hundred employees per tenant and becomes the bottleneck after that. The upgrade is pgvector:

```sql
CREATE EXTENSION vector;
ALTER TABLE face_profiles ALTER COLUMN embedding TYPE vector(512);
CREATE INDEX ON face_profiles USING hnsw (embedding vector_cosine_ops);
```

512 is the ArcFace dimension. Do this only after the face engine is settled — the column
width is model-specific, and changing models means re-enrolling everyone anyway.

## Deploying the backend

**1. Database.** Any managed Postgres. Create the database, then:

```bash
export DATABASE_URL='postgresql+psycopg://user:pass@host:5432/faceattend'
alembic upgrade head
```

Alembic owns the schema. The app refuses to start if migrations have not run rather than
creating tables behind Alembic's back.

**2. The face model.** InsightFace downloads ~630 MB to `~/.insightface` on first use. On a
container that means every deploy re-downloads it and the first request hangs for a minute.
Bake it into the image, or mount it as a persistent volume:

```dockerfile
RUN python -c "from insightface.app import FaceAnalysis; \
    FaceAnalysis(name='buffalo_l').prepare(ctx_id=-1)"
```

**3. Run it.** Behind a reverse proxy that terminates TLS — the app speaks plain HTTP:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
```

Workers each load their own copy of the model, so size the instance for
`workers x ~1 GB` plus overhead.

## Production configuration

| Setting | Must be | What happens otherwise |
| --- | --- | --- |
| `APP_ENV` | `production` | CORS stays wide open to `*` |
| `JWT_SECRET` | long random value | **app refuses to start** with the dev default |
| `DATABASE_URL` | Postgres | SQLite will not survive more than one worker |
| `SEED_DEMO_DATA` | `false` | demo tenants and known passwords get created |
| `FACE_ENGINE` | `insightface` | the stub is not a face recogniser |

Generate the secret with `python -c "import secrets; print(secrets.token_urlsafe(48))"`.
Changing it signs everyone out, which is the intended way to respond to a leak.

## Not ready for production

Be clear-eyed about these before putting real attendance data behind it.

**Liveness is self-reported.** `LIVENESS_MODE=client_attested` means the server trusts a
score the client sends. A modified client can send `1.0` forever, and a photo held to the
camera already passes. For an attendance system this is the hole that matters: it is the
difference between proving someone was present and proving someone's picture was. Fix it
with on-device attestation the server can verify (Play Integrity plus an SDK-signed result),
or move liveness server-side, before anyone's pay depends on it.

**No rate limiting.** `/auth/login` will accept unlimited attempts. Add it at the proxy.

**Access tokens cannot be revoked.** They are short-lived instead (30 minutes). Refresh
tokens *are* revocable and rotate on every use, with replay treated as theft.

**No password reset.** The endpoint does not exist; the Android screen is not wired to it.
Admins must reset passwords for people.

**Attendance history is not paginated.** `GET /attendance` returns everything the caller may
see. That is fine for a pilot and will not be at a few thousand records.

## Android release build

The debug build talks to a dev machine over cleartext HTTP. The release build has no such
exemption and will only talk HTTPS, so point it at the deployed API:

```kotlin
// app/build.gradle.kts, release block
buildConfigField("String", "API_BASE_URL", "\"https://api.yourdomain.com/\"")
```

Then sign it. Signing config and keystore are not in this repo and must never be.
