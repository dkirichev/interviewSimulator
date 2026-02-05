# copilot-instructions.md

AI assistant guidance for the Interview Simulator project.

## Project Overview

**AI-Powered Job Interview Simulator** using Gemini Live API with real-time bidirectional audio.

**Stack:** Spring Boot 4.0.0 | Java 21 | PostgreSQL | Thymeleaf | WebSocket/STOMP | OkHttp | Lombok | PDFBox | Apache POI

**Features:**
- Real-time voice conversation with AI interviewer
- Multi-language support (English/Bulgarian)
- CV/Resume upload and parsing (PDF/DOCX)
- Voice selection (4 interviewer voices)
- DEV/PROD modes (backend vs user-provided API keys)
- Position-specific and difficulty-aware interviews
- Automatic grading and detailed feedback

---

## 🚨 Mandatory Code Formatting

These rules are **non-negotiable**:

| Rule | Example |
|------|---------|
| File ends with empty line | All files: `.java`, `.sql`, `.xml`, `.html`, `.js` |
| Two empty lines between methods | Includes before first method |
| One empty line between fields | Each field declaration separated |
| Closing brace comments | `}//methodName` and `}//ClassName` |
| 4-space indentation | No tabs |
| Same-line braces | `if (x) {` not `if (x)\n{` |

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class MyService {

    private final MyRepository repository;


    public void doWork() {
        log.info("Working");
    }//doWork

}//MyService
```

---

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Entity | CamelCase | `InterviewSession` |
| Table | snake_case | `interview_sessions` |
| Service Interface | `{Name}Service` | `InterviewService` |
| Service Impl | `{Name}ServiceImpl` | `InterviewServiceImpl` |
| Repository | `{Entity}Repository` | `InterviewSessionRepository` |
| Controller | `{Name}Controller` | `InterviewWebSocketController` |
| Test | `{Class}Test` | `InterviewServiceTest` |
| Migration | `V{n}__{desc}.sql` | `V1__initial_schema.sql` |

**⚠️ NEVER use `${var}` in SQL files** - Flyway treats it as placeholder!

---

## Required Lombok Annotations

| Annotation | Use Case |
|------------|----------|
| `@Slf4j` | All services/controllers (auto-injects `log`) |
| `@RequiredArgsConstructor` | Constructor injection for `final` fields |
| `@Data` | Entities (getters/setters/equals/hashCode) |
| `@Builder` | Fluent object construction |

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class MyService {
    private final MyRepository repo;  // Auto-injected via constructor
    
    public void work() {
        log.info("Working");  // log available via @Slf4j
    }//work
}//MyService
```

---

## Logging Standards

**Log these:** External API calls, WebSocket events, errors, state changes  
**Don't log:** Simple CRUD, getters/setters, every method entry/exit

| Level | Use |
|-------|-----|
| `error` | Exceptions, critical failures |
| `warn` | Recoverable issues, approaching limits |
| `info` | Business events (session start/end) |
| `debug` | Diagnostic details (audio chunks) |

```java
// ❌ log.info("Started");
// ✅ log.info("Started session {} for {}", sessionId, name);
```

---

## Architectural Patterns

### Service Layer
```java
// Interface
public interface MyService {
    void doWork();
}

// Implementation
@Slf4j
@RequiredArgsConstructor
@Service
public class MyServiceImpl implements MyService {
    private final MyRepository repo;
    
    @Override
    @Transactional
    public void doWork() { }//doWork
}//MyServiceImpl
```

### Exception Handling
```java
// Custom exception
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID id) {
        super("Session not found: " + id);
    }
}

// Global handler with @ControllerAdvice
```

### Entity Pattern
```java
@Entity
@Table(name = "my_table")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}//MyEntity
```

---

## WebSocket Endpoints

| Client → Server | Purpose |
|-----------------|---------|
| `/app/interview/start` | Start interview session with config |
| `/app/interview/audio` | Send audio chunk (base64 PCM) |
| `/app/interview/end` | End interview manually |
| `/app/interview/mic-off` | Signal mic muted |

| Server → Client | Purpose |
|-----------------|---------|
| `/user/queue/status` | Connection/turn status |
| `/user/queue/audio` | AI audio response |
| `/user/queue/transcript` | Speech transcription (user/AI) |
| `/user/queue/report` | Final grading report |
| `/user/queue/error` | Error messages (with flags: rateLimited, invalidKey, requiresApiKey) |

## REST API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/` | Redirects to `/setup/step1` |
| `GET` | `/setup/step1` | Setup wizard - Step 1 (Profile) |
| `GET` | `/setup/step2` | Setup wizard - Step 2 (Details + CV) |
| `GET` | `/setup/step3` | Setup wizard - Step 3 (Voice + Language) |
| `GET` | `/interview` | Interview page (requires completed setup in session) |
| `GET` | `/report/{sessionId}` | Server-rendered report page |
| `POST` | `/api/cv/upload` | Upload CV (PDF/DOCX), returns extracted text |
| `GET` | `/api/mode` | Get app mode (DEV/PROD) |
| `POST` | `/api/validate-key` | Validate Gemini API key |
| `GET` | `/api/voices` | Get available voice options |
| `GET` | `/api/voices/preview/{voiceId}/{language}` | Get voice preview WAV |

---

## Frontend Architecture

**Approach:** Server-rendered multi-page wizard with minimal JavaScript

### Page Flow
```
/setup/step1 → /setup/step2 → /setup/step3 → /interview → /report/{id}
```

### Session Management
- Form data stored in HTTP session via `@SessionAttributes("setupForm")`
- Persists across wizard steps and language switches
- Cleared after interview starts (one-time use)
- Uses `SessionStatus.setComplete()` for proper cleanup

### JavaScript (1,409 lines total - 41% reduced)
- `audio-processor.js` (588 lines) - WebSocket + audio processing
- `interview.js` (308 lines) - Interview UI controls
- `apikey.js` (363 lines) - API key modal
- `language-switcher.js` (150 lines) - Language switching

All JavaScript is essential for browser APIs (WebSocket, Web Audio, getUserMedia).

---

## Development Commands

```bash
mvn clean compile          # Build
mvn spring-boot:run        # Run (port 8080)
mvn test                   # All tests
mvn test -Dtest=MyTest     # Specific test
mvn flyway:migrate         # Apply DB migrations
```

### Environment Variables for Development

```bash
export APP_MODE=DEV
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=interview_db
export DB_USERNAME=postgres
export DB_PASSWORD=secret
export GEMINI_API_KEY=AIza...
```

---

## Service Layer Overview

| Service | Responsibility |
|---------|----------------|
| `GeminiIntegrationService` | Session lifecycle, WebSocket routing, reconnection |
| `GeminiLiveClient` | Low-level WebSocket to Gemini API |
| `InterviewService` | Database CRUD for sessions |
| `GradingService` | AI-powered evaluation after interview |
| `InterviewPromptService` | Language/difficulty/position-aware prompts |
| `CvProcessingService` | PDF/DOCX text extraction |

---

## Internationalization (i18n)

Message files: `messages.properties`, `messages_bg.properties`, `messages_en.properties`

### Naming Convention for Messages
```properties
# Format: section.subsection.element.attribute
setup.step1.candidateName=Candidate Name
setup.step1.candidateName.placeholder=e.g., John Doe
report.verdict.strongHire=STRONG HIRE
apikey.modal.title=Gemini API Key Required
```

### Using in Templates
```html
<span th:text="#{setup.title}" />
<input th:placeholder="#{setup.step1.candidateName.placeholder}" />
```

### Adding New Translations
1. Add key to `messages.properties` (English)
2. Add Bulgarian translation to `messages_bg.properties`
3. Use `#{key.name}` in Thymeleaf templates

---

## Code Review Checklist

- [ ] File ends with empty line
- [ ] Two empty lines between methods
- [ ] Closing brace comments (`}//name`)
- [ ] `@Slf4j` instead of manual logger
- [ ] Contextual log messages
- [ ] No secrets in logs
- [ ] Custom exceptions used
- [ ] Tests written
- [ ] Flyway migration for DB changes
- [ ] i18n messages for new UI text
- [ ] Both English and Bulgarian translations added

---

## Documentation

For complete documentation, see the `docs/` folder:

| Document | Description |
|----------|-------------|
| [README.md](README.md) | Project overview and quick start |
| [docs/SETUP.md](docs/SETUP.md) | Local development setup |
| [docs/DOCKER.md](docs/DOCKER.md) | Docker deployment guide |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture |
| [docs/API.md](docs/API.md) | REST and WebSocket API reference |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Contribution guidelines |
