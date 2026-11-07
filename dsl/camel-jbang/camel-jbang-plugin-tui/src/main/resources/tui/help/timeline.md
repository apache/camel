# Timeline

The last minute of the selected integration on one time axis, one column
per second: what went wrong (the cause) on top, and what it did to the
routes (the effect) under it. Read it left to right: "the stock service
answered 500 for ten seconds, redelivery saved the first orders, the rest
went to the dead letter channel, and it recovered by itself".

The integration records the timeline itself, for the last 5 minutes (the
`timeline` dev console, on with `camel run`), so the screen opens with
the last minutes, also when the TUI was started after the integration.
`camel get timeline` shows the same from the command line.

## Errors per second

The errors of the integration, stacked by the kind of exception, with the
HTTP status when there is one, such as `HttpOperationFailedException 503`.
An error counts once per exchange that failed or that the error handler
handled. A failure that a redelivery recovered is not an error of the
exchange: look for it in the yellow of the routes.

With camel-chaos on, the faults of each chaos rule are a layer of their
own, marked 🎲 with the id of the rule (`🎲 r1 error 503 (stock)`). The
integration records them too, with the seconds a rule had a route down,
so they are there also for an outage before the TUI started. An
error that a rule made is not counted again as an error, so an injected
fault and a real one stay apart.

When nothing went wrong, the band is one line: `Errors/s none`.

## Routes

A line per route, one character per second. The height is the number of
exchanges done that second, relative to the busiest second of the route.
The colour is the worst that happened to them that second:

- green: completed
- yellow: recovered by redelivery (completed after a redelivery in this
  route), or redelivery attempts
- orange: handled by the error handler, such as moved to the dead letter
  channel
- red: failed

A `·` is a second with no exchange, and a red `░` is a second a chaos
rule had the route down (`stall` or a lifecycle fault). A mark says what
happened to the route then: `▶` started, `■` stopped, `↻` reloaded, `✕`
removed, `!` a restart failed. At the right: the
exchanges per second over the window, and the worst count.

## The selected route

Its outcomes stacked per second (green at the bottom, then yellow, orange
and red), the mean processing time per second with the average, the p95
and the max of its slowest second, the exchanges in flight, the
redelivery attempts, and the slowest remote calls of its failed and
slowest exchanges. The last line has the totals of the window.

## The exchanges of a second

Paused, a cursor `▲` under the time axis picks a second (or a column of
seconds). **Enter** lists the exchanges the integration kept of it: the
failed and handled ones (up to 10 a second) and the 3 slowest, with their outcome,
time and slowest call, and the chaos rule that faulted one. **Enter**
again shows the selected exchange: its calls with their times and its
exception. The title says how many exchanges were done then, of which
these were kept.

## Keys

- **Up/Down** — Select a route for the detail, or an exchange in the list
- **w** — The window: 1 minute, 2 minutes or 5 minutes (several seconds
  a column when the screen is too narrow)
- **Space** — Pause, to read a moment while it scrolls away; Space
  follows now again
- **Left/Right** — While paused, move the cursor a second; at the edge
  the window moves
- **Shift+Left/Right** — While paused, go back and forth in the 5
  minutes kept
- **Enter** — The exchanges of the second at the cursor (it pauses
  first), or the selected exchange in detail
- **Esc** — Close the exchange, then the list, then follow now again
- **Shift+F8** — Explain with AI what happened, as cause and effect

An AI can read the same per second series with `tui_get_table` on the
Timeline tab, and the exchanges of the second at the cursor while the
list is open.
