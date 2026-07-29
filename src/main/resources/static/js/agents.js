const ADMIN_API = '/api/admin/agents';
let agents = [];
let chatProviders = [];

async function adminFetch(path, options) {
    const res = await fetch(`${ADMIN_API}/${path}`, options);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (res.status === 204) return null;
    return res.json();
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
        await loadAdminAgents();

        if (agents.length > 0) {
            const defaultAgent = agents[0];
            const diagram = await fetchAgentDiagram(defaultAgent.agentId);
            fillForm(defaultAgent, diagram);
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
    if (!agents.length) {
        list.innerHTML = '<div class="conversation-item">No agent definitions.</div>';
        return;
    }
    list.innerHTML = '';
    for (const agent of agents) {
        const item = document.createElement('div');
        item.className = `admin-agent-item ${agent.agentId === agents[0].agentId ? 'active' : ''}`;
        item.innerHTML = '<strong>' + escapeHtml(agent.details.displayName) + '</strong>'
            + chatBadgeHtml(agent.chatProperties?.providerId, agent.chatProperties?.model);
        item.onclick = async () => {
            list.querySelectorAll('.active').forEach(i => i.classList.remove('active'));
            const diagram = await fetchAgentDiagram(agent.agentId);
            item.classList.add('active');
            fillForm(agent, diagram);
        };
        list.appendChild(item);
    }
}

async function fetchAgentDiagram(agentId) {
    const res = await fetch(`${ADMIN_API}/${agentId}/workflowDiagram`, {
        headers: { 'Accept': 'text/plain' }
    });
    if (!res.ok) return '';
    return res.text();
}

function fillForm(agent, agentWorkflowDiagram) {
    document.getElementById('agent-id').value = agent.agentId;
    document.getElementById('agent-enabled').checked = agent.details.enabled;
    document.getElementById('display-name').value = agent.details.displayName || '';
    document.getElementById('description').value = agent.details.description || '';
    document.getElementById('chat-provider').value = agent.chatProperties.providerId || chatProviders[0]?.providerId || '';
    renderModelOptions(agent.chatProperties.model || '');
    document.getElementById('temperature').value = agent.chatProperties.temperature ?? 0.7;
    document.getElementById('memory-enabled').checked = !!agent.chatProperties.memoryEnabled;
    document.getElementById('agent-prompt').value = (agent.chatProperties.agentPrompt);
    document.getElementById('supported-capabilities').value = (agent.workflowPolicyProperties.supportedCapabilities || []).join('\n');
    document.getElementById('fallback-failed-to-process').value = agent.workflowPolicyProperties.fallbackFailedToProcess || '';
    fillResponseContract(agent.workflowPolicyProperties.responseContract);
    renderWorkflowDiagram(agentWorkflowDiagram || '', agent.agentId);
    setStatus('');
}

function fillResponseContract(responseContract) {
    const format = responseContract?.format || 'TEXT';
    document.getElementById('response-format').value = format;
    document.getElementById('response-min-length').value = responseContract?.minLength ?? 0;
    document.getElementById('response-required-fields').value = (responseContract?.requiredFields || []).join('\n');
    toggleResponseContractFields(format);
}

function toggleResponseContractFields(format) {
    const isJson = (format ?? document.getElementById('response-format').value) === 'JSON';
    document.getElementById('response-required-fields-row').classList.toggle('hidden', !isJson);
}

function resetForm() {
    document.getElementById('agent-form').reset();
    renderChatProviders();
    renderModelOptions();
    document.getElementById('temperature').value = 0.7;
    document.getElementById('fallback-failed-to-process').value = "I couldn't process that safely right now. Please try again.";
    fillResponseContract(null);
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
        agentId: document.getElementById('agent-id').value,
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
        workflowPolicyProperties: {
            supportedCapabilities: lines('supported-capabilities'),
            fallbackFailedToProcess: document.getElementById('fallback-failed-to-process').value.trim(),
            responseContract: readResponseContract(),
        },
    };
}

function readResponseContract() {
    const format = document.getElementById('response-format').value;
    return {
        format: format,
        requiredFields: format === 'JSON' ? nonEmptyLines('response-required-fields') : [],
        minLength: Number(document.getElementById('response-min-length').value) || 0,
    };
}

async function saveAgent(e) {
    e.preventDefault();
    const agent = readForm();
    const exists = agents.some(a => a.agentId === agent.agentId);
    await adminFetch(exists ? `${agent.agentId}` : '', {
        method: exists ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(agent),
    });
    await loadAdminAgents();
    const diagram = await fetchAgentDiagram(agent.agentId);
    fillForm(agent, diagram);
    setStatus('Saved. It may take some time for runtime agent changes to take effect.');
}

async function deleteAgent() {
    const agentId = document.getElementById('agent-id').value;
    if (!agentId || !confirm('Delete agent: ' + agentId + '?')) return;
    await adminFetch(agentId, { method: 'DELETE' });
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
    initAdmin();
});