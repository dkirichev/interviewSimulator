# 🤝 Contributing Guide

Thank you for your interest in contributing to the AI Interview Simulator! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Code Style Guidelines](#code-style-guidelines)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Pull Request Process](#pull-request-process)
- [Testing Guidelines](#testing-guidelines)

---

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to:

- **Be respectful** - Treat everyone with respect and consideration
- **Be constructive** - Provide helpful feedback, not criticism
- **Be inclusive** - Welcome newcomers and help them learn
- **Be professional** - Keep discussions focused and productive

---

## How Can I Contribute?

### 🐛 Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates.

**To report a bug:**

1. Go to [Issues](https://github.com/dkirichev/interviewSimulator/issues)
2. Click "New Issue"
3. Use this template:

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '...'
3. See error

**Expected behavior**
What you expected to happen.

**Screenshots**
If applicable, add screenshots.

**Environment:**
- OS: [e.g., Windows 11, macOS 14, Ubuntu 22.04]
- Browser: [e.g., Chrome 120, Firefox 121]
- Java Version: [e.g., 21.0.1]
```

### 💡 Suggesting Features

We welcome feature suggestions! Please:

1. Check if the feature already exists or is planned
2. Create an issue with the "Feature Request" label
3. Describe:
   - **The problem** you're trying to solve
   - **Your proposed solution**
   - **Alternatives** you've considered

### 🔧 Submitting Code

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes following our [code style](#code-style-guidelines)
4. Write or update tests as needed
5. Commit with a [good message](#commit-message-guidelines)
6. Push and create a Pull Request

---

## Development Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+
- Git

### Quick Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/interviewSimulator.git
cd interviewSimulator

# Add upstream remote
git remote add upstream https://github.com/dkirichev/interviewSimulator.git

# Create .env file
cp .env.example .env
# Edit .env with your configuration

# Load environment variables
source .env

# Run the application
./mvnw spring-boot:run
```

### IDE Setup

**IntelliJ IDEA (Recommended):**
1. Open the project folder
2. Enable annotation processing: Settings → Build → Compiler → Annotation Processors
3. Install Lombok plugin if prompted

**VS Code:**
1. Install "Extension Pack for Java"
2. Install "Spring Boot Extension Pack"
3. Open the project folder

---

## Code Style Guidelines

We maintain strict code formatting standards. **These are non-negotiable.**

### Java Formatting

| Rule | Example |
|------|---------|
| **File ends with empty line** | All files must end with a newline |
| **Two empty lines between methods** | Includes before first method |
| **One empty line between fields** | Each field declaration separated |
| **Closing brace comments** | `}//methodName` and `}//ClassName` |
| **4-space indentation** | No tabs |
| **Same-line braces** | `if (x) {` not `if (x)\n{` |

### Example

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class MyService {

    private final MyRepository repository;

    private final OtherService otherService;


    public void doWork() {
        log.info("Working");
    }//doWork


    public void doOtherWork() {
        log.info("Other work");
    }//doOtherWork

}//MyService
```

### Required Annotations

| Annotation | Use Case |
|------------|----------|
| `@Slf4j` | All services/controllers (auto-injects `log`) |
| `@RequiredArgsConstructor` | Constructor injection for `final` fields |
| `@Data` | Entities (getters/setters/equals/hashCode) |
| `@Builder` | Fluent object construction |

### Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Entity | CamelCase | `InterviewSession` |
| Table | snake_case | `interview_sessions` |
| Service | `{Name}Service` | `InterviewService` |
| Repository | `{Entity}Repository` | `InterviewSessionRepository` |
| Controller | `{Name}Controller` | `SetupController` |
| Migration | `V{n}__{desc}.sql` | `V1__initial_schema.sql` |

### Logging Standards

**Log these:**
- External API calls
- WebSocket events
- Errors and exceptions
- State changes

**Don't log:**
- Simple CRUD operations
- Getters/setters
- Every method entry/exit

```java
// ❌ Bad
log.info("Started");

// ✅ Good
log.info("Started session {} for {}", sessionId, candidateName);
```

---

## Commit Message Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/).

### Format

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation changes |
| `style` | Code style (formatting, no logic change) |
| `refactor` | Code refactoring |
| `test` | Adding or updating tests |
| `chore` | Maintenance tasks |

### Examples

```bash
feat(interview): add support for German language

fix(audio): resolve audio playback glitch on Safari

docs(readme): update Docker setup instructions

refactor(grading): simplify score calculation logic

test(cv): add tests for DOCX processing
```

---

## Pull Request Process

### Before Submitting

1. **Update from upstream:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests:**
   ```bash
   ./mvnw test
   ```

3. **Check code style:**
   - Ensure all files end with newline
   - Verify closing brace comments
   - Check proper spacing between methods

4. **Update documentation** if needed

### PR Template

When creating a PR, include:

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## How Has This Been Tested?
Describe the tests you ran.

## Checklist
- [ ] My code follows the style guidelines
- [ ] I have added tests for new functionality
- [ ] I have updated documentation
- [ ] All tests pass locally
```

### Review Process

1. Maintainers will review your PR
2. Address any requested changes
3. Once approved, your PR will be merged

---

## Testing Guidelines

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=GradingServiceTest

# Run with coverage
./mvnw test jacoco:report
```

### Writing Tests

- Use JUnit 5
- Test class names: `{ClassName}Test`
- Test method names: describe the behavior being tested
- Use Testcontainers for database tests

### Example

```java
@SpringBootTest
class GradingServiceTest {

    @Autowired
    private GradingService gradingService;


    @Test
    void gradeInterview_shouldReturnValidScores() {
        // Arrange
        UUID sessionId = createTestSession();
        
        // Act
        InterviewFeedback feedback = gradingService.gradeInterview(sessionId);
        
        // Assert
        assertThat(feedback.getOverallScore()).isBetween(0, 100);
    }//gradeInterview_shouldReturnValidScores

}//GradingServiceTest
```

---

## Internationalization (i18n)

When adding UI text:

1. Add key to `messages.properties` (English)
2. Add Bulgarian translation to `messages_bg.properties`
3. Use `#{key.name}` in Thymeleaf templates

### Naming Convention

```properties
# Format: section.subsection.element.attribute
setup.step1.candidateName=Candidate Name
setup.step1.candidateName.placeholder=e.g., John Doe
```

---

## Questions?

If you have questions:

1. Check existing [Issues](https://github.com/dkirichev/interviewSimulator/issues)
2. Read the [Documentation](../README.md)
3. Create a new issue with the "Question" label

---

Thank you for contributing! 🎉

---

[← Back to README](../README.md)
