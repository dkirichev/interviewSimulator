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


/**
 * Called after AI finishes speaking to auto-enable mic.
 * This gives the user their turn to speak.
 */
function enableMicAfterAI() {
	if (!isInterviewActive) return;

	// Enable mic
	isMicActive = true;
	const btn = document.getElementById('mic-btn');
	if (!btn) return;

	const icon = btn.querySelector('i');
	const muteOverlay = document.getElementById('mic-mute-overlay');

	btn.classList.add('bg-slate-700', 'ring-2', 'ring-green-500');
	if (icon) {
		icon.classList.remove('fa-microphone-slash');
		icon.classList.add('fa-microphone');
	}
	if (muteOverlay) {
		muteOverlay.classList.add('hidden');
	}

	updateStatus('Listening...', 'bg-green-500/20 text-green-400 border-green-500/50');
	hideThinkingIndicator();

	startAudioCapture();
	startVisualizer();
}


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

		// Only show thinking indicator if AI is not currently speaking
		// (thinking only makes sense when waiting for AI response)
		if (typeof isAISpeakingNow === 'function' && isAISpeakingNow()) {
			updateStatus('Muted', 'bg-slate-500/20 text-slate-400 border-slate-500/50');
			setAvatarState('talking');
		} else {
			updateStatus('Processing...', 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50');
			showThinkingIndicator();
			setAvatarState('thinking');
		}

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

	// Stop camera if active
	if (isCameraActive && cameraStream) {
		cameraStream.getTracks().forEach(track => track.stop());
		cameraStream = null;
		isCameraActive = false;
	}

	// Show grading screen
	showGradingScreen();

	// Tell server to end interview and start grading
	endInterviewConnection();
}


// Camera Toggle (Optional - Local Only)
let isCameraActive = false;
let cameraStream = null;

async function toggleCamera() {
	isCameraActive = !isCameraActive;
	const btn = document.getElementById('camera-btn');
	const icon = btn.querySelector('i');
	const cameraOffOverlay = document.getElementById('camera-off-overlay');
	const cameraContainer = document.getElementById('user-camera-container');
	const cameraFeed = document.getElementById('user-camera-feed');

	if (isCameraActive) {
		try {
			// Request camera access
			cameraStream = await navigator.mediaDevices.getUserMedia({
				video: {
					width: {ideal: 640},
					height: {ideal: 480},
					facingMode: 'user'
				}
			});

			// Display camera feed
			if (cameraFeed) {
				cameraFeed.srcObject = cameraStream;
			}

			// Update UI
			btn.classList.add('bg-slate-700', 'ring-2', 'ring-green-500');
			icon.classList.remove('fa-video-slash');
			icon.classList.add('fa-video');
			if (cameraOffOverlay) {
				cameraOffOverlay.classList.add('hidden');
			}
			if (cameraContainer) {
				cameraContainer.classList.remove('hidden');
			}

		} catch (err) {
			console.error('Camera access denied:', err);
			alert('Camera access denied. You can continue the interview without video.');
			isCameraActive = false;
		}
	} else {
		// Stop camera
		if (cameraStream) {
			cameraStream.getTracks().forEach(track => track.stop());
			cameraStream = null;
		}

		// Update UI
		btn.classList.remove('bg-slate-700', 'ring-2', 'ring-green-500');
		icon.classList.remove('fa-video');
		icon.classList.add('fa-video-slash');
		if (cameraOffOverlay) {
			cameraOffOverlay.classList.remove('hidden');
		}
		if (cameraContainer) {
			cameraContainer.classList.add('hidden');
		}
		if (cameraFeed) {
			cameraFeed.srcObject = null;
		}
	}
}



