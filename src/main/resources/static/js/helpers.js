// ── Theme ─────────────────────────────────────────────────────────────────

const applyTheme = function(theme) {
    const html = document.documentElement;
    const btn = document.querySelector('.theme-toggle');

    if (theme === 'light') {
        html.setAttribute('data-theme', 'light');
        if (btn) btn.innerHTML = '<i data-lucide="moon"></i>';
    } else {
        html.removeAttribute('data-theme');
        if (btn) btn.innerHTML = '<i data-lucide="sun"></i>';
    }

    if (window.lucide) lucide.createIcons();
}

const toggleTheme = function() {
    const nextTheme = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
    localStorage.setItem('theme', nextTheme);
    applyTheme(nextTheme);
}

applyTheme(localStorage.getItem('theme') || 'dark');
document.getElementById('theme-toggle')?.addEventListener('click', toggleTheme);

