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

// Session data
let currentSession = {
    candidateName: '',
    position: '',
    difficulty: ''
};

// Connect to backend WebSocket
function connectToBackend() {
    console.log("Connecting to WebSocket...");
    
    const socket = new SockJS('/ws/interview');
    stompClient = Stomp.over(socket);
    
    // Disable debug logging in production
    stompClient.debug = function(str) {
        console.debug(str);
    };
    
    stompClient.connect({}, function(frame) {
        console.log('Connected to WebSocket:', frame);
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
    
    // Send start message with interview parameters
    stompClient.send('/app/interview/start', {}, JSON.stringify({
        candidateName: currentSession.candidateName,
        position: currentSession.position,
        difficulty: currentSession.difficulty
    }));
    
    console.log('Interview start request sent:', currentSession);
}

function handleStatusMessage(message) {
    const data = JSON.parse(message.body);
    console.log('Status:', data);
    
    switch(data.type) {
        case 'CONNECTED':
            updateStatus('Connected', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
            hideConnectionOverlay();
            break;
        case 'TURN_COMPLETE':
            setAvatarState('idle');
            if (isMicActive) {
                updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
            }
            break;
        case 'INTERRUPTED':
            // User interrupted, clear audio queue and reset playback timing
            audioQueue.length = 0;
            nextPlayTime = 0;
            isPlaying = false;
            break;
        case 'GRADING':
            showGradingScreen();
            break;
        case 'DISCONNECTED':
            updateStatus('Disconnected', 'bg-red-500/20 text-red-400 border-red-500/50');
            break;
    }
}

function handleAudioMessage(message) {
    const data = JSON.parse(message.body);
    if (data.data) {
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
    }
}

function handleTranscriptMessage(message) {
    const data = JSON.parse(message.body);
    console.log('Transcript:', data.speaker, '-', data.text);
    
    // Store transcript for display
    appendToLiveTranscript(data.speaker, data.text);
}

function handleReportMessage(message) {
    const data = JSON.parse(message.body);
    console.log('Report received:', data);
    
    // Store report data and switch to report view
    displayReport(data);
    switchView('report');
}

function handleErrorMessage(message) {
    const data = JSON.parse(message.body);
    console.error('Error from server:', data.message);
    alert('Error: ' + data.message);
}

function handleTextMessage(message) {
    const data = JSON.parse(message.body);
    console.log('Text from AI:', data.text);
}

// Audio capture
async function startAudioCapture() {
    try {
        globalStream = await navigator.mediaDevices.getUserMedia({
            audio: {
                channelCount: 1,
                sampleRate: 16000,
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true
            }
        });

        audioContext = new (window.AudioContext || window.webkitAudioContext)({
            sampleRate: 16000
        });
        
        input = audioContext.createMediaStreamSource(globalStream);

        // Use ScriptProcessor for audio data access
        processor = audioContext.createScriptProcessor(4096, 1, 1);

        processor.onaudioprocess = (e) => {
            if (!isMicActive || !stompClient || !isConnected) return;

            const inputData = e.inputBuffer.getChannelData(0);

            // Convert Float32 to Int16 PCM
            const pcmData = floatTo16BitPCM(inputData);
            
            // Convert to base64 and send
            const base64Audio = arrayBufferToBase64(pcmData);
            stompClient.send('/app/interview/audio', {}, base64Audio);
        };

        input.connect(processor);
        processor.connect(audioContext.destination);
        
        console.log('Audio capture started');

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
    
    console.log('Audio capture stopped');
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
        if (!playbackAudioContext || playbackAudioContext.state === 'closed') {
            playbackAudioContext = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: 24000 // Gemini outputs at 24kHz
            });
            nextPlayTime = 0;
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
        
        // Create AudioBuffer
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
        
        // If nextPlayTime is in the past or not set, start immediately with small buffer
        if (nextPlayTime <= currentTime) {
            nextPlayTime = currentTime + 0.005; // 5ms buffer for scheduling
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

