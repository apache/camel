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

Press `Enter` on a selected route to **drill down** into its
internal EIP structure (the route diagram). Press `Esc` to
return to the topology view.

## Route Diagram

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

## Keys

**Topology view:**
- `↑↓←→` — navigate between route boxes
- `Enter` — drill down into selected route
- `c` — show route source code
- `Esc` — close diagram

**Route diagram:**
- `↑↓←→` — navigate between EIP nodes
- `Enter` — jump to linked route (when `↵` indicator shown)
- `c` — show source code at selected node
- `d` — toggle EIP detail panel (shows configured options)
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
- `n` — toggle description labels on/off
- `PgUp/PgDn` — page scroll
- `Home/End` — top/end
