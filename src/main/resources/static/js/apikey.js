// API Key Management for PROD mode
// Handles localStorage, validation, and modal display

const STORAGE_KEY = 'gemini_api_key';
const STORAGE_TIMESTAMP_KEY = 'gemini_key_validated_at';

let appMode = 'DEV'; // Will be set by checkAppMode()


// Check application mode on page load
async function checkAppMode() {
	try {
		const response = await fetch('/api/mode');
		const data = await response.json();
		appMode = data.mode;

		if (data.requiresUserKey) {
			// PROD mode - check for valid API key
			const storedKey = getStoredApiKey();
			if (!storedKey) {
				showApiKeyModal();
			} else {
				// Optionally validate stored key on load

			}
		} else {
			// DEV mode - hide modal if visible
			hideApiKeyModal();
		}
	} catch (error) {
		console.error('Failed to check app mode:', error);
		// Default to allowing access on error
	}
}


// Get stored API key from localStorage
function getStoredApiKey() {
	try {
		return localStorage.getItem(STORAGE_KEY);
	} catch (e) {
		console.error('Failed to read from localStorage:', e);
		return null;
	}
}


// Save API key to localStorage
function saveApiKey(apiKey) {
	try {
		localStorage.setItem(STORAGE_KEY, apiKey);
		localStorage.setItem(STORAGE_TIMESTAMP_KEY, Date.now().toString());
		return true;
	} catch (e) {
		console.error('Failed to save to localStorage:', e);
		return false;
	}
}


// Clear stored API key
function clearStoredApiKey() {
	try {
		localStorage.removeItem(STORAGE_KEY);
		localStorage.removeItem(STORAGE_TIMESTAMP_KEY);
	} catch (e) {
		console.error('Failed to clear localStorage:', e);
	}
}


// Show API key modal
function showApiKeyModal() {
	const modal = document.getElementById('apikey-modal');
	if (modal) {
		modal.classList.remove('hidden');
		document.body.style.overflow = 'hidden';
	}
}


// Hide API key modal
function hideApiKeyModal() {
	const modal = document.getElementById('apikey-modal');
	if (modal) {
		modal.classList.add('hidden');
		document.body.style.overflow = '';
	}
}


// Show rate limit modal
function showRateLimitModal() {
	const modal = document.getElementById('ratelimit-modal');
	if (modal) {
		modal.classList.remove('hidden');
		document.body.style.overflow = 'hidden';
	}
}


// Hide rate limit modal
function hideRateLimitModal() {
	const modal = document.getElementById('ratelimit-modal');
	if (modal) {
		modal.classList.add('hidden');
		document.body.style.overflow = '';
	}
}


// Clear API key and show modal (called when rate limited)
function clearApiKeyAndShowModal() {
	clearStoredApiKey();
	hideRateLimitModal();
	showApiKeyModal();

	// Clear the input field
	const input = document.getElementById('apikey-input');
	if (input) input.value = '';

	// Hide any previous messages
	hideApiKeyError();
	hideApiKeySuccess();
}


// Toggle tutorial video visibility and lazy load
function toggleTutorialVideo() {
	const container = document.getElementById('tutorial-video-container');
	const video = document.getElementById('tutorial-video');

	if (container.classList.contains('hidden')) {
		container.classList.remove('hidden');

		// Lazy load the video source
		const source = video.querySelector('source');
		if (source && source.dataset.src && !source.src) {
			source.src = source.dataset.src;
			video.load();
		}

		video.play().catch(() => {
		});
	} else {
		container.classList.add('hidden');
		video.pause();
	}
}


// Toggle API key visibility
function toggleApiKeyVisibility() {
	const input = document.getElementById('apikey-input');
	const icon = document.getElementById('apikey-visibility-icon');

	if (input.type === 'password') {
		input.type = 'text';
		icon.classList.remove('fa-eye');
		icon.classList.add('fa-eye-slash');
	} else {
		input.type = 'password';
		icon.classList.remove('fa-eye-slash');
		icon.classList.add('fa-eye');
	}
}


// Show error message
function showApiKeyError(message) {
	const errorDiv = document.getElementById('apikey-error');
	const errorText = document.getElementById('apikey-error-text');

	if (errorDiv && errorText) {
		errorText.textContent = message;
		errorDiv.classList.remove('hidden');
	}

	hideApiKeySuccess();
}


// Hide error message
function hideApiKeyError() {
	const errorDiv = document.getElementById('apikey-error');
	if (errorDiv) {
		errorDiv.classList.add('hidden');
	}
}


// Show success message
function showApiKeySuccess() {
	const successDiv = document.getElementById('apikey-success');
	if (successDiv) {
		successDiv.classList.remove('hidden');
	}
	hideApiKeyError();
}


// Hide success message
function hideApiKeySuccess() {
	const successDiv = document.getElementById('apikey-success');
	if (successDiv) {
		successDiv.classList.add('hidden');
	}
}


// Set button loading state
function setButtonLoading(isLoading) {
	const btn = document.getElementById('apikey-submit-btn');
	const icon = document.getElementById('apikey-submit-icon');
	const text = document.getElementById('apikey-submit-text');

	if (isLoading) {
		btn.disabled = true;
		icon.classList.remove('fa-check');
		icon.classList.add('fa-spinner', 'fa-spin');
		text.textContent = text.dataset.validating || 'Validating...';
	} else {
		btn.disabled = false;
		icon.classList.remove('fa-spinner', 'fa-spin');
		icon.classList.add('fa-check');
		text.textContent = text.dataset.original || 'Verify & Continue';
	}
}


// Get translated message from the page
function getI18nMessage(key, fallback) {
	// Try to get message from a hidden element with data-i18n attribute
	const element = document.querySelector(`[data-i18n="${key}"]`);
	if (element && element.textContent) {
		return element.textContent;
	}
	return fallback;
}


// Validate and save API key
async function validateAndSaveApiKey() {
	const input = document.getElementById('apikey-input');
	const apiKey = input.value.trim();

	if (!apiKey) {
		const errorMsg = getI18nMessage('apikey.error.required', 'Please enter an API key');
		showApiKeyError(errorMsg);
		return;
	}

	// Basic format check
	if (!apiKey.startsWith('AIza') || apiKey.length < 35) {
		const errorMsg = getI18nMessage('apikey.error.invalidFormat', 'Invalid API key format. Gemini API keys start with "AIza" and are about 39 characters.');
		showApiKeyError(errorMsg);
		return;
	}

	// Store original button text
	const text = document.getElementById('apikey-submit-text');
	if (!text.dataset.original) {
		text.dataset.original = text.textContent;
	}
	const validatingMsg = getI18nMessage('apikey.modal.validating', 'Validating...');
	text.dataset.validating = validatingMsg;

	setButtonLoading(true);
	hideApiKeyError();

	try {
		const response = await fetch('/api/validate-key', {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json'
			},
			body: JSON.stringify({apiKey: apiKey})
		});

		const data = await response.json();

		if (data.valid) {
			// Success! Save key and close modal
			saveApiKey(apiKey);
			showApiKeySuccess();

			// Wait a moment to show success, then close
			setTimeout(() => {
				hideApiKeyModal();
			}, 1000);
		} else {
			// Show error
			if (data.rateLimited) {
				const errorMsg = data.error || getI18nMessage('apikey.error.rateLimited', 'This API key has exceeded its quota.');
				showApiKeyError(errorMsg);
			} else {
				const errorMsg = data.error || getI18nMessage('apikey.error.invalid', 'Invalid API key. Please check and try again.');
				showApiKeyError(errorMsg);
			}
		}
	} catch (error) {
		console.error('API key validation error:', error);
		const errorMsg = getI18nMessage('apikey.error.network', 'Network error. Please try again.');
		showApiKeyError(errorMsg);
	} finally {
		setButtonLoading(false);
	}
}


// Handle rate limit errors from the application
function handleRateLimitError() {
	clearStoredApiKey();
	showRateLimitModal();
}


// Check if we need to show API key modal (for PROD mode)
function requiresApiKey() {
	return appMode === 'PROD' && !getStoredApiKey();
}


// Change language from within the modal
function changeLanguageInModal(lang) {
	// Use the existing language change function if available
	if (typeof changeLanguage === 'function') {
		changeLanguage(lang);
	} else {
		// Fallback: reload page with new language
		window.location.href = '/?lang=' + lang;
	}
}


// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
	checkAppMode();

	// Allow Enter key to submit
	const input = document.getElementById('apikey-input');
	if (input) {
		input.addEventListener('keypress', (e) => {
			if (e.key === 'Enter') {
				validateAndSaveApiKey();
			}
		});
	}
});


// Expose functions for global use
window.getStoredApiKey = getStoredApiKey;
window.clearStoredApiKey = clearStoredApiKey;
window.showApiKeyModal = showApiKeyModal;
window.hideApiKeyModal = hideApiKeyModal;
window.showRateLimitModal = showRateLimitModal;
window.handleRateLimitError = handleRateLimitError;
window.requiresApiKey = requiresApiKey;
window.clearApiKeyAndShowModal = clearApiKeyAndShowModal;
window.toggleTutorialVideo = toggleTutorialVideo;
window.toggleApiKeyVisibility = toggleApiKeyVisibility;
window.validateAndSaveApiKey = validateAndSaveApiKey;
window.changeLanguageInModal = changeLanguageInModal;
