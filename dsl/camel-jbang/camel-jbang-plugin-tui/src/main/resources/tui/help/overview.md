# Overview

The Overview tab shows all running Camel integrations at a glance.
Select an integration to monitor it in detail on the other tabs.

## Integration List

Each row represents one running Camel integration:

- **PID** — Process ID of the JVM running this integration
- **NAME** — Name of the integration (from the route file or application configuration). Example: `camel-demo`, `my-app`
- **VERSION** — Camel version the integration is running on (e.g., `4.21.0`)
- **STATUS** — Current lifecycle state: `Running` (processing messages), `Started` (ready), or `Stopped`
- **AGE** — How long the integration has been running (e.g., `2m30s`, `1h15m`)
- **MSG/S** or **MSG/M** — Messages processed per second or per minute (current throughput). The rate unit can be configured in settings. This is the overall rate across all routes
- **TOTAL** — Total number of exchanges (messages) processed since the integration started
- **FAIL** — Number of exchanges that ended with an unhandled error
- **INFLIGHT** — Exchanges currently being processed right now. A consistently high inflight count may indicate slow downstream services
- **SINCE-LAST** — Time since the last exchange activity, shown as up to three values separated by `/`: started/completed/failed. For example, `1s/3s/1m14s` means the last exchange started 1s ago, the last completed 3s ago, and the last failure was 1m14s ago. Values are omitted when there is no activity of that type

## Percentile Latency (P50/P95/P99)

When **Extended** statistics level is enabled, a timing column shows
percentile latencies instead of MIN/MAX/MEAN:

- **P50** — Median processing time (50th percentile). Half of all exchanges completed faster than this
- **P95** — 95th percentile. 95% of exchanges completed faster than this. Useful for SLA monitoring
- **P99** — 99th percentile. Only 1% of exchanges were slower. Highlights worst-case tail latency

Percentiles are computed over a sliding window of recent exchanges, making
them more meaningful than MIN/MAX for understanding real-world performance.
With very few messages (e.g., 10), P95 and P99 may equal the MAX value since
there aren't enough samples to differentiate.

To enable Extended statistics, set `camel.main.load-statistics-enabled = true`
in your application configuration. Without Extended statistics, the column
shows MIN/MAX/MEAN instead.

## Example Screen

```
 PID   NAME         VERSION    STATUS   AGE    MSG/S  TOTAL  FAIL  INFLIGHT  P50/P95/P99  SINCE-LAST
 73136 camel-demo   4.21.0     Running  2m30s  1.00   142    0     0           1/10/31    0s
 64628 my-routes    4.21.0     Running  1h15m  0.50   2850   3     1           2/15/42    2s
```

## Sparkline Chart

The sparkline at the bottom shows message throughput over time.
Each vertical bar represents one sample interval:

- **Green bars** — successful messages per second
- **Red bars** — failed messages per second

This helps you spot traffic patterns, load spikes, and error bursts
at a glance. A sudden drop in throughput may indicate a problem with
an external system. A spike in red bars means errors are occurring.

## Info Panel

When an integration is selected, the right panel shows:

- **Runtime** — Camel runtime type (e.g., `Camel`, `Spring Boot`, `Quarkus`)
- **Profile** — Active profile (`dev` for development, `prod` for production)
- **Reload** — Number of times routes have been live-reloaded (useful in `dev` mode where file changes trigger automatic reload)
- **JVM** — Java version and vendor (e.g., `21.0.5 Azul Systems`)
- **Uptime** — Integration uptime
- **Heap** — JVM heap memory usage (used / committed). See the Memory tab for details
- **Meta** — Metaspace usage (where Java class definitions are stored)
- **Threads** — JVM thread count
- **Load avg** — Three comma-separated load averages over 1-minute, 5-minute, and 15-minute windows. These measure message throughput, not CPU usage — similar concept to Unix load average but for Camel exchanges

## Dev/Infra Services

Dev/Infra Services are backing services (databases, message brokers, etc.)
running in containers via Docker or Podman. This is similar to Quarkus Dev Services
and Spring Boot Development-time Services.

For example, if your integration uses Kafka, you can start a Kafka broker
directly from the TUI using `F2` → `Run Dev/Infra Service...` → select `kafka`.
The service starts in the background and appears in a separate panel below
the integrations list with its own columns:

- **PID** — Container process ID
- **SERVICE** — Service alias (e.g., `kafka`, `postgres`)
- **VERSION** — Service version
- **PORT** — Primary port number
- **STATUS** — Running or Stopped

When running an example that requires infra services, they are started
automatically before the example launches.

Press `Tab` to toggle focus between the integrations and infra panels.
Each panel remembers its own selection. Press `d` while the infra panel
is focused to toggle a details panel showing the service's connection
properties (host, port, etc.).

Sorting applies to whichever panel is focused. Press `s` to cycle through
the sort columns of the focused panel (integration columns: PID, NAME,
VERSION, STATUS, TOTAL, FAIL; infra columns: SERVICE, VERSION, PORT, STATUS).

## Shell and AI Panels

Two panels can be opened on top of any tab. Opening one closes the other.

- `F6` — **Shell**: an embedded Camel JBang shell where you can run any `camel`
  command (`run`, `infra`, `cmd send`, `get`, ...) without leaving the TUI. Press
  `F6` again to close it and `Shift+F6` to cycle its height. `PgUp/PgDn` scrolls
  the output and `Up/Down` recalls earlier commands
- `F8` — **AI Prompt**: ask questions about the running integrations in plain
  English. The AI answers by calling the same tools an MCP agent uses (status,
  routes, log, errors, traces, infra services). It needs an API key in the
  environment or a local Ollama model; `F2` → `AI & MCP` → `Setup AI` explains
  the options. Press `F8` again to close it and `Shift+F8` to cycle its height.
  Inside the panel `Ctrl+U` toggles the AI usage view (tokens per model and
  per question, and how much time went to the model versus tool calls),
  `Ctrl+P` switches provider or model, `Ctrl+Y` copies the last answer
  (just the code when the answer has a code block, with a picker when it
  has several) and `Ctrl+E` exports the conversation. `Ctrl+N` starts a new
  line in the question (terminals send Shift+Enter as plain Enter), pasted
  text keeps its line breaks, and `Up`/`Down` move between the lines before
  they recall earlier prompts. The AI can also edit
  the source files of the selected integration; a dialog asks you to confirm
  each write (`Enter` applies, `d` shows the diff, `Esc` rejects) unless you
  switched to `/write auto`; `/write live` replays the edit in the Source
  editor instead, so you watch it being typed (`Enter` next change, `F4` edit
  yourself, `F9` continue, `F8` ask the AI about the edit, `Esc` stop; then
  `Ctrl+S` saves or `Esc` discards). Type `/help` for the slash commands

Where the panels open (bottom or top) is configured in Settings
(`F2` → `Settings...` → `Panel Position`). The tool calls and answers of the AI panel
are recorded in `F2` → `AI & MCP` → `AI Log`.

## Keys

- `Up/Down` — select within the focused panel
- `Tab` — switch between integrations and infra panels (when infra services are running)
- `d` — toggle infra service details panel (when infra panel is focused)
- `Enter` — view routes for selected integration
- `s` — cycle sort column (for the focused panel)
- `S` — reverse sort order
- `F2` — actions menu (includes theme toggle, go to tab, etc.)
- `F3` — switch integration
- `Ctrl+F` — browse the selected integration's files (plain `f` on the Overview tab)
- `F6` — toggle the embedded shell panel
- `F8` — toggle the AI prompt panel

## Run

- `F10` — open run popup (run, stop routes, start routes, restart, stop, kill)
- `q` — quit the TUI

By default, stop/restart actions show a confirmation dialog before executing.
You can turn this off in Settings (`F2` → `Settings...` → `Confirm`).
