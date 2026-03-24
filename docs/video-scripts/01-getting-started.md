# CF Llama Chat — Getting Started: Deploy and First Chat

> Learn how to deploy CF Llama Chat to Cloud Foundry and have your first AI conversation in minutes.

---

## Problem Statement

Enterprise teams need a secure, self-hosted AI chat platform that runs on their existing Cloud Foundry or Tanzu Platform infrastructure. Off-the-shelf SaaS tools like ChatGPT don't meet compliance requirements, lack integration with internal services, and offer no control over model selection or data residency. CF Llama Chat solves this by deploying with a single `cf push` and binding directly to Tanzu GenAI tile services — zero custom infrastructure required.

---

## Scene 1: Building and Deploying

CF Llama Chat ships as a single Spring Boot JAR. The entire deployment workflow takes just two commands.

### Step 1 — Build the application

Open a terminal in the project root and run the Maven build:

```bash
mvn clean package -DskipTests -q
```

This produces a deployable JAR file in the `target/` directory.

### Step 2 — Review the manifest

The `manifest.yml` file defines the Cloud Foundry deployment configuration including memory allocation, buildpack, bound services, and environment variables. It binds to GenAI tile services for model access and a PostgreSQL database for persistence and vector storage.

### Step 3 — Push to Cloud Foundry

Deploy with a single command:

```bash
cf push
```

Cloud Foundry detects the Java buildpack, stages the application, binds the configured services, and starts the app. The application automatically reads model credentials from `VCAP_SERVICES` environment variables — no manual API key configuration required.

### Step 4 — Access the application

Once the push completes, open the application URL shown in the terminal output. You'll see the CF Llama Chat login page. Sign in with the default admin credentials or your SSO provider.

---

## Scene 2: Your First Chat Conversation

The main chat interface is where users spend most of their time. It provides a modern, responsive conversation experience with real-time streaming.

### Starting a new conversation

Click the "New Chat" button in the sidebar. The welcome screen displays with quick-action example prompts to help you get started. Click any example prompt or type your own question in the message input area at the bottom.

### Selecting an AI model

Use the model selector dropdown at the top of the chat area to choose which AI model powers your conversation. CF Llama Chat supports multiple providers simultaneously — OpenAI models, Ollama local models, Tanzu GenAI tile models, and external API-compatible models. Switch models mid-conversation at any time.

### Streaming responses

Toggle the "Stream" switch next to the message input to control response delivery. When streaming is enabled, you see the AI's response appear token by token in real time via Server-Sent Events. When disabled, the full response loads at once. Streaming provides a more interactive experience for longer responses.

### Rich Markdown output

Type your message and press Enter to send, or Shift+Enter for a new line. The AI responds with fully rendered Markdown including syntax-highlighted code blocks, tables, math equations via KaTeX, and formatted lists.

### Temporary chats

Toggle the "Temporary" switch to start a conversation that won't be saved to your history. This is useful for quick one-off questions you don't need to reference later.

---

## Scene 3: Organizing Your Conversations

CF Llama Chat provides rich conversation organization features to help you manage and find past chats.

### Searching conversations

Use the search bar at the top of the sidebar to filter your conversation list by keyword. Results update in real time as you type.

### Tabs and favorites

The sidebar offers three tabs: "All" shows every conversation, "Favorites" shows pinned conversations, and "Projects" shows conversations organized into folders. Right-click any conversation to pin it as a favorite — pinned conversations appear with a star icon for quick access.

### Tags and projects

Add tags to conversations to categorize them by topic or project. Create project folders to group related conversations together — right-click a conversation and select "Move to folder" to organize it. Access all project folders from the Projects tab.

### Renaming and cleanup

Right-click any conversation to rename it with a custom title, or delete it when no longer needed. Use the "Clear All History" button at the bottom of the sidebar to remove all conversations at once.

---

## Summary

CF Llama Chat deploys to Cloud Foundry in minutes with `mvn clean package` and `cf push`. Once running, you get a full-featured AI chat interface with multi-model selection, real-time streaming, rich Markdown rendering, and conversation management with search, favorites, tags, and project folders. Next, explore the Workspace features to unlock document-powered RAG, channels, notes, and more.
