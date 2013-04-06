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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * @version 
 */
public class SplitRouteNumberOfProcessorTest extends ContextTestSupport {

    private static AtomicBoolean failed = new AtomicBoolean();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testOneProcessor() throws Exception {
        failed.set(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(","), new AggregationStrategy() {
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
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertFalse("Should not have out", failed.get());
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
        result.expectedMessageCount(1);

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hi Claus", "Hi Willem");

        template.requestBodyAndHeader("direct:start", "Claus,Willem", "id", 1);

        assertMockEndpointsSatisfied();
    }

    public void testThreeProcessors() throws Exception {
        failed.set(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(","), new AggregationStrategy() {
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
                        .pipeline("log:a", "log:b")
                        .to("log:foo")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertFalse("Should not have out", failed.get());
                                String s = exchange.getIn().getBody(String.class);
                                exchange.getIn().setBody("Hi " + s);
                                context.createProducerTemplate().send("mock:foo", exchange);
                            }
                        })
                        .to("mock:split")
                    .end()
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hi Claus", "Hi Willem");

        template.requestBodyAndHeader("direct:start", "Claus,Willem", "id", 1);

        assertMockEndpointsSatisfied();
    }

}