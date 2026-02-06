// Language Switcher Handler
// Language persistence is handled server-side by Spring's CookieLocaleResolver (ui_lang cookie, 365 days).
// This JS only handles: dropdown toggle, language change redirect, click-outside close, and interview disable.
(function () {

	// Disable language switching during active interview
	function isLanguageSwitchDisabled() {
		return window.location.pathname === '/interview';
	}


	function toggleLanguageDropdown() {
		if (isLanguageSwitchDisabled()) return;

		const dropdown = document.getElementById('lang-dropdown-menu');
		const arrow = document.getElementById('lang-dropdown-arrow');
		if (dropdown) {
			dropdown.classList.toggle('hidden');
			if (arrow) arrow.classList.toggle('rotate-180');
		}
	}


	// Reload page with ?lang= param to trigger Spring's LocaleChangeInterceptor
	function changeLanguage(lang) {
		if (lang !== 'en' && lang !== 'bg') return;
		if (isLanguageSwitchDisabled()) {
			closeDropdown();
			return;
		}

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


	// Clean up ?lang= param from URL after Spring has processed it
	function cleanUrlOnLoad() {
		const url = new URL(window.location.href);
		if (url.searchParams.has('lang')) {
			url.searchParams.delete('lang');
			window.history.replaceState({}, '', url.toString());
		}

		// Disable dropdown on interview page
		if (isLanguageSwitchDisabled()) {
			const button = document.querySelector('#lang-dropdown > button');
			if (button) {
				button.classList.add('opacity-50', 'cursor-not-allowed');
				button.classList.remove('hover:bg-slate-700');
			}
		}
	}


	document.addEventListener('DOMContentLoaded', function () {
		cleanUrlOnLoad();
		document.addEventListener('click', function (event) {
			const dropdown = document.getElementById('lang-dropdown');
			if (dropdown && !dropdown.contains(event.target)) closeDropdown();
		});
	});


	window.toggleLanguageDropdown = toggleLanguageDropdown;
	window.changeLanguage = changeLanguage;

	// Expose for modal language switchers (apikey-modal, microphone-modal, etc.)
	window.changeLanguageInModal = changeLanguage;
})();
