# XML to JSON

The supplier sends orders as XML, the shop works in JSON. The Jackson XML data format reads the XML into a map
and the Jackson JSON data format writes the map out: no mapping code, elements and attributes become fields.

## What you will see

```text
INFO ... xml-to-json.camel.yaml:13 : Supplier order as XML: <?xml version="1.0" encoding="UTF-8"?><order id="ORD-1001" country="DK">...
INFO ... xml-to-json.camel.yaml:20 : The same order as JSON: {"id":"ORD-1001","country":"DK","customer":"C-482","line":[{"sku":"CAMEL-TSHIRT","qty":"2","price":"19.95"},{"sku":"CAMEL-MUG","qty":"1","price":"9.50"}],"status":"paid"}
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- The `file` consumer reads `inbox/supplier-order.xml`; `noop: true` leaves the file where it is so the example
  can run again.
- `unmarshal` with `jacksonXml` turns the XML into a map: attributes and child elements become keys, the two
  `line` elements become a list.
- `marshal` with `json` writes that map as JSON. Values stay strings, because XML has no numbers; the
  `data-mapping` example shows how to shape and type the result.
- The way back, JSON to XML, needs a name for the root element, which the Jackson XML data format takes from a
  class; that comes with the bean examples later on the ladder.

## Build it step by step

1. A `file` route on `inbox` that logs the XML it reads.
2. Add `unmarshal` with `jacksonXml` and log the body: a map now.
3. Add `marshal` with `json` and log again: the same order as JSON.
4. Drop a second XML file into `inbox` while it runs and watch it get converted too.

## Try changing

- Add `prettyPrint: true` to the `json` data format for indented output.
- Replace `jacksonXml` by `xslt` when the JSON should have a different shape than the XML: the `xslt` example
  next door does that for the packing slip.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/xml-to-json.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/xml-to-json.citrus.it.yaml
```

The test starts the route and verifies that the order is logged as JSON.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
