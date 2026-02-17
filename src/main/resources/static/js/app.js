// CF Llama Chat - Main Application JavaScript

class ChatApp {
    constructor() {
        const appDataEl = document.getElementById('app-data');
        const appData = appDataEl ? JSON.parse(appDataEl.textContent) : {};
        this.conversationId = appData.conversationId || null;
        this.models = appData.models || [];
        this.currentUser = appData.currentUser || null;
        this.isStreaming = true;
        this.isWaiting = false;
        this.abortController = null;
        this.useDocumentContext = false;
        this.documentsAvailable = false;
        this.ragRetrievalMode = localStorage.getItem('ragRetrievalMode') || 'snippet';
        this.useTools = true;
        this.toolsAvailable = false;
        this.tools = [];
        this.toolPreferences = {};
        this.temporaryChat = false;
        this.initElements();
        this.initEventListeners();
        this.initMarkdown();
        this.initTheme();
        this.initDocuments();
        this.initTools();
        this.initPasteDetection();
        this.initKeyboardShortcuts();
        this.initSettingsSearch();
        this.initQuickActions();
        this.initSidebar();
        this.initSlashCommands();
        this.initTags();

        this.restoreModelSelection();
        this.scrollToBottom();
    }

    initElements() {
        this.sidebar = document.getElementById('sidebar');
        this.sidebarToggle = document.getElementById('sidebarToggle');
        this.conversationsList = document.getElementById('conversationsList');
        this.messagesContainer = document.getElementById('messagesContainer');
        this.messages = document.getElementById('messages');
        this.welcomeScreen = document.getElementById('welcomeScreen');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.newChatBtn = document.getElementById('newChatBtn');
        this.modelSelect = document.getElementById('modelSelect');
        this.streamToggle = document.getElementById('streamToggle');
        this.themeToggle = document.getElementById('themeToggle');
        this.userMenuBtn = document.getElementById('userMenuBtn');
        this.userMenuDropdown = document.getElementById('userMenuDropdown');

        // Document elements
        this.documentsPanel = document.getElementById('documentsPanel');
        this.docsBtn = document.getElementById('docsBtn');
        this.closePanelBtn = document.getElementById('closePanelBtn');
        this.clearHistoryBtn = document.getElementById('clearHistoryBtn');
        this.uploadArea = document.getElementById('uploadArea');
        this.fileInput = document.getElementById('fileInput');
        this.uploadProgress = document.getElementById('uploadProgress');
        this.progressFill = document.getElementById('progressFill');
        this.progressText = document.getElementById('progressText');
        this.documentsList = document.getElementById('documentsList');
        this.docCount = document.getElementById('docCount');
        this.chunkCount = document.getElementById('chunkCount');
        this.noDocuments = document.getElementById('noDocuments');
        this.useDocumentsToggle = document.getElementById('useDocumentsToggle');
        this.docToggleLabel = document.getElementById('docToggleLabel');

        // RAG retrieval mode toggle elements
        this.ragRetrievalModeSection = document.getElementById('ragRetrievalModeSection');
        this.ragModeToggle = document.getElementById('ragModeToggle');
        this.ragModeSnippetBtn = document.getElementById('ragModeSnippet');
        this.ragModeFullBtn = document.getElementById('ragModeFull');

        // Temporary chat toggle
        this.tempChatToggle = document.getElementById('tempChatToggle');
        this.tempChatToggleLabel = document.getElementById('tempChatToggleLabel');

        // Tools elements
        this.useToolsToggle = document.getElementById('useToolsToggle');
        this.toolsToggleLabel = document.getElementById('toolsToggleLabel');
    }

    initEventListeners() {
        // Chat form submission
        this.chatForm.addEventListener('submit', (e) => {
            e.preventDefault();
            this.sendMessage();
        });

        // Auto-resize textarea
        this.messageInput.addEventListener('input', () => {
            this.autoResizeTextarea();
        });

        // Enter to send (Shift+Enter for newline)
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        // New chat button
        this.newChatBtn.addEventListener('click', () => {
            this.startNewChat();
        });

        // Sidebar toggle for mobile
        this.sidebarToggle.addEventListener('click', () => {
            this.haptic();
            this.sidebar.classList.toggle('open');
            const overlay = document.getElementById('sidebarOverlay');
            if (overlay) overlay.classList.toggle('visible', this.sidebar.classList.contains('open'));
        });

        // Stream toggle
        this.streamToggle.addEventListener('change', (e) => {
            this.isStreaming = e.target.checked;
        });

        // Model selection persistence
        if (this.modelSelect) {
            this.modelSelect.addEventListener('change', () => {
                localStorage.setItem('selectedModel', this.modelSelect.value);
            });
        }

        // Conversation items click
        this.conversationsList.addEventListener('click', (e) => {
            const item = e.target.closest('.conversation-item');
            const deleteBtn = e.target.closest('.delete-btn');
            const unarchiveBtn = e.target.closest('.unarchive-btn');

            if (unarchiveBtn) {
                e.stopPropagation();
                this.unarchiveConversation(unarchiveBtn.dataset.id);
            } else if (deleteBtn) {
                e.stopPropagation();
                const id = deleteBtn.dataset.id;
                this.deleteConversation(id);
            } else if (item) {
                const id = item.dataset.id;
                this.loadConversation(id);
            }
        });

        // Right-click context menu on conversations
        this.conversationsList.addEventListener('contextmenu', (e) => {
            const item = e.target.closest('.conversation-item');
            if (item && !item.classList.contains('archived-item')) {
                e.preventDefault();
                this.showConvContextMenu(e.clientX, e.clientY, item.dataset.id);
            }
        });

        // Close context menu on click elsewhere
        document.addEventListener('click', (e) => {
            const ctxMenu = document.getElementById('convContextMenu');
            if (ctxMenu && !ctxMenu.contains(e.target)) {
                ctxMenu.style.display = 'none';
            }
            const picker = document.getElementById('folderPicker');
            if (picker && !picker.contains(e.target)) {
                picker.remove();
            }
        });

        // Quick action buttons
        document.querySelectorAll('.quick-action').forEach(btn => {
            btn.addEventListener('click', () => {
                const prompt = btn.dataset.prompt;
                this.messageInput.value = prompt;
                this.messageInput.focus();
            });
        });

        // Close sidebar on mobile when clicking outside or overlay
        document.addEventListener('click', (e) => {
            if (window.innerWidth <= 768) {
                const overlay = document.getElementById('sidebarOverlay');
                if (!this.sidebar.contains(e.target) && !this.sidebarToggle.contains(e.target)) {
                    this.sidebar.classList.remove('open');
                    if (overlay) overlay.classList.remove('visible');
                }
            }
        });

        // Theme toggle
        if (this.themeToggle) {
            this.themeToggle.addEventListener('click', () => {
                this.toggleTheme();
            });
        }

        // User menu toggle
        if (this.userMenuBtn && this.userMenuDropdown) {
            this.userMenuBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.userMenuDropdown.classList.toggle('open');
            });

            document.addEventListener('click', (e) => {
                if (!this.userMenuDropdown.contains(e.target) && !this.userMenuBtn.contains(e.target)) {
                    this.userMenuDropdown.classList.remove('open');
                }
            });
        }

        // Clear all history button
        if (this.clearHistoryBtn) {
            this.clearHistoryBtn.addEventListener('click', () => {
                this.clearAllHistory();
            });
        }

        // Temporary chat toggle
        if (this.tempChatToggle) {
            this.tempChatToggle.addEventListener('change', (e) => {
                this.temporaryChat = e.target.checked;
                localStorage.setItem('temporaryChat', this.temporaryChat);
                console.debug('Temporary chat toggle changed:', this.temporaryChat);
                // Visual indicator when temporary mode is active
                const indicator = document.getElementById('tempChatIndicator');
                if (indicator) {
                    indicator.style.display = this.temporaryChat ? 'inline-flex' : 'none';
                }
            });
            // Restore saved preference
            const savedTempChat = localStorage.getItem('temporaryChat');
            if (savedTempChat === 'true') {
                this.temporaryChat = true;
                this.tempChatToggle.checked = true;
                const indicator = document.getElementById('tempChatIndicator');
                if (indicator) indicator.style.display = 'inline-flex';
            }
        }

        // Use tools toggle — registered here (synchronous) rather than in async
        // initTools() to ensure the listener is always attached
        if (this.useToolsToggle) {
            this.useToolsToggle.addEventListener('change', (e) => {
                this.useTools = e.target.checked;
                localStorage.setItem('useTools', this.useTools);
                console.debug('Tools toggle changed:', this.useTools);
            });
        }

        // Message action buttons
        if (this.messagesContainer) {
            this.messagesContainer.addEventListener('click', (e) => {
                const actionBtn = e.target.closest('.msg-action-btn');
                if (actionBtn) {
                    const action = actionBtn.dataset.action;
                    const messageEl = actionBtn.closest('.message');
                    this.handleMessageAction(action, messageEl);
                }
            });
        }
    }

    restoreModelSelection() {
        const saved = localStorage.getItem('selectedModel');
        if (saved && this.modelSelect) {
            const option = this.modelSelect.querySelector(`option[value="${saved}"]`);
            if (option) {
                this.modelSelect.value = saved;
            }
        }
    }

    initTheme() {
        const savedTheme = localStorage.getItem('theme') || 'dark';
        document.body.setAttribute('data-theme', savedTheme);

        // Update theme toggle tooltip
        if (this.themeToggle) {
            const themeNames = { dark: 'Dark', light: 'Light', oled: 'OLED Dark' };
            this.themeToggle.title = `Theme: ${themeNames[savedTheme] || savedTheme} (click to switch)`;
        }

        // Load organization theme if user is part of an organization
        this.loadOrganizationTheme();

        // Load user's saved theme preference
        this.loadUserThemePreference();
    }

    async loadUserThemePreference() {
        try {
            const response = await fetch('/api/user/preferences');
            if (!response.ok) return;
            const data = await response.json();
            if (data && data.theme) {
                document.body.setAttribute('data-theme', data.theme);
                localStorage.setItem('theme', data.theme);
                if (this.themeToggle) {
                    const themeNames = { dark: 'Dark', light: 'Light', oled: 'OLED Dark' };
                    this.themeToggle.title = `Theme: ${themeNames[data.theme] || data.theme} (click to switch)`;
                }
            }
        } catch (e) {
            // Fall back to localStorage
        }
    }

    async loadOrganizationTheme() {
        try {
            const response = await fetch('/api/theme');
            if (!response.ok) return;

            const theme = await response.json();

            // Apply organization theme via CSS variables
            this.applyOrganizationTheme(theme);

            // Store organization info for reference
            this.organizationTheme = theme;

            // Apply organization's default theme preference if user hasn't set one
            if (!localStorage.getItem('theme') && theme.defaultTheme) {
                document.body.setAttribute('data-theme', theme.defaultTheme.toLowerCase());
            }
        } catch (error) {
            console.warn('Failed to load organization theme:', error);
        }
    }

    applyOrganizationTheme(theme) {
        if (!theme) return;

        const root = document.documentElement;

        // Apply colors if provided
        if (theme.primaryColor) {
            root.style.setProperty('--org-primary', theme.primaryColor);
            root.style.setProperty('--accent-color', theme.primaryColor);
        }
        if (theme.accentColor) {
            root.style.setProperty('--org-accent', theme.accentColor);
            // Calculate hover color (slightly darker)
            root.style.setProperty('--accent-hover', this.adjustColor(theme.accentColor, -20));
        }
        if (theme.backgroundColor) {
            root.style.setProperty('--org-bg-primary', theme.backgroundColor);
        }
        if (theme.secondaryColor) {
            root.style.setProperty('--org-bg-secondary', theme.secondaryColor);
        }
        if (theme.sidebarColor) {
            root.style.setProperty('--org-sidebar-bg', theme.sidebarColor);
        }
        if (theme.textColor) {
            root.style.setProperty('--org-text-primary', theme.textColor);
        }
        if (theme.fontFamily) {
            root.style.setProperty('--org-font-family', theme.fontFamily);
        }
        if (theme.borderRadius) {
            root.style.setProperty('--org-border-radius', theme.borderRadius);
        }

        // Update header text if provided
        if (theme.headerText) {
            const headerTitle = document.querySelector('.chat-header-title');
            if (headerTitle) {
                headerTitle.textContent = theme.headerText;
            }
        }

        // Update logo if provided
        if (theme.logoUrl) {
            const logoImg = document.querySelector('.org-logo-img');
            if (logoImg) {
                logoImg.src = theme.logoUrl;
                logoImg.style.display = 'block';
            }
        }

        // Update favicon if provided
        if (theme.faviconUrl) {
            let favicon = document.querySelector('link[rel="icon"]');
            if (!favicon) {
                favicon = document.createElement('link');
                favicon.rel = 'icon';
                document.head.appendChild(favicon);
            }
            favicon.href = theme.faviconUrl;
        }

        // Apply custom CSS if provided
        if (theme.customCss) {
            let customStyleEl = document.getElementById('org-custom-css');
            if (!customStyleEl) {
                customStyleEl = document.createElement('style');
                customStyleEl.id = 'org-custom-css';
                document.head.appendChild(customStyleEl);
            }
            customStyleEl.textContent = theme.customCss;
        }
    }

    adjustColor(color, amount) {
        // Simple color adjustment for hover states
        const hex = color.replace('#', '');
        const num = parseInt(hex, 16);
        const r = Math.max(0, Math.min(255, (num >> 16) + amount));
        const g = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amount));
        const b = Math.max(0, Math.min(255, (num & 0x0000FF) + amount));
        return '#' + (b | (g << 8) | (r << 16)).toString(16).padStart(6, '0');
    }

    toggleTheme() {
        // Show theme selector popup instead of just cycling
        this.showThemeSelector();
    }

    showThemeSelector() {
        // Remove existing popup if any
        const existing = document.getElementById('theme-selector-popup');
        if (existing) { existing.remove(); return; }

        const popup = document.createElement('div');
        popup.id = 'theme-selector-popup';
        popup.className = 'theme-selector-popup';

        const themes = [
            { id: 'dark', name: 'Dark', icon: '\u{1F319}', desc: 'Easy on the eyes' },
            { id: 'light', name: 'Light', icon: '\u{2600}\u{FE0F}', desc: 'Classic bright mode' },
            { id: 'oled', name: 'OLED Dark', icon: '\u{1F5A5}\u{FE0F}', desc: 'True black background' }
        ];

        const currentTheme = document.body.getAttribute('data-theme') || 'dark';

        themes.forEach(theme => {
            const option = document.createElement('button');
            option.className = 'theme-option' + (theme.id === currentTheme ? ' active' : '');
            option.innerHTML = `<span class="theme-icon">${theme.icon}</span><span class="theme-name">${theme.name}</span><span class="theme-desc">${theme.desc}</span>`;
            option.addEventListener('click', () => {
                document.body.setAttribute('data-theme', theme.id);
                localStorage.setItem('theme', theme.id);
                // Save to server for per-user persistence
                fetch('/api/user/preferences/theme', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ theme: theme.id })
                }).catch(() => {});
                if (this.themeToggle) {
                    const themeNames = { dark: 'Dark', light: 'Light', oled: 'OLED Dark' };
                    this.themeToggle.title = 'Theme: ' + (themeNames[theme.id] || theme.id);
                }
                popup.remove();
                this.haptic();
            });
            popup.appendChild(option);
        });

        // Position near the theme toggle button
        if (this.themeToggle) {
            const rect = this.themeToggle.getBoundingClientRect();
            popup.style.position = 'fixed';
            popup.style.top = (rect.bottom + 4) + 'px';
            popup.style.left = rect.left + 'px';
            popup.style.zIndex = '10000';
        }

        document.body.appendChild(popup);

        // Close on outside click
        const closeHandler = (e) => {
            if (!popup.contains(e.target) && e.target !== this.themeToggle) {
                popup.remove();
                document.removeEventListener('click', closeHandler);
            }
        };
        setTimeout(() => document.addEventListener('click', closeHandler), 10);
    }

    // DOMPurify config that allows KaTeX MathML output
    domPurifyConfig = {
        ADD_TAGS: ['math', 'mi', 'mo', 'mn', 'ms', 'mtext', 'mrow', 'mfrac', 'msqrt',
                   'mroot', 'msup', 'msub', 'msubsup', 'munder', 'mover', 'munderover',
                   'mtable', 'mtr', 'mtd', 'mspace', 'mpadded', 'mphantom', 'mfenced',
                   'menclose', 'semantics', 'annotation', 'annotation-xml'],
        ADD_ATTR: ['mathvariant', 'fence', 'separator', 'accent', 'accentunder',
                   'lspace', 'rspace', 'stretchy', 'symmetric', 'maxsize', 'minsize',
                   'largeop', 'movablelimits', 'columnalign', 'rowalign', 'columnspan',
                   'rowspan', 'columnlines', 'rowlines', 'frame', 'framespacing',
                   'equalrows', 'equalcolumns', 'displaystyle', 'scriptlevel',
                   'linethickness', 'depth', 'height', 'width', 'xmlns', 'encoding']
    };

    protectMathDelimiters(text) {
        const blocks = [];
        let result = text;
        // Protect display math $$...$$ first (greedy within lines or across lines)
        result = result.replace(/\$\$([\s\S]+?)\$\$/g, (match, inner) => {
            const idx = blocks.length;
            blocks.push({ type: 'block', content: match });
            return `MATH_BLOCK_${idx}`;
        });
        // Protect inline math $...$ (non-greedy, single line, not preceded/followed by digit)
        result = result.replace(/(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)/g, (match, inner) => {
            // Skip if it looks like a currency amount (e.g. $100)
            if (/^\d/.test(inner.trim())) return match;
            const idx = blocks.length;
            blocks.push({ type: 'inline', content: match });
            return `MATH_INLINE_${idx}`;
        });
        return { text: result, blocks };
    }

    restoreMathDelimiters(html, blocks) {
        let result = html;
        for (let i = 0; i < blocks.length; i++) {
            const placeholder = blocks[i].type === 'block' ? `MATH_BLOCK_${i}` : `MATH_INLINE_${i}`;
            result = result.replace(placeholder, blocks[i].content);
        }
        return result;
    }

    /**
     * Extract LaTeX blocks ($$...$$ and $...$) before markdown parsing
     * to prevent markdown from mangling them. Returns the sanitized text
     * and an array of extracted blocks with their placeholders.
     */
    preprocessLatex(text) {
        const blocks = [];
        let result = text;

        // Extract display math $$...$$ first
        result = result.replace(/\$\$([\s\S]+?)\$\$/g, (match, inner) => {
            const idx = blocks.length;
            blocks.push({ type: 'display', latex: inner.trim(), original: match });
            return `%%LATEX_DISPLAY_${idx}%%`;
        });

        // Extract display math \[...\]
        result = result.replace(/\\\[([\s\S]+?)\\\]/g, (match, inner) => {
            const idx = blocks.length;
            blocks.push({ type: 'display', latex: inner.trim(), original: match });
            return `%%LATEX_DISPLAY_${idx}%%`;
        });

        // Extract inline math $...$ (not currency like $100)
        result = result.replace(/(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)/g, (match, inner) => {
            if (/^\d/.test(inner.trim())) return match;
            const idx = blocks.length;
            blocks.push({ type: 'inline', latex: inner.trim(), original: match });
            return `%%LATEX_INLINE_${idx}%%`;
        });

        // Extract inline math \(...\)
        result = result.replace(/\\\(([\s\S]+?)\\\)/g, (match, inner) => {
            const idx = blocks.length;
            blocks.push({ type: 'inline', latex: inner.trim(), original: match });
            return `%%LATEX_INLINE_${idx}%%`;
        });

        return { text: result, blocks };
    }

    /**
     * Render extracted LaTeX blocks back into the HTML using KaTeX.
     * Replaces placeholders with rendered KaTeX HTML output.
     */
    postprocessLatex(html, blocks) {
        let result = html;
        for (let i = 0; i < blocks.length; i++) {
            const block = blocks[i];
            const isDisplay = block.type === 'display';
            const placeholder = isDisplay ? `%%LATEX_DISPLAY_${i}%%` : `%%LATEX_INLINE_${i}%%`;

            let rendered;
            if (typeof katex !== 'undefined') {
                try {
                    rendered = katex.renderToString(block.latex, {
                        displayMode: isDisplay,
                        throwOnError: false,
                        output: 'htmlAndMathml'
                    });
                } catch (e) {
                    console.warn('KaTeX render error for block', i, ':', e);
                    rendered = block.original;
                }
            } else {
                // KaTeX not available, restore original delimiters
                rendered = block.original;
            }
            result = result.replace(placeholder, rendered);
        }
        return result;
    }

    /**
     * Full rendering pipeline: preprocess LaTeX, parse markdown, postprocess LaTeX,
     * and optionally sanitize with DOMPurify.
     */
    renderMarkdownWithLatex(text) {
        const { text: preprocessed, blocks } = this.preprocessLatex(text);
        let html = marked.parse(preprocessed);
        html = this.postprocessLatex(html, blocks);
        return this.sanitizeHtml(html);
    }

    /**
     * RAG Citation rendering - replaces [Source N] markers with styled badges.
     */
    renderCitations(html, citations) {
        if (!citations || citations.length === 0) return html;

        return html.replace(/\[Source (\d+)\]/g, (match, num) => {
            const idx = parseInt(num) - 1;
            const citation = citations[idx];
            if (!citation) return match;

            const relevance = Math.round(citation.relevance * 100);
            return `<span class="citation-badge" data-source="${num}" title="${citation.documentName} (${relevance}% relevance)">[${num}]</span>`;
        });
    }

    /**
     * Sanitize HTML output using DOMPurify if available.
     */
    sanitizeHtml(html) {
        if (typeof DOMPurify !== 'undefined') {
            return DOMPurify.sanitize(html, this.domPurifyConfig);
        }
        return html;
    }

    /**
     * Detect text direction (RTL vs LTR) based on the proportion of RTL
     * script characters in the first 100 characters of the text.
     */
    detectDirection(text) {
        const rtl = /[\u0600-\u06FF\u0750-\u077F\u0590-\u05FF\uFB1D-\uFB4F]/;
        const sample = text.substring(0, 100);
        const count = (sample.match(new RegExp(rtl.source, 'g')) || []).length;
        return count > sample.length * 0.3 ? 'rtl' : 'ltr';
    }

    renderMath(element) {
        if (typeof renderMathInElement === 'function') {
            try {
                renderMathInElement(element, {
                    delimiters: [
                        { left: '$$', right: '$$', display: true },
                        { left: '\\[', right: '\\]', display: true },
                        { left: '$', right: '$', display: false },
                        { left: '\\(', right: '\\)', display: false }
                    ],
                    throwOnError: false,
                    ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
                });
            } catch (e) {
                console.warn('KaTeX rendering error:', e);
            }
        }
    }

    initMarkdown() {
        // Custom renderer for marked v12 (positional arguments, not object destructuring)
        const renderer = {
            code(text, lang, escaped) {
                const safeText = text || '';
                const validLang = lang && hljs.getLanguage(lang) ? lang : '';
                const langClass = validLang ? `language-${validLang}` : '';
                let highlighted;
                try {
                    highlighted = validLang
                        ? hljs.highlight(safeText, { language: validLang, ignoreIllegals: true }).value
                        : hljs.highlightAuto(safeText).value;
                } catch (e) {
                    highlighted = safeText;
                }
                return `<pre><code class="hljs ${langClass}">${highlighted}</code></pre>`;
            },
            table(header, body) {
                return `<div class="table-wrapper"><table class="markdown-table"><thead>${header || ''}</thead><tbody>${body || ''}</tbody></table></div>`;
            }
        };

        marked.use({ renderer, breaks: true, gfm: true });
    }

    prepareStreamingContent(raw) {
        let safe = raw;

        // 1. Close unclosed code fences so partial code is visible during streaming
        const fencePattern = /^```/gm;
        const fences = safe.match(fencePattern);
        if (fences && fences.length % 2 !== 0) {
            safe = safe + '\n```';
        }

        // 2. Close partial table rows (starts with | but doesn't end with |)
        const lines = safe.split('\n');
        const lastLine = lines[lines.length - 1];
        if (lastLine && lastLine.trimStart().startsWith('|') && !lastLine.trimEnd().endsWith('|')) {
            lines[lines.length - 1] = lastLine + ' |';
            safe = lines.join('\n');
        }

        return safe;
    }

    autoResizeTextarea() {
        this.messageInput.style.height = 'auto';
        this.messageInput.style.height = Math.min(this.messageInput.scrollHeight, 200) + 'px';
    }

    async sendMessage(overrideText) {
        const message = overrideText ? overrideText.trim() : this.messageInput.value.trim();
        if (!message || this.isWaiting) return;

        this.haptic();
        this.isWaiting = true;
        this.sendBtn.disabled = true;

        // Get selected model — split on first colon only (model IDs may contain colons)
        const modelValue = this.modelSelect.value;
        const colonIdx = modelValue ? modelValue.indexOf(':') : -1;
        const provider = colonIdx > -1 ? modelValue.substring(0, colonIdx) : 'openai';
        const model = colonIdx > -1 ? modelValue.substring(colonIdx + 1) : (modelValue || 'gpt-4o-mini');

        // Add user message to UI
        this.addMessage('user', message);
        this.messageInput.value = '';
        this.autoResizeTextarea();

        // Hide welcome screen if visible
        if (this.welcomeScreen) {
            this.welcomeScreen.style.display = 'none';
            if (!this.messages) {
                const messagesDiv = document.createElement('div');
                messagesDiv.className = 'messages';
                messagesDiv.id = 'messages';
                this.messagesContainer.appendChild(messagesDiv);
                this.messages = messagesDiv;
            }
        }

        // Show typing indicator
        const typingIndicator = this.showTypingIndicator();

        try {
            // Read toggle state directly from DOM for reliability
            const toolsEnabled = this.useToolsToggle ? this.useToolsToggle.checked : this.useTools;

            const useDocContext = this.useDocumentContext && this.documentsAvailable;
            const request = {
                conversationId: this.temporaryChat ? null : this.conversationId,
                message: message,
                provider: provider,
                model: model,
                useDocumentContext: useDocContext,
                useTools: toolsEnabled && this.toolsAvailable,
                temporary: this.temporaryChat,
                ragRetrievalMode: useDocContext ? this.ragRetrievalMode : null
            };

            console.debug('Chat request - useTools:', request.useTools, '(toggle:', toolsEnabled, ', available:', this.toolsAvailable, '), temporary:', request.temporary, ', ragMode:', request.ragRetrievalMode);

            if (this.isStreaming) {
                await this.sendStreamingMessage(request, typingIndicator);
            } else {
                await this.sendRegularMessage(request, typingIndicator);
            }
        } catch (error) {
            console.error('Error sending message:', error);
            typingIndicator.remove();
            this.addErrorMessage('Failed to send message. Please try again.');
        } finally {
            this.isWaiting = false;
            this.sendBtn.disabled = false;
            this.messageInput.focus();
        }
    }

    async sendRegularMessage(request, typingIndicator) {
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error('Failed to send message');
        }

        const data = await response.json();
        typingIndicator.remove();

        // For temporary chats, do not update conversationId or URL
        if (!data.temporary) {
            this.conversationId = data.conversationId;
            this.updateURL(data.conversationId);
            this.refreshConversationsList();
        }
        this.addMessage('assistant', data.content, data.htmlContent, data.model, data.citations);
    }

    async sendStreamingMessage(request, typingIndicator) {
        this.abortController = new AbortController();
        this.showCancelButton();

        try {
            const response = await fetch('/api/chat/stream', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(request),
                signal: this.abortController.signal
            });

            if (!response.ok) {
                throw new Error('Failed to send message');
            }

            typingIndicator.remove();

            // Create assistant message element
            const messageEl = this.createMessageElement('assistant', '');
            this.messages.appendChild(messageEl);
            const textEl = messageEl.querySelector('.message-text');
            let fullContent = '';
            let streamComplete = false;

            // Streaming debounce: re-render content at most every 100ms
            let streamRenderTimer = null;
            let pendingRender = false;
            const debouncedStreamRender = () => {
                pendingRender = true;
                if (streamRenderTimer) return;
                streamRenderTimer = setTimeout(() => {
                    streamRenderTimer = null;
                    if (pendingRender) {
                        pendingRender = false;
                        const displayContent = this.prepareStreamingContent(fullContent);
                        textEl.innerHTML = this.renderMarkdownWithLatex(displayContent);
                        this.scrollToBottom();
                    }
                }, 100);
            };

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';  // Buffer for incomplete SSE events

            while (true) {
                const { value, done } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');

                // Keep the last line in buffer if it's incomplete (doesn't end with newline)
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        try {
                            const jsonStr = line.slice(5).trim();
                            if (!jsonStr) continue;
                            const data = JSON.parse(jsonStr);

                            if (!this.conversationId && data.conversationId && !data.temporary) {
                                this.conversationId = data.conversationId;
                                this.updateURL(data.conversationId);
                            }

                            if (data.error) {
                                streamComplete = true;
                                // Clear any pending debounced render
                                if (streamRenderTimer) { clearTimeout(streamRenderTimer); streamRenderTimer = null; }
                                const errorHtml = `<div class="stream-error">${this.escapeHtml(data.error)}</div>`;
                                textEl.innerHTML = this.sanitizeHtml(errorHtml);
                                this.scrollToBottom();
                            } else if (data.complete) {
                                streamComplete = true;
                                // Clear any pending debounced render
                                if (streamRenderTimer) { clearTimeout(streamRenderTimer); streamRenderTimer = null; }
                                // Final message with full HTML content from backend
                                if (data.htmlContent) {
                                    let finalHtml = this.sanitizeHtml(data.htmlContent);
                                    if (data.citations && data.citations.length > 0) {
                                        finalHtml = this.renderCitations(finalHtml, data.citations);
                                    }
                                    textEl.innerHTML = finalHtml;
                                }
                                this.highlightCode(textEl);
                                this.renderMath(textEl);
                                this.renderArtifacts(textEl);

                                // Update RTL direction based on final content
                                if (fullContent) {
                                    messageEl.setAttribute('dir', this.detectDirection(fullContent));
                                }

                                // Add performance metrics if available (check for existence, not truthiness)
                                const hasMetrics = data.timeToFirstTokenMs != null ||
                                                   data.tokensPerSecond != null ||
                                                   data.totalResponseTimeMs != null;

                                if (hasMetrics) {
                                    const metaEl = messageEl.querySelector('.message-meta') ||
                                        (() => {
                                            const meta = document.createElement('div');
                                            meta.className = 'message-meta';
                                            messageEl.querySelector('.message-content').appendChild(meta);
                                            return meta;
                                        })();

                                    let metricsText = [];
                                    if (data.model) metricsText.push(data.model);
                                    if (data.tokensPerSecond != null && data.tokensPerSecond > 0) {
                                        metricsText.push(`${data.tokensPerSecond.toFixed(1)} t/s`);
                                    }
                                    if (data.timeToFirstTokenMs != null) {
                                        metricsText.push(`TTFT: ${data.timeToFirstTokenMs}ms`);
                                    }
                                    if (data.totalResponseTimeMs != null) {
                                        metricsText.push(`Total: ${(data.totalResponseTimeMs / 1000).toFixed(1)}s`);
                                    }
                                    if (metricsText.length > 0) {
                                        metaEl.textContent = metricsText.join(' | ');
                                    }
                                }

                                if (!data.temporary) {
                                    this.refreshConversationsList();
                                }
                            } else if (data.content) {
                                fullContent += data.content;
                                // Use debounced rendering during streaming (100ms)
                                debouncedStreamRender();
                            }
                        } catch (e) {
                            console.warn('SSE parse error:', e.message, 'Line:', line);
                        }
                    }
                }
            }

            // Clear any pending debounced render before final processing
            if (streamRenderTimer) { clearTimeout(streamRenderTimer); streamRenderTimer = null; }

            // Flush any remaining data left in the SSE buffer (e.g. final event
            // that arrived without a trailing newline before the connection closed)
            if (!streamComplete && buffer.trim()) {
                const remaining = buffer.trim();
                if (remaining.startsWith('data:')) {
                    try {
                        const jsonStr = remaining.slice(5).trim();
                        if (jsonStr) {
                            const data = JSON.parse(jsonStr);
                            if (data.complete && data.htmlContent) {
                                streamComplete = true;
                                let finalHtml = this.sanitizeHtml(data.htmlContent);
                                if (data.citations && data.citations.length > 0) {
                                    finalHtml = this.renderCitations(finalHtml, data.citations);
                                }
                                textEl.innerHTML = finalHtml;
                                this.highlightCode(textEl);
                                this.renderMath(textEl);
                                this.renderArtifacts(textEl);
                            } else if (data.content) {
                                fullContent += data.content;
                            }
                        }
                    } catch (e) {
                        console.warn('SSE buffer flush parse error:', e.message);
                    }
                }
            }

            // Fallback: only re-render if stream ended without a complete event
            if (fullContent && !streamComplete) {
                textEl.innerHTML = this.renderMarkdownWithLatex(fullContent);
                this.highlightCode(textEl);
                this.renderMath(textEl);
                this.renderArtifacts(textEl);
                // Update RTL direction based on final content
                messageEl.setAttribute('dir', this.detectDirection(fullContent));
                this.scrollToBottom();
            }
        } finally {
            this.hideCancelButton();
            this.abortController = null;
        }
    }

    showCancelButton() {
        if (!this.sendBtn) return;
        this._originalBtnHtml = this.sendBtn.innerHTML;
        this.sendBtn.textContent = 'Cancel';
        this.sendBtn.classList.add('cancel-mode');
        this.sendBtn.disabled = false;
        this.sendBtn.onclick = () => this.cancelStream();
    }

    hideCancelButton() {
        if (!this.sendBtn) return;
        if (this._originalBtnHtml) {
            this.sendBtn.innerHTML = this._originalBtnHtml;
        }
        this.sendBtn.classList.remove('cancel-mode');
        this.sendBtn.onclick = null;
    }

    cancelStream() {
        if (this.abortController) {
            this.abortController.abort();
            this.abortController = null;
        }
        this.hideCancelButton();
    }

    addMessage(role, content, htmlContent = null, model = null, citations = null, messageId = null) {
        if (!this.messages) {
            const messagesDiv = document.createElement('div');
            messagesDiv.className = 'messages';
            messagesDiv.id = 'messages';
            this.messagesContainer.appendChild(messagesDiv);
            this.messages = messagesDiv;
        }

        const messageEl = this.createMessageElement(role, content, htmlContent, model, citations, messageId);
        this.messages.appendChild(messageEl);
        this.highlightCode(messageEl);
        this.renderMath(messageEl);
        this.renderArtifacts(messageEl);
        this.scrollToBottom();
    }

    createMessageElement(role, content, htmlContent = null, model = null, citations = null, messageId = null) {
        const div = document.createElement('div');
        div.className = `message ${role}`;
        if (messageId) div.dataset.messageId = messageId;

        // Detect text direction for RTL language support
        const rawText = content || '';
        const dir = this.detectDirection(rawText);
        div.setAttribute('dir', dir);

        const avatarText = role === 'user' ? 'U' : 'AI';
        // Use the enhanced rendering pipeline with LaTeX preprocessing and DOMPurify sanitization
        const displayContent = htmlContent
            ? this.sanitizeHtml(htmlContent)
            : (content ? this.renderMarkdownWithLatex(content) : '');

        // Build message element using safe DOM construction
        const avatarDiv = document.createElement('div');
        avatarDiv.className = 'message-avatar';
        avatarDiv.textContent = avatarText;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        // displayContent is sanitized via DOMPurify in renderMarkdownWithLatex / sanitizeHtml
        textDiv.innerHTML = displayContent;

        // Apply citation rendering if citations are provided
        if (citations && citations.length > 0 && role === 'assistant') {
            textDiv.innerHTML = this.renderCitations(textDiv.innerHTML, citations);
        }

        contentDiv.appendChild(textDiv);

        if (model) {
            const metaDiv = document.createElement('div');
            metaDiv.className = 'message-meta';
            metaDiv.textContent = model;
            contentDiv.appendChild(metaDiv);
        }

        // Add message actions toolbar
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'message-actions';

        if (role === 'user') {
            // Edit button for user messages
            actionsDiv.innerHTML = `
                <button class="msg-action-btn" data-action="edit" title="Edit message">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                </button>
                <button class="msg-action-btn" data-action="copy" title="Copy message">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                </button>
            `;
        } else {
            // Regenerate + copy + favorite for assistant messages
            actionsDiv.innerHTML = `
                <button class="msg-action-btn" data-action="regenerate" title="Regenerate response">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <polyline points="23 4 23 10 17 10"></polyline>
                        <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
                    </svg>
                </button>
                <button class="msg-action-btn" data-action="favorite" title="Favorite response">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                    </svg>
                </button>
                <button class="msg-action-btn" data-action="copy" title="Copy message">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                </button>
                <button class="msg-action-btn" data-action="translate" title="Translate">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <path d="M5 8l6 6"></path>
                        <path d="M4 14l6-6 2-3"></path>
                        <path d="M2 5h12"></path>
                        <path d="M7 2h1"></path>
                        <path d="M22 22l-5-10-5 10"></path>
                        <path d="M14 18h6"></path>
                    </svg>
                </button>
            `;
        }

        contentDiv.appendChild(actionsDiv);

        div.appendChild(avatarDiv);
        div.appendChild(contentDiv);

        return div;
    }

    showTypingIndicator() {
        const div = document.createElement('div');
        div.className = 'message assistant';
        div.innerHTML = `
            <div class="message-avatar">AI</div>
            <div class="message-content">
                <div class="typing-indicator">
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            </div>
        `;
        this.messages?.appendChild(div);
        this.scrollToBottom();
        return div;
    }

    addErrorMessage(text) {
        const div = document.createElement('div');
        div.className = 'error-message';
        div.textContent = text;
        this.messages?.appendChild(div);
        this.scrollToBottom();
    }

    // Interactive Artifacts - renders HTML/SVG code blocks as sandboxed iframes
    renderArtifacts(container) {
        if (!container) return;
        container.querySelectorAll('pre code').forEach(codeBlock => {
            const lang = (codeBlock.className.match(/language-(\w+)/) || [])[1];
            if (lang && ['html', 'svg'].includes(lang.toLowerCase())) {
                const code = codeBlock.textContent;
                const wrapper = document.createElement('div');
                wrapper.className = 'artifact-container';
                wrapper.style.cssText = 'margin: 8px 0; position: relative;';

                const toggleBtn = document.createElement('button');
                toggleBtn.textContent = '\u25b6 Run';
                toggleBtn.className = 'artifact-toggle';
                toggleBtn.style.cssText = 'background: var(--accent-color, #4a9eff); color: white; border: none; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 12px; margin-bottom: 4px;';

                const iframe = document.createElement('iframe');
                iframe.sandbox = 'allow-scripts';
                iframe.style.cssText = 'width:100%;border:1px solid var(--border-color, #ddd);border-radius:8px;min-height:200px;display:none;background:white;';

                toggleBtn.addEventListener('click', () => {
                    if (iframe.style.display === 'none') {
                        iframe.srcdoc = code;
                        iframe.style.display = 'block';
                        toggleBtn.textContent = '\u25bc Hide';
                    } else {
                        iframe.style.display = 'none';
                        iframe.srcdoc = '';
                        toggleBtn.textContent = '\u25b6 Run';
                    }
                });

                wrapper.appendChild(toggleBtn);
                wrapper.appendChild(iframe);
                codeBlock.parentElement.after(wrapper);
            }
        });
    }

    highlightCode(element) {
        element.querySelectorAll('pre code').forEach((block) => {
            // Apply syntax highlighting
            hljs.highlightElement(block);

            // Get the pre element (parent of code)
            const pre = block.parentElement;
            if (!pre || pre.querySelector('.code-block-header')) {
                return; // Already enhanced
            }

            // Detect language from hljs classes
            const classes = block.className.split(' ');
            let language = 'code';
            for (const cls of classes) {
                if (cls.startsWith('language-')) {
                    language = cls.replace('language-', '');
                    break;
                } else if (cls.startsWith('hljs-')) {
                    continue;
                } else if (cls && cls !== 'hljs') {
                    language = cls;
                    break;
                }
            }

            // Create header with language label, edit and copy buttons
            const header = document.createElement('div');
            header.className = 'code-block-header';
            header.innerHTML = `
                <span class="code-language">${this.escapeHtml(language)}</span>
                <div class="code-block-actions">
                    <button class="edit-code-btn" title="Edit code">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                        </svg>
                        <span>Edit</span>
                    </button>
                    <button class="copy-code-btn" title="Copy code">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        </svg>
                        <span>Copy</span>
                    </button>
                </div>
            `;

            // Insert header before the pre content
            pre.insertBefore(header, pre.firstChild);

            // Add edit functionality
            const editBtn = header.querySelector('.edit-code-btn');
            editBtn.addEventListener('click', () => {
                const isEditing = pre.classList.contains('editing');
                if (isEditing) {
                    // Save mode: read textarea, update code block, re-highlight
                    const textarea = pre.querySelector('.code-edit-textarea');
                    if (textarea) {
                        block.textContent = textarea.value;
                        hljs.highlightElement(block);
                        textarea.remove();
                        block.style.display = '';
                        // Update line numbers if present
                        const lineNums = pre.querySelector('.line-numbers');
                        if (lineNums) {
                            const lines = block.textContent.split('\n');
                            lineNums.innerHTML = lines.map((_, i) => `<span>${i + 1}</span>`).join('');
                            lineNums.style.display = '';
                        }
                    }
                    pre.classList.remove('editing');
                    editBtn.querySelector('span').textContent = 'Edit';
                } else {
                    // Edit mode: hide code, show textarea
                    const textarea = document.createElement('textarea');
                    textarea.className = 'code-edit-textarea';
                    textarea.value = block.textContent;
                    textarea.spellcheck = false;
                    block.style.display = 'none';
                    const lineNums = pre.querySelector('.line-numbers');
                    if (lineNums) lineNums.style.display = 'none';
                    pre.appendChild(textarea);
                    // Auto-size textarea
                    textarea.style.height = 'auto';
                    textarea.style.height = textarea.scrollHeight + 'px';
                    textarea.addEventListener('input', () => {
                        textarea.style.height = 'auto';
                        textarea.style.height = textarea.scrollHeight + 'px';
                    });
                    pre.classList.add('editing');
                    editBtn.querySelector('span').textContent = 'Save';
                    textarea.focus();
                }
            });

            // Add copy functionality
            const copyBtn = header.querySelector('.copy-code-btn');
            copyBtn.addEventListener('click', async () => {
                const code = pre.classList.contains('editing')
                    ? (pre.querySelector('.code-edit-textarea')?.value || block.textContent)
                    : block.textContent;
                try {
                    await navigator.clipboard.writeText(code);
                    copyBtn.classList.add('copied');
                    copyBtn.querySelector('span').textContent = 'Copied!';
                    copyBtn.querySelector('svg').innerHTML = `
                        <polyline points="20 6 9 17 4 12"></polyline>
                    `;
                    setTimeout(() => {
                        copyBtn.classList.remove('copied');
                        copyBtn.querySelector('span').textContent = 'Copy';
                        copyBtn.querySelector('svg').innerHTML = `
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        `;
                    }, 2000);
                } catch (err) {
                    console.error('Failed to copy code:', err);
                }
            });

            // Add line numbers for longer code blocks (more than 3 lines)
            const lines = block.textContent.split('\n');
            if (lines.length > 3) {
                const lineNumbersDiv = document.createElement('div');
                lineNumbersDiv.className = 'line-numbers';
                lineNumbersDiv.innerHTML = lines.map((_, i) => `<span>${i + 1}</span>`).join('');

                // Wrap in a container for proper positioning
                const wrapper = document.createElement('div');
                wrapper.className = 'code-block-wrapper with-line-numbers';
                pre.parentNode.insertBefore(wrapper, pre);
                wrapper.appendChild(pre);

                // Insert line numbers after header
                pre.insertBefore(lineNumbersDiv, block);
            }
        });
    }

    scrollToBottom() {
        if (this.messagesContainer) {
            // Use requestAnimationFrame to ensure DOM has updated
            requestAnimationFrame(() => {
                this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
            });
        }
    }

    startNewChat() {
        this.haptic();
        this.conversationId = null;
        window.history.pushState({}, '', '/');

        // Reset UI
        if (this.messages) {
            this.messages.innerHTML = '';
        }

        // Show welcome screen
        if (this.welcomeScreen) {
            this.welcomeScreen.style.display = 'flex';
        } else {
            location.reload();
        }

        // Remove active state from conversations
        document.querySelectorAll('.conversation-item').forEach(item => {
            item.classList.remove('active');
        });

        this.messageInput.focus();
        this.sidebar.classList.remove('open');
    }

    async loadConversation(id) {
        try {
            const response = await fetch(`/api/conversations/${id}`);
            if (!response.ok) throw new Error('Failed to load conversation');

            const conversation = await response.json();
            this.conversationId = id;
            this.updateURL(id);

            // Update active state
            document.querySelectorAll('.conversation-item').forEach(item => {
                item.classList.toggle('active', item.dataset.id === id);
            });

            // Hide welcome screen
            if (this.welcomeScreen) {
                this.welcomeScreen.style.display = 'none';
            }

            // Render messages
            if (!this.messages) {
                const messagesDiv = document.createElement('div');
                messagesDiv.className = 'messages';
                messagesDiv.id = 'messages';
                this.messagesContainer.appendChild(messagesDiv);
                this.messages = messagesDiv;
            }

            this.messages.innerHTML = '';
            conversation.messages?.forEach(msg => {
                this.addMessage(msg.role, msg.content, msg.htmlContent, msg.modelUsed, msg.citations, msg.id);
            });

            this.sidebar.classList.remove('open');
        } catch (error) {
            console.error('Error loading conversation:', error);
            this.addErrorMessage('Failed to load conversation.');
        }
    }

    async deleteConversation(id) {
        this.haptic('heavy');
        if (!confirm('Are you sure you want to delete this conversation?')) {
            return;
        }

        try {
            const response = await fetch(`/api/conversations/${id}`, {
                method: 'DELETE'
            });

            if (!response.ok) throw new Error('Failed to delete conversation');

            // Remove from UI
            const item = document.querySelector(`.conversation-item[data-id="${id}"]`);
            if (item) {
                item.remove();
            }

            // If this was the current conversation, start new chat
            if (this.conversationId === id) {
                this.startNewChat();
            }
        } catch (error) {
            console.error('Error deleting conversation:', error);
            alert('Failed to delete conversation.');
        }
    }

    async clearAllHistory() {
        this.haptic('heavy');
        const conversationCount = this.conversationsList?.querySelectorAll('.conversation-item').length || 0;
        if (conversationCount === 0) {
            alert('No conversations to clear.');
            return;
        }

        if (!confirm(`Are you sure you want to delete ALL ${conversationCount} conversation(s)? This action cannot be undone.`)) {
            return;
        }

        try {
            const response = await fetch('/api/conversations/clear-all', {
                method: 'DELETE'
            });

            if (!response.ok) throw new Error('Failed to clear history');

            const data = await response.json();

            // Clear the conversations list
            if (this.conversationsList) {
                this.conversationsList.innerHTML = '';
            }

            // Start a new chat
            this.startNewChat();

            // Show success message
            alert(`Successfully deleted ${data.deletedCount} conversation(s).`);
        } catch (error) {
            console.error('Error clearing history:', error);
            alert('Failed to clear chat history.');
        }
    }

    async refreshConversationsList() {
        try {
            const response = await fetch('/api/conversations');
            if (!response.ok) return;

            const conversations = await response.json();
            this.conversationsList.innerHTML = conversations.map(conv => `
                <div class="conversation-item ${conv.id === this.conversationId ? 'active' : ''}"
                     data-id="${conv.id}">
                    <div class="conversation-title">${this.escapeHtml(conv.title)}</div>
                    <div class="conversation-meta">${conv.messageCount} messages</div>
                    <button class="delete-btn" data-id="${conv.id}">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="3 6 5 6 21 6"></polyline>
                            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                        </svg>
                    </button>
                </div>
            `).join('');
        } catch (error) {
            console.error('Error refreshing conversations:', error);
        }
    }

    updateURL(conversationId) {
        if (conversationId) {
            window.history.pushState({}, '', `/chat/${conversationId}`);
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Document Management Methods
    async initDocuments() {
        // Check if document service is available
        try {
            const response = await fetch('/api/documents/status');
            if (response.ok) {
                const data = await response.json();
                if (data.available) {
                    this.initDocumentEventListeners();
                    await this.loadDocuments();
                    // Load saved RAG retrieval mode preference from server
                    this.loadRagRetrievalModePreference();
                }
            }
        } catch (error) {
            console.warn('Document service not available:', error);
        }
    }

    /**
     * Load the RAG retrieval mode preference from the server and apply it.
     */
    async loadRagRetrievalModePreference() {
        try {
            const response = await fetch('/api/user/preferences');
            if (response.ok) {
                const prefs = await response.json();
                if (prefs && prefs.ragRetrievalMode) {
                    this.ragRetrievalMode = prefs.ragRetrievalMode;
                    localStorage.setItem('ragRetrievalMode', prefs.ragRetrievalMode);
                    this.applyRagModeUI(prefs.ragRetrievalMode);
                }
            }
        } catch (error) {
            console.warn('Failed to load RAG retrieval mode preference:', error);
        }
    }

    initDocumentEventListeners() {
        // Documents button
        if (this.docsBtn) {
            this.docsBtn.addEventListener('click', () => {
                this.toggleDocumentsPanel();
            });
        }

        // Close panel button
        if (this.closePanelBtn) {
            this.closePanelBtn.addEventListener('click', () => {
                this.closeDocumentsPanel();
            });
        }

        // Upload area click
        if (this.uploadArea) {
            this.uploadArea.addEventListener('click', (e) => {
                if (e.target === this.fileInput) return;
                this.fileInput.click();
            });

            // Drag and drop
            this.uploadArea.addEventListener('dragover', (e) => {
                e.preventDefault();
                this.uploadArea.classList.add('dragover');
            });

            this.uploadArea.addEventListener('dragleave', () => {
                this.uploadArea.classList.remove('dragover');
            });

            this.uploadArea.addEventListener('drop', (e) => {
                e.preventDefault();
                this.uploadArea.classList.remove('dragover');
                const files = e.dataTransfer.files;
                this.uploadFiles(files);
            });
        }

        // File input change
        if (this.fileInput) {
            this.fileInput.addEventListener('change', (e) => {
                this.uploadFiles(e.target.files);
            });
        }

        // Use documents toggle
        if (this.useDocumentsToggle) {
            this.useDocumentsToggle.addEventListener('change', (e) => {
                this.useDocumentContext = e.target.checked;
            });
        }

        // RAG retrieval mode toggle buttons
        if (this.ragModeToggle) {
            this.ragModeToggle.querySelectorAll('.rag-mode-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    const mode = btn.dataset.mode;
                    this.setRagRetrievalMode(mode);
                });
            });
            // Apply saved mode on init
            this.applyRagModeUI(this.ragRetrievalMode);
        }

    }

    /**
     * Set the RAG retrieval mode and persist to server and localStorage.
     * @param {string} mode - "snippet" or "full"
     */
    setRagRetrievalMode(mode) {
        this.ragRetrievalMode = mode;
        localStorage.setItem('ragRetrievalMode', mode);
        this.applyRagModeUI(mode);

        // Persist to server
        fetch('/api/user/preferences/rag-retrieval-mode', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ragRetrievalMode: mode })
        }).catch(err => console.warn('Failed to save RAG mode preference:', err));

        console.debug('RAG retrieval mode set to:', mode);
    }

    /**
     * Update the RAG mode toggle UI to reflect the active mode.
     * @param {string} mode - "snippet" or "full"
     */
    applyRagModeUI(mode) {
        if (!this.ragModeToggle) return;
        this.ragModeToggle.querySelectorAll('.rag-mode-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.mode === mode);
        });
    }

    toggleDocumentsPanel() {
        if (this.documentsPanel) {
            // Close tools panel if open
            this.closeToolsPanel();
            this.documentsPanel.classList.toggle('open');
            if (this.documentsPanel.classList.contains('open')) {
                this.loadDocuments();
            }
        }
    }

    closeDocumentsPanel() {
        if (this.documentsPanel) {
            this.documentsPanel.classList.remove('open');
        }
    }

    async loadDocuments() {
        try {
            const [docsResponse, statsResponse] = await Promise.all([
                fetch('/api/documents'),
                fetch('/api/documents/stats')
            ]);

            if (docsResponse.ok && statsResponse.ok) {
                const documents = await docsResponse.json();
                const stats = await statsResponse.json();

                this.renderDocuments(documents);
                this.updateDocumentStats(stats);

                this.documentsAvailable = stats.completedDocuments > 0;
            }
        } catch (error) {
            console.error('Error loading documents:', error);
        }
    }

    async initTools() {
        // Load tool preferences from localStorage
        this.loadToolPreferences();

        // Check if MCP tools are available using the chat endpoint (accessible to all users)
        try {
            const response = await fetch('/api/chat/available-tools');
            if (response.ok) {
                this.tools = await response.json();
                this.toolsAvailable = this.tools.length > 0;

                // Show "Use Tools" toggle if tools are available
                if (this.toolsToggleLabel) {
                    this.toolsToggleLabel.style.display = this.toolsAvailable ? 'flex' : 'none';
                }

                // Set initial toggle state from saved preference (defaults to true from constructor)
                const savedPref = localStorage.getItem('useTools');
                if (savedPref !== null) {
                    this.useTools = savedPref === 'true';
                }
                if (this.useToolsToggle) {
                    this.useToolsToggle.checked = this.useTools;
                }
            }
        } catch (error) {
            console.warn('Could not check tools availability:', error);
            if (this.toolsToggleLabel) {
                this.toolsToggleLabel.style.display = 'none';
            }
        }
    }

    loadToolPreferences() {
        try {
            const saved = localStorage.getItem('toolPreferences');
            if (saved) {
                this.toolPreferences = JSON.parse(saved);
            }
        } catch (error) {
            console.warn('Error loading tool preferences:', error);
            this.toolPreferences = {};
        }
    }

    saveToolPreferences() {
        try {
            localStorage.setItem('toolPreferences', JSON.stringify(this.toolPreferences));
        } catch (error) {
            console.warn('Error saving tool preferences:', error);
        }
    }

    isToolEnabled(toolId) {
        // Default to enabled if no preference is set
        if (this.toolPreferences[toolId] === undefined) {
            return true;
        }
        return this.toolPreferences[toolId];
    }

    toggleToolEnabled(checkbox) {
        const toolId = checkbox.dataset.toolId;
        this.toolPreferences[toolId] = checkbox.checked;
        this.saveToolPreferences();
        this.updateToolStats();
    }

    getEnabledToolIds() {
        return this.tools
            .filter(tool => this.isToolEnabled(tool.id))
            .map(tool => tool.id);
    }

    renderDocuments(documents) {
        if (!this.documentsList) return;

        if (documents.length === 0) {
            this.documentsList.innerHTML = '<p class="no-documents">No documents uploaded yet</p>';
            return;
        }

        this.documentsList.innerHTML = documents.map(doc => `
            <div class="document-item" data-id="${doc.id}">
                <div class="document-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                        <polyline points="14 2 14 8 20 8"></polyline>
                    </svg>
                </div>
                <div class="document-info">
                    <div class="document-name" title="${this.escapeHtml(doc.originalFilename)}">${this.escapeHtml(doc.originalFilename)}</div>
                    <div class="document-meta">
                        <span class="status status-${doc.status.toLowerCase()}">${doc.status}</span>
                        ${doc.chunkCount ? `<span>${doc.chunkCount} chunks</span>` : ''}
                        <span>${this.formatFileSize(doc.fileSize)}</span>
                    </div>
                </div>
                <button class="delete-doc-btn" data-id="${doc.id}" title="Delete document">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                        <polyline points="3 6 5 6 21 6"></polyline>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                </button>
            </div>
        `).join('');

        // Add delete event listeners
        this.documentsList.querySelectorAll('.delete-doc-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteDocument(btn.dataset.id);
            });
        });
    }

    updateDocumentStats(stats) {
        if (this.docCount) {
            this.docCount.textContent = `${stats.completedDocuments} documents`;
        }
        if (this.chunkCount) {
            this.chunkCount.textContent = `${stats.totalChunks} chunks`;
        }
    }

    async uploadFiles(files) {
        if (!files || files.length === 0) return;

        // Client-side file size validation (100MB default)
        const maxSize = 100 * 1024 * 1024;
        const oversized = [];
        const valid = [];
        for (const file of files) {
            if (file.size > maxSize) {
                oversized.push(file.name);
            } else {
                valid.push(file);
            }
        }

        if (oversized.length > 0) {
            alert(`The following files exceed the maximum size of ${this.formatFileSize(maxSize)}:\n${oversized.join('\n')}`);
        }

        for (const file of valid) {
            await this.uploadSingleFile(file);
        }

        this.fileInput.value = '';
        await this.loadDocuments();
    }

    async uploadSingleFile(file) {
        const formData = new FormData();
        formData.append('file', file);

        if (this.uploadProgress) {
            this.uploadProgress.style.display = 'block';
            this.progressText.textContent = `Uploading ${file.name}...`;
            this.progressFill.style.width = '0%';
        }

        try {
            const response = await fetch('/api/documents/upload', {
                method: 'POST',
                body: formData
            });

            const result = await response.json();

            if (response.ok && result.status === 'COMPLETED') {
                this.haptic();
                this.progressText.textContent = `${file.name} uploaded successfully!`;
                this.progressFill.style.width = '100%';
            } else {
                this.progressText.textContent = `Error: ${result.message || 'Upload failed'}`;
                this.progressFill.style.width = '100%';
                this.progressFill.style.backgroundColor = 'var(--error-color, #ef4444)';
            }

            setTimeout(() => {
                if (this.uploadProgress) {
                    this.uploadProgress.style.display = 'none';
                    this.progressFill.style.backgroundColor = '';
                }
            }, 4000);

        } catch (error) {
            console.error('Error uploading file:', error);
            this.progressText.textContent = 'Upload failed';
            setTimeout(() => {
                if (this.uploadProgress) {
                    this.uploadProgress.style.display = 'none';
                }
            }, 2000);
        }
    }

    async deleteDocument(documentId) {
        if (!confirm('Are you sure you want to delete this document?')) {
            return;
        }

        try {
            const response = await fetch(`/api/documents/${documentId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                await this.loadDocuments();
            } else {
                alert('Failed to delete document');
            }
        } catch (error) {
            console.error('Error deleting document:', error);
            alert('Failed to delete document');
        }
    }

    // Feature 53: Large text paste detection
    initPasteDetection() {
        if (this.messageInput) {
            this.messageInput.addEventListener('paste', (e) => {
                const text = e.clipboardData.getData('text');
                if (text.length > 5000) {
                    e.preventDefault();
                    if (confirm(`Large text detected (${text.length} characters). Upload as document for RAG instead?`)) {
                        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
                        const file = new File([text], `pasted-content-${timestamp}.txt`, { type: 'text/plain' });
                        this.uploadPastedDocument(file);
                    } else {
                        // Insert text at cursor position preserving selection
                        const start = this.messageInput.selectionStart;
                        const end = this.messageInput.selectionEnd;
                        this.messageInput.value =
                            this.messageInput.value.substring(0, start) +
                            text +
                            this.messageInput.value.substring(end);
                        this.messageInput.selectionStart = this.messageInput.selectionEnd = start + text.length;
                        this.autoResizeTextarea();
                    }
                }
            });
        }
    }

    async uploadPastedDocument(file) {
        const formData = new FormData();
        formData.append('file', file);

        try {
            const response = await fetch('/api/documents/upload', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const result = await response.json();
                if (result.status === 'COMPLETED') {
                    alert('Pasted content uploaded as document successfully.');
                    await this.loadDocuments();
                } else {
                    alert(`Document upload status: ${result.status}. ${result.message || ''}`);
                }
            } else {
                alert('Failed to upload pasted content as document.');
            }
        } catch (error) {
            console.error('Error uploading pasted document:', error);
            alert('Failed to upload pasted content.');
        }
    }

    // Feature 57: Keyboard shortcuts
    initKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.metaKey) {
                switch (e.key) {
                    case 'n':
                        e.preventDefault();
                        this.startNewChat();
                        break;
                    case 'k':
                        e.preventDefault();
                        this.toggleSearchPanel();
                        break;
                    case '/':
                        e.preventDefault();
                        if (this.messageInput) this.messageInput.focus();
                        break;
                }
            }
            if (e.key === 'Escape') {
                this.closePanels();
            }
        });
    }

    toggleSearchPanel() {
        // Toggle the documents panel as a search/reference panel
        this.toggleDocumentsPanel();
    }

    closePanels() {
        this.closeDocumentsPanel();
        this.closeToolsPanel();
        this.closeChannelsPanel();
        this.closeNotesPanel();
        this.closeMemoryPanel();
        // Close user menu dropdown if open
        if (this.userMenuDropdown) {
            this.userMenuDropdown.classList.remove('open');
        }
        // Close sidebar on mobile
        if (window.innerWidth <= 768) {
            this.sidebar.classList.remove('open');
            const overlay = document.getElementById('sidebarOverlay');
            if (overlay) overlay.classList.remove('visible');
        }
    }

    // Feature 58: Settings search functionality
    initSettingsSearch() {
        const searchInput = document.getElementById('settings-search');
        if (!searchInput) return;

        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase();
            document.querySelectorAll('.settings-section').forEach(section => {
                const keywords = (section.dataset.searchKeywords || section.textContent).toLowerCase();
                section.style.display = keywords.includes(query) ? '' : 'none';
            });
        });
    }

    // Feature 59: Haptic feedback for mobile
    haptic(style = 'light') {
        if (navigator.vibrate) {
            navigator.vibrate(style === 'heavy' ? 50 : 10);
        }
    }

    // Feature 64: Quick actions on text selection
    initQuickActions() {
        let toolbar = document.getElementById('quick-actions-toolbar');
        if (!toolbar) {
            toolbar = document.createElement('div');
            toolbar.id = 'quick-actions-toolbar';
            toolbar.className = 'quick-actions-toolbar';
            toolbar.style.display = 'none';
            toolbar.innerHTML = `
                <button class="quick-action-btn" data-action="ask">Ask</button>
                <button class="quick-action-btn" data-action="explain">Explain</button>
                <button class="quick-action-btn" data-action="copy">Copy</button>
            `;
            document.body.appendChild(toolbar);
        }

        document.addEventListener('selectionchange', () => {
            const sel = window.getSelection();
            if (sel.rangeCount && sel.toString().trim().length > 0) {
                const range = sel.getRangeAt(0);
                const rect = range.getBoundingClientRect();
                // Only show within message area
                const msgArea = document.querySelector('.chat-messages, .messages-container, #messages');
                if (msgArea && msgArea.contains(range.startContainer)) {
                    toolbar.style.top = (rect.top + window.scrollY - 45) + 'px';
                    toolbar.style.left = (rect.left + window.scrollX) + 'px';
                    toolbar.style.display = 'flex';
                    toolbar.dataset.selectedText = sel.toString();
                }
            } else {
                toolbar.style.display = 'none';
            }
        });

        toolbar.addEventListener('click', (e) => {
            const btn = e.target.closest('.quick-action-btn');
            if (!btn) return;
            const text = toolbar.dataset.selectedText;
            const action = btn.dataset.action;
            toolbar.style.display = 'none';

            if (action === 'copy') {
                navigator.clipboard.writeText(text);
                this.haptic();
            } else if (action === 'ask') {
                if (this.messageInput) {
                    this.messageInput.value = 'Tell me more about: ' + text;
                    this.messageInput.focus();
                }
                this.haptic();
            } else if (action === 'explain') {
                if (this.messageInput) {
                    this.messageInput.value = 'Please explain this: ' + text;
                    this.messageInput.focus();
                }
                this.haptic();
            }
        });
    }

    handleMessageAction(action, messageEl) {
        const textEl = messageEl.querySelector('.message-text');
        const rawText = textEl ? textEl.innerText : '';

        switch(action) {
            case 'copy':
                navigator.clipboard.writeText(rawText).then(() => {
                    this.showToast('Copied to clipboard');
                });
                break;
            case 'edit':
                // Put text in input and remove the message + subsequent messages
                this.messageInput.value = rawText;
                this.messageInput.focus();
                this.autoResizeTextarea();
                // Remove this message and all following
                const messages = Array.from(this.messages.children);
                const idx = messages.indexOf(messageEl);
                for (let i = messages.length - 1; i >= idx; i--) {
                    messages[i].remove();
                }
                break;
            case 'regenerate':
                // Re-send the last user message
                const userMsgs = this.messages.querySelectorAll('.message.user');
                const lastUserMsg = userMsgs[userMsgs.length - 1];
                if (lastUserMsg) {
                    const userText = lastUserMsg.querySelector('.message-text').innerText;
                    // Remove the last assistant message
                    messageEl.remove();
                    // Re-send
                    this.sendMessage(userText);
                }
                break;
            case 'favorite':
                this.toggleFavorite(messageEl);
                break;
            case 'translate':
                this.showTranslateDropdown(messageEl);
                break;
        }
    }

    async toggleFavorite(messageEl) {
        if (!this.conversationId) return;
        const msgId = messageEl.dataset.messageId;
        if (!msgId) return;
        try {
            const resp = await fetch(`/api/conversations/${this.conversationId}/messages/${msgId}/favorite`, {
                method: 'PATCH'
            });
            if (resp.ok) {
                const btn = messageEl.querySelector('[data-action="favorite"]');
                btn.classList.toggle('active');
                const svg = btn.querySelector('svg');
                svg.setAttribute('fill', btn.classList.contains('active') ? 'currentColor' : 'none');
            }
        } catch(e) { console.error('Failed to toggle favorite:', e); }
    }

    showToast(message) {
        const toast = document.createElement('div');
        toast.className = 'toast-message';
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(() => toast.classList.add('visible'), 10);
        setTimeout(() => { toast.classList.remove('visible'); setTimeout(() => toast.remove(), 300); }, 2000);
    }

    initSidebar() {
        // Search
        const searchInput = document.getElementById('sidebarSearch');
        if (searchInput) {
            let debounceTimer;
            searchInput.addEventListener('input', () => {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => this.searchConversations(searchInput.value), 300);
            });
        }

        // Tabs
        const tabs = document.querySelectorAll('.sidebar-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                this.loadSidebarTab(tab.dataset.tab);
            });
        });

        // Load folders for the folders tab
        this.folders = [];
        this.loadFolders();
    }

    async searchConversations(query) {
        if (!query.trim()) {
            // Reset to show all
            document.querySelectorAll('.conversation-item').forEach(el => el.style.display = '');
            return;
        }
        try {
            const resp = await fetch(`/api/conversations/search?q=${encodeURIComponent(query)}&size=20`);
            if (resp.ok) {
                const data = await resp.json();
                const ids = new Set(data.content.map(c => c.id));
                document.querySelectorAll('.conversation-item').forEach(el => {
                    el.style.display = ids.has(el.dataset.id) ? '' : 'none';
                });
            }
        } catch(e) { console.error('Search failed:', e); }
    }

    async loadSidebarTab(tab) {
        const list = this.conversationsList;
        if (!list) return;

        switch(tab) {
            case 'all':
                document.querySelectorAll('.conversation-item').forEach(el => el.style.display = '');
                document.querySelectorAll('.folder-section').forEach(el => el.style.display = 'none');
                { const fv = document.getElementById('foldersView'); if (fv) fv.style.display = 'none'; }
                { const ai = document.getElementById('archivedItems'); if (ai) ai.style.display = 'none'; }
                break;
            case 'pinned':
                try {
                    const resp = await fetch('/api/conversations/pinned');
                    if (resp.ok) {
                        const pinned = await resp.json();
                        const pinnedIds = new Set(pinned.map(c => c.id));
                        document.querySelectorAll('.conversation-item').forEach(el => {
                            el.style.display = pinnedIds.has(el.dataset.id) ? '' : 'none';
                        });
                        document.querySelectorAll('.folder-section').forEach(el => el.style.display = 'none');
                        { const fv = document.getElementById('foldersView'); if (fv) fv.style.display = 'none'; }
                        { const ai = document.getElementById('archivedItems'); if (ai) ai.style.display = 'none'; }
                    }
                } catch(e) {}
                break;
            case 'folders':
                this.showFolderView();
                break;
            case 'archived':
                try {
                    const resp = await fetch('/api/conversations/archived?size=50');
                    if (resp.ok) {
                        const data = await resp.json();
                        // Hide all current, show archived
                        document.querySelectorAll('.conversation-item').forEach(el => el.style.display = 'none');
                        document.querySelectorAll('.folder-section').forEach(el => el.style.display = 'none');
                        // Add archived items temporarily
                        let archivedContainer = document.getElementById('archivedItems');
                        if (!archivedContainer) {
                            archivedContainer = document.createElement('div');
                            archivedContainer.id = 'archivedItems';
                            list.appendChild(archivedContainer);
                        }
                        archivedContainer.innerHTML = data.content.map(c => `
                            <div class="conversation-item archived-item" data-id="${c.id}">
                                <div class="conversation-title">${this.escapeHtml(c.title || 'Untitled')}</div>
                                <div class="conversation-meta">
                                    <span>Archived</span>
                                    <button class="unarchive-btn" data-id="${c.id}" title="Unarchive">Restore</button>
                                </div>
                            </div>
                        `).join('');
                        archivedContainer.style.display = '';
                    }
                } catch(e) {}
                break;
        }
    }

    async loadFolders() {
        try {
            const resp = await fetch('/api/folders');
            if (resp.ok) {
                this.folders = await resp.json();
            }
        } catch(e) { this.folders = []; }
    }

    showFolderView() {
        document.querySelectorAll('.conversation-item').forEach(el => el.style.display = 'none');
        let existing = document.getElementById('archivedItems');
        if (existing) existing.style.display = 'none';

        let foldersContainer = document.getElementById('foldersView');
        if (!foldersContainer) {
            foldersContainer = document.createElement('div');
            foldersContainer.id = 'foldersView';
            this.conversationsList.appendChild(foldersContainer);
        }

        foldersContainer.textContent = '';
        this._buildFolderListView(foldersContainer);
        foldersContainer.style.display = '';
    }

    _buildFolderListView(container) {
        container.textContent = '';

        // New Folder button
        const newBtn = document.createElement('button');
        newBtn.className = 'new-folder-btn';
        const plusSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        plusSvg.setAttribute('viewBox', '0 0 24 24');
        plusSvg.setAttribute('fill', 'none');
        plusSvg.setAttribute('stroke', 'currentColor');
        plusSvg.setAttribute('stroke-width', '2');
        plusSvg.setAttribute('width', '16');
        plusSvg.setAttribute('height', '16');
        const l1 = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        l1.setAttribute('x1', '12'); l1.setAttribute('y1', '5'); l1.setAttribute('x2', '12'); l1.setAttribute('y2', '19');
        const l2 = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        l2.setAttribute('x1', '5'); l2.setAttribute('y1', '12'); l2.setAttribute('x2', '19'); l2.setAttribute('y2', '12');
        plusSvg.appendChild(l1);
        plusSvg.appendChild(l2);
        newBtn.appendChild(plusSvg);
        newBtn.appendChild(document.createTextNode(' New Folder'));
        newBtn.addEventListener('click', () => this.createFolder());
        container.appendChild(newBtn);

        if (this.folders.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.textContent = 'No folders yet. Create one to organize your chats.';
            container.appendChild(empty);
        } else {
            this.folders.forEach(f => {
                const row = document.createElement('div');
                row.className = 'folder-row';
                row.dataset.folderId = f.id;

                const folderSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                folderSvg.setAttribute('viewBox', '0 0 24 24');
                folderSvg.setAttribute('fill', 'none');
                folderSvg.setAttribute('stroke', 'currentColor');
                folderSvg.setAttribute('stroke-width', '2');
                folderSvg.setAttribute('width', '16');
                folderSvg.setAttribute('height', '16');
                const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                path.setAttribute('d', 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z');
                folderSvg.appendChild(path);
                row.appendChild(folderSvg);

                const nameSpan = document.createElement('span');
                nameSpan.className = 'folder-name';
                nameSpan.textContent = f.name;
                row.appendChild(nameSpan);

                const countSpan = document.createElement('span');
                countSpan.className = 'folder-count';
                countSpan.textContent = f.conversationCount || 0;
                row.appendChild(countSpan);

                // Chevron right arrow
                const chevron = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                chevron.setAttribute('viewBox', '0 0 24 24');
                chevron.setAttribute('fill', 'none');
                chevron.setAttribute('stroke', 'currentColor');
                chevron.setAttribute('stroke-width', '2');
                chevron.setAttribute('width', '14');
                chevron.setAttribute('height', '14');
                chevron.classList.add('folder-chevron');
                const chevPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                chevPath.setAttribute('d', 'M9 18l6-6-6-6');
                chevron.appendChild(chevPath);
                row.appendChild(chevron);

                row.addEventListener('click', () => this._openFolder(f));
                container.appendChild(row);
            });
        }
    }

    async _openFolder(folder) {
        const container = document.getElementById('foldersView');
        if (!container) return;
        container.textContent = '';

        // Back button header
        const backHeader = document.createElement('div');
        backHeader.className = 'folder-back-header';

        const backBtn = document.createElement('button');
        backBtn.className = 'folder-back-btn';
        const backSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        backSvg.setAttribute('viewBox', '0 0 24 24');
        backSvg.setAttribute('fill', 'none');
        backSvg.setAttribute('stroke', 'currentColor');
        backSvg.setAttribute('stroke-width', '2');
        backSvg.setAttribute('width', '16');
        backSvg.setAttribute('height', '16');
        const backPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        backPath.setAttribute('d', 'M19 12H5M12 19l-7-7 7-7');
        backSvg.appendChild(backPath);
        backBtn.appendChild(backSvg);
        backBtn.appendChild(document.createTextNode(' Folders'));
        backBtn.addEventListener('click', () => this._buildFolderListView(container));
        backHeader.appendChild(backBtn);

        // Delete folder button
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'folder-delete-btn';
        deleteBtn.title = 'Delete folder';
        const delSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        delSvg.setAttribute('viewBox', '0 0 24 24');
        delSvg.setAttribute('fill', 'none');
        delSvg.setAttribute('stroke', 'currentColor');
        delSvg.setAttribute('stroke-width', '2');
        delSvg.setAttribute('width', '14');
        delSvg.setAttribute('height', '14');
        const delPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        delPath.setAttribute('d', 'M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2');
        delSvg.appendChild(delPath);
        deleteBtn.appendChild(delSvg);
        deleteBtn.addEventListener('click', async () => {
            if (!confirm(`Delete folder "${folder.name}"? Conversations will be unfoldered, not deleted.`)) return;
            try {
                const resp = await fetch(`/api/folders/${folder.id}`, { method: 'DELETE' });
                if (resp.ok) {
                    await this.loadFolders();
                    this._buildFolderListView(container);
                    this.showToast('Folder deleted');
                }
            } catch(e) { this.showToast('Failed to delete folder'); }
        });
        backHeader.appendChild(deleteBtn);

        container.appendChild(backHeader);

        // Folder title
        const titleEl = document.createElement('div');
        titleEl.className = 'folder-title';
        const folderSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        folderSvg.setAttribute('viewBox', '0 0 24 24');
        folderSvg.setAttribute('fill', 'none');
        folderSvg.setAttribute('stroke', 'currentColor');
        folderSvg.setAttribute('stroke-width', '2');
        folderSvg.setAttribute('width', '18');
        folderSvg.setAttribute('height', '18');
        const fPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        fPath.setAttribute('d', 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z');
        folderSvg.appendChild(fPath);
        titleEl.appendChild(folderSvg);
        const titleText = document.createElement('span');
        titleText.textContent = folder.name;
        titleEl.appendChild(titleText);
        container.appendChild(titleEl);

        // Loading indicator
        const loading = document.createElement('div');
        loading.className = 'empty-state';
        loading.textContent = 'Loading conversations...';
        container.appendChild(loading);

        // Fetch conversations
        try {
            const resp = await fetch(`/api/folders/${folder.id}/conversations`);
            if (resp.ok) {
                const convs = await resp.json();
                container.removeChild(loading);
                if (convs.length === 0) {
                    const empty = document.createElement('div');
                    empty.className = 'empty-state';
                    empty.textContent = 'No conversations in this folder.';
                    container.appendChild(empty);
                } else {
                    convs.forEach(c => {
                        const item = document.createElement('div');
                        item.className = 'conversation-item folder-conv-item';
                        item.dataset.id = c.id;

                        const titleSpan = document.createElement('div');
                        titleSpan.className = 'conv-title';
                        titleSpan.textContent = c.title || 'Untitled';
                        item.appendChild(titleSpan);

                        const meta = document.createElement('div');
                        meta.className = 'conv-meta';
                        meta.textContent = `${c.messageCount} message${c.messageCount !== 1 ? 's' : ''}`;
                        item.appendChild(meta);

                        item.addEventListener('click', () => this.loadConversation(c.id));
                        container.appendChild(item);
                    });
                }
            }
        } catch(e) {
            container.removeChild(loading);
            const err = document.createElement('div');
            err.className = 'empty-state';
            err.textContent = 'Failed to load conversations.';
            container.appendChild(err);
        }
    }

    async createFolder() {
        const name = prompt('Folder name:');
        if (!name || !name.trim()) return;
        try {
            const resp = await fetch('/api/folders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name.trim() })
            });
            if (resp.ok) {
                await this.loadFolders();
                this.showFolderView();
                this.showToast('Folder created');
            }
        } catch(e) {
            console.error('Failed to create folder:', e);
            this.showToast('Failed to create folder');
        }
    }

    // --- Conversation Context Menu ---

    showConvContextMenu(x, y, convId) {
        const menu = document.getElementById('convContextMenu');
        if (!menu) return;
        this.ctxMenuConvId = convId;

        // Determine if already pinned
        const item = document.querySelector(`.conversation-item[data-id="${convId}"]`);
        const isPinned = item && item.dataset.pinned === 'true';
        const pinLabel = document.getElementById('ctxPinLabel');
        if (pinLabel) pinLabel.textContent = isPinned ? 'Unpin' : 'Pin';

        // Position
        menu.style.display = 'block';
        const menuRect = menu.getBoundingClientRect();
        const maxX = window.innerWidth - menuRect.width - 8;
        const maxY = window.innerHeight - menuRect.height - 8;
        menu.style.left = Math.min(x, maxX) + 'px';
        menu.style.top = Math.min(y, maxY) + 'px';

        // Attach action handlers (remove old ones by cloning)
        menu.querySelectorAll('.ctx-menu-item').forEach(btn => {
            const clone = btn.cloneNode(true);
            btn.parentNode.replaceChild(clone, btn);
        });

        menu.querySelector('[data-action="pin"]').addEventListener('click', () => {
            menu.style.display = 'none';
            this.togglePinConversation(convId);
        });
        menu.querySelector('[data-action="rename"]').addEventListener('click', () => {
            menu.style.display = 'none';
            this.renameConversation(convId);
        });
        menu.querySelector('[data-action="archive"]').addEventListener('click', () => {
            menu.style.display = 'none';
            this.archiveConversation(convId);
        });
        menu.querySelector('[data-action="folder"]').addEventListener('click', (e) => {
            e.stopPropagation();
            menu.style.display = 'none';
            this.showFolderPicker(e.clientX, e.clientY, convId);
        });
        menu.querySelector('[data-action="delete"]').addEventListener('click', () => {
            menu.style.display = 'none';
            this.deleteConversation(convId);
        });
        const tagBtn = menu.querySelector('[data-action="tag"]');
        if (tagBtn) {
            tagBtn.addEventListener('click', () => {
                menu.style.display = 'none';
                this.showTagsModal(convId);
            });
        }
    }

    async togglePinConversation(convId) {
        const item = document.querySelector(`.conversation-item[data-id="${convId}"]`);
        const isPinned = item && item.dataset.pinned === 'true';
        try {
            const resp = await fetch(`/api/conversations/${convId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pinned: !isPinned })
            });
            if (resp.ok) {
                if (item) {
                    item.dataset.pinned = String(!isPinned);
                    let indicator = item.querySelector('.pin-indicator');
                    if (!isPinned) {
                        if (!indicator) {
                            indicator = document.createElement('span');
                            indicator.className = 'pin-indicator';
                            indicator.textContent = '\u{1F4CC}';
                            item.querySelector('.conversation-title').appendChild(indicator);
                        }
                    } else {
                        if (indicator) indicator.remove();
                    }
                }
                this.showToast(isPinned ? 'Unpinned' : 'Pinned');
            }
        } catch(e) { this.showToast('Failed to update pin'); }
    }

    async renameConversation(convId) {
        const item = document.querySelector(`.conversation-item[data-id="${convId}"]`);
        const titleEl = item ? item.querySelector('.conversation-title') : null;
        const currentTitle = titleEl ? titleEl.textContent.replace('\u{1F4CC}', '').trim() : '';
        const newTitle = prompt('Rename conversation:', currentTitle);
        if (!newTitle || newTitle.trim() === currentTitle) return;
        try {
            const resp = await fetch(`/api/conversations/${convId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: newTitle.trim() })
            });
            if (resp.ok && titleEl) {
                titleEl.textContent = newTitle.trim();
                if (item && item.dataset.pinned === 'true') {
                    const indicator = document.createElement('span');
                    indicator.className = 'pin-indicator';
                    indicator.textContent = '\u{1F4CC}';
                    titleEl.appendChild(indicator);
                }
                this.showToast('Renamed');
            }
        } catch(e) { this.showToast('Failed to rename'); }
    }

    async archiveConversation(convId) {
        try {
            const resp = await fetch(`/api/conversations/${convId}/archive`, { method: 'POST' });
            if (resp.ok) {
                const item = document.querySelector(`.conversation-item[data-id="${convId}"]`);
                if (item) item.remove();
                if (this.conversationId === convId) {
                    this.startNewChat();
                }
                this.showToast('Archived');
            }
        } catch(e) { this.showToast('Failed to archive'); }
    }

    async unarchiveConversation(convId) {
        try {
            const resp = await fetch(`/api/conversations/${convId}/unarchive`, { method: 'POST' });
            if (resp.ok) {
                const item = document.querySelector(`.archived-item[data-id="${convId}"]`);
                if (item) item.remove();
                this.showToast('Restored');
                window.location.reload();
            }
        } catch(e) { this.showToast('Failed to restore'); }
    }

    async showFolderPicker(x, y, convId) {
        const existing = document.getElementById('folderPicker');
        if (existing) existing.remove();

        // Refresh folders list before showing picker
        await this.loadFolders();

        const picker = document.createElement('div');
        picker.id = 'folderPicker';
        picker.className = 'folder-picker';

        const noFolder = document.createElement('button');
        noFolder.className = 'folder-picker-item remove-folder';
        noFolder.textContent = 'No folder';
        noFolder.addEventListener('click', () => {
            this.moveToFolder(convId, null);
            picker.remove();
        });
        picker.appendChild(noFolder);

        if (this.folders.length === 0) {
            const empty = document.createElement('div');
            empty.style.cssText = 'padding: 8px 12px; color: var(--text-muted); font-size: 0.85rem;';
            empty.textContent = 'No folders. Create one in the Folders tab.';
            picker.appendChild(empty);
        } else {
            this.folders.forEach(f => {
                const item = document.createElement('button');
                item.className = 'folder-picker-item';
                item.textContent = f.name;
                item.addEventListener('click', () => {
                    this.moveToFolder(convId, f.id);
                    picker.remove();
                });
                picker.appendChild(item);
            });
        }

        document.body.appendChild(picker);
        const pickerRect = picker.getBoundingClientRect();
        const maxX = window.innerWidth - pickerRect.width - 8;
        const maxY = window.innerHeight - pickerRect.height - 8;
        picker.style.left = Math.min(x, maxX) + 'px';
        picker.style.top = Math.min(y, maxY) + 'px';
    }

    async moveToFolder(convId, folderId) {
        try {
            const resp = await fetch(`/api/conversations/${convId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ folderId: folderId })
            });
            if (resp.ok) {
                this.showToast(folderId ? 'Moved to folder' : 'Removed from folder');
                await this.loadFolders();
            }
        } catch(e) { this.showToast('Failed to move'); }
    }

    initSlashCommands() {
        this.slashDropdown = null;
        this.messageInput.addEventListener('input', () => {
            const val = this.messageInput.value;
            if (val.startsWith('/')) {
                this.showSlashDropdown(val.substring(1));
            } else {
                this.hideSlashDropdown();
            }
        });

        this.messageInput.addEventListener('keydown', (e) => {
            if (this.slashDropdown && this.slashDropdown.style.display !== 'none') {
                if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                    e.preventDefault();
                    this.navigateSlashDropdown(e.key === 'ArrowDown' ? 1 : -1);
                } else if (e.key === 'Tab' || (e.key === 'Enter' && !e.shiftKey)) {
                    const active = this.slashDropdown.querySelector('.slash-item.active');
                    if (active) {
                        e.preventDefault();
                        e.stopPropagation();
                        active.click();
                    }
                } else if (e.key === 'Escape') {
                    this.hideSlashDropdown();
                }
            }
        });
    }

    async showSlashDropdown(query) {
        try {
            const resp = await fetch(`/api/prompts/search?q=${encodeURIComponent(query)}&size=8`);
            if (!resp.ok) { this.hideSlashDropdown(); return; }
            const presets = await resp.json();

            if (presets.length === 0) { this.hideSlashDropdown(); return; }

            if (!this.slashDropdown) {
                this.slashDropdown = document.createElement('div');
                this.slashDropdown.className = 'slash-dropdown';
                this.messageInput.parentElement.parentElement.appendChild(this.slashDropdown);
            }

            this.slashDropdown.innerHTML = presets.map((p, i) => `
                <div class="slash-item${i === 0 ? ' active' : ''}" data-content="${this.escapeHtml(p.content || p.promptText || '')}">
                    <div class="slash-name">/${this.escapeHtml(p.name || p.title)}</div>
                    <div class="slash-desc">${this.escapeHtml(p.description || '')}</div>
                </div>
            `).join('');

            this.slashDropdown.style.display = 'block';

            // Click handler
            this.slashDropdown.querySelectorAll('.slash-item').forEach(item => {
                item.addEventListener('click', () => {
                    this.messageInput.value = item.dataset.content;
                    this.hideSlashDropdown();
                    this.messageInput.focus();
                    this.autoResizeTextarea();
                });
            });
        } catch(e) { this.hideSlashDropdown(); }
    }

    hideSlashDropdown() {
        if (this.slashDropdown) this.slashDropdown.style.display = 'none';
    }

    navigateSlashDropdown(dir) {
        const items = this.slashDropdown.querySelectorAll('.slash-item');
        const activeIdx = Array.from(items).findIndex(i => i.classList.contains('active'));
        items[activeIdx]?.classList.remove('active');
        const newIdx = Math.max(0, Math.min(items.length - 1, activeIdx + dir));
        items[newIdx]?.classList.add('active');
        items[newIdx]?.scrollIntoView({ block: 'nearest' });
    }

    // Channels, Notes, Memory, Prompts, Tools — managed via /workspace sub-pages
    // Stub methods to prevent errors from any remaining references
    closeChannelsPanel() {}
    closeNotesPanel() {}
    closeMemoryPanel() {}
    closeToolsPanel() {}

    // --- Tags ---

    initTags() {
        this.tags = [];
        this.tagsModalConvId = null;

        const tagsModal = document.getElementById('tagsModal');
        if (!tagsModal) return;

        // Close button
        const closeBtn = tagsModal.querySelector('[data-action="closeTagsModal"]');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => tagsModal.classList.remove('open'));
        }
        tagsModal.addEventListener('click', (e) => {
            if (e.target === tagsModal) tagsModal.classList.remove('open');
        });

        // Create tag
        const createTagBtn = document.getElementById('createTagBtn');
        if (createTagBtn) {
            createTagBtn.addEventListener('click', () => {
                const name = document.getElementById('tagNameInput').value.trim();
                const color = document.getElementById('tagColorInput').value;
                if (name) {
                    this.createTag(name, color);
                }
            });
        }

        // Tag list click delegation (delete + assign)
        const tagList = document.getElementById('tagList');
        if (tagList) {
            tagList.addEventListener('click', (e) => {
                const deleteBtn = e.target.closest('.tag-item-delete');
                if (deleteBtn) {
                    this.deleteTag(deleteBtn.dataset.tagId);
                }
            });
        }

        const tagAssignList = document.getElementById('tagAssignList');
        if (tagAssignList) {
            tagAssignList.addEventListener('click', (e) => {
                const tagItem = e.target.closest('.tag-item');
                if (!tagItem || !this.tagsModalConvId) return;
                const tagId = tagItem.dataset.tagId;
                if (tagItem.classList.contains('assigned')) {
                    this.untagConversation(tagId, this.tagsModalConvId);
                } else {
                    this.tagConversation(tagId, this.tagsModalConvId);
                }
            });
        }

        // Tag action is now handled by the unified context menu (showConvContextMenu)
    }

    async loadTags() {
        try {
            const resp = await fetch('/api/tags');
            if (!resp.ok) return;
            this.tags = await resp.json();
            this.renderTagList();
        } catch (e) {
            console.error('Failed to load tags:', e);
        }
    }

    renderTagList() {
        const tagList = document.getElementById('tagList');
        if (!tagList) return;

        if (this.tags.length === 0) {
            tagList.innerHTML = '<p class="tag-list-empty">No tags yet</p>';
            return;
        }

        tagList.innerHTML = this.tags.map(tag => `
            <div class="tag-item" data-tag-id="${tag.id}">
                <div class="tag-color" style="background-color: ${this.escapeHtml(tag.color || '#10a37f')}"></div>
                <span class="tag-item-name">${this.escapeHtml(tag.name)}</span>
                <button class="tag-item-delete" data-tag-id="${tag.id}" title="Delete tag">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                </button>
            </div>
        `).join('');
    }

    async createTag(name, color) {
        try {
            const resp = await fetch('/api/tags', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, color })
            });
            if (!resp.ok) throw new Error('Failed to create tag');
            document.getElementById('tagNameInput').value = '';
            this.showToast('Tag created');
            await this.loadTags();
            if (this.tagsModalConvId) {
                this.renderTagAssignList(this.tagsModalConvId);
            }
        } catch (e) {
            console.error('Failed to create tag:', e);
            this.showToast('Failed to create tag');
        }
    }

    async deleteTag(id) {
        if (!confirm('Delete this tag?')) return;
        try {
            const resp = await fetch(`/api/tags/${id}`, { method: 'DELETE' });
            if (!resp.ok) throw new Error('Failed to delete tag');
            this.showToast('Tag deleted');
            await this.loadTags();
            if (this.tagsModalConvId) {
                this.renderTagAssignList(this.tagsModalConvId);
            }
        } catch (e) {
            console.error('Failed to delete tag:', e);
            this.showToast('Failed to delete tag');
        }
    }

    async tagConversation(tagId, convId) {
        try {
            const resp = await fetch(`/api/tags/${tagId}/conversations/${convId}`, { method: 'POST' });
            if (!resp.ok) throw new Error('Failed to tag conversation');
            this.showToast('Tag assigned');
            this.renderTagAssignList(convId);
        } catch (e) {
            console.error('Failed to tag conversation:', e);
        }
    }

    async untagConversation(tagId, convId) {
        try {
            const resp = await fetch(`/api/tags/${tagId}/conversations/${convId}`, { method: 'DELETE' });
            if (!resp.ok) throw new Error('Failed to untag conversation');
            this.showToast('Tag removed');
            this.renderTagAssignList(convId);
        } catch (e) {
            console.error('Failed to untag conversation:', e);
        }
    }

    async showTagsModal(convId) {
        this.tagsModalConvId = convId || null;
        const tagsModal = document.getElementById('tagsModal');
        if (!tagsModal) return;

        await this.loadTags();

        const assignSection = document.getElementById('tagAssignSection');
        if (convId && assignSection) {
            assignSection.style.display = '';
            this.renderTagAssignList(convId);
        } else if (assignSection) {
            assignSection.style.display = 'none';
        }

        tagsModal.classList.add('open');
    }

    async renderTagAssignList(convId) {
        const assignList = document.getElementById('tagAssignList');
        if (!assignList) return;

        // Determine which tags are already on this conversation
        const assignedTagIds = new Set();
        for (const tag of this.tags) {
            try {
                const resp = await fetch(`/api/tags/${tag.id}/conversations`);
                if (resp.ok) {
                    const convs = await resp.json();
                    if (convs.some(c => c.id === convId)) {
                        assignedTagIds.add(tag.id);
                    }
                }
            } catch (e) { /* skip */ }
        }

        assignList.innerHTML = this.tags.map(tag => `
            <div class="tag-item ${assignedTagIds.has(tag.id) ? 'assigned' : ''}" data-tag-id="${tag.id}">
                <div class="tag-color" style="background-color: ${this.escapeHtml(tag.color || '#10a37f')}"></div>
                <span class="tag-item-name">${this.escapeHtml(tag.name)}</span>
            </div>
        `).join('');
    }

    // --- Translation ---

    showTranslateDropdown(messageEl) {
        // Remove any existing dropdown
        const existing = document.querySelector('.translate-dropdown');
        if (existing) existing.remove();

        const translateBtn = messageEl.querySelector('[data-action="translate"]');
        if (!translateBtn) return;

        const languages = [
            { code: 'es', name: 'Spanish' },
            { code: 'fr', name: 'French' },
            { code: 'de', name: 'German' },
            { code: 'it', name: 'Italian' },
            { code: 'pt', name: 'Portuguese' },
            { code: 'zh', name: 'Chinese' },
            { code: 'ja', name: 'Japanese' },
            { code: 'ko', name: 'Korean' },
            { code: 'ar', name: 'Arabic' },
            { code: 'hi', name: 'Hindi' },
            { code: 'ru', name: 'Russian' },
            { code: 'en', name: 'English' }
        ];

        const dropdown = document.createElement('div');
        dropdown.className = 'translate-dropdown';
        dropdown.innerHTML = languages.map(lang =>
            `<button class="translate-dropdown-item" data-lang="${lang.code}">${lang.name}</button>`
        ).join('');

        translateBtn.style.position = 'relative';
        translateBtn.appendChild(dropdown);

        const handleClick = (e) => {
            const item = e.target.closest('.translate-dropdown-item');
            if (item) {
                this.translateMessage(messageEl, item.dataset.lang);
                dropdown.remove();
            }
        };
        dropdown.addEventListener('click', handleClick);

        // Close on outside click
        const closeDropdown = (e) => {
            if (!dropdown.contains(e.target) && e.target !== translateBtn) {
                dropdown.remove();
                document.removeEventListener('click', closeDropdown);
            }
        };
        setTimeout(() => document.addEventListener('click', closeDropdown), 0);
    }

    async translateMessage(messageEl, targetLang) {
        const textEl = messageEl.querySelector('.message-text');
        if (!textEl) return;

        const text = textEl.innerText;
        if (!text.trim()) return;

        // Remove any existing translation
        const existingTranslation = messageEl.querySelector('.translated-text');
        if (existingTranslation) existingTranslation.remove();

        // Show loading
        const loadingEl = document.createElement('div');
        loadingEl.className = 'translated-text';
        loadingEl.innerHTML = '<div class="search-loading"><div class="search-loading-spinner"></div> Translating...</div>';
        textEl.after(loadingEl);

        try {
            const resp = await fetch('/api/translate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text, sourceLanguage: 'auto', targetLanguage: targetLang })
            });
            if (!resp.ok) throw new Error('Translation failed');
            const result = await resp.json();

            const langNames = {
                es: 'Spanish', fr: 'French', de: 'German', it: 'Italian',
                pt: 'Portuguese', zh: 'Chinese', ja: 'Japanese', ko: 'Korean',
                ar: 'Arabic', hi: 'Hindi', ru: 'Russian', en: 'English'
            };

            loadingEl.innerHTML = `
                <div class="translated-text-header">
                    <span class="translated-text-label">Translated to ${langNames[targetLang] || targetLang}</span>
                    <button class="translated-text-close" title="Remove translation">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                            <line x1="18" y1="6" x2="6" y2="18"></line>
                            <line x1="6" y1="6" x2="18" y2="18"></line>
                        </svg>
                    </button>
                </div>
                <div>${this.escapeHtml(result.translatedText || result.translation || '')}</div>
            `;

            const closeBtn = loadingEl.querySelector('.translated-text-close');
            if (closeBtn) {
                closeBtn.addEventListener('click', () => loadingEl.remove());
            }
        } catch (e) {
            console.error('Translation failed:', e);
            loadingEl.remove();
            this.showToast('Translation failed');
        }
    }

    formatFileSize(bytes) {
        if (!bytes) return '0 B';
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(1024));
        return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
    }
}

// Change Password Modal Functions
function showChangePasswordModal() {
    const modal = document.getElementById('changePasswordModal');
    if (modal) {
        document.getElementById('currentPassword').value = '';
        document.getElementById('newPasswordInput').value = '';
        document.getElementById('confirmPasswordInput').value = '';
        modal.classList.add('open');
        document.getElementById('currentPassword').focus();
    }
}

function closeChangePasswordModal() {
    const modal = document.getElementById('changePasswordModal');
    if (modal) {
        modal.classList.remove('open');
    }
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.chatApp = new ChatApp();

    // Render math in any SSR-rendered messages
    document.querySelectorAll('.message-text').forEach(el => {
        window.chatApp.renderMath(el);
    });

    // Change Password Modal Button Event Listeners
    const changePasswordBtn = document.querySelector('[data-action="changePassword"]');
    if (changePasswordBtn) {
        changePasswordBtn.addEventListener('click', showChangePasswordModal);
    }

    const cancelChangePasswordBtn = document.querySelector('[data-action="cancelChangePassword"]');
    if (cancelChangePasswordBtn) {
        cancelChangePasswordBtn.addEventListener('click', closeChangePasswordModal);
    }

    // Change Password Modal Handler
    const changePasswordModal = document.getElementById('changePasswordModal');
    const confirmChangePasswordBtn = document.getElementById('confirmChangePasswordBtn');

    if (confirmChangePasswordBtn) {
        confirmChangePasswordBtn.addEventListener('click', async () => {
            const currentPassword = document.getElementById('currentPassword').value;
            const newPassword = document.getElementById('newPasswordInput').value;
            const confirmPassword = document.getElementById('confirmPasswordInput').value;

            if (!currentPassword) {
                alert('Please enter your current password');
                return;
            }

            if (!newPassword || newPassword.length < 8 || !/[A-Z]/.test(newPassword) || !/[a-z]/.test(newPassword) || !/\d/.test(newPassword)) {
                alert('Password must be at least 8 characters with uppercase, lowercase, and a number');
                return;
            }

            if (newPassword !== confirmPassword) {
                alert('Passwords do not match');
                return;
            }

            try {
                const response = await fetch('/auth/change-password', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        currentPassword: currentPassword,
                        newPassword: newPassword
                    })
                });

                const data = await response.json();
                if (data.success) {
                    alert('Password changed successfully');
                    closeChangePasswordModal();
                } else {
                    alert(data.error || 'Failed to change password');
                }
            } catch (error) {
                console.error('Error changing password:', error);
                alert('Failed to change password');
            }
        });
    }

    if (changePasswordModal) {
        changePasswordModal.addEventListener('click', (e) => {
            if (e.target === changePasswordModal) {
                closeChangePasswordModal();
            }
        });

        // Enter key submits the password form
        changePasswordModal.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && confirmChangePasswordBtn) {
                e.preventDefault();
                confirmChangePasswordBtn.click();
            }
        });
    }
});
