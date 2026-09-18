# Route Controller

Shows the status of the Camel route controller. The route controller manages
route startup and can automatically restart routes that fail to start.

There are two types of route controllers:

- **Default** — Routes are started in order and a failure stops the context.
  This tab shows "Route controller: Default (not supervised)" in this case.
- **Supervising** — Routes that fail to start are retried with exponential
  backoff. The controller tracks attempts and manages restart scheduling.

When using the supervised controller and all routes start successfully,
the tab shows "All routes started successfully".

## Table Columns

- **ROUTE** — The route ID
- **STATUS** — Route state: `Started`, `Stopped`, or other lifecycle states
- **SUPERVISING** — Supervision status (e.g., `Active` when the route is being
  restarted by the controller)
- **ATTEMPTS** — Number of restart attempts so far
- **LAST** — Time since the last restart attempt
- **NEXT** — Time until the next scheduled restart attempt
- **URI** — The route's consumer endpoint URI. If the route has a startup error,
  the error message is shown here instead (in red)

## Keys

- `Up/Down` — select route
- `s` — cycle sort column
- `S` — reverse sort order
