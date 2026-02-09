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
        this.useTools = true;
        this.toolsAvailable = false;
        this.tools = [];
        this.toolPreferences = {};
        this._renderPending = false;
        this._lastRenderTime = 0;
        this._renderThrottleMs = 80;

        this.initElements();
        this.initEventListeners();
        this.initMarkdown();
        this.initTheme();
        this.initDocuments();
        this.initTools();
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

        // Tools elements
        this.useToolsToggle = document.getElementById('useToolsToggle');
        this.toolsToggleLabel = document.getElementById('toolsToggleLabel');
        this.toolsPanel = document.getElementById('toolsPanel');
        this.toolsBtn = document.getElementById('toolsBtn');
        this.closeToolsPanelBtn = document.getElementById('closeToolsPanelBtn');
        this.toolsList = document.getElementById('toolsList');
        this.toolCount = document.getElementById('toolCount');
        this.enabledToolCount = document.getElementById('enabledToolCount');
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

            if (deleteBtn) {
                e.stopPropagation();
                const id = deleteBtn.dataset.id;
                this.deleteConversation(id);
            } else if (item) {
                const id = item.dataset.id;
                this.loadConversation(id);
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

        // Use tools toggle — registered here (synchronous) rather than in async
        // initTools() to ensure the listener is always attached
        if (this.useToolsToggle) {
            this.useToolsToggle.addEventListener('change', (e) => {
                this.useTools = e.target.checked;
                localStorage.setItem('useTools', this.useTools);
                console.debug('Tools toggle changed:', this.useTools);
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

        // Load organization theme if user is part of an organization
        this.loadOrganizationTheme();
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
        const currentTheme = document.body.getAttribute('data-theme') || 'dark';
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        document.body.setAttribute('data-theme', newTheme);
        localStorage.setItem('theme', newTheme);
    }

    initMarkdown() {
        // Custom renderer for marked v12 API (object parameters)
        // Only override renderers that receive pre-rendered strings — NOT ones
        // that receive tokens (tablecell, heading, paragraph etc.) since those
        // need this.parser which may not be available in marked.use() context
        const renderer = {
            code({ text, lang }) {
                const safeText = text || '';
                if (!safeText.trim()) return ''; // Skip empty code block outlines during streaming
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
            table({ header, body }) {
                if (!body) {
                    // During streaming: header arrived but no body rows yet — show
                    // header content as text instead of an empty table skeleton
                    return header || '';
                }
                return `<div class="table-wrapper"><table class="markdown-table"><thead>${header || ''}</thead><tbody>${body}</tbody></table></div>`;
            },
            tablerow({ text }) {
                if (!text) return ''; // Skip empty rows during streaming
                return `<tr>${text}</tr>\n`;
            }
        };

        marked.use({ renderer, breaks: true, gfm: true });
    }

    prepareStreamingContent(raw) {
        let safe = raw;

        // 1. Unclosed code fences — count ``` at start of lines
        const fencePattern = /^```/gm;
        const fences = safe.match(fencePattern);
        if (fences && fences.length % 2 !== 0) {
            // Odd number of fences means the last one is unclosed — trim to before it
            const lastFenceIdx = safe.lastIndexOf('```');
            safe = safe.substring(0, lastFenceIdx);
        }

        // 2. Incomplete tables — if trailing lines look like a header/separator
        //    with no body rows, trim them
        const lines = safe.split('\n');
        let tableStart = -1;
        for (let i = lines.length - 1; i >= 0; i--) {
            const line = lines[i].trim();
            if (!line) continue;
            // Table separator line (|---|---|)
            if (/^\|[\s\-:|]+\|$/.test(line)) {
                tableStart = i;
                continue;
            }
            // Table header line (| head | head |)
            if (tableStart !== -1 && /^\|.+\|$/.test(line)) {
                // Check if next non-empty line after separator exists (body row)
                let hasBody = false;
                for (let j = tableStart + 1; j < lines.length; j++) {
                    const bodyLine = lines[j].trim();
                    if (!bodyLine) continue;
                    if (/^\|.+\|$/.test(bodyLine)) {
                        hasBody = true;
                    }
                    break;
                }
                if (!hasBody) {
                    safe = lines.slice(0, i).join('\n');
                }
            }
            break;
        }

        // 3. Unclosed emphasis on the last line
        const lastNewline = safe.lastIndexOf('\n');
        const lastLine = safe.substring(lastNewline + 1);
        // Count unmatched ** or * markers
        const boldCount = (lastLine.match(/\*\*/g) || []).length;
        if (boldCount % 2 !== 0) {
            const idx = safe.lastIndexOf('**');
            safe = safe.substring(0, idx);
        } else {
            // Check single * (but not **)
            const stripped = lastLine.replace(/\*\*/g, '');
            const italicCount = (stripped.match(/\*/g) || []).length;
            if (italicCount % 2 !== 0) {
                const idx = safe.lastIndexOf('*');
                safe = safe.substring(0, idx);
            }
        }

        // 4. Unclosed links — [ after last ]
        const lastClose = safe.lastIndexOf(']');
        const lastOpen = safe.lastIndexOf('[');
        if (lastOpen > lastClose) {
            safe = safe.substring(0, lastOpen);
        }

        return safe;
    }

    scheduleStreamingRender(textEl, fullContent) {
        // Store the latest content — only the most recent matters
        this._latestStreamContent = fullContent;
        this._latestStreamTarget = textEl;

        if (this._renderPending) return; // coalesce rapid chunks

        const now = Date.now();
        const elapsed = now - this._lastRenderTime;
        const delay = Math.max(0, this._renderThrottleMs - elapsed);

        this._renderPending = true;
        setTimeout(() => {
            requestAnimationFrame(() => {
                if (!this._renderPending) return; // cancelled by complete event
                try {
                    const prepared = this.prepareStreamingContent(this._latestStreamContent);
                    this._latestStreamTarget.innerHTML = DOMPurify.sanitize(marked.parse(prepared));
                } catch (renderErr) {
                    this._latestStreamTarget.textContent = this._latestStreamContent;
                }
                this._lastRenderTime = Date.now();
                this._renderPending = false;
                this.scrollToBottom();
            });
        }, delay);
    }

    autoResizeTextarea() {
        this.messageInput.style.height = 'auto';
        this.messageInput.style.height = Math.min(this.messageInput.scrollHeight, 200) + 'px';
    }

    async sendMessage() {
        const message = this.messageInput.value.trim();
        if (!message || this.isWaiting) return;

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

            const request = {
                conversationId: this.conversationId,
                message: message,
                provider: provider,
                model: model,
                useDocumentContext: this.useDocumentContext && this.documentsAvailable,
                useTools: toolsEnabled && this.toolsAvailable
            };

            console.debug('Chat request - useTools:', request.useTools, '(toggle:', toolsEnabled, ', available:', this.toolsAvailable, ')');

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

        this.conversationId = data.conversationId;
        this.addMessage('assistant', data.content, data.htmlContent, data.model);
        this.updateURL(data.conversationId);
        this.refreshConversationsList();
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
            this._renderPending = false;

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

                            if (!this.conversationId && data.conversationId) {
                                this.conversationId = data.conversationId;
                                this.updateURL(data.conversationId);
                            }

                            if (data.error) {
                                streamComplete = true;
                                this._renderPending = false;
                                textEl.innerHTML = DOMPurify.sanitize(`<div class="stream-error">${this.escapeHtml(data.error)}</div>`);
                                this.scrollToBottom();
                            } else if (data.complete) {
                                streamComplete = true;
                                this._renderPending = false;
                                // Final message with full HTML content from backend
                                if (data.htmlContent) {
                                    textEl.innerHTML = DOMPurify.sanitize(data.htmlContent);
                                }
                                this.highlightCode(textEl);

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

                                this.refreshConversationsList();
                            } else if (data.content) {
                                fullContent += data.content;
                                this.scheduleStreamingRender(textEl, fullContent);
                            }
                        } catch (e) {
                            console.warn('SSE parse error:', e.message, 'Line:', line);
                        }
                    }
                }
            }

            // Fallback: only re-render if stream ended without a complete event
            if (fullContent && !streamComplete) {
                textEl.innerHTML = DOMPurify.sanitize(marked.parse(fullContent));
                this.highlightCode(textEl);
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

    addMessage(role, content, htmlContent = null, model = null) {
        if (!this.messages) {
            const messagesDiv = document.createElement('div');
            messagesDiv.className = 'messages';
            messagesDiv.id = 'messages';
            this.messagesContainer.appendChild(messagesDiv);
            this.messages = messagesDiv;
        }

        const messageEl = this.createMessageElement(role, content, htmlContent, model);
        this.messages.appendChild(messageEl);
        this.highlightCode(messageEl);
        this.scrollToBottom();
    }

    createMessageElement(role, content, htmlContent = null, model = null) {
        const div = document.createElement('div');
        div.className = `message ${role}`;

        const avatarText = role === 'user' ? 'U' : 'AI';
        const displayContent = DOMPurify.sanitize(htmlContent || (content ? marked.parse(content) : ''));

        div.innerHTML = `
            <div class="message-avatar">${avatarText}</div>
            <div class="message-content">
                <div class="message-text">${displayContent}</div>
                ${model ? `<div class="message-meta">${model}</div>` : ''}
            </div>
        `;

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

            // Create header with language label and copy button
            const header = document.createElement('div');
            header.className = 'code-block-header';
            header.innerHTML = `
                <span class="code-language">${this.escapeHtml(language)}</span>
                <button class="copy-code-btn" title="Copy code">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                    <span>Copy</span>
                </button>
            `;

            // Insert header before the pre content
            pre.insertBefore(header, pre.firstChild);

            // Add copy functionality
            const copyBtn = header.querySelector('.copy-code-btn');
            copyBtn.addEventListener('click', async () => {
                const code = block.textContent;
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
                this.addMessage(msg.role, msg.content, msg.htmlContent, msg.modelUsed);
            });

            this.sidebar.classList.remove('open');
        } catch (error) {
            console.error('Error loading conversation:', error);
            this.addErrorMessage('Failed to load conversation.');
        }
    }

    async deleteConversation(id) {
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
                }
            }
        } catch (error) {
            console.warn('Document service not available:', error);
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
            this.uploadArea.addEventListener('click', () => {
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

                // Show "Use My Docs" toggle if user has documents
                this.documentsAvailable = stats.completedDocuments > 0;
                if (this.docToggleLabel) {
                    this.docToggleLabel.style.display = this.documentsAvailable ? 'flex' : 'none';
                }
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

                // Show Tools button if tools are available
                if (this.toolsBtn) {
                    this.toolsBtn.style.display = this.toolsAvailable ? 'flex' : 'none';
                }

                // Set initial toggle state from saved preference (defaults to true from constructor)
                const savedPref = localStorage.getItem('useTools');
                if (savedPref !== null) {
                    this.useTools = savedPref === 'true';
                }
                if (this.useToolsToggle) {
                    this.useToolsToggle.checked = this.useTools;
                }

                // Set up tools panel if tools are available
                if (this.toolsAvailable) {
                    this.initToolsEventListeners();
                    this.renderTools();
                    this.updateToolStats();
                }
            }
        } catch (error) {
            console.warn('Could not check tools availability:', error);
            // Hide the toggle and button if we can't determine availability
            if (this.toolsToggleLabel) {
                this.toolsToggleLabel.style.display = 'none';
            }
            if (this.toolsBtn) {
                this.toolsBtn.style.display = 'none';
            }
        }
    }

    initToolsEventListeners() {
        // Tools button
        if (this.toolsBtn) {
            this.toolsBtn.addEventListener('click', () => {
                this.toggleToolsPanel();
            });
        }

        // Close tools panel button
        if (this.closeToolsPanelBtn) {
            this.closeToolsPanelBtn.addEventListener('click', () => {
                this.closeToolsPanel();
            });
        }
    }

    toggleToolsPanel() {
        if (this.toolsPanel) {
            // Close documents panel if open
            this.closeDocumentsPanel();
            this.toolsPanel.classList.toggle('open');
            if (this.toolsPanel.classList.contains('open')) {
                this.loadTools();
            }
        }
    }

    closeToolsPanel() {
        if (this.toolsPanel) {
            this.toolsPanel.classList.remove('open');
        }
    }

    async loadTools() {
        try {
            const response = await fetch('/api/chat/available-tools');
            if (response.ok) {
                this.tools = await response.json();
                this.renderTools();
                this.updateToolStats();
            }
        } catch (error) {
            console.error('Error loading tools:', error);
        }
    }

    renderTools() {
        if (!this.toolsList) return;

        if (this.tools.length === 0) {
            this.toolsList.innerHTML = '<p class="no-tools">No tools available</p>';
            return;
        }

        this.toolsList.innerHTML = this.tools.map(tool => {
            const isEnabled = this.isToolEnabled(tool.id);
            const badgeClass = tool.type === 'MCP' ? 'mcp' : 'custom';
            const serverInfo = tool.mcpServerName ? `<div class="tool-server">Server: ${this.escapeHtml(tool.mcpServerName)}</div>` : '';

            return `
                <div class="tool-item" data-id="${tool.id}">
                    <div class="tool-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"></path>
                        </svg>
                    </div>
                    <div class="tool-info">
                        <div class="tool-header">
                            <span class="tool-name" title="${this.escapeHtml(tool.displayName || tool.name)}">${this.escapeHtml(tool.displayName || tool.name)}</span>
                            <span class="tool-badge ${badgeClass}">${tool.type}</span>
                        </div>
                        ${tool.description ? `<div class="tool-description" title="${this.escapeHtml(tool.description)}">${this.escapeHtml(tool.description)}</div>` : ''}
                        ${serverInfo}
                    </div>
                    <div class="tool-toggle">
                        <input type="checkbox" ${isEnabled ? 'checked' : ''}
                               data-tool-id="${tool.id}"
                               title="${isEnabled ? 'Disable tool' : 'Enable tool'}">
                    </div>
                </div>
            `;
        }).join('');

        // Attach event listeners (CSP-compliant, no inline handlers)
        this.toolsList.querySelectorAll('input[data-tool-id]').forEach(checkbox => {
            checkbox.addEventListener('change', () => {
                this.toggleToolEnabled(checkbox);
            });
        });
    }

    updateToolStats() {
        const enabledCount = this.tools.filter(tool => this.isToolEnabled(tool.id)).length;

        if (this.toolCount) {
            this.toolCount.textContent = `${this.tools.length} tool${this.tools.length !== 1 ? 's' : ''}`;
        }
        if (this.enabledToolCount) {
            this.enabledToolCount.textContent = `${enabledCount} enabled`;
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
