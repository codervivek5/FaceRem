# FaceAttend

FaceAttend is a production-oriented, multi-tenant SaaS facial-attendance platform. The Android client is written in Kotlin and is designed for companies, colleges, schools, institutes, offices, factories, and multi-branch organizations.

The product name and branding must remain configurable; business logic must not depend on FaceAttend-specific strings.

The Phase 0 implementation blueprint is maintained in [docs/PHASE_0_ARCHITECTURE.md](docs/PHASE_0_ARCHITECTURE.md).

## Product principles

- Treat biometric information as sensitive data and design for privacy, consent, auditing, and deletion.
- Enforce organization isolation on the server. A client-provided `organizationId` is never sufficient authorization.
- Use ML Kit only for on-device face detection and quality signals; recognition is performed by the backend using InsightFace/ArcFace embeddings.
- Build the product in explicit phases. Do not begin a later phase until it is requested.
- Keep UI, domain, and data concerns separate. Compose screens must not call Retrofit directly or contain business logic.

## Platform architecture

```text
Android application
        |
        v
FastAPI backend
   |       |       \
   v       v        v
PostgreSQL Redis  Face engine
+ pgvector         (InsightFace / ArcFace)
```

### Android

The Android application uses Kotlin, Jetpack Compose, Material 3, Clean Architecture, MVVM, StateFlow, Coroutines, Hilt, CameraX, ML Kit Face Detection, Retrofit, OkHttp, Kotlin Serialization, Room, DataStore, WorkManager, Navigation Compose, and Coil where needed. It uses Gradle Kotlin DSL and version catalogs, with `minSdk = 23` and current mutually compatible stable Android dependencies.

Preferred boundaries:

```text
UI -> ViewModel -> Use case -> Repository interface -> Repository implementation -> Remote / local data source
```

Suggested Android structure:

```text
app/
core/
  common/
  network/
  database/
  datastore/
  designsystem/
  model/
feature/
  auth/
  dashboard/
  employees/
  faceenrollment/
  attendance/
  history/
  reports/
  settings/
build-logic/
```

An initially package-based monolith is acceptable when the module layout would add premature overhead, provided the above boundaries are preserved for later modularization.

### Backend

The cloud-ready backend targets Microsoft Azure and uses Python, FastAPI, SQLAlchemy, Alembic, PostgreSQL with pgvector, Redis, Pydantic, JWT access/refresh tokens, InsightFace, and ArcFace.

```text
backend/
  app/
    api/v1/
    core/                 # configuration, security, exceptions
    models/
    schemas/
    repositories/
    services/
      auth/
      attendance/
      face/
      liveness/
    db/
    main.py
  migrations/
  tests/
  Dockerfile
  docker-compose.yml
  requirements/
```

Backend responses use a predictable envelope with machine-readable error codes:

```json
{
  "success": true,
  "data": {},
  "message": "Operation successful"
}
```

```json
{
  "success": false,
  "error": {
    "code": "FACE_NOT_RECOGNIZED",
    "message": "Face could not be recognized."
  }
}
```

## Multi-tenancy and authorization

Every tenant-owned record has organization ownership, normally through `organization_id`. Tenant access is derived from the authenticated user or device session and validated on every server-side operation. Face searches must always be filtered to the authorized organization before vector similarity matching.

Initial roles:

```text
SUPER_ADMIN
ORGANIZATION_ADMIN
HR_ADMIN
MANAGER
EMPLOYEE
ATTENDANCE_DEVICE
```

Role-based access control must be extensible. Organization-facing navigation must never expose `SUPER_ADMIN` functionality.

## Face enrollment and recognition

### Enrollment

```text
Employee -> permission -> front camera -> ML Kit detection -> quality checks
         -> capture selected valid frames -> secure multipart upload
         -> backend detection/alignment/validation -> ArcFace embedding -> pgvector storage
```

The client uses CameraX `Preview` and `ImageAnalysis` with a latest-frame backpressure strategy. It does not upload every camera frame. Enrollment captures multiple high-quality views (front, slight left, slight right).

The quality evaluator returns statuses such as `VALID`, `NO_FACE`, `MULTIPLE_FACES`, `TOO_FAR`, `TOO_CLOSE`, `LOOK_STRAIGHT`, `TOO_DARK`, and `BLURRY`. It considers face size, position, pose, brightness, blur, frame boundaries, and face count.

### Recognition and attendance

```text
CameraX -> ML Kit detects a stable face -> quality validation -> optional liveness
        -> capture one image -> authenticated API request -> organization-scoped vector search
        -> confidence check -> attendance-rule evaluation -> result and cooldown
```

Recognition never searches face embeddings globally and never guesses an identity below the configured confidence threshold. Duplicate scans are prevented by a configurable backend cooldown. Unknown users receive a generic “Face Not Recognized” response without exposing near matches.

Liveness is a separate `LivenessService` abstraction. The initial MVP may use randomized challenge-based checks (blink, look left/right, smile), while preserving the ability to introduce a dedicated passive anti-spoofing service later. Basic eye-open detection alone is not enterprise-grade anti-spoofing.

## Privacy and security

- HTTPS-only API communication, secure token storage, JWT access tokens, refresh-token rotation, and logout/session invalidation.
- RBAC, tenant isolation, server-side request validation, rate limits, upload validation, audit logs, and redacted sensitive logs.
- Never log passwords, access or refresh tokens, raw biometric embeddings, or unnecessary personal data.
- Support face-enrollment consent, consent timestamps, re-enrollment, face-profile removal, retention policies, and organization offboarding.
- Removing a face profile must not delete attendance history.
- Device identity alone is not a trust boundary; use server-side device registration and revocable device credentials.

## Core data model

Planned tables:

```text
organizations
users
roles
employees
departments
branches
shifts
face_profiles
attendance_records
attendance_devices
holidays
leave_requests
audit_logs
refresh_tokens
subscriptions
```

Key fields and indexes include `organization_id`, `employee_id`, `date`, `employee_code`, and `status`. `face_profiles` stores normalized embeddings in pgvector-compatible storage—not arbitrary JSON—and records `model_name`, `model_version`, quality score, and lifecycle timestamps to support model migrations.

Attendance records support `CHECK_IN` and `CHECK_OUT` now, with room for `BREAK_START` and `BREAK_END`. The backend calculates shifts, grace periods, late time, overtime, status, and organization-specific attendance rules.

## Initial API surface

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
DELETE /api/v1/employees/{id}       # generally soft delete

POST /api/v1/employees/{employeeId}/face/enroll
POST /api/v1/attendance/recognize
```

Each feature is contract-first: define request and response schemas, error cases, Android DTOs, domain models, and repository interfaces before building UI.

Representative error codes include:

```text
INVALID_CREDENTIALS, TOKEN_EXPIRED, UNAUTHORIZED, FORBIDDEN,
EMPLOYEE_NOT_FOUND, FACE_NOT_FOUND, FACE_NOT_RECOGNIZED,
MULTIPLE_FACES, FACE_QUALITY_LOW, LIVENESS_FAILED,
ATTENDANCE_ALREADY_MARKED, DEVICE_NOT_AUTHORIZED,
ORGANIZATION_SUSPENDED, NETWORK_ERROR
```

## MVP scope and sequence

The first production MVP includes authentication, tenant architecture, dashboard, employee management, face enrollment, camera face detection, backend face recognition, check-in/check-out, attendance history, basic shift management, basic reporting, security, and audit logging.

Development phases:

1. Architecture and planning
2. Android foundation
3. Authentication
4. Organization and tenant foundation
5. Dashboard
6. Employee management
7. Face enrollment
8. Face AI backend
9. Attendance scanner
10. Liveness / anti-spoofing
11. Attendance rules
12. Shifts, departments, and branches
13. History
14. Reporting
15. Offline and poor-network support
16. Device registration
17. Security and privacy
18. Audit log
19. Settings
20. Testing and performance optimization
21. UX polish
22. Release preparation

Future work—such as payroll, geofencing, push/email/WhatsApp notifications, billing, iOS, web dashboard, advanced passive liveness, and visitor management—is deliberately outside the MVP.

## Offline strategy

Room caches the current user, basic organization details, appropriate employee summaries, recent attendance, and safe pending operations. WorkManager synchronizes non-sensitive queued actions. Facial verification remains server-backed in the initial version; if unavailable, the application clearly communicates that internet connectivity is required rather than silently recording authoritative attendance offline.

## Testing and release requirements

Test ViewModels, use cases, repositories, validators, and attendance calculations; API authentication, refresh, authorization, face enrollment, attendance, and duplicate prevention; and essential Android UI flows. Include explicit tenant-isolation tests proving that an administrator from Organization A receives `403` or `404` when accessing Organization B’s employees, attendance, reports, face profiles, devices, departments, branches, or similar resources.

Before release, provide environment-specific DEV/STAGING/PRODUCTION configuration, secure API endpoints, R8/ProGuard configuration, signing documentation, crash-reporting and logging abstractions, and Play Store readiness. Production credentials must never be embedded in the APK.

## Delivery rule

Implement only the phase explicitly requested. At the end of each phase, build the project, correct compilation and import/dependency issues, run relevant tests, document changed files and the current project tree, state what remains, and stop for approval before the next phase.
