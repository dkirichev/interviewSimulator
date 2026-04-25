# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build & run
./mvnw clean compile
./mvnw spring-boot:run          # port 8080

# Tests
./mvnw test
./mvnw test -Dtest=MyTest       # single test class

# Database
./mvnw flyway:migrate

# CSS (only needed when editing Tailwind — Maven does this automatically on full build)
npm run watch:css               # dev watch mode
npm run build:css               # one-shot compile

# Docker (full stack)
docker-compose up -d
```

Maven automatically invokes npm (`npm ci` + `npm run build:css`) during `compile`. Run `npm run watch:css` only when iterating on Tailwind locally without triggering a full Maven build.

## Architecture

Real-time voice interview app. User completes a 3-step setup wizard, then conducts a live spoken interview against Google Gemini's Live Audio API via WebSocket. After the interview ends, Gemini grades the transcript and produces a scored report.

### Setup → Interview data flow

```
SetupController (HTTP session: @SessionAttributes("setupForm"))
  └─► PageController GET /interview
        └─► injects window.interviewSession into HTML
              └─► audio-processor.js startInterviewFromSession()
                    └─► STOMP /app/interview/start
                          └─► InterviewWebSocketController
                                └─► GeminiIntegrationService
                                      ├─► InterviewService  ──► PostgreSQL
                                      ├─► InterviewPromptService
                                      └─► GeminiLiveClient  ──► wss://generativelanguage.googleapis.com
```

### GeminiLiveClient reconnect

Gemini Live connections time out after 15 minutes. `GeminiLiveClient` fires a proactive reconnect at 14 minutes using a scheduled timer. Reconnect state is buffered in `GeminiIntegrationService.activeSessions` (`ConcurrentHashMap<String, InterviewState>`).

### Grading / model rotation

After interview ends, `GradingService` calls Gemini's REST `generateContent`. `GeminiModelRotationService` tracks exhausted (key, model) combos with time-based expiry: 65 s for rate limits, 1 h for 403/inaccessible, midnight-PT for daily quota. DEV/PROD/REVIEWER modes affect which keys and models are available.

### Transcript storage

`V4__remove_transcript_storage.sql` dropped `transcript` and `feedback_json` from `interview_sessions` (privacy decision). Transcripts are **in-memory only** during a session (`GeminiIntegrationService.activeSessions`). Only structured feedback scores/text are persisted to `interview_feedback`.

### Frontend JS loading

Scripts are conditionally injected via Thymeleaf `th:if` in `layouts/bodyBottom.html` using model attributes (`appMode`, `isSetupPage`, `isInterviewPage`) set by controllers. SockJS/STOMP CDN libraries load only on the interview page.

## Code Formatting (non-negotiable)

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class MyService {

	private final MyRepository repo;


	public void doWork() {
		log.info("Doing work for {}", id);
	}//doWork


	public void other() { }//other

}//MyService
```

| Rule | Detail |
|------|--------|
| Indentation | Tabs, not spaces |
| Braces | Same-line (`if (x) {`) |
| Between methods | Two blank lines |
| Closing braces | `}//methodName` and `}//ClassName` |
| File ending | Empty line at EOF (all file types) |

## Lombok

Use `@Slf4j` on all services/controllers (gives `log`). Use `@RequiredArgsConstructor` for constructor injection of `final` fields. Use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` on entities.

## Flyway

**Never use `${var}` syntax in `.sql` migration files** — Flyway treats it as a placeholder and will fail. Use literals or PL/pgSQL variables instead.

## Testing

All integration tests extend `AbstractIntegrationTest` which starts a shared singleton Testcontainers PostgreSQL instance via `@DynamicPropertySource`. Test config is in `src/test/resources/application.properties` with `app.mode=DEV` and a dummy API key.

## Internationalization

New UI strings require entries in all three message files: `messages.properties`, `messages_en.properties`, `messages_bg.properties`. Key format: `section.subsection.element[.attribute]`.

## App Modes

| Mode | API key source |
|------|---------------|
| `DEV` | `GEMINI_API_KEY` env var (backend) |
| `PROD` | User-provided key via browser modal, stored in localStorage |
| `REVIEWER` | Key rotation via `GEMINI_REVIEWER_KEYS` env var |

`GeminiConfig` (`@ConfigurationProperties(prefix = "gemini")`) is the single source of truth for mode detection and key/model lists.

## Custom Validation

Input validation annotations live in `validation/` (`@LettersOnly`, `@SafeText`, `@ValidDifficulty`, `@ValidLanguage`, `@ValidVoice`, `@ValidInterviewLength`). Use these on DTO fields; do not duplicate validation logic in services.

## Logging

Log external API calls, WebSocket events, errors, and business-level state changes. Include context (session ID, user name) in messages. Do not log simple CRUD or every method entry/exit.

## Security envs

Two security-related env vars worth knowing:

| Var | Default | Use |
|-----|---------|-----|
| `SESSION_COOKIE_SECURE` | `true` | Set to `false` for local plain-HTTP dev; otherwise the session cookie is dropped by the browser. |
| `APP_TRUST_FORWARDED_HEADERS` | `false` | Enable ONLY behind a trusted proxy (Cloudflare Tunnel / nginx / load balancer). Otherwise attackers can spoof source IPs and bypass per-IP rate limits. |

Per-IP rate limits exist on WS handshake, `/app/interview/start`, CV upload, admin login, and admin password change. Source IP comes from `ClientIpResolver`. When `APP_TRUST_FORWARDED_HEADERS=true` the resolver honors headers in this order: `CF-Connecting-IP` → `X-Forwarded-For` (leftmost) → socket peer. `CF-Connecting-IP` is preferred because Cloudflare always overwrites it at the edge, so the caller cannot spoof it; `X-Forwarded-For` can be prepended by the client and Cloudflare only appends, so it is fallback only.

Actuator exposes **only** `/actuator/health` (Docker `HEALTHCHECK`). Don't add new endpoints to `management.endpoints.web.exposure.include` without a security review.
