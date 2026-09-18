# Routes

Routes are the building blocks of a Camel integration. Each route defines
a message flow: where messages come from, how they are processed, and where
they are sent to. A typical integration has multiple routes working together.

## Route Table Columns

- **ROUTE** — Unique route identifier (e.g., `timer-to-log`, `seda-consumer`)
- **FROM** — The endpoint that triggers this route (e.g., `timer`, `kafka`, `file`). This is the source of messages
- **STATUS** — Route state: `Started` (running), `Stopped` (not running), or `Suspended` (paused, can be resumed)
- **MSG/S** or **MSG/M** — Current message throughput (per second or per minute) for this route. Configure in Settings (F2)
- **TOTAL** — Total exchanges processed by this route since startup
- **FAIL** — Exchanges that ended with an unhandled error in this route
- **MIN** — Fastest exchange processing time in milliseconds. This is the time from when the exchange entered the route until it completed
- **MEAN** — Average exchange processing time in milliseconds. A rising MEAN may indicate a downstream service getting slower
- **MAX** — Slowest exchange processing time in milliseconds. A very high MAX compared to MEAN suggests occasional slow outliers
- **SINCE-LAST** — Time since the last exchange activity on this route, shown as up to three values separated by `/`: started/completed/failed (e.g., `1s/3s/1m14s`). Values are omitted when there is no activity of that type

## Percentile Latency (P50/P95/P99)

When **Extended** statistics level is enabled, the timing columns show
percentile latencies instead of MIN/MEAN/MAX — both for routes and processors:

- **P50** — Median processing time (50th percentile). Half of all exchanges completed faster than this
- **P95** — 95th percentile. 95% of exchanges completed faster than this. Useful for SLA monitoring
- **P99** — 99th percentile. Only 1% of exchanges were slower. Highlights worst-case tail latency

Percentiles are computed over a sliding window of recent exchanges, making
them more meaningful than MIN/MAX for understanding real-world performance.
With very few messages (e.g., 10), P95 and P99 may equal the MAX value since
there aren't enough samples to differentiate.

To enable Extended statistics, set `camel.main.load-statistics-enabled = true`
in your application configuration. Without Extended statistics, the columns
show MIN/MEAN/MAX instead.

The TOTAL summary row shows the overall percentiles across all routes.

## Example Screen

```
 ROUTE           FROM                  STATUS   MSG/S  TOTAL  FAIL  P50/P95/P99            SINCE-LAST
 timer-to-log    timer://hello?p=2000  Started  0.50   142    0       1/10/31  ███▒▒▒▒▒░  1s
 timer-to-seda   timer://pump?p=3000   Started  0.33   95     0       0/1/2    ▒░░░░░░░░  2s
 seda-consumer   seda://queue          Started  0.33   95     0       0/0/1               2s
```

## Top Mode

Press `t` to switch to **Top mode** — a performance-focused view that
includes processor-level breakdown and load averages. This shows every
processor (step) inside a route, not just the route totals.

- **LOAD** — Three throughput averages over 1m/5m/15m windows, similar to Unix load average but measuring message throughput instead of CPU. Higher values mean more messages flowing through. The three windows help you see if traffic is increasing or decreasing

## Route Diagram

Press `d` to see a topology diagram showing how all routes connect to each
other. This is the same view as the Diagram tab. Use arrow keys to navigate
between route boxes and press `Enter` to drill down into a route's internal
EIP structure.

## Navigation

In the topology view, use arrow keys to select route boxes:
- `↑↓` moves between layers (upstream/downstream routes)
- `←→` moves between routes in the same layer

When a route is selected, an **Info panel** appears on the left
showing key metrics: state, uptime, throughput, exchange counts,
and processing times.

Press `Enter` on a selected route to **drill down** into its
internal EIP structure (the route diagram). Press `Esc` to
return to the topology view.

## Route Diagram (drill-down)

In the route diagram, each EIP node shows its type tag (colored)
and endpoint URI or description. Nodes that connect to other routes
display a `↵` indicator — press `Enter` to jump directly to the
linked route's diagram.

Navigation history is maintained as a stack: pressing `Esc` goes
back to the previous route, and eventually back to the topology view.

## Route Structure Preview

A compact tree structure preview appears in the bottom-right corner
of the diagram area — like a minimap of the route's EIP structure.

In **topology mode**, the preview shows the structure of the currently
selected route and updates as you navigate between route boxes.

In **drill-down mode**, the preview highlights the currently selected
EIP node (shown in yellow) as you navigate with arrow keys, giving
you an at-a-glance view of where you are in the route.

## Source View

Press `c` to see the original route source code (YAML, XML, or Java).
A `>>` cursor highlights the current line. When opened from a diagram
node, the matching source line is positioned at 2/3 of the viewport.

Use `↑↓` to move the cursor, `Ctrl+↑↓` to scroll the viewport without
moving the cursor. Press `Enter` to select the closest diagram node at
the cursor line — this closes the source view and highlights that node
in the diagram.

## Keys

**Route table:**
- `Up/Down` — select route
- `Enter` — open route diagram
- `p` — start/stop selected route
- `P` — suspend/resume selected route
- `c` — show route source code
- `n` — toggle description labels
- `s` — cycle sort column
- `S` — reverse sort order
- `t` — toggle Top mode

**Topology view:**
- `↑↓←→` — navigate between route boxes
- `Enter` — drill down into selected route
- `c` — show route source code
- `Esc` — close diagram (back to route table)
- `m` — toggle metrics on/off
- `e` — toggle external systems on/off
- `n` — toggle description labels

**Route diagram (drill-down):**
- `↑↓←→` — navigate between EIP nodes
- `Enter` — jump to linked route (when `↵` indicator shown)
- `d` — toggle detail panel (EIP/component catalog docs)
- `g` — go to node (fuzzy search popup)
- `c` — show source code at selected node
- `Esc` — go back (previous route or topology)
- `t` — jump back to topology view
- `m` — toggle metrics
- `n` — toggle description labels
- `PgUp/PgDn` — scroll detail panel (when detail is on)

**Source view:**
- `↑↓` — move cursor between lines
- `Ctrl+↑↓` — scroll viewport without moving cursor
- `←→` — horizontal scroll
- `PgUp/PgDn` — page jump
- `Home/End` — go to top/bottom
- `w` — toggle word wrap
- `p` — toggle plain mode (hides line numbers and borders for easy copy/paste)
- `Enter` — select the closest diagram node at cursor line
- `Esc/c` — close source view
