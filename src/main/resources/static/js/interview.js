// Interview Handler
let isMicActive = false;
let isInterviewActive = false;

const statusBadge = document.getElementById('status-badge');
const visualizerBars = document.querySelectorAll('.bar');
const avatarVisual = document.getElementById('avatar-visual');
const connectionOverlay = document.getElementById('connection-overlay');

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
    
    // Connect to backend
    connectToBackend();
}

function updateStatus(text, classes) {
    if (statusBadge) {
        statusBadge.innerText = text;
        statusBadge.className = `px-3 py-1 rounded-full text-xs font-bold tracking-wider uppercase border ${classes} transition-all duration-300`;
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
        
        startAudioCapture();
        startVisualizer();
    } else {
        btn.classList.remove('bg-slate-700', 'ring-2', 'ring-green-500');
        if (muteOverlay) {
            muteOverlay.classList.remove('hidden');
        }
        
        updateStatus('Mic Muted', 'bg-red-500/20 text-red-400 border-red-500/50');
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
    
    if (state === 'talking') {
        avatarVisual.classList.remove('avatar-idle');
        avatarVisual.classList.add('avatar-talking');
        avatarVisual.style.borderColor = '#60a5fa';
        avatarVisual.style.boxShadow = '0 0 40px rgba(59, 130, 246, 0.5)';
    } else {
        avatarVisual.classList.add('avatar-idle');
        avatarVisual.classList.remove('avatar-talking');
        avatarVisual.style.borderColor = '#334155';
        avatarVisual.style.boxShadow = 'none';
    }
}

function endInterview() {
    if (!isInterviewActive) return;
    
    isInterviewActive = false;
    isMicActive = false;
    
    stopVisualizer();
    stopAudioCapture();
    
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

