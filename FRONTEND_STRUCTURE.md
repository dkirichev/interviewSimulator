# Frontend Structure

## Template Hierarchy

```
templates/
├── layouts/
│   ├── main.html                 # Base layout wrapper with language switcher
│   └── fragments/
│       ├── apikey-modal.html     # PROD mode API key entry + rate limit modal
│       ├── head.html             # <head> with CDN links (Tailwind, FontAwesome)
│       ├── styles.html           # Custom CSS animations and components
│       └── bodyBottom.html       # JS imports (SockJS, STOMP, app scripts)
└── pages/
    ├── setup/                    # Multi-page setup wizard
    │   ├── step1.html            # Profile (name)
    │   ├── step2.html            # Details (position, difficulty, CV)
    │   ├── step3.html            # Voice & Language
    │   └── fragments/
    │       └── progress.html     # Reusable progress indicator
    ├── interview-standalone.html # Live interview (reads from session)
    ├── report-standalone.html    # Server-rendered report page
    └── report-error.html         # Report not found error page
```

## Architecture: Multi-Page Flow

```
/setup/step1 → /setup/step2 → /setup/step3 → /interview → /report/{id}
```
- Form data stored in HTTP session (`@SessionAttributes`)
- Server-side validation with Spring Validation
- Server-rendered report from database
- Minimal JavaScript (only audio/WebSocket)
- Session cleared after interview starts (one-time use)

## Controllers

| Controller | Path | Purpose |
|------------|------|---------|
| `PageController` | `/`, `/interview` | Root redirect, interview page |
| `SetupController` | `/setup/*` | Multi-step setup wizard |
| `ReportController` | `/report/{id}` | Server-rendered report |
| `ApiKeyController` | `/api/*` | API key validation (REST) |
| `CvController` | `/api/cv/*` | CV upload processing (REST) |
| `VoiceController` | `/api/voices/*` | Voice preview audio (REST) |

## JavaScript Modules

| File | Lines | Purpose |
|------|-------|---------|
| `audio-processor.js` | ~588 | WebSocket, mic capture, audio playback |
| `interview.js` | ~308 | Mic/camera toggle, timer, avatar states |
| `apikey.js` | ~363 | API key modal, localStorage |
| `language-switcher.js` | ~150 | Language switching (simplified) |

**Total: 1,409 lines** (down from 2,388 - **41% reduction**)

All remaining JavaScript is essential for browser APIs (WebSocket, Web Audio, getUserMedia).

## Multi-Step Setup Wizard

The setup form is split into 3 separate server-rendered pages:

### Step 1: Profile (`/setup/step1`)
- Candidate name input (required)
- Server-side validation

### Step 2: Details (`/setup/step2`)
- Target position dropdown (with custom option, defaults to placeholder)
- Difficulty level radio buttons (Chill/Standard/Stress)
- CV upload (optional, PDF/DOCX)
- Client-side preview with auto-submit
- Multipart form with server-side CV processing

### Step 3: Voice (`/setup/step3`)
- Interview language selection (English/Bulgarian)
- Interviewer voice selection with preview playback
- Audio visualizer for voice previews (animated during preview)
- Submits to start interview

### Session Data Flow
```java
@SessionAttributes("setupForm")
public class SetupController {
    // Form data persists in InterviewSetupDTO across steps
    // Cleared after interview starts via SessionStatus.setComplete()
}
}
```

## Interview Page

The interview page (`/interview`) reads setup data from the HTTP session:

```html
<script th:inline="javascript">
    window.interviewSession = {
        candidateName: /*[[${setupForm.candidateName}]]*/ 'Candidate',
        position: /*[[${setupForm.effectivePosition}]]*/ 'Developer',
        // ... other fields
    };
</script>
```

This data is used by `audio-processor.js` to start the WebSocket session.

## Report Page

After interview ends, user is redirected to `/report/{sessionId}`:
- Server fetches feedback from database
- Fully server-rendered (no JS needed)
- Scores, verdict, strengths/improvements all rendered by Thymeleaf

## Key UI Components

### API Key Modal (PROD Mode)
- Shown when `app.mode=PROD` and no stored key
- Step-by-step guide with video tutorial
- Key validation before allowing access
- Rate limit error handling with separate modal

### Voice Preview
- Click to play sample audio for each voice
- Audio visualizer animation during playback
- Automatic stop when switching languages

### Interview Controls
- Mic toggle (starts muted, auto-enables after AI speaks)
- Camera toggle (optional, local only)
- End call button
- Call duration timer
- AI avatar with states (idle/thinking/talking)

### Connection Flow
1. **Page loads** → Connection overlay with spinner ("Establishing Secure Websocket...")
2. **Mic permission requested** → Waits for user approval
3. **WebSocket connects** → Updates message to "Waiting for interviewer..."
4. **AI starts speaking** → Overlay hides, interview begins
5. **AI finishes** → Mic auto-enables, user's turn

The overlay stays visible until the AI actually starts speaking, preventing blank screen wait times.

### Report Dashboard
- Circular score gauge with animation
- Verdict badge (STRONG_HIRE/HIRE/MAYBE/NO_HIRE)
- Score breakdown bars (Communication/Technical/Confidence)
- Strengths and improvements lists
- Detailed analysis text

## Internationalization (i18n)

Templates use Thymeleaf message expressions:

```html
<span th:text="#{setup.title}" />
<input th:placeholder="#{setup.step1.candidateName.placeholder}" />
```

Message keys follow the pattern: `section.subsection.element.attribute`

### Language Switching
- Dropdown in top-right corner (hidden during interview)
- Simple page reload with `?lang=` parameter
- Form data preserved in HTTP session (no JS needed)
- Stores preference in localStorage and cookie

## Tech Stack

- **Tailwind CSS** (CDN) - Utility-first styling
- **FontAwesome 6.4** (CDN) - Icons
- **SockJS + STOMP.js** - WebSocket communication
- **Web Audio API** - Microphone capture and audio playback
- **getUserMedia API** - Camera access (optional)
