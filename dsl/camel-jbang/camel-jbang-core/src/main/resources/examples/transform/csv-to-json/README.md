# CSV to JSON

The accountant drops a CSV of invoices in the inbox. Each line becomes one JSON invoice, logged and written to
the outbox as its own file.

## What you will see

```text
INFO ... csv-to-json.camel.yaml:29 : Invoice INV-2001: {"invoiceId":"INV-2001","orderId":"ORD-1001","customer":"C-482","amount":"49.40","currency":"EUR","dueDate":"2026-10-01"}
INFO ... csv-to-json.camel.yaml:29 : Invoice INV-2002: {"invoiceId":"INV-2002","orderId":"ORD-1002",...}
INFO ... csv-to-json.camel.yaml:29 : Invoice INV-2003: {"invoiceId":"INV-2003","orderId":"ORD-1003",...}

$ ls outbox
INV-2001.json INV-2002.json INV-2003.json
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- `unmarshal` with the `csv` data format reads `inbox/invoices.csv`; `captureHeaderRecord` takes the first line
  as the field names and `useMaps` makes each following line a map.
- `split` turns the list of maps into one message per invoice.
- Inside the split, the invoice id is kept in a header, `marshal` with `json` writes the map as JSON, and the
  `file` producer names the output file after the invoice.
- Values are strings, as in the CSV; the `data-mapping` example shows how to compute and type fields.

## Build it step by step

1. A `file` route on `inbox` that logs the CSV as text.
2. Add `unmarshal` with `csv` and log the body: a list of maps.
3. Add `split` over `${body}` and log each invoice.
4. Add `marshal` with `json` inside the split.
5. Write each invoice to `outbox`, named after its id.

## Try changing

- Aggregate the amounts per customer before writing: the `aggregator` example shows the EIP.
- Delete or move the CSV after reading by dropping `noop: true` and adding `move: done`.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/csv-to-json.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/csv-to-json.citrus.it.yaml
```

The test starts the route and verifies that the three invoices are logged as JSON.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
