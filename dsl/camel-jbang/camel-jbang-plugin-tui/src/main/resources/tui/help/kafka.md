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

## Fix with AI

Press `Shift+F8` on a consumer with an error, or one that is not
running or paused, to ask the AI to fix it. The AI panel opens with a
question made of its state and last error, its endpoint and consumer
group, the topic, partition and offset it last read, the line of its
route in the source and the last error of the route. The AI finds the
cause and fixes it in the routes or configuration (brokers, topic,
group, deserializers, security), or says how to fix it when it is
outside the integration, such as a broker that is not running.

## Keys

- `Up/Down` — select consumer
- `s` — cycle sort column
- `S` — reverse sort order
- `Shift+F8` — fix with AI the selected consumer, when it has an error or does not run
