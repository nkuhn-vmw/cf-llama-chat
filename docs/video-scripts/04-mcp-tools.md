# CF Llama Chat — MCP Tools and Skills: Extending AI With External Actions

> Connect external tools via the Model Context Protocol so the AI can take real-world actions — query databases, call APIs, and execute workflows — right from the chat interface.

---

## Problem Statement

Text generation alone isn't enough for enterprise workflows. Teams need the AI to interact with external systems — query a database, check a monitoring dashboard, file a ticket, or call an internal API. The Model Context Protocol provides a standardized way to give AI models access to external tools, and CF Llama Chat integrates MCP natively so your AI can take action, not just talk.

---

## Scene 1: Using Tools in Chat

From the main chat interface, look at the toggles next to the message input area.

### Enabling tools

Toggle the "Use Tools" switch to enable tool usage for your conversation. When enabled, the AI can automatically invoke any registered and enabled tools based on the context of your question.

### How tool invocation works

When you ask a question that could benefit from external data or actions, the AI automatically decides which tool to call, constructs the appropriate parameters, executes the tool, receives the result, and incorporates that information into its response. You don't need to explicitly tell the AI to use a tool — it reasons about when tools are helpful based on your question.

### Example interaction

If a weather API tool is registered, asking "What's the weather in San Francisco?" triggers the AI to call the weather tool with the location parameter, receive current weather data, and present it in a natural language response. The same pattern works for any registered tool — database queries, API lookups, file operations, or custom business logic.

---

## Scene 2: Browsing and Configuring Tools

Navigate to the Workspace and select "Tools" from the dashboard.

### Tool inventory

The Tools page displays all available tools organized by their source MCP server. Each tool shows its name, a description of what it does, and a type badge indicating whether it's an MCP tool or a custom tool. Statistics at the top show total tools, enabled tools, MCP tools, and custom tools.

### Server filtering

Use the server filter dropdown to view tools from a specific MCP server. This is useful when multiple servers are registered and you want to focus on tools from a particular integration.

### Toggling individual tools

Click the toggle switch next to any tool to enable or disable it for your conversations. Disabled tools won't be available to the AI even when the "Use Tools" switch is on in chat. This gives you fine-grained control over which capabilities the AI can access.

### Bulk operations

Use the "Select All" and "Deselect All" buttons to enable or disable all tools from a particular server at once. Your tool preferences are saved locally in your browser and persist across sessions.

---

## Scene 3: Skills — Tools Plus System Prompts

Skills combine specific tools with custom system prompts to create focused, purpose-built AI capabilities.

### What is a skill

A skill packages one or more tools together with a system prompt that guides the AI's behavior. For example, a "Database Assistant" skill might combine a SQL query tool with a system prompt that instructs the AI to always explain queries before executing them and to never run destructive operations without confirmation.

### Using skills in chat

When skills are available, you can select them from the chat interface. Activating a skill configures the AI with the associated system prompt and makes only the skill's assigned tools available — creating a focused, safe interaction pattern for specific use cases.

### Skill examples

Consider these practical skills: a "Code Reviewer" skill that combines a Git diff tool with a prompt focused on security and performance review. A "Report Generator" skill that combines database query and chart generation tools with a prompt for structured business reporting. An "Incident Responder" skill that combines monitoring, logging, and ticketing tools with a prompt for structured incident triage.

---

## Scene 4: How MCP Servers Connect (For Administrators)

This section provides context on how tools get registered — covered in more detail in the Admin Guide video.

### Transport types

MCP servers connect via two transport types. SSE transport is for remote HTTP servers — the application makes HTTP requests to the MCP server endpoint. STDIO transport is for local process-based servers — the application spawns and communicates with a local process.

### Auto-discovery on Cloud Foundry

When running on Cloud Foundry with bound MCP services, CF Llama Chat automatically discovers and registers MCP servers from service bindings. This means platform-managed MCP services are available to users with zero manual configuration.

### Manual registration

Administrators can also manually register MCP servers through the Admin Portal, providing the server name, transport type, and connection details. Once registered, the server's tools are automatically discovered and made available for users to enable.

---

## Summary

MCP integration transforms CF Llama Chat from a conversational AI into an action-oriented platform. Toggle "Use Tools" in chat to let the AI call external services automatically. Browse and configure individual tools from the Workspace. Skills combine tools with system prompts for focused, safe AI interactions. Auto-discovery on Cloud Foundry means MCP services work out of the box with zero configuration.
