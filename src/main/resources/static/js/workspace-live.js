// Workspace landing — live UTC clock + time-of-day greeting.
// Kept small and external (CSP: script-src 'self').
(function () {
    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    function tickClock() {
        var d = new Date();
        var set = function (id, v) { var el = document.getElementById(id); if (el) el.textContent = v; };
        set('wsTimeHours',   pad(d.getUTCHours()));
        set('wsTimeMinutes', pad(d.getUTCMinutes()));
        set('wsTimeSeconds', pad(d.getUTCSeconds()));
        set('wsTimeDate',
            d.getUTCFullYear() + '-' +
            pad(d.getUTCMonth() + 1) + '-' +
            pad(d.getUTCDate())
        );
    }

    function writeGreeting(name) {
        var heading = document.getElementById('wsGreeting');
        if (!heading) return;
        var hour = new Date().getHours();
        var greet;
        if (hour < 5)       greet = 'Still up';
        else if (hour < 12) greet = 'Good morning';
        else if (hour < 17) greet = 'Good afternoon';
        else if (hour < 22) greet = 'Good evening';
        else                greet = 'Late shift';

        // Safe rebuild via DOM API — no innerHTML, no untrusted HTML injection.
        while (heading.firstChild) heading.removeChild(heading.firstChild);

        var plain = document.createElement('span');
        plain.className = 'upright';
        plain.textContent = greet + ',';
        heading.appendChild(plain);
        heading.appendChild(document.createTextNode(' '));

        var accent = document.createElement('span');
        accent.className = 'accent';
        accent.id = 'wsGreetingName';
        accent.textContent = name || 'there';
        heading.appendChild(accent);

        var period = document.createElement('span');
        period.className = 'period';
        period.textContent = '.';
        heading.appendChild(period);
    }

    function hydrateName() {
        fetch('/auth/status', { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (s) {
                if (!s || !s.authenticated) return;
                var name = s.displayName || s.username;
                if (name) writeGreeting(String(name).split(' ')[0]);
            })
            .catch(function () {});
    }

    tickClock();
    setInterval(tickClock, 1000);
    writeGreeting(); // placeholder before /api/auth/current responds
    hydrateName();
})();
