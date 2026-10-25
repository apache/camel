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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * CAMEL-25823: when route B is force-stopped while an inflight exchange is inside a nested EIP (split
 * inside multicast), the exchange is marked as interrupted after the cut-off WARN is logged. A calling
 * route A that is still running and passed the same exchange into route B over {@code direct} then also
 * skips its {@code onException}, redelivery, and dead letter channel — the same behaviour as
 * {@code DirectProducer} cuts off a blocked {@code direct} producer (CAMEL-25502). This test pins down
 * that behaviour: the DLC mock is NOT called when the nested-EIP cut-off propagates back to the caller.
 */
public class RouteStopCutOffCallerRouteTest extends ContextTestSupport {

    /**
     * Route A has a dead letter channel and sends to route B over {@code direct}. Route B is stopped inside
     * a split nested in a multicast while the sub-exchange is blocked waiting for a consumer on
     * {@code direct:shipment}. Because the cut-off happens inside a nested EIP of route B,
     * {@code setInterrupted(true)} is set on the shared exchange. Route A's error handler then sees the
     * interrupted flag and skips redelivery, onException, and the dead letter channel.
     */
    @Test
    public void callerRouteDlcNotCalledOnNestedEipCutOff() throws Exception {
        MockEndpoint dlc = getMockEndpoint("mock:dlc");
        dlc.expectedMessageCount(0);

        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
        context.getInflightRepository().setInflightBrowseEnabled(true);

        // Send asynchronously so the test thread can stop route B while the exchange is in-flight.
        // Route A (seda:caller) calls direct:b; inside B the sub-exchange blocks on direct:shipment.
        template.asyncSendBody("seda:caller", "x");

        // wait until the sub-exchange is blocked at the direct:shipment step (no consumer registered)
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getInflightRepository().browse().stream()
                .anyMatch(e -> "shipment".equals(e.getNodeId())));

        // force-stop route B while the exchange is inside its nested EIP
        context.getRouteController().stopRoute("nested-b");

        // wait until route B has fully stopped and all in-flight exchanges have finished
        await().atMost(5, TimeUnit.SECONDS).until(
                () -> context.getRouteController().getRouteStatus("nested-b").isStopped()
                        && context.getInflightRepository().browse().isEmpty());

        // The DLC must NOT have been called: route A's error handler skipped it because the exchange
        // was marked as interrupted by route B's nested stopped error handler.
        dlc.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route A: dead letter channel configured; calls route B via direct
                from("seda:caller").routeId("caller-a")
                        .errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0))
                        .to("direct:b");

                // Route B: split nested inside multicast — sub-exchange blocks on direct:shipment
                // (no consumer is registered for direct:shipment, so DirectProducer blocks by default)
                from("direct:b").routeId("nested-b")
                        .multicast()
                            .split(body())
                                .to("direct:shipment").id("shipment")
                            .end()
                        .end();
            }
        };
    }
}
