# Producers

Producers are the **output** side of a Camel route. They send data to
external systems (message brokers, HTTP endpoints, databases, files, etc.).

Unlike consumers (one per route), a route can have multiple producers
— each `.to()` or `.toD()` call in the route creates a producer.

## Table Columns

- **ROUTE** — The route this producer belongs to
- **STATUS** — Producer state: `Started` (running normally) or `Stopped`
- **TYPE** — The Camel component type (e.g., `Kafka`, `Http`, `Log`, `Seda`)
- **REMOTE** — Whether this producer sends to a remote system (`Yes`) or is in-process (`No`)
- **URI** — The full endpoint URI (e.g., `kafka://my-topic`, `log://mylogger`)

## Keys

- `Up/Down` — select producer
- `s` — cycle sort column
- `S` — reverse sort order
