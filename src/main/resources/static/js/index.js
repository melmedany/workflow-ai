
// ── INDEX ───────────────────────────────────────────────────────────────────

const loadAgents = async function() {
    const listEl = document.getElementById('agents-list');

    try {
        const res = await fetch('/api/agents/');
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const agents = await res.json();

        if (!agents || agents.length === 0) {
            listEl.innerHTML = '<li>No available agents currently.</li>';
            return;
        }

        listEl.innerHTML = '';
        for (const agent of agents) {
            const id = typeof agent.id === 'string' ? agent.id : agent.id?.name;
            const name = agent.displayName || id || 'Agent';
            const desc = agent.description || '';
            const tags = Array.isArray(agent.tags) ? agent.tags.join(' · ') : '';
            const llmBadge = llmBadgeHtml(agent.llmProviderId, agent.model);

            const li = document.createElement('li');
            li.innerHTML =
                '<a class="agent-card" href="/chat.html?agentId=' + encodeURIComponent(id) + '">' +
                '<div class="agent-avatar">' + name.charAt(0) + '</div>' +
                '<div class="agent-info">' +
                '<div class="agent-name-row"><h2>' + name + '</h2>' + llmBadge + '</div>' +
                '<p>' + desc + '</p>' +
                (tags ? '<span class="agent-tag">' + tags + '</span>' : '') +
                '</div>' +
                '<div class="agent-card-action"><i data-lucide="arrow-right"></i></div>' +
                '</a>';
            listEl.appendChild(li);
        }
        if (window.lucide) lucide.createIcons();
    } catch (err) {
        console.error('Failed to load agents:', err);
        listEl.innerHTML = '<li class="error">Failed to load agents: ' + err.message + '</li>';
    }
}
document.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) lucide.createIcons();
    loadAgents();
});