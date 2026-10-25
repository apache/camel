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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CAMEL-25823: verifies that a route-stop cut-off produces exactly one WARN line even when the exchange passes through
 * multiple nested stopped error handlers. Route B uses a {@code filter} wrapping a {@code .process()} step, giving the
 * exchange three nested error-handler channels: the {@code .process()} step's channel, the filter's channel, and the
 * outer pipeline's channel. The {@code setInterrupted(true)} marker set after the first WARN prevents subsequent
 * stopped handlers from logging the same cut-off again.
 *
 * <p>
 * Design: route A ({@code seda:start}) forwards to route B ({@code direct:nested}) via {@code direct} (same exchange
 * object, no thread switch). A {@code .process()} inside B's filter counts down an {@code entered} latch and waits on a
 * {@code release} latch. The test waits for {@code entered}, stops route B (whose error-handler wrappers are now in
 * stopped state), then releases the latch. The processor exits normally; the {@code .process()} step's own stopped
 * channel handler re-enters {@code run()}, which sets the {@code RouteStoppingException}. The filter's stopped channel
 * handler then finds the exception already set and logs the WARN, marking {@code interrupted=true}. The outer pipeline
 * channel sees the flag and skips its own log. Route A is kept running; the WARN count is asserted after the exchange
 * completes.
 */
public class RouteStopCutOffLogNestedTest extends ContextTestSupport {

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

    /**
     * A filter wrapping the blocked processor gives the exchange three nested error-handler channels when route B is
     * stopped mid-exchange. The single-WARN guarantee should hold: only one WARN line per exchange, not one per
     * channel.
     */
    @Test
    public void nestedEipCutOffIsOneWarnLine() throws Exception {
        // The tests in this module run in parallel, and log through the same error handler logger:
        // only keep the events from the threads of this CamelContext.
        String threadMarker = "(" + context.getName() + ")";
        ConsumingAppender.newAppender(LOGGER, "RouteStopCutOffLogNestedTest", Level.DEBUG, event -> {
            if (event.getThreadName() != null && event.getThreadName().contains(threadMarker)) {
                messages.add(event.getLevel() + " " + event.getMessage().getFormattedMessage()
                             + (event.getThrown() != null ? " THROWN" : ""));
            }
        });
        // A short shutdown timeout ensures stopRoute force-stops route B after 1 s.
        // Route B is a direct: consumer with no executor thread of its own; the seda:start
        // consumer thread is NOT interrupted when only route B is stopped.
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        // Send asynchronously so the test thread is free to call stopRoute.
        Future<Object> future = template.asyncRequestBody("seda:start", "x", Object.class);

        // Wait until the processor inside route B's nested EIP has entered and is blocking.
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Processor did not enter within 5 seconds");

        // Stop route B: stopRouteService stops B's error-handler wrappers after the 1 s force-stop.
        // The seda:start consumer thread is NOT interrupted (it owns no thread in route B).
        context.getRouteController().stopRoute("nested");

        // Release the latch: the processor exits normally (no interrupt, no manually thrown exception).
        // The .process() step's own stopped channel handler re-enters run(), which sets a
        // RouteStoppingException. The filter's stopped channel handler finds the exception already set,
        // logs the WARN, and marks interrupted=true. The outer pipeline channel sees the flag and
        // skips its own log.
        release.countDown();

        // Wait for the exchange to complete fully.
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // expected: RouteStoppingException propagated back
        } catch (TimeoutException e) {
            fail("Exchange did not complete within 10 seconds — likely a hang in the error handler chain", e);
        }

        // Exactly one WARN should be emitted, even though the exchange passed through three nested
        // stopped error-handler channels (process step, filter, outer pipeline).
        assertEquals(1, messages.size(), messages.toString());
        String message = messages.peek();
        assertTrue(message.startsWith("WARN Exchange cut off (MessageId: "), message);
        assertTrue(message.contains(": its route is being stopped or reloaded, so it is not continued."), message);
        assertFalse(message.contains("Exhausted"), message);
        assertFalse(message.contains("Message History"), message);
        assertFalse(message.endsWith("THROWN"), message);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route A: forwards to route B via direct (same exchange object, no thread switch).
                // Route A is NOT stopped during the test — it stays running throughout.
                from("seda:start").routeId("caller-nested")
                        .to("direct:nested");

                // Route B: a filter wrapping the processor gives the exchange three nested error-handler
                // channels. The processor counts down the entered latch and waits on the release latch.
                // After the processor exits, the .process() step's stopped channel sets the exception;
                // the filter's stopped channel logs the WARN and marks interrupted=true; the outer
                // pipeline channel sees the flag and skips.
                from("direct:nested").routeId("nested")
                        .filter(constant(true))
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                entered.countDown();
                                // Block until the test releases, or 30 s to avoid hanging
                                // the test suite if something goes wrong.
                                if (!release.await(30, TimeUnit.SECONDS)) {
                                    fail("Release latch timed out after 30 seconds");
                                }
                            }
                        })
                        .to("mock:afterBlock")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
