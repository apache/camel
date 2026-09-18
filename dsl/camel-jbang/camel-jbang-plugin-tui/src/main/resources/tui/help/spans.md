# OTel Spans

The Spans tab shows OpenTelemetry traces captured from the running
integration. Use `--observe` for lightweight Camel-only tracing, or
`--open-telemetry-agent` for full auto-instrumentation (HTTP clients,
JDBC, Kafka clients, etc.) via the OpenTelemetry Java Agent.

## Trace List

The main view shows a table of traces with:

- **TRACE-ID** — Short 8-character trace identifier
- **ROUTE** — The root route that started the trace
- **FROM** — The entry endpoint URI (e.g., timer:orders)
- **SPANS** — Total number of spans in the trace
- **ROUTES** — Number of distinct routes touched
- **REMOTE** — External components used (kafka, http, sql, etc.)
- **STATUS** — OK or ERROR
- **DURATION** — Total trace duration (wall-clock envelope)
- **DEPTH** — Maximum nesting depth of the span tree

## Waterfall View

Press **Enter** on a trace to see the Jaeger-style waterfall showing
the span tree with proportional duration bars. Each span shows its
endpoint URI, processor ID, route context in parentheses, and duration.

Indentation shows the parent-child relationship between spans. Duration
bars are proportional to the trace envelope so you can visually spot
where time is spent. Colors indicate relative duration: green (fast),
yellow (medium), red (slow).

### Span Colors

- **Cyan** — Camel spans (route execution, processors, endpoints)
- **Magenta** — 3rd-party spans from the OTel Java Agent
  (HTTP clients, JDBC, Kafka clients, gRPC, etc.)
- **Red** — Error spans (regardless of source)

The 3rd-party spans are only visible when using `--open-telemetry-agent`.
The detail panel shows the **Source** field for agent-instrumented spans
(e.g., `io.opentelemetry.jdk-http-client`).

Processor spans (setBody, log, etc.) are shown by default. Press **p**
to toggle them off for a cleaner view focused on endpoint-to-endpoint
flow. Error spans are always shown regardless of the toggle.

Press **Esc** to return to the trace list.

Example waterfall for an order-processing integration:

```
 Trace 4bb73039 [15 spans, 4ms]
▸ timer://orders (order-generator)       █████████████████████████████████  2ms
    setBody1 (order-generator)            █  0ms
    direct://process-order (process)      ████████████████  1ms
      direct://validate-order (validate)  █  0ms
        log2 (validate-order)             █  0ms
      log1 (process-order)                           █  0ms
      kafka://orders (order-dispatcher)                 ████████████████  1ms
        log3 (order-dispatcher)                        █  0ms
        multicast1 (order-dispatcher)                  █  0ms
          kafka://fulfillment (fulfill)                           █  0ms
            log4 (fulfillment)                                   █  0ms
            kafka://warehouse (fulfill)                          █  0ms
          kafka://notifications (notif)                          █  0ms
            log5 (notification)                                  █  0ms
            kafka://email-outbox (notif)                         █  0ms
```

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Enter | Drill into trace waterfall |
| Esc | Back to list / clear filter |
| / | Open filter input (matches trace ID, exchange ID, route, component) |
| s | Cycle sort column (trace-id, route, from, spans, routes, status, duration) |
| S | Reverse sort direction |
| p | Toggle processor spans in waterfall |
| c | Toggle camel-only (hide 3rd-party agent spans) |
| F5 | Refresh span data |

## Filtering

Press `/` to open the filter input. Type a search term and press Enter.
Matches against trace ID, exchange ID, route names, and remote component
names. For example, type `kafka` to find traces that use Kafka, or paste
an exchange ID from a log line to find its trace.
