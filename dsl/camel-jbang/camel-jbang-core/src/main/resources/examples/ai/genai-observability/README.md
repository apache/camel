## GenAI Observability (JBang / CLI / TUI)

This example demonstrates **GenAI observability** in Apache Camel 4.23+: OpenTelemetry
`gen_ai.*` span attributes and Micrometer metrics for `langchain4j-chat` LLM calls.

A timer route sends a prompt to Ollama via LangChain4j every 15 seconds. With
`--observe`, Camel exposes health/metrics/tracing and the TUI can show GenAI spans
and token usage.

Blog post: see `docs/blog-drafts/genai-observability-01-jbang-cli-tui.adoc` in the Camel source tree.

### Prerequisites

- [Camel JBang](https://camel.apache.org/manual/camel-jbang-jdk-installation.html) (Camel 4.23+)
- [Ollama](https://ollama.com/) running locally

```sh
ollama pull llama3.2
ollama serve
```

### How to run

From this directory:

```sh
camel run GenAiObservabilityRoute.java application.properties --observe \
  --dependency=camel-langchain4j-chat \
  --dependency=camel-ai-observability \
  --dependency=langchain4j-ollama
```

### Verify GenAI observability

**Metrics** (Prometheus format):

```sh
curl -s http://127.0.0.1:9876/observe/metrics | grep gen_ai
```

Look for `gen_ai_client_operation` and `gen_ai_client_token_usage`.

**TUI — Spans tab**

In another terminal:

```sh
camel tui
```

Select the running integration, open the **Spans** tab, and trigger a timer tick.
Each LLM call creates a child span with `gen_ai.operation.name=chat`,
`gen_ai.request.model`, and token usage attributes.

**TUI — AI usage (Ctrl+U)**

With the AI panel open, press **Ctrl+U** to see combined token usage from
`camel ask` and route-level GenAI spans (Camel 4.23 Phase 2).

**Ask about your integration**

```sh
camel ask "Which routes call an LLM and what model do they use?"
```

### Disable GenAI observability

```properties
camel.ai.observability.enabled=false
```

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
