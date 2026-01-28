# Get Started
Get started with Live API

The Live API enables low-latency, real-time voice and video interactions with Gemini. It processes continuous streams of audio, video, or text to deliver immediate, human-like spoken responses, creating a natural conversational experience for your users.

Live API offers a comprehensive set of features such as Voice Activity Detection, tool use and function calling, session management (for managing long running conversations) and ephemeral tokens (for secure client-sided authentication).

This page gets you up and running with examples and basic code samples.

Choose an implementation approach
When integrating with Live API, you'll need to choose one of the following implementation approaches:

Server-to-server: Your backend connects to the Live API using WebSockets. Typically, your client sends stream data (audio, video, text) to your server, which then forwards it to the Live API.
Client-to-server: Your frontend code connects directly to the Live API using WebSockets to stream data, bypassing your backend.

Note: Client-to-server generally offers better performance for streaming audio and video, since it bypasses the need to send the stream to your backend first. It's also easier to set up since you don't need to implement a proxy that sends data from your client to your server and then your server to the API. However, for production environments, in order to mitigate security risks, we recommend using ephemeral tokens instead of standard API keys.

Partner integrations
To streamline the development of real-time audio and video apps, you can use a third-party integration that supports the Gemini Live API over WebRTC or WebSockets.

Pipecat by Daily

Create a real-time AI chatbot using Gemini Live and Pipecat.

LiveKit

Use the Gemini Live API with LiveKit Agents.

Fishjam by Software Mansion

Create live video and audio streaming applications with Fishjam.

Agent Development Kit (ADK)

Implement the Live API with Agent Development Kit (ADK).

Vision Agents by Stream

Build real-time voice and video AI applications with Vision Agents.

Voximplant

Connect inbound and outbound calls to Live API with Voximplant.


Get started
Microphone stream Audio file stream

This server-side example streams audio from the microphone and plays the returned audio. For complete end-to-end examples including a client application, see Example applications.

The input audio format should be in 16-bit PCM, 16kHz, mono format, and the received audio uses a sample rate of 24kHz.

PYTHON/JAVASCRIPT

Install helpers for audio streaming. Additional system-level dependencies might be required (sox for Mac/Windows or ALSA for Linux). Refer to the speaker and mic docs for detailed installation steps.


npm install mic speaker
Note: Use headphones. This script uses the system default audio input and output, which often won't include echo cancellation. To prevent the model from interrupting itself, use headphones.
```JavaScript
import { GoogleGenAI, Modality } from '@google/genai';
import mic from 'mic';
import Speaker from 'speaker';

const ai = new GoogleGenAI({});
// WARNING: Do not use API keys in client-side (browser based) applications
// Consider using Ephemeral Tokens instead
// More information at: https://ai.google.dev/gemini-api/docs/ephemeral-tokens

// --- Live API config ---
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';
const config = {
responseModalities: [Modality.AUDIO],
systemInstruction: "You are a helpful and friendly AI assistant.",
};

async function live() {
const responseQueue = [];
const audioQueue = [];
let speaker;

async function waitMessage() {
while (responseQueue.length === 0) {
await new Promise((resolve) => setImmediate(resolve));
}
return responseQueue.shift();
}

function createSpeaker() {
if (speaker) {
process.stdin.unpipe(speaker);
speaker.end();
}
speaker = new Speaker({
channels: 1,
bitDepth: 16,
sampleRate: 24000,
});
speaker.on('error', (err) => console.error('Speaker error:', err));
process.stdin.pipe(speaker);
}

async function messageLoop() {
// Puts incoming messages in the audio queue.
while (true) {
const message = await waitMessage();
if (message.serverContent && message.serverContent.interrupted) {
// Empty the queue on interruption to stop playback
audioQueue.length = 0;
continue;
}
if (message.serverContent && message.serverContent.modelTurn && message.serverContent.modelTurn.parts) {
for (const part of message.serverContent.modelTurn.parts) {
if (part.inlineData && part.inlineData.data) {
audioQueue.push(Buffer.from(part.inlineData.data, 'base64'));
}
}
}
}
}

async function playbackLoop() {
// Plays audio from the audio queue.
while (true) {
if (audioQueue.length === 0) {
if (speaker) {
// Destroy speaker if no more audio to avoid warnings from speaker library
process.stdin.unpipe(speaker);
speaker.end();
speaker = null;
}
await new Promise((resolve) => setImmediate(resolve));
} else {
if (!speaker) createSpeaker();
const chunk = audioQueue.shift();
await new Promise((resolve) => {
speaker.write(chunk, () => resolve());
});
}
}
}

// Start loops
messageLoop();
playbackLoop();

// Connect to Gemini Live API
const session = await ai.live.connect({
model: model,
config: config,
callbacks: {
onopen: () => console.log('Connected to Gemini Live API'),
onmessage: (message) => responseQueue.push(message),
onerror: (e) => console.error('Error:', e.message),
onclose: (e) => console.log('Closed:', e.reason),
},
});

// Setup Microphone for input
const micInstance = mic({
rate: '16000',
bitwidth: '16',
channels: '1',
});
const micInputStream = micInstance.getAudioStream();

micInputStream.on('data', (data) => {
// API expects base64 encoded PCM data
session.sendRealtimeInput({
audio: {
data: data.toString('base64'),
mimeType: "audio/pcm;rate=16000"
}
});
});

micInputStream.on('error', (err) => {
console.error('Microphone error:', err);
});

micInstance.start();
console.log('Microphone started. Speak now...');
}

live().catch(console.error);
```
Install helpers for audio streaming. Additional system-level dependencies (e.g. portaudio) might be required. Refer to the PyAudio docs for detailed installation steps.

pip install pyaudio
Note: Use headphones. This script uses the system default audio input and output, which often won't include echo cancellation. To prevent the model from interrupting itself, use headphones.

```Python
import asyncio
from google import genai
import pyaudio

client = genai.Client()

# --- pyaudio config ---
FORMAT = pyaudio.paInt16
CHANNELS = 1
SEND_SAMPLE_RATE = 16000
RECEIVE_SAMPLE_RATE = 24000
CHUNK_SIZE = 1024

pya = pyaudio.PyAudio()

# --- Live API config ---
MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
CONFIG = {
"response_modalities": ["AUDIO"],
"system_instruction": "You are a helpful and friendly AI assistant.",
}

audio_queue_output = asyncio.Queue()
audio_queue_mic = asyncio.Queue(maxsize=5)
audio_stream = None

async def listen_audio():
"""Listens for audio and puts it into the mic audio queue."""
global audio_stream
mic_info = pya.get_default_input_device_info()
audio_stream = await asyncio.to_thread(
pya.open,
format=FORMAT,
channels=CHANNELS,
rate=SEND_SAMPLE_RATE,
input=True,
input_device_index=mic_info["index"],
frames_per_buffer=CHUNK_SIZE,
)
kwargs = {"exception_on_overflow": False} if __debug__ else {}
while True:
data = await asyncio.to_thread(audio_stream.read, CHUNK_SIZE, **kwargs)
await audio_queue_mic.put({"data": data, "mime_type": "audio/pcm"})

async def send_realtime(session):
"""Sends audio from the mic audio queue to the GenAI session."""
while True:
msg = await audio_queue_mic.get()
await session.send_realtime_input(audio=msg)

async def receive_audio(session):
"""Receives responses from GenAI and puts audio data into the speaker audio queue."""
while True:
turn = session.receive()
async for response in turn:
if (response.server_content and response.server_content.model_turn):
for part in response.server_content.model_turn.parts:
if part.inline_data and isinstance(part.inline_data.data, bytes):
audio_queue_output.put_nowait(part.inline_data.data)

        # Empty the queue on interruption to stop playback
        while not audio_queue_output.empty():
            audio_queue_output.get_nowait()

async def play_audio():
"""Plays audio from the speaker audio queue."""
stream = await asyncio.to_thread(
pya.open,
format=FORMAT,
channels=CHANNELS,
rate=RECEIVE_SAMPLE_RATE,
output=True,
)
while True:
bytestream = await audio_queue_output.get()
await asyncio.to_thread(stream.write, bytestream)

async def run():
"""Main function to run the audio loop."""
try:
async with client.aio.live.connect(
model=MODEL, config=CONFIG
) as live_session:
print("Connected to Gemini. Start speaking!")
async with asyncio.TaskGroup() as tg:
tg.create_task(send_realtime(live_session))
tg.create_task(listen_audio())
tg.create_task(receive_audio(live_session))
tg.create_task(play_audio())
except asyncio.CancelledError:
pass
finally:
if audio_stream:
audio_stream.close()
pya.terminate()
print("\nConnection closed.")

if __name__ == "__main__":
try:
asyncio.run(run())
except KeyboardInterrupt:
print("Interrupted by user.")
```
Example applications
Check out the following example applications that illustrate how to use Live API for end-to-end use cases:

Live audio starter app on AI Studio, using JavaScript libraries to connect to Live API and stream bidirectional audio through your microphone and speakers.
See the Partner integrations for additional examples and getting started guides.

# Live API capabilities guide

This is a comprehensive guide that covers capabilities and configurations available with the Live API. See Get started with Live API page for a overview and sample code for common use cases.

Before you begin
Familiarize yourself with core concepts: If you haven't already done so, read the Get started with Live API page first. This will introduce you to the fundamental principles of the Live API, how it works, and the different implementation approaches.
Try the Live API in AI Studio: You may find it useful to try the Live API in Google AI Studio before you start building. To use the Live API in Google AI Studio, select Stream.
Establishing a connection
The following example shows how to create a connection with an API key:

```JavaScript
import { GoogleGenAI, Modality } from '@google/genai';

const ai = new GoogleGenAI({});
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';
const config = { responseModalities: [Modality.AUDIO] };

async function main() {

  const session = await ai.live.connect({
    model: model,
    callbacks: {
      onopen: function () {
        console.debug('Opened');
      },
      onmessage: function (message) {
        console.debug(message);
      },
      onerror: function (e) {
        console.debug('Error:', e.message);
      },
      onclose: function (e) {
        console.debug('Close:', e.reason);
      },
    },
    config: config,
  });

  console.debug("Session started");
  // Send content...

  session.close();
}

main();
```
Interaction modalities
The following sections provide examples and supporting context for the different input and output modalities available in Live API.

Sending and receiving audio
The most common audio example, audio-to-audio, is covered in the Getting started guide.

Audio formats
Audio data in the Live API is always raw, little-endian, 16-bit PCM. Audio output always uses a sample rate of 24kHz. Input audio is natively 16kHz, but the Live API will resample if needed so any sample rate can be sent. To convey the sample rate of input audio, set the MIME type of each audio-containing Blob to a value like audio/pcm;rate=16000.

Sending text
Here's how you can send text:
```JavaScript
const message = 'Hello, how are you?';
session.sendClientContent({ turns: message, turnComplete: true });
```
Incremental content updates
Use incremental updates to send text input, establish session context, or restore session context. For short contexts you can send turn-by-turn interactions to represent the exact sequence of events:
```JavaScript
let inputTurns = [
  { "role": "user", "parts": [{ "text": "What is the capital of France?" }] },
  { "role": "model", "parts": [{ "text": "Paris" }] },
]

session.sendClientContent({ turns: inputTurns, turnComplete: false })

inputTurns = [{ "role": "user", "parts": [{ "text": "What is the capital of Germany?" }] }]

session.sendClientContent({ turns: inputTurns, turnComplete: true })
```
For longer contexts it's recommended to provide a single message summary to free up the context window for subsequent interactions. See Session Resumption for another method for loading session context.

Audio transcriptions
In addition to the model response, you can also receive transcriptions of both the audio output and the audio input.

To enable transcription of the model's audio output, send output_audio_transcription in the setup config. The transcription language is inferred from the model's response.

```JavaScript
import { GoogleGenAI, Modality } from '@google/genai';

const ai = new GoogleGenAI({});
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';

const config = {
  responseModalities: [Modality.AUDIO],
  outputAudioTranscription: {}
};

async function live() {
  const responseQueue = [];

  async function waitMessage() {
    let done = false;
    let message = undefined;
    while (!done) {
      message = responseQueue.shift();
      if (message) {
        done = true;
      } else {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
    }
    return message;
  }

  async function handleTurn() {
    const turns = [];
    let done = false;
    while (!done) {
      const message = await waitMessage();
      turns.push(message);
      if (message.serverContent && message.serverContent.turnComplete) {
        done = true;
      }
    }
    return turns;
  }

  const session = await ai.live.connect({
    model: model,
    callbacks: {
      onopen: function () {
        console.debug('Opened');
      },
      onmessage: function (message) {
        responseQueue.push(message);
      },
      onerror: function (e) {
        console.debug('Error:', e.message);
      },
      onclose: function (e) {
        console.debug('Close:', e.reason);
      },
    },
    config: config,
  });

  const inputTurns = 'Hello how are you?';
  session.sendClientContent({ turns: inputTurns });

  const turns = await handleTurn();

  for (const turn of turns) {
    if (turn.serverContent && turn.serverContent.outputTranscription) {
      console.debug('Received output transcription: %s\n', turn.serverContent.outputTranscription.text);
    }
  }

  session.close();
}

async function main() {
  await live().catch((e) => console.error('got error', e));
}

main();
```
To enable transcription of the model's audio input, send input_audio_transcription in setup config.

```JavaScript
import { GoogleGenAI, Modality } from '@google/genai';
import * as fs from "node:fs";
import pkg from 'wavefile';
const { WaveFile } = pkg;

const ai = new GoogleGenAI({});
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';

const config = {
	responseModalities: [Modality.AUDIO],
	inputAudioTranscription: {}
};

async function live() {
	const responseQueue = [];

	async function waitMessage() {
		let done = false;
		let message = undefined;
		while (!done) {
			message = responseQueue.shift();
			if (message) {
				done = true;
			} else {
				await new Promise((resolve) => setTimeout(resolve, 100));
			}
		}
		return message;
	}

	async function handleTurn() {
		const turns = [];
		let done = false;
		while (!done) {
			const message = await waitMessage();
			turns.push(message);
			if (message.serverContent && message.serverContent.turnComplete) {
				done = true;
			}
		}
		return turns;
	}

	const session = await ai.live.connect({
		model: model,
		callbacks: {
			onopen: function () {
				console.debug('Opened');
			},
			onmessage: function (message) {
				responseQueue.push(message);
			},
			onerror: function (e) {
				console.debug('Error:', e.message);
			},
			onclose: function (e) {
				console.debug('Close:', e.reason);
			},
		},
		config: config,
	});

	// Send Audio Chunk
	const fileBuffer = fs.readFileSync("16000.wav");

	// Ensure audio conforms to API requirements (16-bit PCM, 16kHz, mono)
	const wav = new WaveFile();
	wav.fromBuffer(fileBuffer);
	wav.toSampleRate(16000);
	wav.toBitDepth("16");
	const base64Audio = wav.toBase64();

	// If already in correct format, you can use this:
	// const fileBuffer = fs.readFileSync("sample.pcm");
	// const base64Audio = Buffer.from(fileBuffer).toString('base64');

	session.sendRealtimeInput(
		{
			audio: {
				data: base64Audio,
				mimeType: "audio/pcm;rate=16000"
			}
		}
	);

	const turns = await handleTurn();
	for (const turn of turns) {
		if (turn.text) {
			console.debug('Received text: %s\n', turn.text);
		}
		else if (turn.data) {
			console.debug('Received inline data: %s\n', turn.data);
		}
		else if (turn.serverContent && turn.serverContent.inputTranscription) {
			console.debug('Received input transcription: %s\n', turn.serverContent.inputTranscription.text);
		}
	}

	session.close();
}

async function main() {
	await live().catch((e) => console.error('got error', e));
}

main();
```
## Stream audio and video
Change voice and language
Native audio output models support any of the voices available for our Text-to-Speech (TTS) models. You can listen to all the voices in AI Studio.

To specify a voice, set the voice name within the speechConfig object as part of the session configuration:
```JavaScript
const config = {
  responseModalities: [Modality.AUDIO],
  speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "Kore" } } }
};
```
Note: If you're using the generateContent API, the set of available voices is slightly different. See the audio generation guide for generateContent audio generation voices.
The Live API supports multiple languages. Native audio output models automatically choose the appropriate language and don't support explicitly setting the language code.

### Native audio capabilities
Our latest models feature native audio output, which provides natural, realistic-sounding speech and improved multilingual performance. Native audio also enables advanced features like affective (emotion-aware) dialogue, proactive audio (where the model intelligently decides when to respond to input), and "thinking".

### Affective dialog
This feature lets Gemini adapt its response style to the input expression and tone.

To use affective dialog, set the api version to v1alpha and set enable_affective_dialog to truein the setup message:

```JavaScript
const ai = new GoogleGenAI({ httpOptions: {"apiVersion": "v1alpha"} });

const config = {
  responseModalities: [Modality.AUDIO],
  enableAffectiveDialog: true
};
```
Proactive audio
When this feature is enabled, Gemini can proactively decide not to respond if the content is not relevant.

To use it, set the api version to v1alpha and configure the proactivity field in the setup message and set proactive_audio to true:
```JavaScript
const ai = new GoogleGenAI({ httpOptions: {"apiVersion": "v1alpha"} });

const config = {
  responseModalities: [Modality.AUDIO],
  proactivity: { proactiveAudio: true }
}
```
## Thinking
The latest native audio output model gemini-2.5-flash-native-audio-preview-12-2025 supports thinking capabilities, with dynamic thinking enabled by default.

The thinkingBudget parameter guides the model on the number of thinking tokens to use when generating a response. You can disable thinking by setting thinkingBudget to 0. For more info on the thinkingBudget configuration details of the model, see the thinking budgets documentation.

```JavaScript
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';
const config = {
  responseModalities: [Modality.AUDIO],
  thinkingConfig: {
    thinkingBudget: 1024,
  },
};

async function main() {

  const session = await ai.live.connect({
    model: model,
    config: config,
    callbacks: ...,
  });

  // Send audio input and receive audio

  session.close();
}

main();
```
Additionally, you can enable thought summaries by setting includeThoughts to true in your configuration. See thought summaries for more info:
```JavaScript
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';
const config = {
  responseModalities: [Modality.AUDIO],
  thinkingConfig: {
    thinkingBudget: 1024,
    includeThoughts: true,
  },
};
```
## Voice Activity Detection (VAD)
Voice Activity Detection (VAD) allows the model to recognize when a person is speaking. This is essential for creating natural conversations, as it allows a user to interrupt the model at any time.

When VAD detects an interruption, the ongoing generation is canceled and discarded. Only the information already sent to the client is retained in the session history. The server then sends a BidiGenerateContentServerContent message to report the interruption.

The Gemini server then discards any pending function calls and sends a BidiGenerateContentServerContent message with the IDs of the canceled calls.

```JavaScript
const turns = await handleTurn();

for (const turn of turns) {
  if (turn.serverContent && turn.serverContent.interrupted) {
    // The generation was interrupted

    // If realtime playback is implemented in your application,
    // you should stop playing audio and clear queued playback here.
  }
}
```
## Automatic VAD
By default, the model automatically performs VAD on a continuous audio input stream. VAD can be configured with the realtimeInputConfig.automaticActivityDetection field of the setup configuration.

When the audio stream is paused for more than a second (for example, because the user switched off the microphone), an audioStreamEnd event should be sent to flush any cached audio. The client can resume sending audio data at any time.

```JavaScript
// example audio file to try:
// URL = "https://storage.googleapis.com/generativeai-downloads/data/hello_are_you_there.pcm"
// !wget -q $URL -O sample.pcm
import { GoogleGenAI, Modality } from '@google/genai';
import * as fs from "node:fs";

const ai = new GoogleGenAI({});
const model = 'gemini-live-2.5-flash-preview';
const config = { responseModalities: [Modality.TEXT] };

async function live() {
  const responseQueue = [];

  async function waitMessage() {
    let done = false;
    let message = undefined;
    while (!done) {
      message = responseQueue.shift();
      if (message) {
        done = true;
      } else {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
    }
    return message;
  }

  async function handleTurn() {
    const turns = [];
    let done = false;
    while (!done) {
      const message = await waitMessage();
      turns.push(message);
      if (message.serverContent && message.serverContent.turnComplete) {
        done = true;
      }
    }
    return turns;
  }

  const session = await ai.live.connect({
    model: model,
    callbacks: {
      onopen: function () {
        console.debug('Opened');
      },
      onmessage: function (message) {
        responseQueue.push(message);
      },
      onerror: function (e) {
        console.debug('Error:', e.message);
      },
      onclose: function (e) {
        console.debug('Close:', e.reason);
      },
    },
    config: config,
  });

  // Send Audio Chunk
  const fileBuffer = fs.readFileSync("sample.pcm");
  const base64Audio = Buffer.from(fileBuffer).toString('base64');

  session.sendRealtimeInput(
    {
      audio: {
        data: base64Audio,
        mimeType: "audio/pcm;rate=16000"
      }
    }

  );

  // if stream gets paused, send:
  // session.sendRealtimeInput({ audioStreamEnd: true })

  const turns = await handleTurn();
  for (const turn of turns) {
    if (turn.text) {
      console.debug('Received text: %s\n', turn.text);
    }
    else if (turn.data) {
      console.debug('Received inline data: %s\n', turn.data);
    }
  }

  session.close();
}

async function main() {
  await live().catch((e) => console.error('got error', e));
}

main();
```
With send_realtime_input, the API will respond to audio automatically based on VAD. While send_client_content adds messages to the model context in order, send_realtime_input is optimized for responsiveness at the expense of deterministic ordering.

## Automatic VAD configuration
For more control over the VAD activity, you can configure the following parameters. See API reference for more info.
```JavaScript
import { GoogleGenAI, Modality, StartSensitivity, EndSensitivity } from '@google/genai';

const config = {
  responseModalities: [Modality.TEXT],
  realtimeInputConfig: {
    automaticActivityDetection: {
      disabled: false, // default
      startOfSpeechSensitivity: StartSensitivity.START_SENSITIVITY_LOW,
      endOfSpeechSensitivity: EndSensitivity.END_SENSITIVITY_LOW,
      prefixPaddingMs: 20,
      silenceDurationMs: 100,
    }
  }
};
```
## Disable automatic VAD
Alternatively, the automatic VAD can be disabled by setting realtimeInputConfig.automaticActivityDetection.disabled to true in the setup message. In this configuration the client is responsible for detecting user speech and sending activityStart and activityEnd messages at the appropriate times. An audioStreamEnd isn't sent in this configuration. Instead, any interruption of the stream is marked by an activityEnd message.
```JavaScript
const config = {
  responseModalities: [Modality.TEXT],
  realtimeInputConfig: {
    automaticActivityDetection: {
      disabled: true,
    }
  }
};

session.sendRealtimeInput({ activityStart: {} })

session.sendRealtimeInput(
  {
    audio: {
      data: base64Audio,
      mimeType: "audio/pcm;rate=16000"
    }
  }

);

session.sendRealtimeInput({ activityEnd: {} })
```
## Token count
You can find the total number of consumed tokens in the usageMetadata field of the returned server message.
```JavaScript
const turns = await handleTurn();

for (const turn of turns) {
  if (turn.usageMetadata) {
    console.debug('Used %s tokens in total. Response token breakdown:\n', turn.usageMetadata.totalTokenCount);

    for (const detail of turn.usageMetadata.responseTokensDetails) {
      console.debug('%s\n', detail);
    }
  }
}
```
Media resolution
You can specify the media resolution for the input media by setting the mediaResolution field as part of the session configuration:

```JavaScript
import { GoogleGenAI, Modality, MediaResolution } from '@google/genai';

const config = {
    responseModalities: [Modality.TEXT],
    mediaResolution: MediaResolution.MEDIA_RESOLUTION_LOW,
};
```
## Limitations:
Consider the following limitations of the Live API when you plan your project.

Response modalities
You can only set one response modality (TEXT or AUDIO) per session in the session configuration. Setting both results in a config error message. This means that you can configure the model to respond with either text or audio, but not both in the same session.

Client authentication
The Live API only provides server-to-server authentication by default. If you're implementing your Live API application using a client-to-server approach, you need to use ephemeral tokens to mitigate security risks.

Session duration
Audio-only sessions are limited to 15 minutes, and audio plus video sessions are limited to 2 minutes. However, you can configure different session management techniques for unlimited extensions on session duration.

Context window
A session has a context window limit of:

128k tokens for native audio output models
32k tokens for other Live API models

# Session management with Live API

In the Live API, a session refers to a persistent connection where input and output are streamed continuously over the same connection (read more about how it works). This unique session design enables low latency and supports unique features, but can also introduce challenges, like session time limits, and early termination. This guide covers strategies for overcoming the session management challenges that can arise when using the Live API.

Session lifetime
Without compression, audio-only sessions are limited to 15 minutes, and audio-video sessions are limited to 2 minutes. Exceeding these limits will terminate the session (and therefore, the connection), but you can use context window compression to extend sessions to an unlimited amount of time.

The lifetime of a connection is limited as well, to around 10 minutes. When the connection terminates, the session terminates as well. In this case, you can configure a single session to stay active over multiple connections using session resumption. You'll also receive a GoAway message before the connection ends, allowing you to take further actions.

Context window compression
To enable longer sessions, and avoid abrupt connection termination, you can enable context window compression by setting the contextWindowCompression field as part of the session configuration.

In the ContextWindowCompressionConfig, you can configure a sliding-window mechanism and the number of tokens that triggers compression.

```JavaScript
const config = {
  responseModalities: [Modality.AUDIO],
  contextWindowCompression: { slidingWindow: {} }
};
```
Session resumption
To prevent session termination when the server periodically resets the WebSocket connection, configure the sessionResumption field within the setup configuration.

Passing this configuration causes the server to send SessionResumptionUpdate messages, which can be used to resume the session by passing the last resumption token as the SessionResumptionConfig.handle of the subsequent connection.

Resumption tokens are valid for 2 hr after the last sessions termination.

```JavaScript
import { GoogleGenAI, Modality } from '@google/genai';

const ai = new GoogleGenAI({});
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';

async function live() {
  const responseQueue = [];

  async function waitMessage() {
    let done = false;
    let message = undefined;
    while (!done) {
      message = responseQueue.shift();
      if (message) {
        done = true;
      } else {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
    }
    return message;
  }

  async function handleTurn() {
    const turns = [];
    let done = false;
    while (!done) {
      const message = await waitMessage();
      turns.push(message);
      if (message.serverContent && message.serverContent.turnComplete) {
        done = true;
      }
    }
    return turns;
  }

console.debug('Connecting to the service with handle %s...', previousSessionHandle)
const session = await ai.live.connect({
  model: model,
  callbacks: {
    onopen: function () {
      console.debug('Opened');
    },
    onmessage: function (message) {
      responseQueue.push(message);
    },
    onerror: function (e) {
      console.debug('Error:', e.message);
    },
    onclose: function (e) {
      console.debug('Close:', e.reason);
    },
  },
  config: {
    responseModalities: [Modality.AUDIO],
    sessionResumption: { handle: previousSessionHandle }
    // The handle of the session to resume is passed here, or else null to start a new session.
  }
});

const inputTurns = 'Hello how are you?';
session.sendClientContent({ turns: inputTurns });

const turns = await handleTurn();
for (const turn of turns) {
  if (turn.sessionResumptionUpdate) {
    if (turn.sessionResumptionUpdate.resumable && turn.sessionResumptionUpdate.newHandle) {
      let newHandle = turn.sessionResumptionUpdate.newHandle
      // ...Store newHandle and start new session with this handle here
    }
  }
}

  session.close();
}

async function main() {
  await live().catch((e) => console.error('got error', e));
}

main();
```
Receiving a message before the session disconnects
The server sends a GoAway message that signals that the current connection will soon be terminated. This message includes the timeLeft, indicating the remaining time and lets you take further action before the connection will be terminated as ABORTED.

```JavaScript
const turns = await handleTurn();

for (const turn of turns) {
  if (turn.goAway) {
    console.debug('Time left: %s\n', turn.goAway.timeLeft);
  }
}
```
Receiving a message when the generation is complete
The server sends a generationComplete message that signals that the model finished generating the response.

```JavaScript
const turns = await handleTurn();

for (const turn of turns) {
  if (turn.serverContent && turn.serverContent.generationComplete) {
    // The generation is complete
  }
}
```

# Ephemeral tokens

Ephemeral tokens are short-lived authentication tokens for accessing the Gemini API through WebSockets. They are designed to enhance security when you are connecting directly from a user's device to the API (a client-to-server implementation). Like standard API keys, ephemeral tokens can be extracted from client-side applications such as web browsers or mobile apps. But because ephemeral tokens expire quickly and can be restricted, they significantly reduce the security risks in a production environment. You should use them when accessing the Live API directly from client-side applications to enhance API key security.

How ephemeral tokens work
Here's how ephemeral tokens work at a high level:

1 Your client (e.g. web app) authenticates with your backend.
2 Your backend requests an ephemeral token from Gemini API's provisioning service.
3 Gemini API issues a short-lived token.
4 Your backend sends the token to the client for WebSocket connections to Live API. You can do this by swapping your API key with an ephemeral token.
5 The client then uses the token as if it were an API key.

This enhances security because even if extracted, the token is short-lived, unlike a long-lived API key deployed client-side. Since the client sends data directly to Gemini, this also improves latency and avoids your backends needing to proxy the real time data.

Create an ephemeral token
Here is a simplified example of how to get an ephemeral token from Gemini. By default, you'll have 1 minute to start new Live API sessions using the token from this request (newSessionExpireTime), and 30 minutes to send messages over that connection (expireTime).

```JavaScript
import { GoogleGenAI } from "@google/genai";

const client = new GoogleGenAI({});
const expireTime = new Date(Date.now() + 30 * 60 * 1000).toISOString();

  const token: AuthToken = await client.authTokens.create({
    config: {
      uses: 1, // The default
      expireTime: expireTime // Default is 30 mins
      newSessionExpireTime: new Date(Date.now() + (1 * 60 * 1000)), // Default 1 minute in the future
      httpOptions: {apiVersion: 'v1alpha'},
    },
  });
```
For expireTime value constraints, defaults, and other field specs, see the API reference. Within the expireTime timeframe, you'll need sessionResumption to reconnect the call every 10 minutes (this can be done with the same token even if uses: 1).

It's also possible to lock an ephemeral token to a set of configurations. This might be useful to further improve security of your application and keep your system instructions on the server side.

```JavaScript
import { GoogleGenAI } from "@google/genai";

const client = new GoogleGenAI({});
const expireTime = new Date(Date.now() + 30 * 60 * 1000).toISOString();

const token = await client.authTokens.create({
    config: {
        uses: 1, // The default
        expireTime: expireTime,
        liveConnectConstraints: {
            model: 'gemini-2.5-flash-native-audio-preview-12-2025',
            config: {
                sessionResumption: {},
                temperature: 0.7,
                responseModalities: ['AUDIO']
            }
        },
        httpOptions: {
            apiVersion: 'v1alpha'
        }
    }
});

// You'll need to pass the value under token.name back to your client to use it
```
You can also lock a subset of fields, see the SDK documentation for more info.

Connect to Live API with an ephemeral token
Once you have an ephemeral token, you use it as if it were an API key (but remember, it only works for the live API, and only with the v1alpha version of the API).

The use of ephemeral tokens only adds value when deploying applications that follow client-to-server implementation approach.

```JavaScript
import { GoogleGenAI, Modality } from '@google/genai';

// Use the token generated in the "Create an ephemeral token" section here
const ai = new GoogleGenAI({
  apiKey: token.name
});
const model = 'gemini-2.5-flash-native-audio-preview-12-2025';
const config = { responseModalities: [Modality.AUDIO] };

async function main() {

  const session = await ai.live.connect({
    model: model,
    config: config,
    callbacks: { ... },
  });

  // Send content...

  session.close();
}

main();
```
Note: If not using the SDK, note that ephemeral tokens must either be passed in an access_token query parameter, or in an HTTP Authorization prefixed by the auth-scheme Token.
See Get started with Live API for more examples.

Best practices
Set a short expiration duration using the expire_time parameter.
Tokens expire, requiring re-initiation of the provisioning process.
Verify secure authentication for your own backend. Ephemeral tokens will only be as secure as your backend authentication method.
Generally, avoid using ephemeral tokens for backend-to-Gemini connections, as this path is typically considered secure.
Limitations
Ephemeral tokens are only compatible with Live API at this time.
