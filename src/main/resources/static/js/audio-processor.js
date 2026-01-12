// src/main/resources/static/js/audio-processor.js

let audioContext;
let processor;
let input;
let globalStream;
let websocket;

// 1. Initialize WebSocket
function connectToBackend() {
	// For development: Simulate connection without actual WebSocket
	// TODO: Uncomment when backend WebSocket is ready
	
	/*
	// Protocol must match (ws:// for http, wss:// for https)
	const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
	websocket = new WebSocket(`${protocol}//${window.location.host}/ws/interview`);

	websocket.binaryType = 'arraybuffer'; // Crucial for audio

	websocket.onopen = () => {
		console.log("Connected to AI Brain");
		updateStatus('Connected', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
		// Hide overlay with animation
		const overlay = document.getElementById('connection-overlay');
		overlay.style.opacity = '0';
		setTimeout(() => overlay.style.display = 'none', 500);
	};

	websocket.onerror = (error) => {
		console.error("WebSocket error:", error);
		updateStatus('Connection Error', 'bg-red-500/20 text-red-400 border-red-500/50');
		// Hide overlay even on error so UI isn't stuck
		const overlay = document.getElementById('connection-overlay');
		overlay.style.opacity = '0';
		setTimeout(() => overlay.style.display = 'none', 500);
	};

	websocket.onmessage = (event) => {
		// Handle incoming audio from Gemini (Server -> Browser)
		playAudioChunk(event.data);
	};

	websocket.onclose = () => {
		console.log("Disconnected");
		updateStatus('Disconnected', 'bg-red-500/20 text-red-400 border-red-500/50');
	};
	*/
	
	// MOCK: Simulate successful connection for frontend development
	console.log("MOCK: Simulating WebSocket connection...");
	setTimeout(() => {
		console.log("MOCK: Connected to AI Brain");
		updateStatus('Connected (MOCK)', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
		// Hide overlay with animation
		const overlay = document.getElementById('connection-overlay');
		overlay.style.opacity = '0';
		setTimeout(() => overlay.style.display = 'none', 500);
	}, 1500); // Simulate 1.5s connection time
}

// 2. Capture & Process Audio
async function startAudioCapture() {
	try {
		globalStream = await navigator.mediaDevices.getUserMedia({
			audio: {
				channelCount: 1,
				echoCancellation: true,
				noiseSuppression: true
			}
		});

		// Use default sample rate to avoid mismatch
		audioContext = new (window.AudioContext || window.webkitAudioContext)();
		
		input = audioContext.createMediaStreamSource(globalStream);

		// bufferSize: 2048 or 4096.
		// 1 channel input, 1 channel output
		processor = audioContext.createScriptProcessor(4096, 1, 1);

		processor.onaudioprocess = (e) => {
			if (!isMicActive || !websocket || websocket.readyState !== WebSocket.OPEN) return;

			const inputData = e.inputBuffer.getChannelData(0);

			// Convert Float32 (Browser) to Int16 (Gemini Requirement)
			const pcmData = floatTo16BitPCM(inputData);

			// Send to Backend
			websocket.send(pcmData);
		};

		input.connect(processor);
		processor.connect(audioContext.destination); // Needed for the processor to run

	} catch (err) {
		console.error("Mic Error:", err);
		alert("Microphone access denied!");
	}
}

function stopAudioCapture() {
	if (globalStream) globalStream.getTracks().forEach(track => track.stop());
	if (processor) processor.disconnect();
	if (input) input.disconnect();
	if (audioContext) audioContext.close();
}

// Helper: Convert Float32Array to Int16Array (PCM)
function floatTo16BitPCM(input) {
	const output = new Int16Array(input.length);
	for (let i = 0; i < input.length; i++) {
		let s = Math.max(-1, Math.min(1, input[i]));
		output[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
	}
	return output.buffer;
}

// 3. Play Incoming Audio (Simple Buffer Queue)
const audioQueue = [];
let isPlaying = false;

function playAudioChunk(arrayBuffer) {
	// 1. Decode generic raw PCM?
	// Usually Gemini sends WAV or raw PCM. If raw PCM, we need to wrap it in WAV container or use AudioContext to play buffer.
	// For simplicity, let's assume Backend sends a WAV header or we use AudioContext to decode.

	// Quickest way: Blob URL if it's a valid audio file chunk
	// Better way for streams: AudioContext.decodeAudioData

	const blob = new Blob([arrayBuffer], { type: 'audio/wav' });
	const url = URL.createObjectURL(blob);
	const audio = new Audio(url);

	audio.onplay = () => setAvatarState('talking');
	audio.onended = () => setAvatarState('idle');
	audio.play();
}
