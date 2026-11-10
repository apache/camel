# Chaos

The shop takes an order every second, charges it at the payment service over HTTP and hands it to shipping. Nothing
in the routes fails on its own: you break things on purpose while it runs, with chaos rules, and watch whether the
retries, the dead letter channel and the hand-off to shipping do what they should. A scenario file then replays a bad
half minute at the payment service, and its verdict is the exit code, so it can gate a build.

Chaos is new in Camel 4.24 ([camel-chaos](https://camel.apache.org/components/next/others/chaos.html)), and it is for
development only: it is on with profile `dev`, the default of `camel run`, and never with profile `prod`.

## What you will see

Without rules, every order is charged and shipped:

```text
INFO ... chaos.camel.yaml:35 : Order ORD-1 charged: {"payment": "PAY-30456"}
INFO ... chaos.camel.yaml:45 : Order ORD-1 shipped
```

With a rule that makes the payment service answer 503 to one call in four, the error handler retries, and the order
is charged on the second or third attempt; the few that fail three times in a row are parked:

```text
WARN ... DeadLetterChannel : Failed delivery for (...) at orders[to1] chaos.camel.yaml:33. On delivery attempt: 0 caught: org.apache.camel.http.base.HttpOperationFailedException: HTTP operation failed invoking http://localhost:8080/payment with statusCode: 503
INFO ... chaos.camel.yaml:35 : Order ORD-45 charged: {"payment": "PAY-27475"}
...
WARN ... DeadLetterChannel : Failed delivery for (...) at orders[to1] chaos.camel.yaml:33. On delivery attempt: 2 caught: org.apache.camel.http.base.HttpOperationFailedException: HTTP operation failed invoking http://localhost:8080/payment with statusCode: 503
INFO ... parked.camel.yaml:12 : Order ORD-47 parked for manual review: HTTP operation failed invoking http://localhost:8080/payment with statusCode: 503
```

and the scenario ends with its verdict:

```text
Chaos verdict: PASS (3 expectations met)
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install. This example needs
Camel 4.24 or newer.

## Run it

```shell
camel run * --chaos
```

`--chaos` adds camel-chaos to the integration. Leave it running, and give the commands below in another terminal.
Stop it with `ctrl` + `c`: when rules were added, the chaos report is logged as the application stops.

## How it works

- `chaos.camel.yaml` is the shop. Route `orders` creates an order every second (`order.period`), charges it with a
  `POST` to `http://localhost:8080/payment` and sends it to `seda:shipping`, a hand-off to route `shipping` that does
  not wait for it. The `errorHandler` of the file, a dead letter channel to `direct:parked` with two retries half a
  second apart, applies to both routes. Route `payment` is the payment service, a `platform-http` route of the same
  application, so the example needs nothing else to run; it has `noErrorHandler`, so a failure there is the caller's
  to deal with.
- `parked.camel.yaml` writes an order that could not be charged or shipped to `parked/<order id>.json`. It is in a
  file of its own, with an error handler of its own, as in [Error handling](../error-handling/).
- `refunds.camel.yaml` pays refunds back at the payment service. They arrive on the Kafka topic `refunds`; the shop
  needs no broker, as `camel.jbang.stub=kafka` in `application.properties` stubs Kafka, and a refund is sent with
  `camel cmd send --endpoint=kafka:refunds --body='{"refundId": "R-1"}'`. The route has only the default error
  handler, on purpose: see [Let there be chaos](#let-there-be-chaos).
- `test/chaos/payment-outage.chaos` is a scenario: chaos rules in a file, one per line, with a seed, so the same
  experiment runs again.
- A chaos rule is one line: what it targets (an `endpoint=`, a `route=`, a `component=` or a `step=`), the fault
  (`error`, `delay`, `drop`, `duplicate`, `corrupt`, `stall`), how often (`rate=`) and for how long (`until=`), and
  optionally what must still hold (`expect=`). A rule ends by itself, at the latest after 10 minutes.

## Break it step by step

1. Ask the integration what is worth trying. Chaos reads the routes and suggests rules, the most worth trying first:

   ```shell
   camel cmd chaos-suggest
   ```

   ```text
   Chaos rules to try, the most worth trying first:

   1. route orders: Returns an error (HTTP status) (errors)
      an HTTP call to http://localhost:8080/payment without a circuit breaker: what does the route do when the service answers 503?
      camel cmd chaos-add --rule='endpoint=http://localhost:8080/payment error status=503 rate=0.25 until=10m'

   2. route orders: The route after a hand-off fails (lost)
      route orders hands the message to route shipping over seda and does not wait: when shipping fails, is the message lost?
      camel cmd chaos-add --rule='route=shipping error rate=0.2 until=2m expect=orders->shipping:lost<=0 within=3m'
   ...
   ```

2. Make the payment service answer 503 to one call in four, the first suggestion:

   ```shell
   camel cmd chaos-add --rule='endpoint=http://localhost:8080/payment error status=503 rate=0.25 until=10m'
   ```

   ```text
   Added chaos rule r1: fail 25% of the sends to http://localhost:8080/payment with HTTP status 503, for 10 minutes
   ```

   The log shows the retries, and once in a while an order parked in `parked/`. `camel get chaos` shows how the rule
   is doing: its observed rate, and the effect of its faults (`60 recovered, 4 handled`: recovered by a retry, or
   handled by the dead letter channel). Before you add a rule, `chaos-add --dry-run` says what it would hit, without
   adding it.

3. Take the payment service down for 10 seconds. A suspended `platform-http` route answers 503, so the HTTP client
   retries once by itself, then the error handler twice, and the orders of those seconds are parked:

   ```shell
   camel cmd chaos-add --rule='route=payment stall duration=10s'
   ```

4. Fail route `shipping` one message in five, with an expectation that no order charged is lost on the way:

   ```shell
   camel cmd chaos-add --rule='route=shipping error rate=0.2 until=1m expect=orders->shipping:lost<=0 within=90s'
   ```

   The dead letter channel of the file covers `shipping` too: a retry ships the order, or else it is parked, so
   nothing is lost; when the expectation is decided, `camel get chaos --report` says `met, 0 lost`.

5. Now the last safety net: fail the dead letter channel too, while the payment service is down:

   ```shell
   camel cmd chaos-add --rule='endpoint=http://localhost:8080/payment error status=503 until=10s' --rule='endpoint=direct:parked error at=dlq until=10s'
   ```

   The orders of those seconds are neither charged nor parked. The log has only a WARN for each
   (`New exception occurred during processing by the DeadLetterChannel[direct:parked]`), and the report counts them
   as lost, with the verdict FAIL:

   ```text
   chaos (pid 58473): FAIL
     lost: 9
     FAIL: rule r5 failed or dropped 9 sends of the dead letter channel, which handled them without delivering them: 9 lost
   ```

6. Read the report: per rule its matches, faults and effect, per route the exchanges completed, failed, handled by
   the error handler and redelivered, the recent faults, and per route which kinds of fault have been tried and which
   not yet:

   ```shell
   camel get chaos --report
   ```

   `camel cmd chaos-clear --reset` removes the rules and starts the report again, for the next experiment.

## Watch it in the Camel TUI

```shell
camel tui
```

- The chaos screen (`Chaos` under `0 More`) lists the rules with their live rate, faults per second and effect, and
  the report of the selected rule. `a` adds a rule from a form of known rules, `w` lists what to try (the
  suggestions), `e` changes, `x` removes and `c` clears a rule, `R` starts a new experiment, and `s` saves the rules
  as a scenario file.
- `4 Timeline` shows per second the faults of each rule, and what each route did: completed, recovered, handled,
  failed, and the seconds route `payment` was down.
- `t` on a rule asks the AI panel to make a test of it, with the seed, so it fails the same messages again.

## A scenario as a gate

`test/chaos/payment-outage.chaos` is a bad half minute at the payment service, with an expectation on each rule:

```text
seed=42

# one call in four fails for 20 seconds: the redeliveries recover them, and the shop still takes 20 orders
endpoint=http://localhost:8080/payment error status=503 rate=0.25 until=20s expect=orders:20 within=25s
# shipping fails one message in five for 15 seconds: its redeliveries still ship every order charged
route=shipping error rate=0.2 until=15s expect=orders->shipping:lost<=0 within=20s
# then the payment service is down for 10 seconds: every order is charged or parked, none fails
route=payment stall duration=10s from=+20s expect=orders:failed<=0 within=15s
```

Run the shop with it for 45 seconds:

```shell
camel run * --chaos=test/chaos/payment-outage.chaos --max-seconds=45
```

The report is logged when it stops, and the verdict is the exit code: 0 for PASS, 1 for FAIL, so a build can run it.
`seed=42` makes the rules fail the same calls each time. `camel cmd chaos-load test/chaos/payment-outage.chaos` loads the
scenario into a shop that runs already.

## Let there be chaos

Instead of choosing the rules yourself, let an AI explore for a time budget. It reads the suggestions, says what it
expects (a hypothesis), runs an experiment (rules with expectations, and messages for the route it is about), reads the
verdict and chooses the next one. It needs an LLM; a local model with Ollama is enough:

```shell
camel cmd chaos-explore --budget=10m
```

Route `refunds` has a gap on purpose: with only the default error handler, a refund the payment service answers with
503 fails, and is neither retried nor parked, while route `orders` recovers from the same fault. An exploration finds
it as a FAIL, and often the loss of step 5 as well: the orders lost when the dead letter channel fails too.

Each experiment ends with a verdict: PASS, FAIL, or INCONCLUSIVE when it cannot tell (the faults never fired, or the
route had no messages). The exploration saves its report, and a scenario of each experiment that failed, in
`test/chaos`, so `camel cmd chaos-load` replays it. In the Camel TUI, `l` on the chaos screen shows the exploration:
the budget as a strip of time with the experiments, and the selected one in detail.

## Try changing

- Add `endpoint=direct:parked error at=dlq until=10s from=+20s` to the scenario: the parked orders are lost, and the
  verdict is FAIL, exit code 1.
- Give route `refunds` an `errorHandler` with redeliveries, and let the AI explore again: its refunds experiments
  pass now.
- Set `maximumRedeliveries` to 0 in `chaos.camel.yaml` and run the scenario: the first rule parks the orders instead
  of recovering them.
- Wrap the payment call in a `circuitBreaker` (see [Circuit breaker](../circuit-breaker/)) and ask
  `camel cmd chaos-suggest` again: it suggests an outage, to see the breaker open and the fallback answer.
- Slow the payment service down, with an expectation on the time of an order:
  `endpoint=http://localhost:8080/payment delay duration=100ms..2s rate=0.5 until=1m expect=orders:meanTime<=1s within=90s`.
- Make the payment service charge twice: `endpoint=http://localhost:8080/payment duplicate rate=0.1` sends one call
  in ten again. Is your payment service idempotent?
- Fail only some orders: `when="${variable.orderId} ends with '7'"` on any rule fires only for the orders whose
  number ends with 7.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/chaos.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/chaos.citrus.it.yaml
```

The test starts the shop with the scenario and verifies that the payment service goes down and comes back, and that
orders are still shipped.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
