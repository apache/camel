# Producers

Producers are the **output** side of a Camel route. They send data to
external systems (message brokers, HTTP endpoints, databases, files, etc.).

Unlike consumers (one per route), a route can have multiple producers
— each `.to()` or `.toD()` call in the route creates a producer.

## Table Columns

- **ROUTE** — The route this producer belongs to
- **STATUS** — Producer state: `Started` (running normally) or `Stopped`
- **TYPE** — The Camel component type (e.g., `Kafka`, `Http`, `Log`, `Seda`)
- **REMOTE** — `x` when this producer sends to a remote system, blank when it is in-process
- **TOTAL** — The messages sent: the exchanges of the steps of the route that send to this endpoint (`to`, `wireTap`, `enrich`)
- **FAIL** — How many of those sends failed
- **P50/P95/P99** — How long a send took, from the busiest of those steps (`MIN/MAX/MEAN` when the integration does not record percentiles)
- **URI** — The full endpoint URI (e.g., `kafka://my-topic`, `log://mylogger`)

The numbers are blank for a producer no step of its route sends to by
that URI, such as the one of a `toD` whose endpoint is computed per
message.

## Keys

- `Up/Down` — select producer
- `s` — cycle sort column
- `S` — reverse sort order
