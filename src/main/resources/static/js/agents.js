const ADMIN_API = '/api/admin/agents';
const TABS = ['overview', 'instructions', 'workflow'];
let agents = [];
let chatProviders = [];
let selectedAgentId = null;

async function adminFetch(path, options) {
    const res = await fetch(`${ADMIN_API}/${path}`, options);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (res.status === 204) return null;
    return res.json();
}

function currentTab() {
    const hash = location.hash.replace('#', '');
    return TABS.includes(hash) ? hash : 'overview';
}

function switchTab(tab) {
    TABS.forEach(t => document.getElementById('tab-' + t).classList.toggle('hidden', t !== tab));
    document.querySelectorAll('.tab-link').forEach(link => link.classList.toggle('active', link.dataset.tab === tab));
}

async function loadTabsData() {
    if (!selectedAgentId) return;
    try {
        const agent = await adminFetch(`${selectedAgentId}`);
        fillOverview(agent);
        fillInstructions(agent);
        fillWorkflow(agent);
        setStatus('');
    } catch (err) {
        setStatus('Failed to load ' + tab + ': ' + err.message, true);
    }
}

async function initAdmin() {
    try {
        const response = await adminFetch('supported-chat-providers');
        chatProviders = Object.fromEntries(
            Object.entries(response)
                .sort((a, b) => a[0].localeCompare(b[0]))
                .map(([key, arrayValue]) => [key, [...arrayValue].sort((a, b) => a.localeCompare(b))])
        );
        renderChatProviders();
        switchTab(currentTab());
        await loadAdminAgents();

        if (agents.length > 0) {
            selectAgent(agents[0].agentId);
        } else {
            resetForm();
        }
    } catch (err) {
        setStatus('Failed to load admin data: ' + err.message, true);
        throw err;
    }
}

async function loadAdminAgents() {
    agents = await adminFetch('');
    const list = document.getElementById('admin-agent-list');
    list.innerHTML = '';
    if (!agents.length) {
        list.innerHTML = '<div class="conversation-item">No agent definitions.</div>';
        return;
    }
    for (const agent of agents) {
        const item = document.createElement('div');
        item.className = 'admin-agent-item';
        item.dataset.agentId = agent.agentId;
        item.innerHTML = '<strong>' + escapeHtml(agent.displayName) + '</strong>'
            + chatBadgeHtml(agent.chatProviderId, agent.model);
        item.onclick = () => selectAgent(agent.agentId);
        list.appendChild(item);
    }
    highlightSelectedAgent();
}

function highlightSelectedAgent() {
    document.querySelectorAll('#admin-agent-list .admin-agent-item').forEach(item => {
        item.classList.toggle('active', item.dataset.agentId === selectedAgentId);
    });
}

function selectAgent(agentId) {
    selectedAgentId = agentId;
    highlightSelectedAgent();
    loadTabsData();
}

function fillOverview(agent) {
    document.getElementById('agent-id').value = agent.agentId || '';
    document.getElementById('agent-enabled').checked = !!agent.details.enabled;
    document.getElementById('display-name').value = agent.details.displayName || '';
    document.getElementById('description').value = agent.details.description || '';
}

function fillInstructions(agent) {
    document.getElementById('agent-prompt').value = agent.chatProperties.agentPrompt || '';
    document.getElementById('supported-capabilities').value = (agent.workflowPolicy.supportedCapabilities || []).join('\n');
    document.getElementById('fallback-failed-to-process').value = agent.workflowPolicy.fallbackFailedToProcess || '';

    const format = agent.workflowPolicy.responseContract?.format || 'TEXT';
    document.getElementById('response-format').value = format;
    document.getElementById('response-min-length').value = agent.workflowPolicy.responseContract?.minLength ?? 0;
    document.getElementById('response-required-fields').value = (agent.workflowPolicy.responseContract?.requiredFields || []).join('\n');
    toggleResponseContractFields(format);
}

async function fetchAgentDiagram(agentId) {
    const res = await fetch(`${ADMIN_API}/${agentId}/workflowDiagram`, {
        headers: { 'Accept': 'text/plain' }
    });
    if (!res.ok) return '';
    return res.text();
}

async function fillWorkflow(agent) {
    document.getElementById('workflow-id').value = agent.workflowId || 'STANDARD';
    document.getElementById('chat-provider').value = agent.chatProperties.providerId || chatProviders[0]?.providerId || '';
    renderModelOptions(agent.chatProperties.model || '');
    document.getElementById('temperature').value = agent.chatProperties.temperature ?? 0.7;
    document.getElementById('memory-enabled').checked = !!agent.chatProperties.memoryEnabled;
    const diagram = await fetchAgentDiagram(agent.agentId);
    renderWorkflowDiagram(diagram || '', agent.agentId);
}

function toggleResponseContractFields(format) {
    const isJson = (format ?? document.getElementById('response-format').value) === 'JSON';
    document.getElementById('response-required-fields-row').classList.toggle('hidden', !isJson);
}

function resetForm() {
    document.getElementById('agent-form').reset();
    selectedAgentId = null;
    highlightSelectedAgent();
    renderChatProviders();
    renderModelOptions();
    document.getElementById('workflow-id').value = 'STANDARD';
    document.getElementById('temperature').value = 0.7;
    document.getElementById('fallback-failed-to-process').value = "I couldn't process that safely right now. Please try again.";
    renderWorkflowDiagram('', '');
    setStatus('');
}

async function renderWorkflowDiagram(diagram, agentId) {
    const container = document.getElementById('workflow-diagram');
    if (!diagram) {
        container.textContent = 'Select an agent to view its workflow.';
        return;
    }
    if (!window.mermaid) {
        container.textContent = diagram;
        return;
    }
    mermaid.initialize({
        startOnLoad: false,
        theme: document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'
    });

    const id = 'workflow-diagram-' + agentId;
    const rendered = await mermaid.render(id, diagram);
    container.innerHTML = rendered.svg;

    const svgEl = container.querySelector('svg');
    if (!svgEl) return;

    const pz = svgPanZoom(svgEl, {
        zoomEnabled: true,
        controlIconsEnabled: false,
        fit: true,
        center: true,
        minZoom: 0.3,
        maxZoom: 10,
        dblClickZoomEnabled: true,
        mouseWheelZoomEnabled: true,
        preventMouseEventsDefault: true,
    });
    addToolbar(container, pz);
}

function addToolbar(container, pz) {
    const toolbar = document.createElement('div');
    toolbar.className = 'diagram-toolbar';
    toolbar.innerHTML = `
        <a class="diagram-btn" data-action="zoom-in" aria-label="Zoom in"><i data-lucide="zoom-in"></i></a>
        <a class="diagram-btn" data-action="reset" aria-label="Reset view"><i data-lucide="rotate-ccw"></i></a>
        <a class="diagram-btn" data-action="zoom-out" aria-label="Zoom out"><i data-lucide="zoom-out"></i></a>
        <span class="diagram-hint">Scroll · Drag</span>
    `;

    toolbar.querySelectorAll('[data-action]').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const action = btn.dataset.action;
            if (action === 'zoom-in') pz.zoomIn();
            else if (action === 'zoom-out') pz.zoomOut();
            else if (action === 'reset') {
                pz.resetZoom();
                pz.center();
            }
        });
    });

    container.appendChild(toolbar);
    if (window.lucide) lucide.createIcons();
}

function renderChatProviders() {
    const select = document.getElementById('chat-provider');
    select.innerHTML = Object.keys(chatProviders)
        .map(option => {
            const val = typeof option === 'string' ? option : option.providerId;
            return '<option value="' + escapeHtml(val) + '">' + escapeHtml(val) + '</option>';
        })
        .join('');
}

function renderModelOptions(selectedModel) {
    const chatProvider = document.getElementById('chat-provider').value || chatProviders[0]?.providerId || '';
    const models = chatProviders[chatProvider] || [];
    const select = document.getElementById('model');
    select.innerHTML = models
        .map(model => '<option value="' + escapeHtml(model) + '">' + escapeHtml(model) + '</option>')
        .join('');

    if (selectedModel && !models.includes(selectedModel)) {
        select.insertAdjacentHTML('afterbegin', '<option value="' + escapeHtml(selectedModel) + '">' + escapeHtml(selectedModel) + ' (saved)</option>');
    }
    select.value = selectedModel || models[0] || '';
}

function lines(id) {
    return document.getElementById(id).value.split('\n').map(v => v.trim());
}

function nonEmptyLines(id) {
    return lines(id).filter(v => v.length > 0);
}

function readForm() {
    return {
        agentId: selectedAgentId || null,
        workflowId: document.getElementById('workflow-id').value,
        details: {
            enabled: document.getElementById('agent-enabled').checked,
            displayName: document.getElementById('display-name').value.trim(),
            description: document.getElementById('description').value.trim(),
        },
        chatProperties: {
            providerId: document.getElementById('chat-provider').value.trim(),
            model: document.getElementById('model').value.trim(),
            agentPrompt: document.getElementById('agent-prompt').value.trim(),
            temperature: Number(document.getElementById('temperature').value),
            memoryEnabled: document.getElementById('memory-enabled').checked,
        },
        workflowPolicy: {
            supportedCapabilities: lines('supported-capabilities'),
            fallbackFailedToProcess: document.getElementById('fallback-failed-to-process').value.trim(),
            responseContract: {
                format: document.getElementById('response-format').value,
                requiredFields: document.getElementById('response-format').value === 'JSON' ?
                    nonEmptyLines('response-required-fields') : [],
                minLength: Number(document.getElementById('response-min-length').value) || 0,

            }
        },
    };
}

async function saveAgent(e) {
    e.preventDefault();
    const agent = readForm();
    const exists = agents.some(a => a.agentId === agent.agentId);
    const saved = await adminFetch(exists ? `${agent.agentId}` : '', {
        method: exists ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agent),
    });
    selectedAgentId = saved.agentId;
    await loadAdminAgents();
    await loadTabsData();
    setStatus('Saved. It may take some time for runtime agent changes to take effect.');
}

async function deleteAgent() {
    if (!selectedAgentId || !confirm('Delete agent: ' + selectedAgentId + '?')) return;
    await adminFetch(selectedAgentId, { method: 'DELETE' });
    selectedAgentId = null;
    await loadAdminAgents();
    resetForm();
    setStatus('Deleted. It may take some time for runtime agent changes to take effect.');
}

function setStatus(message, error) {
    const el = document.getElementById('admin-status');
    el.textContent = message;
    el.className = 'admin-status' + (error ? ' error-note' : '');
}

function escapeHtml(text) {
    if (text == null) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

document.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) lucide.createIcons();
    document.getElementById('agent-form').addEventListener('submit', saveAgent);
    document.getElementById('chat-provider').addEventListener('change', () => renderModelOptions());
    document.getElementById('response-format').addEventListener('change', (e) => toggleResponseContractFields(e.target.value));
    document.getElementById('delete-agent-btn').addEventListener('click', deleteAgent);
    document.getElementById('new-agent-btn').addEventListener('click', resetForm);
    window.addEventListener('hashchange', () => {
        loadTabsData().then(switchTab(currentTab()));
    });
    initAdmin();
});
