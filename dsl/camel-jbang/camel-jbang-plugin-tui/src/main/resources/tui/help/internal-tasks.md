# Recovery Tasks

Recovery tasks are background operations that Camel components schedule
for reconnection and retry purposes. For example, when a Kafka broker or
JMS connection drops, the component registers a recovery task that
periodically attempts to reconnect.

Tasks self-register when they start running and are removed when they
complete, fail permanently, or exhaust their retry budget. The table
shows only **currently active** tasks.

## Table Columns

- **NAME** — Descriptive name of the task (e.g., connection target or component)
- **STATUS** — Current state: `Waiting` (waiting for next attempt), `Attempting` (actively trying now), `Completed` (finished successfully), `Failed` (gave up), `Exhausted` (retry budget spent)
- **ATTEMPTS** — Number of retry attempts made so far
- **DELAY** — Current delay between attempts in milliseconds
- **ELAPSED** — Total time since the task started
- **FIRST** — When the first attempt was made (ago)
- **LAST** — When the last attempt was made (ago)
- **NEXT** — Time until the next attempt
- **ERROR** — Last error message, if any

## When Tasks Appear

You will typically see recovery tasks when:
- A messaging broker connection is lost and the consumer is reconnecting
- A leader election is in progress (e.g., camel-master)
- A component is retrying a failed initialization

An empty table means all connections and background operations are healthy.

## Keys

- `Up/Down` — select task
- `s` — cycle sort column
- `S` — reverse sort order
