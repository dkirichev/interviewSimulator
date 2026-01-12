// Interview Handler
let isMicActive = false;
let isInterviewActive = false;

const statusBadge = document.getElementById('status-badge');
const visualizerBars = document.querySelectorAll('.bar');
const avatarVisual = document.getElementById('avatar-visual');
const connectionOverlay = document.getElementById('connection-overlay');

function startInterviewSimulation() {
    isInterviewActive = true;
    connectionOverlay.style.display = 'flex';
    connectionOverlay.style.opacity = '1';
    connectToBackend();
}

function updateStatus(text, classes) {
    statusBadge.innerText = text;
    statusBadge.className = `px-3 py-1 rounded-full text-xs font-bold tracking-wider uppercase border ${classes} transition-all duration-300`;
}

function toggleMic() {
    isMicActive = !isMicActive;
    const btn = document.getElementById('mic-btn');
    const icon = btn.querySelector('i');
    
    if (isMicActive) {
        btn.classList.add('bg-slate-700', 'ring-2', 'ring-green-500');
        icon.classList.remove('fa-microphone-slash');
        icon.classList.add('fa-microphone');
        document.getElementById('mic-mute-overlay').classList.add('hidden');
        
        updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
        
        startAudioCapture();
        startVisualizer();
    } else {
        btn.classList.remove('bg-slate-700', 'ring-2', 'ring-green-500');
        document.getElementById('mic-mute-overlay').classList.remove('hidden');
        
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

function triggerMockAIResponse() {
    if (!isInterviewActive) return;

    updateStatus('AI Speaking', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
    setAvatarState('talking');
    
    setTimeout(() => {
        setAvatarState('idle');
        if(isMicActive) {
            updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
        } else {
            updateStatus('Waiting...', 'bg-slate-800 text-slate-400 border-slate-700');
        }
    }, 3000);
}

function endInterview() {
    isInterviewActive = false;
    stopVisualizer();
    
    const randomScore = Math.floor(Math.random() * (95 - 60 + 1) + 60);
    document.getElementById('final-score').innerText = randomScore;
    document.getElementById('report-id').innerText = Math.floor(Math.random() * 9000 + 1000);

    switchView('report');
}
