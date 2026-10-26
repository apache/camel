# Order lines

An order is one message with several lines; the warehouse picks one line at a time. The splitter turns the order
into one message per line, and the order id travels along in a variable.

## What you will see

```text
INFO ... order-lines.camel.yaml:20 : Order ORD-1001 with 2 line(s)
INFO ... order-lines.camel.yaml:27 :   pick 2 x CAMEL-TSHIRT for ORD-1001
INFO ... order-lines.camel.yaml:27 :   pick 1 x CAMEL-MUG for ORD-1001
INFO ... order-lines.camel.yaml:29 : Order ORD-1001: all 2 line(s) sent to picking
INFO ... order-lines.camel.yaml:20 : Order ORD-1002 with 1 line(s)
...
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

- The order is parsed with `unmarshal` and `json`, so `${body[lines]}` is a list and `${body[lines].size()}` its
  length.
- `setVariable` keeps the order id before the split: every split message gets a copy of the variables, so each pick
  line knows its order. A variable, not a header: a header is sent on with the message to the next endpoint, and
  the order id is only for the route itself.
- `split` with `${body[lines]}` runs its steps once per line with that line as the body; `CamelSplitIndex` and
  `CamelSplitSize` are available inside if a count is needed.
- After the split the route continues with the original order, which logs the confirmation.

## Build it step by step

1. A `file` route on `orders` that unmarshals each order and logs the number of lines.
2. Add `split` over `${body[lines]}` and log `${body[sku]}` inside it.
3. Keep the order id in a variable before the split and use it in the pick line.
4. Log the confirmation after the split and notice the body is the whole order again.

## Try changing

- Send each line to a `direct:pick` route instead of logging; the `aggregator` example collects them again.
- Split in parallel with `parallelProcessing: true` and watch the pick lines interleave.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/order-lines.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/order-lines.citrus.it.yaml
```

The test starts the route and verifies the pick lines and the confirmation.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
