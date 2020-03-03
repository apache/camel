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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RecipientList;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class BeanRecipientListTimeoutTest extends ContextTestSupport {

    private volatile Exchange receivedExchange;
    private volatile int receivedIndex;
    private volatile int receivedTotal;
    private volatile long receivedTimeout;

    @Test
    public void testBeanRecipientListParallelTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // A will timeout so we only get B and/or C
        mock.message(0).body().not(body().contains("A"));

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();

        assertNotNull(receivedExchange);
        assertEquals(0, receivedIndex);
        assertEquals(3, receivedTotal);
        assertEquals(1000, receivedTimeout);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myBean", new MyBean());
        answer.bind("myStrategy", new MyAggregationStrategy());
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").bean("myBean", "route").to("mock:result");

                from("direct:a").delay(2000).setBody(constant("A"));

                from("direct:b").setBody(constant("B"));

                from("direct:c").delay(500).setBody(constant("C"));
            }
        };
    }

    public static class MyBean {

        @RecipientList(strategyRef = "myStrategy", parallelProcessing = true, timeout = 1000)
        public String[] route(String body) {
            return new String[] {"direct:a", "direct:b", "direct:c"};
        }
    }

    private class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            // we can't assert on the expected values here as the contract of
            // this method doesn't
            // allow to throw any Throwable (including AssertionError) so that
            // we assert
            // about the expected values directly inside the test method itself.
            // other than that
            // asserting inside a thread other than the main thread dosen't make
            // much sense as
            // junit would not realize the failed assertion!
            receivedExchange = oldExchange;
            receivedIndex = index;
            receivedTotal = total;
            receivedTimeout = timeout;
        }

        @Override
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
