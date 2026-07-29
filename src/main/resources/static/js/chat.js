const API_BASE = '/api/agents';
const params = new URLSearchParams(window.location.search);
const agentId = params.get('agentId');

if (!agentId) {
    window.location.href = '/';
}

let currentConversationId = params.get('conversationId') || null;
let activeStream = null;

// ── API ───────────────────────────────────────────────────────────────────

async function apiGet(path) {
    const res = await fetch(`${API_BASE}/${path}`);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return res.json();
}

// ── Init ──────────────────────────────────────────────────────────────────

async function init() {
    try {
        const agent = await apiGet(`${agentId}`);
        const name = agent.displayName || (typeof agent.id === 'string' ? agent.id : agent.id?.name) || agentId;

        document.title = 'Workflow AI — ' + name;
        document.getElementById('agent-name').textContent = name;
        document.getElementById('agent-desc').textContent = agent.description || '';
        document.getElementById('agent-avatar').textContent = name.charAt(0);

        const badge = document.getElementById('agent-chat-badge');
        const badgeLabel = chatBadgeLabel(agent.chatProviderId, agent.model);
        if (badgeLabel) {
            badge.textContent = badgeLabel;
            badge.title = badgeLabel;
            badge.classList.remove('hidden');
        } else {
            badge.classList.add('hidden');
        }

        await loadConversations();

        if (currentConversationId && currentConversationId !== 'NEW_CONVERSATION') {
            await loadMessages(currentConversationId);
        }

        document.getElementById('input').focus();
    } catch (err) {
        document.getElementById('messages').innerHTML = '<div class="error-note">Failed to load: ' + err.message + '</div>';
    }
}

// ── Conversations ─────────────────────────────────────────────────────────

async function loadConversations() {
    const container = document.getElementById('conversation-list');
    try {
        const list = await apiGet(`${agentId}/conversations`);
        container.innerHTML = '';

        if (!list || list.length === 0) {
            container.innerHTML = '<div class="conversation-item">No conversations.</div>';
            return;
        }

        for (const c of list) {
            const item = document.createElement('div');
            item.className = 'conversation-item' + (c.id === currentConversationId ? ' active' : '');

            const title = document.createElement('span');
            title.className = 'conversation-title';
            title.textContent = c.title || 'Untitled';

            const deleteBtn = document.createElement('button');
            deleteBtn.className = 'delete-btn';
            deleteBtn.title = 'Delete conversation';
            deleteBtn.innerHTML = '<i data-lucide="trash-2"></i>';
            deleteBtn.onclick = (_) => deleteConversation(c.id, item);

            item.appendChild(title);
            item.appendChild(deleteBtn);
            item.onclick = () => navigateTo(c.id);
            container.appendChild(item);
            if (window.lucide) lucide.createIcons();
        }
    } catch (err) {
        container.innerHTML = '<div class="conversation-item error">Failed to load</div>';
    }
}

async function navigateTo(id) {
    abortStream();
    currentConversationId = id;
    updateUrl();
    await loadConversations();
    await loadMessages(id);
}

async function deleteConversation(id) {
    if (!confirm('Are you sure you want to delete this conversation?')) return;

    const response = await fetch(`${API_BASE}/${agentId}/conversations/${id}`, {
        method: 'DELETE',
    });
    if (!response.ok) throw new Error(`Failed to delete: ${response.status}`);
    await loadConversations()
    window.location.href = `/chat.html?agentId=${agentId}`;
}

async function newConversation() {
    abortStream();
    currentConversationId = 'NEW_CONVERSATION';
    updateUrl();
    document.getElementById('messages').innerHTML = '';
    await loadConversations();
    document.getElementById('input').focus();
}

function updateUrl() {
    const url = new URL(window.location.href);
    if (currentConversationId) url.searchParams.set('conversationId', currentConversationId);
    else url.searchParams.delete('conversationId');
    window.history.pushState({}, '', url);
}

// ── Messages ──────────────────────────────────────────────────────────────

async function loadMessages(conversationId) {
    const messagesEl = document.getElementById('messages');
    messagesEl.innerHTML = '';

    try {
        const messages = await apiGet(`${agentId}/conversations/${conversationId}/messages`);
        for (const m of messages) {
            const role = m.role === 'USER' ? 'user' : 'agent';
            const wrapper = createMessage(role);
            wrapper.querySelector('.message-content').innerHTML = marked.parse(m.content || '');
            if (role === 'agent') {
                wrapper.querySelector('.message-stages')?.remove();
            }
            messagesEl.appendChild(wrapper);
        }
        scrollToBottom();
    } catch (err) {
        messagesEl.innerHTML = '<div class="error-note">Failed to load messages: ' + err.message + '</div>';
    }
}

// ── Send / Stream ─────────────────────────────────────────────────────────

async function sendMessage() {
    const input = document.getElementById('input');
    const btn = document.getElementById('send-btn');
    const text = input.value.trim();
    if (!text) return;

    input.value = '';
    input.disabled = true;
    btn.disabled = true;

    // User message
    const userWrapper = createMessage('user');
    userWrapper.querySelector('.message-content').textContent = text;
    document.getElementById('messages').appendChild(userWrapper);

    // Agent placeholder
    const agentWrapper = createMessage('agent');
    const content = agentWrapper.querySelector('.message-content');
    content.classList.add('loading', 'streaming');
    content.innerHTML = '<span class="typing-indicator"><span></span><span></span><span></span></span>';
    const stageList = agentWrapper.querySelector('.stage-list');
    const summary = agentWrapper.querySelector('.message-stages summary');
    document.getElementById('messages').appendChild(agentWrapper);
    scrollToBottom();

    let conversationId = currentConversationId || 'NEW_CONVERSATION';
    let fullText = '';
    let stageCount = 0;

    // Start SSE stream
    const abortController = new AbortController();
    activeStream = abortController;

    try {
        const response = await fetch( `${API_BASE}/${agentId}/conversations/${conversationId}/chat`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({message: text}),
            signal: abortController.signal,
        });

        if (!response.ok) throw new Error('HTTP ' + response.status);

        content.classList.remove('loading');

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const {done, value} = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, {stream: true});
            const events = buffer.split('\n\n');
            buffer = events.pop() ?? '';

            for (const raw of events) {
                const lines = raw.split('\n');
                const eventType = lines.find(l => l.startsWith('event:'))?.slice(6).trim()
                const dataLine = lines.find(l => l.startsWith('data:'));
                if (!dataLine) continue;
                const data = dataLine.slice(5);

                switch (EventType[eventType]) {
                    case EventType.TOKEN:
                        fullText += data;
                        content.innerHTML = marked.parse(fullText);
                        scrollToBottom();
                        break;
                    case EventType.STAGE:
                        try {
                            renderStage(JSON.parse(data), stageList, summary, ++stageCount);
                        } catch {
                        }
                        break;
                    case EventType.DECISION:
                        try {
                            renderDecision(JSON.parse(data), stageList);
                        } catch {
                        }
                        break;
                    case EventType.MEMORY_UPDATED:
                        renderSystemStage('Memory updated', stageList);
                        break;
                    case EventType.CONVERSATION_CREATED:
                        try {
                            const conv = JSON.parse(data);
                            currentConversationId = conv.id;
                            updateUrl();
                            loadConversations();
                        } catch {
                        }
                        break;
                    case EventType.ERROR:
                        try {
                            const e = JSON.parse(data);
                            appendError(content, e.message || 'Unknown error');
                        } catch {
                            appendError(content, data);
                        }
                        break;
                }
            }
        }

        content.classList.remove('streaming');
        finalizeStages(stageList, summary);

    } catch (err) {
        if (!abortController.signal.aborted) {
            content.classList.remove('loading', 'streaming');
            content.textContent = 'Error: ' + err.message;
            content.classList.add('error');
        }
    } finally {
        activeStream = null;
        input.disabled = false;
        btn.disabled = false;
        input.focus();
    }
}

function abortStream() {
    if (activeStream) {
        activeStream.abort();
        activeStream = null;
    }
}

// ── Stage rendering ───────────────────────────────────────────────────────

function renderStage(stage, stageList, summary, count) {
    const key = (stage.stageId || stage.stageName || 'unknown').replace(/\s+/g, '-');
    let item = stageList.querySelector('[data-stage="' + key + '"]');

    if (!item) {
        item = document.createElement('div');
        item.dataset.stage = key;
        stageList.appendChild(item);
        summary.parentElement.open = true;
        summary.textContent = count + ' stage' + (count > 1 ? 's' : '') + '...';
    }

    const statusClass = {
        STARTED: 'stage-started',
        COMPLETED: 'stage-completed',
        FAILED: 'stage-failed',
    }[stage.status] || '';

    item.className = 'stage-item ' + statusClass;
    const icon = {STARTED: 'loader-2', COMPLETED: 'check', FAILED: 'x'}[stage.status] || 'circle';
    item.innerHTML = '<span class="stage-icon"><i data-lucide="' + icon + '"></i></span>' + (stage.label || stage.stageName || key);
    if (window.lucide) lucide.createIcons();
    if (stage.reason) item.title = stage.reason;
}

function renderDecision(decision, stageList) {
    let item = stageList.querySelector('[data-stage="decision"]');
    if (!item) {
        item = document.createElement('div');
        item.dataset.stage = EventType.DECISION;
        item.className = 'stage-item stage-decision';
        stageList.appendChild(item);
    }
    const modeClass = {
        EXECUTE: 'decision-execute',
        CLARIFY: 'decision-clarify',
        REDIRECT: 'decision-redirect',
        REFUSE: 'decision-refuse',
    }[decision.mode] || '';
    item.innerHTML = '<span class="decision-badge ' + modeClass + '">' + decision.mode + '</span> ' + (decision.reason || '');
}

function renderSystemStage(label, stageList) {
    const item = document.createElement('div');
    item.className = 'stage-item stage-system';
    item.innerHTML = '<span class="stage-icon"><i data-lucide="refresh-cw"></i></span>' + label;
    stageList.appendChild(item);
    if (window.lucide) lucide.createIcons();
}

function appendError(content, message) {
    const note = document.createElement('div');
    note.className = 'error-note';
    note.innerHTML = '<i data-lucide="triangle-alert"></i> ' + message;
    content.appendChild(note);
    if (window.lucide) lucide.createIcons();
}

function finalizeStages(stageList, summary) {
    const completed = stageList.querySelectorAll('.stage-completed').length;
    const failed = stageList.querySelectorAll('.stage-failed').length;
    const total = completed + failed;
    summary.parentElement.open = false;
    if (failed > 0) {
        summary.textContent = total + ' stages \u2014 ' + failed + ' failed';
        summary.className = 'stages-fail';
    } else {
        summary.textContent = total + ' stages \u2713';
        summary.className = 'stages-ok';
    }
}

// ── DOM helpers ───────────────────────────────────────────────────────────

function createMessage(role) {
    const wrapper = document.createElement('div');
    wrapper.classList.add('message', role);

    const content = document.createElement('div');
    content.classList.add('message-content');
    wrapper.appendChild(content);

    if (role === 'agent') {
        const details = document.createElement('details');
        details.className = 'message-stages';
        const summary = document.createElement('summary');
        summary.textContent = 'stages...';
        details.appendChild(summary);
        const list = document.createElement('div');
        list.className = 'stage-list';
        details.appendChild(list);
        wrapper.appendChild(details);
    }

    return wrapper;
}

function scrollToBottom() {
    const m = document.getElementById('messages');
    m.scrollTop = m.scrollHeight;
}

function resizeComposer() {
    const input = document.getElementById('input');
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 180) + 'px';
}

const EventType =  {
    CONVERSATION_CREATED: 'CONVERSATION_CREATED',
    DECISION: 'DECISION',
    TOKEN: 'TOKEN',
    RESPONSE_COMPLETED: 'RESPONSE_COMPLETED',
    MEMORY_UPDATED: 'MEMORY_UPDATED',
    CONVERSATION_COMPLETED: 'CONVERSATION_COMPLETED',
    ERROR: 'ERROR',
    STAGE: 'STAGE'
};

// ── Events ────────────────────────────────────────────────────────────────

document.getElementById('new-chat-btn').addEventListener('click', () => newConversation());
document.getElementById('send-btn').addEventListener('click', () => sendMessage());
document.getElementById('input').addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});
document.getElementById('input').addEventListener('input', resizeComposer);

window.addEventListener('popstate', function () {
    const p = new URLSearchParams(window.location.search);
    const newId = p.get('conversationId') || null;
    if (newId !== currentConversationId) {
        abortStream();
        currentConversationId = newId;
        if (newId && newId !== 'NEW_CONVERSATION') loadMessages(newId);
        else document.getElementById('messages').innerHTML = '';
        loadConversations();
    }
});

document.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) lucide.createIcons();
    resizeComposer();
    init();
});