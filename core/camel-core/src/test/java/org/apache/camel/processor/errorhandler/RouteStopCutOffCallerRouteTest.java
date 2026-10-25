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

/**
 * CAMEL-25823: when route B is force-stopped while an inflight exchange is inside a nested EIP (split inside
 * multicast), the exchange is marked as interrupted after the cut-off WARN is logged. A calling route A that is still
 * running and passed the same exchange into route B over {@code direct} then also skips its {@code onException},
 * redelivery, and dead letter channel — the same behaviour as when {@code DirectProducer} is cut off while waiting for
 * a consumer (CAMEL-25502). This test pins down that behaviour: the DLC mock is NOT called when the nested-EIP cut-off
 * propagates back to the caller.
 *
 * <p>
 * Design: a {@code .process()} inside route B's nested EIP counts down an {@code entered} latch and then blocks
 * indefinitely ({@code Thread.sleep(MAX_VALUE)}). The test waits for {@code entered}, force-stops route B (setting its
 * error-handler wrappers to stopped state), and then stops route A's seda consumer, which shuts down its thread-pool
 * and interrupts the blocked thread. The processor converts the {@code InterruptedException} to a
 * {@code RouteStoppingException}. Route B's nested stopped error-handler wrappers then invoke {@code runNotAllowed()}:
 * the inner wrapper sees the exception already set and logs the cut-off WARN, then marks the shared exchange as
 * interrupted. Route A's error handler then sees the interrupted flag and skips redelivery, {@code onException}, and
 * the dead letter channel.
 */
public class RouteStopCutOffCallerRouteTest extends ContextTestSupport {

    private final CountDownLatch entered = new CountDownLatch(1);

    /**
     * Route A has a dead letter channel and sends to route B over {@code direct}. Route B is stopped inside a split
     * nested in a multicast while a {@code .process()} is blocked. Because the cut-off happens inside a nested EIP of
     * route B, {@code setInterrupted(true)} is set on the shared exchange. Route A's error handler then sees the
     * interrupted flag and skips redelivery, {@code onException}, and the dead letter channel.
     */
    @Test
    public void callerRouteDlcNotCalledOnNestedEipCutOff() throws Exception {
        MockEndpoint dlc = getMockEndpoint("mock:dlc");
        dlc.expectedMessageCount(0);

        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        // Route A sends synchronously to route B via direct (same exchange object).
        // The Future lets us wait for the exchange to fully propagate back.
        Future<Object> future = template.asyncRequestBody("seda:caller", "x", Object.class);

        // Wait until the processor inside route B's nested EIP has entered and is blocking.
        entered.await(5, TimeUnit.SECONDS);

        // 1. Stop route B: sets MulticastProcessor and its nested error-handler wrappers to stopped
        //    state (isStoppingOrStopped() returns true). The thread is still blocked in the processor.
        context.getRouteController().stopRoute("nested-b");

        // 2. Stop route A: after the shutdown timeout (1 s) its seda executor calls shutdownNow(),
        //    which interrupts the blocked thread. The processor converts InterruptedException to
        //    RouteStoppingException; route B's stopped wrappers then invoke runNotAllowed():
        //    the outer wrapper logs the cut-off WARN and marks the exchange as interrupted.
        //    Route A's DLC error handler skips redelivery and the DLC because interrupted=true.
        context.getRouteController().stopRoute("caller-a");

        // Wait for the exchange to finish. It should complete with a RouteStoppingException
        // (route A's runInterrupted() keeps the exception and fires the callback without the DLC).
        ExecutionException ex = null;
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            ex = e;
        }
        assertNotNull(ex, "Expected the exchange to fail with a RouteStoppingException");
        assertInstanceOf(CamelExecutionException.class, ex.getCause());
        assertInstanceOf(RouteStoppingException.class, ex.getCause().getCause());

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
                from("seda:caller").routeId("caller-a")
                        .errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0))
                        .to("direct:b");

                // Route B: split nested inside multicast. The processor counts down the entered latch
                // and blocks until interrupted. On interrupt it throws RouteStoppingException so that
                // the nested stopped error-handler wrappers see a RouteStoppingException on the exchange,
                // log the cut-off WARN once, and mark the exchange as interrupted.
                from("direct:b").routeId("nested-b")
                        .multicast()
                        .split(body())
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                entered.countDown();
                                try {
                                    Thread.sleep(Long.MAX_VALUE);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RouteStoppingException(
                                            "cut off by route stop (interrupted while blocked in test processor)",
                                            e);
                                }
                            }
                        })
                        .end()
                        .end();
            }
        };
    }
}
