# Content-based router

Three orders from three countries. A `choice` routes each by its country: the Danish order to local delivery,
EU orders to shipping without customs, everything else to export with a customs declaration.

## What you will see

```text
INFO ... content-based-router.camel.yaml:22 : Order ORD-1001 from DK: local delivery from the Copenhagen warehouse
INFO ... content-based-router.camel.yaml:28 : Order ORD-1002 from DE: EU shipping, no customs
INFO ... content-based-router.camel.yaml:32 : Order ORD-1003 from US: export, customs declaration needed
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

- The `file` consumer reads the three orders from `orders/` in name order; `unmarshal` with `json` parses each,
  so `${body[country]}` reads a field.
- `choice` tests the country: `== 'DK'` first, then `in 'DE,SE,NL,FR'`, the simple language's list test, and
  `otherwise` takes the rest. The first matching `when` wins.
- Each branch only logs here; in a real shop each `to:` a different route or system.

## Build it step by step

1. A `file` route on `orders` that unmarshals each order and logs `${body[orderId]}` and `${body[country]}`.
2. Add a `choice` with one `when` for `DK` and an `otherwise`, each logging a different line.
3. Add a second `when` for the EU countries with `in 'DE,SE,NL,FR'`.
4. Add a fourth order file for a Swedish customer and see it take the EU branch.

## Try changing

- Route on the order value instead: `${body[lines].size()} > 2` for big orders.
- Replace the logs with `to: direct:...` routes, one per branch, as `filter-and-multicast` does.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/content-based-router.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/content-based-router.citrus.it.yaml
```

The test starts the route and verifies that each of the three orders is logged from its branch.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
