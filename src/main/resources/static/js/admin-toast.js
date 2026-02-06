// CSRF Protection - automatically adds XSRF-TOKEN header to state-changing requests
(function() {
    var originalFetch = window.fetch;
    window.fetch = function(url, options) {
        options = options || {};
        var method = (options.method || 'GET').toUpperCase();
        if (method !== 'GET' && method !== 'HEAD') {
            var match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
            if (match) {
                if (!options.headers) {
                    options.headers = {};
                }
                if (options.headers instanceof Headers) {
                    if (!options.headers.has('X-XSRF-TOKEN')) {
                        options.headers.set('X-XSRF-TOKEN', decodeURIComponent(match[1]));
                    }
                } else {
                    if (!options.headers['X-XSRF-TOKEN']) {
                        options.headers['X-XSRF-TOKEN'] = decodeURIComponent(match[1]);
                    }
                }
            }
        }
        return originalFetch.call(this, url, options);
    };
})();

// Admin Toast Notification Utility
(function() {
    let container;

    function getContainer() {
        if (!container) {
            container = document.createElement('div');
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        return container;
    }

    function show(message, type, duration) {
        type = type || 'info';
        duration = duration || 3000;

        const toast = document.createElement('div');
        toast.className = 'toast toast-' + type;
        toast.textContent = message;

        toast.addEventListener('click', function() {
            dismiss(toast);
        });

        getContainer().appendChild(toast);

        setTimeout(function() {
            dismiss(toast);
        }, duration);
    }

    function dismiss(toast) {
        if (!toast.parentNode) return;
        toast.classList.add('toast-out');
        setTimeout(function() {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 200);
    }

    window.toast = {
        success: function(msg, dur) { show(msg, 'success', dur); },
        error: function(msg, dur) { show(msg, 'error', dur || 5000); },
        warning: function(msg, dur) { show(msg, 'warning', dur); },
        info: function(msg, dur) { show(msg, 'info', dur); }
    };
})();
