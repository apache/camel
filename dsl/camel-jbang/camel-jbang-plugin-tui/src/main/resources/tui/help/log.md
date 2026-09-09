# Log

The Log tab shows live log output from the running integration, similar
to `tail -f` on a log file. Log entries are color-coded by level for
quick visual scanning.

## Log Levels

Each log message has a severity level:

- **ERROR** (red) — Something went wrong that needs attention. Check the message and stack trace. Errors often correspond to entries on the Errors tab
- **WARN** (yellow) — Potential issues that may need attention but are not immediately critical. Examples: deprecated features, connection retries, configuration warnings
- **INFO** (green) — Normal operational messages: routes starting, endpoints connecting, messages processed. This is the default level
- **DEBUG** (blue) — Detailed diagnostic information for troubleshooting. Shows internal decision-making, message transformations, and routing logic
- **TRACE** (dim) — Very fine-grained output showing every step of processing. Generates a lot of output — use only when debugging a specific problem

## Example Screen

```
 10:29:32 INFO  [main] CamelContext started in 1s234ms
 10:29:33 INFO  [Camel (camel-demo) thread #2] HIGH: Hello from Camel at 10:29:33
 10:29:34 INFO  [Camel (camel-demo) thread #2] LOW: Hello from Camel at 10:29:34
 10:29:35 WARN  [Camel (camel-demo) thread #3] Connection retry 1 of 3
 10:29:38 ERROR [Camel (camel-demo) thread #3] Connection refused: localhost:9092
```

## Log Level

Press `l` to open the log level picker. Selecting a level changes the
running application's root logger to that level:

- Select **ERROR** — only ERROR messages are logged
- Select **WARN** — WARN and ERROR are logged
- Select **INFO** — INFO, WARN, and ERROR are logged (default)
- Select **DEBUG** — everything except TRACE is logged
- Select **TRACE** — all messages are logged

This changes the actual log level of the running integration, not
just a display filter. It is useful for temporarily enabling debug
logging to diagnose an issue.

## Find and Highlight

**Find** (`/`) — search for text in the log. Type a search term and
press Enter to jump to the first match. Use `n` to go to the next
match and `N` for the previous match. The current match is shown
with a green background, other matches with yellow. Press `Esc`
to clear the search.

**Highlight** (`h`) — persistently highlight all occurrences of a
word in the log. Type a word and press Enter — all occurrences
are highlighted with a yellow background while the log continues
scrolling in follow mode. Press `h` again and submit an empty
term to clear the highlight. Both find and highlight can be
active at the same time.

Both find and highlight are case-insensitive.

## Thread Names

The thread name in square brackets (e.g., `[Camel (camel-demo) thread #2]`)
tells you which thread produced the log message. This helps correlate
log entries with specific routes when multiple routes run concurrently.

## Pinned Log

Press `Ctrl+L` from any tab to pin the log to the bottom of the screen.
This lets you watch log output while working in other tabs (Routes,
Diagram, Overview, etc.) without switching back and forth.

When pinned, pressing `Ctrl+L` again cycles the panel height through
25%, 50%, 75%, and then turns the pin off. The default pin size is 50%.

When you navigate to the Log tab itself (key `2`), the log goes
full-screen as usual. Leaving the Log tab brings the pin back.

If the Shell (`F6`) or AI panel (`F8`) is opened while the log is
pinned, it temporarily takes over the bottom panel. Closing Shell/AI
restores the pinned log automatically.

The pinned log panel can also be resized by dragging its top border
with the mouse.

## Keys

- `Up/Down` — scroll log
- `PgUp/PgDn` — scroll by page
- `Home/End` — jump to beginning/end of log
- `/` — find (search for text)
- `n` — next match
- `N` — previous match
- `h` — highlight a word
- `l` — change log level
- `f` — toggle follow mode (`End` turns it on). Scrolling with the mouse wheel, `Up`, `PgUp` or `Home` turns it off; the title then shows `(paused, End follows)` when new lines arrive below the view
- `w` — toggle word wrap
- `Ctrl+L` — pin/cycle/unpin log panel (works from any tab)
- `Esc` — clear find / back
