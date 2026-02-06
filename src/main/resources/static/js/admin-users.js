// Apply saved theme
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

let deleteUserId = null;

function updateRole(select) {
    const userId = select.dataset.userId;
    const newRole = select.value;

    fetch(`/api/admin/users/${userId}/role`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ role: newRole })
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            toast.error(data.error);
            location.reload();
        }
    })
    .catch(error => {
        console.error('Error updating role:', error);
        toast.error('Failed to update role');
        location.reload();
    });
}

function showDeleteModal(btn) {
    deleteUserId = btn.dataset.userId;
    document.getElementById('deleteUsername').textContent = btn.dataset.username;
    document.getElementById('deleteConversations').checked = false;
    document.getElementById('deleteModal').classList.add('open');
}

function closeDeleteModal() {
    document.getElementById('deleteModal').classList.remove('open');
    deleteUserId = null;
}

document.getElementById('confirmDeleteBtn').addEventListener('click', function() {
    if (!deleteUserId) return;

    const deleteConversations = document.getElementById('deleteConversations').checked;

    fetch(`/api/admin/users/${deleteUserId}?deleteConversations=${deleteConversations}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            toast.error(data.error);
        } else {
            location.reload();
        }
    })
    .catch(error => {
        console.error('Error deleting user:', error);
        toast.error('Failed to delete user');
    });

    closeDeleteModal();
});

// Close modal on overlay click
document.getElementById('deleteModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeDeleteModal();
    }
});

// Reset Password Modal
let resetPasswordUserId = null;

function showResetPasswordModal(btn) {
    resetPasswordUserId = btn.dataset.userId;
    document.getElementById('resetUsername').textContent = btn.dataset.username;
    document.getElementById('newPassword').value = '';
    document.getElementById('confirmNewPassword').value = '';
    document.getElementById('resetPasswordModal').classList.add('open');
    document.getElementById('newPassword').focus();
}

function closeResetPasswordModal() {
    document.getElementById('resetPasswordModal').classList.remove('open');
    resetPasswordUserId = null;
}

document.getElementById('confirmResetBtn').addEventListener('click', function() {
    if (!resetPasswordUserId) return;

    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmNewPassword').value;

    if (!newPassword || newPassword.length < 8) {
        toast.warning('Password must be at least 8 characters with uppercase, lowercase, and a number');
        return;
    }

    if (newPassword !== confirmPassword) {
        toast.warning('Passwords do not match');
        return;
    }

    fetch(`/api/admin/users/${resetPasswordUserId}/reset-password`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ newPassword: newPassword })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            toast.success('Password reset successfully');
            closeResetPasswordModal();
        } else {
            toast.error(data.error || 'Failed to reset password');
        }
    })
    .catch(error => {
        console.error('Error resetting password:', error);
        toast.error('Failed to reset password');
    });
});

document.getElementById('resetPasswordModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeResetPasswordModal();
    }
});

// Bind event listeners (CSP-compliant, no inline handlers)
document.querySelectorAll('.role-select').forEach(select => {
    select.addEventListener('change', function() { updateRole(this); });
});

document.querySelectorAll('.action-btn.reset').forEach(btn => {
    btn.addEventListener('click', function() { showResetPasswordModal(this); });
});

document.querySelectorAll('.action-btn.danger[data-user-id]').forEach(btn => {
    btn.addEventListener('click', function() { showDeleteModal(this); });
});

document.querySelector('#deleteModal .modal-btn.cancel')?.addEventListener('click', closeDeleteModal);
document.querySelector('#resetPasswordModal .modal-btn.cancel')?.addEventListener('click', closeResetPasswordModal);
