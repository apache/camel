# Health

Health checks verify that the integration and its dependencies are
functioning correctly. They are essential for container orchestration
platforms like Kubernetes, but also useful for local monitoring.

## Table Columns

- **GROUP** — Category of the health check: `camel` (core checks), `routes` (per-route health), `consumers` (consumer health)
- **NAME** — Specific check name. For route checks this is the route ID; for consumer checks it is the consumer endpoint
- **STATUS** — Health state: `UP` (green, healthy), `DOWN` (red, unhealthy), `UNKNOWN` (yellow, cannot determine)
- **KIND** — Check type: `R` = Readiness only, `L` = Liveness only, `R/L` = serves both purposes
- **MESSAGE** — Status details. Most useful when a check is DOWN — shows what went wrong (e.g., `Connection refused`, `Route stopped`)

## Example Screen

```
 GROUP    NAME              STATUS  KIND  MESSAGE
 camel    context           UP      R/L
 camel    route-controller  UP      R
 camel    security-policy   UP      R     security.status: clean
 routes   timer-to-log      UP      R
 routes   seda-consumer     UP      R
```

All checks UP means the integration is healthy and ready to receive
traffic. If any check shows DOWN, the MESSAGE column explains why.

## Readiness vs Liveness

These concepts come from Kubernetes but apply to any deployment:

- **Liveness** (`L`): Is the process alive and not stuck in a deadlock
  or infinite loop? If a liveness check fails, the platform should
  **restart** the process. Only fundamental checks use liveness —
  the `context` check verifies the CamelContext is still running.

- **Readiness** (`R`): Is the process ready to handle traffic? During
  startup, a process may be alive but not yet ready (e.g., still
  connecting to a database or loading routes). If readiness fails,
  the platform should **stop sending traffic** but not restart.

A check marked `R/L` serves both purposes. The `context` check is
typically both — if the CamelContext is down, the process should be
restarted and traffic should be rerouted.

## Built-in Health Checks

Camel includes several built-in checks:

- **context** — Is the CamelContext started and running?
- **route-controller** — Are all routes started successfully?
- **security-policy** — Are there any security policy violations?
- **consumers** — Are consumers connected and healthy?
- **routes** — Per-route health status

Components can also contribute their own health checks. For example,
a Kafka consumer checks its broker connectivity, and a database
component checks its connection pool.

## Kubernetes Integration

When deployed to Kubernetes, these health checks are exposed as
HTTP endpoints:

- `/observe/health/ready` — returns 200 if all readiness checks pass
- `/observe/health/live` — returns 200 if all liveness checks pass

Kubernetes uses these to decide when to send traffic to a pod and
when to restart it.

## Keys

- `Up/Down` — select health check
- `s` — cycle sort column
- `S` — reverse sort order
