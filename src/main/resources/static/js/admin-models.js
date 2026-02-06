// Apply saved theme
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

let deleteBindingId = null;
let bindingsData = [];

// Load bindings on page load
document.addEventListener('DOMContentLoaded', loadBindings);

function loadBindings() {
    fetch('/api/admin/external-bindings')
        .then(response => response.json())
        .then(bindings => {
            bindingsData = bindings;
            renderBindings(bindings);
        })
        .catch(error => {
            console.error('Error loading bindings:', error);
        });
}

function renderBindings(bindings) {
    const table = document.getElementById('bindingsTable');
    const emptyState = document.getElementById('bindingsEmptyState');
    const tbody = document.getElementById('bindingsTableBody');

    if (bindings.length === 0) {
        table.style.display = 'none';
        emptyState.style.display = 'block';
        return;
    }

    table.style.display = 'block';
    emptyState.style.display = 'none';

    tbody.innerHTML = bindings.map(binding => `
        <tr>
            <td>
                <div style="font-weight: 500;">${escapeHtml(binding.name)}</div>
                <div style="font-size: 0.8rem; color: var(--text-muted);">${escapeHtml(binding.description || '')}</div>
            </td>
            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis;">
                ${escapeHtml(binding.apiBase)}
            </td>
            <td>
                <span class="type-badge">${binding.type}</span>
            </td>
            <td>
                <span title="${binding.modelNames ? Array.from(binding.modelNames).join(', ') : ''}">${binding.modelCount}</span>
            </td>
            <td>
                <label class="toggle-switch">
                    <input type="checkbox"
                           ${binding.enabled ? 'checked' : ''}
                           data-binding-id="${binding.id}">
                    <span class="toggle-slider"></span>
                </label>
            </td>
            <td>
                <button class="action-btn primary"
                        data-binding-id="${binding.id}"
                        data-action="reload">Reload</button>
                <button class="action-btn"
                        data-binding-id="${binding.id}"
                        data-action="edit">Edit</button>
                <button class="action-btn danger"
                        data-binding-id="${binding.id}"
                        data-binding-name="${escapeHtml(binding.name)}"
                        data-action="delete">Delete</button>
            </td>
        </tr>
    `).join('');
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showAddBindingModal() {
    document.getElementById('bindingModalTitle').textContent = 'Add External Binding';
    document.getElementById('bindingForm').reset();
    document.getElementById('bindingId').value = '';
    document.getElementById('bindingModal').classList.add('open');
}

function showEditBindingModal(bindingId) {
    const binding = bindingsData.find(b => b.id === bindingId);
    if (!binding) return;

    document.getElementById('bindingModalTitle').textContent = 'Edit External Binding';
    document.getElementById('bindingId').value = binding.id;
    document.getElementById('bindingName').value = binding.name || '';
    document.getElementById('bindingDescription').value = binding.description || '';
    document.getElementById('bindingApiBase').value = binding.apiBase || '';
    document.getElementById('bindingApiKey').value = ''; // Don't show existing key
    document.getElementById('bindingApiKey').placeholder = 'Leave blank to keep existing key';
    document.getElementById('bindingApiKey').required = false;
    document.getElementById('bindingConfigUrl').value = binding.configUrl || '';
    document.getElementById('bindingModal').classList.add('open');
}

function closeBindingModal() {
    document.getElementById('bindingModal').classList.remove('open');
    document.getElementById('bindingApiKey').required = true;
    document.getElementById('bindingApiKey').placeholder = 'Your API key or JWT token';
}

document.getElementById('bindingForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const bindingId = document.getElementById('bindingId').value;
    const data = {
        name: document.getElementById('bindingName').value,
        description: document.getElementById('bindingDescription').value,
        apiBase: document.getElementById('bindingApiBase').value,
        configUrl: document.getElementById('bindingConfigUrl').value
    };

    // Only include apiKey if it's provided (for edits, allow empty to keep existing)
    const apiKey = document.getElementById('bindingApiKey').value;
    if (apiKey) {
        data.apiKey = apiKey;
    }

    const url = bindingId ? `/api/admin/external-bindings/${bindingId}` : '/api/admin/external-bindings';
    const method = bindingId ? 'PUT' : 'POST';

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
            closeBindingModal();
            loadBindings();
            // Reload the page to update the models table
            setTimeout(() => location.reload(), 500);
        }
    })
    .catch(error => {
        console.error('Error:', error);
        toast.error('Failed to save binding');
    });
});

function toggleBindingEnabled(checkbox) {
    const bindingId = checkbox.dataset.bindingId;
    const enabled = checkbox.checked;

    fetch(`/api/admin/external-bindings/${bindingId}/enabled`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled })
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.error);
            checkbox.checked = !enabled;
        } else {
            loadBindings();
            // Reload after short delay to update models
            setTimeout(() => location.reload(), 500);
        }
    })
    .catch(error => {
        console.error('Error:', error);
        checkbox.checked = !enabled;
    });
}

function reloadBinding(btn) {
    const bindingId = btn.dataset.bindingId;
    btn.disabled = true;
    btn.textContent = 'Reloading...';

    fetch(`/api/admin/external-bindings/${bindingId}/reload`, { method: 'POST' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.error);
            } else {
                toast.success(`Reloaded ${result.reloadedModelCount} model(s)`);
                loadBindings();
                // Reload to update models
                setTimeout(() => location.reload(), 500);
            }
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to reload binding');
            btn.disabled = false;
            btn.textContent = 'Reload';
        });
}

function showDeleteBindingModal(btn) {
    deleteBindingId = btn.dataset.bindingId;
    document.getElementById('deleteBindingName').textContent = btn.dataset.bindingName;
    document.getElementById('deleteBindingModal').classList.add('open');
}

function closeDeleteBindingModal() {
    document.getElementById('deleteBindingModal').classList.remove('open');
    deleteBindingId = null;
}

document.getElementById('confirmDeleteBindingBtn').addEventListener('click', function() {
    if (!deleteBindingId) return;

    fetch(`/api/admin/external-bindings/${deleteBindingId}`, { method: 'DELETE' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.error);
            } else {
                closeDeleteBindingModal();
                loadBindings();
                // Reload to update models
                setTimeout(() => location.reload(), 500);
            }
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to delete binding');
        });
});

// Close modals when clicking outside
document.querySelectorAll('.modal-overlay').forEach(modal => {
    modal.addEventListener('click', function(e) {
        if (e.target === this) {
            this.classList.remove('open');
        }
    });
});

// Bind event listeners (CSP-compliant, no inline handlers)
document.querySelector('.add-btn')?.addEventListener('click', showAddBindingModal);
document.querySelector('#bindingModal .modal-btn.cancel')?.addEventListener('click', closeBindingModal);
document.querySelector('#deleteBindingModal .modal-btn.cancel')?.addEventListener('click', closeDeleteBindingModal);

// Event delegation for dynamically rendered binding rows
document.getElementById('bindingsTableBody')?.addEventListener('click', function(e) {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;

    switch(btn.dataset.action) {
        case 'reload': reloadBinding(btn); break;
        case 'edit': showEditBindingModal(btn.dataset.bindingId); break;
        case 'delete': showDeleteBindingModal(btn); break;
    }
});

document.getElementById('bindingsTableBody')?.addEventListener('change', function(e) {
    const checkbox = e.target.closest('input[data-binding-id]');
    if (checkbox) toggleBindingEnabled(checkbox);
});
