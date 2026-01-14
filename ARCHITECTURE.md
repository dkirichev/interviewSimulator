# AI Interview Simulator - Architecture & Implementation Plan

## System Overview

This project is an **AI-powered job interview simulator** that provides real-time conversational practice using Google's Gemini Live API with bidirectional audio streaming.

## Architecture

```
┌─────────────┐         WebSocket/STOMP          ┌──────────────────┐         OkHttp WebSocket        ┌─────────────────┐
│   Browser   │ ◄──────────────────────────────► │  Spring Boot     │ ◄────────────────────────────► │ Gemini Live API │
│             │                                   │                  │                                 │                 │
│  - Mic      │ ───── PCM Audio (Base64) ──────► │ - Orchestration  │ ── JSON realtime_input ──────► │ - AI Processing │
│  - Speaker  │ ◄──── Audio + Events ───────────  │ - State Mgmt     │ ◄─ server_content (Audio/Text) │ - Speech-to-Text│
│  - UI       │                                   │ - Persistence    │                                 │ - Text-to-Speech│
└─────────────┘                                   └──────────────────┘                                 └─────────────────┘
```

### Why This Architecture?

This **3-tier WebSocket design** provides significant advantages over direct browser-to-API connections:

- ✅ **Security:** API keys remain server-side, never exposed to clients
- ✅ **Session Management:** Persistent storage of transcripts and analytics
- ✅ **Multi-User Support:** Concurrent interview sessions with resource management
- ✅ **Business Logic:** Grading, question selection, and feedback generation
- ✅ **Rate Limiting:** Server-controlled API usage and cost management
- ✅ **Monitoring:** Centralized logging and health checks

## Technology Stack

### Backend
- **Spring Boot 4.0.0** (Java 21 with virtual threads)
- **PostgreSQL** + Flyway for database migrations
- **Thymeleaf** for server-side rendering
- **WebSocket/STOMP** for real-time browser communication
- **OkHttp** for Gemini Live API WebSocket client
- **Jackson** for JSON processing
- **Lombok** for boilerplate reduction
- **Spring Security** for authentication/authorization

### Frontend
- **Vanilla JavaScript** (audio processing)
- **WebSocket API** for real-time communication
- **Web Audio API** for microphone access
- **HTML5 + CSS3** for UI

---

## Project Standards & Conventions

This project follows professional enterprise Java conventions for maintainability, testability, and scalability.

### 1. Code Formatting (CRITICAL - Enforced for Consistency)

**These rules are mandatory and enforced across the entire codebase:**

- ✅ `@Slf4j` annotation instead of manual logger declarations
- ✅ One empty line between field declarations
- ✅ Two empty lines between method definitions
- ✅ Closing brace comments: `}//methodName` or `}//ClassName`
- ✅ Every file MUST end with an empty line (all file types: `.java`, `.sql`, `.xml`, `.properties`)
- ✅ 4-space indentation (no tabs)
- ✅ Maximum 400 characters per line
- ✅ Opening braces on same line: `if (condition) {`
- ✅ Single space after keywords: `if `, `for `, `while `

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
        // Implementation
    }//startSession


    @Transactional
    public void finalizeSession(UUID sessionId) {
        log.info("Finalizing interview session: {}", sessionId);
        // Implementation
    }//finalizeSession

}//InterviewSessionServiceImpl
```

### 2. Lombok Usage (Reduce Boilerplate)

Modern Java development with Lombok annotations:

- ✅ `@Slf4j` - Automatic logger injection (use `log.info()`, `log.error()`, etc.)
- ✅ `@RequiredArgsConstructor` - Constructor injection for `final` fields
- ✅ `@Data` - Entities: generates getters, setters, equals, hashCode, toString
- ✅ `@Builder` - Fluent object construction
- ✅ `@Getter/@Setter` - Individual field accessors when needed

**Example:**
```java
@Slf4j
@RequiredArgsConstructor
@Service
public class MyService {
    private final MyRepository repository; // Constructor auto-generated
    
    public void doSomething() {
        log.info("Doing something"); // Logger auto-injected
    }
}
```

### 3. Logging Strategy (Be Selective)

**DO log:**
- External API calls (Gemini, OpenAI, etc.)
- Security operations (authentication, authorization failures)
- File operations (upload, download, delete)
- Important state changes (session start/end, grading completion)
- Error conditions (exceptions, validation failures)

**DON'T log:**
- Simple CRUD operations (database logging handles this)
- Entity/DTO constructors or getters/setters
- Every method entry/exit (performance overhead)

**Log Levels:**
- `log.error()` - Exceptions and critical failures
- `log.warn()` - Recoverable issues (fallback used, rate limit approaching)
- `log.info()` - Important business events (session started, grading complete)
- `log.debug()` - Detailed diagnostic info (message content, audio chunk sizes)

---

## Recommended Architecture Improvements

### Phase 1: Foundation (Week 1-2)

#### 1.1 Base Classes Pattern
**Current:** Entities don't extend base class  
**Recommended:** All entities extend `BaseEntity` for automatic timestamp tracking

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

**Benefits:**
- Automatic `createdAt` and `updatedAt` timestamps
- Consistent ID generation strategy (UUID)
- DRY principle - no repeated timestamp code
- Audit trail for all entities

#### 1.2 Service Interface Pattern
**Current:** Single service class  
**Recommended:** Service interface + ServiceImpl implementation

```java
public interface InterviewSessionService {
    UUID startSession(String name, String position, String difficulty);
    void appendTranscript(UUID sessionId, String text);
    void finalizeSession(UUID sessionId);
}

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {
    // Implementation
}
```

**Benefits:**
- Easier testing with mocked implementations
- Clear contracts between layers
- Interface-based dependency injection
- Future-proof for alternative implementations

#### 1.3 Exception Handling
**Current:** Generic RuntimeException  
**Recommended:** Custom exception hierarchy + GlobalExceptionHandler

```java
// Custom exceptions
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
        log.error("Session not found", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }
}
```

**Benefits:**
- Consistent error responses across all endpoints
- Security: no stack traces exposed to clients
- Centralized error logging
- Easier debugging with custom exception types
- HTTP status code standardization

---

### Phase 2: Real-Time Communication (Week 2-3)

#### 2.1 WebSocket/STOMP Configuration
**Missing:** Browser ↔ Spring Boot WebSocket

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/interview")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
```

#### 2.2 WebSocket Controller
**New Component:** Handle browser messages

```java
@Slf4j
@RequiredArgsConstructor
@Controller
public class InterviewWebSocketController {
    
    private final GeminiLiveClient geminiClient;
    private final SimpMessagingTemplate messagingTemplate;
    
    @MessageMapping("/interview/audio")
    public void handleAudioFromBrowser(@Payload byte[] pcmData, Principal principal) {
        log.debug("Received audio from browser: {} bytes", pcmData.length);
        geminiClient.sendAudio(pcmData);
    }
    
    @MessageMapping("/interview/start")
    public void startInterview(@Payload InterviewStartRequest request, Principal principal) {
        // Initialize Gemini connection
        // Send confirmation to browser
    }
}
```

#### 2.3 Gemini Integration Service
**New Component:** Orchestrate Gemini ↔ Browser communication

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class GeminiIntegrationService {
    
    private final GeminiLiveClient geminiClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final InterviewSessionService sessionService;
    
    public void initializeForSession(UUID sessionId, String apiKey) {
        geminiClient.setOnAudioReceived(audio -> {
            // Forward to browser via WebSocket
            messagingTemplate.convertAndSend("/topic/interview/" + sessionId + "/audio", audio);
        });
        
        geminiClient.setOnTextReceived(text -> {
            // Save transcript + forward to browser
            sessionService.appendTranscript(sessionId, text);
            messagingTemplate.convertAndSend("/topic/interview/" + sessionId + "/text", text);
        });
        
        geminiClient.setOnError(error -> {
            log.error("Gemini error for session {}: {}", sessionId, error);
            messagingTemplate.convertAndSend("/topic/interview/" + sessionId + "/error", error);
        });
        
        geminiClient.connect();
    }
}
```

---

### Phase 3: Domain Modeling (Week 3-4)

#### 3.1 Enhanced Entity Model

```java
// Question.java
@Entity
@Table(name = "questions")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class Question extends BaseEntity {
    @Column(nullable = false)
    private String position;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficulty;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;
    
    @Column(columnDefinition = "TEXT")
    private String expectedKeywords;
}

// InterviewFeedback.java
@Entity
@Table(name = "interview_feedback")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class InterviewFeedback extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;
    
    @Column(nullable = false)
    private Integer communicationScore;
    
    @Column(nullable = false)
    private Integer technicalScore;
    
    @Column(nullable = false)
    private Integer overallScore;
    
    @Column(columnDefinition = "TEXT")
    private String strengths;
    
    @Column(columnDefinition = "TEXT")
    private String improvements;
    
    @Column(columnDefinition = "TEXT")
    private String detailedAnalysis;
}
```

#### 3.2 Enums for Type Safety

```java
@Getter
@RequiredArgsConstructor
public enum DifficultyLevel {
    JUNIOR("Junior Level"),
    MID("Mid Level"),
    SENIOR("Senior Level"),
    EXPERT("Expert Level");
    
    private final String displayName;
}

@Getter
@RequiredArgsConstructor
public enum InterviewStatus {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");
    
    private final String displayName;
}
```

---

### Phase 4: Advanced Features (Week 4-6)

#### 4.1 AI-Powered Grading Service

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewGradingService {
    
    private final OpenAiClient openAiClient; // Or Gemini for grading
    
    public InterviewFeedback gradeInterview(InterviewSession session) {
        log.info("Grading interview session: {}", session.getId());
        
        String prompt = buildGradingPrompt(session);
        String aiResponse = openAiClient.analyze(prompt);
        
        return parseGradingResponse(aiResponse);
    }
    
    private String buildGradingPrompt(InterviewSession session) {
        return String.format("""
            Analyze this %s interview for %s position.
            
            Transcript:
            %s
            
            Provide scores (0-100) for:
            1. Communication skills
            2. Technical knowledge
            3. Problem-solving
            4. Overall impression
            
            Format response as JSON.
            """, 
            session.getDifficulty(),
            session.getJobPosition(),
            session.getTranscript()
        );
    }
}
```

#### 4.2 Question Bank System

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class QuestionBankService {
    
    private final QuestionRepository questionRepository;
    
    public List<Question> selectQuestionsForInterview(String position, DifficultyLevel difficulty) {
        // Algorithm to select balanced questions
        return questionRepository.findByPositionAndDifficulty(position, difficulty);
    }
    
    public Question generateDynamicQuestion(String topic, DifficultyLevel difficulty) {
        // Use AI to generate follow-up questions
        // Based on candidate's previous answers
    }
}
```

#### 4.3 Audio Processing Utilities

```java
@Slf4j
@Component
public class AudioProcessingService {
    
    public byte[] convertWebmToPcm(byte[] webmData) {
        // FFmpeg integration or Java sound API
        // Convert browser WebM to 16-bit PCM
    }
    
    public byte[] resample(byte[] pcmData, int fromRate, int toRate) {
        // Resample to Gemini's required rate (16kHz)
    }
    
    public boolean detectSilence(byte[] pcmData) {
        // Detect when candidate stops speaking
        // Trigger interruption handling
    }
}
```

---

### Phase 5: Security & Multi-Tenancy (Week 6-8)

#### 5.1 User Authentication
**PSCR Pattern:** Spring Security 6.x

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register").permitAll()
                .requestMatchers("/interview/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
            )
            .build();
    }
}
```

#### 5.2 User Entity

```java
@Entity
@Table(name = "users")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    private String fullName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role; // CANDIDATE, RECRUITER, ADMIN
    
    @Column(nullable = false)
    private Boolean enabled = true;
    
    @OneToMany(mappedBy = "user")
    private Set<InterviewSession> sessions;
}
```

#### 5.3 Session Ownership & Authorization

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewAuthorizationService {
    
    private final InterviewSessionRepository sessionRepository;
    
    public boolean canAccessSession(UUID sessionId, Authentication auth) {
        User user = (User) auth.getPrincipal();
        InterviewSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        
        // Check ownership or admin role
        return session.getUser().getId().equals(user.getId()) 
            || user.getRole() == UserRole.ADMIN;
    }
}
```

---

### Phase 6: Production Readiness (Week 8-10)

#### 6.1 Configuration Management

```java
@Slf4j
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {
    private String apiKey;
    private String modelName = "gemini-2.0-flash-exp";
    private List<String> modalities = List.of("AUDIO", "TEXT");
    private Integer maxTokens = 8192;
    
    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Gemini API key not configured!");
            throw new IllegalStateException("GEMINI_API_KEY environment variable required");
        }
    }
}
```

**application.properties:**
```properties
# Gemini Configuration
gemini.api-key=${GEMINI_API_KEY}
gemini.model-name=gemini-2.0-flash-exp
gemini.modalities=AUDIO,TEXT

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/interview_simulator
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# WebSocket
spring.websocket.allowed-origins=http://localhost:8080

# File Upload (for resume parsing, future feature)
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

#### 6.2 Health Checks & Monitoring

```java
@Component
public class GeminiHealthIndicator implements HealthIndicator {
    
    private final GeminiLiveClient geminiClient;
    
    @Override
    public Health health() {
        try {
            boolean connected = geminiClient.isConnected();
            return connected 
                ? Health.up().withDetail("gemini", "Connected").build()
                : Health.down().withDetail("gemini", "Disconnected").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

#### 6.3 Rate Limiting & Resource Management

```java
@Slf4j
@Component
public class SessionManager {
    
    private final Map<UUID, GeminiLiveClient> activeConnections = new ConcurrentHashMap<>();
    private static final int MAX_CONCURRENT_SESSIONS = 50;
    
    public synchronized void registerSession(UUID sessionId, GeminiLiveClient client) {
        if (activeConnections.size() >= MAX_CONCURRENT_SESSIONS) {
            throw new TooManyActiveSessionsException("Server at capacity");
        }
        activeConnections.put(sessionId, client);
        log.info("Active sessions: {}", activeConnections.size());
    }
    
    public synchronized void unregisterSession(UUID sessionId) {
        GeminiLiveClient client = activeConnections.remove(sessionId);
        if (client != null) {
            client.close();
        }
        log.info("Active sessions: {}", activeConnections.size());
    }
    
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupStaleConnections() {
        // Find and close sessions older than X minutes
    }
}
```

#### 6.4 Flyway Database Migrations

```sql
-- V1__initial_schema.sql
CREATE TABLE interview_sessions (
    id UUID PRIMARY KEY,
    candidate_name VARCHAR(255) NOT NULL,
    job_position VARCHAR(255) NOT NULL,
    difficulty VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    transcript TEXT,
    score INTEGER,
    feedback_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sessions_candidate ON interview_sessions(candidate_name);
CREATE INDEX idx_sessions_started_at ON interview_sessions(started_at);

-- V2__add_users.sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE interview_sessions ADD COLUMN user_id UUID REFERENCES users(id);
CREATE INDEX idx_sessions_user ON interview_sessions(user_id);
```

---

## Testing Strategy (PSCR-Inspired)

### Unit Tests

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InterviewSessionServiceTest {
    
    @Mock
    private InterviewSessionRepository repository;
    
    @InjectMocks
    private InterviewSessionServiceImpl service;
    
    
    @Test
    void testStartSession() {
        // Given
        String name = "John Doe";
        String position = "Java Developer";
        String difficulty = "SENIOR";
        
        InterviewSession session = InterviewSession.builder()
            .candidateName(name)
            .build();
        
        when(repository.save(any())).thenReturn(session);
        
        // When
        UUID result = service.startSession(name, position, difficulty);
        
        // Then
        assertNotNull(result);
        verify(repository).save(any(InterviewSession.class));
    }//testStartSession
    
}
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
    void testCreateSessionEndpoint() throws Exception {
        mockMvc.perform(post("/interview/start")
                .param("name", "Jane Doe")
                .param("position", "DevOps Engineer")
                .param("difficulty", "MID"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").exists());
        
        assertEquals(1, repository.count());
    }//testCreateSessionEndpoint
    
}
```

---

## Deployment Recommendations

### Docker Setup

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/interview-simulator-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: interview_simulator
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  app:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/interview_simulator
      SPRING_DATASOURCE_USERNAME: ${DB_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
    ports:
      - "8080:8080"
    depends_on:
      - postgres

volumes:
  postgres_data:
```

---

## Next Steps Prioritization

### Immediate (This Week)
1. ✅ Apply PSCR formatting conventions
2. 🔄 Create `BaseEntity` class
3. 🔄 Add service interfaces (InterviewSessionService)
4. 🔄 Implement custom exceptions

### Short Term (Next 2 Weeks)
5. 🔲 WebSocket/STOMP configuration for Browser ↔ Spring Boot
6. 🔲 WebSocket controller for audio streaming
7. 🔲 Integrate GeminiLiveClient with WebSocket messages
8. 🔲 Frontend JavaScript for microphone capture + WebSocket

### Medium Term (Next Month)
9. 🔲 User authentication (Spring Security)
10. 🔲 Question bank system
11. 🔲 AI-powered grading service
12. 🔲 Feedback entity + UI

### Long Term (Next Quarter)
13. 🔲 Multi-language support (i18n)
14. 🔲 Admin dashboard for recruiters
15. 🔲 Analytics & reporting
16. 🔲 Resume parsing + position matching
17. 🔲 Video recording (optional)
18. 🔲 Mock coding challenges integration

---

---

## Summary

This architecture is designed for **production-ready, enterprise-grade interview simulation**. The 3-tier WebSocket approach provides the perfect balance of real-time performance, security, and maintainability.

### Project Benefits

By following these conventions and patterns, you'll achieve:

- **Maintainability:** Consistent code structure across all components
- **Testability:** Clear separation of concerns with interface-based design
- **Scalability:** Proper resource management and connection pooling
- **Security:** Robust authentication, authorization, and secret management
- **Observability:** Comprehensive logging and health monitoring
- **Performance:** Efficient WebSocket communication with minimal latency

### Development Strategy

**Focus Areas:**
1. **Phase 1-2** (Foundation + WebSocket) - Critical infrastructure
2. **Phase 3-4** (Domain + Features) - Business value
3. **Phase 5-6** (Security + Production) - Enterprise readiness

The **Gemini Live API integration** is your competitive differentiator—invest time making it robust, handling edge cases (reconnection, rate limits, audio quality), and providing excellent UX during the conversation.

**Next step:** Review `CLAUDE.md` for detailed development guidelines and coding standards.

🚀 **Happy Building!**
