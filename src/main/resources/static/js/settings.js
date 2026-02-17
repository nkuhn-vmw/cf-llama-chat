/**
 * Settings page JavaScript
 * Loads, displays, and auto-saves user preferences.
 */
(function () {
    'use strict';

    // ── State ──────────────────────────────────────────────────────────────
    var preferences = {};
    var saveTimeout = null;
    var SAVE_DEBOUNCE_MS = 400;
    var FONT_SIZE_MAP = ['small', 'medium', 'large'];

    // Store original button content for restore after export
    var exportBtnOriginalContent = null;

    // ── DOM References ─────────────────────────────────────────────────────
    var els = {
        // Appearance
        themeControl: document.getElementById('themeControl'),
        backgroundControl: document.getElementById('backgroundControl'),
        fontSizeSlider: document.getElementById('fontSizeSlider'),
        fontSizeLabel: document.getElementById('fontSizeLabel'),
        densityControl: document.getElementById('densityControl'),
        // Chat
        defaultModel: document.getElementById('defaultModel'),
        defaultStreaming: document.getElementById('defaultStreaming'),
        autoTitle: document.getElementById('autoTitle'),
        systemInstructions: document.getElementById('systemInstructions'),
        charCount: document.getElementById('charCount'),
        // Language
        languageSelect: document.getElementById('languageSelect'),
        dateFormat: document.getElementById('dateFormat'),
        // Notifications
        desktopNotifications: document.getElementById('desktopNotifications'),
        soundNotifications: document.getElementById('soundNotifications'),
        // Data
        exportDataBtn: document.getElementById('exportDataBtn'),
        deleteConversationsBtn: document.getElementById('deleteConversationsBtn'),
        deleteAccountBtn: document.getElementById('deleteAccountBtn'),
        // Modals
        deleteConversationsModal: document.getElementById('deleteConversationsModal'),
        deleteAccountModal: document.getElementById('deleteAccountModal'),
        confirmDeleteConversations: document.getElementById('confirmDeleteConversations'),
        confirmDeleteAccount: document.getElementById('confirmDeleteAccount'),
        deleteAccountPassword: document.getElementById('deleteAccountPassword'),
        deleteAccountConfirm: document.getElementById('deleteAccountConfirm')
    };

    // ── API Helpers ────────────────────────────────────────────────────────

    function fetchPreferences() {
        return fetch('/api/user/preferences')
            .then(function (resp) {
                if (!resp.ok) throw new Error('Failed to load preferences');
                return resp.json();
            })
            .catch(function (err) {
                console.error('Error loading preferences:', err);
                toast.error('Failed to load preferences');
                return {};
            });
    }

    function savePreferences(prefs) {
        return fetch('/api/user/preferences', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(prefs)
        }).then(function (resp) {
            if (!resp.ok) throw new Error('Save failed');
            toast.success('Settings saved');
        }).catch(function (err) {
            console.error('Error saving preferences:', err);
            toast.error('Failed to save settings');
        });
    }

    function saveTheme(theme) {
        return fetch('/api/user/preferences/theme', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ theme: theme })
        }).then(function (resp) {
            if (!resp.ok) throw new Error('Save failed');
            document.body.setAttribute('data-theme', theme);
            localStorage.setItem('theme', theme);
            toast.success('Theme updated');
        }).catch(function (err) {
            console.error('Error saving theme:', err);
            toast.error('Failed to update theme');
        });
    }

    function saveLanguage(language) {
        return fetch('/api/user/preferences/language', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ language: language })
        }).then(function (resp) {
            if (!resp.ok) throw new Error('Save failed');
            toast.success('Language updated');
        }).catch(function (err) {
            console.error('Error saving language:', err);
            toast.error('Failed to update language');
        });
    }

    function saveBackground(background) {
        return fetch('/api/user/preferences/background', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ background: background })
        }).then(function (resp) {
            if (!resp.ok) throw new Error('Save failed');
            toast.success('Background updated');
        }).catch(function (err) {
            console.error('Error saving background:', err);
            toast.error('Failed to update background');
        });
    }

    /**
     * Debounced save for generic preference key/value pairs.
     */
    function debouncedSave(key, value) {
        preferences[key] = value;
        clearTimeout(saveTimeout);
        saveTimeout = setTimeout(function () {
            var payload = {};
            payload[key] = value;
            savePreferences(payload);
        }, SAVE_DEBOUNCE_MS);
    }

    // ── UI Helpers ─────────────────────────────────────────────────────────

    function capitalize(str) {
        if (!str) return '';
        return str.charAt(0).toUpperCase() + str.slice(1);
    }

    function updateSegmentedActive(container) {
        var labels = container.querySelectorAll('label');
        labels.forEach(function (label) {
            var radio = document.getElementById(label.getAttribute('for'));
            if (radio && radio.checked) {
                label.classList.add('active');
            } else {
                label.classList.remove('active');
            }
        });
    }

    function updateCharCount() {
        var len = els.systemInstructions.value.length;
        els.charCount.textContent = len + ' / 2000';
        els.charCount.classList.remove('near-limit', 'at-limit');
        if (len >= 2000) {
            els.charCount.classList.add('at-limit');
        } else if (len >= 1800) {
            els.charCount.classList.add('near-limit');
        }
    }

    // ── Apply Preferences to UI ───────────────────────────────────────────

    function applyPreferencesToUI(prefs) {
        // Theme
        var theme = prefs.theme || 'dark';
        var themeRadio = document.getElementById('theme' + capitalize(theme));
        if (themeRadio) themeRadio.checked = true;
        updateSegmentedActive(els.themeControl);

        // Background
        var bg = prefs.background || 'none';
        var bgValue = 'none';
        if (bg.indexOf('gradient') === 0) bgValue = 'gradient-dark';
        else if (bg.indexOf('pattern') === 0) bgValue = 'pattern-dots';
        else bgValue = bg;
        var bgRadio = els.backgroundControl.querySelector('input[value="' + bgValue + '"]');
        if (!bgRadio) bgRadio = els.backgroundControl.querySelector('input[value="none"]');
        if (bgRadio) bgRadio.checked = true;
        updateSegmentedActive(els.backgroundControl);

        // Font size
        var fontSize = prefs.fontSize || 'medium';
        var idx = FONT_SIZE_MAP.indexOf(fontSize);
        if (idx === -1) idx = 1;
        els.fontSizeSlider.value = idx;
        els.fontSizeLabel.textContent = capitalize(FONT_SIZE_MAP[idx]);

        // Density
        var density = prefs.messageDensity || 'normal';
        var densityRadio = document.getElementById('density' + capitalize(density));
        if (densityRadio) densityRadio.checked = true;
        updateSegmentedActive(els.densityControl);

        // Default model
        if (prefs.defaultModel) {
            els.defaultModel.value = prefs.defaultModel;
        }

        // Streaming
        els.defaultStreaming.checked = prefs.defaultStreaming !== false;

        // Auto-title
        els.autoTitle.checked = prefs.autoTitle !== false;

        // System instructions
        els.systemInstructions.value = prefs.systemInstructions || '';
        updateCharCount();

        // Language
        if (prefs.language) {
            els.languageSelect.value = prefs.language;
        }

        // Date format
        if (prefs.dateFormat) {
            els.dateFormat.value = prefs.dateFormat;
        }

        // Notifications
        els.desktopNotifications.checked = prefs.desktopNotifications === true;
        els.soundNotifications.checked = prefs.soundNotifications === true;
    }

    // ── Event Binding ─────────────────────────────────────────────────────

    function bindEvents() {
        // Theme
        els.themeControl.addEventListener('change', function (e) {
            if (e.target.name === 'theme') {
                updateSegmentedActive(els.themeControl);
                saveTheme(e.target.value);
            }
        });

        // Background
        els.backgroundControl.addEventListener('change', function (e) {
            if (e.target.name === 'background') {
                updateSegmentedActive(els.backgroundControl);
                saveBackground(e.target.value);
            }
        });

        // Font size
        els.fontSizeSlider.addEventListener('input', function () {
            var val = parseInt(this.value, 10);
            var label = FONT_SIZE_MAP[val] || 'medium';
            els.fontSizeLabel.textContent = capitalize(label);
            debouncedSave('fontSize', label);
        });

        // Density
        els.densityControl.addEventListener('change', function (e) {
            if (e.target.name === 'density') {
                updateSegmentedActive(els.densityControl);
                debouncedSave('messageDensity', e.target.value);
            }
        });

        // Default model
        els.defaultModel.addEventListener('change', function () {
            debouncedSave('defaultModel', this.value);
        });

        // Streaming toggle
        els.defaultStreaming.addEventListener('change', function () {
            debouncedSave('defaultStreaming', this.checked);
        });

        // Auto-title toggle
        els.autoTitle.addEventListener('change', function () {
            debouncedSave('autoTitle', this.checked);
        });

        // System instructions
        els.systemInstructions.addEventListener('input', function () {
            updateCharCount();
            debouncedSave('systemInstructions', this.value);
        });

        // Language
        els.languageSelect.addEventListener('change', function () {
            saveLanguage(this.value);
        });

        // Date format
        els.dateFormat.addEventListener('change', function () {
            debouncedSave('dateFormat', this.value);
        });

        // Desktop notifications
        els.desktopNotifications.addEventListener('change', function () {
            var checkbox = this;
            if (checkbox.checked && 'Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission().then(function (perm) {
                    if (perm !== 'granted') {
                        checkbox.checked = false;
                        toast.warning('Notification permission denied by browser');
                        return;
                    }
                    debouncedSave('desktopNotifications', true);
                });
            } else {
                debouncedSave('desktopNotifications', checkbox.checked);
            }
        });

        // Sound notifications
        els.soundNotifications.addEventListener('change', function () {
            debouncedSave('soundNotifications', this.checked);
        });

        // Export data
        exportBtnOriginalContent = els.exportDataBtn.cloneNode(true).childNodes;
        els.exportDataBtn.addEventListener('click', handleExportData);

        // Delete conversations
        els.deleteConversationsBtn.addEventListener('click', function () {
            els.deleteConversationsModal.classList.add('open');
        });

        els.confirmDeleteConversations.addEventListener('click', handleDeleteConversations);

        // Delete account
        els.deleteAccountBtn.addEventListener('click', function () {
            els.deleteAccountModal.classList.add('open');
            if (els.deleteAccountPassword) els.deleteAccountPassword.value = '';
            if (els.deleteAccountConfirm) els.deleteAccountConfirm.value = '';
            els.confirmDeleteAccount.disabled = true;
        });

        // Enable delete account button only when DELETE is typed
        if (els.deleteAccountConfirm) {
            els.deleteAccountConfirm.addEventListener('input', function () {
                els.confirmDeleteAccount.disabled = this.value !== 'DELETE';
            });
        }

        els.confirmDeleteAccount.addEventListener('click', handleDeleteAccount);

        // Modal dismiss buttons
        document.querySelectorAll('[data-dismiss]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var modalId = this.getAttribute('data-dismiss');
                var modal = document.getElementById(modalId);
                if (modal) modal.classList.remove('open');
            });
        });

        // Close modals on backdrop click
        document.querySelectorAll('.settings-modal-overlay').forEach(function (overlay) {
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay) {
                    overlay.classList.remove('open');
                }
            });
        });

        // Close modals on Escape key
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                document.querySelectorAll('.settings-modal-overlay.open').forEach(function (m) {
                    m.classList.remove('open');
                });
            }
        });
    }

    // ── Action Handlers ───────────────────────────────────────────────────

    function restoreExportButton() {
        // Remove all children and restore original
        while (els.exportDataBtn.firstChild) {
            els.exportDataBtn.removeChild(els.exportDataBtn.firstChild);
        }
        var svgNs = 'http://www.w3.org/2000/svg';
        var svg = document.createElementNS(svgNs, 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '2');
        var path = document.createElementNS(svgNs, 'path');
        path.setAttribute('d', 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4');
        svg.appendChild(path);
        var polyline = document.createElementNS(svgNs, 'polyline');
        polyline.setAttribute('points', '7 10 12 15 17 10');
        svg.appendChild(polyline);
        var line = document.createElementNS(svgNs, 'line');
        line.setAttribute('x1', '12');
        line.setAttribute('y1', '15');
        line.setAttribute('x2', '12');
        line.setAttribute('y2', '3');
        svg.appendChild(line);
        els.exportDataBtn.appendChild(svg);
        els.exportDataBtn.appendChild(document.createTextNode(' Export Data'));
    }

    function handleExportData() {
        els.exportDataBtn.disabled = true;
        els.exportDataBtn.textContent = 'Exporting...';

        fetch('/api/conversations')
            .then(function (resp) {
                if (!resp.ok) throw new Error('Failed to fetch conversations');
                return resp.json();
            })
            .then(function (conversations) {
                var fullData = {
                    exportDate: new Date().toISOString(),
                    conversationCount: conversations.length,
                    conversations: []
                };

                // Fetch messages for each conversation sequentially
                var chain = Promise.resolve();
                conversations.forEach(function (conv) {
                    chain = chain.then(function () {
                        return fetch('/api/conversations/' + conv.id + '/messages')
                            .then(function (msgResp) {
                                if (msgResp.ok) return msgResp.json();
                                return [];
                            })
                            .then(function (messages) {
                                conv.messages = messages;
                                fullData.conversations.push(conv);
                            })
                            .catch(function () {
                                fullData.conversations.push(conv);
                            });
                    });
                });

                return chain.then(function () { return fullData; });
            })
            .then(function (fullData) {
                var blob = new Blob([JSON.stringify(fullData, null, 2)], { type: 'application/json' });
                var url = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url;
                a.download = 'cf-llama-chat-export-' + new Date().toISOString().split('T')[0] + '.json';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
                toast.success('Data exported successfully');
            })
            .catch(function (err) {
                console.error('Export failed:', err);
                toast.error('Failed to export data');
            })
            .finally(function () {
                els.exportDataBtn.disabled = false;
                restoreExportButton();
            });
    }

    function handleDeleteConversations() {
        els.confirmDeleteConversations.disabled = true;
        els.confirmDeleteConversations.textContent = 'Deleting...';

        fetch('/api/conversations/clear-all', { method: 'DELETE' })
            .then(function (resp) {
                if (!resp.ok) {
                    return resp.json().catch(function () { return {}; }).then(function (data) {
                        throw new Error(data.error || 'Failed to delete conversations');
                    });
                }
                return resp.json();
            })
            .then(function (result) {
                els.deleteConversationsModal.classList.remove('open');
                toast.success('Deleted ' + (result.deletedCount || 'all') + ' conversations');
            })
            .catch(function (err) {
                console.error('Delete failed:', err);
                toast.error(err.message || 'Failed to delete conversations');
            })
            .finally(function () {
                els.confirmDeleteConversations.disabled = false;
                els.confirmDeleteConversations.textContent = 'Delete All';
            });
    }

    function handleDeleteAccount() {
        var confirmText = els.deleteAccountConfirm ? els.deleteAccountConfirm.value : '';
        if (confirmText !== 'DELETE') {
            toast.error('Please type DELETE to confirm');
            return;
        }

        var password = els.deleteAccountPassword ? els.deleteAccountPassword.value : '';

        els.confirmDeleteAccount.disabled = true;
        els.confirmDeleteAccount.textContent = 'Deleting...';

        var body = {};
        if (password) body.password = password;

        fetch('/api/user/account', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
            .then(function (resp) {
                if (!resp.ok) {
                    return resp.json().catch(function () { return {}; }).then(function (data) {
                        throw new Error(data.error || 'Failed to delete account');
                    });
                }
                toast.success('Account deleted. Redirecting...');
                setTimeout(function () {
                    window.location.href = '/login.html';
                }, 1500);
            })
            .catch(function (err) {
                console.error('Account deletion failed:', err);
                toast.error(err.message || 'Failed to delete account');
                els.confirmDeleteAccount.disabled = false;
                els.confirmDeleteAccount.textContent = 'Delete My Account';
            });
    }

    // ── Initialization ────────────────────────────────────────────────────

    function init() {
        fetchPreferences().then(function (prefs) {
            preferences = prefs;
            applyPreferencesToUI(preferences);
            bindEvents();
        });
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
