/**
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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.TimeoutAwareAggregationStrategy;
import org.junit.Test;

/**
 * @version
 */
public class AggregateTimeoutTest extends ContextTestSupport {

    private final AtomicInteger invoked = new AtomicInteger();
    private volatile Exchange receivedExchange;
    private volatile int receivedIndex;
    private volatile int receivedTotal;
    private volatile long receivedTimeout;

    @Test
    public void testAggregateTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);

        // wait about 0.2 second so that the timeout kicks in but it was discarded
        mock.assertIsSatisfied(200);

        // should invoke the timeout method
        assertEquals(1, invoked.get());

        assertNotNull(receivedExchange);
        assertEquals("AB", receivedExchange.getIn().getBody());
        assertEquals(-1, receivedIndex);
        assertEquals(-1, receivedTotal);
        assertEquals(100, receivedTimeout);

        mock.reset();
        mock.expectedBodiesReceived("ABC");

        // now send 3 exchanges which shouldn't trigger the timeout anymore
        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        // should complete before timeout
        mock.assertIsSatisfied(150);

        // should have not invoked the timeout method anymore
        assertEquals(1, invoked.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new MyAggregationStrategy())
                        .discardOnCompletionTimeout()
                        .completionSize(3)
                        // use a 0.1 second timeout
                        .completionTimeout(100).completionTimeoutCheckerInterval(10)
                        .to("mock:aggregated");
            }
        };
    }

    private class MyAggregationStrategy implements TimeoutAwareAggregationStrategy {

        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            invoked.incrementAndGet();

            // we can't assert on the expected values here as the contract of this method doesn't
            // allow to throw any Throwable (including AssertionError) so that we assert
            // about the expected values directly inside the test method itself. other than that
            // asserting inside a thread other than the main thread dosen't make much sense as
            // junit would not realize the failed assertion!
            receivedExchange = oldExchange;
            receivedIndex = index;
            receivedTotal = total;
            receivedTimeout = timeout;
        }

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String body = oldExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
            return oldExchange;
        }
    }

}
