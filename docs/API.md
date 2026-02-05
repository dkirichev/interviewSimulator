# 🔌 API Reference

Complete reference for all REST and WebSocket endpoints.

## Table of Contents

- [REST API Endpoints](#rest-api-endpoints)
- [WebSocket Endpoints](#websocket-endpoints)
- [Error Handling](#error-handling)
- [Rate Limiting](#rate-limiting)

---

## REST API Endpoints

### Application Mode

#### `GET /api/mode`

Returns the current application mode.

**Response:**
```json
{
  "mode": "PROD",
  "requiresUserKey": true
}
```

| Field | Type | Description |
|-------|------|-------------|
| `mode` | string | `"DEV"` or `"PROD"` |
| `requiresUserKey` | boolean | Whether users must provide their own API key |

---

### API Key Validation

#### `POST /api/validate-key`

Validates a user-provided Gemini API key.

**Request:**
```json
{
  "apiKey": "AIza..."
}
```

**Success Response (200):**
```json
{
  "valid": true,
  "message": "API key is valid"
}
```

**Error Responses:**

| Status | Response |
|--------|----------|
| 400 | `{"valid": false, "error": "Invalid API key format..."}` |
| 400 | `{"valid": false, "error": "Invalid API key. Please check..."}` |
| 429 | `{"valid": false, "error": "This API key has exceeded its quota...", "rateLimited": true}` |
| 500 | `{"valid": false, "error": "Server error while validating..."}` |

---

### CV Upload

#### `POST /api/cv/upload`

Uploads and extracts text from a CV/resume file.

**Request:**
- Content-Type: `multipart/form-data`
- Field: `file` (PDF or DOCX, max 2MB)

**Success Response (200):**
```json
{
  "success": true,
  "text": "Extracted CV text content...",
  "fileName": "resume.pdf"
}
```

**Error Responses:**

| Status | Response |
|--------|----------|
| 400 | `{"success": false, "error": "File is empty or null"}` |
| 400 | `{"success": false, "error": "File size exceeds maximum allowed (2MB)"}` |
| 400 | `{"success": false, "error": "Invalid file type. Only PDF and DOCX are allowed."}` |
| 400 | `{"success": false, "error": "Invalid PDF file - file content doesn't match"}` |

---

### Voice Options

#### `GET /api/voices`

Returns available interviewer voices.

**Response:**
```json
{
  "voices": [
    {
      "id": "Algieba",
      "nameEN": "George",
      "nameBG": "Георги",
      "gender": "male"
    },
    {
      "id": "Kore",
      "nameEN": "Victoria",
      "nameBG": "Виктория",
      "gender": "female"
    },
    {
      "id": "Fenrir",
      "nameEN": "Max",
      "nameBG": "Макс",
      "gender": "male"
    },
    {
      "id": "Despina",
      "nameEN": "Diana",
      "nameBG": "Диана",
      "gender": "female"
    }
  ]
}
```

#### `GET /api/voices/preview/{voiceId}/{language}`

Returns a preview audio file for a voice.

**Path Parameters:**

| Parameter | Values |
|-----------|--------|
| `voiceId` | `Algieba`, `Kore`, `Fenrir`, `Despina` |
| `language` | `en`, `bg` |

**Response:**
- Content-Type: `audio/wav`
- Body: WAV audio data

---

### Page Routes

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Redirects to `/setup/step1` |
| GET | `/setup/step1` | Setup wizard - Profile |
| GET | `/setup/step2` | Setup wizard - Details + CV |
| GET | `/setup/step3` | Setup wizard - Voice & Language |
| POST | `/setup/step1` | Process step 1 form |
| POST | `/setup/step2` | Process step 2 form |
| POST | `/setup/step3` | Process step 3 form, redirect to interview |
| POST | `/setup/clear` | Clear setup form from session |
| GET | `/interview` | Interview page (requires completed setup) |
| GET | `/report/{sessionId}` | Server-rendered interview report |

---

## WebSocket Endpoints

### Connection

**Endpoint:** `ws://localhost:8080/ws/interview`

The application uses STOMP over SockJS for WebSocket communication.

**JavaScript Connection Example:**
```javascript
const socket = new SockJS('/ws/interview');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    // Subscribe to personal queues
    stompClient.subscribe('/user/queue/status', handleStatus);
    stompClient.subscribe('/user/queue/audio', handleAudio);
    stompClient.subscribe('/user/queue/transcript', handleTranscript);
    stompClient.subscribe('/user/queue/report', handleReport);
    stompClient.subscribe('/user/queue/error', handleError);
});
```

---

### Client → Server Messages

#### `/app/interview/start`

Starts a new interview session.

**Payload:**
```json
{
  "candidateName": "John Doe",
  "position": "Senior Java Developer",
  "difficulty": "Standard",
  "language": "en",
  "cvText": "Extracted CV text...",
  "voiceId": "Fenrir",
  "interviewerNameEN": "Max",
  "interviewerNameBG": "Макс",
  "userApiKey": "AIza..."
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `candidateName` | Yes | Candidate's name (2-30 chars, letters only) |
| `position` | Yes | Target job position |
| `difficulty` | Yes | `"Easy"`, `"Standard"`, or `"Hard"` |
| `language` | Yes | `"en"` or `"bg"` |
| `cvText` | No | Extracted CV text |
| `voiceId` | No | Voice ID (defaults to config) |
| `interviewerNameEN` | No | Interviewer name in English |
| `interviewerNameBG` | No | Interviewer name in Bulgarian |
| `userApiKey` | Prod only | User's Gemini API key |

---

#### `/app/interview/audio`

Sends an audio chunk to the AI.

**Payload:** Base64-encoded 16kHz PCM audio string

**Example:**
```javascript
stompClient.send('/app/interview/audio', {}, base64AudioData);
```

---

#### `/app/interview/end`

Manually ends the interview and triggers grading.

**Payload:** None

---

#### `/app/interview/mic-off`

Signals that the microphone was muted.

**Payload:** None

---

### Server → Client Messages

#### `/user/queue/status`

Connection and turn status updates.

**Payload:**
```json
{
  "type": "CONNECTED",
  "message": "AI interviewer ready"
}
```

| Type | Description |
|------|-------------|
| `CONNECTED` | WebSocket connected, interview starting |
| `TURN_COMPLETE` | AI finished speaking, user's turn |
| `INTERRUPTED` | User interrupted AI speech |
| `GRADING` | Interview ended, grading in progress |
| `DISCONNECTED` | Connection lost |

---

#### `/user/queue/audio`

AI audio response chunks.

**Payload:**
```json
{
  "data": "base64EncodedAudioData..."
}
```

Audio format: 24kHz 16-bit PCM mono

---

#### `/user/queue/transcript`

Speech transcription (both user and AI).

**Payload:**
```json
{
  "speaker": "user",
  "text": "Transcribed speech text..."
}
```

| Speaker | Description |
|---------|-------------|
| `"user"` | User's speech transcription |
| `"ai"` | AI's speech transcription |

---

#### `/user/queue/report`

Final interview report after grading.

**Payload:**
```json
{
  "sessionId": "uuid-here",
  "overallScore": 78,
  "communicationScore": 82,
  "technicalScore": 75,
  "confidenceScore": 80,
  "strengths": ["Clear communication", "Good problem-solving approach"],
  "improvements": ["Could provide more specific examples"],
  "detailedAnalysis": "The candidate demonstrated solid technical knowledge...",
  "verdict": "HIRE",
  "transcript": "[Interviewer]: Hello...\n[Candidate]: Hi..."
}
```

| Verdict | Meaning |
|---------|---------|
| `STRONG_HIRE` | Excellent candidate, highly recommended |
| `HIRE` | Good candidate, recommended |
| `MAYBE` | Borderline, needs more evaluation |
| `NO_HIRE` | Not recommended at this time |

---

#### `/user/queue/error`

Error messages.

**Payload:**
```json
{
  "message": "Error description...",
  "rateLimited": true,
  "invalidKey": false,
  "requiresApiKey": false
}
```

| Flag | Description |
|------|-------------|
| `rateLimited` | API key has exceeded its quota |
| `invalidKey` | API key is invalid |
| `requiresApiKey` | No API key provided (PROD mode) |

---

## Error Handling

### HTTP Status Codes

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 400 | Bad Request - Invalid input |
| 404 | Not Found - Resource doesn't exist |
| 429 | Too Many Requests - Rate limited |
| 500 | Internal Server Error |

### WebSocket Error Messages

The application sends error messages via `/user/queue/error` for:

- Invalid or missing API key
- Rate limit exceeded
- Connection failures
- Grading failures

---

## Rate Limiting

### API Key Validation

API key validation (`/api/validate-key`) is rate-limited by IP address:
- **10 requests per minute** per IP
- Returns 429 if exceeded

### Gemini API Limits

Google's free tier has its own limits:
- Requests per minute: 15
- Tokens per minute: 32,000
- Requests per day: 1,500

When rate limited, errors include `rateLimited: true` flag.

---

## Audio Specifications

### Input (to Gemini)

| Property | Value |
|----------|-------|
| Format | 16-bit PCM, mono |
| Sample Rate | 16kHz |
| Encoding | Base64 |
| MIME Type | `audio/pcm;rate=16000` |

### Output (from Gemini)

| Property | Value |
|----------|-------|
| Format | 16-bit PCM, mono |
| Sample Rate | 24kHz |
| Encoding | Base64 |

---

[← Back to README](../README.md) | [Architecture →](ARCHITECTURE.md)
