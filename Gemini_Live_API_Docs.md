# Gemini Live API - Quick Reference

Project-relevant excerpts from Google's Gemini Live API documentation.

---

## Audio Format Requirements

| Direction | Format | Sample Rate |
|-----------|--------|-------------|
| Input (to Gemini) | 16-bit PCM, mono, little-endian | 16kHz (native), any rate accepted |
| Output (from Gemini) | 16-bit PCM, mono, little-endian | 24kHz (fixed) |

**MIME type:** `audio/pcm;rate=16000`

---

## Connection Setup

```javascript
const session = await ai.live.connect({
  model: 'gemini-2.5-flash-native-audio-preview-12-2025',
  config: {
    responseModalities: [Modality.AUDIO],
    systemInstruction: "You are a helpful assistant.",
    speechConfig: { 
      voiceConfig: { 
        prebuiltVoiceConfig: { voiceName: "Aoede" } 
      } 
    },
    inputAudioTranscription: {},   // Enable user speech transcription
    outputAudioTranscription: {}   // Enable AI speech transcription
  },
  callbacks: {
    onopen: () => console.log('Connected'),
    onmessage: (msg) => handleMessage(msg),
    onerror: (e) => console.error(e),
    onclose: (e) => console.log('Closed')
  }
});
```

---

## Sending Audio

```javascript
session.sendRealtimeInput({
  audio: {
    data: base64EncodedPcmData,
    mimeType: "audio/pcm;rate=16000"
  }
});

// When mic paused for >1 second:
session.sendRealtimeInput({ audioStreamEnd: true });
```

---

## Receiving Messages

```javascript
// Audio response
if (message.serverContent?.modelTurn?.parts) {
  for (const part of message.serverContent.modelTurn.parts) {
    if (part.inlineData?.data) {
      const audioBytes = Buffer.from(part.inlineData.data, 'base64');
      playAudio(audioBytes); // 24kHz PCM
    }
  }
}

// Turn complete
if (message.serverContent?.turnComplete) {
  // AI finished speaking
}

// Interrupted (user spoke over AI)
if (message.serverContent?.interrupted) {
  clearAudioQueue(); // Stop playback immediately
}

// Transcriptions
if (message.serverContent?.inputTranscription?.text) {
  // User's speech transcribed
}
if (message.serverContent?.outputTranscription?.text) {
  // AI's speech transcribed
}
```

---

## Voice Activity Detection (VAD)

Enabled by default. Gemini automatically detects when user speaks.

```javascript
// Custom VAD config (optional)
config: {
  realtimeInputConfig: {
    automaticActivityDetection: {
      disabled: false,
      startOfSpeechSensitivity: StartSensitivity.START_SENSITIVITY_LOW,
      endOfSpeechSensitivity: EndSensitivity.END_SENSITIVITY_LOW,
      silenceDurationMs: 100
    }
  }
}
```

---

## Session Limits

| Type | Limit |
|------|-------|
| Audio-only session | 15 minutes |
| Audio+video session | 2 minutes |
| Context window | 128k tokens (native audio) |
| Connection lifetime | ~10 minutes (use session resumption) |

**Session resumption:** Pass `sessionResumption: { handle: previousHandle }` to reconnect.

---

## Key Caveats

1. **One modality per session** - Cannot mix TEXT and AUDIO responses
2. **Output is always 24kHz** - Browser must handle sample rate conversion
3. **Interruption clears queue** - When user speaks, discard pending AI audio
4. **GoAway message** - Server warns before disconnecting

---

## Available Voices

Aoede, Charon, Fenrir, Kore, Puck, and more. Set via `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`.

---

**Full documentation:** https://ai.google.dev/api/live
