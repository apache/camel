# Ollama

The Ollama tab shows how the model served by Ollama is performing, in
the spirit of an LLM dashboard. It finds Ollama at `localhost:11434`,
at the address of `camel infra run ollama`, or at the endpoint the AI
panel (F8) uses, and works with or without a running integration. The
tab is listed in the More menu only while an Ollama server answers; the
TUI checks every ten seconds, so it appears shortly after `ollama serve`
starts and disappears when it stops.

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
   your question) is processed in one batch. Prompt tokens already in
   Ollama's cache from the previous turn or tool step are skipped; they
   show as **cached**. *Prefill tokens per second* counts only the
   tokens that had to be evaluated, so a long prompt with a high cache
   hit still has a short prefill.
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
- `max ctx 256k` on the model line — the largest context the model was
  trained for. `ctx 64k` on the loaded line — the window Ollama
  allocated for this load; it is fixed for the life of the runner, and a
  request for a different size makes Ollama reload the model.
- `AI panel compacts above 32k` — the prompt size above which the TUI's
  AI panel compacts its history, shown once the panel has made a
  request. The panel asks for `OLLAMA_CONTEXT_LENGTH` when set, else the
  window already loaded, else 64k when the model's cache fits the
  machine's memory, else 32k; `AI panel asks ...` appears only when that
  differs from the loaded window.
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
  the number of compactions seen. The colour follows the AI panel's
  compaction point once known (yellow from three quarters of it, red at
  it), else the window (yellow from 50%, red from 80%): that is when
  `/compact` in the AI panel, or a smaller toolset, pays off.

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

One line per question, newest first. A question asked in the AI panel
usually costs several requests, because every tool call the model makes
is answered in a new request with the whole prompt again; the line shows
the question text (cut to fit) and the figures for the question as a
whole. Press **Enter** (or **→**) on a question with `×N` in the SOURCE
column to unfold its steps, **←** to fold them again. A call made by a
Camel route is one line of its own.

The question the AI panel is working on is listed from the moment it is
asked, with `working` and a spinner as its reason and TOTAL counting up;
the figures fill in as its requests return, and the reason becomes
`stop` or `limit` when the answer lands (or the row goes when you cancel
with Esc).

| Column | Question line | Step line |
|--------|---------------|-----------|
| TIME | When the first request started | When the request started |
| SOURCE | `#7 ×10`: question number and request count, or `route:<id>` | `step 3/10` |
| QUESTION | The question, first line cut to fit (the model for a route) | The model that answered |
| IN | The largest prompt of the question: how far the context was pushed | Prompt tokens, the whole prompt the model saw |
| OUT | Tokens generated across all steps | Tokens generated |
| CACHE | Share of all prompt tokens served from Ollama's cache | Share of this prompt served from cache (`-` when none) |
| CTX | Highest share of the context window reached | Share of the context window this prompt filled |
| PREFILL | Evaluated prompt tokens per second across the steps (prompt minus cached over the prefill time) | Same for this request |
| DECODE | Generated tokens per second across the steps | Same for this request |
| TTFT | Time to the first token of the first step (load plus prefill), yellow after a cold start | Time to first token of this request |
| TOTAL | From the first request starting to the last finishing: what you waited | This request as Ollama measured it |
| REASON | Why the last step stopped: `stop`, `length` (hit the token limit), `limit` (the AI panel's tool-call limit ended the question; the answer summarises what was found) | `tool_calls` for every step but the last |

Route requests show `-` for prefill, decode and TTFT because the GenAI
span carries tokens and duration only.

With two or more questions a footer row, `avg/question`, gives the
session's average per question for IN, OUT, TTFT and TOTAL, the pooled
cache hit and rates, the peak CTX, and how many questions ended at the
tool-call limit. Its TOTAL is the average time you waited per question,
the figure the AI panel's usage view (Ctrl+U) reports as well. Route calls
are not questions and are left out.

Two figures are coloured so a costly question stands out without reading
the row: the `×N` request count is yellow from 10 requests and red when
the question ended at the AI panel's tool-call limit (REASON `limit`);
TOTAL is yellow from 30 seconds and orange from a minute.

## Remote and Containerised Ollama

The Ollama API and the per-request data work against any host. The
live figures, the context panel's live state and the host panel need
the runner on this machine; against a remote host or a container the
tab says so and keeps the rest.

## How often the tab reads

While the tab is showing, the runner's slot state is read twice a
second during generation and every two seconds when idle, the Ollama
API once a second and the host probes once a second. With the tab
closed only a version probe every ten seconds keeps the More menu
entry current. Each read shows up in Ollama's own log at the verbosity
it starts the runner with.

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| ↑ / ↓ | Select a question or step |
| Enter / → | Unfold the steps of a question |
| ← | Fold them again |
| r | Reset the request log and session totals |
| F5 | Refresh now |

The same data is available to AI agents as the `tui_get_ollama` MCP
tool and through `tui_get_table` on this tab.
