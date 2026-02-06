// Microphone availability checker for setup wizard
// Runs early to ensure user has a working microphone before starting interview

let micCheckInProgress = false;
let permissionDenied = false; // Track if user explicitly denied permission


// Check if microphone is available
async function checkMicrophoneAvailability() {
	if (micCheckInProgress) return;
	micCheckInProgress = true;
	permissionDenied = false;

	try {
		// Check if getUserMedia is supported
		if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
			showMicrophoneModal(false);
			micCheckInProgress = false;
			return;
		}

		// Check permission state if supported
		try {
			const permissionStatus = await navigator.permissions.query({name: 'microphone'});
			if (permissionStatus.state === 'denied') {
				permissionDenied = true;
				showMicrophoneModal(true);
				micCheckInProgress = false;
				return;
			}
		} catch (e) {
			// Permissions API not supported, continue with normal check
		}

		// Try to enumerate devices first (less intrusive)
		const devices = await navigator.mediaDevices.enumerateDevices();
		const audioInputs = devices.filter(device => device.kind === 'audioinput');

		if (audioInputs.length === 0) {
			showMicrophoneModal(false);
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
			// Check if it's a permission error
			if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
				permissionDenied = true;
				showMicrophoneModal(true);
			} else {
				showMicrophoneModal(false);
			}
			micCheckInProgress = false;
		}
	} catch (error) {
		console.error('Microphone check failed:', error);
		showMicrophoneModal(false);
		micCheckInProgress = false;
	}
}


// Show the microphone error modal
function showMicrophoneModal(isDenied) {
	const modal = document.getElementById('microphone-modal');
	const permissionNote = document.getElementById('mic-permission-note');
	
	if (modal) {
		modal.classList.remove('hidden');
		document.body.style.overflow = 'hidden';
		
		// Show/hide permission note based on denial
		if (permissionNote) {
			if (isDenied) {
				permissionNote.classList.remove('hidden');
			} else {
				permissionNote.classList.add('hidden');
			}
		}
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
	// Wait a moment for user to potentially plug in device or reset permissions
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
