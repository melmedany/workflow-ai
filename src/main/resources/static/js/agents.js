const ADMIN_API = '/api/admin/agents';
let agents = [];
let providerOptions = [];

async function adminFetch(path, options) {
    const res = await fetch(`${ADMIN_API}/${path}`, options);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (res.status === 204) return null;
    return res.json();
}

async function initAdmin() {
    try {
        const [ providers] = await Promise.all([
            adminFetch('providers'),
        ]);
        providerOptions = providers || [];
        renderProviderOptions();
        await loadAdminAgents();
        if (agents.length > 0) fillForm(agents[0]);
        else resetForm();
    } catch (err) {
        setStatus('Failed to load admin data: ' + err.message, true);
        throw err;
    }
}

async function loadAdminAgents() {
    agents = await adminFetch('');
    const list = document.getElementById('admin-agent-list');
    if (!agents.length) {
        list.innerHTML = '<div class="conversation-item">No configured agents.</div>';
        return;
    }
    list.innerHTML = '';
    for (const agent of agents) {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'admin-agent-item';
        item.innerHTML = '<strong>' + agent.details.displayName + '</strong><span>' + agent.agentId + '</span>';
        item.onclick = () => fillForm(agent);
        list.appendChild(item);
    }
}

function fillForm(agent) {
    document.getElementById('agent-id').value = agent.agentId;
    document.getElementById('agent-enabled').checked = agent.details.enabled;
    document.getElementById('display-name').value = agent.details.displayName || '';
    document.getElementById('description').value = agent.details.description || '';
    document.getElementById('provider').value = agent.llmConfig.provider || providerOptions[0]?.provider || '';
    renderModelOptions(agent.llmConfig.model || '');
    document.getElementById('temperature').value = agent.llmConfig.temperature ?? 0.7;
    document.getElementById('memory-limit').value = agent.llmConfig.memoryLimit ?? 10;
    document.getElementById('memory-enabled').checked = !!agent.llmConfig.memoryEnabled;
    document.getElementById('validation-enabled').checked = !!agent.llmConfig.validationEnabled;
    document.getElementById('agent-prompt').value = (agent.llmConfig.agentPrompt);
    document.getElementById('capabilities').value = (agent.policyConfig.capabilities || []).join('\n');
    document.getElementById('greetings').value = readList(agent.policyConfig.greetings).join('\n');
    document.getElementById('refuse-messages').value = readList(agent.policyConfig.refuseMessages).join('\n');
    document.getElementById('redirect-messages').value = readList(agent.policyConfig.redirectMessages).join('\n');
    document.getElementById('max-retries').value = agent.policyConfig.maxRetries ?? 1;
    setStatus('');
}

function resetForm() {
    document.getElementById('agent-form').reset();
    renderProviderOptions();
    renderModelOptions();
    document.getElementById('temperature').value = 0.7;
    document.getElementById('memory-limit').value = 10;
    document.getElementById('max-retries').value = 1;
    setStatus('');
}

function renderProviderOptions() {
    const select = document.getElementById('provider');
    select.innerHTML = providerOptions.map(option => '<option value="' + option.provider + '">' + option.provider + '</option>').join('');
}

function renderModelOptions(selectedModel) {
    const provider = document.getElementById('provider').value || providerOptions[0]?.provider || '';
    const option = providerOptions.find(item => item.provider === provider);
    const models = option?.models || [];
    const select = document.getElementById('model');
    select.innerHTML = models.map(model => '<option value="' + model + '">' + model + '</option>').join('');
    if (selectedModel && !models.includes(selectedModel)) {
        select.insertAdjacentHTML('afterbegin', '<option value="' + selectedModel + '">' + selectedModel + ' (saved)</option>');
    }
    select.value = selectedModel || models[0] || '';
}

function lines(id) {
    return document.getElementById(id).value.split('\n').map(v => v.trim());
}

function readList(values, legacyValue) {
    if (Array.isArray(values)) return values;
    return legacyValue ? [legacyValue] : [];
}

function readForm() {
    return {
        agentId: document.getElementById('agent-id').value,
        details: {
            enabled: document.getElementById('agent-enabled').checked,
            displayName: document.getElementById('display-name').value.trim(),
            description: document.getElementById('description').value.trim(),
        },
        llmConfig: {
            provider: document.getElementById('provider').value.trim(),
            model: document.getElementById('model').value.trim(),
            agentPrompt: document.getElementById('agent-prompt').value.trim(),
            temperature: Number(document.getElementById('temperature').value),
            memoryEnabled: document.getElementById('memory-enabled').checked,
            validationEnabled: document.getElementById('validation-enabled').checked,
            memoryLimit: Number(document.getElementById('memory-limit').value),
        },
        policyConfig: {
            agentPrompt: lines('agent-prompt'),
            capabilities: lines('capabilities'),
            greetings: lines('greetings'),
            refuseMessages: lines('refuse-messages'),
            redirectMessages: lines('redirect-messages'),
            maxRetries: Number(document.getElementById('max-retries').value),
        },
    };
}

async function saveAgent(e) {
    e.preventDefault();
    const agent = readForm();
    const exists = agents.some(a => a.agentId === agent.agentId);
    await adminFetch(exists ? `${agent.agentId}` : '', {
        method: exists ? 'PUT' : 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(agent),
    });
    await fetch(`/api/agents/${agent.agentId}/reload`);
    await loadAdminAgents();
    fillForm(agent);
    setStatus('Saved. It may take some time for runtime agent changes to take effect.');
}

async function deleteAgent() {
    const agentId = document.getElementById('agent-id').value;
    if (!agentId || !confirm('Delete configuration for ' + agentId + '?')) return;
    await adminFetch(agentId, {method: 'DELETE'});
    await loadAdminAgents();
    resetForm();
    setStatus('Deleted. It may take some time for runtime agent changes to take effect.');
}

function setStatus(message, error) {
    const el = document.getElementById('admin-status');
    el.textContent = message;
    el.className = 'admin-status' + (error ? ' error-note' : '');
}

document.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) lucide.createIcons();
    document.getElementById('agent-form').addEventListener('submit', saveAgent);
    document.getElementById('provider').addEventListener('change', () => renderModelOptions());
    document.getElementById('delete-agent-btn').addEventListener('click', deleteAgent);
    document.getElementById('new-agent-btn').addEventListener('click', resetForm);
    initAdmin();
});