# Kafka

Displays live status for all Kafka consumers in the selected integration.
Data comes from the Kafka dev console built into `camel-kafka`, which
tracks each consumer worker thread's group membership, current position,
and health state.

## Table Columns

- **ROUTE** — Route ID that owns this Kafka consumer
- **STATUS** — Worker state: `Running` (green, actively polling),
  `Paused` (yellow, consumer paused), or error states (red)
- **GROUP-ID** — Kafka consumer group ID this consumer belongs to
- **TOPIC** — Topic of the last consumed record
- **PARTITION** — Partition number of the last consumed record
- **OFFSET** — Offset of the last consumed record
- **ENDPOINT** — Camel endpoint URI for this consumer
- **ERROR** — Last error message if the consumer is unhealthy

## What to Look For

- **All consumers Running**: Normal operation. Offsets should be
  advancing steadily if messages are flowing.
- **Consumer Paused**: The consumer has been programmatically paused
  (e.g., by a route policy or manual suspension).
- **Error column populated**: The consumer worker is unhealthy.
  Check the error message for connection issues, authentication
  failures, or deserialization errors.
- **Offset not advancing**: If messages are being produced but the
  offset stays the same, the consumer may be stuck or the topic
  may have no new messages on that partition.

## Keys

- `Up/Down` — select consumer
- `s` — cycle sort column
- `S` — reverse sort order
