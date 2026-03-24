# CF Llama Chat — Complete Feature Guide

> A comprehensive how-to user guide for CF Llama Chat, the enterprise AI chat platform built on Spring Boot and Spring AI for Cloud Foundry and Tanzu Platform.

---

## Problem Statement

Enterprise teams need a secure, self-hosted AI chat platform that runs on their existing Cloud Foundry or Tanzu Platform infrastructure. Off-the-shelf SaaS tools like ChatGPT don't meet compliance requirements, lack integration with internal services, and offer no control over model selection or data residency. CF Llama Chat solves this by providing a full-featured, multi-model AI chat experience that deploys with a simple `cf push` and binds directly to Tanzu GenAI tile services.

---

## Scene 1: Deploying CF Llama Chat to Cloud Foundry

CF Llama Chat deploys to Cloud Foundry with zero custom infrastructure. The entire application ships as a single Spring Boot JAR.

### Step 1 — Build the application

Open a terminal in the project root and run the Maven build:

```bash
mvn clean package -DskipTests -q
```

This produces a deployable JAR file in the `target/` directory.

### Step 2 — Review the manifest

The `manifest.yml` file defines the Cloud Foundry deployment configuration including memory allocation, buildpack, bound services, and environment variables. It binds to GenAI tile services for model access and a PostgreSQL database for persistence.

### Step 3 — Push to Cloud Foundry

Deploy with a single command:

```bash
cf push
```

Cloud Foundry detects the Java buildpack, stages the application, binds the configured services, and starts the app. The application automatically reads model credentials from `VCAP_SERVICES` environment variables — no manual API key configuration required.

### Step 4 — Access the application

Once the push completes, open the application URL shown in the terminal output. You'll see the CF Llama Chat login page. Sign in with the default admin credentials or your SSO provider.

---

## Scene 2: The Chat Interface — Your AI Conversation Hub

The main chat interface is where users spend most of their time. It provides a modern, responsive conversation experience with real-time streaming.

### Starting a new conversation

Click the "New Chat" button in the sidebar or use the keyboard shortcut. The welcome screen displays with quick-action example prompts to help you get started. Click any example prompt or type your own question in the message input area at the bottom.

### Selecting an AI model

Use the model selector dropdown at the top of the chat area to choose which AI model powers your conversation. CF Llama Chat supports multiple providers simultaneously — OpenAI models, Ollama local models, Tanzu GenAI tile models, and external API-compatible models. Switch models mid-conversation at any time.

### Streaming vs. non-streaming responses

Toggle the "Stream" switch next to the message input to control response delivery. When streaming is enabled, you see the AI's response appear token by token in real time via Server-Sent Events. When disabled, the full response loads at once. Streaming provides a more interactive experience for longer responses.

### Using the chat input

Type your message in the input field. Press Enter to send, or Shift+Enter to add a new line for multi-line messages. The AI responds with fully rendered Markdown including syntax-highlighted code blocks, tables, math equations via KaTeX, and formatted lists.

### Temporary chats

Toggle the "Temporary" switch to start a conversation that won't be saved to your history. This is useful for quick one-off questions you don't need to reference later.

---

## Scene 3: RAG — Chat With Your Documents

CF Llama Chat includes built-in Retrieval-Augmented Generation that lets the AI reference your uploaded documents when answering questions.

### Uploading documents

Navigate to the Workspace by clicking "Workspace" in the header navigation, then select "Documents" from the dashboard. Click the upload area or drag and drop files directly. Supported formats include PDF, Word documents, plain text, and Markdown files. You can also import YouTube video transcripts by pasting a YouTube URL.

Each uploaded document is automatically chunked into smaller segments and converted into vector embeddings stored in PostgreSQL with pgvector. The upload progress bar shows real-time status, and once complete, you'll see the document listed with its file size and chunk count.

### Activating document search in chat

Return to the chat interface and toggle the "Use My Docs" switch next to the message input. When enabled, the AI performs a semantic search across your uploaded documents before generating a response. Relevant document chunks are injected into the prompt as context, so the AI can cite and reference your specific content.

### How it works

When you send a message with document search enabled, the system converts your question into a vector embedding, performs a similarity search against your document chunks using pgvector, retrieves the most relevant passages, and includes them as context in the AI prompt. The AI then generates a response grounded in your actual documents rather than just its training data.

### Managing documents

From the Documents page in your Workspace, you can view all uploaded documents, see chunk counts and file sizes, and delete documents you no longer need. Documents are isolated per user — each user's uploads are private and only searchable by that user.

---

## Scene 4: MCP Tools — Extending AI Capabilities

The Model Context Protocol integration allows the AI to call external tools and services during conversations, giving it the ability to take actions beyond just generating text.

### Enabling tools in chat

In the chat interface, toggle the "Use Tools" switch next to the message input. When enabled, the AI can invoke any tools that have been registered and enabled by your administrator. The AI decides when to use tools based on the context of your question.

### Browsing available tools

Navigate to the Workspace and select "Tools" from the dashboard. Here you see all available tools organized by their source MCP server. Each tool displays its name, description, and type badge. Use the server filter dropdown to view tools from a specific server.

### Toggling individual tools

Click the toggle switch next to any tool to enable or disable it for your conversations. You can also use the bulk select and deselect buttons to enable or disable all tools from a particular server at once. Your tool preferences are saved locally and persist across sessions.

### How tools work in practice

When you ask the AI a question that could benefit from external data or actions, it automatically selects the appropriate tool, calls it with the right parameters, receives the result, and incorporates that information into its response. For example, if a weather tool is registered, asking "What's the weather in San Francisco?" triggers the AI to call the weather API and return real-time data.

---

## Scene 5: Conversation Management

CF Llama Chat provides rich conversation organization features to help you manage and find past chats.

### Searching conversations

Use the search bar at the top of the sidebar to filter your conversation list by keyword. Results update in real time as you type.

### Organizing with tabs

The sidebar offers three tabs for filtering conversations: "All" shows every conversation, "Favorites" shows only pinned conversations, and "Projects" shows conversations organized into folders.

### Pinning favorites

Right-click any conversation in the sidebar or use its context menu to pin it as a favorite. Pinned conversations appear with a star icon and are accessible from the Favorites tab for quick access.

### Tagging conversations

Add tags to conversations to categorize them by topic, project, or any label you choose. Tags make it easy to filter and find related conversations later.

### Moving to projects

Create project folders to group related conversations together. Right-click a conversation and select "Move to folder" to organize it into a project. Access all project folders from the Projects tab.

### Renaming and deleting

Right-click any conversation to rename it with a custom title, or delete it when no longer needed. Use the "Clear All History" button at the bottom of the sidebar to remove all conversations at once.

---

## Scene 6: Workspace Hub — Your Personal Productivity Center

The Workspace Hub is your central dashboard for managing personal productivity features. Access it by clicking "Workspace" in the header navigation.

### Dashboard overview

The Workspace dashboard displays card-based statistics showing your total Channels, Notes, Memories, Prompts, Documents, and Tools. Each card links to its respective management page.

### Channels — Real-time group messaging

Click "Channels" from the dashboard. Create a new channel by providing a name and description. Channels enable real-time group messaging with other users on the platform. Click into a channel to view its message history and send new messages. Messages stream in real time via Server-Sent Events, so you see new messages instantly without refreshing.

### Notes — Personal knowledge base

Click "Notes" from the dashboard. Create notes with a title and rich Markdown content. Use the Markdown preview toggle to see rendered output as you write. Notes serve as your personal knowledge base within the platform — jot down meeting notes, code snippets, research findings, or anything you want to reference later.

### Memory — Persistent AI context

Click "Memory" from the dashboard. Add memory entries that the AI remembers across all your conversations. Categorize memories as General, Preference, Fact, or Instruction. For example, add a memory like "I work on the payments team and prefer Python code examples" — the AI will reference this context in future conversations without you having to repeat it.

Search memories by content and filter by category. Delete memories when they're no longer relevant.

### Prompt Presets — Reusable templates

Click "Prompts" from the dashboard. Create reusable prompt templates with placeholder variables using double curly brace syntax. Assign slash commands to prompts for quick access — for example, create a `/summarize` command that expands into a detailed summarization prompt. Mark prompts as shared to make them available to all users on the platform. Edit and manage existing prompts from this page.

---

## Scene 7: User Settings and Personalization

Click your avatar in the header to open the user menu, then select "Settings" to access the full settings page.

### Appearance

Choose between Dark, Light, and OLED themes. Select a background style — None for a clean look, Gradient for a modern feel, or Pattern for texture. Adjust font size between Small, Medium, and Large. Set message density to Compact, Normal, or Comfortable based on your preference.

### Chat preferences

Select your default AI model so every new conversation starts with your preferred model. Toggle streaming responses on or off by default. Enable auto-titling to have the AI automatically generate descriptive titles for your conversations. Write custom system instructions up to 2000 characters that are prepended to every conversation — for example, "Always respond in bullet points" or "You are a senior Java developer."

### Language and region

Select your preferred language from English, Spanish, French, German, Japanese, Chinese, or Arabic. Choose your preferred date format.

### Notifications

Enable desktop notifications to get alerts for new channel messages and other events. Toggle sound notifications on or off.

### Data and privacy

Export all your data as a JSON file for backup or portability. Delete all conversations with a confirmation dialog. In the Danger Zone section, permanently delete your account — this action requires password confirmation and is irreversible.

---

## Scene 8: Admin Portal — User Management

Administrators access the Admin Portal by clicking "Admin" in the header navigation. The admin dashboard shows platform-wide statistics including total users, administrators, available models, and organizations.

### Managing users

Click "Users" from the admin dashboard. View all registered users with their roles and status. Create new users by providing a username and password. Promote users to administrator or demote administrators back to regular users. Reset passwords for users who are locked out. Delete user accounts when employees leave the organization.

### User groups

Click "Groups" from the admin dashboard. Create groups to organize users — for example, by department or project team. Assign users to groups for easier permission management and bulk operations.

---

## Scene 9: Admin Portal — Model Configuration

Click "Models" from the admin dashboard to manage AI model availability.

### Viewing available models

The models page lists all configured AI models with their provider, status, name, and description. Models are automatically discovered from bound GenAI tile services via VCAP_SERVICES.

### Adding external models

Click the "Add External Model" button to connect additional OpenAI-compatible API endpoints. Provide the model name, API endpoint URL, and API key. This allows you to integrate models hosted on other infrastructure — such as a private Ollama instance or any OpenAI-compatible API — without redeploying the application.

### Testing connectivity

Use the test button to verify that a model endpoint is reachable and responding correctly before making it available to users.

---

## Scene 10: Admin Portal — MCP Server Management

Click "MCP" from the admin dashboard to manage Model Context Protocol server connections.

### Registering an MCP server

Click "Add MCP Server" and provide the server name, display name, transport type (SSE or STDIO), and connection details. The transport type determines how the application communicates with the MCP server — SSE for remote HTTP servers and STDIO for local process-based servers.

### Auto-discovery

When running on Cloud Foundry with bound MCP services, CF Llama Chat automatically discovers and registers MCP servers from service bindings. No manual configuration needed for platform-managed MCP services.

### Managing tools from servers

After registering a server, its tools appear in the admin tools list. Use the server filter dropdown to view tools from a specific server. Enable or disable individual tools to control which capabilities are available to users. Use bulk select and deselect to manage all tools from a server at once.

### Deleting servers

Remove an MCP server registration when it's no longer needed. This also removes all associated tools from the platform.

---

## Scene 11: Admin Portal — Skills Configuration

Click "Skills" from the admin dashboard. Skills combine tools with custom system prompts to create focused AI capabilities.

### Creating a skill

Click "Add Skill" and provide a name and description. Assign one or more tools to the skill — these are the tools the AI can use when the skill is active. Write a custom system prompt that guides the AI's behavior when using this skill. For example, create a "Database Assistant" skill that combines a SQL query tool with a system prompt instructing the AI to always explain queries before executing them.

### Managing skills

Enable or disable skills to control availability. Edit existing skills to update their tools or system prompts. Delete skills that are no longer needed.

---

## Scene 12: Admin Portal — Organization Theming and Multi-Tenancy

Click "Organizations" from the admin dashboard. Organizations enable multi-tenant deployment with custom branding for different teams or clients.

### Creating an organization

Click "Add Organization" and provide a name and URL slug. The slug determines the organization's URL path — for example, a slug of "acme" means users access the platform at `/acme`.

### Custom branding

Each organization gets its own visual identity. Upload a custom logo that replaces the default in the header. Set a custom header text. Choose primary, accent, background, and sidebar colors to match corporate branding. Add a custom favicon. Write a custom welcome message that appears on the chat welcome screen. Inject custom CSS for advanced styling beyond the color pickers.

### Organization isolation

Users within an organization see only their organization's branding and configuration. This allows a single CF Llama Chat deployment to serve multiple teams or clients with distinct visual experiences.

---

## Scene 13: Admin Portal — System Administration

The Admin Portal includes several system administration features for monitoring and configuration.

### Database monitoring

Click "Database" from the admin dashboard. View PostgreSQL performance metrics including table statistics, index usage, active connections, and query performance. Monitor database size to plan capacity.

### System settings

Click "Settings" from the admin dashboard. Configure rate limiting to prevent API abuse. Set up content moderation rules. Manage feature flags to enable or disable platform capabilities. Control model access rules to restrict which models are available to specific users or groups.

### Document storage

Click "Storage" from the admin dashboard. Configure S3-compatible object storage for document persistence. Provide the S3 endpoint, bucket name, and access credentials. Enable or disable the storage backend.

### Notification banners

Click "Banners" from the admin dashboard. Create system-wide announcement banners that appear at the top of every page. Set banner styling — info, warning, error, or success. Schedule banners with start and end dates. Configure whether users can dismiss banners.

### Webhooks

Click "Webhooks" from the admin dashboard. Configure webhook endpoints to receive notifications when events occur on the platform — such as new messages, user activity, or system events. Test webhook delivery and view error logs for failed deliveries.

---

## Scene 14: Usage Metrics and Analytics

Click "Metrics" in the header navigation to view your usage analytics.

### Chat metrics

View your chat token usage broken down by model. See response time tracking to understand how quickly different models respond. Monitor request counts over time.

### Embedding metrics

Track document processing metrics including total documents, total chunks, and processing time. This helps you understand the RAG pipeline's performance and capacity.

### Per-model breakdown

See a detailed breakdown of usage per AI model — how many tokens each model has consumed, average response times, and request volumes. This data helps administrators make informed decisions about model provisioning and cost management.

---

## Scene 15: Authentication and Security

CF Llama Chat provides enterprise-grade authentication and role-based access control.

### Local authentication

Users can sign in with username and password credentials managed directly within the platform. Passwords are securely hashed with bcrypt. Users can change their passwords from the user menu. Administrators can reset passwords for other users.

### SSO and OAuth2

For enterprise deployments, CF Llama Chat integrates with Cloud Foundry's p-identity service for single sign-on via OAuth2. Users authenticate through their corporate identity provider — no separate credentials needed.

### Invitation codes

Administrators can optionally require invitation codes for new user registration. This prevents unauthorized signups on public-facing deployments.

### Role-based access control

The platform has two roles — Admin and User. Admins have full access to the Admin Portal and all management features. Regular users have access to the chat interface, workspace features, and their own settings. All admin-only features are hidden from the navigation for regular users.

---

## Summary

CF Llama Chat is a complete enterprise AI chat platform that deploys to Cloud Foundry with a single `cf push` command. Key capabilities include multi-model AI chat with real-time streaming, document-powered RAG with pgvector, MCP tool integration for extensible AI actions, rich workspace features including channels, notes, memory, and prompt templates, full admin portal with user management, model configuration, organization theming, and system monitoring, plus enterprise security with SSO, RBAC, and invitation codes. It's everything your team needs for a secure, self-hosted AI experience on Tanzu Platform.
