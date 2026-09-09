# Metrics

The Metrics tab shows Micrometer metrics collected by the integration.
Micrometer is the standard metrics library for Java — Camel automatically
instruments exchanges, routes, and JVM internals when the `camel-micrometer`
component is present.

These metrics can be exported to monitoring systems like Prometheus,
Grafana, Datadog, or InfluxDB.

## Dashboard Mode (default)

A high-level overview organized into panels showing the most important
metrics at a glance:

**Camel Exchanges:**
- **Total** — Total exchanges processed across all routes
- **Succeeded** — Exchanges that completed without error
- **Failed** — Exchanges that ended with an exception
- **Inflight** — Exchanges currently being processed
- **Ext Reloaded** — External reloads counter (how many times routes were reloaded from file changes or API calls)

**Route Timers:**
Per-route processing time statistics. Shows the mean and max processing
time in milliseconds for each route. A rising mean indicates degrading
performance; a high max with low mean suggests occasional slow outliers.

**Event Notifiers:**
Timing of exchange lifecycle events. Camel fires events at each stage
of an exchange's life (created, sending, sent, completed, failed).
The timers here show how long these lifecycle hooks take — useful for
detecting slow event listeners or interceptors.

**JVM Metrics:**
- **Memory**: Heap used/max/committed, non-heap, buffer pools
- **CPU**: Process CPU usage and system CPU usage as percentages
- **Threads**: Current thread count, peak threads, daemon threads
- **GC**: Per-cause collection count and total pause time

## Example Dashboard

```
 ── Camel Exchanges ─────────────────────
 Total: 1,250    Succeeded: 1,247
 Failed: 3       Inflight: 1
 Ext Reloaded: 2

 ── Route Timers ────────────────────────
 timer-to-log    mean: 0.5ms   max: 12ms
 seda-consumer   mean: 0.1ms   max: 2ms

 ── JVM ─────────────────────────────────
 Heap: 55/80 MB  CPU: 2.1%  Threads: 12
```

## Table Mode

Press `t` to switch to a table showing every individual meter:

- **TYPE** — Meter type:
  - `counter` — Cumulative count that only goes up (e.g., total exchanges)
  - `gauge` — Current value that goes up and down (e.g., memory used)
  - `timer` — Tracks both count and duration (e.g., processing time)
  - `dist` — Distribution summary with percentiles
- **NAME** — Metric name following Micrometer conventions (e.g., `camel.exchanges.total`, `jvm.memory.used`)
- **VALUE** — Current metric value with appropriate formatting
- **TAGS** — Key-value dimensions that identify the metric (e.g., `routeId=myRoute`, `area=heap`). Tags let you filter and group metrics in monitoring dashboards

## Raw Mode

Press `r` to see the Prometheus exposition format — the same text output
you would get from the `/observe/metrics` HTTP endpoint. This is the
exact format Prometheus scrapes, useful for:

- Debugging why a metric does not appear in Grafana
- Verifying metric names and labels match your PromQL queries
- Copying metric definitions for alerting rules

## Common Camel Metrics

- `camel.exchanges.total` — Total exchanges (tagged by routeId)
- `camel.exchanges.failed` — Failed exchanges
- `camel.exchanges.inflight` — Currently processing
- `camel.exchange.processing.time` — Processing duration timer
- `jvm.memory.used` — JVM memory (tagged by area: heap/nonheap)
- `jvm.gc.pause` — GC pause durations

## Keys

- `d` — toggle table mode
- `r` — toggle raw Prometheus mode
- `Up/Down` — navigate (in table or raw mode)
- `s` — cycle sort column (in table mode)
- `S` — reverse sort order
