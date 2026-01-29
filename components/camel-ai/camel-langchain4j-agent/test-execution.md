## Test Execution

### macOS or Linux Without an NVIDIA Graphics Card
If Ollama is already installed on the system, execute the tests with:

```bash
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=granite4:3b -Dollama.instance.type=remote
```

The Ollama Docker image is very slow on a MacBook without NVIDIA hardware acceleration.

### Linux With an NVIDIA Graphics Card
Hardware acceleration can be used, and the tests can be executed with:

```bash
mvn verify -Dollama.container.enable.gpu=enabled
```

### OpenAI or OpenAI-Compatible Endpoints
To run tests against OpenAI or any OpenAI-compatible endpoint (including local Ollama via its OpenAI-compatible API):

```bash
# Using real OpenAI
mvn verify -Dollama.instance.type=openai \
    -Dopenai.api.key=sk-your-api-key \
    -Dopenai.model=gpt-4o-mini

# Using local Ollama as OpenAI-compatible endpoint
mvn verify -Dollama.instance.type=openai \
    -Dopenai.endpoint=http://localhost:11434/v1/ \
    -Dopenai.model=granite4:3b \
    -Dopenai.api.key=dummy
```

Note that `LangChain4jAgentWrappedFileIT` and `LangChain4jAgentMultimodalityIT` require multimodal models.

All tests (tools and multimodal) can be executed with an OpenAI instance and the model `qwen3-vl:8b`:

```bash
mvn verify -Dollama.instance.type=openai \
    -Dopenai.endpoint=http://localhost:11434/v1/ \
    -Dopenai.model=qwen3-vl:8b \
    -Dopenai.api.key=dummy
```