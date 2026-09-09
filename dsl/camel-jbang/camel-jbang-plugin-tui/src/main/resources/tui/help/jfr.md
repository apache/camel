# JFR

The JFR tab shows the live status of camel-jfr's runtime instrumentation
and lets you view aggregated runtime data from JFR recordings.

## Data Views

Press **F5** to take a snapshot of the active JFR recording. The snapshot
data is aggregated into five views:

- **Routes** — per-route exchange count, failures, and timing with
  a processor panel below showing processors for the selected route
- **Processors** — per-processor invocation count and timing across all routes
- **Endpoints** — per-endpoint send count and timing
- **Failures** — recent exchange failures with exception details
- **Redeliveries** — recent redelivery attempts

## Controls

- `F5` — refresh (take new JFR snapshot)
- `Space` — cycle view
- `s` / `S` — cycle sort column / reverse sort direction
- `Esc` — back

Requires at least one active recording; start one via `--jfr`,
`jcmd <pid> JFR.start`, or JMX.
