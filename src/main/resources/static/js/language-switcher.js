// Language Switcher Handler - Simplified for Multi-Page Setup
(function () {
	const STORAGE_KEY = 'ui_language';
	const DEFAULT_LANG = 'bg';


	function getStoredLanguage() {
		return localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
	}


	function setStoredLanguage(lang) {
		localStorage.setItem(STORAGE_KEY, lang);
	}


	// Check if we're in a view where language switching should be disabled
	function isLanguageSwitchDisabled() {
		// Disable during active interview (check for interview view being active)
		const interviewView = document.getElementById('interview-view');
		if (interviewView && interviewView.classList.contains('active')) {
			return true;
		}
		// Also check if we're on the interview standalone page
		if (window.location.pathname === '/interview') {
			return true;
		}
		return false;
	}


	// Toggle dropdown visibility
	function toggleLanguageDropdown() {
		if (isLanguageSwitchDisabled()) return;

		const dropdown = document.getElementById('lang-dropdown-menu');
		const arrow = document.getElementById('lang-dropdown-arrow');
		if (dropdown) {
			dropdown.classList.toggle('hidden');
			if (arrow) {
				arrow.classList.toggle('rotate-180');
			}
		}
	}


	// Change language - simple page reload with lang param
	// Form data is preserved in HTTP session (handled by SetupController)
	function changeLanguage(lang) {
		if (lang !== 'en' && lang !== 'bg') return;

		const currentLang = getStoredLanguage();
		if (lang === currentLang) {
			closeDropdown();
			return;
		}

		if (isLanguageSwitchDisabled()) {
			closeDropdown();
			return;
		}

		setStoredLanguage(lang);

		// Navigate with lang parameter to trigger Spring's LocaleChangeInterceptor
		// Form data is preserved in HTTP session automatically
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


	function syncLanguageOnLoad() {
		const storedLang = getStoredLanguage();
		const currentUrl = new URL(window.location.href);
		const urlLang = currentUrl.searchParams.get('lang');

		// If URL has lang param, update localStorage and clean up URL
		if (urlLang && (urlLang === 'en' || urlLang === 'bg')) {
			setStoredLanguage(urlLang);
			currentUrl.searchParams.delete('lang');
			window.history.replaceState({}, '', currentUrl.toString());
		} else {
			// Check if cookie matches localStorage, if not redirect
			const cookieLang = getCookieLanguage();
			if (cookieLang && cookieLang !== storedLang) {
				changeLanguage(storedLang);
			} else if (!cookieLang && !isLanguageSwitchDisabled()) {
				// No cookie, set default
				changeLanguage(storedLang);
			}
		}

		updateDropdownState();
	}


	function updateDropdownState() {
		const button = document.querySelector('#lang-dropdown > button');
		if (!button) return;

		if (isLanguageSwitchDisabled()) {
			button.classList.add('opacity-50', 'cursor-not-allowed');
			button.classList.remove('hover:bg-slate-700');
		} else {
			button.classList.remove('opacity-50', 'cursor-not-allowed');
			button.classList.add('hover:bg-slate-700');
		}
	}


	// Initialize
	document.addEventListener('DOMContentLoaded', function () {
		syncLanguageOnLoad();
		document.addEventListener('click', handleClickOutside);
	});


	// Expose functions globally
	window.toggleLanguageDropdown = toggleLanguageDropdown;
	window.changeLanguage = changeLanguage;
})();
