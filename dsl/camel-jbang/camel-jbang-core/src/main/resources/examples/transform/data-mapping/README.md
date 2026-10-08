# Data mapping

The case every integration has: the shop's order mapped field by field to the courier's shipment format, which has
different names, a different structure, and values the order does not carry. The mapping is a Groovy script.

## What you will see

```text
INFO ... data-mapping.camel.yaml:27 : Shipment for the courier: {"shipmentRef":"SHIP-1001","recipient":{"customerNo":"C-482","countryCode":"DK"},"parcels":[{"article":"CAMEL-TSHIRT","pieces":2},{"article":"CAMEL-MUG","pieces":1}],"totalPieces":3,"service":"domestic","createdAt":"2026-09-18"}
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- The order in `order.json` is parsed with `unmarshal` and the `json` data format, so the script works on a map,
  not on text.
- `setBody` with a `groovy` expression runs `shipment-mapping.groovy`: `resource:file:` loads the script from the
  file next to the route. The script's last expression, a map with the courier's field names, becomes the body.
- `marshal` with `json` writes the shipment out. Renamed fields, a nested `recipient`, one `parcels` entry per
  line, a computed `totalPieces`, a `service` chosen from the country, and a `createdAt` the order never had.

## Build it step by step

1. A one-shot timer route that sets the body from `order.json`, unmarshals it with `json`, and logs
   `${body[orderId]}`.
2. Add a `setBody` with an inline `groovy` expression returning `[shipmentRef: body.orderId]` and log the body.
3. Move the expression into `shipment-mapping.groovy` and load it with `resource:file:`.
4. Grow the map: the recipient, the parcels with `collect`, the total with `sum`.
5. Add `marshal` with `json` and log the shipment.

## Try changing

- Map the country to the courier's service codes with a Groovy map: `[DK: 'DOM', SE: 'NORDIC'][order.country]`.
- Do the same mapping with a Java bean instead of Groovy; the `routes` quick-start shows a bean.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/data-mapping.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/data-mapping.citrus.it.yaml
```

The test starts the route and verifies that the shipment is logged with the courier's fields.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
