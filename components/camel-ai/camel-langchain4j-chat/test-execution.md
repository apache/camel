## Test execution

### Local Ollama (recommended)
Use Ollama's OpenAI-compatible endpoint and the validated Qwen3.5 model:

```bash
ollama pull qwen3.5:9b

mvn verify -Dollama.instance.type=openai \
    -Dopenai.endpoint=http://localhost:11434/v1/ \
    -Dopenai.model=qwen3.5:9b \
    -Dopenai.api.key=dummy
```

The OpenAI-compatible test model disables reasoning mode and uses a zero temperature so that assertions receive
the final assistant content rather than an unfinished reasoning trace.

### OpenAI

```bash
mvn verify -Dollama.instance.type=openai \
    -Dopenai.api.key=sk-your-api-key \
    -Dopenai.model=gpt-4o-mini
```

### Ollama container with NVIDIA GPU

```bash
mvn verify -Dollama.container.enable.gpu=enabled
```

Available OpenAI properties:

- `openai.api.key` - API key (required for OpenAI; use `dummy` for local Ollama)
- `openai.endpoint` - base URL (defaults to `https://api.openai.com/v1/`)
- `openai.model` - chat model (defaults to `gpt-4o-mini`)
