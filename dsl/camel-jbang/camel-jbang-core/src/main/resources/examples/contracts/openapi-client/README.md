# OpenAPI client

The picking desk reserves the stock for every order line by calling the stock API by contract: `stock-api.json`
says which operations exist and how to call them, and the `rest-openapi` component turns an `operationId` into
the HTTP request. The server is the `openapi-server` example, running in another terminal.

## What you will see

```text
INFO ... openapi-client.camel.yaml:63 : ORD-1001: reserved 2 x CAMEL-TSHIRT, 120 left on the shelf
INFO ... openapi-client.camel.yaml:63 : ORD-1001: reserved 1 x CAMEL-MUG, 42 left on the shelf
INFO ... openapi-client.camel.yaml:63 : ORD-1002: reserved 3 x CAMEL-MUG, 42 left on the shelf
INFO ... openapi-client.camel.yaml:63 : ORD-1003: reserved 1 x CAMEL-TSHIRT, 120 left on the shelf
WARN ... openapi-client.camel.yaml:13 : ORD-1003: CAMEL-CAP not reserved, the stock API answered 409: {"error": "only 0 CAMEL-CAP in stock, 1 wanted for ORD-1003"}
INFO ... openapi-client.camel.yaml:63 : ORD-1003: reserved 2 x CAMEL-MUG, 42 left on the shelf
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

Start the server first, in the `openapi-server` directory:

```shell
camel run *
```

Then, in this directory:

```shell
camel run *
```

The three orders in `orders/` are read once each (`noop: true` leaves the files in place), so the run
can be repeated. `stock.api.url` in `application.properties` says where the server is.

## How it works

- `rest-openapi` with `specificationUri: stock-api.json` and `operationId: reserveStock` is the call: the
  component reads the verb, the path and the parameters from the contract, so the route never spells out
  `POST /api/stock/{sku}/reserve`. `host` is the server; `componentName: http` picks the HTTP client to use.
- The path parameter `sku` is taken from the header of the same name; the request body is the message body,
  built here as the JSON the contract's `Reservation` schema describes.
- Client request validation is on (`camel.component.rest-openapi.request-validation-enabled=true` in
  `application.properties`): a call without a value for a path parameter, a required header or query parameter,
  or a required body is refused before it is sent, with a message that names what is missing, instead of going out
  with `{sku}` in the path.
- The order id and SKU are also kept in variables for the log, since the response replaces the body
  and headers are sent on the wire, as the `http-client` example explains.
- A 409 or 400 from the server is an `HttpOperationFailedException`; the `onException` logs its status code
  and response body and continues with the next line.

## Build it step by step

1. A timer route that calls `rest-openapi` with `operationId: getStock` and a fixed `sku` header; log the body.
2. Read the orders, split the lines, and call `reserveStock` with the reservation as the body.
3. Keep the order id and SKU in variables and log the result.
4. Add the `onException` and see the cap's 409 handled instead of failing the order.

## Try changing

- Remove the `sku` header before the call: the validation refuses it with "Path parameter 'sku' is required but
  none found" before any HTTP call. Turn the validation off in `application.properties` and the request goes out
  with `{sku}` in the path and gets a 404.
- Point `stock.api.url` at a server that is not running and watch the connection error reach the error handler.
- Replace the contract's server with a public one, `https://petstore3.swagger.io/api/v3/openapi.json`, and
  call `getPetById`.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/openapi-client.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/openapi-client.citrus.it.yaml
```

The test starts the `openapi-server` example and this client, and verifies the reservations and the 409.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
