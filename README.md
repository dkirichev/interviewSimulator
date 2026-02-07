<p align="center">
  <img src="/docs/assets/logo.png" alt="Interview Simulator Logo" width="120" />
</p>

<h1 align="center">🎙️ AI Interview Simulator</h1>

<p align="center">
  <strong>Practice job interviews with a real-time AI interviewer powered by Google Gemini</strong>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-documentation">Documentation</a> •
  <a href="#-tech-stack">Tech Stack</a> •
  <a href="#-contributing">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen?style=flat-square&logo=spring" alt="Spring Boot 4.0.0" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Gemini-2.5%20Flash-red?style=flat-square&logo=google" alt="Gemini AI" />
  <img src="https://img.shields.io/badge/License-GPL%20v3-blue?style=flat-square" alt="License: GPL v3" />
</p>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🎤 Real-Time Voice Conversation
Have natural, bidirectional voice conversations with an AI interviewer using Google's Gemini Live API. No typing required - just speak!

### 📄 CV/Resume Integration
Upload your PDF or DOCX resume. The AI uses it to ask personalized questions about your experience and projects.

### 🌍 Multi-Language Support
Interview in **English** or **Bulgarian**. The AI adapts its questions and speech to your chosen language.

</td>
<td width="50%">

### 🎭 4 Interviewer Voices
Choose from 4 distinct AI voices:
- 👨 **George/Георги** (Algieba)
- 👩 **Victoria/Виктория** (Kore)  
- 👨 **Max/Макс** (Fenrir)
- 👩 **Diana/Диана** (Despina)

### 📊 AI-Powered Grading
Get detailed performance feedback after each interview:
- Overall, Communication, Technical, & Confidence scores
- Strengths & areas for improvement
- Verdict: STRONG HIRE / HIRE / MAYBE / NO HIRE

</td>
</tr>
</table>

### 🎯 Difficulty Levels

| Level | Style | Best For |
|-------|-------|----------|
| **😌 Chill** | Relaxed, CV-focused conversation | Beginners, confidence building |
| **⚖️ Standard** | Balanced technical + soft skills | General interview prep |
| **🔥 Stress** | High-pressure, deep technical | Senior roles, tough companies |

### 💼 Position-Specific Questions

The AI tailors questions to your target role:
- **Java/Backend Developer** → OOP, Spring Boot, databases, API design
- **Frontend Developer** → HTML/CSS/JS, React/Vue/Angular, UX
- **QA Engineer** → Testing methodologies, automation, SDLC
- **DevOps Engineer** → CI/CD, cloud, containerization
- **Project/Product Manager** → Leadership, planning, stakeholders

---

## 🚀 Quick Start

### Option 1: Docker (Recommended)

```bash
# Clone the repository
git clone https://github.com/dkirichev/interviewSimulator.git
cd interviewSimulator

# Start with Docker Compose
docker-compose up -d

# Open in browser
open http://localhost:8080
```

👉 See [Docker Deployment Guide](docs/DOCKER.md) for detailed instructions.

### Option 2: Local Development

**Prerequisites:**
- Java 21+
- PostgreSQL 14+
- Maven 3.9+
- Gemini API Key (free from [Google AI Studio](https://aistudio.google.com/app/apikey))

```bash
# Clone and navigate
git clone https://github.com/dkirichev/interviewSimulator.git
cd interviewSimulator

# Set up environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=interview_simulator
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export GEMINI_API_KEY=your_api_key
export APP_MODE=DEV

# Build and run
./mvnw spring-boot:run
```

👉 See [Local Setup Guide](docs/SETUP.md) for detailed instructions.

---

## 📖 Documentation

| Document | Description |
|----------|-------------|
| [📋 Local Setup Guide](docs/SETUP.md) | Complete local development setup |
| [🐳 Docker Guide](docs/DOCKER.md) | Production deployment with Docker |
| [🏗️ Architecture](docs/ARCHITECTURE.md) | System design and data flow |
| [🔌 API Reference](docs/API.md) | REST and WebSocket endpoints |
| [🤝 Contributing](docs/CONTRIBUTING.md) | How to contribute to the project |

---

## 🛠️ Tech Stack

<table>
<tr>
<td align="center" width="120">
<img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="48" height="48" alt="Spring" />
<br><strong>Spring Boot 4</strong>
</td>
<td align="center" width="120">
<img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="48" height="48" alt="Java" />
<br><strong>Java 21</strong>
</td>
<td align="center" width="120">
<img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="48" height="48" alt="PostgreSQL" />
<br><strong>PostgreSQL</strong>
</td>
<td align="center" width="120">
<img src="https://www.vectorlogo.zone/logos/google/google-icon.svg" width="48" height="48" alt="Gemini" />
<br><strong>Gemini AI</strong>
</td>
<td align="center" width="120">
<img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/thymeleaf/thymeleaf-original.svg" width="48" height="48" alt="Thymeleaf" />
<br><strong>Thymeleaf</strong>
</td>
<td align="center" width="120">
<img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/docker/docker-original.svg" width="48" height="48" alt="Docker" />
<br><strong>Docker</strong>
</td>
</tr>
</table>

**Backend:**
- Spring Boot 4.0.0 with WebSocket/STOMP
- Spring Security
- Flyway database migrations
- OkHttp for Gemini WebSocket client
- Apache PDFBox & POI for CV parsing
- Thymeleaf for server-side templating

**Frontend:**
- Vanilla JavaScript (minimal, ~1,500 lines)
- Thymeleaf templates with i18n support
- Tailwind CSS (via CDN)
- Web Audio API for audio capture/playback

**AI:**
- Gemini 2.5 Flash (real-time audio conversations)
- Gemini 3 Flash (interview grading)

---

## 🔧 Application Modes

| Mode | API Key | Use Case |
|------|---------|----------|
| **DEV** | Backend provides key | Local development, testing |
| **PROD** | User provides own key | Production deployment |
| **REVIEWER** | Multi-key rotation (server) | Competition judges, demos |

In **PROD mode**, users are prompted to enter their free Gemini API key. This keeps hosting costs at zero while giving each user their own rate limits.

In **REVIEWER mode**, the API key modal is hidden and the server uses multiple pre-configured keys with automatic model fallback rotation. This is designed for competition judges who shouldn't have to set up API keys.

---

## 🖥️ Screenshots

<p align="center">
  <em>Coming soon - screenshots of the setup wizard, interview screen, and report page</em>
</p>

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](docs/CONTRIBUTING.md) for details on:

- 🐛 Reporting bugs
- 💡 Suggesting features
- 🔧 Submitting pull requests
- 📝 Code style guidelines

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

This means you can:
- ✅ Use the software for any purpose
- ✅ Change the software to suit your needs
- ✅ Share the software with anyone
- ✅ Share the changes you make

As long as you:
- 📋 Include the original license
- 📋 State significant changes made
- 📋 Make source code available when distributing

---

## 🙏 Acknowledgements

- [Google Gemini](https://ai.google.dev/) for the amazing AI models
- [Spring Boot](https://spring.io/projects/spring-boot) for the robust framework
- [Tailwind CSS](https://tailwindcss.com/) for beautiful styling

---

<p align="center">
  <strong>Made with ❤️ for interview prep enthusiasts</strong>
</p>

<p align="center">
  <a href="https://github.com/dkirichev/interviewSimulator/stargazers">⭐ Star this repo</a> •
  <a href="https://github.com/dkirichev/interviewSimulator/issues">🐛 Report Bug</a> •
  <a href="https://github.com/dkirichev/interviewSimulator/issues">💡 Request Feature</a>
</p>
