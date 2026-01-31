# CF Llama Chat

A modern, enterprise-ready chat application built with Spring Boot and Spring AI, designed for Cloud Foundry and Tanzu Platform deployments. Inspired by [open-webui](https://github.com/open-webui/open-webui).

## Features

### Core Chat
- **Multi-Model Support**: Connect to OpenAI, Ollama, or Tanzu GenAI services
- **Streaming Responses**: Real-time Server-Sent Events (SSE) streaming
- **Conversation History**: Persistent conversation storage with full message history
- **Markdown Rendering**: Full markdown support with syntax highlighting
- **Responsive Design**: Works on desktop and mobile devices

### Document Embeddings & RAG
- **User Document Upload**: Each user can upload documents (PDF, Word, text, etc.)
- **Automatic Chunking**: Documents are split into optimal chunks for embedding
- **Vector Search**: Semantic search across user's document collection
- **RAG Integration**: Optionally include document context in chat responses
- **Per-User Isolation**: Document embeddings are isolated per user

### MCP (Model Context Protocol) Integration
- **MCP Server Management**: Configure and manage MCP servers from the admin portal
- **Transport Types**: Support for SSE and STDIO-based MCP servers
- **Tool Discovery**: Automatically sync tools from connected MCP servers
- **Tool Execution**: Execute MCP tools during chat conversations

### Tools & Skills
- **Tool Management**: View, enable/disable, and configure tools from MCP servers
- **Skill Creation**: Combine tools with custom system prompts into reusable skills
- **User Access Control**: Grant specific tools and skills to individual users

### Organizations & Multi-Tenancy
- **Organization Management**: Create organizations with unique slugs
- **Custom Branding**: Logo, favicon, welcome message, and header text per org
- **Theme Customization**: Full color scheme, fonts, and border radius control
- **Custom CSS**: Inject organization-specific CSS styles
- **Slug-Based Routing**: Access via `/{org-slug}` URLs

### Authentication & Authorization
- **Local Authentication**: Username/password with secure password hashing
- **OAuth2/SSO Integration**: Enterprise SSO support via Cloud Foundry UAA
- **Role-Based Access**: Admin and User roles with permission control
- **Invitation Codes**: Optional invitation requirement for registration
- **User Access Control**: Granular access to tools, MCP servers, and skills

### Admin Portal
- **User Management**: Create, edit, delete users and manage roles
- **Model Overview**: View all available AI models and their status
- **MCP Server Configuration**: Add, connect, and manage MCP servers
- **Tool Management**: View and configure discovered tools
- **Skill Builder**: Create skills combining tools with system prompts
- **Organization Management**: CRUD operations with full theming control
- **S3 Storage Configuration**: Configure object storage for document downloads

### Document Storage (S3)
- **Optional S3 Integration**: Store original documents in S3-compatible storage
- **Admin Configuration**: Configure S3 via admin portal (bucket, credentials, region)
- **S3-Compatible Support**: Works with AWS S3, MinIO, and other compatible services
- **Document Downloads**: Users can download their original uploaded documents

### Usage Metrics
- **Per-User Tracking**: Token usage, response times, throughput
- **Model Statistics**: Performance metrics per model
- **Admin Dashboard**: Global usage overview for administrators

### Cloud Foundry / Tanzu Platform
- **GenAI Service Binding**: Auto-configure from VCAP_SERVICES
- **PostgreSQL Service Binding**: Auto-configure database connection
- **SSO Service Binding**: Auto-configure OAuth2 from SSO service
- **PgVector Support**: Vector store using PostgreSQL with pgvector extension

## Tech Stack

- **Backend**: Spring Boot 3.4, Spring AI 1.1
- **Frontend**: Thymeleaf, Vanilla JavaScript, CSS3
- **Database**: H2 (dev), PostgreSQL with pgvector (production)
- **AI Providers**: OpenAI, Ollama, Tanzu GenAI
- **Vector Store**: PgVector (PostgreSQL)
- **Document Processing**: Apache Tika, PDF Box
- **Object Storage**: AWS S3 SDK (optional)

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- OpenAI API Key or Ollama running locally
- PostgreSQL with pgvector extension (for embeddings in production)

### Local Development

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd cf-llama-chat
   ```

2. Set environment variables:
   ```bash
   export OPENAI_API_KEY=your-api-key
   # Or for Ollama:
   export CHAT_PROVIDER=ollama
   export OLLAMA_BASE_URL=http://localhost:11434
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

4. Open http://localhost:8080 in your browser

### Configuration

#### Core Settings

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | - |
| `OPENAI_MODEL` | OpenAI model to use | gpt-4o-mini |
| `CHAT_PROVIDER` | Default AI provider | openai |
| `OLLAMA_BASE_URL` | Ollama server URL | http://localhost:11434 |
| `OLLAMA_MODEL` | Ollama model to use | llama3.2 |
| `PORT` | Server port | 8080 |

#### Document Embedding Settings

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `EMBEDDING_MODEL` | Embedding model name | text-embedding-3-small |
| `EMBEDDING_DIMENSIONS` | Vector dimensions | 512 |
| `MAX_DOCUMENT_SIZE` | Max upload size (bytes) | 104857600 (100MB) |
| `MAX_DOCUMENTS_PER_USER` | Document limit per user | 50 |
| `DOCUMENT_CHUNK_SIZE` | Chunk size in tokens | 800 |
| `DOCUMENT_CHUNK_OVERLAP` | Chunk overlap in tokens | 100 |
| `RAG_TOP_K` | Top-K results for RAG | 5 |

#### Authentication Settings

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `APP_AUTH_SECRET` | Invitation code for registration | - |
| `APP_REQUIRE_INVITATION` | Require invitation code | false |

## Cloud Foundry Deployment

1. Build the application:
   ```bash
   ./mvnw clean package -DskipTests
   ```

2. Create required services:
   ```bash
   # PostgreSQL with pgvector for data and embeddings
   cf create-service postgresql small chat-db

   # Optional: Tanzu GenAI service for AI models
   cf create-service genai standard chat-genai

   # Optional: SSO service for OAuth2 authentication
   cf create-service p-identity standard chat-sso
   ```

3. Set environment variables:
   ```bash
   cf set-env cf-llama-chat OPENAI_API_KEY your-api-key
   ```

4. Push to Cloud Foundry:
   ```bash
   cf push
   ```

## API Endpoints

### Chat

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/chat` | Send a message (non-streaming) |
| `POST` | `/api/chat/stream` | Send a message (streaming SSE) |
| `GET` | `/api/chat/models` | List available models |
| `GET` | `/api/chat/available-tools` | Get tools available to current user |
| `GET` | `/api/chat/available-skills` | Get skills available to current user |

### Conversations

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/conversations` | List all conversations |
| `GET` | `/api/conversations/{id}` | Get conversation with messages |
| `POST` | `/api/conversations` | Create new conversation |
| `PATCH` | `/api/conversations/{id}` | Update conversation title |
| `DELETE` | `/api/conversations/{id}` | Delete conversation |

### Documents

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/documents/status` | Check embedding service status |
| `POST` | `/api/documents/upload` | Upload document for embedding |
| `GET` | `/api/documents` | List user's documents |
| `GET` | `/api/documents/{id}` | Get document metadata |
| `GET` | `/api/documents/{id}/download` | Download original document |
| `DELETE` | `/api/documents/{id}` | Delete document |
| `GET` | `/api/documents/stats` | Get document statistics |
| `GET` | `/api/documents/search` | Semantic search documents |

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/auth/status` | Get current auth status |
| `GET` | `/auth/provider` | Get auth provider config |
| `POST` | `/auth/register` | Register new user |
| `GET` | `/auth/check-username` | Check username availability |
| `GET` | `/auth/check-email` | Check email availability |

### Organizations & Theming

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/theme` | Get theme for current user's org |
| `GET` | `/api/theme/{slug}` | Get theme by org slug |
| `GET` | `/{slug}` | Organization-specific chat UI |

### Metrics

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/metrics/summary` | Get user's usage summary |
| `GET` | `/api/metrics/global` | Get global metrics (admin) |
| `GET` | `/api/metrics/models` | Get model performance stats |

### Admin APIs

All admin endpoints require ADMIN role.

#### User Management
- `GET /api/admin/users` - List all users
- `POST /api/admin/users` - Create user
- `POST /api/admin/users/{id}/role` - Update user role
- `DELETE /api/admin/users/{id}` - Delete user
- `GET /api/admin/users/{id}/access` - Get user's resource access
- `PUT /api/admin/users/{id}/access` - Update user's resource access

#### MCP Servers
- `GET /api/admin/mcp/servers` - List MCP servers
- `POST /api/admin/mcp/servers` - Create MCP server
- `PUT /api/admin/mcp/servers/{id}` - Update MCP server
- `DELETE /api/admin/mcp/servers/{id}` - Delete MCP server
- `POST /api/admin/mcp/servers/{id}/connect` - Connect to server
- `POST /api/admin/mcp/servers/{id}/sync-tools` - Sync tools from server

#### Tools
- `GET /api/admin/tools` - List all tools
- `PUT /api/admin/tools/{id}` - Update tool
- `PUT /api/admin/tools/{id}/enabled` - Toggle tool enabled

#### Skills
- `GET /api/admin/skills` - List all skills
- `POST /api/admin/skills` - Create skill
- `PUT /api/admin/skills/{id}` - Update skill
- `DELETE /api/admin/skills/{id}` - Delete skill

#### Organizations
- `GET /api/admin/organizations` - List organizations
- `POST /api/admin/organizations` - Create organization
- `PUT /api/admin/organizations/{id}` - Update organization
- `DELETE /api/admin/organizations/{id}` - Delete organization

#### Storage
- `GET /api/admin/storage` - Get S3 configuration
- `POST /api/admin/storage` - Save S3 configuration
- `POST /api/admin/storage/test` - Test S3 connection
- `POST /api/admin/storage/enable` - Enable S3 storage
- `POST /api/admin/storage/disable` - Disable S3 storage

## Project Structure

```
src/main/java/com/example/cfchat/
├── CfLlamaChatApplication.java    # Main application class
├── auth/                          # Authentication services
├── config/                        # Configuration classes
│   ├── GenAiConfig.java          # Tanzu GenAI auto-configuration
│   ├── SecurityConfig.java       # Spring Security configuration
│   └── VectorStoreConfig.java    # Embedding & vector store config
├── controller/                    # REST and web controllers
│   ├── AdminController.java      # Admin portal
│   ├── AdminMcpController.java   # MCP server management
│   ├── AdminSkillsController.java
│   ├── AdminStorageController.java
│   ├── AdminToolsController.java
│   ├── AuthController.java
│   ├── ChatController.java
│   ├── ConversationController.java
│   ├── DocumentController.java
│   ├── MetricsController.java
│   └── OrganizationController.java
├── dto/                          # Data transfer objects
├── model/                        # JPA entities
│   ├── Conversation.java
│   ├── DocumentStorageConfig.java
│   ├── McpServer.java
│   ├── Message.java
│   ├── Organization.java
│   ├── Skill.java
│   ├── Tool.java
│   ├── UsageMetric.java
│   ├── User.java
│   ├── UserAccess.java
│   └── UserDocument.java
├── repository/                   # Spring Data repositories
└── service/                      # Business logic services
    ├── ChatService.java
    ├── ConversationService.java
    ├── DocumentEmbeddingService.java
    ├── DocumentStorageService.java
    ├── McpService.java
    ├── MetricsService.java
    ├── OrganizationService.java
    ├── SkillService.java
    ├── ToolService.java
    └── UserAccessService.java

src/main/resources/
├── application.yml               # Application configuration
├── application-cloud.yml         # Cloud Foundry configuration
├── static/
│   ├── css/style.css            # Main stylesheet
│   └── js/app.js                # Frontend JavaScript
└── templates/
    ├── index.html               # Main chat interface
    ├── admin.html               # Admin dashboard
    └── admin/                   # Admin sub-pages
        ├── users.html
        ├── models.html
        ├── mcp.html
        ├── tools.html
        ├── skills.html
        ├── organizations.html
        └── storage.html
```

## License

MIT License
