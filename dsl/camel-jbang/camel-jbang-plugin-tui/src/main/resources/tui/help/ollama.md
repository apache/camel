# Ollama

The Ollama tab shows how the model served by Ollama is performing, in
the spirit of an LLM dashboard. It finds Ollama at `localhost:11434`,
at the address of `camel infra run ollama`, or at the endpoint the AI
panel (F8) uses, and works with or without a running integration.

Two kinds of requests feed it. Questions asked in the AI panel with
Ollama as the provider arrive with the timings Ollama reports for every
request. Calls made by Camel routes (`camel-langchain4j-chat`,
`camel-openai`, `camel-spring-ai-chat`) arrive through the GenAI
observability spans when the integration runs with `--observe`; Camel
records tokens and duration for those, not the phase split.

## How a Request Is Spent

A local model answers in phases, and every column on this tab maps to
one of them:

1. **Load** — if the model is not in memory yet (first request, or the
   keep-alive expired, or a request asked for a different context
   size), Ollama starts a runner and loads the weights. Seconds to tens
   of seconds. A load of a second or more is shown as a **cold** start.
2. **Prefill** — the prompt (system prompt, tool definitions, history,
   your question) is processed in one batch. Reported as *prefill
   tokens per second*. Prompt tokens already in Ollama's cache from the
   previous turn are skipped; they show as **cached**.
3. **Decode** — the answer is generated one token at a time. Reported
   as *decode tokens per second*; this is the "typing speed" you see.

**TTFT**, time to first token, is load plus prefill: how long you wait
before anything appears. **Total** is the whole request as Ollama
measured it, including its own queueing.

## Header

The loaded model and its shape:

- Family, parameter count and quantization (`Q4_K_M` is 4-bit)
- Layers, and for a mixture-of-experts model the expert count with the
  number active per token (`256 experts (8 active)`)
- Capabilities beyond plain completion: vision, tools, thinking
- Size in memory and the share held by the GPU (`100% GPU` means fully
  offloaded; a lower figure means part of the model runs on the CPU
  and decode will be slow)
- `ctx 32,768 of 262,144` — the context window Ollama allocated for
  this load, and the maximum the model supports. The AI panel asks for
  `32768` unless `OLLAMA_CONTEXT_LENGTH` is set. A request with a
  different size makes Ollama reload the model.
- When the model unloads (its keep-alive)

With no model loaded the installed models are listed instead; a model
loads on the first request.

## Throughput

- **decode** — tokens per second. Marked *live* while the runner on this
  machine is generating (sampled from the runner several times a
  second), otherwise the figure of the *last request*.
- **prefill** — prompt tokens per second, same rule.
- **TTFT** and **load** of the last request, with *cold* or *warm*.
- **session** — average decode rate, total input and output tokens and
  request count since the TUI started or since the last reset.
- The sparkline is the live decode rate over the last 60 seconds when
  the runner is on this machine, otherwise one bar per request.

## Context

- The bar is how full the context window is: prompt plus generated
  tokens against the allocated size. Near 100% the model starts
  forgetting the beginning of the conversation.
- **prompt** and **cache hit** — the prompt size of the current or last
  request and the share of it served from Ollama's cache. A high cache
  hit on the second and later turns of a conversation means the system
  prompt and tool block were reused; zero means every turn pays the
  full prefill again.
- **working** / **idle** — whether the runner is generating right now.
  During a long prompt the prefill progress is shown.
- **speculative** — the speculative decoding method the runner uses
  (`draft-mtp` is multi-token prediction), which is why decode can
  exceed one token per step.
- **turns** — one bar per AI panel turn, oldest first, scaled to the
  whole context window: how much of the window each turn's prompt
  filled (system prompt, tool definitions, history, question). The
  bars grow as a conversation continues and drop when the history is
  compacted; the line ends with the latest fill, the session peak and
  the number of compactions seen. Yellow from 50%, red from 80%: that is
  when `/compact` in the AI panel, or a smaller toolset, pays off.

Without the local runner the panel falls back to the last request's
tokens against the model's context length.

## Host

Shown when Ollama runs on this machine:

- **GPU** — utilization and memory. Apple silicon through `ioreg`
  (unified memory wired by the GPU), NVIDIA through `nvidia-smi`.
- **llama-server** — CPU and resident memory of the model runner
  Ollama started for the loaded model. Resident memory is roughly the
  model size plus the context.
- **ollama serve** — the Ollama server process itself.

## Requests

One line per request, newest first:

| Column | Meaning |
|--------|---------|
| TIME | When the request started |
| SOURCE | `tui` for the AI panel, `route:<id>` for a Camel route |
| MODEL | The model that answered |
| IN | Prompt tokens evaluated |
| OUT | Tokens generated |
| CACHED | Prompt tokens served from Ollama's cache (`-` when none) |
| CTX | Share of the context window the prompt filled (IN plus CACHED against the window that served it) |
| PREFILL | Prompt tokens per second |
| DECODE | Generated tokens per second |
| TTFT | Time to first token (load plus prefill), shown in yellow after a cold start |
| TOTAL | Whole request as Ollama measured it |
| REASON | Why generation stopped: `stop`, `length` (hit the token limit), `tool_calls` |

Route requests show `-` for prefill, decode and TTFT because the GenAI
span carries tokens and duration only.

## Remote and Containerised Ollama

The Ollama API and the per-request data work against any host. The
live figures, the context panel's live state and the host panel need
the runner on this machine; against a remote host or a container the
tab says so and keeps the rest.

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| ↑ / ↓ | Select a request |
| r | Reset the request log and session totals |
| F5 | Refresh now |

The same data is available to AI agents as the `tui_get_ollama` MCP
tool and through `tui_get_table` on this tab.
