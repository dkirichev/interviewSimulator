// src/main/resources/static/js/audio-processor.js

let audioContext;
let processor;            // ScriptProcessor fallback OR AudioWorkletNode
let input;
let workletNode;          // AudioWorkletNode (preferred)
let globalStream;
let stompClient = null;
let isConnected = false;
let usingWorklet = false;

// Audio playback queue
const audioQueue = [];
let isPlaying = false;
let playbackAudioContext;
let nextPlayTime = 0;

// Jitter buffer: wait until this much audio is queued before starting playback.
// Absorbs network jitter so brief gaps don't cause underruns on high-ping links.
const INITIAL_JITTER_MS = 150;
// On underrun, pad this much silence forward to avoid glitches.
const UNDERRUN_PAD_MS = 20;
// Target Gemini output rate
const PLAYBACK_SAMPLE_RATE = 24000;
// Active adaptive jitter target (grows on high latency, shrinks on stable net)
let jitterTargetMs = INITIAL_JITTER_MS;
let hasPrebuffered = false;
let queuedPcmSamples = 0;

// Track if introduction has completed (for one-time auto-unmute)
let hasIntroductionCompleted = false;

// Track if AI is currently speaking (to prevent interruption)
let isAISpeaking = false;

// Track if grading is in progress (to keep overlay visible)
let isGradingInProgress = false;
let hideOverlayTimeout = null;

// Network latency tracking
let pingInterval = null;
let rttSamples = [];
const RTT_WINDOW = 5;
const RTT_WARN_THRESHOLD_MS = 300;   // median RTT above this → show warning
const RTT_CLEAR_THRESHOLD_MS = 180;  // below this + user hasn't dismissed → hide
let networkWarningShown = false;
let networkWarningDismissed = false;

// Session data
let currentSession = {
	candidateName: '',
	position: '',
	difficulty: '',
	language: 'en'
};


/**
 * Start interview from server-provided session data.
 */
function startInterviewFromSession() {
	if (window.interviewSession) {
		currentSession = {
			candidateName: window.interviewSession.candidateName || 'Candidate',
			position: window.interviewSession.position || 'Developer',
			difficulty: window.interviewSession.difficulty || 'Easy',
			interviewLength: window.interviewSession.interviewLength || 'Standard',
			language: window.interviewSession.language || 'bg',
			cvText: window.interviewSession.cvText || null,
			voiceId: window.interviewSession.voiceId || 'Algieba',
			interviewerNameEN: window.interviewSession.interviewerNameEN || 'George',
			interviewerNameBG: window.interviewSession.interviewerNameBG || 'Георги'
		};

		requestMicrophoneAndConnect();
	} else {
		console.error('No interview session data found');
		alert('Session expired. Please restart the setup.');
		window.location.href = '/setup/step1';
	}
}


/**
 * Request microphone permission and connect to WebSocket.
 */
async function requestMicrophoneAndConnect() {
	try {
		const stream = await navigator.mediaDevices.getUserMedia({
			audio: {
				channelCount: 1,
				sampleRate: 16000,
				echoCancellation: true,
				noiseSuppression: true,
				autoGainControl: true
			}
		});

		window.preinitializedMicStream = stream;
		connectToBackend();

	} catch (error) {
		console.error('Microphone access denied:', error);
		alert('Microphone access is required for the interview. Please allow access and try again.');
		window.location.href = '/setup/step3';
	}
}


// Connect to backend WebSocket
function connectToBackend() {

	const socket = new SockJS('/ws/interview');
	stompClient = Stomp.over(socket);
	stompClient.debug = function (str) {};

	// STOMP heartbeat: detect dead connections faster on flaky links.
	stompClient.heartbeat.outgoing = 10000;
	stompClient.heartbeat.incoming = 10000;

	stompClient.connect({}, function (frame) {
		isConnected = true;

		stompClient.subscribe('/user/queue/status', handleStatusMessage);
		stompClient.subscribe('/user/queue/audio', handleAudioMessage);
		stompClient.subscribe('/user/queue/transcript', handleTranscriptMessage);
		stompClient.subscribe('/user/queue/report', handleReportMessage);
		stompClient.subscribe('/user/queue/error', handleErrorMessage);
		stompClient.subscribe('/user/queue/text', handleTextMessage);
		stompClient.subscribe('/user/queue/pong', handlePongMessage);

		startInterviewSession();
		startPingLoop();

	}, function (error) {
		console.error('WebSocket connection error:', error);
		updateStatus('Connection Failed', 'bg-red-500/20 text-red-400 border-red-500/50');
		hideConnectionOverlay();
		stopPingLoop();
	});
}

function startInterviewSession() {
	if (!stompClient || !isConnected) {
		console.error('Not connected to WebSocket');
		return;
	}

	hasIntroductionCompleted = false;
	isAISpeaking = false;

	const startPayload = {
		candidateName: currentSession.candidateName,
		position: currentSession.position,
		difficulty: currentSession.difficulty,
		interviewLength: currentSession.interviewLength,
		language: currentSession.language,
		pttMode: String(typeof isPttMode !== 'undefined' ? isPttMode : false)
	};

	if (currentSession.cvText) {
		startPayload.cvText = currentSession.cvText;
	}

	if (currentSession.voiceId) {
		startPayload.voiceId = currentSession.voiceId;
		startPayload.interviewerNameEN = currentSession.interviewerNameEN;
		startPayload.interviewerNameBG = currentSession.interviewerNameBG;
	}

	if (typeof getStoredApiKey === 'function') {
		const userApiKey = getStoredApiKey();
		if (userApiKey) {
			startPayload.userApiKey = userApiKey;
		}
	}

	stompClient.send('/app/interview/start', {}, JSON.stringify(startPayload));
}

function handleStatusMessage(message) {
	const data = JSON.parse(message.body);

	switch (data.type) {
		case 'CONNECTED':
			updateStatus('Connected', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
			if (typeof updateLoadingStep === 'function') {
				updateLoadingStep('connect', 'done');
			}
			if (typeof startCallTimer === 'function') {
				startCallTimer();
			}
			const overlay = document.getElementById('connection-overlay');
			if (overlay) {
				const overlayText = overlay.querySelector('p');
				if (overlayText) {
					overlayText.innerText = 'Waiting for interviewer...';
				}
			}
			break;
		case 'TURN_COMPLETE':
			setAvatarState('idle');
			if (typeof hideThinkingIndicator === 'function') {
				hideThinkingIndicator();
			}

			isAISpeaking = false;
			hideConnectionOverlay();

			if (!hasIntroductionCompleted) {
				hasIntroductionCompleted = true;
				if (!isMicActive && typeof enableMicAfterAI === 'function') {
					enableMicAfterAI();
				}
			} else if (isMicActive) {
				updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
			} else {
				if (typeof isPttMode !== 'undefined' && isPttMode && typeof pttKeyConfig !== 'undefined') {
					updateStatus('Hold ' + pttKeyConfig.display + ' to speak', 'bg-slate-700/50 text-slate-400 border-slate-600/50');
				} else {
					updateStatus('Your Turn', 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50');
				}
			}
			break;
		case 'INTERRUPTED':
			audioQueue.length = 0;
			queuedPcmSamples = 0;
			hasPrebuffered = false;
			nextPlayTime = 0;
			isPlaying = false;
			if (typeof hideThinkingIndicator === 'function') {
				hideThinkingIndicator();
			}
			break;
		case 'GRADING':
			if (isMicActive) {
				isMicActive = false;
				stopAudioCapture();
			}
			if (typeof stopCallTimer === 'function') {
				stopCallTimer();
			}
			stopPingLoop();
			showGradingScreen();
			break;
		case 'DISCONNECTED':
			updateStatus('Disconnected', 'bg-red-500/20 text-red-400 border-red-500/50');
			if (typeof stopCallTimer === 'function') {
				stopCallTimer();
			}
			stopPingLoop();
			break;
	}
}

function handleAudioMessage(message) {
	const data = JSON.parse(message.body);
	if (data.data) {
		hideConnectionOverlay();
		isAISpeaking = true;

		const audioBytes = base64ToArrayBuffer(data.data);
		audioQueue.push(audioBytes);
		queuedPcmSamples += audioBytes.byteLength / 2; // Int16 = 2 bytes/sample

		// Pre-buffer: wait for jitter target before starting playback.
		// This absorbs network jitter and prevents robotic/stuttering output.
		const bufferedMs = (queuedPcmSamples / PLAYBACK_SAMPLE_RATE) * 1000;
		if (!isPlaying && !hasPrebuffered && bufferedMs < jitterTargetMs) {
			// Still filling the jitter buffer
			setAvatarState('talking');
			updateStatus('AI Speaking', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
			if (typeof hideThinkingIndicator === 'function') hideThinkingIndicator();
			return;
		}

		hasPrebuffered = true;

		if (!isPlaying) {
			playNextAudio();
		}

		setAvatarState('talking');
		updateStatus('AI Speaking', 'bg-blue-500/20 text-blue-400 border-blue-500/50');

		if (typeof hideThinkingIndicator === 'function') {
			hideThinkingIndicator();
		}
	}
}

function handleTranscriptMessage(message) {
	const data = JSON.parse(message.body);
	appendToLiveTranscript(data.speaker, data.text);
}

function handleReportMessage(message) {
	isGradingInProgress = false;
	if (window._gradingInterval) {
		clearInterval(window._gradingInterval);
	}
	const data = JSON.parse(message.body);

	if (data.sessionId) {
		window.location.href = '/report/' + data.sessionId;
	} else {
		if (typeof displayReport === 'function') {
			displayReport(data);
		}
		if (typeof switchView === 'function') {
			switchView('report');
		}
	}
}

function handleErrorMessage(message) {
	isGradingInProgress = false;
	if (window._gradingInterval) {
		clearInterval(window._gradingInterval);
	}
	const data = JSON.parse(message.body);
	console.error('Error from server:', data.message);

	if (data.rateLimited) {
		if (typeof handleRateLimitError === 'function') {
			handleRateLimitError();
		} else {
			alert('API rate limit exceeded. Please use a new API key.');
		}
		if (typeof switchView === 'function') {
			switchView('setup');
		}
		return;
	}

	if (data.invalidKey) {
		if (typeof clearApiKeyAndShowModal === 'function') {
			clearApiKeyAndShowModal();
		} else if (typeof showApiKeyModal === 'function') {
			showApiKeyModal();
		} else {
			alert('Invalid API key. Please provide a valid Gemini API key.');
		}
		if (typeof switchView === 'function') {
			switchView('setup');
		}
		return;
	}

	if (data.requiresApiKey) {
		if (typeof showApiKeyModal === 'function') {
			showApiKeyModal();
		} else {
			alert('API key required. Please provide a valid Gemini API key.');
		}
		return;
	}

	alert('Error: ' + data.message);
}

function handleTextMessage(message) {
	const data = JSON.parse(message.body);
}


function handlePongMessage(message) {
	try {
		const data = JSON.parse(message.body);
		const rtt = Date.now() - data.t;
		if (rtt < 0 || rtt > 30000) return;

		rttSamples.push(rtt);
		if (rttSamples.length > RTT_WINDOW) rttSamples.shift();

		if (rttSamples.length < 3) return;

		const sorted = [...rttSamples].sort((a, b) => a - b);
		const median = sorted[Math.floor(sorted.length / 2)];

		adaptJitterBuffer(median);
		updateNetworkWarning(median);
	} catch (e) {
		console.error('Pong parse error', e);
	}
}


function startPingLoop() {
	stopPingLoop();
	pingInterval = setInterval(() => {
		if (stompClient && isConnected) {
			try {
				stompClient.send('/app/interview/ping', {}, JSON.stringify({t: Date.now()}));
			} catch (e) {
				// ignore — next tick will retry
			}
		}
	}, 4000);
}


function stopPingLoop() {
	if (pingInterval) {
		clearInterval(pingInterval);
		pingInterval = null;
	}
}


/**
 * Adapt playback jitter buffer to measured latency: bigger buffer on slow links
 * absorbs more jitter (smoother but higher turn-to-turn delay).
 */
function adaptJitterBuffer(medianRttMs) {
	let target;
	if (medianRttMs > 500) target = 500;
	else if (medianRttMs > 300) target = 350;
	else if (medianRttMs > 200) target = 250;
	else target = INITIAL_JITTER_MS;

	jitterTargetMs = target;
}


function updateNetworkWarning(medianRttMs) {
	if (networkWarningDismissed) return;

	if (medianRttMs > RTT_WARN_THRESHOLD_MS && !networkWarningShown) {
		showNetworkWarning(medianRttMs);
		networkWarningShown = true;
	} else if (medianRttMs < RTT_CLEAR_THRESHOLD_MS && networkWarningShown) {
		// Auto-hide once network recovers (before user dismissed).
		// Per UX spec user must click X to dismiss — so we keep it visible.
		// Update the latency readout instead.
		updateNetworkWarningLatency(medianRttMs);
	} else if (networkWarningShown) {
		updateNetworkWarningLatency(medianRttMs);
	}
}


function showNetworkWarning(medianRttMs) {
	const toast = document.getElementById('network-warning-toast');
	if (!toast) return;
	toast.classList.remove('hidden');
	updateNetworkWarningLatency(medianRttMs);
}


function updateNetworkWarningLatency(medianRttMs) {
	const el = document.getElementById('network-warning-latency');
	if (el) el.textContent = Math.round(medianRttMs) + ' ms';
}


function dismissNetworkWarning() {
	networkWarningDismissed = true;
	const toast = document.getElementById('network-warning-toast');
	if (toast) toast.classList.add('hidden');
}


// ─── Audio capture ────────────────────────────────────────────────────────────
async function startAudioCapture() {
	try {
		if (window.preinitializedMicStream) {
			globalStream = window.preinitializedMicStream;
			window.preinitializedMicStream = null;
		} else {
			globalStream = await navigator.mediaDevices.getUserMedia({
				audio: {
					channelCount: 1,
					sampleRate: 16000,
					echoCancellation: true,
					noiseSuppression: true,
					autoGainControl: true
				}
			});
		}

		audioContext = new (window.AudioContext || window.webkitAudioContext)({
			sampleRate: 16000
		});

		input = audioContext.createMediaStreamSource(globalStream);

		// Prefer AudioWorklet (off-thread, more reliable under CPU pressure).
		// Fall back to ScriptProcessor on browsers without Worklet support.
		if (audioContext.audioWorklet && typeof audioContext.audioWorklet.addModule === 'function') {
			try {
				await audioContext.audioWorklet.addModule('/js/pcm-recorder-worklet.js');
				workletNode = new AudioWorkletNode(audioContext, 'pcm-recorder', {
					numberOfInputs: 1,
					numberOfOutputs: 0,
					channelCount: 1,
					processorOptions: {targetSampleRate: 16000, batchSize: 4096}
				});
				workletNode.port.onmessage = (ev) => {
					if (!isMicActive || !stompClient || !isConnected || isAISpeaking) return;

					const pcmBuffer = ev.data; // ArrayBuffer of Int16 samples
					if (typeof isPttMode !== 'undefined' && isPttMode) {
						// Drop silent frames in PTT mode so Gemini VAD sees only speech
						const view = new Int16Array(pcmBuffer);
						let sumSq = 0;
						for (let i = 0; i < view.length; i++) {
							const f = view[i] / 32768;
							sumSq += f * f;
						}
						if (Math.sqrt(sumSq / view.length) < 0.005) return;
					}

					const base64Audio = arrayBufferToBase64(pcmBuffer);
					stompClient.send('/app/interview/audio', {}, base64Audio);
				};
				input.connect(workletNode);
				usingWorklet = true;
				return;
			} catch (e) {
				console.warn('AudioWorklet init failed, falling back to ScriptProcessor:', e);
			}
		}

		// Fallback: ScriptProcessor
		processor = audioContext.createScriptProcessor(4096, 1, 1);
		processor.onaudioprocess = (e) => {
			if (!isMicActive || !stompClient || !isConnected || isAISpeaking) return;

			const inputData = e.inputBuffer.getChannelData(0);

			if (typeof isPttMode !== 'undefined' && isPttMode) {
				let sumSq = 0;
				for (let i = 0; i < inputData.length; i++) sumSq += inputData[i] * inputData[i];
				if (Math.sqrt(sumSq / inputData.length) < 0.005) return;
			}

			const pcmData = floatTo16BitPCM(inputData);
			const base64Audio = arrayBufferToBase64(pcmData);
			stompClient.send('/app/interview/audio', {}, base64Audio);
		};

		input.connect(processor);
		processor.connect(audioContext.destination);
		usingWorklet = false;

	} catch (err) {
		console.error("Mic Error:", err);
		alert("Microphone access denied! Please allow microphone access to use the interview simulator.");
	}
}

function sendMicOffSignal() {
	if (stompClient && isConnected) {
		stompClient.send('/app/interview/mic-off', {}, '');
	}
}

function stopAudioCaptureOnly() {
	if (globalStream) globalStream.getTracks().forEach(track => track.stop());
	if (workletNode) {
		try { workletNode.disconnect(); } catch (e) {}
		workletNode.port.onmessage = null;
		workletNode = null;
	}
	if (processor) {
		try { processor.disconnect(); } catch (e) {}
		processor = null;
	}
	if (input) {
		try { input.disconnect(); } catch (e) {}
		input = null;
	}
	if (audioContext && audioContext.state !== 'closed') {
		audioContext.close().catch(() => {});
	}
	usingWorklet = false;
}

function stopAudioCapture() {
	stopAudioCaptureOnly();
	sendMicOffSignal();
}

function floatTo16BitPCM(input) {
	const output = new Int16Array(input.length);
	for (let i = 0; i < input.length; i++) {
		let s = Math.max(-1, Math.min(1, input[i]));
		output[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
	}
	return output.buffer;
}

function arrayBufferToBase64(buffer) {
	let binary = '';
	const bytes = new Uint8Array(buffer);
	for (let i = 0; i < bytes.byteLength; i++) {
		binary += String.fromCharCode(bytes[i]);
	}
	return btoa(binary);
}

function base64ToArrayBuffer(base64) {
	const binaryString = atob(base64);
	const bytes = new Uint8Array(binaryString.length);
	for (let i = 0; i < binaryString.length; i++) {
		bytes[i] = binaryString.charCodeAt(i);
	}
	return bytes.buffer;
}

// ─── Audio playback ──────────────────────────────────────────────────────────
// Gemini emits contiguous 24kHz PCM chunks. Concatenate with sample-accurate
// scheduling — NO per-chunk fades (fades were dampening every boundary and
// produced a robotic / warbly output). On underrun we pad small silence to
// keep timing monotonic without forward-jumping.
async function playNextAudio() {
	if (audioQueue.length === 0) {
		isPlaying = false;
		hasPrebuffered = false;
		queuedPcmSamples = 0;
		nextPlayTime = 0;
		return;
	}

	isPlaying = true;
	const audioData = audioQueue.shift();
	const pcm16 = new Int16Array(audioData);
	queuedPcmSamples = Math.max(0, queuedPcmSamples - pcm16.length);

	try {
		if (!playbackAudioContext || playbackAudioContext.state === 'closed') {
			playbackAudioContext = new (window.AudioContext || window.webkitAudioContext)({
				sampleRate: PLAYBACK_SAMPLE_RATE
			});
			nextPlayTime = 0;
		}

		if (playbackAudioContext.state === 'suspended') {
			await playbackAudioContext.resume();
		}

		const floatData = new Float32Array(pcm16.length);
		for (let i = 0; i < pcm16.length; i++) {
			floatData[i] = pcm16[i] / 32768.0;
		}

		const audioBuffer = playbackAudioContext.createBuffer(1, floatData.length, PLAYBACK_SAMPLE_RATE);
		audioBuffer.getChannelData(0).set(floatData);

		const source = playbackAudioContext.createBufferSource();
		source.buffer = audioBuffer;
		source.connect(playbackAudioContext.destination);

		const currentTime = playbackAudioContext.currentTime;
		const chunkDuration = floatData.length / PLAYBACK_SAMPLE_RATE;

		// Underrun handling: if the scheduled start is in the past, shift forward
		// by a small pad so we don't glitch at the boundary. First chunk after
		// pre-buffer: start a tick in the future to give the context headroom.
		if (nextPlayTime <= currentTime) {
			nextPlayTime = currentTime + (UNDERRUN_PAD_MS / 1000);
		}

		source.start(nextPlayTime);
		nextPlayTime += chunkDuration;

		// Schedule next chunk a bit before this one ends for seamless playback
		const timeUntilNextChunk = (nextPlayTime - currentTime) * 1000 - 30;
		setTimeout(() => {
			playNextAudio();
		}, Math.max(0, timeUntilNextChunk));

	} catch (err) {
		console.error('Audio playback error:', err);
		nextPlayTime = 0;
		setTimeout(() => playNextAudio(), 10);
	}
}

// End interview and disconnect
function endInterviewConnection() {
	if (stompClient && isConnected) {
		stompClient.send('/app/interview/end', {}, '');
	}

	stopAudioCapture();
	stopPingLoop();
	audioQueue.length = 0;
	queuedPcmSamples = 0;
	hasPrebuffered = false;
	nextPlayTime = 0;
	isPlaying = false;
}

// Disconnect WebSocket
function disconnectWebSocket() {
	if (stompClient) {
		stompClient.disconnect();
		stompClient = null;
		isConnected = false;
	}
}

function hideConnectionOverlay() {
	if (isGradingInProgress) return;
	const overlay = document.getElementById('connection-overlay');
	if (overlay) {
		overlay.style.opacity = '0';
		hideOverlayTimeout = setTimeout(() => {
			if (!isGradingInProgress) {
				overlay.style.display = 'none';
			}
		}, 500);
	}
}

function showGradingScreen() {
	isGradingInProgress = true;
	if (hideOverlayTimeout) {
		clearTimeout(hideOverlayTimeout);
		hideOverlayTimeout = null;
	}
	const overlay = document.getElementById('connection-overlay');
	if (!overlay) return;

	const msgs = window.gradingMessages || {
		title: 'Analyzing Your Interview',
		step1: 'Processing interview transcript...',
		step2: 'Evaluating your responses...',
		step3: 'Generating detailed feedback...',
		pleaseWait: 'This usually takes a few seconds'
	};

	overlay.innerHTML = `
		<div class="flex flex-col items-center gap-6 max-w-sm text-center">
			<div class="w-16 h-16 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
			<h2 class="text-xl font-semibold text-white">${msgs.title}</h2>
			<p id="grading-step" class="text-blue-400 font-mono text-base transition-opacity duration-500">${msgs.step1}</p>
			<div class="flex gap-2 mt-2">
				<div id="grading-dot-1" class="w-2.5 h-2.5 rounded-full bg-blue-500"></div>
				<div id="grading-dot-2" class="w-2.5 h-2.5 rounded-full bg-slate-600"></div>
				<div id="grading-dot-3" class="w-2.5 h-2.5 rounded-full bg-slate-600"></div>
			</div>
			<p class="text-slate-500 text-sm mt-2">${msgs.pleaseWait}</p>
		</div>
	`;
	overlay.style.display = 'flex';
	overlay.style.opacity = '1';

	const steps = [msgs.step1, msgs.step2, msgs.step3];
	let currentStep = 0;

	window._gradingInterval = setInterval(() => {
		currentStep = (currentStep + 1) % steps.length;
		const stepEl = document.getElementById('grading-step');
		if (stepEl) {
			stepEl.style.opacity = '0';
			setTimeout(() => {
				stepEl.textContent = steps[currentStep];
				stepEl.style.opacity = '1';
			}, 300);
		}
		for (let i = 0; i < 3; i++) {
			const dot = document.getElementById('grading-dot-' + (i + 1));
			if (dot) {
				dot.className = i <= currentStep
					? 'w-2.5 h-2.5 rounded-full bg-blue-500'
					: 'w-2.5 h-2.5 rounded-full bg-slate-600';
			}
		}
	}, 3000);
}

let liveTranscript = [];

function appendToLiveTranscript(speaker, text) {
	liveTranscript.push({speaker, text});
}

function getLiveTranscript() {
	return liveTranscript;
}

function clearLiveTranscript() {
	liveTranscript = [];
}

function isAISpeakingNow() {
	return isAISpeaking || isPlaying;
}
