## Route Topology

This example demonstrates inter-route topology in an order processing system.
It showcases how multiple routes connect through shared endpoints — both internal (direct)
and external (kafka) — making it a good example for the `route-topology` command.

### How to run

    camel run route-topology.camel.yaml

You can use `--stub` to run without a Kafka broker installed.
This replaces Kafka with an internal in-memory queue, so the routes still connect and messages flow end-to-end:

    camel run route-topology.camel.yaml --stub=kafka

### View the route topology

    camel cmd route-topology

View as a Unicode diagram with live metrics and route descriptions:

    camel cmd route-topology --theme=unicode --metric --description

Sample output:

```
    ┌──────────────────────┐    ┌──────────────────────┐
    │   Generate Orders    │    │    Order REST API    │
    │          54          │    └──────────────────────┘
    └──────────────────────┘
                │                           │
                │             ┬─────────────┘
                └─────────────│
                              ▼
                  ┌──────────────────────┐
                  │    Process Order     │
                  │          54          │
                  └──────────────────────┘
                              │
                ┬─────────────┴─────────────┬
                ▼                           ▼
    ┌──────────────────────┐    ┌──────────────────────┐
    │    Dispatch Order    │    │    Validate Order    │
    │          54          │    │          54          │
    └──────────────────────┘    └──────────────────────┘
                │
                └───────────────────────────┬
                ▼                           ▼
    ┌──────────────────────┐    ┌──────────────────────┐
    │    Fulfill Order     │    │  Send Notification   │
    │          54          │    │          54          │
    └──────────────────────┘    └──────────────────────┘
```

Save as PNG image:

    camel cmd route-topology --theme=dark --output=topology.png

### View as JSON

    camel cmd route-topology --json
