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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ClaimCheckOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim check repository is scoped per exchange: the exchanges created by a parallel Splitter get their own copy of
 * the parent's claim checks, and must not get the message of another part back.
 */
class ClaimCheckEipSplitParallelTest extends ContextTestSupport {

    private final CountDownLatch part0Saved = new CountDownLatch(1);
    private final CountDownLatch part1Saved = new CountDownLatch(1);
    private final CountDownLatch part0Restored = new CountDownLatch(1);

    @Test
    void testSetGetInParallelSplit() throws Exception {
        assertEachPartRestoresItsOwnMessage("direct:setget");
    }

    @Test
    void testPushPopInParallelSplit() throws Exception {
        assertEachPartRestoresItsOwnMessage("direct:pushpop");
    }

    @Test
    void testPartGetsClaimCheckOfParent() throws Exception {
        getMockEndpoint("mock:part").expectedBodiesReceived("A,B", "A,B");
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B");

        template.sendBody("direct:parent", "A,B");

        assertMockEndpointsSatisfied();
    }

    private void assertEachPartRestoresItsOwnMessage(String uri) throws Exception {
        getMockEndpoint("mock:part").expectedBodiesReceivedInAnyOrder("0:A", "1:B");
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B");

        template.sendBody(uri, "A,B");

        assertMockEndpointsSatisfied();
    }

    // the parts save their message in this order: part 0, part 1, and then restore it: part 0, part 1

    private void beforeSave(Exchange exchange) throws Exception {
        if (splitIndex(exchange) == 1) {
            await(part0Saved);
        }
    }

    private void afterSave(Exchange exchange) throws Exception {
        if (splitIndex(exchange) == 0) {
            part0Saved.countDown();
            await(part1Saved);
        } else {
            part1Saved.countDown();
            await(part0Restored);
        }
    }

    private void afterRestore(Exchange exchange) {
        if (splitIndex(exchange) == 0) {
            part0Restored.countDown();
        }
    }

    private static int splitIndex(Exchange exchange) {
        return exchange.getProperty(Exchange.SPLIT_INDEX, Integer.class);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(10, TimeUnit.SECONDS), "The other part did not reach the expected step");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:setget")
                        .claimCheck(ClaimCheckOperation.Set, "original")
                        .split(body().tokenize(",")).parallelProcessing()
                            .process(e -> beforeSave(e))
                            .claimCheck(ClaimCheckOperation.Set, "item")
                            .process(e -> afterSave(e))
                            .setBody(constant("reply-from-service"))
                            .claimCheck(ClaimCheckOperation.Get, "item")
                            .process(e -> afterRestore(e))
                            .setBody(simple("${exchangeProperty.CamelSplitIndex}:${body}"))
                            .to("mock:part")
                        .end()
                        .claimCheck(ClaimCheckOperation.Get, "original")
                        .to("mock:result");

                from("direct:pushpop")
                        .claimCheck(ClaimCheckOperation.Set, "original")
                        .split(body().tokenize(",")).parallelProcessing()
                            .process(e -> beforeSave(e))
                            .claimCheck(ClaimCheckOperation.Push)
                            .process(e -> afterSave(e))
                            .setBody(constant("reply-from-service"))
                            .claimCheck(ClaimCheckOperation.Pop)
                            .process(e -> afterRestore(e))
                            .setBody(simple("${exchangeProperty.CamelSplitIndex}:${body}"))
                            .to("mock:part")
                        .end()
                        .claimCheck(ClaimCheckOperation.Get, "original")
                        .to("mock:result");

                from("direct:parent")
                        .claimCheck(ClaimCheckOperation.Set, "original")
                        .split(body().tokenize(","))
                            .claimCheck(ClaimCheckOperation.Get, "original")
                            .to("mock:part")
                        .end()
                        .to("mock:result");
            }
        };
    }
}
