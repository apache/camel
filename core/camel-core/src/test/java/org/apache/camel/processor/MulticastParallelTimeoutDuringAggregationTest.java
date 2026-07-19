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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CAMEL-24142: Verifies that a multicast timeout still fires even when the timeout instant falls inside an ongoing
 * aggregation (i.e. the aggregation lock is held). Before the fix, tryLock() in timeout() would silently return and the
 * exchange would hang forever.
 */
@Isolated("Uses short timeouts and latches")
public class MulticastParallelTimeoutDuringAggregationTest extends ContextTestSupport {

    private final CountDownLatch aggregationProceed = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);

    @Test
    public void testTimeoutNotLostDuringAggregation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.setResultWaitTime(10000);

        template.sendBody("direct:start", "Hello");

        mock.assertIsSatisfied();

        Exchange out = mock.getReceivedExchanges().get(0);
        assertNotNull(out, "Should have received an exchange from multicast timeout");

        done.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .multicast(new SlowAggregationStrategy())
                        .parallelProcessing()
                        .timeout(500)
                        .to("direct:fast", "direct:stuck")
                        .end()
                        .to("mock:result");

                from("direct:fast").setBody(constant("A"));

                // block until test signals done — the timeout must handle this
                from("direct:stuck").process(e -> done.await(30, TimeUnit.SECONDS));
            }
        };
    }

    class SlowAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                try {
                    // hold the lock for 2s so the 500ms timeout fires in this window
                    aggregationProceed.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return newExchange;
            }
            String body = oldExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
            return oldExchange;
        }
    }
}
