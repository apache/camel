# Dynamic routing

Where an order goes is computed from the order itself: a recipient list sends it to the warehouse of its country
and to the billing of its status. Billing fails over to a backup invoice service when the primary one is down, and
the US warehouse takes at most one order per second.

## What you will see

```text
INFO ... dynamic-routing.camel.yaml:16 : Order ORD-1001 for DK, paid
INFO ... dynamic-routing.camel.yaml:30 : Warehouse DK picks ORD-1001
INFO ... dynamic-routing.camel.yaml:110 : Invoice for ORD-1001 via the backup service
...
INFO ... dynamic-routing.camel.yaml:16 : Order ORD-1003 for US, pending
INFO ... dynamic-routing.camel.yaml:56 : Warehouse US picks ORD-1003
INFO ... dynamic-routing.camel.yaml:86 : No invoice for ORD-1003 until it is paid
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Stop the example with `ctrl` + `c`.

## How it works

- `recipientList` computes the endpoints for each order with Simple:
  `direct:warehouse-${body[country]},direct:billing-${body[status]}`. ORD-1001 goes to `direct:warehouse-DK` and
  `direct:billing-paid`, ORD-1003 to `direct:warehouse-US` and `direct:billing-pending`. Each endpoint is a route.
- `billing-paid` sends the order through a `failoverLoadBalancer`: the primary invoice service first, and the backup
  when the primary fails with an `IllegalStateException`. `inheritErrorHandler: false` makes it fail over at once,
  without the route's error handler logging the failure first.
- `invoice-primary` is down: it throws the exception, and `noErrorHandler` hands the failure straight back to the
  load balancer.
- `throttle` lets at most one order per second into the US warehouse (`timePeriodMillis: 1000`); an order over the
  limit waits.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A file route on `orders` that unmarshals each order and logs its country and status.
2. A recipient list to `direct:warehouse-<country>` and `direct:billing-<status>`, with a route for each.
3. Bill a paid order through a failover load balancer: a primary invoice service that fails, and a backup that
   takes over.
4. Throttle the US warehouse to one order per second.

## Try changing

- Copy `order-1003.json` to `order-1004.json` and `order-1005.json` with new order ids: the US warehouse picks
  them one second apart.
- Remove the `throwException` from `invoice-primary`: every invoice goes through the primary service.
- Add an order for a country without a warehouse route: the recipient list fails on the unknown endpoint, unless
  you set `ignoreInvalidEndpoints: true`.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/dynamic-routing.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/dynamic-routing.citrus.it.yaml
```

The test runs the routes and verifies the warehouses, the failover and the pending order.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
