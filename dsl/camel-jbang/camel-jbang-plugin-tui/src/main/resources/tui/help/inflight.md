# Inflight

The Inflight tab shows exchanges that are currently being processed or
are blocked waiting for a response. This is your real-time view into
what the integration is doing right now — which messages are in flight
and how long they have been processing.

This tab requires `--inflight-browse=true` when starting the integration.
Without it, Camel does not track individual inflight exchanges.

## Table Columns

- **STATUS** — Exchange state: `inflight` (green, actively processing) or `blocked` (red, waiting for a resource like a lock or a slow downstream service)
- **EXCHANGE ID** — Unique identifier for this exchange (e.g., `ID-myhost-1234-5`)
- **ROUTE/NODE** — Which route and specific processor node is currently handling the exchange. Format: `routeId/nodeId` (e.g., `my-route/to1`). This tells you exactly where in the route the message is right now
- **DURATION** — Time elapsed since processing started (e.g., `1s`, `5s`, `1m30s`). Long durations may indicate a slow downstream service or a deadlock
- **ELAPSED** — Visual duration bar showing relative processing time. Longer bars mean longer processing. Color indicates severity

## Example Screen

```
 STATUS    EXCHANGE ID          ROUTE/NODE          DURATION    ELAPSED
 inflight  ID-myhost-1234-10    my-route/to1        234ms       ██
 inflight  ID-myhost-1234-9     my-route/process2   1s          ████████
 blocked   ID-myhost-1234-7     api-route/to3       12s         ████████████████████
```

The third exchange has been blocked for 12 seconds — it is likely
waiting for a response from a slow external service.

## Duration Colors

- **Green**: less than 1 second — normal processing time
- **Yellow**: 1-10 seconds — getting slow, worth monitoring
- **Red**: over 10 seconds — potentially stuck or waiting on an unresponsive service

## When To Use

- **Diagnosing slow processing**: If messages are taking longer than expected, this tab shows exactly which route and node they are stuck at
- **Detecting blocked exchanges**: Blocked exchanges are waiting for a resource (e.g., a database connection, a file lock, a thread pool). Multiple blocked exchanges at the same node may indicate contention
- **Monitoring concurrent load**: The number of inflight exchanges shows how much concurrent work the integration is handling
- **Identifying stuck messages**: Exchanges that stay inflight for minutes are likely stuck — the ROUTE/NODE column tells you where to investigate

## Keys

- `Up/Down` — select exchange
- `s` — cycle sort column
- `S` — reverse sort order
- `Esc` — back
