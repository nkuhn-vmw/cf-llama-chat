// workspace landing page: gate wiki card on admin feature flag, then hydrate counts
(function () {
    fetch('/api/wiki/feature-status', { credentials: 'same-origin' })
        .then(function (r) { return r.ok ? r.json() : { adminEnabled: false }; })
        .then(function (s) {
            if (s.adminEnabled) {
                var card = document.getElementById('wikiNavCard');
                if (card) card.style.display = '';
                var stat = document.getElementById('wikiCount');
                if (stat && stat.parentElement) stat.parentElement.style.display = '';
            } else {
                var stat = document.getElementById('wikiCount');
                if (stat && stat.parentElement) stat.parentElement.style.display = 'none';
            }
        })
        .catch(function () {});

    Promise.allSettled([
        fetch('/api/channels').then(function (r) { return r.ok ? r.json() : []; }),
        fetch('/api/wiki/pages?limit=1000').then(function (r) { return r.ok ? r.json() : []; }),
        fetch('/api/prompts').then(function (r) { return r.ok ? r.json() : []; }),
        fetch('/api/documents').then(function (r) { return r.ok ? r.json() : []; }),
        fetch('/api/chat/available-tools').then(function (r) { return r.ok ? r.json() : []; })
    ]).then(function (results) {
        function lenOf(i) {
            var v = results[i].status === 'fulfilled' ? results[i].value : [];
            return Array.isArray(v) ? v.length : 0;
        }
        var channelLen = lenOf(0);
        var wikiLen = lenOf(1);
        var promptLen = lenOf(2);
        var docLen = lenOf(3);
        var toolLen = lenOf(4);

        function setText(id, v) {
            var el = document.getElementById(id);
            if (el) el.textContent = v;
        }
        setText('channelCount', channelLen);
        setText('wikiCount', wikiLen);
        setText('promptCount', promptLen);
        setText('documentCount', docLen);
        setText('toolCount', toolLen);

        setText('channelCountStat', channelLen);
        setText('wikiCountStat', wikiLen);
        setText('promptCountStat', promptLen);
        setText('documentCountStat', docLen);
        setText('toolCountStat', toolLen);
    });
})();
