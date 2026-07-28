# CAMEL-23383: JFR Runtime Instrumentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `camel-jfr` to emit runtime JFR events for exchanges, routes, processors, endpoint sends, failures, and redeliveries, so operators can attach a Flight Recorder to a running Camel app with no restart.

**Architecture:** Six `jdk.jfr.Event` subclasses are emitted by three collectors — an `EventNotifier` (exchange/send/failure/redelivery), a `RoutePolicy` (per-route timing), and an `InterceptStrategy` (per-processor timing) — mirroring the proven `camel-telemetry` `Tracer` three-layer design. The collectors are auto-installed (opt-out) by the already-auto-discovered `FlightRecorderStartupStepRecorder`, which is made `CamelContextAware` and registers the collectors from a `LifecycleStrategy.onContextInitializing` hook.

**Tech Stack:** JDK `jdk.jfr` / `jdk.jfr.consumer` (no new dependency), Camel SPI (`EventNotifierSupport`, `RoutePolicySupport`, `RoutePolicyFactory`, `InterceptStrategy`, `DelegateAsyncProcessor`, `LifecycleStrategySupport`, `CamelContextAware`), JUnit 5 + AssertJ.

## Global Constraints

- Target Camel version: **4.22.0-SNAPSHOT**; Java **17+**.
- **No new Maven dependency and no new module.** `jdk.jfr.*` is JDK-native.
- **No change to core SPI signatures** (`camel-api`, `camel-base-engine`). Only `components/camel-jfr` is modified.
- New production package: **`org.apache.camel.runtime.jfr`**.
- Every event class: `extends jdk.jfr.Event`, `@Category({"Camel Application", "Runtime"})`, `@StackTrace(false)`, a **distinct** `@Name`, plus `@Label` and `@Description`. Fields are `public` (JFR requires field access) with `@Label`.
- Field population (string building) happens **only inside** an `isEnabled()`/`shouldCommit()` guard.
- Endpoint URIs are sanitized with `org.apache.camel.util.URISupport.sanitizeUri(...)` before being stored on an event. Exception messages are truncated to 256 chars.
- Tests: JUnit 5, **package-private** classes and methods, **AssertJ** assertions (`assertThat`), **no `Thread.sleep`** (use Awaitility or `MockEndpoint` timed assertions), no FQCNs (use imports).
- Run formatting before every commit: `mvn -q formatter:format impsort:sort -pl components/camel-jfr`.
- Build a single module with: `mvnd clean install -pl components/camel-jfr -Dci.env.name=local` (from repo root). If `mvnd` is unavailable, use `mvn`.
- Work on branch **`CAMEL-23383`** (already created). Every commit ends with the trailer:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

## Confirmed codebase facts (do not re-investigate)

These were verified against source during planning; the plan depends on them:

- `DefaultInjector.newInstance(type, false)` **always** calls `CamelContextAware.trySetCamelContext(answer, camelContext)` (`core/camel-base-engine/.../DefaultInjector.java:79`). So a `CamelContextAware` recorder resolved via `ResolverHelper.resolveService` (`AbstractCamelContext.java:2442`) **is** injected with the context.
- The recorder's `start()` runs inside `AbstractCamelContext.doBuild()` at line 2450, **before** `setupManagement()` at line 2462. At `doStart()` time `getManagementStrategy()` returns **null**. Therefore collectors that touch management (the `EventNotifier`) **must not** be registered in `doStart()`; register from `LifecycleStrategy.onContextInitializing`, which fires after management setup and before routes are built.
- `InterceptStrategy` method is `wrapProcessorInInterceptors(CamelContext, NamedNode definition, Processor target, Processor nextTarget)`. Processor id/type: `definition.getId()` and `definition.getShortName()` (see `TraceProcessorsInterceptStrategy`).
- `RoutePolicyFactory.createRoutePolicy(CamelContext, String routeId, NamedNode route)`.
- `RoutePolicySupport.onExchangeBegin(Route, Exchange)` / `onExchangeDone(Route, Exchange)`.
- `EventNotifierSupport` implements `CamelContextAware`; override `boolean isEnabled(CamelEvent)` and `void notify(CamelEvent)`.
- Relevant `CamelEvent` subtypes (in `org.apache.camel.spi.CamelEvent`): `ExchangeCreatedEvent`, `ExchangeCompletedEvent`, `ExchangeFailedEvent` (also `FailureEvent` with `Throwable getCause()`), `ExchangeSendingEvent` (`Endpoint getEndpoint()`), `ExchangeSentEvent`, `ExchangeRedeliveryEvent` (`int getAttempt()`). All expose `getExchange()`.

---

## File Structure

**Production (all under `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/`):**
- `CamelRouteEvent.java`, `CamelProcessorEvent.java`, `CamelExchangeEvent.java`, `CamelExchangeSendEvent.java`, `CamelExchangeFailedEvent.java`, `CamelRedeliveryEvent.java` — the six JFR event types.
- `CamelJfrRoutePolicyFactory.java`, `CamelJfrRoutePolicy.java` — per-route timing.
- `CamelJfrInterceptStrategy.java` — per-processor timing.
- `CamelJfrEventNotifier.java` — exchange/send/failure/redelivery.
- `CamelJfrRuntimeInstrumentation.java` — `LifecycleStrategySupport` that registers/unregisters all collectors; holds the runtime-event class list and the `runtimeEnabled` decision.

**Modified:**
- `components/camel-jfr/src/main/java/org/apache/camel/startup/jfr/FlightRecorderStartupStepRecorder.java` — implement `CamelContextAware`; in `doStart()` add the `CamelJfrRuntimeInstrumentation` lifecycle strategy when not opted out.

**Test (`components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/`):**
- `JfrRecordingTestSupport.java` — base class: start an in-process `Recording`, run the route, stop, dump to temp `.jfr`, read events back with `RecordingFile`.
- `CamelJfrEventNotifierTest.java`, `CamelJfrRoutePolicyTest.java`, `CamelJfrInterceptStrategyTest.java`, `CamelJfrBootstrapTest.java` (auto-on + opt-out + URI sanitization).

**Docs:**
- `components/camel-jfr/src/main/docs/jfr.adoc` — new "Runtime instrumentation" section.
- `docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_22.adoc` — default-on behavior entry.

---

## Task 1: The six JFR event types + recording test harness

**Files:**
- Create: `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelRouteEvent.java` (and the other five event classes)
- Create: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/JfrRecordingTestSupport.java`
- Create: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelRuntimeEventsTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `CamelRouteEvent` — fields `String routeId, String exchangeId, boolean failed`; `@Name("org.apache.camel.route")`.
  - `CamelProcessorEvent` — `String exchangeId, routeId, processorId, processorType; boolean failed`; `@Name("org.apache.camel.processor")`.
  - `CamelExchangeEvent` — `String exchangeId, routeId; boolean failed`; `@Name("org.apache.camel.exchange")`.
  - `CamelExchangeSendEvent` — `String exchangeId, endpointUri; boolean failed`; `@Name("org.apache.camel.exchange.send")`.
  - `CamelExchangeFailedEvent` — `String exchangeId, routeId, exceptionType, exceptionMessage`; `@Name("org.apache.camel.exchange.failed")`.
  - `CamelRedeliveryEvent` — `String exchangeId, routeId; int attempt, maxAttempts`; `@Name("org.apache.camel.redelivery")`.
  - `JfrRecordingTestSupport` — `protected List<RecordedEvent> recordAndRun(Class<?>[] eventClasses, ThrowingRunnable action)` returning all events captured in a fresh recording.

- [ ] **Step 1: Write the failing test** (`CamelRuntimeEventsTest.java`)

```java
package org.apache.camel.runtime.jfr;

import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelRuntimeEventsTest extends JfrRecordingTestSupport {

    @Test
    void routeEventIsRecordedWithFields() throws Exception {
        List<RecordedEvent> events = recordAndRun(new Class<?>[] { CamelRouteEvent.class }, () -> {
            CamelRouteEvent event = new CamelRouteEvent();
            event.routeId = "route1";
            event.exchangeId = "ex1";
            event.failed = false;
            event.begin();
            event.commit();
        });

        assertThat(events)
                .filteredOn(e -> "org.apache.camel.route".equals(e.getEventType().getName()))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.getString("routeId")).isEqualTo("route1");
                    assertThat(e.getString("exchangeId")).isEqualTo("ex1");
                    assertThat(e.getBoolean("failed")).isFalse();
                });
    }
}
```

- [ ] **Step 2: Write `JfrRecordingTestSupport`** (needed for the test to compile)

```java
package org.apache.camel.runtime.jfr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

abstract class JfrRecordingTestSupport {

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }

    protected List<RecordedEvent> recordAndRun(Class<?>[] eventClasses, ThrowingRunnable action) throws Exception {
        for (Class<?> c : eventClasses) {
            FlightRecorder.register(c.asSubclass(jdk.jfr.Event.class));
        }
        Path file = Files.createTempFile("camel-runtime-test", ".jfr");
        try (Recording recording = new Recording()) {
            for (Class<?> c : eventClasses) {
                recording.enable(c.getName());
            }
            recording.start();
            action.run();
            recording.stop();
            recording.dump(file);

            List<RecordedEvent> events = new ArrayList<>();
            try (RecordingFile rf = new RecordingFile(file)) {
                while (rf.hasMoreEvents()) {
                    events.add(rf.readEvent());
                }
            }
            return events;
        } finally {
            for (Class<?> c : eventClasses) {
                FlightRecorder.unregister(c.asSubclass(jdk.jfr.Event.class));
            }
            Files.deleteIfExists(file);
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelRuntimeEventsTest -Dci.env.name=local`
Expected: FAIL — compilation error, `CamelRouteEvent` does not exist.

- [ ] **Step 4: Create the six event classes.** Example (`CamelRouteEvent.java`) — replicate the shape for all six with the fields/names from the Interfaces block above and the ASF license header (copy from `FlightRecorderStartupStep.java`):

```java
package org.apache.camel.runtime.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("org.apache.camel.route")
@Category({ "Camel Application", "Runtime" })
@Label("Camel Route")
@Description("Time spent by an exchange within a single Camel route")
@StackTrace(false)
public class CamelRouteEvent extends Event {

    @Label("Route Id")
    public String routeId;
    @Label("Exchange Id")
    public String exchangeId;
    @Label("Failed")
    public boolean failed;
}
```

The instant events (`CamelExchangeFailedEvent`, `CamelRedeliveryEvent`) are identical in structure but are only `commit()`-ed (no `begin()`); `CamelRedeliveryEvent` uses `int attempt` and `int maxAttempts` fields.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelRuntimeEventsTest -Dci.env.name=local`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
mvn -q formatter:format impsort:sort -pl components/camel-jfr
git add components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/ \
        components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/
git commit -m "CAMEL-23383: Add JFR runtime event types and recording test harness

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: `CamelJfrEventNotifier` (exchange, send, failure, redelivery)

**Files:**
- Create: `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrEventNotifier.java`
- Test: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrEventNotifierTest.java`

**Interfaces:**
- Consumes: the six event types from Task 1; `JfrRecordingTestSupport`.
- Produces: `class CamelJfrEventNotifier extends EventNotifierSupport` with `boolean isEnabled(CamelEvent)` (true only for the six handled subtypes) and `void notify(CamelEvent)`. Exchange property keys are package-private constants `PROP_EXCHANGE_EVENT = "CamelJfrExchangeEvent"` and `PROP_SEND_STACK = "CamelJfrSendStack"`.

- [ ] **Step 1: Write the failing test.** The test manually registers the notifier on a `DefaultCamelContext`, runs a route inside a recording, and asserts the timed exchange event, the send event (sanitized URI), the failed event, and the redelivery event.

```java
package org.apache.camel.runtime.jfr;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrEventNotifierTest extends JfrRecordingTestSupport {

    private static final Class<?>[] EVENTS = {
            CamelExchangeEvent.class, CamelExchangeSendEvent.class,
            CamelExchangeFailedEvent.class, CamelRedeliveryEvent.class
    };

    @Test
    void exchangeAndSendEventsAreEmitted() throws Exception {
        List<RecordedEvent> events = recordAndRun(EVENTS, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main").to("mock:out");
                    }
                });
                context.start();
                context.createProducerTemplate().sendBody("direct:start", "hello");
                // allow async notifier delivery to complete
                org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                        .untilAsserted(() -> assertThat(true).isTrue());
            }
        });

        assertThat(events).anySatisfy(e ->
                assertThat(e.getEventType().getName()).isEqualTo("org.apache.camel.exchange"));
        assertThat(events)
                .filteredOn(e -> "org.apache.camel.exchange.send".equals(e.getEventType().getName()))
                .anySatisfy(e -> assertThat(e.getString("endpointUri")).contains("mock://out"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrEventNotifierTest -Dci.env.name=local`
Expected: FAIL — `CamelJfrEventNotifier` does not exist.

- [ ] **Step 3: Implement `CamelJfrEventNotifier`.** Full implementation (ASF header omitted for brevity — copy from an existing file):

```java
package org.apache.camel.runtime.jfr;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeRedeliveryEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.URISupport;

public class CamelJfrEventNotifier extends EventNotifierSupport {

    static final String PROP_EXCHANGE_EVENT = "CamelJfrExchangeEvent";
    static final String PROP_SEND_STACK = "CamelJfrSendStack";

    private static final int MAX_MESSAGE_LEN = 256;

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof ExchangeCreatedEvent
                || event instanceof ExchangeCompletedEvent
                || event instanceof ExchangeFailedEvent
                || event instanceof ExchangeSendingEvent
                || event instanceof ExchangeSentEvent
                || event instanceof ExchangeRedeliveryEvent;
    }

    @Override
    public void notify(CamelEvent event) {
        if (event instanceof ExchangeCreatedEvent e) {
            onCreated(e.getExchange());
        } else if (event instanceof ExchangeCompletedEvent e) {
            onExchangeDone(e.getExchange());
        } else if (event instanceof ExchangeFailedEvent e) {
            onFailed(e);
            onExchangeDone(e.getExchange());
        } else if (event instanceof ExchangeSendingEvent e) {
            onSending(e);
        } else if (event instanceof ExchangeSentEvent e) {
            onSent(e.getExchange());
        } else if (event instanceof ExchangeRedeliveryEvent e) {
            onRedelivery(e);
        }
    }

    private void onCreated(Exchange exchange) {
        CamelExchangeEvent jfr = new CamelExchangeEvent();
        if (jfr.isEnabled()) {
            jfr.exchangeId = exchange.getExchangeId();
            jfr.routeId = exchange.getFromRouteId();
            jfr.begin();
            exchange.setProperty(PROP_EXCHANGE_EVENT, jfr);
        }
    }

    private void onExchangeDone(Exchange exchange) {
        CamelExchangeEvent jfr = exchange.getProperty(PROP_EXCHANGE_EVENT, CamelExchangeEvent.class);
        if (jfr != null) {
            jfr.failed = exchange.isFailed();
            jfr.end();
            jfr.commit();
            exchange.removeProperty(PROP_EXCHANGE_EVENT);
        }
    }

    @SuppressWarnings("unchecked")
    private void onSending(ExchangeSendingEvent event) {
        CamelExchangeSendEvent jfr = new CamelExchangeSendEvent();
        if (jfr.isEnabled()) {
            Exchange exchange = event.getExchange();
            jfr.exchangeId = exchange.getExchangeId();
            jfr.endpointUri = URISupport.sanitizeUri(event.getEndpoint().getEndpointUri());
            jfr.begin();
            Deque<CamelExchangeSendEvent> stack
                    = exchange.getProperty(PROP_SEND_STACK, Deque.class);
            if (stack == null) {
                stack = new ArrayDeque<>();
                exchange.setProperty(PROP_SEND_STACK, stack);
            }
            stack.push(jfr);
        }
    }

    @SuppressWarnings("unchecked")
    private void onSent(Exchange exchange) {
        Deque<CamelExchangeSendEvent> stack = exchange.getProperty(PROP_SEND_STACK, Deque.class);
        if (stack != null && !stack.isEmpty()) {
            CamelExchangeSendEvent jfr = stack.pop();
            jfr.failed = exchange.isFailed();
            jfr.end();
            jfr.commit();
        }
    }

    private void onFailed(ExchangeFailedEvent event) {
        CamelExchangeFailedEvent jfr = new CamelExchangeFailedEvent();
        if (jfr.isEnabled()) {
            Exchange exchange = event.getExchange();
            jfr.exchangeId = exchange.getExchangeId();
            jfr.routeId = exchange.getFromRouteId();
            Throwable cause = event.getCause();
            if (cause != null) {
                jfr.exceptionType = cause.getClass().getName();
                jfr.exceptionMessage = truncate(cause.getMessage());
            }
            jfr.commit();
        }
    }

    private void onRedelivery(ExchangeRedeliveryEvent event) {
        CamelRedeliveryEvent jfr = new CamelRedeliveryEvent();
        if (jfr.isEnabled()) {
            Exchange exchange = event.getExchange();
            jfr.exchangeId = exchange.getExchangeId();
            jfr.routeId = exchange.getFromRouteId();
            jfr.attempt = event.getAttempt();
            Integer max = exchange.getProperty(Exchange.REDELIVERY_MAX_COUNTER, Integer.class);
            jfr.maxAttempts = max != null ? max : 0;
            jfr.commit();
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_MESSAGE_LEN ? s : s.substring(0, MAX_MESSAGE_LEN);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrEventNotifierTest -Dci.env.name=local`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
mvn -q formatter:format impsort:sort -pl components/camel-jfr
git add components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrEventNotifier.java \
        components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrEventNotifierTest.java
git commit -m "CAMEL-23383: Add CamelJfrEventNotifier for exchange, send, failure and redelivery events

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: `CamelJfrRoutePolicyFactory` + `CamelJfrRoutePolicy` (per-route timing)

**Files:**
- Create: `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrRoutePolicyFactory.java`
- Create: `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrRoutePolicy.java`
- Test: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrRoutePolicyTest.java`

**Interfaces:**
- Consumes: `CamelRouteEvent` (Task 1); `JfrRecordingTestSupport`.
- Produces:
  - `class CamelJfrRoutePolicyFactory implements RoutePolicyFactory` → `createRoutePolicy(...)` returns a shared `CamelJfrRoutePolicy`.
  - `class CamelJfrRoutePolicy extends RoutePolicySupport` with a per-exchange `Deque<CamelRouteEvent>` held in property `PROP_ROUTE_STACK = "CamelJfrRouteStack"`.

- [ ] **Step 1: Write the failing test** — a `direct:`-composed route (A calls B) must yield two `CamelRouteEvent`s with the right route ids.

```java
package org.apache.camel.runtime.jfr;

import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrRoutePolicyTest extends JfrRecordingTestSupport {

    @Test
    void nestedRoutesEmitTwoRouteEvents() throws Exception {
        List<RecordedEvent> events = recordAndRun(new Class<?>[] { CamelRouteEvent.class }, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:a").routeId("a").to("direct:b");
                        from("direct:b").routeId("b").to("mock:out");
                    }
                });
                context.start();
                context.createProducerTemplate().sendBody("direct:a", "hi");
            }
        });

        assertThat(events)
                .filteredOn(e -> "org.apache.camel.route".equals(e.getEventType().getName()))
                .extracting(e -> e.getString("routeId"))
                .contains("a", "b");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrRoutePolicyTest -Dci.env.name=local`
Expected: FAIL — factory/policy do not exist.

- [ ] **Step 3: Implement the factory**

```java
package org.apache.camel.runtime.jfr;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;

public class CamelJfrRoutePolicyFactory implements RoutePolicyFactory {

    private final CamelJfrRoutePolicy policy = new CamelJfrRoutePolicy();

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        return policy;
    }
}
```

- [ ] **Step 4: Implement the policy** (per-exchange stack for nested routes)

```java
package org.apache.camel.runtime.jfr;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

public class CamelJfrRoutePolicy extends RoutePolicySupport {

    static final String PROP_ROUTE_STACK = "CamelJfrRouteStack";

    @Override
    @SuppressWarnings("unchecked")
    public void onExchangeBegin(Route route, Exchange exchange) {
        CamelRouteEvent event = new CamelRouteEvent();
        if (event.isEnabled()) {
            event.routeId = route.getRouteId();
            event.exchangeId = exchange.getExchangeId();
            event.begin();
            Deque<CamelRouteEvent> stack = exchange.getProperty(PROP_ROUTE_STACK, Deque.class);
            if (stack == null) {
                stack = new ArrayDeque<>();
                exchange.setProperty(PROP_ROUTE_STACK, stack);
            }
            stack.push(event);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onExchangeDone(Route route, Exchange exchange) {
        Deque<CamelRouteEvent> stack = exchange.getProperty(PROP_ROUTE_STACK, Deque.class);
        if (stack != null && !stack.isEmpty()) {
            CamelRouteEvent event = stack.pop();
            event.failed = exchange.isFailed();
            event.end();
            event.commit();
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrRoutePolicyTest -Dci.env.name=local`
Expected: PASS — both `a` and `b` route ids present.

- [ ] **Step 6: Format and commit**

```bash
mvn -q formatter:format impsort:sort -pl components/camel-jfr
git add components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrRoutePolicy*.java \
        components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrRoutePolicyTest.java
git commit -m "CAMEL-23383: Add CamelJfrRoutePolicy for per-route timing events

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: `CamelJfrInterceptStrategy` (per-processor timing)

**Files:**
- Create: `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrInterceptStrategy.java`
- Test: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrInterceptStrategyTest.java`

**Interfaces:**
- Consumes: `CamelProcessorEvent` (Task 1); `JfrRecordingTestSupport`.
- Produces: `class CamelJfrInterceptStrategy implements InterceptStrategy` whose `wrapProcessorInInterceptors(...)` returns a `DelegateAsyncProcessor` capturing `definition.getId()`/`getShortName()` **once at wrap time**.

- [ ] **Step 1: Write the failing test**

```java
package org.apache.camel.runtime.jfr;

import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrInterceptStrategyTest extends JfrRecordingTestSupport {

    @Test
    void processorEventsAreEmitted() throws Exception {
        List<RecordedEvent> events = recordAndRun(new Class<?>[] { CamelProcessorEvent.class }, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.getCamelContextExtension().addInterceptStrategy(new CamelJfrInterceptStrategy());
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main").setBody(constant("changed")).to("mock:out");
                    }
                });
                context.start();
                context.createProducerTemplate().sendBody("direct:start", "hi");
            }
        });

        assertThat(events)
                .filteredOn(e -> "org.apache.camel.processor".equals(e.getEventType().getName()))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getString("processorType")).isNotBlank());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrInterceptStrategyTest -Dci.env.name=local`
Expected: FAIL — strategy does not exist.

- [ ] **Step 3: Implement the strategy** (mirror `TraceProcessorsInterceptStrategy`'s async handling)

```java
package org.apache.camel.runtime.jfr;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class CamelJfrInterceptStrategy implements InterceptStrategy {

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext, NamedNode definition, Processor target, Processor nextTarget) {
        return new JfrProcessor(target, definition.getId(), definition.getShortName());
    }

    private static final class JfrProcessor extends DelegateAsyncProcessor {
        private final String processorId;
        private final String processorType;

        JfrProcessor(Processor target, String processorId, String processorType) {
            super(target);
            this.processorId = processorId;
            this.processorType = processorType;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            CamelProcessorEvent event = new CamelProcessorEvent();
            final boolean enabled = event.isEnabled();
            if (enabled) {
                event.exchangeId = exchange.getExchangeId();
                event.routeId = exchange.getFromRouteId();
                event.processorId = processorId;
                event.processorType = processorType;
                event.begin();
            }
            return processor.process(exchange, doneSync -> {
                try {
                    if (enabled) {
                        event.failed = exchange.isFailed();
                        event.end();
                        event.commit();
                    }
                } finally {
                    callback.done(doneSync);
                }
            });
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrInterceptStrategyTest -Dci.env.name=local`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
mvn -q formatter:format impsort:sort -pl components/camel-jfr
git add components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrInterceptStrategy.java \
        components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrInterceptStrategyTest.java
git commit -m "CAMEL-23383: Add CamelJfrInterceptStrategy for per-processor timing events

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Auto-on bootstrap + opt-out flag

**Files:**
- Create: `components/camel-jfr/src/main/java/org/apache/camel/runtime/jfr/CamelJfrRuntimeInstrumentation.java`
- Modify: `components/camel-jfr/src/main/java/org/apache/camel/startup/jfr/FlightRecorderStartupStepRecorder.java`
- Test: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrBootstrapTest.java`

**Interfaces:**
- Consumes: all three collectors (Tasks 2-4) and the six event classes (Task 1).
- Produces:
  - `class CamelJfrRuntimeInstrumentation extends LifecycleStrategySupport` — static field `RUNTIME_EVENTS = new Class[]{...six...}`; `onContextInitializing(CamelContext)` registers the six event classes with `FlightRecorder` and adds the three collectors; `onContextStopped(CamelContext)` unregisters the six event classes. Constructor takes the `CamelContext` for the collector wiring, or reads it from the passed context.
  - `FlightRecorderStartupStepRecorder implements CamelContextAware`; new `boolean runtimeEnabled` field (default `true`) with getter/setter; in `doStart()` it adds a `CamelJfrRuntimeInstrumentation` lifecycle strategy when `runtimeEnabled` and the context is present.

- [ ] **Step 1: Write the failing test** — auto-on (recorder present → events emitted) and opt-out (`runtimeEnabled=false` → none of the six types emitted). Because the recorder is auto-discovered from the classpath, a plain `DefaultCamelContext` already installs it.

```java
package org.apache.camel.runtime.jfr;

import java.util.List;
import java.util.Set;

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrBootstrapTest extends JfrRecordingTestSupport {

    private static final Class<?>[] ALL = {
            CamelRouteEvent.class, CamelProcessorEvent.class, CamelExchangeEvent.class,
            CamelExchangeSendEvent.class, CamelExchangeFailedEvent.class, CamelRedeliveryEvent.class
    };

    private static final Set<String> RUNTIME_NAMES = Set.of(
            "org.apache.camel.route", "org.apache.camel.processor", "org.apache.camel.exchange",
            "org.apache.camel.exchange.send", "org.apache.camel.exchange.failed", "org.apache.camel.redelivery");

    @Test
    void autoOnEmitsRuntimeEvents() throws Exception {
        List<RecordedEvent> events = recordAndRun(ALL, () -> runRoute(true));
        assertThat(events).extracting(e -> e.getEventType().getName()).anyMatch(RUNTIME_NAMES::contains);
    }

    @Test
    void optOutEmitsNoRuntimeEvents() throws Exception {
        List<RecordedEvent> events = recordAndRun(ALL, () -> runRoute(false));
        assertThat(events).extracting(e -> e.getEventType().getName()).noneMatch(RUNTIME_NAMES::contains);
    }

    private void runRoute(boolean runtimeEnabled) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRuntimeEnabled(runtimeEnabled);
            context.getCamelContextExtension().setStartupStepRecorder(recorder);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").routeId("main").to("mock:out");
                }
            });
            context.start();
            context.createProducerTemplate().sendBody("direct:start", "hi");
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrBootstrapTest -Dci.env.name=local`
Expected: FAIL — `setRuntimeEnabled` / `CamelJfrRuntimeInstrumentation` do not exist.

- [ ] **Step 3: Implement `CamelJfrRuntimeInstrumentation`**

```java
package org.apache.camel.runtime.jfr;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import org.apache.camel.CamelContext;
import org.apache.camel.support.LifecycleStrategySupport;

public class CamelJfrRuntimeInstrumentation extends LifecycleStrategySupport {

    static final Class<?>[] RUNTIME_EVENTS = {
            CamelRouteEvent.class, CamelProcessorEvent.class, CamelExchangeEvent.class,
            CamelExchangeSendEvent.class, CamelExchangeFailedEvent.class, CamelRedeliveryEvent.class
    };

    private boolean registered;

    @Override
    public void onContextInitializing(CamelContext context) {
        if (registered) {
            return;
        }
        for (Class<?> c : RUNTIME_EVENTS) {
            FlightRecorder.register(c.asSubclass(Event.class));
        }
        CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
        notifier.setCamelContext(context);
        context.getManagementStrategy().addEventNotifier(notifier);
        context.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
        context.getCamelContextExtension().addInterceptStrategy(new CamelJfrInterceptStrategy());
        registered = true;
    }

    @Override
    public void onContextStopped(CamelContext context) {
        if (!registered) {
            return;
        }
        for (Class<?> c : RUNTIME_EVENTS) {
            FlightRecorder.unregister(c.asSubclass(Event.class));
        }
        registered = false;
    }
}
```

- [ ] **Step 4: Modify `FlightRecorderStartupStepRecorder`** — add the `CamelContextAware` implementation, the flag, and the lifecycle-strategy registration in `doStart()` (leave the existing `isRecording()` block untouched):

```java
// class declaration:
public class FlightRecorderStartupStepRecorder extends DefaultStartupStepRecorder implements CamelContextAware {

    private CamelContext camelContext;
    private boolean runtimeEnabled = true;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    public void setRuntimeEnabled(boolean runtimeEnabled) {
        this.runtimeEnabled = runtimeEnabled;
    }

    // at the END of doStart(), after the existing isRecording() block:
    if (runtimeEnabled && camelContext != null) {
        camelContext.addLifecycleStrategy(new CamelJfrRuntimeInstrumentation());
    }
```

Add imports: `org.apache.camel.CamelContext`, `org.apache.camel.CamelContextAware`, `org.apache.camel.runtime.jfr.CamelJfrRuntimeInstrumentation`.

- [ ] **Step 5: Run the bootstrap test to verify it passes**

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrBootstrapTest -Dci.env.name=local`
Expected: PASS — auto-on emits runtime events; opt-out emits none.

- [ ] **Step 6: Run the full module test suite** (guard against regressions in the startup-recorder tests)

Run: `mvnd -q test -pl components/camel-jfr -Dci.env.name=local`
Expected: PASS.

- [ ] **Step 7: Format and commit**

```bash
mvn -q formatter:format impsort:sort -pl components/camel-jfr
git add components/camel-jfr/src/main/java/org/apache/camel/
git add components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrBootstrapTest.java
git commit -m "CAMEL-23383: Auto-install JFR runtime instrumentation with camel.jfr.runtimeEnabled opt-out

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: URI sanitization test + documentation

**Files:**
- Modify: `components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrEventNotifierTest.java` (add sanitization case)
- Modify: `components/camel-jfr/src/main/docs/jfr.adoc`
- Modify: `docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_22.adoc`

**Interfaces:**
- Consumes: `CamelJfrEventNotifier`, `JfrRecordingTestSupport`.
- Produces: no new API — a regression test plus docs.

- [ ] **Step 1: Add the failing sanitization test** to `CamelJfrEventNotifierTest`. Use a component whose URI carries a password, e.g. an endpoint URI containing `password=secret`, and assert the recorded `endpointUri` masks it (`URISupport.sanitizeUri` renders secrets as `xxxxxx`).

```java
    @Test
    void endpointUriIsSanitized() throws Exception {
        List<RecordedEvent> events = recordAndRun(
                new Class<?>[] { CamelExchangeSendEvent.class }, () -> {
            try (DefaultCamelContext context = new DefaultCamelContext()) {
                CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
                notifier.setCamelContext(context);
                context.getManagementStrategy().addEventNotifier(notifier);
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start").routeId("main")
                                .to("mock:out?password=secret");
                    }
                });
                context.start();
                context.createProducerTemplate().sendBody("direct:start", "hi");
            }
        });

        assertThat(events)
                .filteredOn(e -> "org.apache.camel.exchange.send".equals(e.getEventType().getName()))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getString("endpointUri")).doesNotContain("secret"));
    }
```

- [ ] **Step 2: Run it to verify it passes** (the sanitization is already implemented in Task 2 — this is a regression guard, so it should pass immediately)

Run: `mvnd -q test -pl components/camel-jfr -Dtest=CamelJfrEventNotifierTest#endpointUriIsSanitized -Dci.env.name=local`
Expected: PASS. (If it FAILS, the sanitization in Task 2 is wrong — fix `onSending` before continuing.)

- [ ] **Step 3: Add the "Runtime instrumentation" section to `jfr.adoc`.** After the existing startup description, add:

```asciidoc
== Runtime instrumentation

Since *4.22*, placing `camel-jfr` on the classpath also installs runtime
instrumentation that emits JFR events during message routing. This lets you
attach a Flight Recorder to a *running* application (no restart required) and
inspect per-exchange, per-route, per-processor and per-endpoint timing in the
JVM's own profiler.

The following event types are emitted under the `Camel Application / Runtime`
category:

[cols="2,3,2",options="header"]
|===
| Event | Description | Fields
| `org.apache.camel.route` | Time an exchange spends in a single route | routeId, exchangeId, failed
| `org.apache.camel.processor` | Time spent in an individual processor | exchangeId, routeId, processorId, processorType, failed
| `org.apache.camel.exchange` | End-to-end time of an exchange | exchangeId, routeId, failed
| `org.apache.camel.exchange.send` | Time to send to an endpoint | exchangeId, endpointUri, failed
| `org.apache.camel.exchange.failed` | Emitted when an exchange fails | exchangeId, routeId, exceptionType, exceptionMessage
| `org.apache.camel.redelivery` | Emitted on each redelivery attempt | exchangeId, routeId, attempt, maxAttempts
|===

Endpoint URIs are sanitized so credentials are not written to the recording.

To capture the events on a running application:

[source,bash]
----
jcmd <pid> JFR.start name=camel duration=60s filename=camel.jfr
----

Then open `camel.jfr` in Java Mission Control and look under
*Event Browser -> Camel Application -> Runtime*.

=== Disabling runtime instrumentation

Runtime instrumentation is on by default. Disable it with:

[source,properties]
----
camel.jfr.runtimeEnabled=false
----
```

- [ ] **Step 4: Add the upgrade-guide entry** to `camel-4x-upgrade-guide-4_22.adoc` (migration impact only):

```asciidoc
=== camel-jfr

Placing `camel-jfr` on the classpath now also installs *runtime instrumentation*
in addition to the existing startup instrumentation. This registers a
`RoutePolicyFactory`, an `InterceptStrategy` and an `EventNotifier` that emit JFR
events for exchanges, routes, processors, endpoint sends, failures and
redeliveries. The events are cheap when no recording is active.

To restore the previous behavior (startup instrumentation only), set:

[source,properties]
----
camel.jfr.runtimeEnabled=false
----
```

- [ ] **Step 5: Verify docs render / no broken build**

Run: `mvnd -q verify -pl components/camel-jfr -Dci.env.name=local -DskipTests`
Expected: PASS (catalog/doc generation succeeds; no uncommitted generated changes).

- [ ] **Step 6: Regenerate metadata and check for uncommitted changes**

Run: `git status --porcelain components/camel-jfr`
Expected: only the files you edited appear. If generated files under `components/camel-jfr/src/generated/` changed, `git add` them too.

- [ ] **Step 7: Commit**

```bash
mvn -q formatter:format impsort:sort -pl components/camel-jfr
git add components/camel-jfr/src/main/docs/jfr.adoc \
        docs/user-manual/modules/ROOT/pages/camel-4x-upgrade-guide-4_22.adoc \
        components/camel-jfr/src/test/java/org/apache/camel/runtime/jfr/CamelJfrEventNotifierTest.java \
        components/camel-jfr/src/generated 2>/dev/null
git commit -m "CAMEL-23383: Document JFR runtime instrumentation and verify URI sanitization

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification (after all tasks)

- [ ] Full module build passes: `mvnd -q clean install -pl components/camel-jfr -Dci.env.name=local`
- [ ] Source style check: `mvn -q -Psourcecheck validate -pl components/camel-jfr`
- [ ] No uncommitted generated files: `git status --porcelain`

## Self-review notes (planning)

- **Spec coverage:** All six events (§4) → Task 1; three collectors (§5) → Tasks 2-4; overhead guard (§6) → the `isEnabled()` guard in every hook; activation/bootstrap (§7) → Task 5; testing (§8) → Tasks 2-6 (processor, nesting, whole-exchange, send, failure/redelivery in Task 2, opt-out in Task 5, sanitization in Task 6); docs (§9) → Task 6.
- **Bootstrap refinement vs. spec:** the spec's §7.2 registered collectors inside `doStart()`; planning confirmed that is too early for the `EventNotifier` (management is null at that point), so registration moved to a `LifecycleStrategy.onContextInitializing` hook added from `doStart()`. This satisfies both the "InterceptStrategy before route build" and "management ready for EventNotifier" constraints. Flag this deviation to reviewers.
- **Open item for the implementer:** `camel.jfr.runtimeEnabled` is wired here as a programmatic setter (`setRuntimeEnabled`) plus default-true. If a `camel-main`-style property binding (`camel.jfr.runtimeEnabled=false` from properties) is required for the ticket, add a follow-up that reads the CamelContext property in `doStart()`; the spec (§7.3) explicitly left the exact property wiring as an implementation detail.
