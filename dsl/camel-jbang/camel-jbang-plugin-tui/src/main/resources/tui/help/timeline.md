# Timeline

The last minute of the selected integration on one time axis, one column
per second: what went wrong (the cause) on top, and what it did to the
routes (the effect) under it. Read it left to right: "the stock service
answered 500 for ten seconds, redelivery saved the first orders, the rest
went to the dead letter channel, and it recovered by itself".

The timeline is recorded from the moment the TUI watches the integration,
so it opens with the last minutes, not an empty chart.

## Errors per second

The errors of the integration (as the Errors tab lists them), stacked by
the kind of exception, with the HTTP status when there is one, such as
`HttpOperationFailedException 503`. An error counts once per exchange
that failed or that the error handler handled. A failure that a
redelivery recovered is not an error of the exchange: look for it in the
yellow of the routes.

The errors are read while this screen or the Errors tab shows. The first
time, the errors the integration still keeps are put at their own second.

With camel-chaos on, the faults of each chaos rule are a layer of their
own, marked 🎲 with the id of the rule (`🎲 r1 error 503 (stock)`). An
error that a rule made is not counted again as an error, so an injected
fault and a real one stay apart.

When nothing went wrong, the band is one line: `Errors/s none`.

## Routes

A line per route, one character per second. The height is the number of
exchanges done that second, relative to the busiest second of the route.
The colour is the worst that happened to them that second:

- green: completed
- yellow: recovered by redelivery (completed after the error handler
  redelivered it)
- orange: handled by the error handler, such as moved to the dead letter
  channel
- red: failed

A `·` is a second with no exchange, and a red `░` is a second a chaos
rule had the route down (`stall` or a lifecycle fault). At the right: the
exchanges per second over the window, and the worst count.

## The selected route

Its outcomes stacked per second (green at the bottom, then yellow, orange
and red), the mean processing time of the exchanges done each second, and
the exchanges in flight. The last line has the totals of the window.

## Keys

- **Up/Down** — Select a route for the detail
- **w** — The window: 1 minute, 2 minutes or 5 minutes (several seconds
  a column when the screen is too narrow)
- **Space** — Pause, to read a moment while it scrolls away; Space or Esc
  follows now again
- **Left/Right** — While paused, go back and forth in the 5 minutes kept

The numbers come from the route statistics in the status file, as the
difference from one second to the next. An AI can read the same per
second series with `tui_get_table` on the Timeline tab.
