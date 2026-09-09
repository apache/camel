# Network Services

Network Services shows all **network-facing endpoints** — HTTP listeners,
Kafka connections, database links, messaging brokers — with direction, protocol,
and hit counts. Unlike the Endpoints tab which includes internal plumbing
(`direct:`, `seda:`, `log:`), this tab focuses on real network traffic.

## Table Columns

- **COMPONENT** — The Camel component (e.g., `platform-http`, `kafka`, `sql`)
- **ROUTE** — The route this service belongs to
- **DIR** — Direction: `in` (consuming/listening) or `out` (producing/calling)
- **PROTOCOL** — Network protocol: `http`, `https`, `tcp`, `amqp`, etc.
- **HOSTED** — Whether this is a locally hosted service (e.g., HTTP server) vs a remote client connection
- **HITS** — Total number of messages processed through this service endpoint
- **BODY** — Average message body size (shown when payload sizing is active)
- **HDR** — Average message headers size (shown when payload sizing is active)
- **SERVICE URL** — The network address or connection URL

## Flow Diagram

The bottom panel shows the same in/out flow diagram and throughput sparkline
as the Endpoints tab, but scoped to network services only. This gives a
clearer picture of actual external traffic without internal routing noise.

## Keys

- `Up/Down` — select service
- `s` — cycle sort column
- `S` — reverse sort order
- `a` — toggle chart on/off
