# Consumers

Consumers are the **input** side of a Camel route. They listen for or poll
data from external systems (message brokers, file directories, databases,
timers, etc.) and create exchanges that flow through the route.

There are two types of consumers:

- **Event-driven** (e.g., `seda`, `direct`, `platform-http`): React instantly
  when a message arrives. No polling — messages are pushed to the consumer.
- **Polling** (e.g., `file`, `ftp`, `sql`, `timer`): Run on a scheduler that
  periodically checks for new data. The POLLS column tracks these cycles.

## Table Columns

- **ROUTE** — The route this consumer belongs to. Each route has exactly one consumer (its `from` endpoint)
- **STATUS** — Consumer state: `Started` (running normally), `Polling` (currently in a poll cycle). Shows a health warning icon if the consumer health check is `DOWN`
- **TYPE** — The Camel component type (e.g., `kafka`, `file`, `timer`, `cron`, `seda`)
- **POLLS** — Total number of poll cycles the scheduler has executed. This counts poll **attempts**, not messages received — a poll may check for new files and find none. For event-driven consumers (like `seda`), this column is empty
- **SCHEDULE** — How often the consumer polls: a fixed interval like `1s` or `5000ms`, or a cron expression like `0 0/5 * * *`. Only shown for polling consumers
- **SINCE-LAST** — Three timestamps showing **started** / **completed** / **failed**: when the last exchange was created, when the last exchange completed successfully, and when the last exchange failed. Helps identify stale consumers or recurring failures
- **URI** — The full endpoint URI (e.g., `timer://hello?period=2000`). If the consumer health check is DOWN, the failure message is shown here instead

## Example Screen

```
 ROUTE           STATUS   TYPE     POLLS  SCHEDULE  SINCE-LAST        URI
 timer-to-log    Started  timer    17     2s        1s/1s/-           timer://hello?period=2000
 timer-to-seda   Started  timer    12     3s        0s/0s/-           timer://pump?period=3000
 seda-consumer   Started  seda                      0s/0s/-           seda://queue
```

Notice that `seda-consumer` has no POLLS or SCHEDULE because it is
event-driven — it reacts immediately when a message is put into the
SEDA queue by another route.

## Understanding POLLS vs TOTAL

A common confusion: POLLS counts how many times the scheduler fired,
not how many messages were received. Consider a file consumer polling
every 5 seconds:

- After 1 minute: POLLS = 12 (polled 12 times)
- But TOTAL might be 3 (only 3 files were found and processed)
- The other 9 polls found no new files

A high POLLS count with zero TOTAL exchanges means the consumer is
actively checking but finding no new data — which is normal for many
use cases (e.g., watching a directory for new files).

## Backoff

Some consumers support backoff — reducing poll frequency when polls
return no data. This saves resources when the source is idle. The
consumer automatically returns to normal frequency when data appears.

## Keys

- `Up/Down` — select consumer
- `s` — cycle sort column
- `S` — reverse sort order
