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
            // User interrupted, clear audio queue
            audioQueue.length = 0;
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

// Audio playback using Web Audio API
async function playNextAudio() {
    if (audioQueue.length === 0) {
        isPlaying = false;
        return;
    }
    
    isPlaying = true;
    const audioData = audioQueue.shift();
    
    try {
        if (!playbackAudioContext || playbackAudioContext.state === 'closed') {
            playbackAudioContext = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: 24000 // Gemini outputs at 24kHz
            });
        }
        
        // Convert raw PCM to AudioBuffer
        const pcm16 = new Int16Array(audioData);
        const floatData = new Float32Array(pcm16.length);
        
        for (let i = 0; i < pcm16.length; i++) {
            floatData[i] = pcm16[i] / 32768.0;
        }
        
        const audioBuffer = playbackAudioContext.createBuffer(1, floatData.length, 24000);
        audioBuffer.getChannelData(0).set(floatData);
        
        const source = playbackAudioContext.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(playbackAudioContext.destination);
        
        source.onended = () => {
            playNextAudio();
        };
        
        source.start();
        
    } catch (err) {
        console.error('Audio playback error:', err);
        playNextAudio(); // Try next chunk
    }
}

// End interview and disconnect
function endInterviewConnection() {
    if (stompClient && isConnected) {
        stompClient.send('/app/interview/end', {}, '');
    }
    
    stopAudioCapture();
    audioQueue.length = 0;
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

