# 📋 Local Development Setup Guide

This guide will help you set up the AI Interview Simulator for local development.

## Table of Contents

- [Prerequisites](#prerequisites)
- [1. Database Setup](#1-database-setup)
- [2. Get a Gemini API Key](#2-get-a-gemini-api-key)
- [3. Clone and Configure](#3-clone-and-configure)
- [4. Run the Application](#4-run-the-application)
- [5. Access the Application](#5-access-the-application)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before you begin, ensure you have the following installed:

| Requirement | Version | Check Command |
|-------------|---------|---------------|
| **Java JDK** | 21+ | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **PostgreSQL** | 14+ | `psql --version` |
| **Git** | Any | `git --version` |

### Installing Prerequisites

<details>
<summary><strong>Ubuntu/Debian</strong></summary>

```bash
# Java 21
sudo apt install openjdk-21-jdk

# PostgreSQL
sudo apt install postgresql postgresql-contrib

# Maven
sudo apt install maven
```
</details>

<details>
<summary><strong>macOS (Homebrew)</strong></summary>

```bash
# Java 21
brew install openjdk@21

# PostgreSQL
brew install postgresql@16
brew services start postgresql@16

# Maven
brew install maven
```
</details>

<details>
<summary><strong>Windows</strong></summary>

1. **Java 21**: Download from [Adoptium](https://adoptium.net/)
2. **PostgreSQL**: Download from [postgresql.org](https://www.postgresql.org/download/windows/)
3. **Maven**: Download from [maven.apache.org](https://maven.apache.org/download.cgi)
</details>

---

## 1. Database Setup

### Create the Database

```bash
# Connect to PostgreSQL
sudo -u postgres psql

# Create database and user
CREATE DATABASE interview_simulator;
CREATE USER interview_user WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE interview_simulator TO interview_user;

# Exit
\q
```

### Verify Connection

```bash
psql -h localhost -U interview_user -d interview_simulator
# Enter your password when prompted
```

> 💡 **Note**: The application uses Flyway for migrations. Tables will be created automatically on first run.

---

## 2. Get a Gemini API Key

You need a **free** Gemini API key from Google:

1. Go to [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Sign in with your Google account
3. Accept the Terms of Service (first time only)
4. Click **"Create API Key"**
5. Select **"Create project"** from the dropdown
6. Click **"Create project"** (rename if you want)
7. Click **"Create key"**
8. Copy the key (starts with `AIza...`)

> ⚠️ **Keep your API key secret!** Never commit it to version control.

---

## 3. Clone and Configure

### Clone the Repository

```bash
git clone https://github.com/dkirichev/interviewSimulator.git
cd interviewSimulator
```

### Configure Environment Variables

Create a `.env` file (it's gitignored):

```bash
cat > .env << 'EOF'
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=interview_simulator
DB_USERNAME=interview_user
DB_PASSWORD=your_secure_password

# Gemini API Configuration
GEMINI_API_KEY=AIza...your_key_here

# Application Mode
# DEV = Backend provides API key (for development)
# PROD = Users must provide their own API key
APP_MODE=DEV
EOF
```

### Load Environment Variables

```bash
# For the current session
export $(cat .env | xargs)

# Or add to your shell profile (~/.bashrc, ~/.zshrc)
set -a; source .env; set +a
```

---

## 4. Run the Application

### Option A: Maven (Development)

```bash
# Build and run with hot reload
./mvnw spring-boot:run
```

### Option B: Build JAR

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Run the JAR
java -jar target/interviewSimulator-0.0.1-SNAPSHOT.jar
```

### Verify Startup

You should see output like:

```
Started InterviewSimulatorApplication in 5.123 seconds
```

---

## 5. Access the Application

Open your browser and navigate to:

```
http://localhost:8080
```

You'll be redirected to the setup wizard at `/setup/step1`.

---

## Development Commands

| Command | Description |
|---------|-------------|
| `./mvnw spring-boot:run` | Run with hot reload |
| `./mvnw clean compile` | Compile the project |
| `./mvnw test` | Run all tests |
| `./mvnw test -Dtest=MyTest` | Run specific test |
| `./mvnw flyway:migrate` | Run database migrations |
| `./mvnw clean package` | Build production JAR |

---

## IDE Setup

### IntelliJ IDEA (Recommended)

1. Open the project folder
2. IntelliJ will detect it as a Maven project
3. Enable annotation processing:
   - Settings → Build → Compiler → Annotation Processors
   - Check "Enable annotation processing"
4. Run `InterviewSimulatorApplication.java`

### VS Code

1. Install extensions:
   - Extension Pack for Java
   - Spring Boot Extension Pack
2. Open the project folder
3. VS Code will detect and configure the project

---

## Troubleshooting

### Database Connection Errors

```
Could not create connection to database server
```

**Solution**: Ensure PostgreSQL is running:
```bash
# Linux
sudo systemctl start postgresql

# macOS
brew services start postgresql@16
```

### Flyway Migration Errors

```
Schema "public" is already initialized
```

**Solution**: Clean the database:
```bash
./mvnw flyway:clean flyway:migrate
```

### API Key Errors

```
GEMINI_API_KEY environment variable required in DEV mode
```

**Solution**: Ensure your `.env` file is loaded:
```bash
echo $GEMINI_API_KEY  # Should print your key
```

### Port Already in Use

```
Port 8080 is already in use
```

**Solution**: Use a different port:
```bash
./mvnw spring-boot:run -Dserver.port=8081
```

### Microphone Not Working

Ensure your browser has microphone permissions:
1. Click the lock icon in the address bar
2. Allow microphone access
3. Refresh the page

---

## Next Steps

- 📖 Read the [Architecture Guide](ARCHITECTURE.md) to understand the codebase
- 🔌 Explore the [API Reference](API.md) for endpoint details
- 🤝 Check out [Contributing Guidelines](CONTRIBUTING.md) to help improve the project

---

[← Back to README](../README.md)
