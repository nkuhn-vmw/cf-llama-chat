// Admin Groups Page - User group management
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

(function() {
    'use strict';

    var groups = [];
    var deleteGroupId = null;
    var membersGroupId = null;

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------
    function escapeHtml(str) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(str));
        return div.innerHTML;
    }

    function clearChildren(el) {
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function createTextTd(text) {
        var td = document.createElement('td');
        td.textContent = text;
        return td;
    }

    // ------------------------------------------------------------------
    // Load groups
    // ------------------------------------------------------------------
    function loadGroups() {
        fetch('/api/admin/groups')
            .then(function(res) {
                if (!res.ok) throw new Error('Failed to load groups');
                return res.json();
            })
            .then(function(data) {
                groups = data;
                renderGroups();
                document.getElementById('totalGroups').textContent = groups.length;
            })
            .catch(function(err) {
                console.error('Error loading groups:', err);
                toast.error('Failed to load groups');
            });
    }

    function renderGroups() {
        var tbody = document.getElementById('groupsTableBody');
        clearChildren(tbody);

        if (groups.length === 0) {
            var emptyRow = document.createElement('tr');
            var emptyTd = document.createElement('td');
            emptyTd.colSpan = 4;
            emptyTd.className = 'empty-state';
            emptyTd.textContent = 'No groups configured yet.';
            emptyRow.appendChild(emptyTd);
            tbody.appendChild(emptyRow);
            return;
        }

        groups.forEach(function(group) {
            var row = document.createElement('tr');

            // Name + permissions column
            var nameTd = document.createElement('td');
            var nameDiv = document.createElement('div');
            nameDiv.style.fontWeight = '500';
            nameDiv.textContent = group.name;
            nameTd.appendChild(nameDiv);

            if (group.permissions && group.permissions.length > 0) {
                var permDiv = document.createElement('div');
                permDiv.className = 'permissions-list';
                permDiv.style.marginTop = '4px';
                group.permissions.forEach(function(p) {
                    var badge = document.createElement('span');
                    badge.className = 'permission-badge';
                    badge.textContent = p;
                    permDiv.appendChild(badge);
                });
                nameTd.appendChild(permDiv);
            }
            row.appendChild(nameTd);

            // Description column
            var descTd = document.createElement('td');
            descTd.className = 'description-cell';
            descTd.textContent = group.description || '';
            row.appendChild(descTd);

            // Members column
            var membersTd = document.createElement('td');
            var memberSpan = document.createElement('span');
            memberSpan.className = 'member-count';
            var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            svg.setAttribute('viewBox', '0 0 24 24');
            svg.setAttribute('fill', 'none');
            svg.setAttribute('stroke', 'currentColor');
            svg.setAttribute('stroke-width', '2');
            var path1 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            path1.setAttribute('d', 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2');
            var circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', '9');
            circle.setAttribute('cy', '7');
            circle.setAttribute('r', '4');
            svg.appendChild(path1);
            svg.appendChild(circle);
            memberSpan.appendChild(svg);
            memberSpan.appendChild(document.createTextNode(' ' + (group.memberCount || 0)));
            membersTd.appendChild(memberSpan);
            row.appendChild(membersTd);

            // Actions column
            var actionsTd = document.createElement('td');

            var membersBtn = document.createElement('button');
            membersBtn.className = 'action-btn primary';
            membersBtn.textContent = 'Members';
            membersBtn.addEventListener('click', function() {
                showMembersPanel(group.id, group.name);
            });
            actionsTd.appendChild(membersBtn);

            var editBtn = document.createElement('button');
            editBtn.className = 'action-btn';
            editBtn.textContent = 'Edit';
            editBtn.addEventListener('click', function() {
                showEditModal(group.id);
            });
            actionsTd.appendChild(editBtn);

            var deleteBtn = document.createElement('button');
            deleteBtn.className = 'action-btn danger';
            deleteBtn.textContent = 'Delete';
            deleteBtn.addEventListener('click', function() {
                showDeleteModal(group.id, group.name);
            });
            actionsTd.appendChild(deleteBtn);

            row.appendChild(actionsTd);
            tbody.appendChild(row);
        });
    }

    // ------------------------------------------------------------------
    // Create / Edit Modal
    // ------------------------------------------------------------------
    function showCreateModal() {
        document.getElementById('groupModalTitle').textContent = 'Create Group';
        document.getElementById('groupForm').reset();
        document.getElementById('groupId').value = '';
        document.getElementById('groupModal').classList.add('open');
        document.getElementById('groupName').focus();
    }

    function showEditModal(groupId) {
        var group = groups.find(function(g) { return g.id === groupId; });
        if (!group) return;

        document.getElementById('groupModalTitle').textContent = 'Edit Group';
        document.getElementById('groupId').value = group.id;
        document.getElementById('groupName').value = group.name || '';
        document.getElementById('groupDescription').value = group.description || '';

        // Reset all permission checkboxes
        document.querySelectorAll('#permissionsGrid input[type="checkbox"]').forEach(function(cb) {
            cb.checked = false;
        });

        // Check matching permissions
        if (group.permissions && group.permissions.length > 0) {
            group.permissions.forEach(function(perm) {
                var cb = document.querySelector('#permissionsGrid input[value="' + perm + '"]');
                if (cb) cb.checked = true;
            });
        }

        document.getElementById('groupModal').classList.add('open');
        document.getElementById('groupName').focus();
    }

    function closeGroupModal() {
        document.getElementById('groupModal').classList.remove('open');
    }

    function getSelectedPermissions() {
        var permissions = [];
        document.querySelectorAll('#permissionsGrid input[type="checkbox"]:checked').forEach(function(cb) {
            permissions.push(cb.value);
        });
        return permissions;
    }

    document.getElementById('groupForm').addEventListener('submit', function(e) {
        e.preventDefault();

        var groupId = document.getElementById('groupId').value;
        var data = {
            name: document.getElementById('groupName').value.trim(),
            description: document.getElementById('groupDescription').value.trim(),
            permissions: getSelectedPermissions()
        };

        if (!data.name) {
            toast.warning('Group name is required');
            return;
        }

        var url = '/api/admin/groups';
        var method = 'POST';

        if (groupId) {
            url = '/api/admin/groups/' + groupId;
            method = 'PUT';
        }

        fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        })
        .then(function(res) {
            if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Save failed'); });
            return res.json();
        })
        .then(function() {
            toast.success(groupId ? 'Group updated' : 'Group created');
            closeGroupModal();
            loadGroups();
        })
        .catch(function(err) {
            console.error('Error saving group:', err);
            toast.error(err.message || 'Failed to save group');
        });
    });

    // ------------------------------------------------------------------
    // Delete Modal
    // ------------------------------------------------------------------
    function showDeleteModal(groupId, groupName) {
        deleteGroupId = groupId;
        document.getElementById('deleteGroupName').textContent = groupName;
        document.getElementById('deleteGroupModal').classList.add('open');
    }

    function closeDeleteModal() {
        document.getElementById('deleteGroupModal').classList.remove('open');
        deleteGroupId = null;
    }

    document.getElementById('confirmDeleteBtn').addEventListener('click', function() {
        if (!deleteGroupId) return;

        fetch('/api/admin/groups/' + deleteGroupId, { method: 'DELETE' })
            .then(function(res) {
                if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Delete failed'); });
                return res.json();
            })
            .then(function() {
                toast.success('Group deleted');
                closeDeleteModal();
                closeMembersPanel();
                loadGroups();
            })
            .catch(function(err) {
                console.error('Error deleting group:', err);
                toast.error(err.message || 'Failed to delete group');
            });
    });

    // ------------------------------------------------------------------
    // Members Panel
    // ------------------------------------------------------------------
    function showMembersPanel(groupId, groupName) {
        membersGroupId = groupId;
        document.getElementById('membersPanelGroupName').textContent = groupName;
        document.getElementById('membersPanel').classList.add('open');
        loadMembers(groupId);
        loadUsersForSelect();
    }

    function closeMembersPanel() {
        document.getElementById('membersPanel').classList.remove('open');
        membersGroupId = null;
    }

    function loadMembers(groupId) {
        var membersList = document.getElementById('membersList');
        clearChildren(membersList);
        var loadingDiv = document.createElement('div');
        loadingDiv.className = 'empty-state';
        loadingDiv.style.padding = '16px';
        loadingDiv.textContent = 'Loading members...';
        membersList.appendChild(loadingDiv);

        fetch('/api/admin/groups/' + groupId + '/members')
            .then(function(res) {
                if (!res.ok) throw new Error('Failed to load members');
                return res.json();
            })
            .then(function(members) {
                renderMembers(members);
            })
            .catch(function(err) {
                console.error('Error loading members:', err);
                clearChildren(membersList);
                var errorDiv = document.createElement('div');
                errorDiv.className = 'empty-state';
                errorDiv.style.padding = '16px';
                errorDiv.textContent = 'Failed to load members.';
                membersList.appendChild(errorDiv);
            });
    }

    function renderMembers(members) {
        var membersList = document.getElementById('membersList');
        clearChildren(membersList);

        if (members.length === 0) {
            var emptyDiv = document.createElement('div');
            emptyDiv.className = 'empty-state';
            emptyDiv.style.padding = '16px';
            emptyDiv.textContent = 'No members in this group.';
            membersList.appendChild(emptyDiv);
            return;
        }

        members.forEach(function(member) {
            var item = document.createElement('div');
            item.className = 'member-item';

            var info = document.createElement('div');
            info.className = 'member-info';

            var name = document.createElement('span');
            name.className = 'member-name';
            name.textContent = member.displayName || member.username;
            info.appendChild(name);

            var email = document.createElement('span');
            email.className = 'member-email';
            email.textContent = member.email || '';
            info.appendChild(email);

            item.appendChild(info);

            var removeBtn = document.createElement('button');
            removeBtn.className = 'action-btn danger';
            removeBtn.textContent = 'Remove';
            removeBtn.addEventListener('click', function() {
                removeMember(member.id);
            });
            item.appendChild(removeBtn);

            membersList.appendChild(item);
        });
    }

    function loadUsersForSelect() {
        fetch('/api/admin/users')
            .then(function(res) {
                if (!res.ok) throw new Error('Failed to load users');
                return res.json();
            })
            .then(function(users) {
                var select = document.getElementById('addMemberSelect');
                clearChildren(select);
                var defaultOpt = document.createElement('option');
                defaultOpt.value = '';
                defaultOpt.textContent = 'Select a user to add...';
                select.appendChild(defaultOpt);

                users.forEach(function(user) {
                    var option = document.createElement('option');
                    option.value = user.id;
                    option.textContent = (user.displayName || user.username) + ' (' + (user.email || '') + ')';
                    select.appendChild(option);
                });
            })
            .catch(function(err) {
                console.error('Error loading users:', err);
            });
    }

    function addMember() {
        var select = document.getElementById('addMemberSelect');
        var userId = select.value;

        if (!userId || !membersGroupId) {
            toast.warning('Please select a user to add');
            return;
        }

        fetch('/api/admin/groups/' + membersGroupId + '/members/' + userId, {
            method: 'POST'
        })
        .then(function(res) {
            if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Add failed'); });
            return res.json();
        })
        .then(function() {
            toast.success('Member added');
            select.value = '';
            loadMembers(membersGroupId);
            loadGroups();
        })
        .catch(function(err) {
            console.error('Error adding member:', err);
            toast.error(err.message || 'Failed to add member');
        });
    }

    function removeMember(userId) {
        if (!membersGroupId) return;

        fetch('/api/admin/groups/' + membersGroupId + '/members/' + userId, {
            method: 'DELETE'
        })
        .then(function(res) {
            if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Remove failed'); });
            return res.json();
        })
        .then(function() {
            toast.success('Member removed');
            loadMembers(membersGroupId);
            loadGroups();
        })
        .catch(function(err) {
            console.error('Error removing member:', err);
            toast.error(err.message || 'Failed to remove member');
        });
    }

    // ------------------------------------------------------------------
    // Event Listeners
    // ------------------------------------------------------------------
    document.getElementById('createGroupBtn').addEventListener('click', showCreateModal);
    document.getElementById('cancelGroupBtn').addEventListener('click', closeGroupModal);
    document.getElementById('cancelDeleteBtn').addEventListener('click', closeDeleteModal);
    document.getElementById('closeMembersPanel').addEventListener('click', closeMembersPanel);
    document.getElementById('addMemberBtn').addEventListener('click', addMember);

    // Close modals on overlay click
    document.getElementById('groupModal').addEventListener('click', function(e) {
        if (e.target === this) closeGroupModal();
    });
    document.getElementById('deleteGroupModal').addEventListener('click', function(e) {
        if (e.target === this) closeDeleteModal();
    });

    // ------------------------------------------------------------------
    // Initialize
    // ------------------------------------------------------------------
    loadGroups();

})();
