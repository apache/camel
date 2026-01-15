# Camel LangChain4j Spring Boot Integration Example

This example demonstrates how to integrate LangChain4j Spring Boot starters with Apache Camel Spring Boot applications.

## Overview

This example shows:
- Using LangChain4j Spring Boot starters for auto-configuration
- Integrating auto-configured LLM models with Camel's langchain4j components
- Profile-based configuration (dev with Ollama, prod with OpenAI)
- REST API for chat interactions
- Best practices for Spring Boot + Camel + LangChain4j integration

## Prerequisites

### For Development Profile (Ollama)
- Java 21 or later
- Maven 3.9 or later
- [Ollama](https://ollama.ai/) installed and running locally
- Ollama models downloaded:
  ```bash
  ollama pull llama2
  ollama pull nomic-embed-text
  ```

### For Production Profile (OpenAI)
- Java 21 or later
- Maven 3.9 or later
- OpenAI API key (set as environment variable `OPENAI_API_KEY`)

## Running the Example

### Using Ollama (Development Profile)

1. Start Ollama:
   ```bash
   ollama serve
   ```

2. Run the application:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

### Using OpenAI (Production Profile)

1. Set your OpenAI API key:
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```

2. Run the application:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

## Testing the Application

### Using Direct Endpoints

You can test the chat functionality using Camel's JMX console or by creating a simple test:

```bash
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Apache Camel?"}'
```

### Expected Response

```json
{
  "response": "Apache Camel is an open-source integration framework..."
}
```

## Configuration

### Application Properties

The application uses `application.yml` for configuration with profile-specific settings:

- **dev profile**: Uses Ollama for local testing
- **prod profile**: Uses OpenAI for production

### Key Configuration Properties

#### OpenAI Configuration
```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o
      temperature: 0.7
```

#### Ollama Configuration
```yaml
langchain4j:
  ollama:
    chat-model:
      base-url: http://localhost:11434
      model-name: llama2
      temperature: 0.8
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── org/apache/camel/example/langchain4j/
│   │       ├── Application.java          # Spring Boot main class
│   │       └── ChatRoute.java            # Camel routes
│   └── resources/
│       └── application.yml               # Configuration
└── test/
    └── java/
        └── org/apache/camel/example/langchain4j/
            └── ChatRouteTest.java        # Integration tests
```

## Key Features

### Auto-Configuration

The LangChain4j Spring Boot starter automatically configures:
- `ChatLanguageModel` bean for chat interactions
- `EmbeddingModel` bean for embeddings
- Connection pooling and retry logic
- Health checks and metrics

### Camel Integration

Camel routes use the auto-configured beans:
```java
from("direct:chat")
    .to("langchain4j-chat:openai?chatModel=#chatLanguageModel");
```

### Profile-Based Configuration

Switch between providers using Spring profiles:
- `dev`: Local Ollama for development
- `prod`: OpenAI for production

## Monitoring

Access Spring Boot Actuator endpoints:
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Prometheus: http://localhost:8080/actuator/prometheus

## Troubleshooting

### Ollama Connection Issues

If you get connection errors with Ollama:
1. Ensure Ollama is running: `ollama serve`
2. Verify the base URL in application.yml
3. Check that the model is downloaded: `ollama list`

### OpenAI API Issues

If you get OpenAI API errors:
1. Verify your API key is set correctly
2. Check your API key has sufficient credits
3. Ensure you're using a valid model name

## Additional Resources

- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Camel LangChain4j Components](https://camel.apache.org/components/latest/langchain4j-chat-component.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

