// Admin Banners Page - Notification banner management
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

(function() {
    'use strict';

    var banners = [];
    var deleteBannerId = null;

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------
    function clearChildren(el) {
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function formatExpiry(dateStr) {
        if (!dateStr) return 'Never';
        var date = new Date(dateStr);
        if (isNaN(date.getTime())) return 'Never';
        var now = new Date();
        var options = { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' };
        var formatted = date.toLocaleDateString('en-US', options);
        if (date < now) return formatted + ' (expired)';
        return formatted;
    }

    function isExpired(dateStr) {
        if (!dateStr) return false;
        var date = new Date(dateStr);
        return !isNaN(date.getTime()) && date < new Date();
    }

    // ------------------------------------------------------------------
    // Load banners
    // ------------------------------------------------------------------
    function loadBanners() {
        fetch('/api/banners')
            .then(function(res) {
                if (!res.ok) throw new Error('Failed to load banners');
                return res.json();
            })
            .then(function(data) {
                banners = data;
                renderBanners();
                document.getElementById('activeBannerCount').textContent = banners.length;
            })
            .catch(function(err) {
                console.error('Error loading banners:', err);
                toast.error('Failed to load banners');
            });
    }

    function renderBanners() {
        var tbody = document.getElementById('bannersTableBody');
        clearChildren(tbody);

        if (banners.length === 0) {
            var emptyRow = document.createElement('tr');
            var emptyTd = document.createElement('td');
            emptyTd.colSpan = 5;
            emptyTd.className = 'empty-state';
            emptyTd.textContent = 'No active banners.';
            emptyRow.appendChild(emptyTd);
            tbody.appendChild(emptyRow);
            return;
        }

        banners.forEach(function(banner) {
            var row = document.createElement('tr');

            // Type column
            var typeTd = document.createElement('td');
            var typeBadge = document.createElement('span');
            typeBadge.className = 'type-badge ' + (banner.type || 'info');
            typeBadge.textContent = (banner.type || 'info').toUpperCase();
            typeTd.appendChild(typeBadge);
            row.appendChild(typeTd);

            // Message column
            var msgTd = document.createElement('td');
            msgTd.className = 'banner-message-cell';
            msgTd.textContent = banner.message || '';
            row.appendChild(msgTd);

            // Dismissible column
            var dismissTd = document.createElement('td');
            dismissTd.className = 'dismissible-cell';
            dismissTd.textContent = banner.dismissible ? 'Yes' : 'No';
            row.appendChild(dismissTd);

            // Expiry column
            var expiryTd = document.createElement('td');
            expiryTd.className = 'expiry-cell';
            if (isExpired(banner.expiresAt)) {
                expiryTd.classList.add('expired');
            }
            expiryTd.textContent = formatExpiry(banner.expiresAt);
            row.appendChild(expiryTd);

            // Actions column
            var actionsTd = document.createElement('td');
            var deleteBtn = document.createElement('button');
            deleteBtn.className = 'action-btn danger';
            deleteBtn.textContent = 'Delete';
            deleteBtn.addEventListener('click', function() {
                showDeleteModal(banner.id);
            });
            actionsTd.appendChild(deleteBtn);
            row.appendChild(actionsTd);

            tbody.appendChild(row);
        });
    }

    // ------------------------------------------------------------------
    // Create Banner Modal
    // ------------------------------------------------------------------
    function showCreateModal() {
        document.getElementById('bannerForm').reset();
        document.getElementById('bannerDismissible').checked = true;
        updatePreview();
        document.getElementById('bannerModal').classList.add('open');
        document.getElementById('bannerMessage').focus();
    }

    function closeBannerModal() {
        document.getElementById('bannerModal').classList.remove('open');
    }

    function updatePreview() {
        var message = document.getElementById('bannerMessage').value.trim();
        var type = document.getElementById('bannerType').value;
        var preview = document.getElementById('bannerPreview');

        preview.className = 'banner-preview ' + type;
        clearChildren(preview);

        if (message) {
            preview.textContent = message;
        } else {
            var placeholder = document.createElement('span');
            placeholder.className = 'banner-preview-placeholder';
            placeholder.textContent = 'Enter a message above to see a preview';
            preview.appendChild(placeholder);
        }
    }

    document.getElementById('bannerMessage').addEventListener('input', updatePreview);
    document.getElementById('bannerType').addEventListener('change', updatePreview);

    document.getElementById('bannerForm').addEventListener('submit', function(e) {
        e.preventDefault();

        var message = document.getElementById('bannerMessage').value.trim();
        if (!message) {
            toast.warning('Banner message is required');
            return;
        }

        var expiryValue = document.getElementById('bannerExpiry').value;
        var data = {
            message: message,
            type: document.getElementById('bannerType').value,
            dismissible: document.getElementById('bannerDismissible').checked,
            expiresAt: expiryValue ? new Date(expiryValue).toISOString() : null
        };

        fetch('/api/admin/banners', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        })
        .then(function(res) {
            if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Create failed'); });
            return res.json();
        })
        .then(function() {
            toast.success('Banner created');
            closeBannerModal();
            loadBanners();
        })
        .catch(function(err) {
            console.error('Error creating banner:', err);
            toast.error(err.message || 'Failed to create banner');
        });
    });

    // ------------------------------------------------------------------
    // Delete Modal
    // ------------------------------------------------------------------
    function showDeleteModal(bannerId) {
        deleteBannerId = bannerId;
        document.getElementById('deleteBannerModal').classList.add('open');
    }

    function closeDeleteModal() {
        document.getElementById('deleteBannerModal').classList.remove('open');
        deleteBannerId = null;
    }

    document.getElementById('confirmDeleteBannerBtn').addEventListener('click', function() {
        if (!deleteBannerId) return;

        fetch('/api/admin/banners/' + deleteBannerId, { method: 'DELETE' })
            .then(function(res) {
                if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Delete failed'); });
                return res.json();
            })
            .then(function() {
                toast.success('Banner deleted');
                closeDeleteModal();
                loadBanners();
            })
            .catch(function(err) {
                console.error('Error deleting banner:', err);
                toast.error(err.message || 'Failed to delete banner');
            });
    });

    // ------------------------------------------------------------------
    // Event Listeners
    // ------------------------------------------------------------------
    document.getElementById('createBannerBtn').addEventListener('click', showCreateModal);
    document.getElementById('cancelBannerBtn').addEventListener('click', closeBannerModal);
    document.getElementById('cancelDeleteBannerBtn').addEventListener('click', closeDeleteModal);

    // Close modals on overlay click
    document.getElementById('bannerModal').addEventListener('click', function(e) {
        if (e.target === this) closeBannerModal();
    });
    document.getElementById('deleteBannerModal').addEventListener('click', function(e) {
        if (e.target === this) closeDeleteModal();
    });

    // ------------------------------------------------------------------
    // Initialize
    // ------------------------------------------------------------------
    loadBanners();

})();
