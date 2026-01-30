# Frontend Structure

## Template Hierarchy

```
templates/
├── layouts/
│   ├── main.html              # Base layout wrapper
│   └── fragments/
│       ├── head.html          # <head> with CDN links
│       ├── styles.html        # Custom CSS
│       └── bodyBottom.html    # JS imports (SockJS, STOMP, app scripts)
└── pages/
    ├── index.html             # Aggregates all views
    ├── setup.html             # Interview configuration form
    ├── interview.html         # Live session UI
    └── report.html            # Post-interview feedback
```

## JavaScript Modules

| File | Purpose |
|------|---------|
| `audio-processor.js` | WebSocket connection, mic capture, audio playback |
| `navigation.js` | View switching, report rendering |
| `interview.js` | Session state, UI updates |

## Controller Pattern

```java
@GetMapping("/")
public String index(Model model) {
    model.addAttribute("content", "pages/index");
    return "layouts/main";
}
```

## Adding New Pages

1. Create `templates/pages/newpage.html` with `th:fragment="newpageView"`
2. Add controller method returning `"layouts/main"` with `content` attribute
3. (Optional) Add JS in `static/js/` and reference in `bodyBottom.html`

## Tech Stack

- **Tailwind CSS** (CDN) - Styling
- **FontAwesome 6.4** - Icons
- **SockJS + STOMP** - WebSocket
- **Web Audio API** - Mic/speaker
