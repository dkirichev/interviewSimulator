# Conventions

## Naming and layering

- Java package root is fixed: `net.k2ai.interviewSimulator`.
- Layered package structure is rigid and singular: `config`, `controller`, `dto`, `entity`, `exception`, `interceptor`,
  `page`, `repository`, `scheduler`, `service`, `validation`.
- Type naming follows role suffixes:
    - Controllers: `*Controller`
    - Services: `*Service` + optional `*ServiceImpl`
    - Repositories: `*Repository`
    - Validators: `*Validator` with paired annotation (`@LettersOnly`, `@SafeText`, etc.).
- DB naming is snake_case with Flyway versioned migration files `V{n}__*.sql`.
- Route constants and template shell usage are centralized per controller (
  `private static final String LAYOUT = "layouts/main"` in MVC controllers).

## Folder and template structure

- Thymeleaf layout composition is mandatory:
    - Shell: `templates/layouts/main.html`
    - Injected page fragment path via model key `content`.
- Page segregation:
    - Setup pages under `templates/pages/setup/`
    - Interview/report standalone pages under `templates/pages/`
    - Admin and legal in dedicated subfolders.
- Frontend runtime scripts are split by concern (`audio-processor.js`, `interview.js`, `apikey.js`,
  `language-switcher.js`, etc.) and loaded conditionally in `layouts/fragments/bodyBottom.html`.

## Imports and code style

- Java imports are direct package imports; there is no aliasing mechanism.
- Wildcard imports are used selectively in annotation-heavy files (`org.springframework.web.bind.annotation.*`,
  `validation.*`) and should be preserved where already used.
- Lombok is standard for boilerplate reduction (`@RequiredArgsConstructor`, `@Data`, `@Slf4j`, `@Builder`).
- Method/closing-brace comments are part of existing style (`}//methodName`, `}//ClassName`) and are pervasive in main
  code.

## Validation and allowed values

- Validation is layered (annotation validators + service sanitization + runtime enum guards); all three are expected in
  user-input paths.
- Canonical enums used in setup/runtime:
    - Difficulty: `Easy|Standard|Hard`
    - Language: `en|bg`
    - Voice: `Algieba|Kore|Fenrir|Despina`
    - App mode: `DEV|PROD|REVIEWER`

## Error handling contract

- REST responses favor structured JSON objects with explicit flags, not opaque messages:
    - common keys: `success`, `error`, `fieldErrors`, `valid`, `rateLimited`, `invalidKey`, `requiresApiKey`.
- Expected validation/business failures are returned as 4xx payloads; unexpected failures return 500 payloads or error
  page fallback.
- `GlobalExceptionHandler` normalizes API vs MVC behavior (`isApiRequest`) and localizes field-level validation messages
  via `MessageSource`.
- WebSocket errors are pushed to `/user/queue/error` with typed flags (`rateLimited`, `invalidKey`) so frontend can
  branch behavior.
- Logging uses parameterized SLF4J with level discipline (`info` for lifecycle, `warn` for invalid input/access, `error`
  for failures).
