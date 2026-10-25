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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CAMEL-25823: when route B is force-stopped while an inflight exchange is inside a nested EIP (split inside
 * multicast), the {@code .process()} step's own stopped channel handler re-enters {@code run()}, which sets a
 * {@code RouteStoppingException} on the exchange and calls {@code logCutOffByRouteStop()}. An outer stopped handler
 * then finds the exception already set and marks the exchange as interrupted
 * ({@code exchange.getExchangeExtension().setInterrupted(true)}). A calling route A that is still running and passed
 * the same exchange into route B over {@code direct} then also skips its {@code onException}, redelivery, and dead
 * letter channel — the same behaviour as when {@code DirectProducer} is cut off while waiting for a consumer
 * (CAMEL-25502). This test pins down that behaviour: the DLC mock is NOT called when the nested-EIP cut-off propagates
 * back to the caller.
 *
 * <p>
 * Design: a {@code .process()} inside route B's nested EIP counts down an {@code entered} latch and then waits on a
 * {@code release} latch (with a timeout). The test waits for {@code entered}, force-stops route B (setting its
 * error-handler wrappers to stopped state), then releases the latch. The processor exits normally; the
 * {@code .process()} step's own stopped channel handler re-enters {@code run()}, which sets a
 * {@code RouteStoppingException} and logs the WARN. An outer stopped handler finds the exception already set and marks
 * {@code interrupted=true}. Route A is kept running throughout; it has a DLC so it runs a {@code RedeliveryTask} whose
 * {@code isDone()} path sees the interrupted flag and skips redelivery and the dead letter channel.
 */
public class RouteStopCutOffCallerRouteTest extends ContextTestSupport {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    /**
     * Route A has a dead letter channel and sends to route B over {@code direct}. Route B is stopped inside a split
     * nested in a multicast while a {@code .process()} is blocked on a latch. After the stop, the latch is released:
     * the processor exits normally and the {@code .process()} step's own stopped channel handler re-enters
     * {@code run()}, which sets a {@code RouteStoppingException} and logs the WARN. An outer stopped handler finds the
     * exception already set and marks {@code interrupted=true}. Route A is kept running; it has a DLC so it runs a
     * {@code RedeliveryTask} whose {@code isDone()} path sees the interrupted flag and skips redelivery,
     * {@code onException}, and the dead letter channel.
     */
    @Test
    public void callerRouteDlcNotCalledOnNestedEipCutOff() throws Exception {
        MockEndpoint dlc = getMockEndpoint("mock:dlc");
        dlc.expectedMessageCount(0);

        MockEndpoint afterBlock = getMockEndpoint("mock:afterBlock");
        afterBlock.expectedMessageCount(0);

        // Route A sends synchronously to route B via direct (same exchange object).
        // The Future lets us wait for the exchange to fully propagate back.
        // Set a short shutdown timeout so stopRoute("nested-b") force-stops after 1 s instead of
        // the default 300 s. route B is a direct: consumer with no executor of its own; the inflight
        // thread belongs to seda:caller (route A) and is not interrupted by the force-stop.
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        Future<Object> future = template.asyncRequestBody("seda:caller", "x", Object.class);

        // Wait until the processor inside route B's nested EIP has entered and is blocking.
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Processor did not enter within 5 seconds");

        // 1. Stop route B: waits up to 1 s for the inflight exchange, then force-stops.
        //    Force-stop calls MulticastProcessor.doStop(), which stops the nested error-handler wrappers
        //    (isStoppingOrStopped() returns true). The seda:caller thread is NOT interrupted.
        //    Route A is NOT stopped — it stays running throughout the test.
        context.getRouteController().stopRoute("nested-b");

        // 2. Release the latch: the processor exits normally (no interrupt, no manually thrown exception).
        //    The .process() step's own stopped channel handler re-enters run(), which sets a
        //    RouteStoppingException and logs the WARN. An outer stopped handler finds the exception
        //    already set and marks interrupted=true. Route A has a DLC so it runs a RedeliveryTask
        //    whose isDone() path sees the interrupted flag and skips redelivery and the dead letter channel.
        release.countDown();

        // Wait for the exchange to finish. It should complete with a RouteStoppingException
        // (route A has a DLC so it takes the RedeliveryTask.isDone() path, which keeps the exception
        // and fires the callback without invoking the DLC).
        ExecutionException ex = null;
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            ex = e;
        } catch (TimeoutException e) {
            fail("Exchange did not complete within 10 seconds — likely a hang in the error handler chain", e);
        }
        assertNotNull(ex, "Expected the exchange to fail with a RouteStoppingException");
        assertInstanceOf(CamelExecutionException.class, ex.getCause());
        assertInstanceOf(RouteStoppingException.class, ex.getCause().getCause());

        // mock:afterBlock must NOT have been called: the stopped error-handler wrapper intercepted
        // the exchange before it reached the endpoint.
        afterBlock.assertIsSatisfied();

        // The DLC must NOT have been called: route A's error handler skipped it because the exchange
        // was marked as interrupted by route B's nested stopped error handler.
        dlc.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route A: dead letter channel configured; calls route B via direct (same exchange object).
                // Route A is NOT stopped during the test — it stays running throughout.
                from("seda:caller").routeId("caller-a")
                        .errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0))
                        .to("direct:b");

                // Route B: split nested inside multicast. The processor counts down the entered latch
                // and then waits on the release latch (with a timeout so the test cannot hang).
                // After the processor exits, the .process() step's own stopped channel handler
                // re-enters run(), which sets a RouteStoppingException and logs the WARN. An outer
                // stopped handler finds the exception already set and marks interrupted=true.
                from("direct:b").routeId("nested-b")
                        .multicast()
                        .split(body())
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                entered.countDown();
                                // Block until the test releases, or until 30 s to avoid
                                // hanging the test suite if something goes wrong.
                                if (!release.await(30, TimeUnit.SECONDS)) {
                                    fail("Release latch timed out after 30 seconds");
                                }
                            }
                        })
                        .to("mock:afterBlock").id("afterBlock")
                        .end()
                        .end();
            }
        };
    }
}
