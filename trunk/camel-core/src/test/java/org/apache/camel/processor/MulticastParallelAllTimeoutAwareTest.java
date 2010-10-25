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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.TimeoutAwareAggregationStrategy;

/**
 * @version $Revision: 777808 $
 */
public class MulticastParallelAllTimeoutAwareTest extends ContextTestSupport {

    public void testMulticastParallelAllTimeoutAware() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // ABC will timeout so we only get our canned response
        mock.expectedBodiesReceived("AllTimeout");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .multicast(new MyAggregationStrategy())
                        .parallelProcessing().timeout(500).to("direct:a", "direct:b", "direct:c")
                        // use end to indicate end of multicast route
                        .end()
                        .to("mock:result");

                from("direct:a").delay(1000).setBody(constant("A"));

                from("direct:b").delay(2000).setBody(constant("B"));

                from("direct:c").delay(1500).setBody(constant("C"));
            }
        };
    }

    private class MyAggregationStrategy implements TimeoutAwareAggregationStrategy {

        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            assertEquals(500, timeout);
            assertEquals(3, total);
            assertEquals(0, index);
            assertNotNull(oldExchange);
            oldExchange.getIn().setBody("AllTimeout");
        }

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            // noop
            return oldExchange;
        }
    }
}
