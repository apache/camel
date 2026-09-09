# Threads

The Threads tab shows all JVM threads with their state, blocked/waited
counts, and stack traces. This helps diagnose deadlocks, thread
contention, and excessive thread creation.

By default, the view is filtered to show only Camel-related threads
(threads whose name contains `camel`, `vertx`, or `netty`). Press `f`
to toggle between Camel threads and all JVM threads.

## Table Columns

- **ID** — Thread ID assigned by the JVM. Lower IDs are typically system threads created at startup
- **NAME** — Thread name. Camel names its threads descriptively (e.g., `Camel (camel-demo) thread #1 - timer://hello`). The thread name often tells you which route or component is using it
- **STATE** — Thread state (see Thread States below)
- **BLOCKED** — Number of times this thread was blocked waiting for a monitor lock, with total blocked time in parentheses if available (e.g., `3(45ms)`). High blocked counts indicate lock contention
- **WAITED** — Number of times this thread entered a waiting state, with total wait time if available (e.g., `150(2340ms)`). High wait counts are normal for thread pool threads waiting for work

## Example Screen

```
 ID  NAME                                             STATE           BLOCKED     WAITED
 1   main                                             WAITING         0           5(120ms)
 22  Camel (camel-demo) thread #1 - timer://hello     TIMED_WAITING   0           17(34000ms)
 23  Camel (camel-demo) thread #2 - timer://pump      TIMED_WAITING   0           12(36000ms)
 24  Camel (camel-demo) thread #3 - seda://queue      WAITING         0           12(1200ms)
 25  vert.x-eventloop-thread-0                        RUNNABLE        0           0
```

## Thread States

- **RUNNABLE** (green) — Actively executing code or ready to run. The thread has work to do and the CPU is available
- **BLOCKED** (red) — Waiting to acquire a monitor lock held by another thread. This is lock contention — another thread holds the `synchronized` block. Multiple BLOCKED threads on the same lock may indicate a bottleneck
- **WAITING** (yellow) — Waiting indefinitely for another thread to signal. Common for thread pool threads sitting idle in a queue, waiting for work to arrive
- **TIMED_WAITING** (cyan) — Waiting with a timeout. Typical for: `Thread.sleep()`, scheduled poll consumers waiting for the next poll interval, `Object.wait(timeout)`. Camel timer and polling consumers spend most of their time in this state between polls

## What To Look For

- **Many BLOCKED threads**: Lock contention — multiple threads competing for the same resource. Check the stack traces to find the contested lock
- **Many WAITING threads**: Usually normal — thread pools waiting for work. But if the application is supposed to be busy, idle threads may indicate a configuration issue
- **Thread count growing over time**: Possible thread leak — threads being created but not properly shut down. Compare current count with peak count in the summary bar
- **Deadlock**: Two or more threads each waiting for a lock held by the other. Look for BLOCKED threads with cross-referencing lock names

## Stack Trace View

The detail panel at the bottom always shows the stack trace of the
currently selected thread. As you navigate threads with `Up/Down`,
the stack trace updates automatically. Lines containing
`org.apache.camel` are highlighted in yellow to help you find
Camel-related frames.

Use `PgUp/PgDn` to scroll through long stack traces.

## Filter Modes

- **camel** (default) — Show only Camel-related threads (names containing `camel`, `vertx`, or `netty`)
- **all** — Show all JVM threads including system threads, GC threads, JIT compiler threads

## Auto-Refresh

Thread data is automatically refreshed every 5 seconds so you can
watch BLOCKED/WAITED counters change over time without manual action.

## Keys

- `Up/Down` — select thread (detail updates automatically)
- `PgUp/PgDn` — scroll stack trace detail
- `s` — cycle sort column
- `S` — reverse sort order
- `f` — toggle filter (camel / all)
- `Esc` — back
