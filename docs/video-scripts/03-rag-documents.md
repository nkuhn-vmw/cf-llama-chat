# CF Llama Chat — RAG: Chat With Your Documents

> Upload documents and YouTube transcripts, then ask the AI questions grounded in your own content using Retrieval-Augmented Generation.

---

## Problem Statement

AI models are powerful but limited to their training data. When you need answers about your company's internal documentation, product specs, or research papers, a general-purpose AI can only guess. Retrieval-Augmented Generation solves this by letting the AI search your actual documents before generating a response — grounding every answer in your real content.

---

## Scene 1: Uploading Documents

Navigate to the Workspace by clicking "Workspace" in the header navigation, then select "Documents" from the dashboard.

### Drag and drop upload

Click the upload area or drag and drop files directly onto it. Supported formats include PDF, Word documents, plain text files, and Markdown files. The upload progress bar shows real-time status as your file is processed.

### Automatic chunking and embedding

Behind the scenes, each uploaded document is automatically split into smaller chunks — optimized segments of roughly 350 tokens each. Each chunk is then converted into a vector embedding and stored in PostgreSQL using the pgvector extension. This vector representation enables semantic search — finding content by meaning, not just keywords.

### YouTube transcript import

Click the "Import YouTube" button and paste a YouTube video URL. CF Llama Chat extracts the full transcript from the video and processes it through the same chunking and embedding pipeline. This is a powerful way to make video content searchable by your AI.

### Viewing your document library

Once processing completes, your document appears in the list with its filename, file size, and chunk count. The chunk count tells you how many searchable segments were created from the document.

---

## Scene 2: Chatting With Your Documents

Return to the main chat interface by clicking the CF Llama Chat logo or navigating to the home page.

### Activating document search

Toggle the "Use My Docs" switch next to the message input. When this switch is on, every message you send triggers a semantic search across your uploaded documents before the AI generates a response.

### Asking questions

Type a question related to your uploaded content and press Enter. For example, if you uploaded a product requirements document, ask "What are the key requirements for the authentication module?" The AI searches your documents, finds the most relevant passages, and generates a response that directly references your content.

### How the RAG pipeline works

When you send a message with document search enabled, four things happen in sequence. First, your question is converted into a vector embedding using the same model that embedded your documents. Second, a similarity search runs against all your document chunks in pgvector, finding the passages most semantically related to your question. Third, the top matching passages are injected into the AI prompt as context. Fourth, the AI generates a response grounded in those specific passages — citing your actual documents rather than relying solely on its training data.

### Combining with regular chat

You can toggle "Use My Docs" on and off at any point in a conversation. When it's off, the AI responds from its general knowledge. When it's on, the AI augments its response with your document content. This flexibility lets you seamlessly switch between general questions and document-specific queries.

---

## Scene 3: Managing Your Document Library

Return to the Workspace Documents page to manage your uploads.

### Document isolation

Documents are isolated per user — your uploads are private and only searchable by you. Other users on the platform cannot see or search your documents, and you cannot see theirs. This ensures data privacy in multi-user deployments.

### Deleting documents

Click the delete button next to any document to remove it and all its vector embeddings from the database. This immediately removes the content from future RAG searches.

### Scaling your knowledge base

Upload as many documents as you need. The more relevant content you provide, the better the AI can answer your questions. Build a comprehensive knowledge base from internal documentation, research papers, meeting transcripts, product specs, and video content.

---

## Summary

RAG transforms CF Llama Chat from a general-purpose AI into a domain expert on your specific content. Upload PDFs, Word docs, text files, or YouTube transcripts — the platform automatically chunks, embeds, and indexes everything in pgvector. Toggle "Use My Docs" in any chat to ground AI responses in your actual documents. Per-user isolation ensures document privacy across the platform.
