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
