# Browse

The Browse tab lets you inspect messages currently queued in browsable
endpoints. Browsable endpoints include `seda`, `browse`, and `stub` —
these are in-memory queues where messages wait to be consumed.

This is useful for debugging message flow: you can see what messages
are waiting in a queue without consuming them (unlike a consumer
which removes messages from the queue).

## Endpoint List

The left panel shows all browsable endpoints with their current
queue sizes. Select an endpoint to see its queued messages.

## Example Screen

```
 Endpoint          Queue Size
 seda://queue      5
 seda://orders     12
 browse://audit    3
```

## Message View

When you select an endpoint, the right panel shows each queued message:

- **Exchange ID** — Unique identifier for this exchange
- **Exchange Pattern** — `InOnly` (fire-and-forget) or `InOut` (request-reply)
- **Headers** — Message headers as key-value pairs (e.g., `Content-Type: application/json`, `CamelFileName: order.xml`)
- **Body** — The message body content. This is the actual data being processed — could be JSON, XML, plain text, or binary

## When To Use

- **Debugging routing**: If messages are not reaching their destination, check if they are accumulating in a SEDA queue
- **Inspecting content**: Verify that message transformations (marshal, unmarshal, setBody) are producing the expected output
- **Testing**: When using `stub` endpoints for testing, browse to verify messages were sent correctly
- **Monitoring backlog**: A growing queue size may indicate that consumers cannot keep up with producers

## Keys

- `Up/Down` — navigate endpoints and messages
- `PgUp/PgDn` — page through list or detail
- `Tab` — switch focus between messages and detail (in detail view)
- `Enter` — browse selected endpoint / view message detail
- `p` — toggle pretty-print for message body
- `Esc` — back to previous view
