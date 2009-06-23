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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * @version $Revision$
 */
public class AggregatorRouteNumberOfProcessorTest extends ContextTestSupport {

    private static volatile boolean failed;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testOneProcesssor() throws Exception {
        failed = false;

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new AggregationStrategy() {
                        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                                // should always be in
                            String body = newExchange.getIn().getBody(String.class);
                            assertNotNull(body);
                            // should not have an out
                            failed = newExchange.hasOut();
                            return newExchange;
                        }
                    }).batchSize(2)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertFalse("Should not have out", failed);
                                String s = exchange.getIn().getBody(String.class);
                                exchange.getIn().setBody("Hi " + s);
                                context.createProducerTemplate().send("mock:foo", exchange);
                            }
                        })
                        .end()
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hi Willem");

        template.requestBodyAndHeader("direct:start", "Claus", "id", 1);
        template.requestBodyAndHeader("direct:start", "Willem", "id", 1);

        assertMockEndpointsSatisfied();
    }
    
    public void testThreeProcesssors() throws Exception {
        failed = false;

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new AggregationStrategy() {
                        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                            // should always be in
                            String body = newExchange.getIn().getBody(String.class);
                            assertNotNull(body);
                            // should not have an out
                            failed = newExchange.hasOut();
                            return newExchange;
                        }
                    }).batchSize(2)
                        .to("log:foo")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertFalse("Should not have out", failed);
                                String s = exchange.getIn().getBody(String.class);
                                exchange.getIn().setBody("Hi " + s);
                                context.createProducerTemplate().send("mock:foo", exchange);
                            }
                        }).to("mock:agg")
                    .end()
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        getMockEndpoint("mock:agg").expectedBodiesReceived("Hi Willem");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hi Willem");

        template.requestBodyAndHeader("direct:start", "Claus", "id", 1);
        template.requestBodyAndHeader("direct:start", "Willem", "id", 1);

        assertMockEndpointsSatisfied();

        assertFalse("Should not have out", failed);
    }

}
