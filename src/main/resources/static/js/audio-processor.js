// src/main/resources/static/js/audio-processor.js

let audioContext;
let processor;
let input;
let globalStream;
let stompClient = null;
let isConnected = false;

// Audio playback queue
const audioQueue = [];
let isPlaying = false;
let playbackAudioContext;
let nextPlayTime = 0;  // Track when next chunk should start for gapless playback
const CROSSFADE_SAMPLES = 64;  // Samples to crossfade between chunks to eliminate clicks

// Track if introduction has completed (for one-time auto-unmute)
let hasIntroductionCompleted = false;

// Track if AI is currently speaking (to prevent interruption)
let isAISpeaking = false;

// Session data
let currentSession = {
    candidateName: '',
    position: '',
    difficulty: '',
    language: 'en'
};


/**
 * Start interview from server-provided session data.
 * Called by interview-standalone.html when the page loads.
 * Reads from window.interviewSession set by Thymeleaf.
 */
function startInterviewFromSession() {
    // Check if session data is available (set by Thymeleaf in interview-standalone.html)
    if (window.interviewSession) {
        currentSession = {
            candidateName: window.interviewSession.candidateName || 'Candidate',
            position: window.interviewSession.position || 'Developer',
            difficulty: window.interviewSession.difficulty || 'Easy',
            language: window.interviewSession.language || 'bg',
            cvText: window.interviewSession.cvText || null,
            voiceId: window.interviewSession.voiceId || 'Algieba',
            interviewerNameEN: window.interviewSession.interviewerNameEN || 'George',
            interviewerNameBG: window.interviewSession.interviewerNameBG || 'Георги'
        };
        
        // Request microphone and start
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
        // Request microphone permission
        const stream = await navigator.mediaDevices.getUserMedia({
            audio: {
                channelCount: 1,
                sampleRate: 16000,
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true
            }
        });
        
        // Store stream for later use
        window.preinitializedMicStream = stream;
        
        // Connect to WebSocket
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
    
    // Disable debug logging in production
    stompClient.debug = function(str) {
    };
    
    stompClient.connect({}, function(frame) {
        isConnected = true;
        
        // Subscribe to user-specific queues
        stompClient.subscribe('/user/queue/status', handleStatusMessage);
        stompClient.subscribe('/user/queue/audio', handleAudioMessage);
        stompClient.subscribe('/user/queue/transcript', handleTranscriptMessage);
        stompClient.subscribe('/user/queue/report', handleReportMessage);
        stompClient.subscribe('/user/queue/error', handleErrorMessage);
        stompClient.subscribe('/user/queue/text', handleTextMessage);
        
        // Start the interview session
        startInterviewSession();
        
    }, function(error) {
        console.error('WebSocket connection error:', error);
        updateStatus('Connection Failed', 'bg-red-500/20 text-red-400 border-red-500/50');
        hideConnectionOverlay();
    });
}

function startInterviewSession() {
    if (!stompClient || !isConnected) {
        console.error('Not connected to WebSocket');
        return;
    }
    
    // Reset introduction flag for new session
    hasIntroductionCompleted = false;
    
    // Reset AI speaking flag
    isAISpeaking = false;
    
    // Send start message with interview parameters (including optional CV text and voice)
    const startPayload = {
        candidateName: currentSession.candidateName,
        position: currentSession.position,
        difficulty: currentSession.difficulty,
        language: currentSession.language
    };
    
    // Add CV text if available
    if (currentSession.cvText) {
        startPayload.cvText = currentSession.cvText;
    }
    
    // Add voice selection if available
    if (currentSession.voiceId) {
        startPayload.voiceId = currentSession.voiceId;
        startPayload.interviewerNameEN = currentSession.interviewerNameEN;
        startPayload.interviewerNameBG = currentSession.interviewerNameBG;
    }
    
    // Add user API key for PROD mode (from localStorage)
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
    
    switch(data.type) {
        case 'CONNECTED':
            updateStatus('Connected', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
            // Update loading step (but keep overlay visible until AI speaks)
            if (typeof updateLoadingStep === 'function') {
                updateLoadingStep('connect', 'done');
            }
            // Start the call timer
            if (typeof startCallTimer === 'function') {
                startCallTimer();
            }
            // Update overlay message to show we're waiting for AI
            const overlay = document.getElementById('connection-overlay');
            if (overlay) {
                const overlayText = overlay.querySelector('p');
                if (overlayText) {
                    overlayText.innerText = 'Waiting for interviewer...';
                }
            }
            // DON'T hide overlay yet - wait for first audio or TURN_COMPLETE
            break;
        case 'TURN_COMPLETE':
            setAvatarState('idle');
            if (typeof hideThinkingIndicator === 'function') {
                hideThinkingIndicator();
            }
            
            // AI finished speaking - allow user audio to be sent again
            isAISpeaking = false;
            
            // Hide connection overlay if still visible (AI finished speaking)
            hideConnectionOverlay();
            
            // Auto-enable mic ONLY after the first AI turn (introduction)
            // After that, user controls their own mute state
            if (!hasIntroductionCompleted) {
                hasIntroductionCompleted = true;
                if (!isMicActive && typeof enableMicAfterAI === 'function') {
                    enableMicAfterAI();
                }
            } else if (isMicActive) {
                updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
            } else {
                updateStatus('Your Turn', 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50');
            }
            break;
        case 'INTERRUPTED':
            // User interrupted, clear audio queue and reset playback timing
            audioQueue.length = 0;
            nextPlayTime = 0;
            isPlaying = false;
            if (typeof hideThinkingIndicator === 'function') {
                hideThinkingIndicator();
            }
            break;
        case 'GRADING':
            // Interview ended - immediately stop microphone to prevent audio spam
            if (isMicActive) {
                isMicActive = false;
                stopAudioCapture();
            }
            if (typeof stopCallTimer === 'function') {
                stopCallTimer();
            }
            showGradingScreen();
            break;
        case 'DISCONNECTED':
            updateStatus('Disconnected', 'bg-red-500/20 text-red-400 border-red-500/50');
            if (typeof stopCallTimer === 'function') {
                stopCallTimer();
            }
            break;
    }
}

function handleAudioMessage(message) {
    const data = JSON.parse(message.body);
    if (data.data) {
        // Hide connection overlay on first audio (AI has started speaking)
        hideConnectionOverlay();
        
        // Mark AI as speaking (prevents sending user audio)
        isAISpeaking = true;
        
        // Queue audio for playback
        const audioBytes = base64ToArrayBuffer(data.data);
        audioQueue.push(audioBytes);
        
        // Start playback if not already playing
        if (!isPlaying) {
            playNextAudio();
        }
        
        // Update UI to show AI is speaking
        setAvatarState('talking');
        updateStatus('AI Speaking', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
        
        // Hide thinking indicator when AI starts speaking
        if (typeof hideThinkingIndicator === 'function') {
            hideThinkingIndicator();
        }
    }
}

function handleTranscriptMessage(message) {
    const data = JSON.parse(message.body);
    
    // Store transcript for display
    appendToLiveTranscript(data.speaker, data.text);
}

function handleReportMessage(message) {
    const data = JSON.parse(message.body);
    
    // Redirect to server-rendered report page
    if (data.sessionId) {
        window.location.href = '/report/' + data.sessionId;
    } else {
        // Fallback: use displayReport if sessionId not available
        if (typeof displayReport === 'function') {
            displayReport(data);
        }
        if (typeof switchView === 'function') {
            switchView('report');
        }
    }
}

function handleErrorMessage(message) {
    const data = JSON.parse(message.body);
    console.error('Error from server:', data.message);
    
    // Check if this is a rate limit error
    if (data.rateLimited) {
        // Handle rate limit - clear cached key and show modal
        if (typeof handleRateLimitError === 'function') {
            handleRateLimitError();
        } else {
            alert('API rate limit exceeded. Please use a new API key.');
        }
        // Go back to setup view
        if (typeof switchView === 'function') {
            switchView('setup');
        }
        return;
    }
    
    // Check if API key is invalid
    if (data.invalidKey) {
        // Handle invalid key - clear cached key and show modal
        if (typeof clearApiKeyAndShowModal === 'function') {
            clearApiKeyAndShowModal();
        } else if (typeof showApiKeyModal === 'function') {
            showApiKeyModal();
        } else {
            alert('Invalid API key. Please provide a valid Gemini API key.');
        }
        // Go back to setup view
        if (typeof switchView === 'function') {
            switchView('setup');
        }
        return;
    }
    
    // Check if API key is required (PROD mode without key)
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

// Audio capture
async function startAudioCapture() {
    try {
        // Use pre-initialized stream if available, otherwise request new one
        if (window.preinitializedMicStream) {
            globalStream = window.preinitializedMicStream;
            window.preinitializedMicStream = null; // Clear it after use
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

        // Use ScriptProcessor for audio data access
        processor = audioContext.createScriptProcessor(4096, 1, 1);

        processor.onaudioprocess = (e) => {
            // Don't send audio if: mic off, not connected, or AI is speaking
            if (!isMicActive || !stompClient || !isConnected || isAISpeaking) return;

            const inputData = e.inputBuffer.getChannelData(0);

            // Convert Float32 to Int16 PCM
            const pcmData = floatTo16BitPCM(inputData);
            
            // Convert to base64 and send
            const base64Audio = arrayBufferToBase64(pcmData);
            stompClient.send('/app/interview/audio', {}, base64Audio);
        };

        input.connect(processor);
        processor.connect(audioContext.destination);
        

    } catch (err) {
        console.error("Mic Error:", err);
        alert("Microphone access denied! Please allow microphone access to use the interview simulator.");
    }
}

function stopAudioCapture() {
    if (globalStream) globalStream.getTracks().forEach(track => track.stop());
    if (processor) processor.disconnect();
    if (input) input.disconnect();
    if (audioContext && audioContext.state !== 'closed') audioContext.close();
    
    // Notify server that audio stream ended
    if (stompClient && isConnected) {
        stompClient.send('/app/interview/mic-off', {}, '');
    }
    
}

// Convert Float32Array to Int16Array (PCM)
function floatTo16BitPCM(input) {
    const output = new Int16Array(input.length);
    for (let i = 0; i < input.length; i++) {
        let s = Math.max(-1, Math.min(1, input[i]));
        output[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
    }
    return output.buffer;
}

// Convert ArrayBuffer to base64
function arrayBufferToBase64(buffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
}

// Convert base64 to ArrayBuffer
function base64ToArrayBuffer(base64) {
    const binaryString = atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}

// Audio playback using Web Audio API with gapless scheduling
async function playNextAudio() {
    if (audioQueue.length === 0) {
        isPlaying = false;
        nextPlayTime = 0;  // Reset for next playback session
        return;
    }
    
    isPlaying = true;
    const audioData = audioQueue.shift();
    
    try {
        // Initialize or resume AudioContext (reuse for entire session)
        // Note: Some browsers may not support 24kHz and will use hardware rate (typically 48kHz)
        if (!playbackAudioContext || playbackAudioContext.state === 'closed') {
            playbackAudioContext = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: 24000 // Request 24kHz to match Gemini output
            });
            nextPlayTime = 0;
            const actualRate = playbackAudioContext.sampleRate;
            
            // Warn if browser doesn't support 24kHz (will cause pitch/speed issues)
            if (actualRate !== 24000) {
            }
        }
        
        // Resume if suspended (browser autoplay policy)
        if (playbackAudioContext.state === 'suspended') {
            await playbackAudioContext.resume();
        }
        
        // Convert raw PCM Int16 to Float32
        const pcm16 = new Int16Array(audioData);
        const floatData = new Float32Array(pcm16.length);
        
        for (let i = 0; i < pcm16.length; i++) {
            floatData[i] = pcm16[i] / 32768.0;
        }
        
        // Apply fade-in to eliminate click at chunk start
        const fadeInSamples = Math.min(CROSSFADE_SAMPLES, floatData.length);
        for (let i = 0; i < fadeInSamples; i++) {
            floatData[i] *= (i / fadeInSamples);
        }
        
        // Apply fade-out to eliminate click at chunk end
        const fadeOutStart = floatData.length - CROSSFADE_SAMPLES;
        if (fadeOutStart > 0) {
            for (let i = 0; i < CROSSFADE_SAMPLES; i++) {
                floatData[fadeOutStart + i] *= (1 - i / CROSSFADE_SAMPLES);
            }
        }
        
        // Create AudioBuffer with GEMINI'S sample rate (24kHz)
        // If browser's AudioContext is different rate, it will handle resampling
        const audioBuffer = playbackAudioContext.createBuffer(1, floatData.length, 24000);
        audioBuffer.getChannelData(0).set(floatData);
        
        // Create source node with gain for smooth transitions
        const source = playbackAudioContext.createBufferSource();
        source.buffer = audioBuffer;
        
        // Add a small gain node to prevent clipping
        const gainNode = playbackAudioContext.createGain();
        gainNode.gain.value = 0.95;
        
        source.connect(gainNode);
        gainNode.connect(playbackAudioContext.destination);
        
        // Calculate gapless start time
        const currentTime = playbackAudioContext.currentTime;
        const chunkDuration = floatData.length / 24000;
        
        // If nextPlayTime is in the past or not set, start immediately (no buffer for first chunk)
        if (nextPlayTime <= currentTime) {
            nextPlayTime = currentTime;
        }
        
        // Schedule this chunk to start exactly when the previous one ends
        source.start(nextPlayTime);
        
        // Calculate when this chunk will end (for scheduling next chunk)
        // Subtract crossfade overlap to create seamless transition
        const overlapTime = CROSSFADE_SAMPLES / 24000;
        nextPlayTime += chunkDuration - overlapTime;
        
        // Schedule next chunk processing slightly before this one ends
        const timeUntilEnd = (nextPlayTime - currentTime) * 1000;
        setTimeout(() => {
            playNextAudio();
        }, Math.max(0, timeUntilEnd - 50)); // Process 50ms before end
        
    } catch (err) {
        console.error('Audio playback error:', err);
        // Reset and try next chunk
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
    audioQueue.length = 0;
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

// Helper to hide connection overlay
function hideConnectionOverlay() {
    const overlay = document.getElementById('connection-overlay');
    if (overlay) {
        overlay.style.opacity = '0';
        setTimeout(() => overlay.style.display = 'none', 500);
    }
}

// Show grading screen
function showGradingScreen() {
    const overlay = document.getElementById('connection-overlay');
    if (overlay) {
        overlay.style.display = 'flex';
        overlay.style.opacity = '1';
        overlay.querySelector('p').innerText = 'Analyzing your performance...';
    }
}

// Live transcript (for debugging/display)
let liveTranscript = [];

function appendToLiveTranscript(speaker, text) {
    liveTranscript.push({ speaker, text });
}

function getLiveTranscript() {
    return liveTranscript;
}

function clearLiveTranscript() {
    liveTranscript = [];
}

// Check if AI is currently speaking (for UI state management)
function isAISpeakingNow() {
    return isAISpeaking || isPlaying;
}

