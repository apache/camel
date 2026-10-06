# Aggregator

The reverse of `order-lines`: the warehouse reports each picked line on its own, and the aggregator collects the
lines of one order back into a shipment. Lines are correlated by the order id, and a shipment completes when all
the lines the order had are in.

## What you will see

```text
INFO ... aggregator.camel.yaml:26 : Picked 2 x CAMEL-TSHIRT for ORD-1001
INFO ... aggregator.camel.yaml:26 : Picked 1 x CAMEL-MUG for ORD-1001
INFO ... aggregator.camel.yaml:26 : Picked 3 x CAMEL-MUG for ORD-1002
...
INFO ... aggregator.camel.yaml:51 : Shipment for ORD-1001 complete: [{"sku":"CAMEL-TSHIRT","qty":2,"price":19.95},{"sku":"CAMEL-MUG","qty":1,"price":9.5}]
INFO ... aggregator.camel.yaml:51 : Shipment for ORD-1002 complete: [{"sku":"CAMEL-MUG","qty":3,"price":9.5}]
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

- The first route reads the orders, keeps the order id in a header, splits them into lines, and sends every
  line to `direct:shipment`, as a warehouse system would report picks one by one.
- The second route aggregates: `correlationExpression` `${header.orderId}` groups lines of the same order,
  `completionSizeExpression` `${header.CamelSplitSize}` says how many lines make the order complete, and the
  `GroupedBodyAggregationStrategy` collects the bodies into a list.
- When a shipment completes, the list of lines is written as JSON with `marshal` and logged.

## Build it step by step

1. Start from `order-lines` and send each line to `direct:shipment` instead of only logging it.
2. Add a route from `direct:shipment` with an `aggregate` correlating on `${header.orderId}` and a fixed
   `completionSize: 2`; only the two-line order completes.
3. Replace the fixed size by `completionSizeExpression` with `${header.CamelSplitSize}`.
4. Add `marshal` with `json` and log the shipment.

## Try changing

- Add `completionTimeout: 5000` so a shipment with a missing line still closes after five seconds.
- Sum the pieces instead of listing the lines: the `data-mapping` example shows the Groovy for it.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/aggregator.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/aggregator.citrus.it.yaml
```

The test starts the route and verifies that the shipments for the three orders complete.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
