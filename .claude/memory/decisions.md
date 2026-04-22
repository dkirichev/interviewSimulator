# Technology decisions

- We use Java 25 because the application benefits from current LTS stability while staying aligned with modern Spring
  Boot baselines.
- We use Spring Boot 4 because it provides integrated web, security, JPA, validation, scheduling, and WebSocket
  infrastructure with minimal custom framework glue.
- We use Spring MVC + Thymeleaf because the product is server-rendered with session-driven setup pages and mode-aware
  conditional UI fragments.
- We use Spring WebSocket with STOMP and SockJS because interview audio/status/report events require bidirectional
  messaging with browser compatibility fallback.
- We use PostgreSQL because interview sessions, feedback, and admin credentials are relational and query-heavy for admin
  dashboard filtering and aggregation.
- We use Spring Data JPA because entity/repository abstractions speed up persistence logic while retaining database
  portability for standard CRUD and finder patterns.
- We use Flyway because deterministic schema versioning is required for reproducible deployments and seeded admin
  bootstrap data.
- We use OkHttp because the backend needs a robust WebSocket client to communicate with Gemini Live and tuned HTTP
  clients for grading/key validation.
- We use Gemini Live (native audio model) because the core feature is low-latency full-duplex voice interviewing instead
  of text-only chat.
- We use Gemini text grading models with fallback rotation because report generation must remain available across quota
  exhaustion or model-access failures.
- We use Apache PDFBox and Apache POI because CV personalization requires extracting text from both PDF and DOCX
  uploads.
- We use Tailwind CSS (CLI build) because utility-first styling keeps template markup expressive while producing a
  single compiled CSS artifact for runtime.
- We use vanilla JavaScript because the frontend logic is mostly browser API orchestration (audio capture/playback,
  WebSocket state, modals) without SPA framework overhead.
- We use Docker and Docker Compose because the app and PostgreSQL need a reproducible, one-command deployment topology
  across local and hosted environments.
- We use Testcontainers for integration tests because repository/service tests require realistic PostgreSQL behavior
  rather than in-memory database approximations.
- We use Lombok because constructor/data/logger boilerplate is repetitive across controllers, services, and entities.
