const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

let deleteServerId = null;

function updateTransportFields() {
    const transportType = document.getElementById('transportType').value;
    const urlGroup = document.getElementById('urlGroup');
    const commandGroup = document.getElementById('commandGroup');
    const argsGroup = document.getElementById('argsGroup');
    const headersGroup = document.getElementById('headersGroup');

    if (transportType === 'SSE' || transportType === 'STREAMABLE_HTTP') {
        urlGroup.style.display = 'block';
        commandGroup.style.display = 'none';
        argsGroup.style.display = 'none';
        headersGroup.style.display = 'block';
        document.getElementById('serverUrl').required = true;
        document.getElementById('serverCommand').required = false;
    } else {
        urlGroup.style.display = 'none';
        commandGroup.style.display = 'block';
        argsGroup.style.display = 'block';
        headersGroup.style.display = 'none';
        document.getElementById('serverUrl').required = false;
        document.getElementById('serverCommand').required = true;
    }
}

function showAddModal() {
    document.getElementById('modalTitle').textContent = 'Add MCP Server';
    document.getElementById('serverForm').reset();
    document.getElementById('serverId').value = '';
    updateTransportFields();
    document.getElementById('serverModal').classList.add('open');
}

function showEditModal(btn) {
    const serverId = btn.dataset.serverId;
    document.getElementById('modalTitle').textContent = 'Edit MCP Server';

    fetch(`/api/admin/mcp/servers`)
        .then(response => response.json())
        .then(servers => {
            const server = servers.find(s => s.id === serverId);
            if (server) {
                document.getElementById('serverId').value = server.id;
                document.getElementById('serverName').value = server.name || '';
                document.getElementById('serverDescription').value = server.description || '';
                document.getElementById('transportType').value = server.transportType || 'SSE';
                document.getElementById('serverUrl').value = server.url || '';
                document.getElementById('serverCommand').value = server.command || '';
                document.getElementById('serverArgs').value = server.args || '';
                document.getElementById('envVars').value = server.envVars || '';
                document.getElementById('serverHeaders').value = server.headers || '';
                updateTransportFields();
                document.getElementById('serverModal').classList.add('open');
            }
        });
}

function closeServerModal() {
    document.getElementById('serverModal').classList.remove('open');
}

document.getElementById('serverForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const serverId = document.getElementById('serverId').value;
    // Strip newlines and extra whitespace from headers (long tokens can wrap)
    const headersRaw = document.getElementById('serverHeaders').value;
    const headersCleaned = headersRaw.replace(/[\r\n]+/g, '').trim();
    const data = {
        name: document.getElementById('serverName').value,
        description: document.getElementById('serverDescription').value,
        transportType: document.getElementById('transportType').value,
        url: document.getElementById('serverUrl').value,
        command: document.getElementById('serverCommand').value,
        args: document.getElementById('serverArgs').value,
        envVars: document.getElementById('envVars').value,
        headers: headersCleaned
    };

    const url = serverId ? `/api/admin/mcp/servers/${serverId}` : '/api/admin/mcp/servers';
    const method = serverId ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.error);
        } else {
            location.reload();
        }
    })
    .catch(error => {
        console.error('Error:', error);
        toast.error('Failed to save server');
    });
});

function toggleEnabled(checkbox) {
    const serverId = checkbox.dataset.serverId;
    const enabled = checkbox.checked;

    fetch(`/api/admin/mcp/servers/${serverId}/enabled`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled })
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.error);
            checkbox.checked = !enabled;
        }
    })
    .catch(error => {
        console.error('Error:', error);
        checkbox.checked = !enabled;
    });
}

function connectServer(btn) {
    const serverId = btn.dataset.serverId;
    btn.disabled = true;
    btn.textContent = 'Connecting...';

    fetch(`/api/admin/mcp/servers/${serverId}/connect`, { method: 'POST' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.error);
            }
            location.reload();
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to connect');
            location.reload();
        });
}

function disconnectServer(btn) {
    const serverId = btn.dataset.serverId;

    fetch(`/api/admin/mcp/servers/${serverId}/disconnect`, { method: 'POST' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.error);
            }
            location.reload();
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to disconnect');
        });
}

function syncTools(btn) {
    const serverId = btn.dataset.serverId;
    btn.disabled = true;
    btn.textContent = 'Syncing...';

    fetch(`/api/admin/mcp/servers/${serverId}/sync-tools`, { method: 'POST' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.error);
            } else {
                toast.success(`Synced ${result.toolCount} tools`);
            }
            location.reload();
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to sync tools');
            location.reload();
        });
}

function showDeleteModal(btn) {
    deleteServerId = btn.dataset.serverId;
    document.getElementById('deleteServerName').textContent = btn.dataset.serverName;
    document.getElementById('deleteModal').classList.add('open');
}

function closeDeleteModal() {
    document.getElementById('deleteModal').classList.remove('open');
    deleteServerId = null;
}

document.getElementById('confirmDeleteBtn').addEventListener('click', function() {
    if (!deleteServerId) return;

    fetch(`/api/admin/mcp/servers/${deleteServerId}`, { method: 'DELETE' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.error);
            } else {
                location.reload();
            }
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to delete server');
        });

    closeDeleteModal();
});

document.querySelectorAll('.modal-overlay').forEach(modal => {
    modal.addEventListener('click', function(e) {
        if (e.target === this) {
            this.classList.remove('open');
        }
    });
});

// Bind event listeners (CSP-compliant, no inline handlers)
document.querySelector('.add-btn')?.addEventListener('click', showAddModal);

document.querySelectorAll('.toggle-switch input[type="checkbox"]').forEach(cb => {
    cb.addEventListener('change', function() { toggleEnabled(this); });
});

document.getElementById('transportType')?.addEventListener('change', updateTransportFields);

function probeBinding(btn) {
    const name = btn.dataset.bindingName;
    btn.disabled = true;
    btn.textContent = 'Probing...';

    fetch(`/api/admin/mcp/bindings/${encodeURIComponent(name)}/probe`, { method: 'POST' })
        .then(response => response.json())
        .then(result => {
            const statusEl = document.getElementById('binding-status-' + name);
            const toolsEl = document.getElementById('binding-tools-' + name);

            if (result.healthy) {
                statusEl.className = 'status-badge connected';
                statusEl.innerHTML = '<span class="dot"></span><span>Connected</span>';
                toolsEl.textContent = result.toolCount;
                toast.success(result.serverName + ': ' + result.toolCount + ' tools available');
            } else {
                statusEl.className = 'status-badge error';
                statusEl.innerHTML = '<span class="dot"></span><span>Unavailable</span>';
                toolsEl.textContent = '0';
                toast.error('Service binding ' + name + ' is not reachable');
            }

            btn.disabled = false;
            btn.textContent = 'Probe';
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to probe service binding');
            btn.disabled = false;
            btn.textContent = 'Probe';
        });
}

let tokenBindingName = null;

function showTokenModal(btn) {
    tokenBindingName = btn.dataset.bindingName;
    document.getElementById('tokenBindingName').textContent = tokenBindingName;
    document.getElementById('tokenValue').value = '';
    document.getElementById('tokenModal').classList.add('open');
}

function closeTokenModal() {
    document.getElementById('tokenModal').classList.remove('open');
    tokenBindingName = null;
}

document.getElementById('tokenForm').addEventListener('submit', function(e) {
    e.preventDefault();
    if (!tokenBindingName) return;

    const token = document.getElementById('tokenValue').value.replace(/[\r\n]+/g, '').trim();
    if (!token) {
        toast.error('Token is required');
        return;
    }

    const saveBtn = this.querySelector('.modal-btn.save');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving...';

    fetch(`/api/admin/mcp/bindings/${encodeURIComponent(tokenBindingName)}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: token })
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.error);
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save Token';
        } else {
            toast.success('Token saved for ' + tokenBindingName);
            closeTokenModal();
            location.reload();
        }
    })
    .catch(error => {
        console.error('Error:', error);
        toast.error('Failed to save token');
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Token';
    });
});

document.querySelector('#tokenModal .modal-btn.cancel')?.addEventListener('click', closeTokenModal);

let renameBindingName = null;

function editBindingName(btn) {
    renameBindingName = btn.dataset.bindingName;
    document.getElementById('renameBindingName').textContent = renameBindingName;
    document.getElementById('renameValue').value = btn.dataset.currentName || '';
    document.getElementById('renameModal').classList.add('open');
}

function closeRenameModal() {
    document.getElementById('renameModal').classList.remove('open');
    renameBindingName = null;
}

document.getElementById('renameForm').addEventListener('submit', function(e) {
    e.preventDefault();
    if (!renameBindingName) return;

    const displayName = document.getElementById('renameValue').value.trim();

    fetch(`/api/admin/mcp/bindings/${encodeURIComponent(renameBindingName)}/display-name`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName: displayName })
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.error);
        } else {
            toast.success('Display name updated');
            closeRenameModal();
            location.reload();
        }
    })
    .catch(error => {
        console.error('Error:', error);
        toast.error('Failed to update display name');
    });
});

document.querySelector('#renameModal .modal-btn.cancel')?.addEventListener('click', closeRenameModal);

// Event delegation for action buttons in server rows
document.addEventListener('click', function(e) {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;

    switch(btn.dataset.action) {
        case 'connect': connectServer(btn); break;
        case 'disconnect': disconnectServer(btn); break;
        case 'syncTools': syncTools(btn); break;
        case 'edit': showEditModal(btn); break;
        case 'delete': showDeleteModal(btn); break;
        case 'probeBinding': probeBinding(btn); break;
        case 'showTokenModal': showTokenModal(btn); break;
        case 'editBindingName': editBindingName(btn); break;
    }
});

document.querySelector('#serverModal .modal-btn.cancel')?.addEventListener('click', closeServerModal);
document.querySelector('#deleteModal .modal-btn.cancel')?.addEventListener('click', closeDeleteModal);
