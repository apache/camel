## Test Execution

### Local Ollama (recommended)
The agent tests include tool calling, RAG, structured output, and multimodal inputs. Use Ollama's
OpenAI-compatible endpoint with the current multimodal, tool-capable model and a separate embedding model:

```bash
ollama pull qwen3.5:9b
ollama pull embeddinggemma

mvn verify -Dollama.instance.type=openai \
    -Dopenai.endpoint=http://localhost:11434/v1/ \
    -Dopenai.model=qwen3.5:9b \
    -Dopenai.embedding.model=embeddinggemma \
    -Dopenai.api.key=dummy
```

`openai.embedding.model` is required: the chat-model setting does not configure embeddings, and the
OpenAI-compatible test service otherwise defaults to `text-embedding-ada-002`, which is not supplied by Ollama.
The OpenAI-compatible path is also required for `LangChain4jAgentWrappedFileIT` and
`LangChain4jAgentMultimodalityIT`, because LangChain4j's native Ollama provider cannot send multiple content
blocks in one user message.

### OpenAI
To run against OpenAI, provide both the chat and embedding models:

```bash
mvn verify -Dollama.instance.type=openai \
    -Dopenai.api.key=sk-your-api-key \
    -Dopenai.model=gpt-4o-mini \
    -Dopenai.embedding.model=text-embedding-3-small
```

### Ollama container with NVIDIA GPU
The test-infra service can start Ollama in a container on Linux with NVIDIA acceleration:

```bash
mvn verify -Dollama.container.enable.gpu=enabled
```

```bash
mvn verify -Dollama.instance.type=openai \
    -Dopenai.endpoint=http://localhost:11434/v1/ \
    -Dopenai.model=qwen3-vl:2b-instruct \
    -Dopenai.api.key=dummy
```
