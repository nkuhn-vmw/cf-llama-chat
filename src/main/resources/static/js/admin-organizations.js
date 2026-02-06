// Apply saved theme
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

let currentOrgId = null;
let deleteOrgId = null;
let membersOrgId = null;

// Open create modal
function openCreateModal() {
    document.getElementById('modalTitle').textContent = 'Create Organization';
    document.getElementById('submitBtn').textContent = 'Create';
    document.getElementById('orgForm').reset();
    document.getElementById('orgId').value = '';

    // Reset color pickers
    document.getElementById('orgPrimaryPicker').value = '#10a37f';
    document.getElementById('orgAccentPicker').value = '#10a37f';
    document.getElementById('orgBackgroundPicker').value = '#0f0f0f';
    document.getElementById('orgSidebarPicker').value = '#0f0f0f';
    document.getElementById('orgSecondaryPicker').value = '#1a1a1a';
    document.getElementById('orgTextPicker').value = '#ffffff';

    document.getElementById('orgModal').classList.add('open');
}

// Open edit modal
async function openEditModal(btn) {
    const orgId = btn.dataset.orgId;

    try {
        const response = await fetch(`/api/admin/organizations/${orgId}`);
        if (!response.ok) throw new Error('Failed to fetch organization');

        const org = await response.json();

        document.getElementById('modalTitle').textContent = 'Edit Organization';
        document.getElementById('submitBtn').textContent = 'Save Changes';
        document.getElementById('orgId').value = org.id;
        document.getElementById('orgName').value = org.name || '';
        document.getElementById('orgSlug').value = org.slug || '';
        document.getElementById('orgWelcome').value = org.welcomeMessage || '';
        document.getElementById('orgHeaderText').value = org.headerText || 'Chat';
        document.getElementById('orgActive').value = org.active ? 'true' : 'false';
        document.getElementById('orgLogo').value = org.logoUrl || '';
        document.getElementById('orgFavicon').value = org.faviconUrl || '';

        // Set colors
        setColorField('orgPrimary', org.primaryColor || '#10a37f');
        setColorField('orgAccent', org.accentColor || '#10a37f');
        setColorField('orgBackground', org.backgroundColor || '#0f0f0f');
        setColorField('orgSidebar', org.sidebarColor || '#0f0f0f');
        setColorField('orgSecondary', org.secondaryColor || '#1a1a1a');
        setColorField('orgText', org.textColor || '#ffffff');

        document.getElementById('orgTheme').value = org.defaultTheme || 'DARK';
        document.getElementById('orgBorderRadius').value = org.borderRadius || '12px';
        document.getElementById('orgFont').value = org.fontFamily || '';
        document.getElementById('orgCustomCss').value = org.customCss || '';

        document.getElementById('orgModal').classList.add('open');
    } catch (error) {
        console.error('Error:', error);
        toast.error('Failed to load organization details');
    }
}

function setColorField(fieldId, value) {
    document.getElementById(fieldId).value = value;
    document.getElementById(fieldId + 'Picker').value = value;
}

function closeModal() {
    document.getElementById('orgModal').classList.remove('open');
}

// Form submission
document.getElementById('orgForm').addEventListener('submit', async function(e) {
    e.preventDefault();

    const formData = new FormData(this);
    const orgId = formData.get('id');
    const isEdit = orgId && orgId.length > 0;

    const data = {
        name: formData.get('name'),
        slug: formData.get('slug') || null,
        welcomeMessage: formData.get('welcomeMessage') || null,
        headerText: formData.get('headerText') || 'Chat',
        active: formData.get('active') === 'true',
        logoUrl: formData.get('logoUrl') || null,
        faviconUrl: formData.get('faviconUrl') || null,
        primaryColor: formData.get('primaryColor'),
        accentColor: formData.get('accentColor'),
        backgroundColor: formData.get('backgroundColor'),
        sidebarColor: formData.get('sidebarColor'),
        secondaryColor: formData.get('secondaryColor'),
        textColor: formData.get('textColor'),
        defaultTheme: formData.get('defaultTheme'),
        borderRadius: formData.get('borderRadius'),
        fontFamily: formData.get('fontFamily') || null,
        customCss: formData.get('customCss') || null
    };

    try {
        const url = isEdit ? `/api/admin/organizations/${orgId}` : '/api/admin/organizations';
        const method = isEdit ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (result.error) {
            toast.error(result.error);
        } else {
            location.reload();
        }
    } catch (error) {
        console.error('Error:', error);
        toast.error('Failed to save organization');
    }
});

// Delete confirmation
function confirmDelete(btn) {
    deleteOrgId = btn.dataset.orgId;
    document.getElementById('deleteOrgName').textContent = btn.dataset.orgName;
    document.getElementById('deleteModal').classList.add('open');
}

function closeDeleteModal() {
    document.getElementById('deleteModal').classList.remove('open');
    deleteOrgId = null;
}

document.getElementById('confirmDeleteBtn').addEventListener('click', async function() {
    if (!deleteOrgId) return;

    try {
        const response = await fetch(`/api/admin/organizations/${deleteOrgId}`, {
            method: 'DELETE'
        });

        const result = await response.json();

        if (result.error) {
            toast.error(result.error);
        } else {
            location.reload();
        }
    } catch (error) {
        console.error('Error:', error);
        toast.error('Failed to delete organization');
    }

    closeDeleteModal();
});

// Members modal
async function openMembersModal(btn) {
    membersOrgId = btn.dataset.orgId;
    await loadMembers();
    document.getElementById('membersModal').classList.add('open');
}

function closeMembersModal() {
    document.getElementById('membersModal').classList.remove('open');
    membersOrgId = null;
}

async function loadMembers() {
    if (!membersOrgId) return;

    try {
        const response = await fetch(`/api/admin/organizations/${membersOrgId}/members`);
        const members = await response.json();

        const container = document.getElementById('membersList');

        if (members.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted); text-align: center;">No members yet</p>';
            return;
        }

        container.innerHTML = members.map(member => `
            <div style="display: flex; justify-content: space-between; align-items: center; padding: 12px; background: var(--bg-tertiary); border-radius: 8px; margin-bottom: 8px;">
                <div>
                    <div style="font-weight: 500;">${member.displayName || member.username}</div>
                    <div style="font-size: 0.85rem; color: var(--text-muted);">${member.email || ''}</div>
                </div>
                <div style="display: flex; align-items: center; gap: 12px;">
                    <span class="status-badge active">${member.organizationRole}</span>
                    <button class="action-btn danger" data-action="removeMember" data-member-id="${member.id}" style="padding: 4px 8px;">Remove</button>
                </div>
            </div>
        `).join('');
    } catch (error) {
        console.error('Error:', error);
    }
}

async function addMember() {
    const userId = document.getElementById('addMemberUser').value;
    const role = document.getElementById('addMemberRole').value;

    if (!userId || !membersOrgId) {
        toast.warning('Please select a user');
        return;
    }

    try {
        const response = await fetch(`/api/admin/organizations/${membersOrgId}/members/${userId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: role })
        });

        const result = await response.json();

        if (result.error) {
            toast.error(result.error);
        } else {
            document.getElementById('addMemberUser').value = '';
            await loadMembers();
        }
    } catch (error) {
        console.error('Error:', error);
        toast.error('Failed to add member');
    }
}

async function removeMember(userId) {
    if (!membersOrgId) return;

    try {
        const response = await fetch(`/api/admin/organizations/${membersOrgId}/members/${userId}`, {
            method: 'DELETE'
        });

        const result = await response.json();

        if (result.error) {
            toast.error(result.error);
        } else {
            await loadMembers();
        }
    } catch (error) {
        console.error('Error:', error);
        toast.error('Failed to remove member');
    }
}

// Close modals on overlay click
document.querySelectorAll('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', function(e) {
        if (e.target === this) {
            this.classList.remove('open');
        }
    });
});

// Bind event listeners (CSP-compliant, no inline handlers)
document.querySelectorAll('[data-action="create"]').forEach(btn => {
    btn.addEventListener('click', openCreateModal);
});
// For the empty state create button that doesn't have data-action yet
document.querySelectorAll('.btn-primary').forEach(btn => {
    if (btn.textContent.trim().includes('Create')) {
        btn.addEventListener('click', openCreateModal);
    }
});

// Event delegation for table row actions
document.addEventListener('click', function(e) {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;

    switch(btn.dataset.action) {
        case 'edit': openEditModal(btn); break;
        case 'members': openMembersModal(btn); break;
        case 'delete': confirmDelete(btn); break;
        case 'removeMember': removeMember(btn.dataset.memberId); break;
    }
});

// Color picker sync
document.querySelectorAll('[data-sync-target]').forEach(input => {
    input.addEventListener('input', function() {
        const target = document.getElementById(this.dataset.syncTarget);
        if (target) target.value = this.value;
    });
});

document.querySelector('#orgModal .modal-btn.cancel')?.addEventListener('click', closeModal);
document.querySelector('#deleteModal .modal-btn.cancel')?.addEventListener('click', closeDeleteModal);
document.querySelector('#membersModal .btn-primary')?.addEventListener('click', addMember);
document.querySelector('#membersModal .modal-btn.cancel')?.addEventListener('click', closeMembersModal);
