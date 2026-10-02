# Error handling

Three orders go to the payment provider. One is charged at once. For one the provider does not answer, twice,
and the error handler retries until it does. One card is declined, which no retry will fix, so the order is
parked as a file for someone to look at.

## What you will see

```text
INFO ... error-handling.camel.yaml:40 : Payment for ORD-1001 charged, 2 line(s) to the warehouse
WARN ... DeadLetterChannel : Failed delivery for (...). On delivery attempt: 0 caught: java.net.ConnectException: payment provider did not answer
WARN ... DeadLetterChannel : Failed delivery for (...). On delivery attempt: 1 caught: java.net.ConnectException: payment provider did not answer
INFO ... error-handling.camel.yaml:40 : Payment for ORD-1002 charged, 1 line(s) to the warehouse
INFO ... error-handling.camel.yaml:21 : Payment for ORD-1003 declined: card declined
INFO ... error-handling.camel.yaml:75 : Order ORD-1003 parked for manual review: card declined
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

The three orders in `orders/` are read once each (`noop: true` leaves the files in place). The declined order
ends up in `parked/order-1003.json`.

## How it works

- `errorHandler` with `deadLetterChannel` is the default for every route in the file: a failure is retried twice
  with a second in between (`redeliveryPolicy`), each attempt logged at WARN, and when the retries are used up the
  message goes to the dead letter uri, `direct:parked`, and counts as handled.
- `onException` for `IllegalStateException`, the declined card, overrides that for one exception type: no
  retries, `handled` so the file consumer is done with the order, and its own steps log and park it.
- The payment provider is its own route with `noErrorHandler`, so its exceptions reach the caller and the retry
  repeats the whole `to: direct:charge` call, as it would for a remote service. Camel retries the step that
  failed, and a step that always throws would always fail; the provider answers on the third attempt because it
  reads `CamelRedeliveryCounter`.
- The parked route can use `${exception.message}` because the error handler keeps the exception on the exchange.

## Build it step by step

1. A `file` route on `orders` that unmarshals each order, calls `direct:charge` and logs the payment; a second
   route from `direct:charge` that throws for ORD-1003. Without any error handler the failure is logged with a
   stack trace and the order is dropped.
2. Add `errorHandler` with `deadLetterChannel` to `direct:parked` and a route that logs there. The stack
   trace is gone and the order is parked, after three attempts a second apart.
3. Add `noErrorHandler` to the provider route and make it throw `ConnectException` for ORD-1002 unless
   `CamelRedeliveryCounter` is 2: two warnings, then charged.
4. Add the `onException` for the declined card with no retries.

## Try changing

- Raise `maximumRedeliveries` to 5 with `backOffMultiplier: 2` and `useExponentialBackOff: true` and watch the
  delays grow.
- Replace `direct:parked` with a file endpoint straight in `deadLetterUri` and drop the parked route.
- Make the provider fail for every order and see all three parked.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/error-handling.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/error-handling.citrus.it.yaml
```

The test starts the route and verifies the charged, declined and parked lines.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
