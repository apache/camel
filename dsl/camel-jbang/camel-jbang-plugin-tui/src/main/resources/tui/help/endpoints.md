# Endpoints

Endpoints are the addresses that messages flow to and from. Every Camel
component (kafka, http, file, log, etc.) provides endpoints that routes
use to receive and send data. An endpoint URI like `kafka://my-topic`
identifies both the component and the specific resource.

## Table Columns

- **COMPONENT** — The Camel component name (e.g., `kafka`, `http`, `file`, `log`, `seda`). This is the scheme part of the endpoint URI
- **ROUTE** — Which route uses this endpoint
- **DIR** — Direction of message flow: `in` (consuming/receiving from this endpoint), `out` (producing/sending to this endpoint), or `both` (used in both directions)
- **TOTAL** — Hit count: how many times a message has passed through this endpoint in the given direction
- **BODY** — Average message body size in bytes passing through this endpoint. Helps identify which endpoints handle large payloads
- **HDR** — Average message headers size in bytes. Large headers may indicate excessive metadata being passed
- **STUB** — Marked `x` if the endpoint has been replaced by a stub component (useful during testing to avoid connecting to real external systems)
- **REMOTE** — Marked `x` if the endpoint connects to an external system outside the JVM (e.g., `kafka`, `http`, `ftp`). Local endpoints like `seda`, `direct`, `log` are not remote
- **URI** — Full endpoint URI with parameters (e.g., `timer://hello?period=2000`)

## Example Screen

```
 COMPONENT  ROUTE          DIR  TOTAL  BODY  HDR  STUB  REMOTE  URI
 timer      timer-to-log   in   21     0     0                  timer://hello?period=2000
 timer      timer-to-seda  in   14     0     0                  timer://pump?period=3000
 seda       seda-consumer  in   14     14    0                  seda://queue
 seda       timer-to-seda  out  14     14    0                  seda://queue
```

Notice that `seda://queue` appears twice — once as `in` (the
`seda-consumer` route reads from it) and once as `out` (the
`timer-to-seda` route writes to it). The BODY column shows 14
bytes for seda because the message `Pumped message` is 14 bytes.

## Flow Chart

The top-left panel shows a visual flow of messages through the
integration:

```
┌─────────┐                      ┌──────────┐
│  kafka  │ ──▸ [integration] ──▸ │   http   │
│  file   │                      │   log    │
└─────────┘                      └──────────┘
 inbound                          outbound
```

The left box lists all inbound components (consumers), the right
box lists all outbound components (producers). The integration
name sits in the middle, showing the overall message flow direction.

## Throughput Chart

A mirrored sparkline chart shows message rates over time:

- **Top half (green)**: inbound messages per second
- **Bottom half (cyan)**: outbound messages per second

This helps you see if input and output rates are balanced. If
inbound consistently exceeds outbound, messages may be queuing up.

## Payload Size Chart

When message size tracking is available, a separate chart shows
body sizes over time. Sudden increases in message size may indicate
unexpected large payloads that could cause memory pressure.

## Filter Modes

Use `f` to cycle between filter modes:
- **all** — show all endpoints
- **remote** — show only remote endpoints (external connections)
- **stub** — show only stubbed endpoints

## Endpoint Documentation

Press `d` to toggle the documentation panel. When enabled, the
bottom panel shows detailed documentation for the selected endpoint's
configured options — parsed from the endpoint URI and looked up in
the Camel catalog matching the integration's Camel version.

For each configured option you'll see:
- **Name** and **current value** (highlighted)
- **Description** — what the option does
- **Type** — the expected value type
- **Default** — the default value if not set
- **Enum values** — valid choices for enum options
- **Group** — whether it's a common, consumer, producer, or advanced option

Use `PgUp/PgDn` to scroll the documentation panel.

## Keys

- `Up/Down` — select endpoint
- `s` — cycle sort column
- `S` — reverse sort order
- `f` — cycle filter mode
- `d` — toggle endpoint documentation panel
- `PgUp/PgDn` — scroll documentation (when doc panel is open)
