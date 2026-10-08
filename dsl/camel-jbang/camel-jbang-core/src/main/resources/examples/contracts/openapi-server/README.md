# OpenAPI server

The stock API, contract first. `stock-api.json` is the OpenAPI contract: three operations, their parameters,
request and response schemas and status codes. The REST DSL serves every operation in it, validates requests
against it and hands each operation to a `direct` route named after its `operationId`.

## What you will see

```text
$ curl localhost:8080/api/stock/CAMEL-MUG
{"sku":"CAMEL-MUG","qty":42}

$ curl -X POST -H 'Content-Type: application/json' -d '{"orderId": "ORD-1001", "qty": 2}' localhost:8080/api/stock/CAMEL-MUG/reserve
{"sku": "CAMEL-MUG", "reserved": 2, "remaining": 42}

$ curl -i -X POST -H 'Content-Type: application/json' -d '{"orderId": "ORD-1003", "qty": 1}' localhost:8080/api/stock/CAMEL-CAP/reserve
HTTP/1.1 409 Conflict
{"error": "only 0 CAMEL-CAP in stock, 1 wanted for ORD-1003"}

$ curl -i -X POST -H 'Content-Type: application/json' localhost:8080/api/stock/CAMEL-MUG/reserve
HTTP/1.1 400 Bad Request

INFO ... openapi-server.camel.yaml:123 : Reserved 2 x CAMEL-MUG for ORD-1001
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

The contract's `servers` entry gives the base path, `/api`, so the operations are under http://localhost:8080/api/stock
and the contract itself is served at http://localhost:8080/openapi. Call the
operations with `curl` as above from another terminal, or run the `openapi-client` example against it.
Stop it with `ctrl` + `c`.

## How it works

- `rest` with `openApi: specification: stock-api.json` is the whole API definition: paths, verbs and parameters
  come from the contract, not from the route file, and the base path `/api` from its `servers` entry, which
  must have a path. `restConfiguration` switches on `clientRequestValidation`,
  which rejects a request without the body or content type the contract requires with 400, and
  `apiContextPath: openapi` serves the contract.
- Each operation is a `direct` route named after its `operationId`: `listStock`, `getStock`, `reserveStock`.
  Path parameters arrive as headers, `sku` here; the request body is the message body.
- `lookup` is a helper route the two SKU operations share: it unmarshals the stock file and finds the SKU
  with Groovy, `body.find { it.sku == headers.sku }`, which leaves the item, or null, as the body.
- The contract check does not look at the values, so `reserveStock` runs `validate` on them and an
  `onException` turns the failed predicate into a 400 with an error body, the status the contract promises.
- `CamelHttpResponseCode` sets 404 and 409; the bodies are the JSON the contract's `Error` schema describes.

## Build it step by step

1. Write `stock-api.json` with only `listStock`, add the `rest` block and a `listStock` route that returns
   `resource:file:stock.json`; run it and open http://localhost:8080/openapi.
2. Add `getStock` to the contract and the route, with the `lookup` helper and the 404.
3. Add `reserveStock` with its request schema; POST without a body and see the 400 from the contract check.
4. Add `validate` and the `onException` for the values, then the 409 for short stock.

## Try changing

- Add a fourth operation to the contract and start the example: the missing route is reported at startup.
- Set `missingOperation: mock` in `restConfiguration` and put an example response under `examples/`: the
  operation is served from the example without a route.
- Change a response schema in the contract and see what the client example makes of it.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/openapi-server.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/openapi-server.citrus.it.yaml
```

The test starts the API and calls the three operations over HTTP, checking the 200, 409 and 400 answers.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
