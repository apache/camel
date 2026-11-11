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

## Official MCP conformance suite

```bash
mvn verify -Poc
```

Runs `OfficialMcpConformanceIT`, which starts a Camel Main HTTP server with the tool
routes required by each conformance scenario and executes:

```
npx @modelcontextprotocol/conformance@0.1.16 server \
    --url http://localhost:<port>/mcp \
    --expected-failures src/test/resources/expected-failures.yaml
```

To upgrade the pinned version, update the `@modelcontextprotocol/conformance@X.Y.Z` reference
in `buildConformanceCommand()` and re-run the suite to identify new scenarios that need baseline entries.

**Prerequisites:** Node.js ≥ 18 and npm registry access (the `@modelcontextprotocol/conformance`
package is downloaded on the first run and cached by npm).

Known gaps (features not yet implemented by Camel's MCP server) are listed in
`src/test/resources/expected-failures.yaml` with a comment explaining the missing
capability. Remove an entry from that file once the corresponding feature is implemented
— the stale-baseline check will then enforce that the implementation actually passes.

You can also enable the IT without the profile:

```bash
mvn verify -Dofficial-mcp-conformance
```

Or run a single scenario manually against a live server:

```bash
npx @modelcontextprotocol/conformance server \
    --url http://localhost:8080/mcp \
    --scenario tools-call-simple-text
```

