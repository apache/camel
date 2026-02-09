## Test execution

### MacOS or Linux without nvidia graphic card
If ollama is already installed on the system execute the test with

```bash
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=granite4:3b -Dollama.instance.type=remote
```

The Ollama docker image is really slow on macbook without nvidia hardware acceleration

### Linux with Nvidia graphic card
The hardware acceleration can be used, and the test can be executed with

```bash
mvn verify -Dollama.container.enable.gpu=enabled
```

### Embedding tests

The embedding integration tests require an embedding model. By default, the test infrastructure uses `granite-embedding:30m`.

To run embedding tests with a local Ollama instance:

```bash
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=granite4:3b -Dollama.embedding.model=granite-embedding:30m -Dollama.instance.type=remote
```

Make sure the embedding model is pulled in Ollama before running the tests:

```bash
ollama pull granite-embedding:30m
```

### Running with OpenAI

To run tests against OpenAI API instead of Ollama:

```bash
mvn verify -Dollama.instance.type=openai -Dopenai.api.key=sk-xxx -Dopenai.model=gpt-4o-mini -Dopenai.embedding.model=text-embedding-ada-002
```

Or using environment variables:

```bash
export OPENAI_API_KEY=sk-xxx
export OPENAI_MODEL=gpt-4o-mini
export OPENAI_EMBEDDING_MODEL=text-embedding-ada-002
mvn verify -Dollama.instance.type=openai
```
