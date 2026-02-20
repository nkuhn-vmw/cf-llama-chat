// Admin Settings Page - Auto-save settings management
(function() {
    'use strict';

    // Apply saved theme
    const savedTheme = localStorage.getItem('theme') || 'dark';
    document.body.setAttribute('data-theme', savedTheme);

    // Debounce utility for text inputs
    function debounce(fn, delay) {
        let timer;
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function() {
                fn.apply(context, args);
            }, delay);
        };
    }

    // Confirmation dialog state
    let pendingConfirm = null;

    // ------------------------------------------------------------------
    // Load all settings on page load
    // ------------------------------------------------------------------
    function loadSettings() {
        fetch('/api/admin/settings')
            .then(function(res) {
                if (!res.ok) throw new Error('Failed to load settings');
                return res.json();
            })
            .then(function(settings) {
                applySettings(settings);
            })
            .catch(function(err) {
                console.error('Error loading settings:', err);
                toast.error('Failed to load settings');
            });
    }

    function applySettings(settings) {
        // --- Text inputs and textareas ---
        document.querySelectorAll('input[type="text"][data-key], input[type="number"][data-key], textarea[data-key]').forEach(function(el) {
            var key = el.dataset.key;
            if (settings[key] !== undefined && settings[key] !== null) {
                el.value = settings[key];
            }
        });

        // --- Selects ---
        document.querySelectorAll('select[data-key]').forEach(function(el) {
            var key = el.dataset.key;
            if (settings[key] !== undefined && settings[key] !== null) {
                el.value = settings[key];
            }
        });

        // --- Toggle checkboxes ---
        document.querySelectorAll('.toggle-switch input[type="checkbox"][data-key]').forEach(function(el) {
            var key = el.dataset.key;
            if (settings[key] !== undefined && settings[key] !== null) {
                el.checked = settings[key] === 'true' || settings[key] === true;
            }
            // Update sub-settings visibility
            var targetId = el.dataset.toggleTarget;
            if (targetId) {
                var sub = document.getElementById(targetId);
                if (sub) {
                    sub.dataset.hidden = el.checked ? 'false' : 'true';
                }
            }
        });

        // --- Slider ---
        document.querySelectorAll('input[type="range"][data-key]').forEach(function(el) {
            var key = el.dataset.key;
            if (settings[key] !== undefined && settings[key] !== null) {
                el.value = settings[key];
            }
            // Update slider display value
            var valueDisplay = el.parentElement.querySelector('.slider-value');
            if (valueDisplay) {
                valueDisplay.textContent = parseFloat(el.value).toFixed(1);
            }
        });

        // --- Model whitelist checkboxes ---
        var allowedModels = settings['allowed.models'];
        if (allowedModels && typeof allowedModels === 'string' && allowedModels.trim() !== '') {
            var allowedList = allowedModels.split(',').map(function(s) { return s.trim(); });
            document.querySelectorAll('.model-checkbox').forEach(function(cb) {
                cb.checked = allowedList.indexOf(cb.value) !== -1;
            });
        }
        // If empty or not set, all remain checked (default)
    }

    // ------------------------------------------------------------------
    // Save a single setting
    // ------------------------------------------------------------------
    function saveSetting(key, value) {
        return fetch('/api/admin/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key: key, value: value })
        })
        .then(function(res) {
            if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Save failed'); });
            return res.json();
        })
        .then(function(data) {
            if (data.success) {
                toast.success('Setting saved');
            } else {
                toast.error(data.error || 'Failed to save setting');
            }
            return data;
        })
        .catch(function(err) {
            console.error('Error saving setting:', err);
            toast.error(err.message || 'Failed to save setting');
        });
    }

    // Save model whitelist as comma-separated string
    function saveModelWhitelist() {
        var checked = [];
        document.querySelectorAll('.model-checkbox:checked').forEach(function(cb) {
            checked.push(cb.value);
        });
        saveSetting('allowed.models', checked.join(','));
    }

    // ------------------------------------------------------------------
    // Confirmation dialog helpers
    // ------------------------------------------------------------------
    var confirmOverlay = document.getElementById('confirmDialog');
    var confirmTitle = document.getElementById('confirmTitle');
    var confirmMessage = document.getElementById('confirmMessage');
    var confirmCancel = document.getElementById('confirmCancel');
    var confirmProceed = document.getElementById('confirmProceed');

    var confirmMessages = {
        'maintenance-mode': {
            title: 'Enable Maintenance Mode',
            message: 'Enabling maintenance mode will prevent all users from accessing the application. Only administrators will be able to log in. Are you sure you want to continue?',
            btnClass: 'proceed'
        },
        'disable-registration': {
            title: 'Disable User Registration',
            message: 'Disabling registration will prevent any new users from creating accounts. Existing users will not be affected. Are you sure you want to continue?',
            btnClass: 'proceed-warning'
        },
        'import-config': {
            title: 'Import Configuration',
            message: 'Importing a configuration file will overwrite all current system settings. This action cannot be undone. Are you sure you want to continue?',
            btnClass: 'proceed'
        }
    };

    function showConfirmation(type, callback) {
        var cfg = confirmMessages[type];
        if (!cfg) {
            callback();
            return;
        }
        confirmTitle.textContent = cfg.title;
        confirmMessage.textContent = cfg.message;
        confirmProceed.className = 'confirm-btn ' + cfg.btnClass;
        pendingConfirm = callback;
        confirmOverlay.classList.add('open');
    }

    function hideConfirmation() {
        confirmOverlay.classList.remove('open');
        pendingConfirm = null;
    }

    confirmCancel.addEventListener('click', function() {
        hideConfirmation();
        // Revert the toggle that triggered the dialog
        loadSettings();
    });

    confirmProceed.addEventListener('click', function() {
        if (typeof pendingConfirm === 'function') {
            pendingConfirm();
        }
        hideConfirmation();
    });

    confirmOverlay.addEventListener('click', function(e) {
        if (e.target === confirmOverlay) {
            hideConfirmation();
            loadSettings();
        }
    });

    // ------------------------------------------------------------------
    // Bind event listeners
    // ------------------------------------------------------------------

    // Toggle switches
    document.querySelectorAll('.toggle-switch input[type="checkbox"][data-key]').forEach(function(el) {
        el.addEventListener('change', function() {
            var key = el.dataset.key;
            var val = el.checked ? 'true' : 'false';
            var confirmType = el.dataset.confirm;

            // Toggle sub-settings visibility
            var targetId = el.dataset.toggleTarget;
            if (targetId) {
                var sub = document.getElementById(targetId);
                if (sub) {
                    sub.dataset.hidden = el.checked ? 'false' : 'true';
                }
            }

            // For dangerous toggles that are being ENABLED (maintenance) or DISABLED (registration)
            var needsConfirm = false;
            if (confirmType === 'maintenance-mode' && el.checked) {
                needsConfirm = true;
            } else if (confirmType === 'disable-registration' && !el.checked) {
                needsConfirm = true;
            }

            if (needsConfirm) {
                showConfirmation(confirmType, function() {
                    saveSetting(key, val);
                });
            } else {
                saveSetting(key, val);
            }
        });
    });

    // Text inputs (debounced)
    var debouncedSave = debounce(function(key, value) {
        saveSetting(key, value);
    }, 600);

    document.querySelectorAll('input[type="text"][data-key], input[type="number"][data-key]').forEach(function(el) {
        el.addEventListener('input', function() {
            debouncedSave(el.dataset.key, el.value);
        });
    });

    // Textareas (debounced)
    document.querySelectorAll('textarea[data-key]').forEach(function(el) {
        el.addEventListener('input', function() {
            debouncedSave(el.dataset.key, el.value);
        });
    });

    // Select dropdowns
    document.querySelectorAll('select[data-key]').forEach(function(el) {
        el.addEventListener('change', function() {
            saveSetting(el.dataset.key, el.value);
        });
    });

    // Slider
    document.querySelectorAll('input[type="range"][data-key]').forEach(function(el) {
        el.addEventListener('input', function() {
            var valueDisplay = el.parentElement.querySelector('.slider-value');
            if (valueDisplay) {
                valueDisplay.textContent = parseFloat(el.value).toFixed(1);
            }
        });
        el.addEventListener('change', function() {
            saveSetting(el.dataset.key, el.value);
        });
    });

    // Model whitelist checkboxes
    document.querySelectorAll('.model-checkbox').forEach(function(cb) {
        cb.addEventListener('change', function() {
            saveModelWhitelist();
        });
    });

    // ------------------------------------------------------------------
    // Config Export / Import
    // ------------------------------------------------------------------
    var exportBtn = document.getElementById('exportConfigBtn');
    var importDropZone = document.getElementById('importDropZone');
    var importFileInput = document.getElementById('importFileInput');

    if (exportBtn) {
        exportBtn.addEventListener('click', function() {
            fetch('/api/admin/config/export')
                .then(function(res) {
                    if (!res.ok) throw new Error('Export failed');
                    return res.blob();
                })
                .then(function(blob) {
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url;
                    a.download = 'system-config-' + new Date().toISOString().slice(0, 10) + '.json';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    URL.revokeObjectURL(url);
                    toast.success('Configuration exported');
                })
                .catch(function(err) {
                    console.error('Error exporting config:', err);
                    toast.error('Failed to export configuration');
                });
        });
    }

    function handleImportFile(file) {
        if (!file || !file.name.endsWith('.json')) {
            toast.warning('Please select a .json file');
            return;
        }

        showConfirmation('import-config', function() {
            var formData = new FormData();
            formData.append('file', file);

            fetch('/api/admin/config/import', {
                method: 'POST',
                body: formData
            })
            .then(function(res) {
                if (!res.ok) return res.json().then(function(d) { throw new Error(d.error || 'Import failed'); });
                return res.json();
            })
            .then(function() {
                toast.success('Configuration imported successfully');
                loadSettings();
            })
            .catch(function(err) {
                console.error('Error importing config:', err);
                toast.error(err.message || 'Failed to import configuration');
            });
        });
    }

    if (importDropZone) {
        importDropZone.addEventListener('click', function() {
            importFileInput.click();
        });

        importFileInput.addEventListener('change', function() {
            if (importFileInput.files.length > 0) {
                handleImportFile(importFileInput.files[0]);
                importFileInput.value = '';
            }
        });

        importDropZone.addEventListener('dragover', function(e) {
            e.preventDefault();
            importDropZone.classList.add('drag-over');
        });

        importDropZone.addEventListener('dragleave', function() {
            importDropZone.classList.remove('drag-over');
        });

        importDropZone.addEventListener('drop', function(e) {
            e.preventDefault();
            importDropZone.classList.remove('drag-over');
            if (e.dataTransfer.files.length > 0) {
                handleImportFile(e.dataTransfer.files[0]);
            }
        });
    }

    // ------------------------------------------------------------------
    // Initialize
    // ------------------------------------------------------------------
    loadSettings();

})();
