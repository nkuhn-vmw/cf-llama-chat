# CF Llama Chat — Admin Guide: Managing Users, Models, MCP, Organizations, and System Settings

> A comprehensive walkthrough of the Admin Portal — user management, model configuration, MCP server registration, organization theming, and platform monitoring.

---

## Problem Statement

Deploying an AI platform is just the beginning. Administrators need to manage users and permissions, configure which AI models are available, connect external tool servers, brand the experience for different teams, and monitor system health. CF Llama Chat's Admin Portal provides a centralized dashboard for all of these responsibilities — no SSH, no config files, no redeployments.

---

## Scene 1: The Admin Dashboard

Click "Admin" in the header navigation to access the Admin Portal. Only users with the Admin role can see this link.

### Dashboard overview

The admin dashboard displays platform-wide statistics with card-based metrics: Total Users, Total Administrators, Available Models, Organizations, GenAI Services (on Cloud Foundry), and Active Users in real-time. Each card links to its management page. This is your command center for the entire platform.

---

## Scene 2: User Management

Click "Users" from the admin dashboard.

### Viewing and searching users

The users page lists all registered accounts with their username, role, and status. Browse the list to see who's on the platform.

### Creating new users

Click "Create User" and provide a username and password. The new account is immediately available for login. Use this for onboarding new team members when local authentication is in use.

### Role management

Each user has one of two roles — Admin or User. Click the promote button to elevate a user to administrator, granting them access to the Admin Portal and all management features. Click demote to return an admin to regular user status.

### Password resets

Click "Reset Password" next to any user to set a new password for their account. This is essential for supporting users who are locked out or have forgotten their credentials.

### Deleting users

Click "Delete" to permanently remove a user account. Use this when employees leave the organization or accounts are no longer needed.

### User groups

Click "Groups" from the admin dashboard. Create groups to organize users by department, project team, or any logical grouping. Assign users to groups for easier permission management and bulk operations. Delete groups when they're no longer needed.

---

## Scene 3: Model Configuration

Click "Models" from the admin dashboard.

### Viewing available models

The models page lists all configured AI models with their provider type, name, model ID, status, and description. Models bound through GenAI tile services on Cloud Foundry are automatically discovered — they appear here without any manual configuration.

### Adding external model bindings

Click "Add External Model" to connect an OpenAI-compatible API endpoint. Provide the model display name, the API endpoint URL, and the API key. This is how you integrate models hosted outside your Cloud Foundry environment — a private Ollama server, an Azure OpenAI deployment, or any API that implements the OpenAI chat completions interface.

### Testing model connectivity

Click the test button next to any model to verify the endpoint is reachable and responding correctly. This sends a lightweight probe request and confirms the model is ready before you make it available to users. Always test new external models before announcing them to your team.

---

## Scene 4: MCP Server Management

Click "MCP" from the admin dashboard.

### Registering a new MCP server

Click "Add MCP Server" and fill in the configuration form. Provide a server name for internal reference, a display name that users will see, and select the transport type. Choose SSE for remote HTTP-based MCP servers or STDIO for local process-based servers. Enter the connection details — URL for SSE servers or command and arguments for STDIO servers.

### Auto-discovery on Cloud Foundry

When CF Llama Chat runs on Cloud Foundry with bound MCP services, it automatically discovers and registers MCP servers from the service bindings in VCAP_SERVICES. These auto-discovered servers appear in the list without any manual configuration — the platform reads the connection details directly from the bound service credentials.

### Tool management from servers

Once a server is registered, click into it to see all the tools it exposes. Each tool shows its name, description, and availability status. Use the server filter dropdown to focus on tools from a specific server. Enable or disable individual tools to control which capabilities are available to your users. Use the bulk "Select All" and "Deselect All" buttons to manage all tools from a server at once.

### Deleting MCP servers

Click "Delete" next to a server to remove the registration and all its associated tools from the platform. This immediately removes the tools from all users' available tool lists.

---

## Scene 5: Skills Configuration

Click "Skills" from the admin dashboard.

### Creating a skill

Click "Add Skill" and provide a name and description. Select one or more tools from the available tool list — these are the tools the AI can use when this skill is active. Write a custom system prompt that guides the AI's behavior for this skill. The system prompt is critical — it defines the guardrails and instructions for how the AI should use the assigned tools.

For example, create a "Database Assistant" skill: assign a SQL query tool, and set the system prompt to "You are a database assistant. Always explain your SQL query before executing it. Never run DELETE or DROP statements without explicit user confirmation. Format results as tables."

### Managing skills

Toggle skills on and off to control availability. Edit existing skills to update their tools, prompts, or descriptions. Delete skills that are no longer needed.

---

## Scene 6: Organization Theming and Multi-Tenancy

Click "Organizations" from the admin dashboard.

### Creating an organization

Click "Add Organization" and provide a name and a URL slug. The slug determines the organization's URL path — a slug of "acme" means users access the branded experience at `/acme`. Organization names must be unique across the platform.

### Custom branding

Each organization gets a full suite of visual customization options. Upload a custom logo that replaces the default in the header. Set a custom header text displayed alongside the logo. Choose a primary color, accent color, background color, and sidebar color to match corporate branding. Add a custom favicon for the browser tab. Write a custom welcome message that appears on the chat welcome screen when users in this organization start a new conversation.

### Advanced CSS injection

For branding needs that go beyond color pickers, inject custom CSS directly. This lets you override any visual element — fonts, spacing, borders, animations, or any other CSS property — for pixel-perfect brand alignment.

### Multi-tenant isolation

Users within an organization see only their organization's branding and configuration. This means a single CF Llama Chat deployment can serve multiple teams, departments, or clients — each with their own distinct visual identity — without separate infrastructure for each.

---

## Scene 7: System Administration and Monitoring

The Admin Portal includes several system administration pages for operational management.

### Database monitoring

Click "Database" from the admin dashboard. View PostgreSQL performance metrics in real time — table statistics showing row counts and sizes, index usage percentages, active connection counts, and query performance data. Monitor total database size to plan storage capacity and identify performance bottlenecks.

### System settings

Click "Settings" from the admin dashboard. Configure rate limiting to prevent individual users from overloading the AI model endpoints. Set up content moderation rules to filter inappropriate content. Manage feature flags to enable or disable platform capabilities. Control model access rules to restrict which models are available to specific users or groups.

### Document storage configuration

Click "Storage" from the admin dashboard. Configure S3-compatible object storage for persistent document storage. Provide the S3 endpoint URL, bucket name, and access key credentials. Toggle the storage backend on or off. This is essential for production deployments where document persistence must survive application restarts.

### Notification banners

Click "Banners" from the admin dashboard. Create system-wide announcement banners that appear at the top of every page for all users. Choose a banner type — info for general announcements, warning for important notices, error for critical alerts, or success for positive updates. Schedule banners with start and end dates so announcements appear and disappear automatically. Configure whether users can dismiss the banner or if it stays pinned.

### Webhooks

Click "Webhooks" from the admin dashboard. Configure webhook endpoints to receive real-time notifications when events occur — new messages, user signups, or system events. Test webhook delivery with a single click. View delivery history and error logs to debug failed deliveries and ensure your integrations are working reliably.

---

## Scene 8: Metrics and Usage Analytics

Click "Metrics" in the header navigation.

### Chat token usage

View total token consumption broken down by AI model. See response time tracking to understand how quickly different models respond. Monitor request counts over time to identify usage patterns and peak hours.

### Embedding metrics

Track document processing metrics including total documents uploaded, total chunks created, and processing time. This helps you understand the RAG pipeline's throughput and plan capacity.

### Per-model cost insight

See a detailed breakdown of usage per AI model — tokens consumed, average response times, and request volumes. This data helps you make informed decisions about model provisioning, cost allocation, and whether to add or remove models from your deployment.

---

## Summary

The Admin Portal gives platform administrators complete control over CF Llama Chat without touching config files or redeploying. Manage users and roles, configure AI models with external bindings, register MCP servers and control tool availability, create skills that package tools with guardrails, brand the experience per organization for multi-tenant deployments, monitor database health and system metrics, broadcast announcements with scheduled banners, and integrate with external systems via webhooks. Everything an enterprise AI platform needs — managed from the browser.
