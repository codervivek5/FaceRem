# Phase 2 — Application shell, authentication and product screens

## Goal

Build the complete Android client experience — splash, onboarding, authentication, and every
MVP product screen — on top of the Phase 1 foundation, so the product can be reviewed and
usability-tested before the FastAPI backend exists.

Nothing in this phase changes the architecture the README specifies. The UI talks only to
repository interfaces; those interfaces are shaped like the documented API surface, and today they
are satisfied by a local demo implementation.

## What was built

### Navigation

```text
Splash ─> Onboarding ─> Login ──> Main (bottom navigation)
             (once)      │          ├─ Dashboard
                         │          ├─ Employees
                         └> Forgot  ├─ History
                            password└─ Settings
                                    
Above the shell: Scan, Employee detail, Add employee, Face enrollment,
                 Reports, Profile, Notifications, Biometric data policy
```

Tab switching lives in a nested graph inside `MainShell`, so modal flows (scan, enrollment,
employee detail) sit above the shell on the root back stack and the tabs keep their own state.

### Screens

| Screen | Notes |
| --- | --- |
| Splash | Animated brand mark; resolves session + onboarding state, then routes. A `core-splashscreen` window covers the cold start. |
| Onboarding | Three-page pager, shown once, recorded in DataStore. |
| Login | Organization code, email, password, "keep me signed in", forgot password, **guest mode**, and a demo-credential prefill. |
| Forgot password | Always answers identically so accounts cannot be enumerated. |
| Dashboard | Today's counts, attendance rate, six-day chart, quick actions, recent activity. |
| Employees | Search, enrollment filters, per-employee enrollment status. |
| Employee detail | Profile fields, enrollment state, recent attendance. |
| Add employee | Validated form; on save it goes straight to face enrollment. |
| Face enrollment | Consent gate → camera permission → three-pose capture with live quality coaching → submit. |
| Attendance scan | Hands-free kiosk loop for a gate tablet: CameraX preview, ML Kit detection, quality coaching, server verification, and self-clearing check-in / already-present / not-recognized results. |
| History | Range and status filters, search, grouped by date. |
| Reports | Period summary, worked/overtime hours, department breakdown, export request. |
| Settings | Account, workspace, theme mode, privacy, sign out. |
| Profile / Notifications / Biometric data policy | Supporting detail screens. |

### Design system

`core/designsystem` and `ui/theme` now carry a full Material 3 implementation: complete light and
dark colour schemes (every surface role defined, so nothing falls back to the purple M3 baseline),
semantic colours for success/warning/info and the brand gradient, a shape scale, a spacing scale, a
type scale, and reusable components — buttons with loading state, validated text fields, stat cards,
quick-action cards, status pills, rate bars, a six-day column chart, a camera framing overlay, and
the guest-mode banner.

The launcher icon, adaptive icon and cold-start splash use a drawn face-scan reticle rather than the
project template's placeholder.

### Camera and face quality

`core/camera` holds the on-device half of the pipeline, exactly as the README scopes it:

- `FaceQualityEvaluator` grades a frame on face count, size, centring, head pose, brightness and
  eye state, returning a coaching verdict (`TOO_FAR`, `LOOK_STRAIGHT`, `TOO_DARK`, …).
- `FaceAnalyzer` runs ML Kit on the latest frame only and emits that verdict. It keeps no frames.
- `FaceCameraPreview` binds a front-camera preview plus a `STRATEGY_KEEP_ONLY_LATEST` analysis use
  case, and unbinds on dispose.

Identity is never decided on the device. A pose is accepted only after the verdict stays valid
across consecutive frames, which filters out lucky single frames during motion.

### Kiosk mode

The scanner defaults to kiosk mode, because the common deployment is a tablet mounted beside a
door where nobody is there to press anything:

- The camera stays bound for the whole session. Rebinding CameraX costs about a second, which at
  a gate means the next person has already walked past.
- A held face triggers recognition on its own, and the result clears itself — briefly for a
  repeat visit, longer for something that needs attention.
- After a result the scanner waits for an **empty frame** before arming again. Without that, the
  person who was just greeted is still standing there and would be scanned on a loop.
- The screen is kept awake and the back gesture is swallowed; leaving is a deliberate long press
  on the kiosk chip, which reveals the handheld controls again.

The loop is a pure transition in `ScanStateMachine.kt`, so those rules are unit-tested without a
camera: see `ScanStateMachineTest`.

## Guest mode

Guest mode exists so the app can be reviewed and tested without a backend or an account.

- Entered from the Login screen; no credentials are exchanged and no server is contacted.
- The session is flagged `isGuest` in `SessionManager`, and every screen that can show data renders
  a persistent amber banner saying the data is local sample data.
- Data comes from `DemoDataStore`: one sample organization, twelve employees, fourteen days of
  attendance, departments, branches and shifts. Changes made during the session (adding an employee,
  enrolling a face, scanning attendance) mutate that in-memory store so the flows are genuinely
  exercisable, and they disappear when the store is recreated.
- The scanner's "recognition" is simulated locally, including a deliberate unknown-face outcome
  roughly one attempt in six, so the not-recognized state can be reviewed. Demo mode follows the
  same check-in / already-present / check-out rules as the server, with the check-out window
  shortened from four hours to two minutes so it can actually be watched.
- Report export is refused in guest mode with an explicit message rather than pretending to work.
- Leaving guest mode clears the session and returns to Login.

`Continue as guest` and `Fill demo admin credentials` are both on the Login screen. The second fills
the form so the ordinary signed-in path can be exercised too.

## Swapping in the real backend

Each feature owns a repository interface whose methods are annotated with the endpoint they map to:

```text
AuthRepository        POST /api/v1/auth/login, /forgot-password, /logout
DashboardRepository   GET  /api/v1/dashboard
EmployeeRepository    GET/POST/PUT /api/v1/employees…, POST …/face/enroll
AttendanceRepository  POST /api/v1/attendance/recognize
ReportRepository      reporting endpoints
```

The demo implementations live beside them in each feature's `data` package and are wired in
`core/data/RepositoryModule`. Replacing one with a Retrofit-backed implementation is a one-line
change to that `@Binds`; no ViewModel or Compose code changes.

Outstanding for the backend phases: real token storage in a hardware-backed keystore, the OkHttp
auth interceptor and refresh coordinator, Room caching of the current user and recent attendance,
WorkManager sync, and the liveness service.

## Verification

- `./gradlew :app:assembleDebug` — builds clean, no warnings.
- `./gradlew :app:testDebugUnitTest` — 30 tests pass (validators, face-quality pose rules, login
  ViewModel including the guest path, history filtering, API error mapping).
- Manually exercised on an API 36 emulator: splash → onboarding → login → guest mode → dashboard →
  people → employee detail → add employee → consent → live camera capture → history → reports →
  settings → profile.

Note: the emulator's front camera is configured as `emulated`, so the preview shows the synthetic
test pattern rather than a face; ML Kit correctly reports `NO_FACE` against it.

## Files added or changed

```text
app/src/main/java/com/facerem/
  MainActivity.kt                     # system splash + persisted theme mode
  MainViewModel.kt
  app/navigation/{AppRoute,AppNavHost,MainShell}.kt
  core/
    camera/{FaceQuality,FaceAnalyzer,CameraPreview,CameraPermission,FaceDetectorProvider,FaceDetectorModule}.kt
    common/{AppClock,Validators,AppResult}.kt
    data/demo/DemoDataStore.kt
    data/RepositoryModule.kt
    datastore/AppPreferences.kt        # theme mode, onboarding, session
    designsystem/component/{AppComponents,Branding,Cards,DataDisplay,StatusVisuals,CameraOverlay}.kt
    model/{Role,User,Employee,Attendance,Dashboard}.kt
    session/SessionManager.kt
  feature/
    auth/{domain,data,presentation}/           # splash, onboarding, login, forgot password
    dashboard/{domain,data,presentation}/
    employees/{domain,data,presentation}/      # list, detail, add
    faceenrollment/presentation/
    attendance/{domain,data,presentation}/     # scanner
    history/presentation/
    reports/{domain,data,presentation}/
    settings/presentation/                     # settings, profile, notifications, privacy
  ui/theme/{Color,Theme,Type,Shape,Spacing}.kt

app/src/main/res/
  drawable/{ic_launcher_background,ic_launcher_foreground}.xml
  mipmap-*/ic_launcher{,_round}.webp
  values/{colors,themes,strings}.xml
  AndroidManifest.xml

app/src/test/java/com/facerem/
  core/common/ValidatorsTest.kt
  core/camera/FaceQualityEvaluatorTest.kt
  feature/auth/LoginViewModelTest.kt
  feature/history/HistoryUiStateTest.kt

gradle/libs.versions.toml               # hilt-navigation-compose, core-splashscreen,
app/build.gradle.kts                    # lifecycle-runtime-compose, coroutines-test
```
