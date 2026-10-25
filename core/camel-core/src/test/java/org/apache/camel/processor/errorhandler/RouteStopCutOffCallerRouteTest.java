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
 * multicast), the stopped channel error handler of the step after the blocked processor invokes
 * {@code runNotAllowed()}, which sets the {@code RouteStoppingException} on the exchange and marks it as interrupted
 * ({@code exchange.getExchangeExtension().setInterrupted(true)}). A calling route A that is still running and passed
 * the same exchange into route B over {@code direct} then also skips its {@code onException}, redelivery, and dead
 * letter channel — the same behaviour as when {@code DirectProducer} is cut off while waiting for a consumer
 * (CAMEL-25502). This test pins down that behaviour: the DLC mock is NOT called when the nested-EIP cut-off propagates
 * back to the caller.
 *
 * <p>
 * Design: a {@code .process()} inside route B's nested EIP counts down an {@code entered} latch and then waits on a
 * {@code release} latch (with a timeout). The test waits for {@code entered}, force-stops route B (setting its
 * error-handler wrappers to stopped state), then releases the latch. The processor exits normally; the next step
 * ({@code mock:afterBlock}) is in a stopped error-handler wrapper, which invokes {@code runNotAllowed()}: this sets a
 * {@code RouteStoppingException} on the exchange and marks it as interrupted. Route A is kept running throughout; its
 * DLC error handler then sees the interrupted flag and skips redelivery and the dead letter channel.
 */
public class RouteStopCutOffCallerRouteTest extends ContextTestSupport {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    /**
     * Route A has a dead letter channel and sends to route B over {@code direct}. Route B is stopped inside a split
     * nested in a multicast while a {@code .process()} is blocked on a latch. After the stop, the latch is released:
     * the processor exits normally and the next step ({@code mock:afterBlock}) is in a stopped error-handler wrapper
     * that invokes {@code runNotAllowed()}, creating the {@code RouteStoppingException} and marking the exchange as
     * interrupted. Route A is kept running; its error handler then sees the interrupted flag and skips redelivery,
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
        Future<Object> future = template.asyncRequestBody("seda:caller", "x", Object.class);

        // Wait until the processor inside route B's nested EIP has entered and is blocking.
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Processor did not enter within 5 seconds");

        // 1. Stop route B: sets MulticastProcessor and its nested error-handler wrappers to stopped
        //    state (isStoppingOrStopped() returns true). The thread is still blocked on the release latch.
        //    Route A is NOT stopped — it stays running throughout the test.
        context.getRouteController().stopRoute("nested-b");

        // 2. Release the latch: the processor exits normally (no interrupt, no manually thrown exception).
        //    The next step (mock:afterBlock) is in a stopped error-handler wrapper, which invokes
        //    runNotAllowed(): this sets a RouteStoppingException on the exchange and marks it as
        //    interrupted. Route A's DLC error handler then sees the interrupted flag and skips
        //    redelivery and the dead letter channel.
        release.countDown();

        // Wait for the exchange to finish. It should complete with a RouteStoppingException
        // (route A's runInterrupted() keeps the exception and fires the callback without the DLC).
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
                // After the processor exits, mock:afterBlock is in a stopped error-handler wrapper
                // (because the route was stopped while the processor was blocked): runNotAllowed()
                // sets a RouteStoppingException on the exchange and marks it as interrupted.
                from("direct:b").routeId("nested-b")
                        .multicast()
                        .split(body())
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                entered.countDown();
                                // Block until the test releases, or until 30 s to avoid
                                // hanging the test suite if something goes wrong.
                                release.await(30, TimeUnit.SECONDS);
                            }
                        })
                        .to("mock:afterBlock").id("afterBlock")
                        .end()
                        .end();
            }
        };
    }
}
