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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * @version 
 */
public class MulticastParallelTest extends ContextTestSupport {

    public void testSingleMulticastParallel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("AB");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    public void testMulticastParallel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);
        mock.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // they should all be AB even though A is slower than B
                assertEquals("AB", exchange.getIn().getBody(String.class));
            }
        });

        for (int i = 0; i < 20; i++) {
            template.sendBody("direct:start", "Hello");
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .multicast(new AggregationStrategy() {
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                if (oldExchange == null) {
                                    return newExchange;
                                }

                                String body = oldExchange.getIn().getBody(String.class);
                                oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
                                return oldExchange;
                            }
                        })
                        .parallelProcessing().to("direct:a", "direct:b")
                    // use end to indicate end of multicast route
                    .end()
                    .to("mock:result");

                from("direct:a").delay(100).setBody(constant("A"));

                from("direct:b").setBody(constant("B"));
            }
        };
    }
}
