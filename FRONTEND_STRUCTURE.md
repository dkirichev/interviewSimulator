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
    ├── index.html                # Aggregates all views (setup, interview, report)
    ├── setup.html                # 3-step interview configuration wizard
    ├── interview.html            # Live session UI with avatar and controls
    └── report.html               # Post-interview feedback dashboard
```

## JavaScript Modules

| File | Purpose |
|------|---------|
| `audio-processor.js` | WebSocket STOMP connection, microphone capture, gapless audio playback |
| `navigation.js` | View switching, multi-step form wizard, CV upload, voice preview |
| `interview.js` | Interview session state, mic/camera toggle, call timer, avatar states |
| `apikey.js` | PROD mode API key modal, localStorage management, validation |
| `language-switcher.js` | UI language switching, form data preservation across reloads |

## Multi-Step Setup Wizard

The setup form is a 3-step wizard with progress indicators:

### Step 1: Profile
- Candidate name input (required)

### Step 2: Details
- Target position dropdown (with custom option)
- Difficulty level radio buttons (Chill/Standard/Stress)
- CV upload (optional, PDF/DOCX)

### Step 3: Voice
- Interview language selection (English/Bulgarian)
- Interviewer voice selection with preview playback
- Audio visualizer for voice previews

## Controller Pattern

```java
@GetMapping("/")
public String index(Model model) {
    model.addAttribute("content", "pages/index");
    return "layouts/main";
}
```

## View Switching (SPA-like)

The application uses CSS classes to toggle between views:

```javascript
function switchView(viewName) {
    // Hide all views
    Object.values(views).forEach(el => el.classList.remove('active'));
    // Show target view
    views[viewName].classList.add('active');
}
```

Views: `setup` → `interview` → `report`

## Key UI Components

### API Key Modal (PROD Mode)
- Shown when `app.mode=PROD` and no stored key
- Step-by-step guide to get Gemini API key
- Key validation before allowing access
- Rate limit error handling with separate modal

### Voice Preview
- Click to play sample audio for each voice
- Audio visualizer animation during playback
- Automatic stop when switching languages

### Interview Controls
- Mic toggle (on/off with visual indicator)
- Camera toggle (optional, local only)
- End call button
- Call duration timer
- AI avatar with states (idle/thinking/talking)

### Report Dashboard
- Circular score gauge with animation
- Verdict badge (STRONG_HIRE/HIRE/MAYBE/NO_HIRE)
- Score breakdown bars (Communication/Technical/Confidence)
- Strengths and improvements lists
- Detailed analysis text

## Adding New Pages

1. Create `templates/pages/newpage.html` with `th:fragment="newpageView"`
2. Add view section to `pages/index.html`
3. Add controller method returning `"layouts/main"` with `content` attribute
4. Add view reference in `navigation.js` views object
5. (Optional) Add JS in `static/js/` and reference in `bodyBottom.html`

## Internationalization (i18n)

Templates use Thymeleaf message expressions:

```html
<span th:text="#{setup.title}" />
<input th:placeholder="#{setup.step1.candidateName.placeholder}" />
```

Message keys follow the pattern: `section.subsection.element.attribute`

### Language Switching
- Dropdown in top-right corner (hidden during interview/report)
- Preserves current form step and data across language switch
- Stores preference in localStorage and cookie

## Tech Stack

- **Tailwind CSS** (CDN) - Utility-first styling
- **FontAwesome 6.4** (CDN) - Icons
- **SockJS + STOMP.js** - WebSocket communication
- **Web Audio API** - Microphone capture and audio playback
- **getUserMedia API** - Camera access (optional)
