# AI Interview Simulator - Architecture

## System Overview

Real-time AI interview practice using **Gemini Live API** with bidirectional audio streaming, supporting multiple languages, voice selection, and CV-based personalized interviews.

```
┌─────────────┐      STOMP/WebSocket      ┌──────────────┐     OkHttp WebSocket     ┌─────────────────┐
│   Browser   │ ◄───────────────────────► │ Spring Boot  │ ◄─────────────────────► │ Gemini Live API │
│  (16kHz)    │                           │ (Orchestrator)│                         │    (24kHz)      │
└─────────────┘                           └──────────────┘                         └─────────────────┘
        │                                        │
        │ REST API                               │
        ├───────────────────────────────────────►│
        │ (CV upload, API key validation,        │
        │  voice preview, mode check)            │
```

### Why 3-Tier?

| Benefit | Description |
|---------|-------------|
| Security | API keys stay server-side (DEV) or validated (PROD) |
| Persistence | Transcripts and feedback stored in PostgreSQL |
| Business Logic | Grading, prompts, session management, CV processing |
| Multi-user | Concurrent sessions with session resumption |
| i18n | Server-side internationalization (English/Bulgarian) |

---

## Technology Stack

**Backend:** Spring Boot 4.0.0, Java 21, PostgreSQL, Flyway, OkHttp, Lombok, Apache PDFBox, Apache POI  
**Frontend:** Vanilla JS, Web Audio API, STOMP/SockJS, Tailwind CSS, FontAwesome  
**AI:** Gemini 2.5 Flash (live audio), Gemini 3 Flash (grading)  
**i18n:** Spring MessageSource with cookie-based locale resolution

---

## Application Modes

The application supports two operational modes configured via `APP_MODE` environment variable:

| Mode | Description | API Key Source |
|------|-------------|----------------|
| **DEV** | Development mode | Backend uses `GEMINI_API_KEY` env var |
| **PROD** | Production mode | Users provide their own API key (stored in localStorage) |

In PROD mode:
- Users are prompted with a modal to enter their Gemini API key
- Keys are validated against Google's API before use
- Rate-limited keys trigger helpful error messages
- Keys are cached in browser localStorage

---

## Data Flow

1. **User speaks** → Browser captures 16kHz PCM via Web Audio API
2. **Browser → Server** → STOMP message to `/app/interview/audio`
3. **Server → Gemini** → OkHttp WebSocket sends base64 PCM
4. **Gemini → Server** → Returns 24kHz audio + transcriptions
5. **Server → Browser** → STOMP to `/user/queue/audio`
6. **Browser plays** → Web Audio API at 24kHz (gapless crossfade scheduling)

### CV Processing Flow

1. **User uploads** → PDF/DOCX file (max 2MB)
2. **Browser → Server** → `POST /api/cv/upload` with multipart form
3. **Server extracts** → Text via PDFBox (PDF) or Apache POI (DOCX)
4. **Text included** → In system instruction for personalized interview

---

## Key Components

### Backend Services

| Component | Purpose |
|-----------|---------|
| `GeminiLiveClient` | OkHttp WebSocket client to Gemini Live API |
| `GeminiIntegrationService` | Orchestrates session state, message routing, reconnection |
| `InterviewService` | Database CRUD for interview sessions |
| `GradingService` | Post-interview AI evaluation via Gemini API |
| `InterviewPromptService` | Generates language-aware interviewer prompts |
| `CvProcessingService` | PDF/DOCX text extraction for CV upload |

### Controllers

| Controller | Purpose |
|------------|---------|
| `InterviewWebSocketController` | STOMP endpoint handlers (`/app/interview/*`) |
| `PageController` | Main page routing, redirects to setup wizard |
| `SetupController` | Multi-step interview setup wizard (`/setup/step1/2/3`) |
| `ReportController` | Server-rendered interview report (`/report/{sessionId}`) |
| `CvController` | REST endpoint for CV upload (`/api/cv/upload`) |
| `ApiKeyController` | API key validation and mode check (`/api/validate-key`, `/api/mode`) |
| `VoiceController` | Voice list and preview audio (`/api/voices/*`) |

### Configuration

| Config | Purpose |
|--------|---------|
| `GeminiConfig` | API keys, model names, voice, app mode |
| `WebSocketConfig` | STOMP broker configuration |
| `WebSocketEventListener` | Session connect/disconnect handling |
| `I18nConfig` | Locale resolver with cookie persistence |

---

## Features

### Voice Selection
Users can choose from 4 AI interviewer voices:

| Voice ID | English Name | Bulgarian Name | Gender |
|----------|--------------|----------------|--------|
| Algieba | George | Георги | Male |
| Kore | Victoria | Виктория | Female |
| Fenrir | Max | Макс | Male |
| Despina | Diana | Диана | Female |

Voice previews are available for both languages.

### Difficulty Levels

| Level | Behavior | CV Usage |
|-------|----------|----------|
| **Chill** | Friendly, encouraging, hints provided | Primary focus - conversational about projects |
| **Standard** | Professional, balanced questioning | Mixed with technical questions |
| **Stress** | Challenging, time pressure, follow-ups | Background context only - deep technical focus |

### Position-Specific Prompts
The AI tailors questions based on the target position:
- Java/Backend Developer → OOP, Spring Boot, databases, API design
- Frontend Developer → HTML/CSS/JS, React/Vue/Angular, UX
- QA Engineer → Testing methodologies, automation, bug tracking
- DevOps Engineer → CI/CD, cloud platforms, containerization
- Project/Product Manager → Planning, leadership, stakeholder management

### Session Resumption
The Gemini Live API connection supports automatic resumption:
- Session resumption handles are stored and updated
- On disconnect, the server attempts automatic reconnection
- Audio is buffered during reconnection and flushed on resume
- GoAway signals trigger proactive reconnection

### Context Window Compression
Enabled by default to allow unlimited session length (vs. 15-minute limit without).

### Interview Auto-Conclusion
The AI naturally concludes interviews after 5-7 questions. Conclusion phrases (in English and Bulgarian) are detected to trigger automatic grading.

---

## Database Schema

```sql
interview_sessions (
    id UUID PRIMARY KEY,
    candidate_name VARCHAR(255) NOT NULL,
    job_position VARCHAR(255) NOT NULL,
    difficulty VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    transcript TEXT,
    score INTEGER,
    feedback_json TEXT
)

interview_feedback (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    overall_score INTEGER NOT NULL,
    communication_score INTEGER NOT NULL,
    technical_score INTEGER NOT NULL,
    confidence_score INTEGER NOT NULL,
    strengths TEXT,
    improvements TEXT,
    detailed_analysis TEXT,
    verdict VARCHAR(50),  -- STRONG_HIRE, HIRE, MAYBE, NO_HIRE
    created_at TIMESTAMP NOT NULL
)
```

---

## WebSocket Endpoints

### Client → Server (STOMP)

| Destination | Purpose | Payload |
|-------------|---------|---------|
| `/app/interview/start` | Start interview session | `{candidateName, position, difficulty, language, cvText?, voiceId?, userApiKey?}` |
| `/app/interview/audio` | Send audio chunk | Base64-encoded 16kHz PCM |
| `/app/interview/end` | End interview manually | (none) |
| `/app/interview/mic-off` | Signal mic muted | (none) |

### Server → Client (User Queues)

| Destination | Purpose | Payload |
|-------------|---------|---------|
| `/user/queue/status` | Connection/turn status | `{type, message}` |
| `/user/queue/audio` | AI audio response | `{data: base64}` |
| `/user/queue/transcript` | Speech transcription | `{speaker, text}` |
| `/user/queue/report` | Final grading report | Full feedback object |
| `/user/queue/error` | Error messages | `{message, rateLimited?, invalidKey?}` |
| `/user/queue/text` | Text responses (rare) | `{text}` |

---

## REST API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/cv/upload` | Upload and extract CV text |
| `GET` | `/api/mode` | Get application mode (DEV/PROD) |
| `POST` | `/api/validate-key` | Validate Gemini API key |
| `GET` | `/api/voices` | Get available voice options |
| `GET` | `/api/voices/preview/{voiceId}/{language}` | Get voice preview audio |

---

## Configuration

```properties
# Application Mode
app.mode=${APP_MODE}  # DEV or PROD

# Database (PostgreSQL)
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# Gemini API (ignored in PROD mode)
gemini.api-key=${GEMINI_API_KEY:}
gemini.live-model=gemini-2.5-flash-native-audio-preview-12-2025
gemini.grading-model=gemini-3-flash-preview
gemini.voice-name=Fenrir

# Internationalization
spring.messages.basename=messages
spring.messages.encoding=UTF-8
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `APP_MODE` | Yes | `DEV` or `PROD` |
| `DB_HOST` | Yes | PostgreSQL host |
| `DB_PORT` | Yes | PostgreSQL port |
| `DB_NAME` | Yes | Database name |
| `DB_USERNAME` | Yes | Database user |
| `DB_PASSWORD` | Yes | Database password |
| `GEMINI_API_KEY` | DEV only | Gemini API key (not needed in PROD mode) |

---

## Internationalization (i18n)

The application supports two languages:
- **Bulgarian (bg)** - Default language
- **English (en)**

### Message Files
- `messages.properties` - Default (English fallback)
- `messages_bg.properties` - Bulgarian translations
- `messages_en.properties` - English translations

### Implementation
- Server-side rendering with Thymeleaf `#{message.key}` syntax
- Cookie-based locale persistence (`ui_lang` cookie, 1-year expiry)
- Language switching via `?lang=en|bg` URL parameter
- Form data preserved across language switches

---

## Deployment

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: interview_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: secret
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      APP_MODE: PROD
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: interview_db
      DB_USERNAME: postgres
      DB_PASSWORD: secret
    depends_on: [postgres]
```

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Project Structure

```
src/main/java/net/k2ai/interviewSimulator/
├── InterviewSimulatorApplication.java
├── config/
│   ├── GeminiConfig.java         # API keys, models, mode
│   ├── I18nConfig.java           # Locale resolver
│   ├── WebSocketConfig.java      # STOMP broker setup
│   └── WebSocketEventListener.java
├── controller/
│   ├── ApiKeyController.java     # Mode check, key validation
│   ├── CvController.java         # CV upload endpoint
│   ├── InterviewWebSocketController.java
│   └── VoiceController.java      # Voice list/preview
├── entity/
│   ├── InterviewFeedback.java
│   └── InterviewSession.java
├── exception/
│   └── RateLimitException.java
├── repository/
│   ├── InterviewFeedbackRepository.java
│   └── InterviewSessionRepository.java
└── service/
    ├── CvProcessingService.java      # PDF/DOCX extraction
    ├── GeminiIntegrationService.java # Session orchestration
    ├── GeminiLiveClient.java         # WebSocket client
    ├── GradingService.java           # AI evaluation
    ├── InterviewPromptService.java   # Prompt generation
    └── InterviewService.java         # Database operations

src/main/resources/
├── application.properties
├── messages.properties           # i18n (default)
├── messages_bg.properties        # Bulgarian
├── messages_en.properties        # English
├── db/migration/
│   └── V1__initial_schema.sql
├── static/
│   ├── audio/voices/             # Voice preview WAV files
│   └── js/
│       ├── apikey.js             # API key modal handling
│       ├── audio-processor.js    # WebSocket, mic, playback
│       ├── interview.js          # Interview UI controls
│       ├── language-switcher.js  # i18n UI handling
│       └── navigation.js         # View switching, form wizard
└── templates/
    ├── layouts/
    │   ├── main.html             # Base layout
    │   └── fragments/
    │       ├── apikey-modal.html # PROD mode API key modal
    │       ├── bodyBottom.html   # JS imports
    │       ├── head.html         # CDN links
    │       └── styles.html       # Custom CSS
    └── pages/
        ├── index.html            # Aggregates all views
        ├── setup.html            # 3-step configuration wizard
        ├── interview.html        # Live session UI
        └── report.html           # Post-interview feedback
```

---

## Future Roadmap

- [x] ~~User authentication (API key validation)~~
- [x] ~~Resume/CV parsing~~
- [x] ~~Voice selection~~
- [x] ~~Multi-language support~~
- [ ] Question bank system
- [ ] Video recording (optional)
- [ ] Interview history/replay
- [ ] OAuth integration for persistent user accounts

---

**See `copilot-instructions.md` for coding standards and development guidelines.**
