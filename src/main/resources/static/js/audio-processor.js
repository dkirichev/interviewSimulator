// src/main/resources/static/js/audio-processor.js

// Capture pipeline (mic → server). Created once on interview start and kept
// alive for the session — muting just stops SENDING, not the pipeline itself.
// That avoids a ~300–500ms unmute lag from re-initialising AudioContext and
// re-loading the worklet module on every mic toggle.
let audioContext;
let processor;            // ScriptProcessor fallback (legacy browsers)
let input;
let workletNode;          // AudioWorkletNode — preferred
let globalStream;
let captureInitialized = false;
let stompClient = null;
let isConnected = false;
let usingWorklet = false;

// Playback pipeline (server → speakers). One ring-buffer AudioWorklet streams
// Gemini PCM continuously — no chunk scheduling, no overlap races.
let playbackAudioContext;
let playerNode;
let playerReady = false;
let bufferedPlayerSamples = 0;

// Gemini output rate (fixed by Live API)
const PLAYBACK_SAMPLE_RATE = 24000;

// Jitter pre-buffer: hold incoming PCM out of the worklet ring until we have
// enough to survive typical network jitter without an underrun. Once the
// threshold is crossed we drain to the worklet and let it stream.
const INITIAL_JITTER_MS = 150;
let jitterTargetMs = INITIAL_JITTER_MS;
let prebufferChunks = [];
let prebufferedSamples = 0;
let hasPrebuffered = false;
// Sanity cap on buffered audio to prevent memory blowup during a runaway
// network burst. ~30 seconds of audio is plenty of headroom.
const MAX_BUFFERED_SAMPLES = PLAYBACK_SAMPLE_RATE * 30;

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
		// Pre-warm both pipelines while AI generates the first audio chunk.
		// This makes the post-intro mic unmute instantaneous.
		ensurePlayerReady();
		initializeAudioCapture();

	}, function (error) {
		console.error('WebSocket connection error:', error);
		updateStatus(window.statusMessages?.connectionFailed || 'Connection Failed', 'bg-red-500/20 text-red-400 border-red-500/50');
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

	safeStompSend('/app/interview/start', {}, JSON.stringify(startPayload));
}

function handleStatusMessage(message) {
	const data = JSON.parse(message.body);

	switch (data.type) {
		case 'CONNECTED':
			updateStatus(window.statusMessages?.connected || 'Connected', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
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
					overlayText.innerText = window.statusMessages?.waitingForInterviewer || 'Waiting for interviewer...';
				}
			}
			break;
		case 'TURN_COMPLETE':
			setAvatarState('idle');
			if (typeof hideThinkingIndicator === 'function') {
				hideThinkingIndicator();
			}

			isAISpeaking = false;
			// Flush any prebuffer so we don't strand the tail of the turn.
			flushPrebuffer();
			hideConnectionOverlay();
			// Reset prebuffer gate for the next turn. Keeps jitter protection
			// at the start of each AI response.
			hasPrebuffered = false;

			if (!hasIntroductionCompleted) {
				hasIntroductionCompleted = true;
				if (!isMicActive && typeof enableMicAfterAI === 'function') {
					enableMicAfterAI();
				}
			} else if (isMicActive) {
				updateStatus(window.statusMessages?.listening || 'Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
			} else {
				if (typeof isPttMode !== 'undefined' && isPttMode && typeof pttKeyConfig !== 'undefined') {
					const tpl = window.statusMessages?.holdToSpeak || 'Hold {key} to speak';
					updateStatus(tpl.replace('{key}', pttKeyConfig.display), 'bg-slate-700/50 text-slate-400 border-slate-600/50');
				} else {
					updateStatus(window.statusMessages?.yourTurn || 'Your Turn', 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50');
				}
			}
			break;
		case 'INTERRUPTED':
			// Hard-cut: purge prebuffer AND the worklet ring so AI voice stops
			// immediately when the user interrupts.
			resetPlaybackBuffers();
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
			updateStatus(window.statusMessages?.disconnected || 'Disconnected', 'bg-red-500/20 text-red-400 border-red-500/50');
			if (typeof stopCallTimer === 'function') {
				stopCallTimer();
			}
			stopPingLoop();
			break;
	}
}

function handleAudioMessage(message) {
	const data = JSON.parse(message.body);
	if (!data.data) return;

	hideConnectionOverlay();
	isAISpeaking = true;
	setAvatarState('talking');
	updateStatus(window.statusMessages?.aiSpeaking || 'AI Speaking', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
	if (typeof hideThinkingIndicator === 'function') hideThinkingIndicator();

	const audioBytes = base64ToArrayBuffer(data.data);
	const chunkSamples = audioBytes.byteLength / 2; // Int16

	// Bring the player online on the first chunk of the session. Safe to call
	// repeatedly; no-op once initialised.
	ensurePlayerReady();

	if (!hasPrebuffered) {
		// Hold chunks until we have jitter target worth, then flush.
		prebufferChunks.push(audioBytes);
		prebufferedSamples += chunkSamples;
		const bufferedMs = (prebufferedSamples / PLAYBACK_SAMPLE_RATE) * 1000;
		if (bufferedMs >= jitterTargetMs) {
			flushPrebuffer();
		}
		return;
	}

	// Streaming: push straight to the worklet.
	pushPcmToPlayer(audioBytes);
}


function flushPrebuffer() {
	if (prebufferChunks.length > 0) {
		for (const buf of prebufferChunks) {
			pushPcmToPlayer(buf);
		}
		prebufferChunks = [];
		prebufferedSamples = 0;
	}
	hasPrebuffered = true;
}


function pushPcmToPlayer(arrayBuffer) {
	if (!playerNode) return;
	// Drop if we're already sitting on way more than we can sensibly buffer —
	// prevents memory growth during a runaway network burst.
	if (bufferedPlayerSamples > MAX_BUFFERED_SAMPLES) {
		console.warn('Player buffer over max, dropping chunk');
		return;
	}
	// Transfer ownership so we don't pay a structured clone on every chunk.
	playerNode.port.postMessage(arrayBuffer, [arrayBuffer]);
}


function resetPlaybackBuffers() {
	prebufferChunks = [];
	prebufferedSamples = 0;
	hasPrebuffered = false;
	bufferedPlayerSamples = 0;
	if (playerNode) {
		try { playerNode.port.postMessage({type: 'clear'}); } catch (e) {}
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
			safeStompSend('/app/interview/ping', {}, JSON.stringify({t: Date.now()}));
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


// ─── Playback worklet init ────────────────────────────────────────────────────
async function ensurePlayerReady() {
	if (playerReady) return;
	if (!playbackAudioContext || playbackAudioContext.state === 'closed') {
		playbackAudioContext = new (window.AudioContext || window.webkitAudioContext)({
			sampleRate: PLAYBACK_SAMPLE_RATE
		});
	}
	if (playbackAudioContext.state === 'suspended') {
		try { await playbackAudioContext.resume(); } catch (e) {}
	}
	try {
		await playbackAudioContext.audioWorklet.addModule('/js/pcm-player-worklet.js');
		playerNode = new AudioWorkletNode(playbackAudioContext, 'pcm-player', {
			numberOfInputs: 0,
			numberOfOutputs: 1,
			outputChannelCount: [1]
		});
		playerNode.port.onmessage = (ev) => {
			if (ev.data && ev.data.type === 'buffered') {
				bufferedPlayerSamples = ev.data.samples;
			}
		};
		playerNode.connect(playbackAudioContext.destination);
		playerReady = true;
	} catch (e) {
		console.error('Failed to init playback worklet:', e);
	}
}


// ─── Audio capture ────────────────────────────────────────────────────────────
// Capture pipeline inits ONCE per interview. Subsequent mute/unmute toggles
// just flip `isMicActive` — the worklet handler short-circuits when the flag
// is off. This makes unmute instant (no AudioContext rebuild, no worklet
// module reload).
async function initializeAudioCapture() {
	if (captureInitialized) return true;

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

					const pcmBuffer = ev.data;
					if (typeof isPttMode !== 'undefined' && isPttMode) {
						const view = new Int16Array(pcmBuffer);
						let sumSq = 0;
						for (let i = 0; i < view.length; i++) {
							const f = view[i] / 32768;
							sumSq += f * f;
						}
						if (Math.sqrt(sumSq / view.length) < 0.005) return;
					}

					const base64Audio = arrayBufferToBase64(pcmBuffer);
					safeStompSend('/app/interview/audio', {}, base64Audio);
				};
				input.connect(workletNode);
				usingWorklet = true;
				captureInitialized = true;
				return true;
			} catch (e) {
				console.warn('AudioWorklet init failed, falling back to ScriptProcessor:', e);
			}
		}

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
			safeStompSend('/app/interview/audio', {}, base64Audio);
		};

		input.connect(processor);
		processor.connect(audioContext.destination);
		usingWorklet = false;
		captureInitialized = true;
		return true;

	} catch (err) {
		console.error("Mic Error:", err);
		alert("Microphone access denied! Please allow microphone access to use the interview simulator.");
		return false;
	}
}


// Public API used by interview.js / ptt.js — backward-compat wrapper.
async function startAudioCapture() {
	await initializeAudioCapture();
}


function safeStompSend(destination, headers, body) {
	try {
		stompClient.send(destination, headers, body);
	} catch (e) {
		console.warn('STOMP send failed:', destination, e);
	}
}


function sendMicOffSignal() {
	if (stompClient && isConnected) {
		safeStompSend('/app/interview/mic-off', {}, '');
	}
}


// Lightweight stop used by mic toggle / PTT release. Does NOT tear down the
// pipeline — that's what makes unmute instant. The `isMicActive` flag is set
// false by the caller; the worklet then drops frames without sending.
function stopAudioCaptureOnly() {
	// No-op — pipeline stays alive. `isMicActive` gate in the worklet handler
	// already prevents any audio from being sent.
}


function stopAudioCapture() {
	stopAudioCaptureOnly();
	sendMicOffSignal();
}


// Full teardown — called only on interview end. Releases the mic, closes the
// AudioContext, stops the playback worklet.
function teardownAudioPipelines() {
	if (globalStream) {
		globalStream.getTracks().forEach(t => { try { t.stop(); } catch (e) {} });
		globalStream = null;
	}
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
		audioContext = null;
	}
	captureInitialized = false;
	usingWorklet = false;

	if (playerNode) {
		try { playerNode.disconnect(); } catch (e) {}
		playerNode.port.onmessage = null;
		playerNode = null;
	}
	if (playbackAudioContext && playbackAudioContext.state !== 'closed') {
		playbackAudioContext.close().catch(() => {});
		playbackAudioContext = null;
	}
	playerReady = false;
	bufferedPlayerSamples = 0;
	prebufferChunks = [];
	prebufferedSamples = 0;
	hasPrebuffered = false;
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

// End interview and disconnect
function endInterviewConnection() {
	if (stompClient && isConnected) {
		safeStompSend('/app/interview/end', {}, '');
	}

	stopPingLoop();
	resetPlaybackBuffers();
	teardownAudioPipelines();
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
	return isAISpeaking || bufferedPlayerSamples > 0;
}
