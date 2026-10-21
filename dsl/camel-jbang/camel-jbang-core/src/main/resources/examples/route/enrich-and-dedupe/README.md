# Enrich and deduplicate

The shop sometimes sends an order twice. An idempotent consumer accepts each order id once, each accepted order is
enriched with its customer, and a wire tap writes a copy to an audit trail without holding the order up.

## What you will see

```text
INFO ... enrich-and-dedupe.camel.yaml:19 : Order ORD-1001 received
INFO ... enrich-and-dedupe.camel.yaml:39 : Order ORD-1001 accepted
INFO ... enrich-and-dedupe.camel.yaml:60 : Order ORD-1001 for Anna Jensen (gold)
...
INFO ... enrich-and-dedupe.camel.yaml:19 : Order ORD-1001 received
INFO ... enrich-and-dedupe.camel.yaml:35 : Order ORD-1001 is a duplicate, skipped
INFO ... enrich-and-dedupe.camel.yaml:85 : Audited ORD-1001.json
...
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

The copies of the accepted orders land in `audit/`. Stop the example with `ctrl` + `c`.

## How it works

- `orders/` holds the three orders and `resent-order-1001.json`, the same ORD-1001 sent a second time.
  `customers/` holds a file per customer with the name and the tier.
- The `beans` entry declares `orderIds`, an in-memory idempotent repository: the set of order ids seen so far.
- `idempotentConsumer` computes the order id with Simple and checks it against the repository. With
  `skipDuplicate: false` a duplicate is not dropped but marked with the exchange property `CamelDuplicateMessage`,
  so the `choice` can log it.
- `pollEnrich` reads the customer's file. The file name is computed into a header first: the file consumer that
  `pollEnrich` uses evaluates `fileName` with the headers of the message, not its body. `variableReceive: customer`
  keeps the customer in a variable, so the order stays the body, and `unmarshal` with `variableSend` and
  `variableReceive` parses the variable in place.
- `wireTap` sends a copy of the order to the `audit` route and goes on at once: the audit never holds the order up.
  The `audit` route marshals the copy to JSON and writes `audit/<orderId>.json`.
- `application.properties` declares `audit/` as data (`camel.jbang.dataDir`), so `camel run --dev` does not reload
  when the audit writes a file.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A file route on `orders` that unmarshals each order and logs "Order ... received" (four files).
2. Accept each order id only once with an idempotent consumer and an in-memory repository, and log the resent
   order as a duplicate.
3. Read the customer's file from `customers/` with `pollEnrich` into a variable, and log the name and the tier.
4. Copy each accepted order to `audit/<orderId>.json` with a wire tap.

## Try changing

- `skipDuplicate: true`, the default: the duplicate is dropped without a word.
- Replace the in-memory repository with a file one,
  `org.apache.camel.support.processor.idempotent.FileIdempotentRepository` with a `fileStore` property: the ids
  then survive a restart, and on the next run every order is a duplicate.
- Add an order for a customer without a file: `pollEnrich` waits for its `timeout` and the variable stays empty.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/enrich-and-dedupe.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/enrich-and-dedupe.citrus.it.yaml
```

The test runs the routes and verifies the enrichment, the duplicate and the audit.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
