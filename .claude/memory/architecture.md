# Architecture

- **System role:** Real-time interview simulator where a browser streams microphone audio to a Spring Boot orchestrator,
  which brokers the session to Gemini Live, then returns AI audio/transcript chunks back to the browser and finally
  persists a graded report.
- **Primary runtime split:** server-rendered web app (Thymeleaf + JS) + stateful WebSocket conversation plane + REST
  utility endpoints for setup concerns (mode, key validation, CV upload, voice previews).

## Data flow

- Setup flow is HTTP/session based:
    - `/setup/step1..3` writes `InterviewSetupDTO` into HTTP session (`setupForm`), validates/sanitizes each step, then
      redirects to `/interview`.
    - `/interview` injects `setupForm` into `window.interviewSession`, then clears session setup state.
- Interview flow is STOMP-over-SockJS:
    - Browser connects to `/ws/interview`, subscribes to `/user/queue/*`, sends `/app/interview/start`.
    - Server creates DB session (`InterviewService.startSession`) and builds interviewer system prompt (
      `InterviewPromptService`).
    - `GeminiLiveClient` opens an OkHttp WebSocket to Gemini, streams 16kHz PCM input, receives 24kHz PCM output +
      transcripts.
    - `GeminiIntegrationService` forwards AI audio/status/transcripts to the browser, accumulates full transcript,
      handles turn completion and interview-end detection.
- Conclusion and grading:
    - End detected either by explicit client `/app/interview/end` or AI conclusion signal/pattern (`[END_INTERVIEW]` +
      regex patterns).
    - Server finalizes session, stores transcript, then asynchronously grades via `GradingService`.
    - Grading result is pushed on `/user/queue/report`; UI redirects to `/report/{sessionId}`.

## Core services

- `GeminiIntegrationService`: session orchestration, WS routing, transcript assembly, reconnection/resumption buffering.
- `GeminiLiveClient`: low-level Gemini WebSocket protocol handler (setup payload, audio send/receive, transcription
  events, GoAway/resume hooks).
- `InterviewPromptService`: language/difficulty/position/CV-aware interviewer prompt generator + interview-conclusion
  detection.
- `GradingService`: transcript evaluation through Gemini text model(s), JSON parsing, feedback persistence.
- `GeminiModelRotationService`: key/model rotation + cooldown tracking (minute and daily style exhaustion handling).
- `InterviewService`: persistence lifecycle for sessions/transcripts/timestamps.
- `CvProcessingService`: PDF/DOCX extraction with MIME and magic-byte validation.
- `InputSanitizerService`: prompt/HTML/name/position/CV sanitization and enum validation utility.
- `AdminServiceImpl`: dashboard aggregations and admin password rotation.
- `RateLimitService`: in-memory per-IP throttling for API-key validation endpoint.

## State management

- **Database state:** `interview_sessions`, `interview_feedback`, `admin_users`.
- **Ephemeral server state:** active interview map keyed by WebSocket session ID (contains Gemini client, transcript
  buffers, reconnection state, selected voice/key).
- **HTTP session state:** setup wizard DTO only, cleared on interview start/report end.
- **Browser state:** localStorage API key cache (PROD mode), live transcript/audio queues in JS runtime.
- **Data retention policy in code:** scheduled cleanup deletes sessions+feedback older than 2 weeks every 6 hours.
