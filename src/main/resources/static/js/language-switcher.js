// Language Switcher Handler - Preserves Current Form Step and Form Data
(function () {
	const STORAGE_KEY = 'ui_language';
	const STEP_STORAGE_KEY = 'current_form_step';
	const FORM_DATA_KEY = 'form_data_backup';
	const DEFAULT_LANG = 'bg';

	// Initialize language from localStorage or default
	function getStoredLanguage() {
		return localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
	}

	function setStoredLanguage(lang) {
		localStorage.setItem(STORAGE_KEY, lang);
	}

	// Check if we're in the interview view
	function isInInterviewView() {
		const interviewView = document.getElementById('interview-view');
		return interviewView && interviewView.classList.contains('active');
	}

	// Check if we're in the report view
	function isInReportView() {
		const reportView = document.getElementById('report-view');
		return reportView && reportView.classList.contains('active');
	}

	// Check if language switching should be disabled
	function isLanguageSwitchDisabled() {
		return isInInterviewView() || isInReportView();
	}

	// Toggle dropdown visibility
	function toggleLanguageDropdown() {
		// Don't open dropdown during interview/report
		if (isLanguageSwitchDisabled()) {
			return;
		}

		const dropdown = document.getElementById('lang-dropdown-menu');
		const arrow = document.getElementById('lang-dropdown-arrow');
		if (dropdown) {
			dropdown.classList.toggle('hidden');
			if (arrow) {
				arrow.classList.toggle('rotate-180');
			}
		}
	}

	// Save form data before language switch
	function saveFormData() {
		const formData = {};

		// Save text inputs
		const candidateName = document.getElementById('candidate-name');
		if (candidateName) formData.candidateName = candidateName.value;

		const customPosition = document.getElementById('custom-position');
		if (customPosition) formData.customPosition = customPosition.value;

		// Save select values
		const jobPosition = document.getElementById('job-position');
		if (jobPosition) formData.jobPosition = jobPosition.value;

		// Save radio buttons
		const difficulty = document.querySelector('input[name="difficulty"]:checked');
		if (difficulty) formData.difficulty = difficulty.value;

		const language = document.querySelector('input[name="language"]:checked');
		if (language) formData.interviewLanguage = language.value;

		const voice = document.querySelector('input[name="voice"]:checked');
		if (voice) formData.voice = voice.value;

		sessionStorage.setItem(FORM_DATA_KEY, JSON.stringify(formData));
	}

	// Restore form data after language switch
	function restoreFormData() {
		const saved = sessionStorage.getItem(FORM_DATA_KEY);
		if (!saved) return;

		sessionStorage.removeItem(FORM_DATA_KEY);

		try {
			const formData = JSON.parse(saved);

			// Restore text inputs
			const candidateName = document.getElementById('candidate-name');
			if (candidateName && formData.candidateName) {
				candidateName.value = formData.candidateName;
			}

			const customPosition = document.getElementById('custom-position');
			if (customPosition && formData.customPosition) {
				customPosition.value = formData.customPosition;
			}

			// Restore select values
			const jobPosition = document.getElementById('job-position');
			if (jobPosition && formData.jobPosition) {
				jobPosition.value = formData.jobPosition;
				// Show custom position input if needed
				if (formData.jobPosition === 'custom') {
					const customContainer = document.getElementById('custom-position-container');
					if (customContainer) customContainer.classList.remove('hidden');
				}
			}

			// Restore radio buttons
			if (formData.difficulty) {
				const difficultyRadio = document.querySelector(`input[name="difficulty"][value="${formData.difficulty}"]`);
				if (difficultyRadio) difficultyRadio.checked = true;
			}

			if (formData.interviewLanguage) {
				const languageRadio = document.querySelector(`input[name="language"][value="${formData.interviewLanguage}"]`);
				if (languageRadio) languageRadio.checked = true;
			}

			if (formData.voice) {
				const voiceRadio = document.querySelector(`input[name="voice"][value="${formData.voice}"]`);
				if (voiceRadio) voiceRadio.checked = true;
			}
		} catch (e) {
			console.error('Failed to restore form data:', e);
		}
	}

	// Change language - preserves current form step and data
	function changeLanguage(lang) {
		if (lang !== 'en' && lang !== 'bg') return;

		const currentLang = getStoredLanguage();
		if (lang === currentLang) {
			closeDropdown();
			return;
		}

		// Block during interview/report
		if (isLanguageSwitchDisabled()) {
			closeDropdown();
			return;
		}

		// Save current form step before reload (use window.currentFormStep for global access)
		const step = window.currentFormStep || 1;
		sessionStorage.setItem(STEP_STORAGE_KEY, step);

		// Save form data before reload
		saveFormData();

		setStoredLanguage(lang);

		// Navigate with lang parameter to trigger Spring's LocaleChangeInterceptor
		const url = new URL(window.location.href);
		url.searchParams.set('lang', lang);
		window.location.href = url.toString();
	}

	function closeDropdown() {
		const menu = document.getElementById('lang-dropdown-menu');
		const arrow = document.getElementById('lang-dropdown-arrow');
		if (menu) menu.classList.add('hidden');
		if (arrow) arrow.classList.remove('rotate-180');
	}

	// Close dropdown when clicking outside
	function handleClickOutside(event) {
		const dropdown = document.getElementById('lang-dropdown');
		if (dropdown && !dropdown.contains(event.target)) {
			closeDropdown();
		}
	}

	function getCookieLanguage() {
		const cookies = document.cookie.split(';');
		for (let cookie of cookies) {
			const [name, value] = cookie.trim().split('=');
			if (name === 'ui_lang') {
				return value;
			}
		}
		return null;
	}

	// Restore form step after language switch
	function restoreFormStep() {
		const savedStep = sessionStorage.getItem(STEP_STORAGE_KEY);
		if (savedStep) {
			sessionStorage.removeItem(STEP_STORAGE_KEY);
			const step = parseInt(savedStep, 10);
			if (!isNaN(step) && step >= 1 && step <= 3) {
				// Wait for navigation.js to fully initialize
				setTimeout(() => {
					// Set the global variable
					window.currentFormStep = step;

					// Call showStep if available
					if (typeof window.showStep === 'function') {
						window.showStep(step);
					} else if (typeof showStep === 'function') {
						showStep(step);
					}

					// Restore form data after step is shown
					setTimeout(() => {
						restoreFormData();
					}, 50);
				}, 150);
				return true;
			}
		}
		return false;
	}

	// Update dropdown button state based on current view
	function updateDropdownState() {
		const button = document.querySelector('#lang-dropdown > button');

		if (isLanguageSwitchDisabled()) {
			// Disable the button visually
			if (button) {
				button.classList.add('opacity-50', 'cursor-not-allowed');
				button.classList.remove('hover:bg-slate-700');
			}
		} else {
			// Enable the button
			if (button) {
				button.classList.remove('opacity-50', 'cursor-not-allowed');
				button.classList.add('hover:bg-slate-700');
			}
		}
	}

	// On page load, sync language and restore step
	function syncLanguageOnLoad() {
		const storedLang = getStoredLanguage();
		const currentUrl = new URL(window.location.href);
		const urlLang = currentUrl.searchParams.get('lang');

		// If URL has lang param, update localStorage
		if (urlLang && (urlLang === 'en' || urlLang === 'bg')) {
			setStoredLanguage(urlLang);
			// Clean up URL by removing lang param
			currentUrl.searchParams.delete('lang');
			window.history.replaceState({}, '', currentUrl.toString());
			// Restore form step and data after language change
			restoreFormStep();
		} else {
			// Check if cookie matches localStorage, if not redirect
			const cookieLang = getCookieLanguage();
			if (cookieLang && cookieLang !== storedLang) {
				// Cookie differs from localStorage, sync by redirecting
				if (!isLanguageSwitchDisabled()) {
					const step = window.currentFormStep || 1;
					sessionStorage.setItem(STEP_STORAGE_KEY, step);
					saveFormData();
				}
				changeLanguage(storedLang);
			} else if (!cookieLang) {
				// No cookie, set default
				if (!isLanguageSwitchDisabled()) {
					const step = window.currentFormStep || 1;
					sessionStorage.setItem(STEP_STORAGE_KEY, step);
					saveFormData();
				}
				changeLanguage(storedLang);
			}
		}

		// Update dropdown state
		updateDropdownState();
	}

	// Initialize
	document.addEventListener('DOMContentLoaded', function () {
		syncLanguageOnLoad();
		document.addEventListener('click', handleClickOutside);

		// Update dropdown state when view changes (for SPA-like navigation)
		const observer = new MutationObserver(function () {
			updateDropdownState();
		});

		// Observe changes to view sections
		const views = ['interview-view', 'report-view', 'setup-view'];
		views.forEach(viewId => {
			const view = document.getElementById(viewId);
			if (view) {
				observer.observe(view, {attributes: true, attributeFilter: ['class']});
			}
		});
	});

	// Expose functions globally
	window.toggleLanguageDropdown = toggleLanguageDropdown;
	window.changeLanguage = changeLanguage;
})();
