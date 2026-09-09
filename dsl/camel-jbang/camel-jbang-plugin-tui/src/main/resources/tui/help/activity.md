# Activity

The Activity tab shows a live feed of recently completed exchanges.
It captures the last N exchanges (default 100) with their message
content, giving you a rolling window of what your integration has
been processing.

Unlike the Inspect tab which traces individual processing steps
within a route, Activity shows one entry per completed exchange
with a summary of the final state.

## Summary Panel

The top panel shows aggregated stats from the visible activity:

- **Total** / **OK** / **Failed** — exchange counts
- **Sends** — total remote endpoint calls across all exchanges
- **p50** / **p95** / **Max** — elapsed time statistics
- **Window** — how far back the oldest and newest entries are

## Activity List

- **EXCHANGE** — Exchange identifier
- **ROUTE** — Route that processed the exchange
- **STATUS** — `OK` (green) or `FAILED` (red)
- **ELAPSED** — Total processing time in milliseconds
- **SENDS** — Number of outbound endpoint calls made during the exchange
- **SINCE** — How long ago the exchange completed (e.g., `5s`, `2m`)
- **ENDPOINT** — The consumer endpoint that received the exchange

## Detail View

Select an exchange to see its details in the panel below:

- **Exchange info**: Exchange ID, route, elapsed time, and failure status
- **Endpoint Sends**: Remote endpoints called during the exchange,
  with individual elapsed times
- **Exception**: If the exchange failed, shows the exception type,
  message, and stack trace

## Keys

- `Up/Down` — select exchange
- `Home/End` — jump to first/last exchange
- `PgUp/PgDn` — scroll the detail panel
- `Left/Right` — horizontal scroll (when wrap is off)
- `Space` — pause/resume data feed (freezes the view for inspection)
- `t` — cycle time filter (all, 1m, 5m, 15m, 30m, 1h)
- `w` — toggle word wrap
- `s` — cycle sort column
- `S` — reverse sort order
