// Theme Switcher (default: light, optional dark)
(function () {
	const STORAGE_KEY = 'ui_theme_preference';
	const THEME_LIGHT = 'light';
	const THEME_DARK = 'dark';
	const ACTION_SWITCH_TO_DARK = 'Switch to dark theme';
	const ACTION_SWITCH_TO_LIGHT = 'Switch to light theme';

	function readStoredTheme() {
		try {
			const stored = localStorage.getItem(STORAGE_KEY);
			return stored === THEME_DARK || stored === THEME_LIGHT ? stored : null;
		} catch (e) {
			console.error('Failed to read theme from localStorage:', e);
			return null;
		}
	}

	function getCurrentTheme() {
		return document.documentElement.classList.contains('theme-dark') ? THEME_DARK : THEME_LIGHT;
	}

	function updateThemeToggle(theme) {
		const toggleButton = document.getElementById('theme-toggle-btn');
		if (!toggleButton) return;

		const isDark = theme === THEME_DARK;
		const actionLabel = isDark ? ACTION_SWITCH_TO_LIGHT : ACTION_SWITCH_TO_DARK;

		toggleButton.setAttribute('aria-pressed', String(isDark));
		toggleButton.setAttribute('aria-label', actionLabel);
		toggleButton.setAttribute('title', actionLabel);
	}

	function applyTheme(theme, persist) {
		const resolvedTheme = theme === THEME_DARK ? THEME_DARK : THEME_LIGHT;
		const root = document.documentElement;

		root.classList.toggle('theme-light', resolvedTheme === THEME_LIGHT);
		root.classList.toggle('theme-dark', resolvedTheme === THEME_DARK);
		root.setAttribute('data-theme', resolvedTheme);

		if (document.body) {
			document.body.setAttribute('data-theme', resolvedTheme);
		}

		if (persist) {
			try {
				localStorage.setItem(STORAGE_KEY, resolvedTheme);
			} catch (e) {
				console.error('Failed to save theme to localStorage:', e);
			}
		}

		updateThemeToggle(resolvedTheme);
	}

	function toggleTheme() {
		const nextTheme = getCurrentTheme() === THEME_DARK ? THEME_LIGHT : THEME_DARK;
		applyTheme(nextTheme, true);
	}

	document.addEventListener('DOMContentLoaded', function () {
		const storedTheme = readStoredTheme();
		const initialTheme = storedTheme || getCurrentTheme() || THEME_LIGHT;
		applyTheme(initialTheme, false);
	});

	window.toggleTheme = toggleTheme;
})();
