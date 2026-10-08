# HTTP client

Before an order goes to picking, every line is checked against the stock service over HTTP. The stock service
runs inside this same example, so there is nothing else to start: the first two routes are a trimmed copy of
`stock-api`, the third is the client.

## What you will see

```text
INFO ... http-client.camel.yaml:112 : ORD-1001: CAMEL-TSHIRT x 2, 120 in stock, ok
INFO ... http-client.camel.yaml:112 : ORD-1001: CAMEL-MUG x 1, 42 in stock, ok
INFO ... http-client.camel.yaml:112 : ORD-1002: CAMEL-MUG x 3, 42 in stock, ok
INFO ... http-client.camel.yaml:112 : ORD-1003: CAMEL-TSHIRT x 1, 120 in stock, ok
INFO ... http-client.camel.yaml:116 : ORD-1003: CAMEL-CAP x 1, only 0 in stock, back-order
INFO ... http-client.camel.yaml:112 : ORD-1003: CAMEL-MUG x 2, 42 in stock, ok
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

The three orders in `orders/` are read once each (`noop: true` leaves the files in place), so the run can be
repeated. The stock levels are in `stock.json`.

## How it works

- The client splits each order into lines, as `order-lines` does, and calls `toD` with
  `http://localhost:8080/stock/${exchangeProperty.sku}`: `toD` because the URI is built per message.
- The SKU, the quantity and the order id are kept in exchange properties, not headers: headers travel on the
  HTTP request, and a header named `sku` would collide with the `{sku}` path parameter on the server side.
  Properties stay in the route.
- The body is set to null before the call: a GET has no body, and the order line would otherwise be sent. The
  response replaces the body, which is why the line was saved in properties first.
- `throwExceptionOnFailure=false` turns a 404 into a normal response with `CamelHttpResponseCode` set, so the
  `choice` can log it instead of the error handler.

## Build it step by step

1. Start from `stock-api` and add a `file` route on `orders` that logs each order.
2. Split the lines and call `http://localhost:8080/stock/CAMEL-MUG` with a fixed SKU; log the response body.
3. Build the URI from the line with `toD` and a property; notice the body is gone after the call.
4. Add `throwExceptionOnFailure=false` and the `choice` on the response code and the quantity.

## Try changing

- Remove `throwExceptionOnFailure=false` and add a SKU that does not exist to an order: the 404 becomes an
  `HttpOperationFailedException` handled by the error handler.
- Call a public API instead, `https://api.github.com/repos/apache/camel` for example, and log `${body[stargazers_count]}`.
- Set `camel.server.port` to another port and update the URI to match.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/http-client.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/http-client.citrus.it.yaml
```

The test starts the routes and verifies the ok and back-order lines.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
