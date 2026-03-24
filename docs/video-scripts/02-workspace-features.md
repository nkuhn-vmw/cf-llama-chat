# CF Llama Chat — Workspace Features: Channels, Notes, Memory, and Prompts

> Explore the Workspace Hub — your personal productivity center for team collaboration, note-taking, AI memory, and reusable prompt templates.

---

## Problem Statement

AI chat is powerful on its own, but enterprise teams need more than just conversations. They need real-time team channels, a place to capture notes and research, persistent AI memory that carries context across sessions, and reusable prompt templates to standardize workflows. CF Llama Chat's Workspace Hub brings all of these together in one place.

---

## Scene 1: The Workspace Dashboard

Access the Workspace Hub by clicking "Workspace" in the header navigation.

### Dashboard overview

The Workspace dashboard displays card-based statistics showing your total Channels, Notes, Memories, Prompts, Documents, and Tools. Each card shows a count and links directly to its management page. This is your home base for all productivity features beyond chat.

---

## Scene 2: Channels — Real-Time Group Messaging

Click "Channels" from the Workspace dashboard.

### Creating a channel

Click the "Create Channel" button and provide a name and description. Channels are visible to all users on the platform for team-wide collaboration.

### Real-time messaging

Click into a channel to view its message history. Type a message in the input field and press Send. Messages stream in real time via Server-Sent Events — you see new messages from other users instantly without refreshing the page. This makes channels ideal for team discussions, incident response coordination, or collaborative brainstorming.

### Managing channels

Channel creators and administrators can delete channels when they're no longer needed. Use the server filter to organize and find channels in larger deployments.

---

## Scene 3: Notes — Your Personal Knowledge Base

Click "Notes" from the Workspace dashboard.

### Creating a note

Click "Create Note" and provide a title. Write your content using rich Markdown syntax — headers, lists, code blocks, links, and more.

### Markdown preview

Toggle the Markdown preview button to see your rendered output side-by-side as you write. This lets you verify formatting before saving.

### Managing notes

Your notes list shows all entries with their last updated date. Click any note to open it for editing. Delete notes you no longer need. Notes are private to your account — they're your personal knowledge base within the platform for meeting notes, code snippets, research findings, or anything you want to reference later.

---

## Scene 4: Memory — Persistent AI Context

Click "Memory" from the Workspace dashboard.

### Adding memories

Click "Add Memory" to create a new entry. Select a category — General for broad context, Preference for how you like things done, Fact for specific information, or Instruction for behavioral directives.

For example, add a Preference memory: "I work on the payments team and prefer Python code examples." Or add an Instruction memory: "Always include error handling in code examples." The AI will reference these memories across all your future conversations without you having to repeat yourself.

### Searching and filtering

Use the search bar to find memories by content. Use the category filter to view only General, Preference, Fact, or Instruction memories. The stats section shows how many memories you have in each category.

### Managing memories

Delete memories when they become outdated or irrelevant. Keep your memory collection current so the AI always has accurate context about your preferences and needs.

---

## Scene 5: Prompt Presets — Reusable Templates

Click "Prompts" from the Workspace dashboard.

### Creating a prompt template

Click "Create Prompt" and provide a name, description, and the prompt content. Use double curly brace syntax for placeholder variables — for example, `{{topic}}` or `{{language}}`. When you use the prompt, you'll be asked to fill in these variables.

### Slash commands

Assign a slash command to any prompt for quick access in chat. For example, create a prompt named "Summarize" with the command `/summarize`. Then in any chat conversation, type `/summarize` to instantly expand the full prompt template.

### Shared prompts

Mark a prompt as "Shared" to make it available to all users on the platform. This is great for standardizing team workflows — create prompts for code review, bug reporting, documentation writing, or any repeatable task.

### Managing prompts

Edit existing prompts to refine their content or update variables. Delete prompts that are no longer needed. Your prompt library grows over time into a powerful collection of reusable AI workflows.

---

## Summary

The Workspace Hub transforms CF Llama Chat from a simple AI chatbot into a full productivity platform. Channels enable real-time team collaboration. Notes provide a personal knowledge base. Memory gives the AI persistent context about your preferences and work. Prompt Presets let you create and share reusable templates with slash commands. Together, these features make your AI experience more personalized, collaborative, and efficient.
