// CF Llama Chat - Main Application JavaScript

class ChatApp {
    constructor() {
        this.conversationId = window.APP_DATA?.conversationId || null;
        this.models = window.APP_DATA?.models || [];
        this.currentUser = window.APP_DATA?.currentUser || null;
        this.isStreaming = true;
        this.isWaiting = false;

        this.initElements();
        this.initEventListeners();
        this.initMarkdown();
        this.initTheme();
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
        });

        // Stream toggle
        this.streamToggle.addEventListener('change', (e) => {
            this.isStreaming = e.target.checked;
        });

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

        // Close sidebar on mobile when clicking outside
        document.addEventListener('click', (e) => {
            if (window.innerWidth <= 768) {
                if (!this.sidebar.contains(e.target) && !this.sidebarToggle.contains(e.target)) {
                    this.sidebar.classList.remove('open');
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
        // Configure marked for code highlighting
        marked.setOptions({
            highlight: function(code, lang) {
                if (lang && hljs.getLanguage(lang)) {
                    return hljs.highlight(code, { language: lang }).value;
                }
                return hljs.highlightAuto(code).value;
            },
            breaks: true,
            gfm: true
        });
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

        // Get selected model
        const modelValue = this.modelSelect.value;
        const [provider, model] = modelValue ? modelValue.split(':') : ['openai', 'gpt-4o-mini'];

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
            const request = {
                conversationId: this.conversationId,
                message: message,
                provider: provider,
                model: model
            };

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
        const response = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
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

                        // Debug: log final response to see metrics
                        if (data.complete) {
                            console.log('Final streaming response:', JSON.stringify(data, null, 2));
                            // Final message with full HTML content
                            if (data.htmlContent) {
                                textEl.innerHTML = data.htmlContent;
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
                            textEl.innerHTML = marked.parse(fullContent);
                            this.scrollToBottom();
                        }
                    } catch (e) {
                        console.warn('SSE parse error:', e.message, 'Line:', line);
                    }
                }
            }
        }
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
        const displayContent = htmlContent || (content ? marked.parse(content) : '');

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
            hljs.highlightElement(block);
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
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.chatApp = new ChatApp();
});
