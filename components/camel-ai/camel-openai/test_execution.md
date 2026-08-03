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

### Parallel MCP tool execution tests

`OpenAIMcpParallelToolExecutionIT` covers `parallelToolExecution=true` end to end. The parallel dispatch path is
only reached when the model emits more than one tool call in a single response, since a batch of one is executed
inline. That is the model's decision, not the component's, so the backend matters:

* `qwen3.5:2b-mlx` reliably emits two-call batches for these prompts, and the MLX build is noticeably faster on
  Apple Silicon.
* Smaller or older models frequently serialise the same prompt into one tool call per iteration, in which case the
  tests still pass but only cover the inline path.

```bash
ollama pull qwen3.5:2b-mlx
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=qwen3.5:2b-mlx -Dollama.instance.type=remote \
  -Dit.test=OpenAIMcpParallelToolExecutionIT
```

To confirm the parallel path was actually exercised, raise
`org.apache.camel.component.openai.McpToolCallExecutor` to `DEBUG` and look for
`Executing N tool call(s) in parallel` in `target/camel-openai-tests.log`.

The deterministic proof that a batch is dispatched concurrently is in the unit tests
(`McpToolCallExecutorTest`, `OpenAIParallelToolExecutionTest`), which do not need Ollama at all.

Note that agentic ITs asserting the model called a tool at all can flake with small models, which sometimes answer
directly instead of calling a tool.

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
