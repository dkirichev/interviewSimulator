# AI Interview Simulator - Architecture

## System Overview

Real-time AI interview practice using **Gemini Live API** with bidirectional audio streaming.

```
┌─────────────┐      STOMP/WebSocket      ┌──────────────┐     OkHttp WebSocket     ┌─────────────────┐
│   Browser   │ ◄───────────────────────► │ Spring Boot  │ ◄─────────────────────► │ Gemini Live API │
│  (16kHz)    │                           │ (Orchestrator)│                         │    (24kHz)      │
└─────────────┘                           └──────────────┘                         └─────────────────┘
```

### Why 3-Tier?

| Benefit | Description |
|---------|-------------|
| Security | API keys stay server-side |
| Persistence | Transcripts stored in PostgreSQL |
| Business Logic | Grading, prompts, session management |
| Multi-user | Concurrent sessions with resource limits |

---

## Technology Stack

**Backend:** Spring Boot 4.0.0, Java 21, PostgreSQL, Flyway, OkHttp, Lombok  
**Frontend:** Vanilla JS, Web Audio API, STOMP/SockJS, Tailwind CSS  
**AI:** Gemini Live API (audio), Gemini API (grading)

---

## Data Flow

1. **User speaks** → Browser captures 16kHz PCM via Web Audio API
2. **Browser → Server** → STOMP message to `/app/interview/audio`
3. **Server → Gemini** → OkHttp WebSocket sends base64 PCM
4. **Gemini → Server** → Returns 24kHz audio + transcriptions
5. **Server → Browser** → STOMP to `/user/queue/audio`
6. **Browser plays** → Web Audio API at 24kHz

---

## Key Components

| Component | Purpose |
|-----------|---------|
| `GeminiLiveClient` | OkHttp WebSocket client to Gemini |
| `GeminiIntegrationService` | Orchestrates session state + message routing |
| `InterviewWebSocketController` | STOMP endpoint handlers |
| `GradingService` | Post-interview AI evaluation |
| `InterviewPromptService` | Generates interviewer personality/prompts |
| `audio-processor.js` | Mic capture + audio playback |

---

## Database Schema

```sql
interview_sessions (id, candidate_name, job_position, difficulty, transcript, score, ...)
interview_feedback (id, session_id, overall_score, communication_score, technical_score, ...)
```

---

## Configuration

```properties
# Required environment variables
GEMINI_API_KEY=your-key
DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
```

---

## Deployment

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
  app:
    build: .
    ports: ["8080:8080"]
    depends_on: [postgres]
```

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Future Roadmap

- [ ] User authentication (Spring Security)
- [ ] Question bank system
- [ ] Admin dashboard
- [ ] Resume parsing
- [ ] Video recording (optional)

---

**See `CLAUDE.md` for coding standards and development guidelines.**
