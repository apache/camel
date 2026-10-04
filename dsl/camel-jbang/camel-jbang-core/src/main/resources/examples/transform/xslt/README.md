# XSLT

The supplier's XML order becomes the packing slip the warehouse prints, transformed by a stylesheet: one item per
line and the total pieces to pick.

## What you will see

```text
INFO ... xslt.camel.yaml:16 : Packing slip:
<?xml version="1.0" encoding="UTF-8"?><packingSlip order="ORD-1001" customer="C-482" country="DK">
    <item sku="CAMEL-TSHIRT" pieces="2"/>
    <item sku="CAMEL-MUG" pieces="1"/>
    <pieces>3</pieces>
</packingSlip>
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- The `file` consumer reads `inbox/supplier-order.xml` and leaves it there with `noop: true`.
- `to: xslt` applies `packing-slip.xsl`: the template matches `/order`, writes one `item` per `line` with the
  attributes renamed, and sums the quantities with `sum(line/@qty)`.
- `${prettyBody}` in the log indents the XML.

## Build it step by step

1. A `file` route on `inbox` that logs the XML.
2. Add `to: xslt` with a stylesheet that copies the order id into a `packingSlip` root element.
3. Add a `for-each` over the lines writing one `item` each.
4. Add the `pieces` total with `sum()`.

## Try changing

- Produce HTML instead: change `xsl:output` to `html` and write a table.
- Use XSLT 2.0 or 3.0 functions such as `current-dateTime()`: switch to the `xslt-saxon` component.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/xslt.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/xslt.citrus.it.yaml
```

The test starts the route and verifies that the packing slip is logged.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
