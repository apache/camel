# Errors

The Errors tab shows exchanges that have failed with exceptions. This is
your primary tool for debugging integration problems — it captures the
exception, the exchange state at the time of failure, and the full path
the message took through the route before it failed.

Camel maintains an error registry that keeps the most recent errors
(up to a configurable limit). Older errors are automatically purged.

## Error List

- **ID** — Exchange identifier (e.g., `ID-myhost-1234-5`). This uniquely identifies the message that failed
- **AGO** — How long ago the error occurred (e.g., `5s`, `2m`, `1h`). Recent errors appear first by default
- **ROUTE** — Route where the error happened
- **NODE** — The specific processor/node in the route where the exception was thrown (e.g., `to1`, `bean2`, `process3`). This tells you exactly which step failed
- **HANDLED** — Whether the error was caught by Camel's error handling: `true` (handled) or `false` (unhandled)
- **EXCEPTION** — Exception class name in short form (e.g., `IOException`, `HttpOperationFailedException`)
- **MESSAGE** — The exception message text explaining what went wrong

## Example Screen

```
 ID                  AGO  ROUTE        NODE   HANDLED  EXCEPTION    MESSAGE
 ID-myhost-1234-5    5s   timer-route  to1    false    IOException  Connection refused
 ID-myhost-1234-3    2m   kafka-route  bean2  true     NPE          null reference in processor
```

## Understanding HANDLED

- **handled=true**: The error was caught by Camel's error handling
  mechanism. This includes:
  - `onException` blocks that handle specific exception types
  - `errorHandler` configured on the route or context
  - `deadLetterChannel` that redirects failed messages to an error queue
  - `doTry/doCatch` blocks in the route

  The route continued executing (or the message was redirected),
  so the caller may not even know an error occurred.

- **handled=false**: The error was not caught by any error handler.
  The exchange failed completely and the exception propagated back to
  the caller. For a consumer like Kafka, this might trigger a message
  redelivery. For an HTTP endpoint, the caller gets a 500 error.

## Detail View

Press `Enter` on an error to see the full details:

- **Stack trace**: Complete Java exception chain showing exactly where
  in the code the error occurred. Look for your own classes and Camel
  component classes — framework internals can usually be skipped
- **Message History**: Step-by-step trace showing every node the
  exchange visited before failing. This is invaluable for understanding
  the path the message took:

```
  RouteId     ProcessorId  Processor       Elapsed
  my-route    from1        timer://tick    0ms
  my-route    setBody1     setBody         0ms
  my-route    to1          http://api      5023ms  <-- failed here
```

  The last entry is where the failure occurred. The elapsed time for
  that step often reveals the problem (e.g., 5023ms suggests a
  connection timeout).

- **Headers**: Exchange message headers at the time of failure
- **Body**: Message body content
- **Properties**: Exchange-level properties
- **Variables**: Exchange variables

Use `h`, `b`, `p`, `v` keys to toggle each section.

## Common Error Patterns

- **Connection refused / timeout**: Downstream service is down or
  unreachable. Check network and service status.
- **HttpOperationFailedException**: HTTP call returned an error
  status code. Check the response body for details.
- **TypeConversionException**: Camel cannot convert a message to the
  required type. Check message format and data types.
- **NullPointerException**: A processor received unexpected null data.
  Check if the message body or headers are populated correctly.

## Error Diagram

Press `d` in the detail view to open a visual route diagram showing
the path the failed exchange took through the route. Visited nodes are
highlighted in red to trace the error path.

Use `Up/Down` arrow keys to step through the visited nodes one by one —
the diagram progressively highlights each step in order, showing exactly
how the exchange flowed through the route before failing. The current
step is shown with a selection highlight. Use `Left/Right` to scroll
the diagram horizontally if it extends beyond the screen.

**Info Panel** — An info panel on the left side of the diagram shows
metadata for the selected error: exchange ID, route, node, elapsed
time, thread, handled status, and the full exception. It also shows
body, headers, properties, and variables respecting the `b/h/p/v`
toggles. Press `i` to cycle the panel size: narrow (35 chars),
wide (half screen), or full (entire area). In wide mode, the minimap
and tree preview are hidden. Word wrap (`w`) is also supported.

Press `d` to close the diagram and return to the detail view.
Press `Esc` to navigate back one route in drill-down mode.

## Keys

- `Up/Down` — select error (list) / navigate path steps (diagram)
- `Enter` — view error details
- `d` — toggle error diagram (open and close)
- `Esc` — back to list / back one route in diagram drill-down
- `i` — cycle info panel size (narrow / wide / full) in diagram
- `h` — toggle headers
- `b` — toggle body
- `p` — toggle properties
- `v` — toggle variables
- `w` — toggle word wrap
- `s` — cycle sort column
- `S` — reverse sort order
