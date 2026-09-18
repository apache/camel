# Events

Shows Camel lifecycle events captured by the event notifier. Events are
grouped into three categories:

- **general** — Context-level events: CamelContext starting/started/stopping,
  service add/remove, component add/remove
- **route** — Route lifecycle events: route added/removed/started/stopped/reloaded
- **exchange** — Exchange events: exchange created/completed/failed/sending/sent

Events are stored in a circular buffer (default capacity 25 per category).
Only the most recent events are shown.

## Table Columns

- **AGO** — How long ago the event occurred (e.g., `2s`, `1m30s`)
- **CATEGORY** — Event category: `general`, `route`, or `exchange`
- **TYPE** — The specific event type (e.g., `CamelContextStartedEvent`,
  `RouteStartedEvent`, `ExchangeCompletedEvent`)
- **MESSAGE** — Human-readable event description

## Keys

- `Up/Down` — select event
- `s` — cycle sort column
- `S` — reverse sort order
