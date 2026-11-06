# Chaos

camel-chaos turns on faults in a running integration during development:
"fail 25% of the calls to the stock service", "stall the poller route
for 20 seconds". This screen shows the rules, how often they fire, what
their faults do to the exchanges, and the verdict of the report.

Chaos is on with `camel run --chaos` (profile `dev`). Add, change and
remove rules with the Camel CLI:

```
camel cmd chaos-add myapp --rule='endpoint=http:stock* error status=503 rate=0.25 until=10m'
camel cmd chaos-remove myapp --id=r1
camel cmd chaos-clear myapp --reset
```

While a rule injects faults, the title bar shows a badge on every
screen (`🎲 chaos 3 rules · FAIL`), so a failure made on purpose is not
debugged as a real one.

## The verdict

The top line has the verdict of the report: PASS, or FAIL with the
reason, such as an expectation that is unmet or messages lost at the
dead letter channel. It also shows how many messages were lost, the
expectations (met, unmet, pending), the faults of all rules in the last
minute, and the seed of the random draws.

## Rules

- **ID** — The id of the rule (r1, r2, ...), with its version when it was changed
- **STATE** — `active`, `waiting` (it starts later, `from=`) or `ended`
- **TARGET** — What the rule matches: `endpoint=`, `component=`, `route=` or `step=`
- **FAULT** — What it does, with its parameters, such as `error status=503`
- **RATE** — The share of the matches it fired at, next to its own: `24% of 25%`, `20% of 1 in 5`, `16% of 5s in 30s`; for a flapping route the round
- **FIRED** — How many faults it fired
- **LAST** — How long ago it last fired
- **FAULTS/S** — Its faults per second over the last minute, as a sparkline
- **EFFECT** — What its faults did to the exchanges: `✓` completed, `↩` recovered by redelivery, `⇩` handled by the error handler (such as the dead letter channel), `✗` failed, `…` in flight
- **NOTE** — When it ends or starts, its bursts, what a stall or lifecycle rule does to its route, and the faults it skipped

## The selected rule

The panel under the table shows the selected rule in full: its faults
per second, rate, the effect as a bar with the counts in words, its
expectation with the time left, why it skipped faults, and its recent
faults: when, the fault, where (the endpoint or step), the route and the
exchange.

## Routes

The counts of the report per route: completed (DONE), failed (FAIL),
failed and kept by the consumer to read again (KEPT), handled by the
error handler (HAND) and the redeliveries (REDEL).
