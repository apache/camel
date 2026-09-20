# Circuit breaker

The shop checks stock at its supplier every second. The supplier goes down for nine calls. Without a breaker
every check would wait on a dead service; with one, the breaker opens after two failures, answers from the
fallback without calling the supplier, and tries again after five seconds until a call succeeds.

## What you will see

```text
INFO ... circuit-breaker.camel.yaml:30 : Stock check 1 (breaker CLOSED): CAMEL-MUG: 47 in stock at the supplier
INFO ... circuit-breaker.camel.yaml:30 : Stock check 2 (breaker CLOSED): CAMEL-MUG: 35 in stock at the supplier
INFO ... circuit-breaker.camel.yaml:30 : Stock check 3 (breaker CLOSED): CAMEL-MUG: 49 in stock at the supplier
INFO ... circuit-breaker.camel.yaml:30 : Stock check 4 (breaker CLOSED): no answer, using last known stock
INFO ... circuit-breaker.camel.yaml:30 : Stock check 5 (breaker OPEN): no answer, using last known stock
...
INFO ... circuit-breaker.camel.yaml:30 : Stock check 14 (breaker OPEN): no answer, using last known stock
INFO ... circuit-breaker.camel.yaml:30 : Stock check 15 (breaker CLOSED): CAMEL-MUG: 58 in stock at the supplier
```

## Install Camel CLI

<!-- see installation instructions in ../../install.adoc -->

## Run it

```shell
camel run *
```

Stop it with `ctrl` + `c`; the state logged on each line is the breaker's state after that call.

## How it works

- `circuitBreaker` wraps the call to `direct:supplier` with [Resilience4j](https://resilience4j.readme.io/):
  the breaker counts the last four calls (`slidingWindowSize`) and opens when half of them failed
  (`failureRateThreshold`), which happens on check 5.
- While open, the steps are not run at all; `onFallback` sets the body instead. After `waitDurationInOpenState`,
  five seconds, the breaker goes half open and lets one call through (`permittedNumberOfCallsInHalfOpenState`):
  check 10 still fails, so it opens for another five seconds; check 15 succeeds and closes it.
- The supplier is a second route with `noErrorHandler`, so its exception reaches the breaker; it is down while
  the timer counter is 4 to 12.
- `CamelCircuitBreakerState` is one of the exchange properties the breaker sets; `CamelCircuitBreakerResponseSuccessfulExecution`
  and `CamelCircuitBreakerResponseFromFallback` are the others.

## Build it step by step

1. A `timer` route that calls `direct:supplier` and logs the body; a supplier route that answers with a stock
   level. Add `includeMetadata: true` to the timer to get `CamelTimerCounter`.
2. Make the supplier throw for counters 4 to 12: nine stack traces in the log.
3. Wrap the call in `circuitBreaker` with `onFallback`: no more stack traces, but the supplier is still called
   nine times because the default window is 100 calls.
4. Add the `resilience4jConfiguration` shown here and log `CamelCircuitBreakerState` to watch it open and close.

## Try changing

- Set `waitDurationInOpenState` to 2000 and see the breaker try the supplier sooner.
- Add `timeoutEnabled: true` and `timeoutDuration: 500` and a `delay` of 1000 in the supplier: a slow supplier
  counts as a failure too.
- Remove `onFallback`: the exception reaches the route and the error handler instead.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/circuit-breaker.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/circuit-breaker.citrus.it.yaml
```

The test starts the route and verifies that the breaker opens and later closes again.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
