# CAMEL-23383: JFR Runtime Instrumentation for Exchanges, Processors, and Endpoints

- **JIRA:** https://issues.apache.org/jira/browse/CAMEL-23383
- **Module:** `components/camel-jfr` (extended, no new module)
- **Target version:** 4.22.0
- **Status:** Design approved, ready for implementation planning

## 1. Problem and Goal

Today `camel-jfr` only instruments the **startup/shutdown lifecycle**: a single JFR
event type (`FlightRecorderStartupStep`) captures each startup step. There is no
per-exchange, per-processor, or per-endpoint runtime visibility.

The goal is to emit JFR events during **message routing** so operators can attach a
Flight Recorder (via `jcmd`, JMC, or a startup recording) to a running Camel
application and see, natively in the JVM's own profiler:

- how long each exchange spends end-to-end,
- how long each route takes for a given exchange,
- how long each individual processor takes,
- how long each endpoint send takes,
- when exchanges fail (with exception type/message),
- when redeliveries happen.

Claus Ibsen's guidance on the ticket: keep it in the existing module and "just
capture all events." This design captures all six proposed event types.

## 2. Non-Goals

- No new Maven module and no new runtime dependency (`jdk.jfr.*` is JDK-native).
- No change to core (`camel-core`, `camel-base-engine`, `camel-api`) SPI signatures.
- Not a replacement for `camel-telemetry` / OpenTelemetry tracing. JFR events are a
  low-overhead, JVM-local complement, not a distributed tracing system.
- No sampling/rate-limiting logic in v1 (JFR's own recording settings and the
  `shouldCommit()` gate already bound overhead). Can be revisited later.

## 3. Background: why three instrumentation layers

Camel exposes three distinct runtime interception mechanisms, each of which sees a
different slice of execution. No single one produces all six events accurately, so
we source each event from the layer that measures it correctly. This mirrors the
proven `camel-telemetry` `Tracer` architecture, which uses the same three layers.

| Layer | What it sees | Why we need it |
|---|---|---|
| `RoutePolicy` (via `RoutePolicyFactory`) | `onExchangeBegin`/`onExchangeDone` bracketing each **route** an exchange enters | Only layer giving accurate **per-route** timing when an exchange traverses multiple/composed routes (`direct:`, `to`), and clean nested bracketing |
| `InterceptStrategy` | wraps **every processor** at route-build time | Only layer that can time an **individual processor**; must be installed **before routes are built** |
| `EventNotifier` | discrete `CamelEvent`s: exchange created/completed/failed, sending/sent, redelivery | Source for **whole-exchange** timing, **endpoint send** timing, **failure**, and **redelivery** without wrapping every processor |

### Why not fewer layers

- **EventNotifier alone** cannot produce `CamelProcessorEvent` at all (no per-processor
  signal), and can only *approximate* per-route timing: for a single exchange composed
  across several routes it cannot cleanly separate each route's elapsed time. Hence
  `RoutePolicy` for routes and `InterceptStrategy` for processors.
- Keeping both a per-route event **and** a whole-exchange event is intentional
  (explicit user decision): they answer different questions ("which route is slow"
  vs. "how long did this message take end to end").

## 4. JFR Events

Package: `org.apache.camel.runtime.jfr` (new). Every event `extends jdk.jfr.Event`,
uses `@Category({"Camel Application", "Runtime"})`, `@StackTrace(false)`, a distinct
`@Name` (so JMC lists them separately from the startup step), a `@Label`, and a
`@Description`. Timed events use JFR-native `begin()`/`commit()` so the JVM computes
duration; instant events just `commit()`.

| Event class | `@Name` | Kind | Source layer | Fields |
|---|---|---|---|---|
| `CamelRouteEvent` | `org.apache.camel.route` | timed | RoutePolicy | `routeId`, `exchangeId`, `failed` |
| `CamelProcessorEvent` | `org.apache.camel.processor` | timed | InterceptStrategy | `exchangeId`, `routeId`, `processorId`, `processorType`, `failed` |
| `CamelExchangeEvent` | `org.apache.camel.exchange` | timed | EventNotifier (created→completed) | `exchangeId`, `routeId`, `failed` |
| `CamelExchangeSendEvent` | `org.apache.camel.exchange.send` | timed | EventNotifier (sending→sent) | `exchangeId`, `endpointUri`, `failed` |
| `CamelExchangeFailedEvent` | `org.apache.camel.exchange.failed` | instant | EventNotifier (failed) | `exchangeId`, `routeId`, `exceptionType`, `exceptionMessage` |
| `CamelRedeliveryEvent` | `org.apache.camel.redelivery` | instant | EventNotifier (redelivery) | `exchangeId`, `routeId`, `attempt`, `maxAttempts` |

Notes:
- `endpointUri` is sanitized via `URISupport.sanitizeUri(...)` so credentials in the
  URI are not written into the recording (avoids information disclosure; consistent
  with how Camel logs endpoint URIs).
- `exceptionMessage` is truncated to a bounded length to keep events small.
- Field population happens **inside** the `isEnabled()` guard (see §6) so we do not
  build strings when nothing will be recorded.

## 5. Collectors and duration mechanics

Three collector classes, each in `org.apache.camel.runtime.jfr`:

### 5.1 `CamelJfrRoutePolicyFactory` + `CamelJfrRoutePolicy`
- Factory implements `RoutePolicyFactory`; `createRoutePolicy(...)` returns a
  `CamelJfrRoutePolicy` (extends `RoutePolicySupport`).
- `onExchangeBegin(route, exchange)`: if `CamelRouteEvent` enabled, create it, set
  `routeId`/`exchangeId`, call `begin()`, and **push** it onto a per-exchange stack
  held in an Exchange property (`Deque<CamelRouteEvent>`), because a single exchange
  can enter nested routes.
- `onExchangeDone(route, exchange)`: **pop** the matching event, set `failed` from
  `exchange.isFailed()`, call `end()` then `commit()`.
- Rationale for the stack: identical to `Tracer`'s `SpanStorageManager` per-exchange
  span stack — it is the established pattern for correct nesting.

### 5.2 `CamelJfrInterceptStrategy`
- Implements `InterceptStrategy.wrapProcessorInPipeline(...)`, returning a
  `DelegateAsyncProcessor` that:
  - on `process(exchange, callback)`: if `CamelProcessorEvent` enabled, create+`begin()`
    an event keyed to this processor (id/type from the `NamedNode` definition), and
    on async completion set `failed` and `end()`/`commit()`.
  - The wrapper must preserve async semantics (return the async processor, propagate
    the callback) exactly like `TraceProcessorsInterceptStrategy`.
- Processor identity (`processorId`, `processorType`) is captured **once at wrap time**
  from the `NamedNode`/definition, not per-exchange, to keep the hot path cheap.

### 5.3 `CamelJfrEventNotifier`
- Extends `EventNotifierSupport`; `isEnabled(event)` accepts only the six relevant
  `CamelEvent` types.
- **Timed pairs** stash the begin-side JFR event object in an Exchange property so the
  end-side callback can finish it:
  - `ExchangeCreatedEvent` → new `CamelExchangeEvent`, `begin()`, store in property.
    `ExchangeCompletedEvent`/`ExchangeFailedEvent` → retrieve, set `failed`, `commit()`.
  - `ExchangeSendingEvent` → new `CamelExchangeSendEvent`, `begin()`, push onto a
    per-exchange **send stack** property (sends can nest). `ExchangeSentEvent` → pop,
    set `failed`, `commit()`.
- **Instant events**:
  - `ExchangeFailedEvent` → also emit an instant `CamelExchangeFailedEvent`
    (exceptionType/message) in addition to closing the timed exchange event.
  - `ExchangeRedeliveryEvent` → emit `CamelRedeliveryEvent` (attempt/maxAttempts).

## 6. Overhead control

Every hook follows this shape so the idle cost is a boolean check plus (at most) one
short-lived TLAB allocation:

```java
CamelRouteEvent event = new CamelRouteEvent();
if (event.isEnabled()) {          // false when no recording wants this type
    event.routeId = route.getRouteId();
    event.exchangeId = exchange.getExchangeId();
    event.begin();
    push(exchange, event);
}
```

- `isEnabled()` is a JIT-friendly near no-op when no recording is active or the type
  is disabled in the active recording's settings.
- String building (`sanitizeUri`, message truncation) happens only inside the guard.
- No per-exchange allocation when instrumentation is globally disabled (see §7).

## 7. Activation and bootstrap

### 7.1 Auto-on with opt-out
When `camel-jfr` is on the classpath, runtime instrumentation is **on by default**.
It can be disabled with a single flag:

```
camel.jfr.runtimeEnabled=false
```

Rationale: JFR events are cheap when unrecorded (§6), and the whole value proposition
is "attach a recorder to a running app with no restart." Requiring explicit opt-in
would defeat that. The opt-out flag exists for users who want zero instrumentation
overhead or who see event-name collisions in their own JFR tooling.

### 7.2 Bootstrap point
`FlightRecorderStartupStepRecorder` is already auto-discovered for **every**
`CamelContext` via `ResolverHelper.resolveService(this, StartupStepRecorder.FACTORY,
...)` in `AbstractCamelContext`, and is started **before** routes are built. That
early, per-context, classpath-driven lifecycle is exactly what we need (the
`InterceptStrategy` must be registered before route build), and it requires **no core
change**. This is preferred over the `camel-main` tracer-config path, which is not
auto-on and requires `camel-main`.

Changes to `FlightRecorderStartupStepRecorder`:
1. Implement `CamelContextAware` so `ResolverHelper` injects the `CamelContext`.
2. In `doStart()`, **independently of the existing `isRecording()` startup-recording
   block**, register the three collectors unless opted out:
   ```java
   if (runtimeEnabled) {   // camel.jfr.runtimeEnabled, default true
       FlightRecorder.register(CamelRouteEvent.class);      // + the other 5
       camelContext.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
       camelContext.getCamelContextExtension().addInterceptStrategy(new CamelJfrInterceptStrategy());
       camelContext.getManagementStrategy().addEventNotifier(new CamelJfrEventNotifier());
   }
   ```
   Registration is **decoupled** from whether a JFR recording is currently active:
   collectors only *emit*; capture depends on any recording started later.
3. In `doStop()`, `FlightRecorder.unregister(...)` the six runtime event classes.

> **Implementation assumption to verify:** that `ResolverHelper.resolveService`
> invokes `CamelContextAware.trySetCamelContext` on the resolved recorder, and that
> `doStart()` runs with the context available and before route building. If the
> context is not injected this way, fall back to reading it in an `onCamelContext
> Initializing` hook. This must be confirmed in the first implementation step.

### 7.3 Reading the opt-out flag
`runtimeEnabled` is resolved from the recorder's own configuration. The recorder
already carries startup-recorder options; the flag is read from CamelContext
properties (`camel.jfr.runtimeEnabled`) with a default of `true`. Exact wiring
(property component vs. a setter populated by `camel-main`) is an implementation
detail to settle in step 1; the default-true, single-flag contract is fixed.

## 8. Testing strategy

JUnit 5, package-private classes and methods, AssertJ assertions, Awaitility for any
async wait (no `Thread.sleep`), per project conventions.

Core technique: run a route under an **in-process** `jdk.jfr.Recording`, stop it, dump
to a temp `.jfr`, then read events back with `jdk.jfr.consumer.RecordingFile` and
assert on emitted types and fields.

Test cases:
1. **Processor timing** — a route with a known slow processor emits at least one
   `CamelProcessorEvent` with matching `processorId`/`processorType`.
2. **Per-route nesting** — a `direct:`-composed exchange (route A calls route B) emits
   two `CamelRouteEvent`s with the correct `routeId`s and non-zero durations.
3. **Whole-exchange event** — one `CamelExchangeEvent` per top-level exchange with a
   duration covering the whole route.
4. **Endpoint send** — a `to("mock:...")` emits `CamelExchangeSendEvent` with the
   sanitized `endpointUri`.
5. **Failure** — a route that throws emits `CamelExchangeFailedEvent` (correct
   `exceptionType`/message) and the timed events carry `failed = true`.
6. **Redelivery** — a route with an error handler configured for N redeliveries emits
   `CamelRedeliveryEvent`s with increasing `attempt` up to `maxAttempts`.
7. **Opt-out** — with `camel.jfr.runtimeEnabled=false`, a recording captures **none**
   of the six runtime event types (collectors not registered).
8. **URI sanitization** — an endpoint URI containing credentials is recorded with the
   secret masked.

## 9. Documentation

- **`components/camel-jfr/src/main/docs/jfr.adoc`**: add a "Runtime instrumentation"
  section: the six event types and their fields (table), the `camel.jfr.runtimeEnabled`
  opt-out, and a worked `jcmd <pid> JFR.start`/JMC example showing the events, plus a
  note that no restart is needed.
- **`docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_22.adoc`**: add an
  entry documenting the **new default-on behavior** — placing `camel-jfr` on the
  classpath now also installs runtime instrumentation (a RoutePolicyFactory,
  InterceptStrategy, and EventNotifier) — and how to disable it. Per project rules the
  upgrade-guide entry lives on `main` and describes migration impact only.

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Context not injected into the recorder via `CamelContextAware` | Verify in step 1; fall back to an init hook if needed (§7.2). |
| Per-processor wrapper breaks async processing | Mirror `TraceProcessorsInterceptStrategy` exactly; assert routes still complete in tests. |
| Overhead when unrecorded | `isEnabled()` guard on every hook; no string work outside the guard (§6). |
| Event-name collision with the existing startup step | Distinct `@Name`s per event (§4). |
| Secrets in endpoint URIs written to `.jfr` | `URISupport.sanitizeUri` before setting the field (§4). |

## 11. Deliverables (for the implementation plan)

1. Six `jdk.jfr.Event` subclasses in `org.apache.camel.runtime.jfr`.
2. `CamelJfrRoutePolicyFactory` + `CamelJfrRoutePolicy`.
3. `CamelJfrInterceptStrategy`.
4. `CamelJfrEventNotifier`.
5. `FlightRecorderStartupStepRecorder` made `CamelContextAware`; collector
   registration in `doStart()`, unregistration in `doStop()`; `runtimeEnabled` flag.
6. Tests (§8).
7. Docs: `jfr.adoc` runtime section + upgrade-guide entry (§9).
