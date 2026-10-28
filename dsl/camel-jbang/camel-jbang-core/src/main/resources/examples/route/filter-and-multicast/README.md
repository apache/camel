# Filter and multicast

Only paid orders go further, and a paid order goes to two places at once: the warehouse to pick it and invoicing
to bill it. The `filter` drops the unpaid order; the `multicast` sends the same message to both routes.

## What you will see

```text
INFO ... filter-and-multicast.camel.yaml:16 : Order ORD-1001 received, status paid
INFO ... filter-and-multicast.camel.yaml:40 : Warehouse: pick 2 line(s) for ORD-1001
INFO ... filter-and-multicast.camel.yaml:49 : Invoicing: bill customer C-482 for ORD-1001
INFO ... filter-and-multicast.camel.yaml:16 : Order ORD-1002 received, status paid
...
INFO ... filter-and-multicast.camel.yaml:16 : Order ORD-1003 received, status pending
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

The three orders in `orders/` are read once each (`noop: true` leaves the files in place), so the run
can be repeated.

## How it works

- `filter` with `${body[status]} == 'paid'` lets paid orders into its steps; the pending order is logged as
  received and goes no further, which is the difference from `choice`: a filter has no otherwise.
- `multicast` sends a copy of the message to each `to:` in turn, `direct:warehouse` then `direct:invoicing`.
- The two `direct` routes are the two departments; each logs its part of the work.

## Build it step by step

1. A `file` route on `orders` that unmarshals each order and logs its id and status.
2. Add a `filter` on the status and log inside it; the pending order disappears from the inner log.
3. Add `direct:warehouse` and `direct:invoicing` routes and a `multicast` to both inside the filter.
4. Add `parallelProcessing: true` to the multicast and see both departments log at the same moment.

## Try changing

- Add a third department, `direct:analytics`, that logs the order total.
- Use `recipientList` with a header listing the departments instead of a fixed `multicast`.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/filter-and-multicast.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/filter-and-multicast.citrus.it.yaml
```

The test starts the route and verifies that the warehouse and invoicing logs appear for the paid orders.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
