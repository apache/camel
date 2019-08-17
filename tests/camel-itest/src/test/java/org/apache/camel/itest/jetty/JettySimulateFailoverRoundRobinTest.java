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
package org.apache.camel.itest.jetty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JettySimulateFailoverRoundRobinTest extends CamelTestSupport {

    private static int port1 = AvailablePortFinder.getNextAvailable();
    private static int port2 = AvailablePortFinder.getNextAvailable();
    private static int port3 = AvailablePortFinder.getNextAvailable();
    private static int port4 = AvailablePortFinder.getNextAvailable();

    private String bad = "jetty:http://localhost:" + port1 + "/bad";
    private String bad2 = "jetty:http://localhost:" + port2 + "/bad2";
    private String good = "jetty:http://localhost:" + port3 + "/good";
    private String good2 = "jetty:http://localhost:" + port4 + "/good2";
    private String hbad = "http://localhost:" + port1 + "/bad";
    private String hbad2 = "http://localhost:" + port2 + "/bad2";
    private String hgood = "http://localhost:" + port3 + "/good";
    private String hgood2 = "http://localhost:" + port4 + "/good2";

    @Test
    public void testJettySimulateFailoverRoundRobin() throws Exception {
        getMockEndpoint("mock:bad").expectedMessageCount(1);
        getMockEndpoint("mock:bad2").expectedMessageCount(1);
        getMockEndpoint("mock:good").expectedMessageCount(1);
        getMockEndpoint("mock:good2").expectedMessageCount(0);

        String reply = template.requestBody("direct:start", null, String.class);
        assertEquals("Good", reply);

        assertMockEndpointsSatisfied();

        // reset mocks and send a message again to see that round robin
        // continue where it should
        resetMocks();

        getMockEndpoint("mock:bad").expectedMessageCount(0);
        getMockEndpoint("mock:bad2").expectedMessageCount(0);
        getMockEndpoint("mock:good").expectedMessageCount(0);
        getMockEndpoint("mock:good2").expectedMessageCount(1);

        reply = template.requestBody("direct:start", null, String.class);
        assertEquals("Also good", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .process(new MyFailoverLoadBalancer(template, hbad, hbad2, hgood, hgood2));

                from(bad)
                    .to("mock:bad")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                            exchange.getIn().setBody("Something bad happened");
                        }
                    });

                from(bad2)
                    .to("mock:bad2")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                            exchange.getIn().setBody("Not found");
                        }
                    });

                from(good)
                    .to("mock:good")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setBody("Good");
                        }
                    });

                from(good2)
                    .to("mock:good2")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setBody("Also good");
                        }
                    });
            }
        };
    }

    /**
     * A custom failover processor
     */
    public static class MyFailoverLoadBalancer implements Processor {

        private final ProducerTemplate template;
        private final List<String> endpoints;
        private int counter = -1;

        public MyFailoverLoadBalancer(ProducerTemplate template, String... endpoints) {
            this.template = template;
            this.endpoints = new ArrayList<>(Arrays.asList(endpoints));
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            boolean done = false;
            while (!done) {
                // pick endpoint
                if (++counter >= endpoints.size()) {
                    counter = 0;
                }
                String endpoint = endpoints.get(counter);

                // process exchange
                try {
                    template.send(endpoint, exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // check whether we are done or prepare for failover
                done = exchange.getException() == null;
                if (!done) {
                    prepareExchangeForFailover(exchange);
                }
            }
        }

        private void prepareExchangeForFailover(Exchange exchange) {
            exchange.setException(null);

            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, null);
            exchange.setProperty(Exchange.FAILURE_HANDLED, null);
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, null);
            exchange.getIn().removeHeader(Exchange.REDELIVERED);
            exchange.getIn().removeHeader(Exchange.REDELIVERY_COUNTER);
        }

    }

}