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
package org.apache.camel.component.disruptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With multipleConsumers the on completions of an InOnly exchange are handed over to the consumers, and must run once,
 * when one of the consumers fails or when all of them are done.
 */
public class DisruptorMultipleConsumersOnCompletionTest extends CamelTestSupport {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private NotifyBuilder consumersDone;

    @AfterEach
    void releaseConsumers() {
        // do not leave a consumer waiting when a test fails
        release.countDown();
    }

    @Override
    protected void doPostSetup() {
        // the exchanges of the two consumers (both are from the disruptor endpoint)
        consumersDone = new NotifyBuilder(context).from("disruptor:fanout*").whenDone(2).create();
    }

    @Test
    void testFailureThenSuccess() throws Exception {
        MockEndpoint slow = getMockEndpoint("mock:slow");
        slow.expectedBodiesReceived("fail-first");

        template.sendBody("direct:start", "fail-first");

        // the consumer that fails completes the exchange, while the other consumer is still routing it
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !events.isEmpty());
        assertEquals(List.of("failure:fail-first"), events);

        release.countDown();
        MockEndpoint.assertIsSatisfied(context);
        // the other consumer is done as well, which must not run the on completions a second time
        assertNoMoreEvents(List.of("failure:fail-first"));
    }

    @Test
    void testBothFail() throws Exception {
        MockEndpoint slow = getMockEndpoint("mock:slow");
        slow.expectedBodiesReceived("both-fail");

        template.sendBody("direct:start", "both-fail");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !events.isEmpty());
        release.countDown();
        MockEndpoint.assertIsSatisfied(context);
        assertNoMoreEvents(List.of("failure:both-fail"));
    }

    @Test
    void testSuccessThenFailure() throws Exception {
        MockEndpoint slow = getMockEndpoint("mock:slow");
        slow.expectedBodiesReceived("success-first");

        NotifyBuilder successDone = new NotifyBuilder(context).from("disruptor:fanout*").whenDone(1).create();
        template.sendBody("direct:start", "success-first");

        // the consumer that fails waits until the other consumer is done
        assertTrue(successDone.matches(10, TimeUnit.SECONDS), "The consumer that succeeds should be done");
        slow.assertIsSatisfied();
        assertTrue(events.isEmpty(), "Events: " + events);
        release.countDown();
        assertNoMoreEvents(List.of("failure:success-first"));
    }

    @Test
    void testAllSucceed() throws Exception {
        MockEndpoint slow = getMockEndpoint("mock:slow");
        slow.expectedBodiesReceived("success");

        template.sendBody("direct:start", "success");
        release.countDown();

        MockEndpoint.assertIsSatisfied(context);
        assertNoMoreEvents(List.of("complete:success"));
    }

    private void assertNoMoreEvents(List<String> expected) {
        // the consumers call back from the on completions of their own exchanges, which run before these are done
        assertTrue(consumersDone.matches(10, TimeUnit.SECONDS), "Both consumers should be done");
        assertEquals(expected, events);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .process(e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                            @Override
                            public void onComplete(Exchange exchange) {
                                events.add("complete:" + exchange.getMessage().getBody(String.class));
                            }

                            @Override
                            public void onFailure(Exchange exchange) {
                                events.add("failure:" + exchange.getMessage().getBody(String.class));
                            }
                        }))
                        .to("disruptor:fanout?multipleConsumers=true");

                // fails at once, or after the other consumer is done
                from("disruptor:fanout?multipleConsumers=true").errorHandler(noErrorHandler())
                        .choice()
                        .when(body().isEqualTo("success-first")).process(e -> release.await(20, TimeUnit.SECONDS))
                        .end()
                        .filter(body().isNotEqualTo("success"))
                        .throwException(new IllegalArgumentException("Forced"));

                // done when released, and fails too for both-fail
                from("disruptor:fanout?multipleConsumers=true").errorHandler(noErrorHandler())
                        .choice()
                        .when(body().isNotEqualTo("success-first")).process(e -> release.await(20, TimeUnit.SECONDS))
                        .end()
                        .to("mock:slow")
                        .filter(body().isEqualTo("both-fail"))
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
