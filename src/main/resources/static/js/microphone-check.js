// Microphone availability checker for setup wizard
// Runs early to ensure user has a working microphone before starting interview

let micCheckInProgress = false;


// Check if microphone is available
async function checkMicrophoneAvailability() {
	if (micCheckInProgress) return;
	micCheckInProgress = true;

	try {
		// Check if getUserMedia is supported
		if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
			showMicrophoneModal();
			micCheckInProgress = false;
			return;
		}

		// Try to enumerate devices first (less intrusive)
		const devices = await navigator.mediaDevices.enumerateDevices();
		const audioInputs = devices.filter(device => device.kind === 'audioinput');

		if (audioInputs.length === 0) {
			showMicrophoneModal();
			micCheckInProgress = false;
			return;
		}

		// Actually test microphone access with a quick request
		try {
			const stream = await navigator.mediaDevices.getUserMedia({audio: true});
			// Success! Stop the stream immediately
			stream.getTracks().forEach(track => track.stop());
			micCheckInProgress = false;
			// Microphone is available, do nothing
		} catch (error) {
			// Permission denied or no microphone
			console.warn('Microphone access denied or unavailable:', error);
			showMicrophoneModal();
			micCheckInProgress = false;
		}
	} catch (error) {
		console.error('Microphone check failed:', error);
		showMicrophoneModal();
		micCheckInProgress = false;
	}
}


// Show the microphone error modal
function showMicrophoneModal() {
	const modal = document.getElementById('microphone-modal');
	if (modal) {
		modal.classList.remove('hidden');
		document.body.style.overflow = 'hidden';
	}
}


// Hide the microphone error modal
function hideMicrophoneModal() {
	const modal = document.getElementById('microphone-modal');
	if (modal) {
		modal.classList.add('hidden');
		document.body.style.overflow = '';
	}
}


// Retry microphone check (called from "Try Again" button)
async function retryMicrophoneCheck() {
	hideMicrophoneModal();
	// Wait a moment for user to potentially plug in device
	await new Promise(resolve => setTimeout(resolve, 500));
	await checkMicrophoneAvailability();
}


// Auto-check on page load (this file is only loaded on setup pages via Thymeleaf conditional)
document.addEventListener('DOMContentLoaded', () => {
	setTimeout(() => {
		checkMicrophoneAvailability();
	}, 500);
});


// Expose functions globally
window.checkMicrophoneAvailability = checkMicrophoneAvailability;
window.retryMicrophoneCheck = retryMicrophoneCheck;
window.showMicrophoneModal = showMicrophoneModal;
window.hideMicrophoneModal = hideMicrophoneModal;
