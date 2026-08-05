# camel-spring-ai test execution

## Unit tests

```bash
mvn test
```

19 tests across the four modules. No Ollama, no Docker.

## Integration tests

The ITs drive a real LLM. `OllamaServiceFactory` uses a local Ollama when
`http://localhost:11434` answers and falls back to a testcontainer otherwise, so a running local
Ollama is picked up automatically. The ITs are skipped when `ci.env.name` is set.

### Recommended setup

No single model covers every IT, so the suite is run in two passes: a small tool-calling model
for the bulk of it, and a small vision model for the multimodal IT.

```bash
ollama pull granite4:3b            # chat and tool calling      2.1 GB
ollama pull embeddinggemma:300m    # embeddings and vector store  621 MB
ollama pull qwen3-vl:2b-instruct   # multimodal                 1.9 GB
ollama pull x/flux2-klein:4b       # image generation           5.7 GB
```

Main pass — everything except the multimodal IT:

```bash
mvn verify -Dollama.model=granite4:3b -Dollama.embedding.model=embeddinggemma:300m
```

Multimodal pass:

```bash
mvn verify -pl camel-spring-ai-chat -Dit.test=SpringAiChatMultimodalIT \
    -Dollama.model=qwen3-vl:2b-instruct
```

Together these are green: 91 chat tests (5 skipped), 7 embeddings, 16 vector store and 4 image.

### Why two models

`granite4:3b` is the `camel-test-infra-ollama` default. It is the smallest model here that passes
every tool-calling IT — `SpringAiChatToolsIntegrationIT`, `SpringAiChatToolContextIT`,
`SpringAiChatToolBeanDiscoveryIT`, `SpringAiChatMcpIT` and `SpringAiChatMcpSseIT` — which is what
most of this component's surface is about.

It has no vision support, so `SpringAiChatMultimodalIT` fails against it with
`Multimodal data provided, but model does not support multimodal requests`. `qwen3-vl:2b-instruct`
covers that in 1.9 GB. Use the `-instruct` tag, not `-thinking`.

Keeping the two roles in separate small models is cheaper than reaching for one large model that
does both: the pair is 4.0 GB combined and each is fast.

### Models pinned inside tests

Some ITs hardcode a model rather than reading a property. They fail with a `404 ... not found`
from Ollama if it has not been pulled:

| Model | Used by |
|---|---|
| `embeddinggemma:300m` | `SpringAiChatVectorStoreIT`, `SpringAiChatVectorMemoryIT`, `QdrantVectorStoreIT` |
| `x/flux2-klein:4b` | `SpringAiImageOllamaIT` |

`ollama.embedding.model` only drives `SpringAiEmbeddingsIT`.

### Extra requirements

- `SpringAiChatMcpIT` needs Node.js and `npx` on the path — it starts the MCP filesystem server
  over stdio.
- `SpringAiChatMcpSseIT` and `QdrantVectorStoreIT` need Docker (testcontainers).
- `SpringAiChatWrappedFileIT` is `@Disabled`: it wants a model that handles images and PDFs.

### Reasoning models

`OllamaTestSupport` calls `OllamaChatOptions.disableThinking()`, which is new in Spring AI 2.0.
Without it a reasoning model spends its whole token budget on the thinking channel and answers
with empty content, so the assertions fail or the run hangs on an unbounded generation. Ollama
accepts `think=false` on models without a thinking mode too, so this is safe for every model —
the granite4:3b result is identical with and without it.

### Other models tried

Measured on `camel-spring-ai-chat` (91 tests, 5 skipped) on Apple Silicon. Ollama is MLX-backed on
Apple Silicon whether or not the tag says `-mlx`, so `granite4:3b` already gets that benefit.

| Model | Size | Time | Result |
|---|---:|---:|---|
| `granite4:3b` | 2.1 GB | 3:13 | recommended; only `SpringAiChatMultimodalIT` fails, for lack of vision |
| `gemma4:e2b-mlx` | 6.5 GB | 2:36 | fastest, and passes the multimodal IT in-process — but 4 failures: `ToolContext` and MCP tool calls come back with empty content |
| `gemma4:12b-mlx` | 7.7 GB | 10:06 | everything it ran passed, including the tool calls `e2b` got wrong and the multimodal IT — but it exceeds the 600s `camel.failsafe.forkTimeout`, so the fork is killed and the build fails |
| `qwen3.5:2b-mlx` | 3.1 GB | — | unusable here: hangs even with thinking disabled |

Gemma 4 is the only family tried that has both vision and tool calling, so it is the one that
could in principle replace the pair above with a single model. Neither size works out: `e2b` is
fast but its tool calling is not reliable enough for these ITs, and `12b` is capable enough but
too slow for the harness — raising `camel.failsafe.forkTimeout` past its 600s default would be a
prerequisite, and even then a 10 minute chat module is a poor trade against granite's 3 minutes.
