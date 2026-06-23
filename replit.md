# MLBB Draft Assistant

An AI-powered draft assistant for Mobile Legends: Bang Bang (MLBB). The app displays an always-on overlay during ranked draft phases, uses computer vision to detect hero picks and bans in real time from the screen, and provides ranked hero recommendations with synergy, counter-pick, and meta scoring — all without requiring any manual input from the player.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Android App Stack (repo.dev/)

- Kotlin 2.1.0, AGP 8.10.1, minSdk 29, compileSdk 36
- Jetpack Compose + Material3, Compose BOM 2025.05.01
- Hilt 2.55 dependency injection
- Room (local DB) + DataStore Preferences
- Retrofit + OkHttp 4.12.0 (stable)
- Kotlinx Coroutines + Flow (MVI/UDF pattern)
- Timber logging + custom CrashLogStore
- MediaProjection + AccessibilityService for CV pipeline

## Where things live

- `repo.dev/` — full Android Kotlin source (MLBB Draft Assistant app)
- `repo.dev/app/src/main/java/com/mlbb/assistant/` — all app source
  - `domain/` — pure Kotlin business logic (use cases, engine, scoring, advisor)
  - `data/` — Room DAOs, entities, Retrofit API, DataStore, repositories
  - `presentation/` — Jetpack Compose screens and ViewModels
  - `service/` — AccessibilityService, VoiceAlertService
  - `utils/` — DateFormatter, NetworkMonitor, AppConstants, CrashLogStore
  - `di/` — Hilt modules (AppModule, NetworkModule, RepositoryModule, OverlayModule)
- `repo.dev/docs/` — all project documentation (AUDIT, ROADMAP, FEATURES, etc.)
- `repo.dev/docs/temp/` — working refactor docs (REFACTOR_PLAN, REFACTOR_SUMMARY, etc.)

## Architecture decisions

- **Clean Architecture + MVI**: Domain layer is pure Kotlin with no Android imports. ViewModels hold `StateFlow<UiState>` and expose one-directional data flow to Compose.
- **Single DataStore instance**: `AppModule.provideDataStore()` is the exclusive Hilt binding; no companion factory methods exist that could bypass migrations.
- **Contract-first API**: OpenAPI spec → Orval codegen for all API hooks.
- **No SimpleDateFormat**: All timestamp formatting uses `java.time.DateTimeFormatter` (thread-safe, API 26+, minSdk=29 ✅).
- **Domain-mediated history access**: `DraftHistoryViewModel` injects `GetDraftHistoryUseCase` (not `DraftSessionDao` directly) to preserve layer separation.

## Product

- Real-time overlay bubble during MLBB ranked draft: always-on, draggable, minimisable
- Computer vision pipeline detecting picks/bans via MediaProjection + perceptual hashing
- Hero scoring engine: meta adherence (40%) + counter-pick (30%) + synergy (30%) with configurable weights
- Composition analyzer: archetype detection, counter-pick warnings, lane coverage gaps
- Draft history persistence: Room DB with score breakdown per session
- Voice alerts (TextToSpeech) for turn notifications
- Full onboarding wizard for overlay, accessibility, battery, and OEM-specific permissions
- Crash log tab for debug diagnostics

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- **Never use `console.log` in server code** — use `req.log` in route handlers.
- **Do not call `pnpm run dev` at the workspace root** — each artifact has its own workflow with `PORT`/`BASE_PATH` env vars.
- **Android: always run codegen** (`pnpm --filter @workspace/api-spec run codegen`) after changing the OpenAPI spec.
- **Android: `toEntity()` must mirror `toDomain()`** — if you add a field to `DraftHistoryItem`, add it to both mapping extensions in `DraftSessionRepositoryImpl`.
- **Android: `SaveDraftSessionUseCase` is the only write path** — do not write to `DraftSessionDao` directly from ViewModels.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
- See `repo.dev/docs/AUDIT.md` for full audit history and issue resolution matrix
- See `repo.dev/docs/ROADMAP.md` for phased feature plan
- See `repo.dev/docs/TODO.md` for master checklist of all actionable items
- See `repo.dev/docs/temp/REFACTOR_PLAN.md` for refactoring checklist with completion status
