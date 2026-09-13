# Phase 0 — Architecture and Planning

## Goal

Define an implementation-ready architecture for FaceAttend before production features are added. This phase adds documentation only; it does not modify Android application behaviour or add backend code.

## 1. Android architecture

### Decision: start as a clean package-based Android app

The repository currently contains a single `:app` module. Start Phase 1 with a package-based monolith to keep the foundation buildable and avoid premature Gradle-module overhead. Preserve clear boundaries so modules can be extracted when features have stable interfaces.

```text
app/src/main/java/com/facerem/
  app/                    # application, navigation, dependency wiring
  core/
    common/               # Result, dispatchers, shared utilities
    model/                # domain entities and enums
    network/              # Retrofit, interceptors, API error mapping
    database/             # Room database and DAOs
    datastore/            # session/preferences storage
    designsystem/         # reusable Compose components and theme
    camera/               # CameraX and face-analysis abstractions
  feature/
    auth/
    dashboard/
    employees/
    faceenrollment/
    attendance/
    history/
    reports/
    settings/
```

Each feature follows this flow:

```text
Compose screen -> ViewModel -> use case -> repository interface
               -> repository implementation -> remote/local source
```

### Android conventions

- Use `PascalCase` for types and `camelCase` for functions, properties, routes, and DTO fields where Kotlin serialization mapping is not required.
- Keep public UI states immutable (`StateFlow<FeatureUiState>`). Model screen states explicitly: loading, content, empty, and recoverable error.
- Keep domain models independent from HTTP DTOs and Room entities. Map at repository boundaries.
- Model expected operation failures with a small `AppResult`/error model, not exception handling in Compose.
- Map HTTP 400, 401, 403, 404, 409, 422, 429, 5xx, unavailable network, timeouts, and DNS errors centrally to safe UI messages and machine-readable categories.
- Persist only safe caches. Store tokens in a secure Android mechanism; never store passwords.
- Use Navigation Compose with typed route arguments or IDs only. Fetch sensitive records from repositories after authorization rather than passing them through navigation.

### Authentication and offline strategy

- An OkHttp auth interceptor attaches the access token.
- A single refresh coordinator prevents duplicate refresh calls and refresh loops.
- On failed refresh, clear the session and route to login.
- Room caches current-user data, basic organization data, recent attendance, safe employee summaries, and pending non-sensitive operations.
- WorkManager retries safe sync work with network constraints.
- Recognition remains online-only for the MVP. The UI must state that internet is required for facial verification rather than silently creating authoritative offline attendance records.

### Camera strategy

- Bind CameraX preview and analysis to the screen lifecycle.
- Configure `ImageAnalysis` to keep only the latest frame.
- Close every `ImageProxy` in all success, error, and cancellation paths.
- ML Kit supplies face count, bounding box, pose, and optional eye-state quality cues only.
- Throttle analysis and recognition. A stable, high-quality frame leads to one request, then a cooldown; never issue an API request for every video frame.

## 2. Backend architecture

```text
backend/
  app/
    api/v1/               # routers and dependency injection
    core/                 # configuration, logging, security, exceptions
    db/                   # engine, sessions, migrations helpers
    models/               # SQLAlchemy persistence models
    schemas/              # Pydantic request/response schemas
    repositories/         # data access with tenant-scoped queries
    services/
      auth/
      organizations/
      employees/
      attendance/
      face/
      liveness/
      reports/
      audit/
    main.py
  migrations/
  tests/
  Dockerfile
  docker-compose.yml
  requirements/
```

FastAPI routers are thin: validate request schemas, resolve the authenticated tenant context, call services, and return a standard response envelope. Business policies reside in services; repository methods use explicit tenant-scoped query parameters. SQLAlchemy and Alembic manage PostgreSQL schema changes. Redis supports rate limiting, short-lived recognition cooldowns, and safe caching where appropriate.

## 3. Database architecture

Core tables and tenant ownership:

| Table | Purpose | Tenant rule |
| --- | --- | --- |
| `organizations` | SaaS tenant metadata and status | root tenant record |
| `users`, `roles`, `refresh_tokens` | identities, roles, sessions | user belongs to an organization except platform admins |
| `employees` | employee profile and employment state | `organization_id` required |
| `departments`, `branches`, `shifts` | configurable organization settings | `organization_id` required |
| `face_profiles` | model-versioned face embeddings and consent lifecycle | `organization_id` and `employee_id` required |
| `attendance_records` | authoritative daily attendance events/calculations | `organization_id` and `employee_id` required |
| `attendance_devices` | approved scanner devices | `organization_id` required |
| `holidays`, `leave_requests` | attendance-rule inputs | `organization_id` required |
| `audit_logs` | security-sensitive events | `organization_id` where applicable |
| `subscriptions` | plan/status lifecycle | `organization_id` required |

Required indexes include `(organization_id, employee_code)`, `(organization_id, employee_id, date)`, `(organization_id, status)`, and organization-scoped indexes for search/filter fields. Store embeddings with pgvector in `face_profiles.embedding`; never serialize them as JSON. Choose an HNSW or IVFFlat vector index after measuring production data volume and query latency.

## 4. Multi-tenant architecture

```text
Authenticated user or device token
             |
             v
Resolved server-side tenant context
             |
             v
Role/permission policy + organization-scoped repository query
             |
             v
Tenant-owned resource
```

- Never trust an `organizationId` submitted by the Android client as authorization.
- Every service call receives authenticated actor and tenant context.
- Platform `SUPER_ADMIN` actions use separate, explicit policies; organization app navigation never offers those capabilities.
- A request for an object outside the resolved tenant returns `404` where concealment is appropriate or `403` when policy requires it.
- Recognition queries always filter face profiles by the authorized organization before vector search.

## 5. Face-recognition architecture

### Enrollment

```text
Android camera -> ML Kit detection -> quality evaluator -> selected frames
-> HTTPS multipart upload -> backend face detection/alignment/quality validation
-> ArcFace embedding -> normalization -> pgvector storage
```

Require one face only. Reject no face, multiple faces, poor framing, insufficient size, excessive pose, darkness, blur, or faces partially outside the frame. Capture selected front, slight-left, and slight-right frames rather than uploading a stream.

`face_profiles` stores `model_name`, `model_version`, embedding, quality score, created/updated times, and consent/retention metadata. This allows controlled re-enrollment and future model migration.

### Attendance recognition

```text
Stable local face -> quality validation -> optional liveness challenge
-> one authenticated capture -> organization-scoped vector search
-> similarity threshold -> attendance rule evaluation -> cooldown -> result
```

The backend returns `NOT_RECOGNIZED` below threshold; it must not guess identities or reveal near matches. Attendance logic is backend-authoritative, including check-in/check-out, shift/grace calculations, duplicate prevention, device restrictions, and timestamps.

### Liveness

Define a `LivenessService` interface. The MVP may use randomized challenge flows (blink, turn left/right, smile), but this is not equivalent to high-assurance passive liveness. Keep the interface suitable for later integration of a dedicated anti-spoofing service.

## 6. Security architecture

- HTTPS only; short-lived JWT access tokens and rotated refresh tokens.
- Secure token storage on Android; token redaction in all logs.
- RBAC, tenant isolation, request/file validation, backend rate limiting, audit logging, and safe user-facing errors.
- Do not log credentials, tokens, raw embeddings, or excessive personal information.
- Enforce device registration server-side and issue revocable device credentials; hardware identifiers alone are not an authorization boundary.
- Support consent, re-enrollment, face deletion, retention, and organization offboarding without deleting attendance history.
- Audit employee changes, face lifecycle events, manual attendance changes, device changes, logins, and configuration changes.

## 7. API list

Initial endpoints:

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
GET  /api/v1/auth/me

GET  /api/v1/dashboard

GET    /api/v1/employees
GET    /api/v1/employees/{id}
POST   /api/v1/employees
PUT    /api/v1/employees/{id}
DELETE /api/v1/employees/{id}

POST /api/v1/employees/{employeeId}/face/enroll
POST /api/v1/attendance/recognize
```

Before each endpoint is implemented, define Pydantic request/response schemas, error cases, Android DTOs, domain models, and repository contracts. API success/error responses use predictable envelopes and codes such as `INVALID_CREDENTIALS`, `FORBIDDEN`, `FACE_QUALITY_LOW`, `LIVENESS_FAILED`, `ATTENDANCE_ALREADY_MARKED`, and `DEVICE_NOT_AUTHORIZED`.

## 8. MVP implementation sequence

1. Android foundation: dependency setup, application graph, theme, navigation, error mapping, and shared UI.
2. Authentication: login, session persistence, interceptor, refresh flow, logout.
3. Tenant foundation: organization model, roles, server-side tenant dependencies.
4. Dashboard and employee management.
5. Departments, branches, shifts, and server-side attendance rules.
6. Face enrollment pipeline and secured face-profile storage.
7. Attendance scanner, recognition API, duplicate cooldown, and basic liveness.
8. History, basic reports, audit logs, device registration, and settings.
9. Test coverage, performance work, release hardening, and operational documentation.

## 9. Major technical risks

| Risk | Mitigation |
| --- | --- |
| Cross-tenant data exposure | Resolve tenant context server-side; test every tenant-owned resource explicitly. |
| Spoofing with photos/screens | Treat challenge liveness as an interim control; design for a dedicated anti-spoofing provider. |
| False positives/negatives | Validate quality, tune thresholds with representative consented data, and never guess identity. |
| Camera performance/battery drain | Latest-frame analysis, throttling, lifecycle binding, one recognition request per stable face. |
| Poor connectivity | Clear online-only verification messaging and safe non-sensitive sync only. |
| Biometric privacy/regulatory exposure | Consent, retention/deletion workflows, least data collection, audit trail, and legal review for target jurisdictions. |
| Vector-search scale | Benchmark pgvector indexing and organization-scoped query plans before scaling tenants. |

## 10. Decisions to finalize before Phase 1

1. Confirm Android package/branding: current scaffold package is `com.facerem`; target naming should be decided before production APIs and releases.
2. Confirm backend repository placement: this repository can host `backend/`, or the backend can live in a dedicated service repository.
3. Confirm Azure deployment topology, region, secret management, managed PostgreSQL/Redis, and storage approach for uploaded enrollment images.
4. Define biometric-consent language, retention period, deletion/offboarding process, and jurisdiction-specific legal requirements.
5. Choose an initial face-engine packaging/deployment approach and target device/network performance budget.
6. Define organization-level attendance policies, device-registration flow, and escalation path for recognition failures.
7. Decide initial API authentication and admin-onboarding ownership.

## Phase 0 verification

- Android code changed: no.
- Backend code changed: no.
- Architecture documentation added: yes.
- Next authorized implementation step: **Start Phase 1**.
