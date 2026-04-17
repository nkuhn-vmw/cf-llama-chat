// workspace/channels page — safe DOM construction, SSE-driven live updates
(function () {
    var currentChannelId = null;
    var eventSource = null;

    function showToast(msg, type) {
        if (window.showAdminToast) window.showAdminToast(msg, type || 'info');
    }

    function createEl(tag, className, textContent) {
        var el = document.createElement(tag);
        if (className) el.className = className;
        if (textContent) el.textContent = textContent;
        return el;
    }

    function renderChannelList(channels) {
        var list = document.getElementById('channelList');
        list.textContent = '';
        document.getElementById('totalChannels').textContent = channels.length;
        if (channels.length === 0) {
            var p = createEl('p', '', 'No channels yet. Create one above.');
            p.style.color = 'var(--text-muted)';
            p.style.padding = '16px';
            list.appendChild(p);
            return;
        }
        channels.forEach(function (ch) {
            var card = createEl('div', 'channel-card' + (ch.id === currentChannelId ? ' active' : ''));
            card.dataset.id = ch.id;
            var header = createEl('div', 'channel-card-header');
            header.appendChild(createEl('h4', '', '#' + (ch.name || '')));
            card.appendChild(header);
            if (ch.description) {
                card.appendChild(createEl('div', 'channel-card-desc', ch.description));
            }
            list.appendChild(card);
        });
    }

    function appendMessage(container, m) {
        var div = createEl('div', 'channel-msg');
        div.appendChild(createEl('div', 'channel-msg-author', m.authorName || m.username || 'User'));
        div.appendChild(createEl('div', 'channel-msg-text', m.content || ''));
        div.appendChild(createEl('div', 'channel-msg-time',
            m.createdAt ? new Date(m.createdAt).toLocaleString() : new Date().toLocaleString()));
        container.appendChild(div);
    }

    function renderMessages(messages) {
        var msgList = document.getElementById('channelMsgList');
        msgList.textContent = '';
        messages.forEach(function (m) { appendMessage(msgList, m); });
        msgList.scrollTop = msgList.scrollHeight;
    }

    async function loadChannels() {
        try {
            var resp = await fetch('/api/channels');
            if (!resp.ok) throw new Error();
            renderChannelList(await resp.json());
        } catch (e) {
            console.error('Failed to load channels:', e);
        }
    }

    async function openChannel(id) {
        currentChannelId = id;
        document.getElementById('emptyChannelView').style.display = 'none';
        document.getElementById('channelViewContent').style.display = 'flex';

        document.querySelectorAll('.channel-card').forEach(function (c) {
            c.classList.toggle('active', c.dataset.id === id);
        });

        try {
            var resp = await fetch('/api/channels/' + id);
            if (resp.ok) {
                var channel = await resp.json();
                document.getElementById('channelViewName').textContent = '#' + (channel.name || '');
            }
        } catch (e) {}

        try {
            var msgResp = await fetch('/api/channels/' + id + '/messages');
            if (msgResp.ok) renderMessages(await msgResp.json());
        } catch (e) {}

        startSSE(id);
    }

    function startSSE(channelId) {
        if (eventSource) eventSource.close();
        try {
            eventSource = new EventSource('/api/channels/' + channelId + '/stream');
            eventSource.onmessage = function (event) {
                try {
                    var msg = JSON.parse(event.data);
                    var msgList = document.getElementById('channelMsgList');
                    appendMessage(msgList, msg);
                    msgList.scrollTop = msgList.scrollHeight;
                } catch (e) {}
            };
        } catch (e) {}
    }

    async function sendMessage() {
        if (!currentChannelId) return;
        var input = document.getElementById('channelMsgInput');
        var content = input.value.trim();
        if (!content) return;
        input.value = '';
        try {
            await fetch('/api/channels/' + currentChannelId + '/messages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            });
        } catch (e) {
            showToast('Failed to send message', 'error');
        }
    }

    async function createChannel() {
        var name = document.getElementById('channelNameInput').value.trim();
        if (!name) { showToast('Channel name is required', 'error'); return; }
        var desc = document.getElementById('channelDescInput').value.trim();
        try {
            var resp = await fetch('/api/channels', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name, description: desc })
            });
            if (!resp.ok) throw new Error();
            document.getElementById('channelNameInput').value = '';
            document.getElementById('channelDescInput').value = '';
            showToast('Channel created', 'success');
            loadChannels();
        } catch (e) {
            showToast('Failed to create channel', 'error');
        }
    }

    async function deleteChannel() {
        if (!currentChannelId || !confirm('Delete this channel?')) return;
        try {
            var resp = await fetch('/api/channels/' + currentChannelId, { method: 'DELETE' });
            if (!resp.ok) throw new Error();
            currentChannelId = null;
            document.getElementById('channelViewContent').style.display = 'none';
            document.getElementById('emptyChannelView').style.display = 'flex';
            showToast('Channel deleted', 'success');
            loadChannels();
        } catch (e) {
            showToast('Failed to delete channel', 'error');
        }
    }

    document.getElementById('createChannelBtn').addEventListener('click', createChannel);
    document.getElementById('deleteChannelBtn').addEventListener('click', deleteChannel);
    document.getElementById('channelSendBtn').addEventListener('click', sendMessage);
    document.getElementById('channelMsgInput').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') { e.preventDefault(); sendMessage(); }
    });
    document.getElementById('channelList').addEventListener('click', function (e) {
        var card = e.target.closest('.channel-card');
        if (card) openChannel(card.dataset.id);
    });

    loadChannels();
})();
