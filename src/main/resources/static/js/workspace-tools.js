// workspace/tools page — per-user on/off preferences persisted to localStorage
(function () {
    var SVG_NS = 'http://www.w3.org/2000/svg';
    var TOOL_ICON_PATH = 'M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z';

    var toolPreferences = {};
    try {
        var saved = localStorage.getItem('toolPreferences');
        if (saved) toolPreferences = JSON.parse(saved);
    } catch (e) {}

    function isToolEnabled(toolId) {
        return toolPreferences[toolId] === undefined ? true : toolPreferences[toolId];
    }

    function savePreferences() {
        try { localStorage.setItem('toolPreferences', JSON.stringify(toolPreferences)); } catch (e) {}
    }

    function buildToolIcon() {
        var svg = document.createElementNS(SVG_NS, 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '2');
        var path = document.createElementNS(SVG_NS, 'path');
        path.setAttribute('d', TOOL_ICON_PATH);
        svg.appendChild(path);
        return svg;
    }

    function createToolCard(tool) {
        var card = document.createElement('div');
        card.className = 'tool-card';
        card.dataset.id = tool.id;

        var icon = document.createElement('div');
        icon.className = 'tool-card-icon';
        icon.appendChild(buildToolIcon());

        var info = document.createElement('div');
        info.className = 'tool-card-info';

        var nameRow = document.createElement('div');
        nameRow.className = 'tool-card-name';
        nameRow.appendChild(document.createTextNode(tool.displayName || tool.name));
        var badge = document.createElement('span');
        badge.className = 'badge ' + (tool.type === 'MCP' ? 'mcp' : 'custom');
        badge.textContent = tool.type;
        nameRow.appendChild(badge);
        info.appendChild(nameRow);

        if (tool.description) {
            var desc = document.createElement('div');
            desc.className = 'tool-card-desc';
            desc.textContent = tool.description;
            info.appendChild(desc);
        }
        if (tool.mcpServerName) {
            var server = document.createElement('div');
            server.className = 'tool-card-server';
            server.textContent = 'Server: ' + tool.mcpServerName;
            info.appendChild(server);
        }

        var toggle = document.createElement('div');
        toggle.className = 'tool-card-toggle';
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.dataset.toolId = tool.id;
        cb.checked = isToolEnabled(tool.id);
        toggle.appendChild(cb);

        card.appendChild(icon);
        card.appendChild(info);
        card.appendChild(toggle);
        card.dataset.serverName = tool.mcpServerName || '';
        return card;
    }

    fetch('/api/chat/available-tools')
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (tools) {
            var grid = document.getElementById('toolsGrid');
            var mcpTools = tools.filter(function (t) { return t.type === 'MCP'; });
            var customTools = tools.filter(function (t) { return t.type !== 'MCP'; });
            var enabledTools = tools.filter(function (t) { return isToolEnabled(t.id); });

            document.getElementById('totalToolCount').textContent = tools.length;
            document.getElementById('enabledCount').textContent = enabledTools.length;
            document.getElementById('mcpCount').textContent = mcpTools.length;
            document.getElementById('customCount').textContent = customTools.length;

            grid.textContent = '';

            if (tools.length === 0) {
                var empty = document.createElement('div');
                empty.className = 'empty-tools';
                var p = document.createElement('p');
                p.textContent = 'No tools available. MCP tools are configured by your administrator.';
                empty.appendChild(p);
                grid.appendChild(empty);
                return;
            }

            tools.forEach(function (tool) { grid.appendChild(createToolCard(tool)); });

            var serverNames = [];
            tools.forEach(function (t) {
                if (t.mcpServerName && serverNames.indexOf(t.mcpServerName) === -1) {
                    serverNames.push(t.mcpServerName);
                }
            });
            serverNames.sort();
            var filterSelect = document.getElementById('serverFilter');
            serverNames.forEach(function (name) {
                var opt = document.createElement('option');
                opt.value = name;
                opt.textContent = name;
                filterSelect.appendChild(opt);
            });

            function filterCards() {
                var selected = filterSelect.value;
                grid.querySelectorAll('.tool-card').forEach(function (card) {
                    card.style.display = (!selected || card.dataset.serverName === selected) ? '' : 'none';
                });
                document.getElementById('bulkEnableBtn').style.display = selected ? '' : 'none';
                document.getElementById('bulkDisableBtn').style.display = selected ? '' : 'none';
            }

            function bulkToggle(enabled) {
                grid.querySelectorAll('.tool-card').forEach(function (card) {
                    if (card.style.display !== 'none') {
                        var cb = card.querySelector('input[type="checkbox"]');
                        if (cb && cb.dataset.toolId) {
                            cb.checked = enabled;
                            toolPreferences[cb.dataset.toolId] = enabled;
                        }
                    }
                });
                savePreferences();
                var en = tools.filter(function (t) { return isToolEnabled(t.id); });
                document.getElementById('enabledCount').textContent = en.length;
                if (window.showToast) window.showToast(enabled ? 'All visible tools enabled' : 'All visible tools disabled');
            }

            filterSelect.addEventListener('change', filterCards);
            document.getElementById('bulkEnableBtn').addEventListener('click', function () { bulkToggle(true); });
            document.getElementById('bulkDisableBtn').addEventListener('click', function () { bulkToggle(false); });

            grid.addEventListener('change', function (e) {
                var cb = e.target;
                if (cb.dataset && cb.dataset.toolId) {
                    toolPreferences[cb.dataset.toolId] = cb.checked;
                    savePreferences();
                    var en = tools.filter(function (t) { return isToolEnabled(t.id); });
                    document.getElementById('enabledCount').textContent = en.length;
                    if (window.showToast) window.showToast(cb.checked ? 'Tool enabled' : 'Tool disabled');
                }
            });
        })
        .catch(function (err) {
            console.error('Failed to load tools:', err);
            var grid = document.getElementById('toolsGrid');
            grid.textContent = '';
            var p = document.createElement('p');
            p.textContent = 'Failed to load tools.';
            grid.appendChild(p);
        });
})();
