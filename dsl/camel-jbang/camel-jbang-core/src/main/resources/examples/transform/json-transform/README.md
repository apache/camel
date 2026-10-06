# JSON transform

The shop's order reshaped for the warehouse. jsonpath reads single values out of the order, jq builds the pick
list the warehouse wants: only the sku and the quantity of each line.

## What you will see

```text
INFO ... json-transform.camel.yaml:25 : Order ORD-1001 with 2 lines: {"orderId": "ORD-1001", "customer": "C-482", ...}
INFO ... json-transform.camel.yaml:32 : Pick list for the warehouse: {
  "order" : "ORD-1001",
  "country" : "DK",
  "pick" : [ { "sku" : "CAMEL-TSHIRT", "qty" : 2 }, { "sku" : "CAMEL-MUG", "qty" : 1 } ]
}
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- The order comes from `order.json`; `resource:file:` in a constant expression loads a file as the body.
- `jsonpath` expressions pick the order id and the number of lines into headers.
- `transform` with a `jq` expression builds a new document from the old one: `.orderId` becomes `order`, and
  `[.lines[] | {sku, qty}]` keeps two fields of every line.
- The Camel CLI adds the jq and jsonpath languages on its own.

## Build it step by step

1. A one-shot timer route that sets the body from `order.json` and logs it.
2. Read the order id into a header with `jsonpath: $.orderId` and log it.
3. Add the number of lines with `jsonpath: $.lines.length()`.
4. Add a `transform` step with a jq expression that keeps only the order id, and log the result.
5. Extend the jq expression with the country and the pick list of sku and qty per line.

## Try changing

- Add the total quantity: `total: ([.lines[].qty] | add)` in the jq expression.
- Filter lines with more than one piece: `[.lines[] | select(.qty > 1)]`.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/json-transform.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/json-transform.citrus.it.yaml
```

The test starts the route and verifies that the pick list is logged.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
