// Workspace Wiki page — uses safe DOM construction + DOMPurify for markdown.
(function () {
    const treeEl = document.getElementById('wiki-tree');
    const emptyEl = document.getElementById('wiki-empty');
    const pageEl = document.getElementById('wiki-page');
    const logEl = document.getElementById('wiki-log-list');
    const searchInput = document.getElementById('wiki-search-input');

    let allPages = [];

    async function fetchJson(url) {
        const r = await fetch(url, { credentials: 'same-origin' });
        if (!r.ok) throw new Error(url + ': ' + r.status);
        return r.json();
    }

    // Render markdown safely. marked output is always passed through DOMPurify
    // before being inserted into the DOM.
    function renderMarkdownSafe(targetEl, md) {
        const rawHtml = window.marked.parse(md || '');
        const cleanHtml = window.DOMPurify.sanitize(rawHtml);
        const range = document.createRange();
        range.selectNodeContents(targetEl);
        range.deleteContents();
        targetEl.appendChild(range.createContextualFragment(cleanHtml));
    }

    async function loadTree() {
        try {
            allPages = await fetchJson('/api/wiki/pages?limit=500');
            renderTree(allPages);
        } catch (e) {
            treeEl.textContent = 'Failed to load wiki.';
            console.error(e);
        }
    }

    function renderTree(pages) {
        const groups = {};
        for (const p of pages) {
            (groups[p.kind] = groups[p.kind] || []).push(p);
        }
        treeEl.textContent = '';
        const order = ['ENTITY','CONCEPT','FACT','PREFERENCE','DECISION','EVENT','INDEX','LOG','NOTE'];
        for (const k of order) {
            if (!groups[k]) continue;
            const h = document.createElement('h4');
            h.textContent = k + ' (' + groups[k].length + ')';
            treeEl.appendChild(h);
            const ul = document.createElement('ul');
            for (const p of groups[k]) {
                const li = document.createElement('li');
                const a = document.createElement('a');
                a.href = '#';
                a.textContent = p.title;
                a.dataset.pageId = p.id;
                a.addEventListener('click', (e) => { e.preventDefault(); showPage(p.id); });
                li.appendChild(a);
                ul.appendChild(li);
            }
            treeEl.appendChild(ul);
        }
    }

    async function showPage(id) {
        const page = await fetchJson('/api/wiki/pages/' + id);
        emptyEl.hidden = true;
        pageEl.hidden = false;
        document.getElementById('wiki-page-title').textContent = page.title;
        document.getElementById('wiki-page-slug').textContent = page.slug;
        document.getElementById('wiki-page-kind').textContent = page.kind;
        document.getElementById('wiki-page-updated').textContent =
            page.updatedAt ? new Date(page.updatedAt).toLocaleString() : '';
        document.getElementById('wiki-page-origin').textContent = page.origin || '';

        renderMarkdownSafe(document.getElementById('wiki-page-body'), page.bodyMd);

        try {
            const history = await fetchJson('/api/wiki/pages/' + id + '/history');
            const list = document.getElementById('wiki-history-list');
            list.textContent = '';
            for (const rev of history) {
                const li = document.createElement('li');
                li.textContent = 'v' + rev.version + ' — ' + (rev.editedBy || '')
                                 + ' — ' + (rev.createdAt ? new Date(rev.createdAt).toLocaleString() : '');
                list.appendChild(li);
            }
        } catch (e) { console.warn('history load failed', e); }
    }

    async function loadLog() {
        try {
            const entries = await fetchJson('/api/wiki/log?limit=20');
            logEl.textContent = '';
            for (const e of entries) {
                const li = document.createElement('li');
                li.textContent = '[' + e.op + '] ' + (e.summary || '')
                                 + ' — ' + (e.ts ? new Date(e.ts).toLocaleTimeString() : '');
                logEl.appendChild(li);
            }
        } catch (e) { console.warn('log load failed', e); }
    }

    function debounce(fn, ms) {
        let t;
        return function () {
            const args = arguments;
            clearTimeout(t);
            t = setTimeout(() => fn.apply(null, args), ms);
        };
    }

    searchInput.addEventListener('input', debounce(async (e) => {
        const q = e.target.value.trim();
        if (!q) { renderTree(allPages); return; }
        const hits = await fetchJson('/api/wiki/search?q=' + encodeURIComponent(q) + '&k=15');
        treeEl.textContent = '';
        const ul = document.createElement('ul');
        for (const h of hits) {
            const li = document.createElement('li');
            const a = document.createElement('a');
            a.href = '#';
            a.textContent = h.title + ' (' + h.kind + ')';
            a.addEventListener('click', (ev) => { ev.preventDefault(); showPage(h.pageId); });
            li.appendChild(a);
            const snip = document.createElement('div');
            snip.className = 'wiki-snippet';
            snip.textContent = h.snippet || '';
            li.appendChild(snip);
            ul.appendChild(li);
        }
        treeEl.appendChild(ul);
    }, 200));

    loadTree();
    loadLog();
})();
