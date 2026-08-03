# camel-mcp-server test execution

## Unit tests

```bash
mvn test
```

Runs the bridge tests and the engine conformance test (`VertxMcpServerConformanceTest`)
against a standalone Vert.x platform HTTP server, driven by the official MCP Java SDK
client. No Docker required.

## Integration tests

```bash
mvn verify
```

- `MainHttpServerMcpConformanceIT` — the conformance kit against the Camel main HTTP
  server (`camel-platform-http-main`), the real Camel Main / JBang serving path.
  No Docker required.
- `McpServerOpenAIAgentIT` — end-to-end agentic loop: the application exposes its own
  `ai-tool` routes over MCP and an LLM (camel-openai) discovers and calls them with
  automatic tool execution. Requires Docker (Ollama testcontainer, model
  `granite4:3b`) or a local Ollama; disabled on CI (`ci.env.name`).

### LLM backend selection (same options as camel-openai)

```bash
# reuse a running Ollama instead of a container
mvn verify -Dollama.instance.type=remote -Dollama.endpoint=http://localhost:11434 -Dollama.model=granite4:3b

# run against the real OpenAI API
mvn verify -Dollama.instance.type=openai -Dopenai.api.key=sk-...

# enable GPU for the Ollama container
mvn verify -Dollama.container.enable.gpu=enabled
```
