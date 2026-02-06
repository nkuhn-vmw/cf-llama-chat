const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

let currentConfig = null;

// Load configuration on page load
document.addEventListener('DOMContentLoaded', loadConfiguration);

function loadConfiguration() {
    fetch('/api/admin/storage')
        .then(response => response.json())
        .then(data => {
            currentConfig = data;
            updateUI(data);
        })
        .catch(error => {
            console.error('Error loading config:', error);
            toast.error('Failed to load configuration');
        });
}

function updateUI(config) {
    const statusIcon = document.getElementById('statusIcon');
    const statusTitle = document.getElementById('statusTitle');
    const statusDescription = document.getElementById('statusDescription');
    const statusBadge = document.getElementById('statusBadge');
    const statusBadgeText = document.getElementById('statusBadgeText');
    const enabledToggle = document.getElementById('storageEnabled');

    if (config.storageActive) {
        statusIcon.className = 'status-icon enabled';
        statusTitle.textContent = 'S3 Storage Active';
        statusDescription.textContent = 'Documents will be stored in S3 and available for download';
        statusBadge.className = 'status-badge enabled';
        statusBadgeText.textContent = 'Active';
    } else if (config.configured && config.enabled) {
        statusIcon.className = 'status-icon disabled';
        statusTitle.textContent = 'Configuration Incomplete';
        statusDescription.textContent = 'Storage is enabled but configuration may be invalid';
        statusBadge.className = 'status-badge disabled';
        statusBadgeText.textContent = 'Error';
    } else {
        statusIcon.className = 'status-icon disabled';
        statusTitle.textContent = 'S3 Storage Disabled';
        statusDescription.textContent = 'Documents are processed for embeddings only';
        statusBadge.className = 'status-badge disabled';
        statusBadgeText.textContent = 'Disabled';
    }

    // Update form fields
    enabledToggle.checked = config.enabled || false;
    document.getElementById('bucketName').value = config.bucketName || '';
    document.getElementById('region').value = config.region || '';
    document.getElementById('endpointUrl').value = config.endpointUrl || '';
    document.getElementById('pathPrefix').value = config.pathPrefix || '';
    document.getElementById('pathStyleAccess').checked = config.pathStyleAccess || false;

    // Clear password fields but show indicator if keys exist
    document.getElementById('accessKey').placeholder = config.hasAccessKey ? '(key configured)' : 'Enter access key';
    document.getElementById('secretKey').placeholder = config.hasSecretKey ? '(key configured)' : 'Enter secret key';
}

function toggleStorage() {
    const enabled = document.getElementById('storageEnabled').checked;
    const endpoint = enabled ? '/api/admin/storage/enable' : '/api/admin/storage/disable';

    fetch(endpoint, { method: 'POST' })
        .then(response => response.json())
        .then(result => {
            if (result.error) {
                toast.error(result.message || result.error);
                document.getElementById('storageEnabled').checked = !enabled;
            } else {
                toast.success(enabled ? 'Storage enabled' : 'Storage disabled');
                loadConfiguration();
            }
        })
        .catch(error => {
            console.error('Error toggling storage:', error);
            toast.error('Failed to toggle storage');
            document.getElementById('storageEnabled').checked = !enabled;
        });
}

function testConnection(e) {
    const btn = e ? e.target : document.querySelector('.btn-secondary');
    btn.disabled = true;
    btn.textContent = 'Testing...';

    const data = getFormData();

    fetch('/api/admin/storage/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    .then(response => response.json())
    .then(result => {
        if (result.success) {
            toast.success('Connection successful!');
        } else {
            toast.error(result.message || 'Connection failed');
        }
    })
    .catch(error => {
        console.error('Error testing connection:', error);
        toast.error('Failed to test connection');
    })
    .finally(() => {
        btn.disabled = false;
        btn.textContent = 'Test Connection';
    });
}

document.getElementById('storageForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const submitBtn = e.target.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Saving...';

    const data = getFormData();

    fetch('/api/admin/storage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    })
    .then(response => response.json())
    .then(result => {
        if (result.error) {
            toast.error(result.message || result.error);
        } else {
            toast.success('Configuration saved successfully');
            loadConfiguration();
        }
    })
    .catch(error => {
        console.error('Error saving config:', error);
        toast.error('Failed to save configuration');
    })
    .finally(() => {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Save Configuration';
    });
});

function getFormData() {
    return {
        bucketName: document.getElementById('bucketName').value,
        region: document.getElementById('region').value,
        endpointUrl: document.getElementById('endpointUrl').value || null,
        pathPrefix: document.getElementById('pathPrefix').value || null,
        pathStyleAccess: document.getElementById('pathStyleAccess').checked,
        accessKey: document.getElementById('accessKey').value || null,
        secretKey: document.getElementById('secretKey').value || null
    };
}

// Bind event listeners (CSP-compliant, no inline handlers)
document.getElementById('storageEnabled')?.addEventListener('change', toggleStorage);
document.querySelector('.btn-secondary')?.addEventListener('click', testConnection);
