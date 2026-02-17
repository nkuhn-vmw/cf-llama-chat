// Admin Webhooks Page - Webhook management
var savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

(function() {
    'use strict';

    var webhooks = [];
    var deleteWebhookId = null;

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------
    function clearChildren(el) {
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function formatEventType(eventType) {
        if (!eventType) return '';
        return eventType.replace('.', ' ').replace(/\b\w/g, function(c) {
            return c.toUpperCase();
        });
    }

    // ------------------------------------------------------------------
    // Load webhooks
    // ------------------------------------------------------------------
    function loadWebhooks() {
        fetch('/api/admin/webhooks')
            .then(function(res) {
                if (!res.ok) throw new Error('Failed to load webhooks');
                return res.json();
            })
            .then(function(data) {
                webhooks = data;
                renderWebhooks();
                document.getElementById('webhookCount').textContent = webhooks.length;
            })
            .catch(function(err) {
                console.error('Error loading webhooks:', err);
                toast.error('Failed to load webhooks');
            });
    }

    function renderWebhooks() {
        var tbody = document.getElementById('webhooksTableBody');
        clearChildren(tbody);

        if (webhooks.length === 0) {
            var emptyRow = document.createElement('tr');
            var emptyTd = document.createElement('td');
            emptyTd.colSpan = 6;
            emptyTd.className = 'empty-state';
            emptyTd.textContent = 'No webhooks configured.';
            emptyRow.appendChild(emptyTd);
            tbody.appendChild(emptyRow);
            return;
        }

        webhooks.forEach(function(webhook) {
            var row = document.createElement('tr');

            // Name column
            var nameTd = document.createElement('td');
            nameTd.textContent = webhook.name || '';
            row.appendChild(nameTd);

            // URL column (truncated)
            var urlTd = document.createElement('td');
            urlTd.className = 'url-cell';
            urlTd.textContent = webhook.url || '';
            urlTd.title = webhook.url || '';
            row.appendChild(urlTd);

            // Event Type column
            var eventTd = document.createElement('td');
            eventTd.className = 'event-cell';
            eventTd.textContent = formatEventType(webhook.eventType);
            row.appendChild(eventTd);

            // Platform column (badge)
            var platformTd = document.createElement('td');
            var platformBadge = document.createElement('span');
            var platform = webhook.platform || 'generic';
            platformBadge.className = 'platform-badge ' + platform;
            platformBadge.textContent = platform.toUpperCase();
            platformTd.appendChild(platformBadge);
            row.appendChild(platformTd);

            // Enabled column
            var enabledTd = document.createElement('td');
            enabledTd.className = 'enabled-cell';
            if (webhook.enabled) {
                enabledTd.classList.add('yes');
                enabledTd.textContent = 'Yes';
            } else {
                enabledTd.classList.add('no');
                enabledTd.textContent = 'No';
            }
            row.appendChild(enabledTd);

            // Actions column
            var actionsTd = document.createElement('td');
            var deleteBtn = document.createElement('button');
            deleteBtn.className = 'action-btn danger';
            deleteBtn.textContent = 'Delete';
            deleteBtn.addEventListener('click', function() {
                showDeleteModal(webhook.id);
            });
            actionsTd.appendChild(deleteBtn);
            row.appendChild(actionsTd);

            tbody.appendChild(row);
        });
    }

    // ------------------------------------------------------------------
    // Create Webhook Modal
    // ------------------------------------------------------------------
    function showCreateModal() {
        document.getElementById('webhookForm').reset();
        document.getElementById('webhookEnabled').checked = true;
        document.getElementById('webhookModal').classList.add('open');
        document.getElementById('webhookName').focus();
    }

    function closeWebhookModal() {
        document.getElementById('webhookModal').classList.remove('open');
    }

    document.getElementById('webhookForm').addEventListener('submit', function(e) {
        e.preventDefault();

        var name = document.getElementById('webhookName').value.trim();
        var url = document.getElementById('webhookUrl').value.trim();
        if (!name || !url) {
            toast.warning('Name and URL are required');
            return;
        }

        var data = {
            name: name,
            url: url,
            eventType: document.getElementById('webhookEventType').value,
            platform: document.getElementById('webhookPlatform').value,
            secret: document.getElementById('webhookSecret').value.trim() || null,
            enabled: document.getElementById('webhookEnabled').checked
        };

        fetch('/api/admin/webhooks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        })
        .then(function(res) {
            if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Create failed'); });
            return res.json();
        })
        .then(function() {
            toast.success('Webhook created');
            closeWebhookModal();
            loadWebhooks();
        })
        .catch(function(err) {
            console.error('Error creating webhook:', err);
            toast.error(err.message || 'Failed to create webhook');
        });
    });

    // ------------------------------------------------------------------
    // Delete Modal
    // ------------------------------------------------------------------
    function showDeleteModal(webhookId) {
        deleteWebhookId = webhookId;
        document.getElementById('deleteWebhookModal').classList.add('open');
    }

    function closeDeleteModal() {
        document.getElementById('deleteWebhookModal').classList.remove('open');
        deleteWebhookId = null;
    }

    document.getElementById('confirmDeleteWebhookBtn').addEventListener('click', function() {
        if (!deleteWebhookId) return;

        fetch('/api/admin/webhooks/' + deleteWebhookId, { method: 'DELETE' })
            .then(function(res) {
                if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Delete failed'); });
                return res.json();
            })
            .then(function() {
                toast.success('Webhook deleted');
                closeDeleteModal();
                loadWebhooks();
            })
            .catch(function(err) {
                console.error('Error deleting webhook:', err);
                toast.error(err.message || 'Failed to delete webhook');
            });
    });

    // ------------------------------------------------------------------
    // Event Listeners
    // ------------------------------------------------------------------
    document.getElementById('createWebhookBtn').addEventListener('click', showCreateModal);
    document.getElementById('cancelWebhookBtn').addEventListener('click', closeWebhookModal);
    document.getElementById('cancelDeleteWebhookBtn').addEventListener('click', closeDeleteModal);

    // Close modals on overlay click
    document.getElementById('webhookModal').addEventListener('click', function(e) {
        if (e.target === this) closeWebhookModal();
    });
    document.getElementById('deleteWebhookModal').addEventListener('click', function(e) {
        if (e.target === this) closeDeleteModal();
    });

    // ------------------------------------------------------------------
    // Initialize
    // ------------------------------------------------------------------
    loadWebhooks();

})();
