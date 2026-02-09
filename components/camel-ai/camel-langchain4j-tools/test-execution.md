## Test execution

### MacOS or Linux without nvidia graphic card
If ollama is already installed on the system execute the test with

```bash
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=granite4:tiny-h -Dollama.instance.type=remote
```

The Ollama docker image is really slow on macbook without nvidia hardware acceleration

### Linux with Nvidia graphic card
The hardware acceleration can be used, and the test can be executed with

```bash
mvn verify -Dollama.container.enable.gpu=enabled
```

### OpenAI or OpenAI-compatible endpoints
To run tests against OpenAI or any OpenAI-compatible endpoint (including local Ollama via its OpenAI-compatible API):

```bash
# Using real OpenAI
mvn verify -Dollama.instance.type=openai \
    -Dopenai.api.key=sk-your-api-key \
    -Dopenai.model=gpt-4o-mini

# Using local Ollama as OpenAI-compatible endpoint
mvn verify -Dollama.instance.type=openai \
    -Dopenai.endpoint=http://localhost:11434/v1/ \
    -Dopenai.model=granite4:tiny-h \
    -Dopenai.api.key=dummy
```

Available OpenAI properties:
- `openai.api.key` - API key (required for real OpenAI, use "dummy" for Ollama)
- `openai.endpoint` - Base URL (defaults to `https://api.openai.com/v1/`)
- `openai.model` - Model name (defaults to `gpt-4o-mini`)