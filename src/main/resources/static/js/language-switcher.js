// Language Switcher Handler
(function() {
    const STORAGE_KEY = 'ui_language';
    const DEFAULT_LANG = 'bg';

    // Initialize language from localStorage or default
    function getStoredLanguage() {
        return localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
    }

    function setStoredLanguage(lang) {
        localStorage.setItem(STORAGE_KEY, lang);
    }

    // Toggle dropdown visibility
    function toggleLanguageDropdown() {
        const dropdown = document.getElementById('lang-dropdown-menu');
        const arrow = document.getElementById('lang-dropdown-arrow');
        if (dropdown) {
            dropdown.classList.toggle('hidden');
            if (arrow) {
                arrow.classList.toggle('rotate-180');
            }
        }
    }

    // Change language and reload page
    function changeLanguage(lang) {
        setStoredLanguage(lang);
        // Navigate with lang parameter to trigger Spring's LocaleChangeInterceptor
        const url = new URL(window.location.href);
        url.searchParams.set('lang', lang);
        window.location.href = url.toString();
    }

    // Close dropdown when clicking outside
    function handleClickOutside(event) {
        const dropdown = document.getElementById('lang-dropdown');
        if (dropdown && !dropdown.contains(event.target)) {
            const menu = document.getElementById('lang-dropdown-menu');
            const arrow = document.getElementById('lang-dropdown-arrow');
            if (menu && !menu.classList.contains('hidden')) {
                menu.classList.add('hidden');
                if (arrow) {
                    arrow.classList.remove('rotate-180');
                }
            }
        }
    }

    // On page load, ensure cookie matches localStorage
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
        } else {
            // Check if cookie matches localStorage, if not redirect
            const cookieLang = getCookieLanguage();
            if (cookieLang && cookieLang !== storedLang) {
                // Cookie differs from localStorage, sync by redirecting
                changeLanguage(storedLang);
            } else if (!cookieLang) {
                // No cookie, set default
                changeLanguage(storedLang);
            }
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

    // Initialize
    document.addEventListener('DOMContentLoaded', function() {
        syncLanguageOnLoad();
        document.addEventListener('click', handleClickOutside);
    });

    // Expose functions globally
    window.toggleLanguageDropdown = toggleLanguageDropdown;
    window.changeLanguage = changeLanguage;
})();
