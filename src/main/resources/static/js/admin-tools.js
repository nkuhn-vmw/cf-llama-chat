const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

function filterTools() {
    const filterValue = document.getElementById('mcpFilter').value;
    const rows = document.querySelectorAll('#toolsTableBody tr');

    rows.forEach(row => {
        if (!filterValue || row.dataset.mcpId === filterValue) {
            row.style.display = '';
        } else {
            row.style.display = 'none';
        }
    });

    // Show bulk buttons only when a specific DB server is selected (not binding or "All")
    const isDbServer = filterValue && !filterValue.startsWith('binding:');
    document.getElementById('bulkEnableBtn').style.display = isDbServer ? '' : 'none';
    document.getElementById('bulkDisableBtn').style.display = isDbServer ? '' : 'none';
}

function bulkSetEnabled(enabled) {
    const mcpServerId = document.getElementById('mcpFilter').value;
    if (!mcpServerId || mcpServerId.startsWith('binding:')) return;

    fetch('/api/admin/tools/bulk-enabled', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mcpServerId, enabled })
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.error);
            return;
        }
        // Update checkboxes in-place for visible rows matching this server
        document.querySelectorAll('#toolsTableBody tr').forEach(row => {
            if (row.dataset.mcpId === mcpServerId) {
                const cb = row.querySelector('input[data-tool-id]');
                if (cb) cb.checked = enabled;
            }
        });
        toast.success((enabled ? 'Enabled' : 'Disabled') + ' ' + result.count + ' tools');
    })
    .catch(error => {
        console.error('Error:', error);
        toast.error('Failed to update tools');
    });
}

function toggleEnabled(checkbox) {
    const toolId = checkbox.dataset.toolId;
    const enabled = checkbox.checked;

    fetch(`/api/admin/tools/${toolId}/enabled`, {
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

function showSchemaModal(btn) {
    const toolName = btn.dataset.toolName;
    const inputSchema = btn.dataset.inputSchema;

    document.getElementById('schemaToolName').textContent = toolName + ' - Input Schema';

    let schemaDisplay = 'No schema available';
    if (inputSchema && inputSchema !== 'null') {
        try {
            const parsed = JSON.parse(inputSchema);
            schemaDisplay = JSON.stringify(parsed, null, 2);
        } catch (e) {
            schemaDisplay = inputSchema;
        }
    }

    document.getElementById('schemaContent').textContent = schemaDisplay;
    document.getElementById('schemaModal').classList.add('open');
}

function closeSchemaModal() {
    document.getElementById('schemaModal').classList.remove('open');
}

document.querySelectorAll('.modal-overlay').forEach(modal => {
    modal.addEventListener('click', function(e) {
        if (e.target === this) {
            this.classList.remove('open');
        }
    });
});

// Bind event listeners (CSP-compliant, no inline handlers)
document.getElementById('mcpFilter')?.addEventListener('change', filterTools);

document.querySelectorAll('.toggle-switch input[data-tool-id]').forEach(cb => {
    cb.addEventListener('change', function() { toggleEnabled(this); });
});

document.querySelectorAll('.action-btn[data-tool-name]').forEach(btn => {
    btn.addEventListener('click', function() { showSchemaModal(this); });
});

document.querySelector('#schemaModal .modal-btn.cancel')?.addEventListener('click', closeSchemaModal);

document.getElementById('bulkEnableBtn')?.addEventListener('click', function() { bulkSetEnabled(true); });
document.getElementById('bulkDisableBtn')?.addEventListener('click', function() { bulkSetEnabled(false); });
