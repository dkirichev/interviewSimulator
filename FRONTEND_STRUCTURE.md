# Interview Simulator - Frontend Structure

## Overview
This project follows a template structure pattern, separating layout, fragments, and page content for better maintainability and scalability.

## Technology Stack
- **Backend**: Spring Boot 4.0.0 with Thymeleaf
- **Styling**: Tailwind CSS (CDN)
- **Icons**: FontAwesome 6.4.0
- **Fonts**: Google Fonts (Inter)
- **JavaScript**: Vanilla JS (minimal usage)

## Template Structure

```
src/main/resources/templates/
├── layouts/
│   ├── main.html                      # Main layout wrapper
│   └── fragments/
│       ├── head.html                  # <head> section with CDN links
│       ├── styles.html                # Custom CSS animations
│       └── bodyBottom.html            # JavaScript imports
└── pages/
    ├── index.html                     # Content aggregator (includes all 3 views)
    ├── setup.html                     # Setup/configuration page
    ├── interview.html                 # Live interview page
    └── report.html                    # Post-interview report
```

## JavaScript Structure

```
src/main/resources/static/js/
├── audio-processor.js                 # WebSocket & audio handling
├── navigation.js                      # View switching logic
└── interview.js                       # Interview session logic
```

## How It Works

### 1. Controller Pattern
```java
@GetMapping("/")
public String index(Model model) {
    model.addAttribute("content", "pages/index");
    model.addAttribute("pageTitle", "Home");
    return "layouts/main";
}
```

### 2. Layout Hierarchy
```
layouts/main.html
  ├─> layouts/fragments/head.html
  ├─> ${content} (dynamic - e.g., pages/index.html)
  └─> layouts/fragments/bodyBottom.html
```

### 3. Page Fragments
Each page uses Thymeleaf fragments:
```html
<section th:fragment="setupView" id="setup-view">
    <!-- Page content -->
</section>
```

## Adding New Pages

### Step 1: Create Page Template
Create `src/main/resources/templates/pages/newpage.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">

<section th:fragment="newpageView" id="newpage-view" class="...">
    <!-- Your content here -->
</section>
</html>
```

### Step 2: Add Controller Method
```java
@GetMapping("/newpage")
public String newPage(Model model) {
    model.addAttribute("content", "pages/newpage");
    model.addAttribute("pageTitle", "New Page");
    return "layouts/main";
}
```

### Step 3: (Optional) Add JavaScript
Create `src/main/resources/static/js/newpage.js` and reference in `layouts/fragments/bodyBottom.html`.

## Key Features

### ✅ Minimal JavaScript
- No frameworks (React, Vue, Angular)
- Pure vanilla JS for essential interactivity
- Separated into logical modules

### ✅ Tailwind CSS
- Utility-first CSS framework
- Loaded via CDN (no build step)
- Custom animations in `layouts/fragments/styles.html`

### ✅ Thymeleaf Server-Side Rendering
- SEO-friendly
- Fast initial page load
- No hydration issues

### ✅ Aligned Structure
- Matches your main project structure
- Easy to maintain consistency
- Familiar patterns

## Current Pages

### 1. Setup Page (`pages/setup.html`)
- Candidate name input
- Job position selection
- Difficulty level (Chill/Standard/Stress)

### 2. Interview Page (`pages/interview.html`)
- Live session header with status badge
- Avatar display (placeholder for video)
- Audio visualizer
- Mic toggle and end call controls

### 3. Report Page (`pages/report.html`)
- Overall performance score
- Speech analysis feedback
- Technical accuracy metrics
- Full transcript view

## Development Notes

### Mock Mode
The application currently runs in **mock mode** for frontend development:
- WebSocket connection is simulated in `audio-processor.js`
- AI responses are mocked in `interview.js`
- To enable real backend: Uncomment WebSocket code in `connectToBackend()`

### Future Enhancements
- [ ] Real WebSocket integration for live AI communication
- [ ] Video avatar support
- [ ] Session persistence (database)
- [ ] PDF report export
- [ ] Admin dashboard

## File Locations

| Type | Path |
|------|------|
| Templates | `src/main/resources/templates/` |
| Static Assets | `src/main/resources/static/` |
| Controllers | `src/main/java/net/k2ai/interviewSimulator/test/` |
| Database Migrations | `src/main/resources/db/migration/` |

## Dependencies

See `pom.xml` for full dependency list. Key dependencies:
- Spring Boot Starter Web MVC
- Spring Boot Starter Thymeleaf
- Spring Boot Starter Data JPA
- Flyway (PostgreSQL)
- Lombok
- Spring Boot DevTools

---

**Old monolithic file backed up at**: `templates/index.html.backup`
