# Inspect

The Inspect tab shows a history of recently processed exchanges
(messages). This is one of the most powerful debugging tools — it
lets you see exactly what happened to each message as it traveled
through the integration, including every step it passed through.

Camel uses a BacklogTracer to record exchange details. The most
recent exchanges are kept in memory for inspection.

## Exchange List

- **ID** — Unique exchange identifier (e.g., `ID-myhost-1234-5`). Every message passing through Camel gets a unique ID
- **STATUS** — Whether the exchange completed successfully (`done`) or failed (`fail`). Failed exchanges also appear on the Errors tab
- **ROUTE** — The route that processed this exchange
- **AGO** — How long ago this exchange was processed (e.g., `2s`, `1m`, `5m`)
- **ELAPSED** — Total processing time from when the exchange entered the route until it completed. Long elapsed times may indicate slow downstream services

## Example Screen

```
 ID                   STATUS  ROUTE          AGO  ELAPSED
 ID-myhost-1234-10    done    timer-to-log   1s   0ms
 ID-myhost-1234-9     done    seda-consumer  2s   0ms
 ID-myhost-1234-8     done    timer-to-seda  2s   1ms
 ID-myhost-1234-7     fail    kafka-route    5s   5023ms
```

The last exchange (`kafka-route`) failed after 5 seconds — likely a
connection timeout to the Kafka broker.

## Detail View

Press `Enter` on an exchange to see its full journey:

**Message History** — A step-by-step trace of every node the exchange
visited. This shows the exact path the message took through the route,
including which branch was taken in a `choice` and how long each step
took:

```
      RouteId        NodeId     Processor            BHPV  Elapsed
 *->  timer-to-log   timer1     from[timer:hello]          0ms
      timer-to-log   setBody1   setBody[simple]      B     0ms
      timer-to-log   choice1    choice                     0ms
      timer-to-log   when1      when[simple]               0ms
 --->  timer-to-log   to1       to[kafka:orders]      H    2ms
      timer-to-log   log1       log[HIGH: ${body}]         0ms
 <-*  timer-to-log   timer1     from[timer:hello]          3ms
```

This tells you the message entered via the timer, went through setBody,
reached a choice node, matched the `when` condition, and was logged.
The elapsed time for each step helps identify bottlenecks.

**Change Indicators (BHPV)** — The BHPV column shows at a glance
which parts of the exchange were modified at each step compared to
the previous step:

- `B` — Body changed
- `H` — Headers changed
- `P` — Exchange properties changed
- `V` — Exchange variables changed

Steps with no changes leave the column blank, so mutations stand
out visually.

**Depth-first ordering** — When an exchange spans multiple routes
via async EIPs (multicast, splitter, recipientList), child exchange
steps are inlined under the parent step that triggered them, indented
with 2 spaces per depth level. This keeps the logical flow readable
instead of interleaving concurrent branches.

## Direction Arrows

The first column shows direction arrows that indicate the type
of each step:

- `*-->` — First step of a route consuming from a **remote** endpoint (e.g., Kafka, HTTP)
- `*-> ` — First step of a route consuming from a **local** endpoint (e.g., timer, direct)
- `<--*` — Last step of a route with a **remote** consumer endpoint
- `<-* ` — Last step of a route with a **local** consumer endpoint
- `--->` — A step that sends to a **remote** endpoint (e.g., `to[kafka:orders]`)
- `~-->` — First step or send to a **stub** endpoint (running with `--stub` mode)
- `<--~` — Last step of a route with a **stub** consumer endpoint
- _(blank)_ — A regular processing step (log, setBody, choice, etc.)

**Exchange Content** — Toggle these sections to inspect the message:

- `h` — **Headers**: Key-value pairs carried with the message (e.g., `Content-Type`, `CamelFileName`, custom headers)
- `b` — **Body**: The actual message content (text, JSON, XML, etc.)
- `p` — **Properties**: Exchange-level metadata (not forwarded to endpoints, used for internal routing)
- `v` — **Variables**: Exchange variables set during processing

## Use Cases

- **Debugging routing logic**: Check which branch a `choice` or `filter` took
- **Verifying transformations**: Compare body before and after a `transform` or `marshal` step
- **Finding bottlenecks**: Look for steps with high elapsed times
- **Understanding failures**: See exactly where in the route a failure occurred

## Route Diagram

Press `d` to open the route diagram for the selected exchange.
The diagram shows the route structure as a visual flowchart with
box-drawing characters, highlighting the path the exchange took
through the route in green (or red for failed exchanges).

**Progressive Path Highlighting** — Use `Up/Down` to step through
the exchange's journey node by node. As you navigate forward, each
visited node lights up progressively in green, creating a visual
replay of the message's path. Stepping backward removes the
highlight from the last node. The currently selected node is
shown with a dark background.

**Multi-route exchanges** — When an exchange spans multiple routes
(e.g., via `direct` or `seda` endpoints), all involved routes are
shown stacked vertically. The diagram auto-scrolls to keep the
current step visible.

**Route Structure Preview** — A compact tree view appears in the
bottom-right corner showing the full route hierarchy. The currently
selected node is highlighted, helping you maintain orientation in
large routes. This is the same minimap available on the Routes and
Diagram tabs.

**Info Panel** — An info panel on the left side of the diagram shows
trace metadata for the current step: exchange ID, route, node,
processor, elapsed time, thread, and direction. It also shows body,
headers, properties, and variables respecting the same `b/h/p/v`
toggles as the table view. Press `i` to cycle the panel size:
narrow (35 chars), wide (half screen), or full (entire area).
In wide mode, the minimap and tree preview are hidden to give more
space. Word wrap (`w`) is also supported.

Press `d` to close the diagram and return to the table.
Press `Esc` to navigate back one route in drill-down mode.

## Keys

- `Up/Down` — select exchange (or step through path in diagram)
- `Enter` — view exchange details
- `d` — toggle route diagram (open and close)
- `Esc` — back to list / back one route in diagram drill-down
- `i` — cycle info panel size (narrow / wide / full) in diagram
- `n` — toggle description mode
- `g` — toggle waterfall view
- `h` — toggle headers
- `b` — toggle body
- `p` — toggle properties
- `v` — toggle variables
- `w` — toggle word wrap
- `s` — cycle sort column
- `S` — reverse sort order
- `Left/Right` — horizontal scroll (diagram or detail)
- `PgUp/PgDn` — page scroll
- `F5` — refresh data
