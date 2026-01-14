# CLAUDE.md

Developer guidance for AI assistants working with the Interview Simulator project.

## Project Overview

**AI-Powered Job Interview Simulator** - Real-time conversational practice using Gemini Live API with bidirectional audio streaming.

**Stack:** Spring Boot 4.0.0 | Java 21 | PostgreSQL | Thymeleaf | WebSocket/STOMP | OkHttp | Lombok

**Architecture:** Browser ↔ WebSocket/STOMP ↔ Spring Boot ↔ OkHttp WebSocket ↔ Gemini Live API

---

## 🚨 Critical Coding Standards

### Code Formatting (MANDATORY - NO EXCEPTIONS)

These rules are **strictly enforced** across the entire codebase:

1. **Every file MUST end with an empty line** - Java, SQL, XML, properties, HTML, CSS, JS, Markdown
2. **Two empty lines between methods** (including before the first method in a class)
3. **One empty line after the last method** (before closing class brace)
4. **One empty line between field declarations**
5. **Closing brace comments** - Every class/method closing brace MUST have comment with name
   ```java
   }//methodName
   }//ClassName
   ```
6. **Indentation:** 4 spaces (no tabs)
7. **Line length:** Maximum 400 characters
8. **Braces:** Opening brace on same line (`if (condition) {`)
9. **Keywords:** Single space after (`if `, `for `, `while `)

**Example:**
```java
@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final InterviewSessionRepository repository;

    private final GeminiIntegrationService geminiService;


    @Transactional
    public UUID startSession(String name, String position, String difficulty) {
        log.info("Starting interview session for: {}", name);
        InterviewSession session = InterviewSession.builder()
                .candidateName(name)
                .jobPosition(position)
                .difficulty(difficulty)
                .startedAt(LocalDateTime.now())
                .build();
        
        return repository.save(session).getId();
    }//startSession


    @Transactional
    public void finalizeSession(UUID sessionId) {
        InterviewSession session = repository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        
        session.setEndedAt(LocalDateTime.now());
        repository.save(session);
        
        log.info("Finalized interview session: {}", sessionId);
    }//finalizeSession

}//InterviewSessionServiceImpl
```

**Note the empty line at end of file above.**

---

## Naming Conventions

### Java Classes

#### Entities
- **Naming:** CamelCase - `InterviewSession`, `Question`, `InterviewFeedback`
- **Base class:** Extend `BaseEntity` (when implemented)
- **Lombok:** Use `@Getter`, `@Setter`, `@EqualsAndHashCode`, `@Builder`
- **Table names:** snake_case - `interview_sessions`, `questions`

#### Services
- **Interface:** `{Entity}Service` - `InterviewSessionService`, `QuestionService`
- **Implementation:** `{Entity}ServiceImpl` - `InterviewSessionServiceImpl`
- **Annotations:** `@Service`, `@RequiredArgsConstructor`, `@Slf4j`

#### Controllers
- **Naming:** `{Entity}Controller` - `InterviewSessionController`, `QuestionController`
- **WebSocket:** `{Entity}WebSocketController` - `InterviewWebSocketController`
- **REST:** `{Entity}RestController` for AJAX endpoints

#### Repositories
- **Naming:** `{Entity}Repository` - `InterviewSessionRepository`, `QuestionRepository`
- **Extend:** `JpaRepository<Entity, UUID>`

### Database

1. **Tables:** snake_case - `interview_sessions`, `interview_feedback`, `questions`
2. **Columns:** snake_case - `candidate_name`, `started_at`, `job_position`
3. **Flyway migrations:** `V{version}__{description}.sql`
   - Example: `V1__initial_schema.sql`, `V2__add_users.sql`
4. **🚨 NEVER use `${variable}` syntax in SQL** - Flyway treats as placeholder even in comments!

### Tests

1. **Test class:** `{Class}Test` - `InterviewSessionServiceTest`, `GeminiLiveClientTest`
2. **Test method:** `test{MethodName}` - `testStartSession`, `testSendAudio`
3. **Integration tests:** `{Class}IntegrationTest` - extend base test class when available
4. **Display names:** Use `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)`

---

## Lombok Annotations (Modern Java)

**Always use Lombok to reduce boilerplate code:**

### Core Annotations

1. **`@Slf4j`** - Automatic logger injection (replaces manual logger declarations)
   ```java
   @Slf4j
   @Service
   public class MyService {
       public void process() {
           log.info("Processing..."); // Logger automatically available
       }
   }
   ```

2. **`@RequiredArgsConstructor`** - Constructor injection for `final` fields
   ```java
   @RequiredArgsConstructor
   @Service
   public class MyService {
       private final MyRepository repository; // Constructor auto-generated
       private final MyOtherService otherService;
   }
   ```

3. **`@Data`** - For entities (generates getters, setters, equals, hashCode, toString)
   ```java
   @Data
   @Entity
   public class InterviewSession {
       private UUID id;
       private String candidateName;
   }
   ```

4. **`@Builder`** - Fluent object construction
   ```java
   @Data
   @Builder
   @Entity
   public class InterviewSession {
       // ...
   }
   
   // Usage:
   InterviewSession session = InterviewSession.builder()
       .candidateName("John Doe")
       .jobPosition("Java Developer")
       .build();
   ```

5. **`@Getter` / `@Setter`** - Individual field accessors
   ```java
   @Getter
   @Setter
   @Entity
   public class User {
       private String name;
       private String email;
   }
   ```

---

## Logging Standards

### When to Log

**DO log:**
- External API calls (Gemini, authentication services)
- WebSocket connection events (connect, disconnect, errors)
- Security operations (authentication, authorization failures)
- Important state changes (session start/end, grading completion)
- File operations (if implemented - transcripts, audio files)
- Error conditions (exceptions, validation failures)
- Scheduled tasks (cleanup, monitoring)

**DON'T log:**
- Simple CRUD operations (JPA/Hibernate logs SQL automatically)
- Entity constructors, getters, setters
- Every method entry/exit (performance overhead)
- DTO mapping operations

### Log Levels

- **`log.error()`** - Exceptions, critical failures
  ```java
  log.error("Failed to connect to Gemini API", exception);
  ```

- **`log.warn()`** - Recoverable issues, fallbacks, approaching limits
  ```java
  log.warn("Session {} approaching time limit", sessionId);
  ```

- **`log.info()`** - Important business events
  ```java
  log.info("Started interview session: {} for candidate: {}", sessionId, name);
  ```

- **`log.debug()`** - Detailed diagnostic information
  ```java
  log.debug("Received audio chunk: {} bytes", audioData.length);
  ```

### Contextual Logging

Always include **context** in log messages:

```java
// ❌ BAD
log.info("Session started");

// ✅ GOOD
log.info("Started interview session: {} for candidate: {}", sessionId, candidateName);

// ❌ BAD
log.error("Connection failed");

// ✅ GOOD
log.error("Gemini WebSocket connection failed for session: {}", sessionId, exception);
```

---

## Architectural Patterns

### Base Classes (Recommended)

All entities should extend `BaseEntity` for consistency:

```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}//BaseEntity
```

### Service Layer Pattern

Use **interface + implementation** for services:

```java
// Interface
public interface InterviewSessionService {
    UUID startSession(String name, String position, String difficulty);
    void appendTranscript(UUID sessionId, String text);
    void finalizeSession(UUID sessionId);
}

// Implementation
@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {
    
    private final InterviewSessionRepository repository;
    
    @Override
    @Transactional
    public UUID startSession(String name, String position, String difficulty) {
        // Implementation
    }//startSession
    
}//InterviewSessionServiceImpl
```

### Exception Handling

Use **custom exceptions** + **global exception handler**:

```java
// Custom exception
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID sessionId) {
        super("Interview session not found: " + sessionId);
    }
}

// Global handler
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex) {
        log.error("Session not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }//handleSessionNotFound
    
}//GlobalExceptionHandler
```

---

## Testing Standards

### Unit Tests

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InterviewSessionServiceTest {
    
    @Mock
    private InterviewSessionRepository repository;
    
    @InjectMocks
    private InterviewSessionServiceImpl service;
    
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }//setUp
    
    
    @Test
    void testStartSession() {
        // Given
        String name = "John Doe";
        InterviewSession session = InterviewSession.builder()
                .candidateName(name)
                .build();
        
        when(repository.save(any())).thenReturn(session);
        
        // When
        UUID result = service.startSession(name, "Developer", "MID");
        
        // Then
        assertNotNull(result);
        verify(repository).save(any(InterviewSession.class));
    }//testStartSession
    
}//InterviewSessionServiceTest
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class InterviewSessionIntegrationTest {
    
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private InterviewSessionRepository repository;
    
    
    @Test
    void testCreateSession() throws Exception {
        mockMvc.perform(post("/interview/start")
                .param("name", "Jane Doe")
                .param("position", "DevOps")
                .param("difficulty", "SENIOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").exists());
        
        assertEquals(1, repository.count());
    }//testCreateSession
    
}//InterviewSessionIntegrationTest
```

---

## Configuration Management

### Environment Variables

Use environment variables for sensitive data:

```properties
# application.properties
gemini.api-key=${GEMINI_API_KEY}
spring.datasource.url=jdbc:postgresql://localhost:5432/interview_simulator
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

### Configuration Classes

Use `@ConfigurationProperties` for typed configuration:

```java
@Slf4j
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {
    
    private String apiKey;
    
    private String modelName = "gemini-2.0-flash-exp";
    
    private List<String> modalities = List.of("AUDIO", "TEXT");
    
    
    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Gemini API key not configured!");
            throw new IllegalStateException("GEMINI_API_KEY environment variable required");
        }
    }//validate
    
}//GeminiConfig
```

---

## WebSocket Development

### STOMP Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }//configureMessageBroker
    
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/interview")
                .setAllowedOrigins("*")
                .withSockJS();
    }//registerStompEndpoints
    
}//WebSocketConfig
```

### WebSocket Controller

```java
@Slf4j
@RequiredArgsConstructor
@Controller
public class InterviewWebSocketController {
    
    private final GeminiIntegrationService geminiService;
    
    
    @MessageMapping("/interview/audio")
    public void handleAudio(@Payload byte[] pcmData, Principal principal) {
        log.debug("Received audio from browser: {} bytes", pcmData.length);
        geminiService.sendAudioToGemini(pcmData);
    }//handleAudio
    
    
    @MessageMapping("/interview/start")
    public void startInterview(@Payload InterviewStartRequest request, Principal principal) {
        log.info("Starting interview for user: {}", principal.getName());
        geminiService.initializeSession(request);
    }//startInterview
    
}//InterviewWebSocketController
```

---

## Common Workflows

### Adding New Entity

1. Create entity extending `BaseEntity` (when available)
2. Create repository extending `JpaRepository<Entity, UUID>`
3. Create service interface
4. Create service implementation with `@Slf4j`, `@RequiredArgsConstructor`, `@Service`
5. Create controller (if needed)
6. Create Flyway migration (`V{X}__{description}.sql`)
7. Write unit tests
8. Write integration tests

### Database Changes

1. Create migration in `src/main/resources/db/migration/`
2. Name: `V{version}__{description}.sql`
3. **🚨 NEVER use `${...}` syntax** - Flyway treats as placeholder
4. Test with `mvn flyway:migrate`

### Adding WebSocket Endpoint

1. Define `@MessageMapping` in WebSocket controller
2. Implement message handling logic
3. Use `SimpMessagingTemplate` to send responses to clients
4. Test with WebSocket client (frontend or test tool)

---

## Security Best Practices

1. **Never expose API keys** - Use environment variables only
2. **Validate all inputs** - Use Bean Validation (`@Valid`, `@NotNull`, etc.)
3. **Custom exceptions** - Never expose stack traces to clients
4. **SQL injection** - Always use JPA/parameterized queries
5. **Authentication** - Implement Spring Security when multi-user support added
6. **WebSocket security** - Authenticate WebSocket connections with Principal

---

## Performance Considerations

1. **Use `@Transactional`** for service methods that modify data
2. **Connection pooling** - Configure HikariCP for database connections
3. **WebSocket limits** - Implement max concurrent sessions
4. **Audio streaming** - Use appropriate buffer sizes (avoid memory issues)
5. **Rate limiting** - Implement Gemini API rate limit handling
6. **Logging** - Use appropriate levels (don't log in tight loops)

---

## Code Review Checklist

Before submitting code, verify:

- ✅ Every file ends with empty line
- ✅ Two empty lines between methods
- ✅ Closing brace comments present
- ✅ `@Slf4j` used instead of manual logger
- ✅ Logging at appropriate level with context
- ✅ No sensitive data in logs (API keys, passwords)
- ✅ Proper exception handling (custom exceptions)
- ✅ Unit tests written for new methods
- ✅ Integration tests for new endpoints
- ✅ Flyway migration for database changes
- ✅ Environment variables for configuration
- ✅ Javadoc for public methods (if non-obvious)

---

## Development Commands

**Build:** `mvn clean compile`  
**Run:** `mvn spring-boot:run` (port 8080)  
**Package:** `mvn clean package`  
**Full build:** `mvn clean install`

**Testing:**
- `mvn test` - Run all tests
- `mvn test -Dtest=ClassNameTest` - Run specific test
- `mvn test -Dtest=ClassNameTest#testMethodName` - Run specific test method

**Database:**
- `mvn flyway:migrate` - Apply migrations
- `mvn flyway:info` - Show migration status
- `mvn flyway:validate` - Validate migrations

---

## Additional Resources

- **Architecture:** See `ARCHITECTURE.md` for complete system design
- **Spring Boot Docs:** https://docs.spring.io/spring-boot/docs/current/reference/html/
- **Lombok:** https://projectlombok.org/features/
- **Flyway:** https://flywaydb.org/documentation/
- **Gemini Live API:** https://ai.google.dev/api/live

---

## Project Philosophy

This project values:

1. **Consistency** - Uniform code style makes collaboration easier
2. **Simplicity** - Readable code over clever code
3. **Testability** - Well-tested code is maintainable code
4. **Documentation** - Code should be self-documenting with clear naming
5. **Security** - Never compromise on security for convenience
6. **Performance** - But not at the cost of readability

**Remember:** The goal is to build a **professional, maintainable, production-ready** interview simulator. Every line of code should reflect this standard.
