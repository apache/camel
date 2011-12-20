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
 * @version 
 */
public class EnricherRouteNumberOfProcessorTest extends ContextTestSupport {

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
                    .enrich("direct:enrich", new AggregationStrategy() {
                        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                            // should always be in
                            String body = newExchange.getIn().getBody(String.class);
                            assertNotNull(body);
                            return newExchange;
                        }
                    })
                    .to("mock:foo")
                    .end()
                    .to("mock:result");

                from("direct:enrich")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            assertFalse("Should not have out", failed);
                            String s = exchange.getIn().getBody(String.class);
                            exchange.getIn().setBody("Hi " + s);
                        }
                    });
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hi Claus");
        getMockEndpoint("mock:foo").expectedHeaderReceived("id", 1);

        template.requestBodyAndHeader("direct:start", "Claus", "id", 1);

        assertMockEndpointsSatisfied();
    }

    public void testThreeProcesssors() throws Exception {
        failed = false;

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .enrich("direct:enrich", new AggregationStrategy() {
                        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                            // should always be in
                            String body = newExchange.getIn().getBody(String.class);
                            assertNotNull(body);
                            return newExchange;
                        }
                    })
                        .to("mock:foo")
                    .end()
                    .to("mock:result");

                from("direct:enrich")
                    .pipeline("log:a", "log:b")
                    .to("log:foo")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            assertFalse("Should not have out", failed);
                            String s = exchange.getIn().getBody(String.class);
                            exchange.getIn().setBody("Hi " + s);
                        }
                    });
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hi Claus");
        getMockEndpoint("mock:foo").expectedHeaderReceived("id", 1);

        template.requestBodyAndHeader("direct:start", "Claus", "id", 1);

        assertMockEndpointsSatisfied();
    }

}