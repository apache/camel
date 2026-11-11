/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.errorhandler;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CAMEL-25484: an exchange that a route stop or a dev mode reload cuts off is logged as one WARN line, not as an
 * exhausted failure with message history and stack trace; CAMEL-25502: and is not recorded in the error registry.
 */
// both RouteStopCutOffLog tests replace and remove the appender of the DefaultErrorHandler logger
@ResourceLock("DefaultErrorHandlerLogger")
public class RouteStopCutOffLogTest extends ContextTestSupport {

    private static final String LOGGER = DefaultErrorHandler.class.getName();

    private final Queue<String> messages = new ConcurrentLinkedQueue<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    public void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
    }

    // ---- Original DirectProducer cut-off tests (CAMEL-25484 / CAMEL-25502) ----
    // These use seda:start → split → direct:shipment (no consumer). The seda thread is interrupted
    // by stopRoute, which causes DirectProducer to set the RouteStoppingException and log one WARN.

    @Test
    public void theCutOffIsOneWarnLine() throws Exception {
        // the tests in this module run in parallel, and log through the same error handler logger:
        // only keep the events from the threads of this CamelContext
        String threadMarker = "(" + context.getName() + ")";
        ConsumingAppender.newAppender(LOGGER, "RouteStopCutOffLogTest", Level.DEBUG, event -> {
            if (event.getThreadName() != null && event.getThreadName().contains(threadMarker)) {
                messages.add(event.getLevel() + " " + event.getMessage().getFormattedMessage()
                             + (event.getThrown() != null ? " THROWN" : ""));
            }
        });
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getInflightRepository().setInflightBrowseEnabled(true);

        template.sendBody("seda:start", List.of("A", "B"));
        // the exchange waits for a consumer on direct:shipment, as after a save that sends to a route not written yet
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().browse().stream()
                .anyMatch(e -> "shipment".equals(e.getNodeId())));
        context.getRouteController().stopRoute("slow");

        await().atMost(5, TimeUnit.SECONDS).until(() -> !messages.isEmpty());
        assertEquals(1, messages.size(), messages.toString());
        String message = messages.peek();
        assertTrue(message.startsWith("WARN Exchange cut off (MessageId: "), message);
        assertTrue(message.contains(": its route is being stopped or reloaded, so it is not continued."), message);
        assertFalse(message.contains("Exhausted"), message);
        assertFalse(message.contains("Message History"), message);
        assertFalse(message.endsWith("THROWN"), message);
    }

    @Test
    public void theCutOffIsNotInTheErrorRegistry() throws Exception {
        context.getErrorRegistry().setEnabled(true);
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getInflightRepository().setInflightBrowseEnabled(true);

        template.sendBody("seda:start", List.of("A", "B"));
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().browse().stream()
                .anyMatch(e -> "shipment".equals(e.getNodeId())));
        context.getRouteController().stopRoute("slow");

        // the cut-off is a RouteStoppingException (the error handler's and the direct producer's): nothing failed
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getRouteController().getRouteStatus("slow").isStopped());
        assertEquals(0, context.getErrorRegistry().size(), () -> context.getErrorRegistry().browse().toString());
    }

    @Test
    public void theCutOffIsInTheErrorRegistryWhenIncluded() throws Exception {
        context.getErrorRegistry().setEnabled(true);
        context.getErrorRegistry().setIncludeRouteStopping(true);
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getInflightRepository().setInflightBrowseEnabled(true);

        template.sendBody("seda:start", List.of("A", "B"));
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().browse().stream()
                .anyMatch(e -> "shipment".equals(e.getNodeId())));
        context.getRouteController().stopRoute("slow");

        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getErrorRegistry().size() > 0);
        assertTrue(context.getErrorRegistry().browse().stream()
                .allMatch(e -> RouteStoppingException.class.getName().equals(e.getExceptionType())),
                () -> context.getErrorRegistry().browse().toString());
    }

    // ---- Latch-based cut-off tests (this PR) ----
    // These use direct:start → split with a processor that blocks. stopRoute force-stops after the
    // timeout, and the stopped error-handler chain emits exactly one cut-off WARN.

    @Test
    public void processedCutOffIsOneWarnLine() throws Exception {
        String threadMarker = "(" + context.getName() + ")";
        ConsumingAppender.newAppender(LOGGER, "RouteStopCutOffLogTest", Level.DEBUG, event -> {
            if (event.getThreadName() != null && event.getThreadName().contains(threadMarker)) {
                messages.add(event.getLevel() + " " + event.getMessage().getFormattedMessage()
                             + (event.getThrown() != null ? " THROWN" : ""));
            }
        });
        // Use a bounded shutdown timeout so that stopRoute force-stops the route (stopping its
        // error handlers) after the timeout, since the inflight exchange will not complete on its own.
        // Using direct: (not seda:) ensures the route stop does not interrupt the processing thread.
        context.getShutdownStrategy().setTimeout(5);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        CompletableFuture<Exchange> future = template.asyncSend("direct:start", exchange -> {
            exchange.getIn().setBody(List.of("A"));
        });

        assertTrue(entered.await(5, TimeUnit.SECONDS), "Processor did not enter within 5 seconds");
        context.getRouteController().stopRoute("slow-latch");
        release.countDown();

        Exchange result = null;
        try {
            result = future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("Exchange did not complete within 10 seconds — likely a hang in the error handler chain", e);
        }
        assertNotNull(result);
        assertInstanceOf(RouteStoppingException.class, result.getException());

        assertEquals(1, messages.size(), messages.toString());
        String message = messages.peek();
        assertTrue(message.startsWith("WARN Exchange cut off (MessageId: "), message);
        assertTrue(message.contains(": its route is being stopped or reloaded, so it is not continued."), message);
        assertFalse(message.contains("Exhausted"), message);
        assertFalse(message.contains("Message History"), message);
        assertFalse(message.endsWith("THROWN"), message);
    }

    @Test
    public void processedCutOffIsNotInTheErrorRegistry() throws Exception {
        context.getErrorRegistry().setEnabled(true);
        context.getShutdownStrategy().setTimeout(5);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        template.asyncSend("direct:start", exchange -> exchange.getIn().setBody(List.of("A")));

        assertTrue(entered.await(5, TimeUnit.SECONDS), "Processor did not enter within 5 seconds");
        context.getRouteController().stopRoute("slow-latch");
        release.countDown();

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("slow-latch").isStopped());
        assertEquals(0, context.getErrorRegistry().size(), () -> context.getErrorRegistry().browse().toString());
    }

    @Test
    public void processedCutOffIsInTheErrorRegistryWhenIncluded() throws Exception {
        context.getErrorRegistry().setEnabled(true);
        context.getErrorRegistry().setIncludeRouteStopping(true);
        context.getShutdownStrategy().setTimeout(5);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        template.asyncSend("direct:start", exchange -> exchange.getIn().setBody(List.of("A")));

        assertTrue(entered.await(5, TimeUnit.SECONDS), "Processor did not enter within 5 seconds");
        context.getRouteController().stopRoute("slow-latch");
        release.countDown();

        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getErrorRegistry().size() > 0);
        assertTrue(context.getErrorRegistry().browse().stream()
                .allMatch(e -> RouteStoppingException.class.getName().equals(e.getExceptionType())),
                () -> context.getErrorRegistry().browse().toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Original scenario: DirectProducer cut-off (CAMEL-25484 / CAMEL-25502)
                from("seda:start").routeId("slow")
                        .split(body())
                            .log("Picked ${body}")
                            .to("direct:shipment").id("shipment")
                        .end()
                        .to("mock:result");

                // Latch-based scenario: runNotAllowed() cut-off through stopped error handler
                from("direct:start").routeId("slow-latch")
                        .split(body())
                        .log("Picked ${body}")
                        // Block here so the test can stop the route while an exchange is inflight
                        // inside the split. When the route is stopped, the stopped error-handler
                        // chain emits exactly one cut-off WARN.
                        .process(exchange -> {
                            entered.countDown();
                            try {
                                if (!release.await(30, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("release latch timed out");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        })
                        .end()
                        .to("mock:result2");
            }
        };
    }
}
