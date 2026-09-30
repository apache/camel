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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When the ring buffer is full, the InOnly exchange is not published, and its on completions (such as the rollback of
 * the consumer) must still run.
 */
public class DisruptorRingBufferFullOnCompletionTest extends CamelTestSupport {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    void testRingBufferFull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A");

        // the consumer holds the only slot of the ring buffer until it is released
        template.sendBody("direct:start", "A");
        try {
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> template.sendBody("direct:start", "B"));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals(List.of("failure:B"), events);
        } finally {
            release.countDown();
        }
        MockEndpoint.assertIsSatisfied(context);
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of("failure:B", "complete:A"), events));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").errorHandler(noErrorHandler())
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
                        .to("disruptor:full?size=1&blockWhenFull=false");

                from("disruptor:full?size=1")
                        .process(e -> release.await(20, TimeUnit.SECONDS))
                        .to("mock:result");
            }
        };
    }
}
