// Push-to-Talk Module
// Manages PTT mode, keyboard binding, and the settings modal.
// Relies on globals from audio-processor.js (stompClient, isConnected, isAISpeaking,
// startAudioCapture, stopAudioCaptureOnly, sendMicOffSignal) and interview.js
// (isMicActive, isInterviewActive, startVisualizer, stopVisualizer, updateStatus,
// showThinkingIndicator, hideThinkingIndicator, setAvatarState, toggleMic).

let isPttMode = false;
let pttKeyConfig = { ctrl: true, shift: false, alt: false, key: 'd', display: 'Ctrl+D' };
let pttKeyIsDown = false;
let pttAudioStarted = false;
let pttInitializing = false;

const PTT_COMBOS = [
	{ ctrl: true,  shift: false, alt: false, key: 'd',  display: 'Ctrl+D'       },
	{ ctrl: true,  shift: false, alt: false, key: ' ',  display: 'Ctrl+Space'   },
	{ ctrl: true,  shift: false, alt: false, key: 'm',  display: 'Ctrl+M'       },
	{ ctrl: true,  shift: true,  alt: false, key: 'm',  display: 'Ctrl+Shift+M' },
	{ ctrl: true,  shift: true,  alt: false, key: 'd',  display: 'Ctrl+Shift+D' },
	{ ctrl: false, shift: false, alt: false, key: 'F9', display: 'F9'           },
];

function initPtt() {
	loadPttSettings();
	document.addEventListener('keydown', handlePttKeyDown);
	document.addEventListener('keyup', handlePttKeyUp);
	window.addEventListener('blur', handleWindowBlur);
}

function loadPttSettings() {
	try {
		const saved = localStorage.getItem('ptt_settings');
		if (saved) {
			const p = JSON.parse(saved);
			if (typeof p.isPttMode === 'boolean') isPttMode = p.isPttMode;
			if (p.keyConfig && PTT_COMBOS.some(c => c.display === p.keyConfig.display)) {
				pttKeyConfig = p.keyConfig;
			}
		}
	} catch (e) {}
	updatePttUI();
}

function savePttSettings() {
	try {
		localStorage.setItem('ptt_settings', JSON.stringify({ isPttMode, keyConfig: pttKeyConfig }));
	} catch (e) {}
}

// ─── Key event handlers ───────────────────────────────────────────────────────

function handlePttKeyDown(e) {
	if (!isPttMode || !isInterviewActive) return;
	if (e.repeat) return;
	if (!matchesPttKey(e)) return;
	e.preventDefault();
	if (pttKeyIsDown) return;
	pttKeyIsDown = true;
	activatePtt();
}

function handlePttKeyUp(e) {
	if (!isPttMode) return;
	if (!matchesPttKey(e)) return;
	e.preventDefault();
	if (!pttKeyIsDown) return;
	pttKeyIsDown = false;
	deactivatePtt();
}

function handleWindowBlur() {
	// Synthetic keyup when window loses focus while key is held
	if (isPttMode && pttKeyIsDown) {
		pttKeyIsDown = false;
		deactivatePtt();
	}
}

function matchesPttKey(e) {
	const k = pttKeyConfig;
	if (e.ctrlKey !== k.ctrl || e.shiftKey !== k.shift || e.altKey !== k.alt) return false;
	return e.key === k.key || e.key.toLowerCase() === k.key.toLowerCase();
}

// ─── PTT activation / deactivation ───────────────────────────────────────────

async function activatePtt() {
	if (!isInterviewActive) return;

	// If AI is currently speaking, show "queued" state but don't start recording.
	// The icon still changes so user knows the key is registered.
	if (typeof isAISpeakingNow === 'function' && isAISpeakingNow()) {
		setPttMicRing(true); // visual feedback — key is held, waiting for AI to finish
		return;
	}

	// First press: initialise the audio pipeline (kept alive between presses)
	if (!pttAudioStarted && !pttInitializing) {
		pttInitializing = true;
		try {
			await startAudioCapture();
			pttAudioStarted = true;
		} catch (err) {
			console.error('PTT: audio capture failed', err);
			pttInitializing = false;
			pttKeyIsDown = false;
			return;
		}
		pttInitializing = false;
	} else if (pttInitializing) {
		// A concurrent init is running — let it finish and handle isMicActive there
		return;
	}

	// Key may have been released while getUserMedia was in progress
	if (!pttKeyIsDown) {
		if (isMicActive) deactivatePtt();
		return;
	}

	isMicActive = true;
	if (typeof startVisualizer === 'function') startVisualizer();
	if (typeof hideThinkingIndicator === 'function') hideThinkingIndicator();
	if (typeof updateStatus === 'function') {
		updateStatus('Recording...', 'bg-green-500/20 text-green-400 border-green-500/50');
	}
	setPttMicRing(true);
}

function deactivatePtt() {
	const wasRecording = isMicActive;

	// Always revert button to idle keyboard state regardless of whether we were recording
	setPttMicRing(false);

	if (!wasRecording) {
		// Key released but we were never actually recording (AI was speaking when key was pressed,
		// or key was released before async stream init finished). Just restore the idle PTT status.
		if (typeof isAISpeakingNow === 'function' && isAISpeakingNow()) {
			if (typeof updateStatus === 'function') {
				updateStatus('AI Speaking', 'bg-blue-500/20 text-blue-400 border-blue-500/50');
			}
		} else {
			if (typeof updateStatus === 'function') {
				updateStatus('Hold ' + pttKeyConfig.display + ' to speak', 'bg-slate-700/50 text-slate-400 border-slate-600/50');
			}
		}
		return;
	}

	isMicActive = false;
	if (typeof stopVisualizer === 'function') stopVisualizer();
	if (typeof sendMicOffSignal === 'function') sendMicOffSignal();
	if (typeof showThinkingIndicator === 'function') showThinkingIndicator();
	if (typeof setAvatarState === 'function') setAvatarState('thinking');
	if (typeof updateStatus === 'function') {
		updateStatus('Processing...', 'bg-yellow-500/20 text-yellow-400 border-yellow-500/50');
	}
}

// ─── Mode switching ───────────────────────────────────────────────────────────

function togglePttMode(enable) {
	if (enable === isPttMode) return;

	if (enable) {
		// Free talk → PTT: if mic is active we must tell Gemini the turn ended, otherwise
		// its VAD will fire after a few seconds of silence and trigger an AI response that
		// may hit the interview-concluding detector. We send audioStreamEnd WITHOUT the
		// timestamp injection (via /mode-switch) so the AI doesn't get an elapsed-time cue.
		if (isMicActive) {
			isMicActive = false;
			if (typeof stopAudioCaptureOnly === 'function') stopAudioCaptureOnly();
			if (typeof stompClient !== 'undefined' && stompClient &&
				typeof isConnected !== 'undefined' && isConnected) {
				stompClient.send('/app/interview/mode-switch', {}, '');
			}
			pttAudioStarted = false;
		}
		isPttMode = true;

	} else {
		// PTT → Free talk: release key state, destroy PTT stream quietly
		if (pttKeyIsDown) {
			pttKeyIsDown = false;
			if (isMicActive) {
				isMicActive = false;
				if (typeof sendMicOffSignal === 'function') sendMicOffSignal();
			}
		}
		if (pttAudioStarted) {
			pttAudioStarted = false;
			if (typeof stopAudioCaptureOnly === 'function') stopAudioCaptureOnly();
		}
		isPttMode = false;
	}

	savePttSettings();
	updatePttUI();
}

function setPttKeyCombo(config) {
	pttKeyConfig = config;
	savePttSettings();
	updatePttKeyDisplay();
	renderKeyComboList();
}

// ─── Cleanup (called on interview end) ───────────────────────────────────────

function cleanupPtt() {
	pttKeyIsDown = false;
	pttAudioStarted = false;
	pttInitializing = false;
	setPttMicRing(false);
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

function updatePttUI() {
	// Drive the custom JS toggle visuals (no peer-* Tailwind needed)
	const track = document.getElementById('ptt-toggle-track');
	const thumb = document.getElementById('ptt-toggle-thumb');
	if (track) track.style.backgroundColor = isPttMode ? '#2563eb' : '#374151';
	if (thumb) thumb.style.transform = isPttMode ? 'translateX(20px)' : 'translateX(0px)';

	const pttSection = document.getElementById('ptt-key-section');
	if (pttSection) pttSection.classList.toggle('hidden', !isPttMode);

	const freeTalkSection = document.getElementById('ptt-freetalk-section');
	if (freeTalkSection) freeTalkSection.classList.toggle('hidden', isPttMode);

	updatePttKeyDisplay();
	updatePttMicArea();
}

function updatePttKeyDisplay() {
	const el = document.getElementById('ptt-current-key');
	if (el) el.textContent = pttKeyConfig.display;

	const hint = document.getElementById('ptt-hint');
	if (hint) {
		if (isPttMode) {
			hint.textContent = 'Hold ' + pttKeyConfig.display;
			hint.classList.remove('hidden');
		} else {
			hint.classList.add('hidden');
		}
	}
}

function updatePttMicArea() {
	const micBtn = document.getElementById('mic-btn');
	const micIcon = micBtn ? micBtn.querySelector('i') : null;
	const pttIndicator = document.getElementById('ptt-mic-indicator');
	const micMuteOverlay = document.getElementById('mic-mute-overlay');

	if (isPttMode) {
		if (micBtn) {
			micBtn.onclick = null;
			micBtn.disabled = true;
			micBtn.classList.remove('hover:bg-slate-700', 'bg-slate-700', 'ring-2', 'ring-green-500',
				'ring-offset-2', 'ring-offset-black');
			micBtn.classList.add('opacity-50', 'cursor-not-allowed');
		}
		if (micIcon) {
			micIcon.classList.remove('fa-microphone', 'fa-microphone-slash');
			micIcon.classList.add('fa-keyboard');
		}
		if (micMuteOverlay) micMuteOverlay.classList.add('hidden');
		if (pttIndicator) pttIndicator.classList.remove('hidden');
	} else {
		if (micBtn) {
			micBtn.onclick = toggleMic;
			micBtn.disabled = false;
			micBtn.classList.remove('opacity-50', 'cursor-not-allowed', 'ring-2', 'ring-green-500',
				'ring-offset-2', 'ring-offset-black');
			micBtn.classList.add('hover:bg-slate-700');
		}
		if (micIcon) {
			micIcon.classList.remove('fa-keyboard', 'fa-microphone');
			micIcon.classList.add('fa-microphone-slash');
		}
		if (micMuteOverlay) micMuteOverlay.classList.remove('hidden');
		if (pttIndicator) pttIndicator.classList.add('hidden');
	}
}

// Visual feedback while key is held / released
function setPttMicRing(active) {
	const micBtn = document.getElementById('mic-btn');
	const micIcon = micBtn ? micBtn.querySelector('i') : null;
	if (!micBtn || !isPttMode) return;

	if (active) {
		micBtn.classList.add('ring-2', 'ring-green-500', 'ring-offset-2', 'ring-offset-black');
		micBtn.classList.remove('opacity-50');
		if (micIcon) {
			micIcon.classList.remove('fa-keyboard');
			micIcon.classList.add('fa-microphone');
		}
	} else {
		micBtn.classList.remove('ring-2', 'ring-green-500', 'ring-offset-2', 'ring-offset-black');
		micBtn.classList.add('opacity-50');
		if (micIcon) {
			micIcon.classList.remove('fa-microphone');
			micIcon.classList.add('fa-keyboard');
		}
	}
}

// ─── Modal ────────────────────────────────────────────────────────────────────

function openPttModal() {
	const modal = document.getElementById('ptt-settings-modal');
	if (!modal) return;
	modal.classList.remove('hidden');
	modal.classList.add('flex');
	updatePttUI();
	if (isPttMode) renderKeyComboList();
}

function closePttModal() {
	const modal = document.getElementById('ptt-settings-modal');
	if (!modal) return;
	modal.classList.add('hidden');
	modal.classList.remove('flex');
}

function handlePttModalBackdrop(e) {
	if (e.target === document.getElementById('ptt-settings-modal')) closePttModal();
}

function renderKeyComboList() {
	const list = document.getElementById('ptt-combo-list');
	if (!list) return;
	list.innerHTML = '';
	PTT_COMBOS.forEach(function(combo) {
		const isActive = combo.display === pttKeyConfig.display;
		const btn = document.createElement('button');
		btn.type = 'button';
		btn.className = 'w-full flex items-center justify-between px-4 py-3 rounded-xl border transition-all duration-150 ' +
			(isActive
				? 'bg-blue-600/20 border-blue-500/50 text-blue-200'
				: 'bg-slate-800/60 border-slate-700/50 text-slate-300 hover:bg-slate-700/60 hover:border-slate-600/60 hover:text-white');
		btn.innerHTML =
			'<span class="font-mono text-sm font-bold tracking-wide">' + combo.display + '</span>' +
			(isActive
				? '<span class="flex items-center gap-1.5 text-blue-400 text-xs font-medium"><i class="fa-solid fa-check text-xs"></i>active</span>'
				: '<span class="text-slate-600 text-xs">select</span>');
		btn.addEventListener('click', function() { setPttKeyCombo(combo); });
		list.appendChild(btn);
	});
}
