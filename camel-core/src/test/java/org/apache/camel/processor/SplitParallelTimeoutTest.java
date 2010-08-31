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
public class SplitParallelTimeoutTest extends ContextTestSupport {

    public void testSplitParallelTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // A will timeout so we only get B and/or C
        mock.message(0).body().not(body().contains("A"));

        template.sendBody("direct:start", "A,B,C");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(","), new MyAggregationStrategy())
                        .parallelProcessing().timeout(1000)
                        .choice()
                            .when(body().isEqualTo("A")).to("direct:a")
                            .when(body().isEqualTo("B")).to("direct:b")
                            .when(body().isEqualTo("C")).to("direct:c")
                        .end() // end choice
                    .end() // end split
                    .to("mock:result");

                from("direct:a").delay(5000).setBody(constant("A"));

                from("direct:b").setBody(constant("B"));

                from("direct:c").delay(500).setBody(constant("C"));
            }
        };
    }

    private class MyAggregationStrategy implements TimeoutAwareAggregationStrategy {

        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            assertEquals(1000, timeout);
            assertEquals(3, total);
            assertEquals(0, index);
            assertNotNull(oldExchange);
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
