# REST code first

The shop's order API written with the REST DSL. The routes declare each operation, the JSON of a new order is bound
to a Java record, and Camel builds the API description from the operations and serves it at `/openapi`.

## What you will see

```text
INFO ... rest-code-first.camel.yaml:101 : Order ORD-1004 created with 1 line(s)
```

and the answers of the API:

```shell
$ curl http://localhost:8080/orders/ORD-1002
{"orderId":"ORD-1002","customer":"C-207","country":"DE","status":"paid","lines":[{"sku":"CAMEL-MUG","qty":3,"price":9.5}]}

$ curl -i http://localhost:8080/orders/ORD-9999
HTTP/1.1 404 Not Found
{"error": "unknown order ORD-9999"}

$ curl -i -H 'Content-Type: application/json' -d '{"orderId": "ORD-1004", "customer": "C-482", "country": "DK", "status": "paid", "lines": [{"sku": "CAMEL-MUG", "qty": 1, "price": 9.5}]}' http://localhost:8080/orders
HTTP/1.1 201 Created
{"orderId":"ORD-1004","customer":"C-482","country":"DK","status":"paid","lines":[{"sku":"CAMEL-MUG","qty":1,"price":9.5}]}
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Then call the API as shown above. `curl http://localhost:8080/openapi` shows the API description.
Stop the example with `ctrl` + `c`.

## How it works

- `rest-code-first.camel.yaml` declares the API with the REST DSL: `GET /orders`, `GET /orders/{id}` and
  `POST /orders`, each with an id and a description, and each sent to a `direct` route.
- `restConfiguration` sets `bindingMode: json`. The answers are Java objects (a List, a Map, a record), and the
  binding writes them as JSON. For `POST /orders`, `type: camel.example.Order` makes the binding read the request
  JSON into the record.
- `Order.java` is the record, with a nested record per line. Simple reads its fields as `${body.orderId}`.
- An error answer, 404 or 400, is not bound (`skipBindingOnErrorCode` is on by default), so the route writes it as
  JSON text.
- `apiContextPath: openapi` serves the API description that Camel generates from the operations.
  `application.properties` adds `camel-openapi-java` for it, which `camel run` does not add by itself for a
  code-first API.
- `orders.json` holds the three orders of the shop.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A REST DSL `GET /orders` that answers the orders from `orders.json`.
2. `GET /orders/{id}`: the order with that id, or 404 with `{"error": "unknown order <id>"}`.
3. `POST /orders` with JSON binding to a Java record `camel.example.Order`: 400 when the order has no lines,
   otherwise log the order and answer 201.
4. Serve the API description at `/openapi`.

## Try changing

- `bindingMode: off`: the answers are no longer JSON, and the routes have to marshal them themselves.
- Add `PUT /orders/{id}` that changes the status of an order.
- Compare with the [openapi-server](../openapi-server/) example, which starts from the contract instead.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/rest-code-first.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/rest-code-first.citrus.it.yaml
```

The test calls each operation and checks the answers, and the API description.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
