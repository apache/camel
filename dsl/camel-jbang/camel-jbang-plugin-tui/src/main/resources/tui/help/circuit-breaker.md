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
- **SINCE-LAST** — Time since the last circuit breaker activity, shown as up to two values separated by `/`: success/failed (e.g., `3s/1m14s`). Values are omitted when there is no activity of that type

## Example Screen

```
 ROUTE    ID            COMPONENT     STATE   WINDOW  INFLIGHT  SUCCESS  FAIL  RATE%  REJECT
 route1   circuitBrk1   resilience4j  CLOSED  10      0         450      5     1.0%   0
 route2   circuitBrk2   resilience4j  OPEN    10      0         100      8     80.0%  25
```

In this example, `route1` is healthy with a 1% failure rate. `route2`
has tripped open with an 80% failure rate — 25 calls have been rejected
since it opened. The circuit will stay open until the wait timeout
expires, then try a few test calls in HALF_OPEN state.

## Detail View

The bottom panel shows when a circuit breaker is selected:

- **Failure rate gauge**: Visual bar from 0% to 100%, colored green
  (low risk), yellow (approaching threshold), or red (above threshold)
- **Sparkline chart**: Mirrored view showing successful calls (green,
  upward) vs failed calls (red, downward) over time
- **Metrics**: Detailed counts for total, fail, inflight, reject,
  and timing statistics (mean/min/max processing time)

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
