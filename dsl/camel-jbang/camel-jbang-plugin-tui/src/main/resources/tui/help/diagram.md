# Diagram

The Diagram tab shows a visual topology of how routes connect to each other.
This helps you understand the overall message flow in your integration.

## Topology View

The topology view shows all routes and their connections:
- **Trigger routes** (timer, cron, etc.) appear at the top
- **Downstream routes** appear below, connected by arrows
- Routes that are connected show edges between them

## Example Topology

```
┌──────────────────┐
│ order-generator  │
│  (timer://gen)   │
│                  │
│      3748        │
└──────────────────┘
         │
         ▼
┌──────────────────┐
│  process-order   │
│ (direct:process) │
│                  │
│    3748/12!      │
└──────────────────┘
```

Each box represents a route. The first line is the route ID,
the second line shows the `from` endpoint, and the bottom line
shows metrics when enabled. Arrows show how routes connect.

## Metrics

When metrics are enabled, each route box shows exchange counts:
- **Green** number — successful exchanges
- **Red** number — failed exchanges
- Combined as `3748/12` means 3748 ok and 12 failed

## External Systems

Press `e` to cycle through three external modes:

- **off** — no external endpoints shown
- **edges** — external endpoints that are truly outside Camel are shown
  as dashed boxes in top/bottom bands. Routes sharing an external
  endpoint (e.g. kafka) are connected with a direct arrow.
- **all** — same as edges, but routes sharing an external endpoint
  are connected through an intermediary dashed box showing the
  endpoint name, instead of a direct arrow.

External system boxes are drawn with dashed borders to distinguish
them from route boxes. Dashed edges connect routes to external systems.

## Navigation

In the topology view, use arrow keys to select route boxes:
- `↑↓` moves between layers (upstream/downstream routes)
- `←→` moves between routes in the same layer

When a route is selected, an **Info panel** appears on the left
showing key metrics: state, uptime, throughput, exchange counts,
and processing times.

The Info panel also has the **checksum** of the route: a short hash of
the route as written, the same in every run. `camel cmd route-structure`
gives the checksum of the routes of a source file, so it tells whether a
file has the route that runs. A route added again changed in this run,
such as by a reload, has its **revision** too: `Revision: 3 · 2 changes`.
An integration of a Camel version before 4.24 has neither.

Press `Enter` on a selected route to **drill down** into its
internal EIP structure (the route diagram). Press `Esc` to
return to the topology view.

## Levels

The line above the diagram shows its three zoom levels:
Architecture › Topology › Route. The level shown is highlighted,
one that does not apply yet is greyed out. `Enter` zooms in, `Esc`
zooms out (route, topology, architecture), `v` (at the start of
the line) moves to the next level, a click goes to a level.
The right side shows the view settings of the level with their
state: `a` ai (off shows only what the sources and the runtime
say: no AI labels, notes or capabilities; it does not change the AI
overview setting; with no summary yet it has the AI write one, as
`/overview` does), `b` view (business: labels and what capabilities achieve;
technical: route ids and endpoints), `m` metrics, `e` external, `y` types
(the body types on the arrows of a route), `d` detail, `g` group, `u` utility (one setting for the architecture
and the topology, so both show the same groups), `x` errors (the
error handling in a frame below the routes; in the architecture
a box of the routes reached only on error). The footer lists
only actions.

## Architecture View

The Architecture level shows the routes grouped by what they
do for the business, one box per group: route groups from the
source, the capabilities the AI project overview proposed (`✦`,
italic), shared services (routes several groups use), Other, and
Utility (plumbing: error handling, logging; `u` shows it). With
`x` on, the routes reached only on error leave Utility for a dashed
Error handling box, with dashed arrows from the groups whose
failures go there. A box lists its route count, entry points,
external systems and warnings (the Info panel lists them); the
Flow panel at the bottom right shows the inside of the selected group:
each route in flow order, with the routes and remote systems it
hands off to (a log is left out).
`Enter` goes down to the topology of all routes with the group's
routes highlighted, `Enter` on a route opens its diagram,
`Esc` goes back up. It follows edited routes and a new summary
by itself.

## AI-assisted descriptions

With descriptions on (`n`), a route that has no description of its
own shows the one the AI project overview (`/overview` in the AI
panel) suggested, in italic and marked with `✦`. It was written by
an AI, not taken from the route: review it, and `/overview apply`
puts it into the route source once you agree.

Inside a route, the decision points (choice, when, otherwise,
filter, split, aggregate, loop, doTry, doCatch, circuitBreaker)
show a short plain-language label from the AI overview in the
business view (`b`), marked with `✦`; the technical view (`n`)
keeps the expression. The Info panel of such a node shows the
label, why the route decides there, and the code.

The endpoints of remote systems are the edges of the integration:
they have their own color and a dashed frame (solid while
selected), in the route diagram as in the topology. The input of
a route from a remote system shows where it comes from in the top
border of its box, `AMQP ──▶`. Where a message is sent to a remote
system and no route of the integration consumes it, the bottom
border names where it leaves to: `──▶ AMQP`. A hand-off to another
route of the integration shows `↵ route` beside the box. A route
that starts itself on a schedule (timer, cron, quartz, scheduler)
shows `↻ ──▶` at its input.

## Route Diagram

In the route diagram, each EIP node shows its type tag (colored)
and endpoint URI or description. Nodes that connect to other routes
display a `↵` indicator — press `Enter` to jump directly to the
linked route's diagram.

Navigation history is maintained as a stack: pressing `Esc` goes
back to the previous route, and eventually back to the topology view.

The route shows its own steps (the happy path). Its error handling,
the onException clauses (a global one is in every route) and where
its failures go, is in a frame below the route when `x` is on. The
history view always shows the onException clauses, so a replay can
step into them.

## Body Types

With the message history on (such as `camel run --dev`), Camel keeps the
body types each step has been reached with, across the messages. In the
route diagram, `y` shows them beside the arrow into each step, so the
arrows show how the body changes along the route:

```
           ┌──────────────────────┐
           │   unmarshal[json]    │
           └──────────────────────┘
                      │
                      │ ⚠ byte[] | LinkedHashMap (payload and data)
                    3 ▼ 12
           ┌────────────────────⚠─┐
           │   to[file:outbox]    │
           └──────────────────────┘
```

A step reached with bodies of more than one kind (`null`, `payload`,
`data` or `collection`) needs attention, as its body depends on the path
a message takes: its box has a `⚠` in its top border, whether the types
are shown or not. Where the arrows of a branch are side by side, the
types are cut to the room there is: the first ones and how many more
(`ByteArrayInputStreamCache +2`). The Info panel of the selected step
lists them all with their kind, and in the topology the Info panel of a
route says how many of its steps were reached with more than one type
(`⚠ 1 mixed · 2 varied`).

## Route Structure Preview

A compact tree structure preview appears in the bottom-right corner
of the diagram area — like a minimap of the route's EIP structure.

In **topology mode**, the preview shows the structure of the currently
selected route and updates as you navigate between route boxes.

In **drill-down mode**, the preview highlights the currently selected
EIP node (shown in yellow) as you navigate with arrow keys, giving
you an at-a-glance view of where you are in the route.

## Keys

**Topology view:**
- `↑↓←→` — navigate between route boxes
- `Enter` — drill down into selected route
- `c` — show route source code
- `v` — next level: architecture, topology, route
- `g` — show each route's group in the topology, its colour as in
  the architecture, with a legend of the groups at the bottom
- `u` — show or hide utility routes (as in the architecture)
- `x` — show or hide the error handling: the routes reached only on
  error, in a frame below the routes, with where each one's failures
  come from and how they are handled (select one to highlight the
  routes that send to it; a route with error handling has a dim ⚡ on
  its box; with `e`, the external systems only they use are in the
  frame too; needs Camel 4.23+ in the integration);
  in the route view, the route's onException clauses and where its
  failures go, in a frame below its steps
- `s` — open the integration summary (camel-summary.md)
- `Esc` — close diagram

**Architecture view:**
- `↑↓←→` — navigate between groups or routes
- `Enter` — the topology of all routes, the group's highlighted
- `u` — show or hide utility routes
- `x` — show or hide the error handling: the routes reached only on
  error in a box of their own, linked from the groups whose failures
  go there (one setting with the topology's `x`)
- `e` — external systems as boxes linked to the groups that use them
- `s` — open the integration summary at its Architecture section
- `Esc` — back to the groups, then to the topology

**Route diagram:**
- `↑↓←→` — navigate between EIP nodes
- `Enter` — jump to linked route (when `↵` indicator shown)
- `c` — show source code at selected node
- `d` — toggle EIP detail panel (shows configured options)
- `x` — show or hide the route's error handling, in a frame below it
- `y` — show the body types each step has been reached with, beside the
  arrows into the steps
- `g` — go to node (fuzzy search popup)
- `Esc` — go back (previous route or topology)
- `t` — jump back to topology view

**Source view:**
- `↑↓` — move cursor between lines
- `Ctrl+↑↓` — scroll viewport without moving cursor
- `←→` — horizontal scroll
- `PgUp/PgDn` — page jump
- `Home/End` — go to top/bottom
- `Enter` — select the closest diagram node at cursor line
- `Esc/c` — close source view

**Common:**
- `m` — toggle metrics on/off (default: on)
- `e` — toggle external systems on/off (topology only)
- `b` — business or technical view (`n` works too)
- `PgUp/PgDn` — page scroll
- `Home/End` — top/end
