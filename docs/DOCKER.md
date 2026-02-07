# 🐳 Docker Deployment Guide

Deploy the AI Interview Simulator using Docker and Docker Compose.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Production Deployment](#production-deployment)
- [Docker Commands Reference](#docker-commands-reference)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Requirement | Version | Check Command |
|-------------|---------|---------------|
| **Docker** | 24+ | `docker --version` |
| **Docker Compose** | 2.20+ | `docker compose version` |

### Installing Docker

<details>
<summary><strong>Ubuntu/Debian</strong></summary>

```bash
# Install Docker
curl -fsSL https://get.docker.com | sh

# Add your user to docker group
sudo usermod -aG docker $USER

# Log out and back in, then verify
docker run hello-world
```
</details>

<details>
<summary><strong>macOS</strong></summary>

Download and install [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)
</details>

<details>
<summary><strong>Windows</strong></summary>

Download and install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)
</details>

---

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/dkirichev/interviewSimulator.git
cd interviewSimulator
```

### 2. Create Environment File

```bash
cat > .env << 'EOF'
# Database Configuration
DB_HOST=postgres
DB_PORT=5432
DB_NAME=interview_simulator
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password_here

# Application Mode (PROD = users provide their own API key)
# REVIEWER = hide API modal, use multi-key rotation (for competition judges)
APP_MODE=PROD

# Optional: Only needed in DEV mode
# GEMINI_API_KEY=AIza...

# Optional: Only needed in REVIEWER mode
# GEMINI_REVIEWER_KEYS=AIza...key1,AIza...key2,AIza...key3
# GEMINI_GRADING_MODELS=gemini-3-flash-preview,gemini-2.5-flash,gemini-2.5-flash-lite,gemma-3-12b-it
EOF
```

### 3. Start the Services

```bash
docker compose up -d
```

### 4. Access the Application

Open your browser at: **http://localhost:8080**

---

## Configuration

### Docker Compose File

The project includes a `docker-compose.yml`:

```yaml
services:
  # PostgreSQL Database
  postgres:
    image: postgres:16-alpine
    container_name: interview-db
    environment:
      POSTGRES_DB: ${DB_NAME:-interview_simulator}
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-secret}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres}"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Spring Boot Application
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: interview-app
    ports:
      - "8080:8080"
    environment:
      APP_MODE: ${APP_MODE:-PROD}
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${DB_NAME:-interview_simulator}
      DB_USERNAME: ${DB_USERNAME:-postgres}
      DB_PASSWORD: ${DB_PASSWORD:-secret}
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

volumes:
  postgres_data:
```

### Dockerfile

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Production Deployment

### Security Recommendations

1. **Use PROD mode** - Users provide their own API keys
2. **Strong database password** - Use a randomly generated password
3. **HTTPS** - Use a reverse proxy (nginx/Traefik) with SSL
4. **Firewall** - Only expose port 443, keep 5432 internal

### Example with Traefik (HTTPS)

```yaml
services:
  traefik:
    image: traefik:v3.0
    command:
      - "--api.insecure=false"
      - "--providers.docker=true"
      - "--entrypoints.web.address=:80"
      - "--entrypoints.websecure.address=:443"
      - "--certificatesresolvers.letsencrypt.acme.httpchallenge=true"
      - "--certificatesresolvers.letsencrypt.acme.email=your@email.com"
      - "--certificatesresolvers.letsencrypt.acme.storage=/letsencrypt/acme.json"
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./letsencrypt:/letsencrypt

  app:
    # ... existing app config ...
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.app.rule=Host(`interview.yourdomain.com`)"
      - "traefik.http.routers.app.entrypoints=websecure"
      - "traefik.http.routers.app.tls.certresolver=letsencrypt"
```

### Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `APP_MODE` | Yes | - | `DEV`, `PROD`, or `REVIEWER` |
| `DB_HOST` | Yes | - | Database hostname |
| `DB_PORT` | Yes | `5432` | Database port |
| `DB_NAME` | Yes | - | Database name |
| `DB_USERNAME` | Yes | - | Database user |
| `DB_PASSWORD` | Yes | - | Database password |
| `GEMINI_API_KEY` | DEV only | - | Backend API key (ignored in PROD/REVIEWER) |
| `GEMINI_REVIEWER_KEYS` | REVIEWER only | - | Comma-separated API keys for model rotation |
| `GEMINI_GRADING_MODELS` | No | `gemini-2.5-pro,...` | Grading model fallback chain |

---

## Docker Commands Reference

### Starting Services

```bash
# Start in background
docker compose up -d

# Start with build (after code changes)
docker compose up -d --build

# Start specific service
docker compose up -d postgres
```

### Viewing Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f app

# Last 100 lines
docker compose logs --tail=100 app
```

### Stopping Services

```bash
# Stop all services
docker compose down

# Stop and remove volumes (deletes database!)
docker compose down -v
```

### Maintenance

```bash
# View running containers
docker compose ps

# Restart a service
docker compose restart app

# Execute command in container
docker compose exec app sh

# View database
docker compose exec postgres psql -U postgres -d interview_simulator
```

### Building

```bash
# Rebuild image
docker compose build

# Rebuild without cache
docker compose build --no-cache

# Pull latest base images
docker compose pull
```

---

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker compose logs app

# Common issues:
# - Database not ready: check postgres health
# - Missing env vars: verify .env file
```

### Database Connection Issues

```
Could not create connection to database server
```

**Solution**: Ensure PostgreSQL is healthy:
```bash
docker compose ps  # Check status
docker compose logs postgres  # Check logs
```

### Port Already in Use

```
Bind for 0.0.0.0:8080 failed: port is already allocated
```

**Solution**: Change the port in `docker-compose.yml`:
```yaml
ports:
  - "8081:8080"  # Use port 8081 externally
```

### Out of Memory

```
java.lang.OutOfMemoryError: Java heap space
```

**Solution**: Add memory limits:
```yaml
app:
  environment:
    JAVA_OPTS: "-Xmx512m -Xms256m"
```

### Reset Everything

```bash
# Stop containers and remove all data
docker compose down -v

# Remove unused images
docker system prune -a
```

---

## Health Checks

### Check Application Health

```bash
curl http://localhost:8080/actuator/health
```

### Check Database Connection

```bash
docker compose exec postgres pg_isready -U postgres
```

---

## Backup and Restore

### Backup Database

```bash
docker compose exec postgres pg_dump -U postgres interview_simulator > backup.sql
```

### Restore Database

```bash
cat backup.sql | docker compose exec -T postgres psql -U postgres interview_simulator
```

---

[← Back to README](../README.md) | [Local Setup Guide →](SETUP.md)
