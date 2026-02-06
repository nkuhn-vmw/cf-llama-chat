const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

let deleteSkillId = null;

function showAddModal() {
    document.getElementById('modalTitle').textContent = 'Add Skill';
    document.getElementById('skillForm').reset();
    document.getElementById('skillId').value = '';
    document.querySelectorAll('#toolsSelector input[type="checkbox"]').forEach(cb => cb.checked = false);
    document.getElementById('skillModal').classList.add('open');
}

function showEditModal(btn) {
    const skillId = btn.dataset.skillId;
    document.getElementById('modalTitle').textContent = 'Edit Skill';

    fetch(`/api/admin/skills/${skillId}`)
        .then(response => response.json())
        .then(skill => {
            document.getElementById('skillId').value = skill.id;
            document.getElementById('skillName').value = skill.name || '';
            document.getElementById('skillDisplayName').value = skill.displayName || '';
            document.getElementById('skillDescription').value = skill.description || '';
            document.getElementById('systemPromptAugmentation').value = skill.systemPromptAugmentation || '';

            // Reset and set tool checkboxes
            document.querySelectorAll('#toolsSelector input[type="checkbox"]').forEach(cb => cb.checked = false);

            if (skill.toolIds) {
                try {
                    const toolIds = JSON.parse(skill.toolIds);
                    toolIds.forEach(id => {
                        const checkbox = document.getElementById('tool-' + id);
                        if (checkbox) checkbox.checked = true;
                    });
                } catch (e) {
                    console.error('Failed to parse tool IDs:', e);
                }
            }

            document.getElementById('skillModal').classList.add('open');
        })
        .catch(error => {
            console.error('Error:', error);
            toast.error('Failed to load skill');
        });
}

function closeSkillModal() {
    document.getElementById('skillModal').classList.remove('open');
}

document.getElementById('skillForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const skillId = document.getElementById('skillId').value;

    // Collect selected tool IDs
    const selectedTools = [];
    document.querySelectorAll('#toolsSelector input[type="checkbox"]:checked').forEach(cb => {
        selectedTools.push(cb.value);
    });

    const data = {
        name: document.getElementById('skillName').value,
        displayName: document.getElementById('skillDisplayName').value,
        description: document.getElementById('skillDescription').value,
        systemPromptAugmentation: document.getElementById('systemPromptAugmentation').value,
        toolIds: selectedTools
    };

    const url = skillId ? `/api/admin/skills/${skillId}` : '/api/admin/skills';
    const method = skillId ? 'PUT' : 'POST';

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
        toast.error('Failed to save skill');
    });
});

function toggleEnabled(checkbox) {
    const skillId = checkbox.dataset.skillId;
    const enabled = checkbox.checked;

    fetch(`/api/admin/skills/${skillId}/enabled`, {
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

function showDeleteModal(btn) {
    deleteSkillId = btn.dataset.skillId;
    document.getElementById('deleteSkillName').textContent = btn.dataset.skillName;
    document.getElementById('deleteModal').classList.add('open');
}

function closeDeleteModal() {
    document.getElementById('deleteModal').classList.remove('open');
    deleteSkillId = null;
}

document.getElementById('confirmDeleteBtn').addEventListener('click', function() {
    if (!deleteSkillId) return;

    fetch(`/api/admin/skills/${deleteSkillId}`, { method: 'DELETE' })
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
            toast.error('Failed to delete skill');
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

document.querySelectorAll('.toggle-switch input[data-skill-id]').forEach(cb => {
    cb.addEventListener('change', function() { toggleEnabled(this); });
});

document.addEventListener('click', function(e) {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    switch(btn.dataset.action) {
        case 'edit': showEditModal(btn); break;
        case 'delete': showDeleteModal(btn); break;
    }
});

document.querySelector('#skillModal .modal-btn.cancel')?.addEventListener('click', closeSkillModal);
document.querySelector('#deleteModal .modal-btn.cancel')?.addEventListener('click', closeDeleteModal);
