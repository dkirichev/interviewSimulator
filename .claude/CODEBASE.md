# CODEBASE

## Directory map (project root)

- `Dockerfile` - Multi-stage container build for Spring Boot JAR (builder + non-root runtime).
- `docker-compose.yml` - Local/prod orchestration for `app` + `postgres` with env-driven mode and DB config.
- `pom.xml` - Primary build file (Java 21, Spring Boot 4, JPA, WebSocket, Security, Flyway, PostgreSQL, Gemini
  integrations, PDF/DOCX parsing).
- `package.json` - Frontend toolchain entry for Tailwind CSS compile/watch only.
- `tailwind.config.js` - Tailwind theme/config.
- `README.md` - Product overview, modes, deployment, privacy claims.
- `docs/` - Human docs (`API.md`, `ARCHITECTURE.md`, `SETUP.md`, `DOCKER.md`, `CONTRIBUTING.md`).
- `src/tailwind-input.css` - Tailwind source stylesheet.
- `src/main/java/net/k2ai/interviewSimulator/InterviewSimulatorApplication.java` - Spring Boot entrypoint + scheduling
  enablement.
- `src/main/java/net/k2ai/interviewSimulator/config/` - App config (`GeminiConfig`, `SecurityConfig`, `WebSocketConfig`,
  `I18nConfig`, WS lifecycle listener).
- `src/main/java/net/k2ai/interviewSimulator/controller/` - MVC/REST/WebSocket controllers.
- `src/main/java/net/k2ai/interviewSimulator/page/PageController.java` - Root and interview page routing.
- `src/main/java/net/k2ai/interviewSimulator/dto/InterviewSetupDTO.java` - Session-scoped setup payload across setup
  wizard steps.
- `src/main/java/net/k2ai/interviewSimulator/entity/` - JPA entities (`InterviewSession`, `InterviewFeedback`,
  `AdminUser`).
- `src/main/java/net/k2ai/interviewSimulator/repository/` - Spring Data repositories.
- `src/main/java/net/k2ai/interviewSimulator/service/` - Business layer (Gemini orchestration, grading, prompts, CV
  parsing, sanitizer, admin, rate limit).
- `src/main/java/net/k2ai/interviewSimulator/validation/` - Custom Bean Validation annotations + validators.
- `src/main/java/net/k2ai/interviewSimulator/exception/` - Domain exceptions + global exception handler.
- `src/main/java/net/k2ai/interviewSimulator/interceptor/MobileDeviceInterceptor.java` - Mobile UA block/redirect.
- `src/main/java/net/k2ai/interviewSimulator/scheduler/SessionCleanupScheduler.java` - 6-hour cleanup of 2-week-old
  sessions.
- `src/main/resources/application.properties` - Mode, datasource, Gemini model/key config, i18n, multipart limits,
  logging.
- `src/main/resources/db/migration/` - Flyway migrations: schema (`V1`), language column (`V2`), admin users (`V3`).
- `src/main/resources/messages*.properties` - i18n catalogs (`en`, `bg`).
- `src/main/resources/templates/layouts/` - Main Thymeleaf shell + script/style/footer/modals fragments.
- `src/main/resources/templates/pages/setup/` - 3-step setup UI.
- `src/main/resources/templates/pages/interview-standalone.html` - Interview runtime view; injects
  `window.interviewSession`.
- `src/main/resources/templates/pages/report-standalone.html` - Report view.
- `src/main/resources/templates/pages/admin/` - Admin login/dashboard.
- `src/main/resources/templates/pages/legal/` - Terms/privacy.
- `src/main/resources/static/js/` - Frontend runtime (`audio-processor.js`, `interview.js`, `apikey.js`,
  `language-switcher.js`, `microphone-check.js`, `theme-switcher.js`).
- `src/main/resources/static/audio/voices/` - Voice preview WAV assets (EN/BG).
- `src/main/resources/static/css/tailwind.min.css` - Compiled CSS artifact.
- `src/test/java/net/k2ai/interviewSimulator/` - Integration/unit tests (controllers/services/repositories/validation +
  Testcontainers utilities).
- `src/test/resources/application.properties` - Test runtime config.

## High-level data model

### Persisted (PostgreSQL via Flyway + JPA)

- `interview_sessions`
    - PK `id` (UUID).
    - Core interview metadata: `candidate_name`, `job_position`, `difficulty`, `language`.
    - Runtime/result fields: `started_at`, `ended_at`, `transcript`, `score`, `feedback_json`.
    - Indexed by `candidate_name`, `started_at`.
- `interview_feedback`
    - PK `id` (UUID), FK `session_id -> interview_sessions.id`.
    - Scoring fields: `overall_score`, `communication_score`, `technical_score`, `confidence_score`.
    - Qualitative fields: `strengths`, `improvements`, `detailed_analysis`, `verdict`, `created_at`.
    - Indexed by `session_id`.
- `admin_users`
    - PK `id` (UUID default), unique `username`, `password_hash`, timestamps.
    - Seeded default `admin` in `V3__create_admin_and_cleanup.sql`.

### In-memory/session/browser state

- `SetupController` + `PageController` use session attribute `setupForm` (`InterviewSetupDTO`) across setup steps then
  clear before interview runtime.
- `GeminiIntegrationService.activeSessions` tracks WS session -> interview state/transcripts/reconnection buffer.
- `RateLimitService.rateLimitMap` tracks `/api/validate-key` attempts per IP (10/minute).
- Browser localStorage in PROD mode stores user key (`gemini_api_key`) and validation timestamp.

### Lifecycle retention

- Transcript + feedback are persisted during interview/report flow.
- `SessionCleanupScheduler` deletes sessions and linked feedback older than 2 weeks every 6 hours.

## Key API routes and entry points

### Runtime entry points

- JVM entry: `net.k2ai.interviewSimulator.InterviewSimulatorApplication`.
- Root page entry: `GET /` -> redirect `/setup/step1`.
- Interview boot path: `GET /interview` (requires completed `setupForm`, injects `window.interviewSession`).
- Frontend interview boot function: `startInterviewFromSession()` in `static/js/audio-processor.js`.

### MVC routes

- Setup wizard (`SetupController`):
    - `GET /setup/step1`, `POST /setup/step1`
    - `GET /setup/step2`, `POST /setup/step2`
    - `GET /setup/step3`, `POST /setup/step3`
    - `POST /setup/clear`
- Reports (`ReportController`): `GET /report/{sessionId}`.
- Admin (`AdminController`): `GET /admin/login`, `GET /admin/dashboard`, `POST /admin/change-password`.
- Legal (`LegalController`): `GET /legal/privacy`, `GET /legal/terms`.
- Error page (`ErrorController`): `GET /error/mobile-not-supported`.

### REST routes

- Mode + key validation (`ApiKeyController`):
    - `GET /api/mode`
    - `POST /api/validate-key`
- CV (`CvController`): `POST /api/cv/upload` (multipart).
- Voices (`VoiceController`):
    - `GET /api/voices`
    - `GET /api/voices/preview/{voiceId}/{language}`

### WebSocket/STOMP

- SockJS endpoint: `/ws/interview`.
- Client -> server (`@MessageMapping` under `/app`):
    - `/app/interview/start`
    - `/app/interview/audio`
    - `/app/interview/mic-off`
    - `/app/interview/end`
- Server -> user queue:
    - `/user/queue/status`
    - `/user/queue/audio`
    - `/user/queue/transcript`
    - `/user/queue/report`
    - `/user/queue/error`
    - `/user/queue/text`

### Security/mode gates

- `SecurityConfig`: `/admin/**` requires `ROLE_ADMIN`; `/admin/login` public; all non-admin public.
- CSRF ignored for `/ws/**` and `/api/**`; admin form login/logout enabled.
- App mode contract from config: `DEV`, `PROD`, `REVIEWER`.
- Strict runtime enums observed in code paths:
    - Difficulty: `Easy|Standard|Hard` (prompt layer also handles `chill|stress` aliases).
    - Language: `en|bg`.
    - Voice IDs: `Algieba|Kore|Fenrir|Despina`.
