# CF Llama Chat

A modern chat application built with Spring Boot and Spring AI, designed to run on Cloud Foundry. Inspired by [open-webui](https://github.com/open-webui/open-webui).

## Features

- **Multi-Model Support**: Connect to OpenAI or Ollama models
- **Streaming Responses**: Real-time streaming of AI responses
- **Conversation History**: Persistent conversation storage
- **Markdown Rendering**: Full markdown support with syntax highlighting
- **Responsive Design**: Works on desktop and mobile devices
- **Cloud Foundry Ready**: Includes manifest for easy CF deployment

## Tech Stack

- **Backend**: Spring Boot 3.3, Spring AI 1.0
- **Frontend**: Thymeleaf, Vanilla JavaScript, CSS3
- **Database**: H2 (dev), PostgreSQL (production)
- **AI Providers**: OpenAI, Ollama

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- OpenAI API Key or Ollama running locally

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

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | - |
| `OPENAI_MODEL` | OpenAI model to use | gpt-4o-mini |
| `CHAT_PROVIDER` | Default AI provider | openai |
| `OLLAMA_BASE_URL` | Ollama server URL | http://localhost:11434 |
| `OLLAMA_MODEL` | Ollama model to use | llama3.2 |
| `PORT` | Server port | 8080 |

## Cloud Foundry Deployment

1. Build the application:
   ```bash
   ./mvnw clean package -DskipTests
   ```

2. Create a PostgreSQL service:
   ```bash
   cf create-service postgresql small chat-db
   ```

3. Set the OpenAI API key:
   ```bash
   cf set-env cf-llama-chat OPENAI_API_KEY your-api-key
   ```

4. Push to Cloud Foundry:
   ```bash
   cf push
   ```

## API Endpoints

### Chat

- `POST /api/chat` - Send a message (non-streaming)
- `POST /api/chat/stream` - Send a message (streaming)
- `GET /api/chat/models` - List available models

### Conversations

- `GET /api/conversations` - List all conversations
- `GET /api/conversations/{id}` - Get a conversation with messages
- `POST /api/conversations` - Create a new conversation
- `PATCH /api/conversations/{id}` - Update conversation title
- `DELETE /api/conversations/{id}` - Delete a conversation

## Project Structure

```
src/main/java/com/example/cfchat/
├── CfLlamaChatApplication.java    # Main application class
├── config/                        # Configuration classes
├── controller/                    # REST and web controllers
├── dto/                          # Data transfer objects
├── model/                        # JPA entities
├── repository/                   # Spring Data repositories
└── service/                      # Business logic services

src/main/resources/
├── application.yml               # Application configuration
├── application-cloud.yml         # Cloud Foundry configuration
├── static/                       # Static assets (CSS, JS)
└── templates/                    # Thymeleaf templates
```

## License

MIT License
