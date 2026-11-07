# Chaos

camel-chaos turns on faults in a running integration during development:
"fail 25% of the calls to the stock service", "stall the poller route
for 20 seconds". This screen shows the rules, how often they fire, what
their faults do to the exchanges, and the verdict of the report.

Chaos is on with `camel run --chaos` (profile `dev`). Add, change and
remove rules on this screen (see Keys below), or with the Camel CLI:

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
- **TARGET** — What the rule matches: `endpoint=`, `component=`, `route=` or `step=`, with `in=` when it is narrowed to the sends and steps of a route
- **FAULT** — What it does, in a few words: `error status 503`, `error ConnectException`, `delay 500ms..1s`, `stop 5s ×3 every 20s` (the rule in full is the title of the panel under the table)
- **RATE** — The share of the matches it fired at, next to its own: `24% of 25%`, `20% of 1 in 5`, `16% of 5s in 30s`; for a flapping route the round
- **FIRED** — How many faults it fired
- **LAST** — How long ago it last fired
- **FAULTS/S** — Its faults per second over the last minute, as a sparkline
- **EFFECT** — What its faults did to the exchanges: `✓` completed, `↩` recovered by redelivery, `⇩` handled by the error handler (such as the dead letter channel), `✗` failed, `◷` in flight. A rule at the polls of a consumer faults no exchange: its effect is the polls it failed or skipped (`39 polls failed`)
- **NOTE** — When it ends or starts, its bursts, what a stall or lifecycle rule does to its route, and the faults it skipped

## The selected rule

The panel under the table shows the selected rule in full: its faults
per second, rate, the effect as a bar with the counts in words, its
expectation with the time left, why it skipped faults, and its recent
faults: when, the fault, where (the endpoint or step), the route and the
exchange. An exchange faulted again on its redeliveries is shown once, with
how many times (`×3`). The routes of its recent faults are listed with
their mean time, p95 and p99, and throughput: what a delay costs, as the
exchanges it delayed still complete.

## Routes

The counts of the report per route: completed (DONE), failed (FAIL),
failed and kept by the consumer to read again (KEPT), handled by the
error handler (HAND) and the redeliveries (REDEL).

## Keys

- **a** — Add a rule: pick what should go wrong from the known rules of
  camel-chaos (such as "Downstream returns an error", "Did the work, then
  failed", "The route flaps"). Each says what it does, what it can
  uncover, when not to use it, and what to watch while it runs. Then fill
  in its values: the endpoints,
  routes and steps of the integration are picked with Left and Right, and
  the rule is shown as it is built. The last entry, "Write the rule
  yourself", types it as one line, such as
  `endpoint=kafka:orders* error rate=0.1 until=5m`
- **w** — What to try: the integration suggests rules per route from what
  its routes do (an HTTP call without a circuit breaker, a dead letter
  channel, a hand-off over seda, an input from outside), the most worth
  trying first, each with why. Enter types the selected rule in, to change
  it or add it after you confirm
- **e** — Change the selected rule: the line starts with the rule as it is
- **x** — Remove the selected rule
- **c** — Clear: remove all rules (the report keeps what they did)
- **R** — A new experiment: no rules and a new report, with the same seed
- **Shift+F8** — Explain with AI: the AI panel opens with a question about
  the selected rule and what it did (its rate, its effect, the
  expectation, the routes its faults were in, the recent faults). The AI
  says what the fault simulates and how the routes coped, and suggests how
  to make them cope better, changing the routes only when you agree.
  Press Enter to send the question, or change it first
- **d** — Describe with AI: say the chaos you want in your own words, such
  as "the order route fails 5% of the time, or when the country is Spain".
  Enter sends it to the AI panel with the routes, the sends and the rules
  of the integration. The AI writes the rules, checks them with
  `tui_chaos_check` (each rule in plain words, and what it would hit),
  shows them to you, and adds them with `tui_chaos_add` (or changes a rule
  with `tui_chaos_change`, which keeps its id and gives it a new version),
  asking you to confirm each one. It does not change the routes. The AI
  panel has these tools only while chaos is on in the integration

Each asks to confirm with **y**, showing the rule. The integration checks
the rule as `camel.chaos.rules` is checked: a rule that is not valid comes
back with the reason, and a rule that matches nothing in the integration
(often a typo) with a warning. Press **e** in that dialog to change the
rule.

## Chaos from where you look

Press **!** on the Endpoint tab, the Consumers tab or a node of the
Diagram to open the known rules for what is selected, with it filled in:

- a `to` (or an endpoint the routes send to): the faults of that endpoint,
  such as an HTTP status for an HTTP endpoint, and the faults of the step;
- the `from` of a route, a consumer, or an endpoint a route consumes: the
  route stalls, flaps or gets corrupt messages, and the polls fail when it
  polls.

Only when chaos is on in the integration.

## The 🎲 marks

An exchange that a chaos rule faulted is marked with the dice and the id
of the rule (`🎲r1`) in the exchanges of a second on the Timeline tab and
on the Errors tab (before the exception), and its detail on the Timeline,
Errors and Inspect tabs says "injected by chaos rule r1" with the rule. So a failure
made on purpose is not debugged as a real one. An error knows its rule
from the `CamelChaosRule` exchange property; for the other tabs the screen
remembers the exchanges of the recent faults of the chaos report while it
runs.
