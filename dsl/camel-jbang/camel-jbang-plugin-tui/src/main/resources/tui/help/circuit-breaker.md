# Circuit Breakers

Circuit breakers protect your integration from cascading failures. When a
downstream service starts failing, the circuit breaker stops sending
requests to it, giving it time to recover instead of overwhelming it
with doomed requests.

## How It Works

Imagine calling a REST API that is down. Without a circuit breaker,
every message would wait for a connection timeout, creating a backlog.
With a circuit breaker, after detecting enough failures, requests are
immediately rejected — fast-failing instead of slow-failing.

## State Machine

```
CLOSED ──(failures exceed threshold)──> OPEN
   ^                                      |
   |                              (wait timeout)
   |                                      v
   +────(success in trial)──── HALF_OPEN
```

- **CLOSED** (normal operation): All requests flow through. Failures
  are counted in a sliding window. When the failure rate exceeds the
  configured threshold (e.g., 50%), the circuit trips to OPEN.

- **OPEN** (circuit tripped): All requests are immediately rejected
  with a fallback response — no call is made to the failing service.
  After a wait duration (e.g., 20 seconds), the circuit moves to
  HALF_OPEN to test if the service has recovered.

- **HALF_OPEN** (testing recovery): A limited number of trial requests
  are allowed through. If they succeed, the circuit closes and normal
  operation resumes. If they fail, the circuit opens again for another
  wait period.

## Table Columns

- **ROUTE** — Route containing this circuit breaker
- **ID** — Processor ID of the circuit breaker node in the route
- **COMPONENT** — Implementation library: `resilience4j` (most common) or `fault-tolerance` (MicroProfile)
- **STATE** — Current breaker state: `CLOSED` (green, normal), `OPEN` (red, rejecting), or `HALF_OPEN` (yellow, testing)
- **WINDOW** — Number of calls in the current sliding window used to calculate the failure rate
- **INFLIGHT** — Calls currently in progress inside the circuit breaker
- **SUCCESS** — Total number of successful calls
- **FAIL** — Total number of failed calls (exceptions thrown by the protected code)
- **RATE%** — Current failure rate percentage in the sliding window. When this exceeds the configured threshold, the circuit trips to OPEN
- **REJECT** — Calls rejected because the circuit is OPEN. These calls never reach the downstream service — they fail fast with a fallback
- **FALLBACK** — Calls answered by the `onFallback`, whatever the cause: a failed call, a timeout or a rejected call. This is how many callers got a degraded answer
- **TIMEOUT** — Calls that hit the configured timeout. The breaker counts them as failures, so this tells a slow service apart from a broken one
- **SINCE-LAST** — Time since the last circuit breaker activity, shown as up to two values separated by `/`: success/failed (e.g., `3s/1m14s`). Values are omitted when there is no activity of that type

## Example Screen

```
 ROUTE        ID                COMPONENT     STATE   WINDOW  INFLIGHT  SUCCESS  FAIL  RATE%  REJECT  FALLBACK  TIMEOUT  SINCE-LAST
 checkout     payment-breaker   resilience4j  CLOSED      10         1      450     5     1%                 5        2  1s/3m12s
 stock-check  supplier-breaker  resilience4j  OPEN         4         0        3     2    50%       9       11           11s/9s
```

Zero counts are shown blank, so a healthy breaker is a quiet line.

`payment-breaker` is healthy with a 1% failure rate. Its five failures
were all answered by the fallback, and two of them were timeouts: the
payment provider is occasionally slow, not broken.

`supplier-breaker` has tripped open: two real failures in a window of
four is a 50% failure rate. Since it opened, 9 calls were rejected
without calling the supplier, and the fallback answered all 11 callers.
REJECT and FALLBACK together show what the breaker saved: the callers
got an answer, and the supplier got a rest. The circuit stays open
until the wait timeout expires, then lets a probe call through in
HALF_OPEN state.

## Reading the counters

- FAIL counts calls the protected code failed, timeouts included.
  TIMEOUT tells how many of those were timeouts.
- REJECT counts calls the OPEN breaker refused. They are not in FAIL.
- FALLBACK counts every call the `onFallback` answered, so it is
  normally FAIL plus REJECT plus the bulkhead rejections, as long as
  the breaker has a fallback. Without a fallback it stays at zero and
  the exceptions reach the route instead.
- Calls rejected by a full bulkhead are not in REJECT; they appear as
  `bulkhead` in the detail view.

## Detail View

The bottom panel shows when a circuit breaker is selected:

- **Failure rate gauge**: Visual bar from 0% to 100%, colored green
  (low risk), yellow (approaching threshold), or red (above threshold)
- **Sparkline chart**: Mirrored view showing successful calls (green,
  upward) vs failed calls (red, downward) over time
- **Metrics**: Detailed counts for total, fail, inflight, reject,
  fallback, timeout, bulkhead (calls rejected because the bulkhead
  was full) and timing statistics (mean/min/max processing time)

## Configuration Tips

Common Resilience4j settings you can tune in your route:

- `minimumNumberOfCalls` — Minimum calls before failure rate is
  calculated (default: 100). Lower this for faster detection
- `waitDurationInOpenState` — How long to wait before testing
  recovery (default: 60s)
- `failureRateThreshold` — Percentage that triggers the circuit
  to open (default: 50%)

## Keys

- `Up/Down` — select circuit breaker
- `s` — cycle sort column
- `S` — reverse sort order
