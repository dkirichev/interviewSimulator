# 🏗️ Architecture Documentation

Comprehensive technical documentation for the AI Interview Simulator.

## Table of Contents

- [System Overview](#system-overview)
- [Architecture Diagram](#architecture-diagram)
- [Technology Stack](#technology-stack)
- [Core Components](#core-components)
- [Data Flow](#data-flow)
- [Database Schema](#database-schema)
- [Application Modes](#application-modes)
- [Key Features Deep Dive](#key-features-deep-dive)
- [Project Structure](#project-structure)

---

## System Overview

The AI Interview Simulator is a **real-time voice-based interview practice platform** that uses Google's Gemini Live API for bidirectional audio streaming. Users can practice job interviews with an AI interviewer that responds naturally in real-time.

### Key Characteristics

- **Real-time audio streaming** - No record-then-send, pure live conversation
- **Server-side orchestration** - Spring Boot manages sessions and security
- **Multi-language support** - English and Bulgarian with localized prompts
- **CV-aware interviews** - AI uses uploaded resumes for personalized questions
- **Automatic grading** - Post-interview AI analysis and scoring

---

## Architecture Diagram

```
┌─────────────────┐      STOMP/WebSocket      ┌──────────────────┐     OkHttp WebSocket     ┌─────────────────┐
│                 │                           │                  │                          │                 │
│     Browser     │ ◄───────────────────────► │   Spring Boot    │ ◄──────────────────────► │ Gemini Live API │
│   (16kHz PCM)   │                           │   Orchestrator   │                          │   (24kHz PCM)   │
│                 │                           │                  │                          │                 │
└─────────────────┘                           └──────────────────┘                          └─────────────────┘
        │                                             │
        │ REST API                                    │
        ├────────────────────────────────────────────►│
        │ • CV upload                                 │
        │ • API key validation                        │
        │ • Voice preview                             │
        │ • Mode check                                │◄────► PostgreSQL
        │                                             │       (Sessions & Feedback)
```

### Why Three-Tier Architecture?

| Benefit | Description |
|---------|-------------|
| **Security** | API keys handled server-side, never exposed to browser |
| **Persistence** | Transcripts and feedback stored permanently |
| **Business Logic** | Grading, prompts, session management centralized |
| **Multi-user** | Concurrent sessions with proper isolation |
| **i18n** | Server-side internationalization in English/Bulgarian |

---

## Technology Stack

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| **Spring Boot** | 4.0.0 | Application framework |
| **Java** | 21 | Runtime (LTS) |
| **Spring WebSocket** | - | STOMP broker for browser communication |
| **OkHttp** | 4.12.0 | WebSocket client for Gemini API |
| **Spring Data JPA** | - | Database abstraction |
| **Flyway** | - | Database migrations |
| **Lombok** | - | Boilerplate reduction |
| **Apache PDFBox** | 3.0.4 | PDF text extraction |
| **Apache POI** | 5.3.0 | DOCX text extraction |

### Frontend

| Technology | Purpose |
|------------|---------|
| **Vanilla JavaScript** | ~1,400 lines, browser APIs only |
| **Web Audio API** | Microphone capture & audio playback |
| **STOMP.js** | WebSocket messaging protocol |
| **SockJS** | WebSocket fallback for older browsers |
| **Tailwind CSS** | Utility-first styling (CDN) |
| **Thymeleaf** | Server-side HTML rendering |

### AI Models

| Model | Purpose |
|-------|---------|
| **Gemini 2.5 Flash** | Real-time audio conversations (live-model) |
| **Gemini 3 Flash** | Post-interview grading (grading-model) |

### Infrastructure

| Component | Purpose |
|-----------|---------|
| **PostgreSQL 16** | Primary database |
| **Docker** | Containerization |
| **Docker Compose** | Multi-container orchestration |

---

## Core Components

### Backend Services

```
src/main/java/net/k2ai/interviewSimulator/
├── service/
│   ├── GeminiIntegrationService.java   # Session lifecycle, message routing
│   ├── GeminiLiveClient.java           # Low-level WebSocket to Gemini API
│   ├── InterviewService.java           # Database CRUD for sessions
│   ├── GradingService.java             # AI-powered post-interview evaluation
│   ├── InterviewPromptService.java     # Language/difficulty-aware prompts
│   ├── CvProcessingService.java        # PDF/DOCX text extraction
│   ├── InputSanitizerService.java      # Input validation & sanitization
│   └── RateLimitService.java           # API key validation rate limiting
```

### Controllers

| Controller | Path | Purpose |
|------------|------|---------|
| `SetupController` | `/setup/*` | Multi-step interview setup wizard |
| `InterviewWebSocketController` | `/app/interview/*` | STOMP message handlers |
| `ReportController` | `/report/{id}` | Server-rendered interview reports |
| `ApiKeyController` | `/api/mode`, `/api/validate-key` | API key validation |
| `CvController` | `/api/cv/upload` | CV file upload |
| `VoiceController` | `/api/voices/*` | Voice list and preview audio |

### Configuration Classes

| Config | Purpose |
|--------|---------|
| `GeminiConfig` | API keys, model names, app mode |
| `WebSocketConfig` | STOMP broker configuration |
| `WebSocketEventListener` | Session connect/disconnect handling |
| `I18nConfig` | Locale resolver with cookie persistence |
| `SecurityConfig` | Spring Security configuration |

---

## Data Flow

### Audio Streaming Flow

```
1. User speaks → Browser captures 16kHz PCM via Web Audio API
2. Browser → Server → STOMP message to /app/interview/audio (Base64)
3. Server → Gemini → OkHttp WebSocket sends Base64 PCM
4. Gemini → Server → Returns 24kHz audio + transcriptions
5. Server → Browser → STOMP to /user/queue/audio
6. Browser plays → Web Audio API at 24kHz (gapless crossfade scheduling)
```

### CV Processing Flow

```
1. User uploads → PDF/DOCX file (max 2MB)
2. Browser → Server → POST /api/cv/upload with multipart form
3. Server extracts → Text via PDFBox (PDF) or Apache POI (DOCX)
4. Text included → In system instruction for personalized interview
```

### Interview Session Flow

```
1. /setup/step1 → User enters name
2. /setup/step2 → User selects position, difficulty, uploads CV
3. /setup/step3 → User selects language and voice
4. /interview → WebSocket connects, interview begins
5. AI greets user → Interview conversation flows
6. AI concludes → Automatic grading triggered
7. /report/{id} → User views detailed feedback
```

---

## Database Schema

### Tables

```sql
-- Interview Sessions
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

-- Interview Feedback
interview_feedback (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    overall_score INTEGER NOT NULL,
    communication_score INTEGER NOT NULL,
    technical_score INTEGER NOT NULL,
    confidence_score INTEGER NOT NULL,
    strengths TEXT,           -- JSON array
    improvements TEXT,        -- JSON array
    detailed_analysis TEXT,
    verdict VARCHAR(50),      -- STRONG_HIRE, HIRE, MAYBE, NO_HIRE
    created_at TIMESTAMP NOT NULL
)
```

### Indexes

```sql
CREATE INDEX idx_sessions_candidate ON interview_sessions(candidate_name);
CREATE INDEX idx_sessions_started_at ON interview_sessions(started_at);
CREATE INDEX idx_feedback_session ON interview_feedback(session_id);
```

---

## Application Modes

| Mode | API Key Source | Use Case |
|------|----------------|----------|
| **DEV** | `GEMINI_API_KEY` env var on server | Local development |
| **PROD** | User provides via modal | Production deployment |

### PROD Mode Flow

1. User loads page → App checks mode via `/api/mode`
2. Modal appears → Step-by-step guide to get free API key
3. User enters key → Validated via `/api/validate-key`
4. Key stored → In browser localStorage (never sent to our server again)
5. Key sent → Only to Gemini API via WebSocket

---

## Key Features Deep Dive

### Voice Selection

| Voice ID | English Name | Bulgarian Name | Gender |
|----------|--------------|----------------|--------|
| Algieba | George | Георги | Male |
| Kore | Victoria | Виктория | Female |
| Fenrir | Max | Макс | Male |
| Despina | Diana | Диана | Female |

Voice previews available as WAV files for both languages.

### Difficulty Levels

| Level | Behavior | CV Usage |
|-------|----------|----------|
| **Chill** | Friendly, encouraging, hints provided | Primary focus - conversational about projects |
| **Standard** | Professional, balanced questioning | Mixed with technical questions |
| **Stress** | Challenging, time pressure, follow-ups | Background context only - deep technical focus |

### Position-Specific Prompts

The AI tailors questions based on target position:
- **Java/Backend** → OOP, Spring Boot, databases, API design
- **Frontend** → HTML/CSS/JS, React/Vue/Angular, UX
- **QA Engineer** → Testing methodologies, automation
- **DevOps** → CI/CD, cloud, containerization
- **PM/Product** → Leadership, planning, stakeholders

### Session Resumption

The Gemini Live API connection supports automatic resumption:
- Session handles stored and updated on each message
- On disconnect, server attempts automatic reconnection
- Audio buffered during reconnection, flushed on resume
- GoAway signals trigger proactive reconnection

### Interview Auto-Conclusion

The AI naturally concludes interviews after 5-7 questions. Conclusion phrases (English and Bulgarian) are pattern-matched to trigger automatic grading:

```java
// English patterns
"thank you for your time"
"that concludes our interview"
"we'll be in touch"

// Bulgarian patterns
"благодаря ви за отделеното време"
"ще се свържем с вас"
```

---

## Project Structure

```
src/main/java/net/k2ai/interviewSimulator/
├── InterviewSimulatorApplication.java
├── config/
│   ├── GeminiConfig.java
│   ├── I18nConfig.java
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   └── WebSocketEventListener.java
├── controller/
│   ├── ApiKeyController.java
│   ├── CvController.java
│   ├── InterviewWebSocketController.java
│   ├── LegalController.java
│   ├── ReportController.java
│   ├── SetupController.java
│   └── VoiceController.java
├── dto/
│   └── InterviewSetupDTO.java
├── entity/
│   ├── InterviewFeedback.java
│   └── InterviewSession.java
├── exception/
│   └── RateLimitException.java
├── repository/
│   ├── InterviewFeedbackRepository.java
│   └── InterviewSessionRepository.java
├── service/
│   ├── CvProcessingService.java
│   ├── GeminiIntegrationService.java
│   ├── GeminiLiveClient.java
│   ├── GradingService.java
│   ├── InputSanitizerService.java
│   ├── InterviewPromptService.java
│   ├── InterviewService.java
│   └── RateLimitService.java
└── validation/
    └── (custom validators)

src/main/resources/
├── application.properties
├── messages.properties         # English (default)
├── messages_bg.properties      # Bulgarian
├── messages_en.properties      # English
├── db/migration/
│   └── V1__initial_schema.sql
├── static/
│   ├── audio/voices/          # Voice preview WAV files
│   └── js/
│       ├── apikey.js          # API key modal handling
│       ├── audio-processor.js # WebSocket, mic, playback
│       ├── interview.js       # Interview UI controls
│       └── language-switcher.js
└── templates/
    ├── layouts/
    │   ├── main.html          # Base layout
    │   └── fragments/
    │       ├── apikey-modal.html
    │       ├── bodyBottom.html
    │       ├── head.html
    │       └── styles.html
    └── pages/
        ├── setup/
        │   ├── step1.html     # Profile
        │   ├── step2.html     # Details + CV
        │   └── step3.html     # Voice & Language
        ├── interview-standalone.html
        ├── report-standalone.html
        └── report-error.html
```

---

## WebSocket Message Reference

### Client → Server

| Destination | Purpose | Payload |
|-------------|---------|---------|
| `/app/interview/start` | Start session | `{candidateName, position, difficulty, language, cvText?, voiceId?, userApiKey?}` |
| `/app/interview/audio` | Send audio chunk | Base64-encoded 16kHz PCM |
| `/app/interview/end` | End interview | (none) |
| `/app/interview/mic-off` | Signal mic muted | (none) |

### Server → Client

| Destination | Purpose | Payload |
|-------------|---------|---------|
| `/user/queue/status` | Connection/turn status | `{type, message}` |
| `/user/queue/audio` | AI audio response | `{data: base64}` |
| `/user/queue/transcript` | Speech transcription | `{speaker, text}` |
| `/user/queue/report` | Final grading report | Full feedback object |
| `/user/queue/error` | Error messages | `{message, rateLimited?, invalidKey?}` |

---

[← Back to README](../README.md) | [API Reference →](API.md)
