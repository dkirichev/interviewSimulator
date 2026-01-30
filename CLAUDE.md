# CLAUDE.md

AI assistant guidance for the Interview Simulator project.

## Project Overview

**AI-Powered Job Interview Simulator** using Gemini Live API with real-time bidirectional audio.

**Stack:** Spring Boot 4.0.0 | Java 21 | PostgreSQL | Thymeleaf | WebSocket/STOMP | OkHttp | Lombok

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
| `/app/interview/start` | Start interview session |
| `/app/interview/audio` | Send audio chunk |
| `/app/interview/end` | End interview |
| `/app/interview/mic-off` | Signal mic muted |

| Server → Client | Purpose |
|-----------------|---------|
| `/user/queue/status` | Connection/turn status |
| `/user/queue/audio` | AI audio response |
| `/user/queue/transcript` | Speech transcription |
| `/user/queue/report` | Final grading report |
| `/user/queue/error` | Error messages |

---

## Development Commands

```bash
mvn clean compile          # Build
mvn spring-boot:run        # Run (port 8080)
mvn test                   # All tests
mvn test -Dtest=MyTest     # Specific test
mvn flyway:migrate         # Apply DB migrations
```

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
