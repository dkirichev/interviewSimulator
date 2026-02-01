// Interview Handler
let isMicActive = false;
let isInterviewActive = false;
let callStartTime = null;
let callTimerInterval = null;

const statusBadge = document.getElementById('status-badge');
const visualizerBars = document.querySelectorAll('.bar');
const avatarVisual = document.getElementById('avatar-visual');
const connectionOverlay = document.getElementById('connection-overlay');
const thinkingIndicator = document.getElementById('thinking-indicator');
const callDurationEl = document.getElementById('call-duration');


function startInterviewSimulation() {
    isInterviewActive = true;
    isMicActive = false;
    
    // Reset UI state
    if (connectionOverlay) {
        connectionOverlay.style.display = 'flex';
        connectionOverlay.style.opacity = '1';
        const overlayText = connectionOverlay.querySelector('p');
        if (overlayText) {
            overlayText.innerText = 'Establishing Secure Websocket...';
        }
    }
    
    // Reset mic button
    const micBtn = document.getElementById('mic-btn');
    if (micBtn) {
        micBtn.classList.remove('bg-slate-700', 'ring-2', 'ring-green-500');
        const muteOverlay = document.getElementById('mic-mute-overlay');
        if (muteOverlay) {
            muteOverlay.classList.remove('hidden');
        }
    }
    
    // Reset call timer
    resetCallTimer();
    
    // Connect to backend
    connectToBackend();
}


function updateStatus(text, classes) {
    if (statusBadge) {
        statusBadge.innerText = text;
        statusBadge.className = `px-4 py-1.5 rounded-full text-sm font-bold tracking-wider uppercase border ${classes} transition-all duration-300`;
    }
}


function toggleMic() {
    if (!isInterviewActive) return;
    
    isMicActive = !isMicActive;
    const btn = document.getElementById('mic-btn');
    const icon = btn.querySelector('i');
    const muteOverlay = document.getElementById('mic-mute-overlay');
    
    if (isMicActive) {
        btn.classList.add('bg-slate-700', 'ring-2', 'ring-green-500');
        icon.classList.remove('fa-microphone-slash');
        icon.classList.add('fa-microphone');
        if (muteOverlay) {
            muteOverlay.classList.add('hidden');
        }
        
        updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
        hideThinkingIndicator();
        
        startAudioCapture();
        startVisualizer();
    } else {
        btn.classList.remove('bg-slate-700', 'ring-2', 'ring-green-500');
        if (muteOverlay) {
            muteOverlay.classList.remove('hidden');
        }
        
        updateStatus('Processing...', 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50');
        showThinkingIndicator();
        setAvatarState('thinking');
        
        stopAudioCapture();
        stopVisualizer();
    }
}


function startVisualizer() {
    visualizerBars.forEach(bar => bar.classList.add('active'));
}


function stopVisualizer() {
    visualizerBars.forEach(bar => bar.classList.remove('active'));
}


function setAvatarState(state) {
    if (!avatarVisual) return;
    
    // Remove all state classes
    avatarVisual.classList.remove('avatar-idle', 'avatar-talking', 'avatar-thinking');
    avatarVisual.style.boxShadow = 'none';
    
    if (state === 'talking') {
        avatarVisual.classList.add('avatar-talking');
        avatarVisual.style.borderColor = '#60a5fa';
        avatarVisual.style.boxShadow = '0 0 40px rgba(59, 130, 246, 0.5)';
        hideThinkingIndicator();
    } else if (state === 'thinking') {
        avatarVisual.classList.add('avatar-thinking');
        avatarVisual.style.borderColor = '#475569';
    } else {
        avatarVisual.classList.add('avatar-idle');
        avatarVisual.style.borderColor = '#334155';
        hideThinkingIndicator();
    }
}


function showThinkingIndicator() {
    if (thinkingIndicator) {
        thinkingIndicator.classList.remove('hidden');
    }
}


function hideThinkingIndicator() {
    if (thinkingIndicator) {
        thinkingIndicator.classList.add('hidden');
    }
}


// Call Timer Functions
function startCallTimer() {
    callStartTime = Date.now();
    updateCallDuration();
    callTimerInterval = setInterval(updateCallDuration, 1000);
}


function stopCallTimer() {
    if (callTimerInterval) {
        clearInterval(callTimerInterval);
        callTimerInterval = null;
    }
}


function resetCallTimer() {
    stopCallTimer();
    callStartTime = null;
    if (callDurationEl) {
        callDurationEl.innerText = '00:00';
    }
}


function updateCallDuration() {
    if (!callStartTime || !callDurationEl) return;
    
    const elapsed = Math.floor((Date.now() - callStartTime) / 1000);
    const minutes = Math.floor(elapsed / 60);
    const seconds = elapsed % 60;
    
    callDurationEl.innerText = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}


function endInterview() {
    if (!isInterviewActive) return;
    
    isInterviewActive = false;
    isMicActive = false;
    
    stopVisualizer();
    stopAudioCapture();
    stopCallTimer();
    hideThinkingIndicator();
    
    // Show grading screen
    if (connectionOverlay) {
        connectionOverlay.style.display = 'flex';
        connectionOverlay.style.opacity = '1';
        const overlayText = connectionOverlay.querySelector('p');
        if (overlayText) {
            overlayText.innerText = 'Analyzing your performance...';
        }
    }
    
    // Tell server to end interview and start grading
    endInterviewConnection();
}


