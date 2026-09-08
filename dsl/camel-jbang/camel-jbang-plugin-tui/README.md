# Camel CLI TUI plugin

This module provides `camel tui`, the terminal dashboard for running Camel integrations, including the
F8 AI panel and the embedded MCP server (`camel tui --mcp`).

User documentation lives in the user manual: `docs/user-manual/modules/ROOT/pages/camel-jbang-tui.adoc`.

## Building

```bash
mvn install -DskipTests
```

installs the plugin JAR into the local Maven repository, which is what a locally built `camel` CLI picks up.
Restart `camel tui` afterwards.

## AI panel prompt size and local model performance

The F8 panel sends a static prefix (system prompt plus the schemas of the `tui_*` tools) with every request.
A local model pays for every token of it in prompt-processing time on every question, so its size is
guarded and measured by two tests in `src/test/java/org/apache/camel/dsl/jbang/core/commands/tui`.

### `AiPanelPromptBudgetTest` (always runs)

Builds the real system prompt and tool schemas for the `core` set (sent to local providers) and the
`full` set (sent to hosted providers) and fails when they exceed a budget of estimated tokens. It runs in
milliseconds, needs no model, and prints the breakdown:

```bash
mvn test -Dtest=AiPanelPromptBudgetTest
```

If a change genuinely needs more room, raise the budget in the same commit and explain why in the message.

### `AiPanelOllamaBenchmarkTest` (opt-in)

Sends the same payload to a local Ollama and prints how long the model spends on prompt processing and
generation, cold and warm, in both tool modes. It is skipped unless `CAMEL_TUI_OLLAMA_BENCH` names the
model to use.

Prerequisites:

1. Ollama installed natively (`brew install ollama` on macOS) and running: `ollama serve`
2. The model pulled, for example the recommended one for the TUI: `ollama pull qwen3.6:35b-a3b`

Run:

```bash
CAMEL_TUI_OLLAMA_BENCH=qwen3.6:35b-a3b mvn test -Dtest=AiPanelOllamaBenchmarkTest
```

`OLLAMA_HOST` overrides the server URL (default `http://localhost:11434`). The output looks like this
(Apple M4 Pro, 64 GB):

```
AI panel benchmark against http://localhost:11434 with qwen3.6:35b-a3b
== full: 47 tools ==
  1st (cold prefix)        prompt= 7283 tok  prompt_eval= 10.4s (  701 tok/s)  load= 0.0s  gen= 16 tok in  0.2s  wall= 10.7s  tool=tui_get_state
  2nd (warm prefix)        prompt= 7284 tok  prompt_eval=  1.6s ( 4455 tok/s)  load= 0.0s  gen= 28 tok in  0.4s  wall=  2.1s  tool=tui_get_table
  3rd (switched integr.)   prompt= 7282 tok  prompt_eval=  1.6s ( 4467 tok/s)  load= 0.0s  gen= 16 tok in  0.3s  wall=  1.9s  tool=tui_get_errors
== core: 19 tools ==
  1st (cold prefix)        prompt= 3222 tok  prompt_eval=  4.7s (  684 tok/s)  load= 0.0s  gen= 26 tok in  0.4s  wall=  5.2s  tool=tui_get_state
  2nd (warm prefix)        prompt= 3223 tok  prompt_eval=  1.5s ( 2097 tok/s)  load= 0.0s  gen= 30 tok in  0.5s  wall=  2.0s  tool=tui_get_state
  3rd (switched integr.)   prompt= 3221 tok  prompt_eval=  1.5s ( 2104 tok/s)  load= 0.0s  gen= 16 tok in  0.3s  wall=  1.8s  tool=tui_get_errors
```

How to read it:

- `prompt_eval` on the 1st request is the cost of processing the whole prefix; on the 2nd and 3rd it
  should drop to the new tokens only, which shows the Ollama prompt cache is being reused. If the 2nd
  request costs as much as the 1st, something in the prefix changes between requests, or the engine
  cannot reuse the cache (the Ollama MLX engine cannot for Qwen 3.x models; use the default GGUF tags).
- The 3rd request switches the monitored integration; it must stay warm because that information travels
  in the user message, not in the system prompt.
- `tool=` shows which tool the model chose for the question, a quick sanity check that trimmed
  descriptions did not hurt tool selection (`tui_get_state` for "what model is this",
  `tui_get_table` for "what routes are running?", `tui_get_errors` for "any errors?").
- Dense models (27B, 32B) process prompts at roughly 110 tokens per second on an M4 Pro, so the 1st
  request takes about a minute. Mixture-of-experts models such as `qwen3.6:35b-a3b` are several times
  faster, which is why the TUI recommends them.

The same figures are available live inside the TUI: `/context` in the F8 panel shows the prefix and
history size of the next request, and `/usage` the tokens and latency spent so far.
