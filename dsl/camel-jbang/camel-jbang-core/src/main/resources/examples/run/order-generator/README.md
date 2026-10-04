# Order generator

The shop's order feed. A timer creates an order every five seconds, a Java bean hands out the order
number, and the order is logged as JSON. Every later example on the ladder starts from this file and
this order shape.

## What you will see

```text
INFO ... order-generator.camel.yaml:23 : New order ORD-1001: {"orderId": "ORD-1001", "customer": "C-482", "country": "DK", "lines": [{"sku": "CAMEL-TSHIRT", "qty": 2, "price": 19.95}], "status": "paid"}
INFO ... order-generator.camel.yaml:23 : New order ORD-1002: {"orderId": "ORD-1002", "customer": "C-207", "country": "DK", "lines": [{"sku": "CAMEL-TSHIRT", "qty": 1, "price": 19.95}], "status": "paid"}
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Stop it with `ctrl` + `c`, or from another terminal with `camel stop order-generator`.

## How it works

- `order-generator.camel.yaml` is the route: a `timer` fires every `order.period` milliseconds, a
  `setHeader` asks the `orderNumber` bean for the next number with a method expression, a `setBody`
  builds the order as JSON with the simple language, and a `log` prints it.
- `OrderNumber.java` is a plain Java class next to the route. `beans.yaml` declares it as the bean
  `orderNumber` and sets its first number from a property.
- `application.properties` holds the period and the first order number; change them without touching
  the route.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A timer route that logs "New order" every five seconds.
2. Set a header `orderId` to a constant `ORD-1001` and log "New order ${header.orderId}".
3. Add a Java class `OrderNumber` with a method `next()` that returns `ORD-` and a counter, declare it
   in `beans.yaml`, and set the header from the bean's method instead of the constant.
4. Set the body to a JSON order with the order id, a random customer and one line, and log the body.
5. Move the period and the first order number to `application.properties`.

## Try changing

- `order.period=1000` for an order every second.
- Add a second line to the order, or pick the sku at random from two products.
- Log only the order id and the number of lines: `${header.orderId} has ${body.lines.size()} line(s)`
  needs the body as an object; the `json-transform` example on the next rung shows how.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/order-generator.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/order-generator.citrus.it.yaml
```

The test starts the route and verifies the logged line.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
