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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JettyFailoverRoundRobinTest extends CamelTestSupport {
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
    void testJettyFailoverRoundRobin() throws Exception {
        getMockEndpoint("mock:bad").expectedMessageCount(1);
        getMockEndpoint("mock:bad2").expectedMessageCount(1);
        getMockEndpoint("mock:good").expectedMessageCount(1);
        getMockEndpoint("mock:good2").expectedMessageCount(0);

        String reply = template.requestBody("direct:start", null, String.class);
        assertEquals("Good", reply);

        MockEndpoint.assertIsSatisfied(context);

        // reset mocks and send a message again to see that round robin
        // continue where it should
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:bad").expectedMessageCount(0);
        getMockEndpoint("mock:bad2").expectedMessageCount(0);
        getMockEndpoint("mock:good").expectedMessageCount(0);
        getMockEndpoint("mock:good2").expectedMessageCount(1);

        reply = template.requestBody("direct:start", null, String.class);
        assertEquals("Also good", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: e1
                from("direct:start")
                        // load balance using failover in round robin mode.
                        // Also do not inherit error handler which means the failover LB will not fallback
                        // and use error handler but trigger failover to next endpoint immediately.
                        // -1 is to indicate that failover LB should newer exhaust and keep trying
                        .loadBalance().failover(-1, false, true)
                        // this is the four endpoints we will load balance with failover
                        .to(hbad, hbad2, hgood, hgood2);
                // END SNIPPET: e1

                from(bad)
                        .to("mock:bad")
                        .process(exchange -> {
                            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                            exchange.getIn().setBody("Something bad happened");
                        });

                from(bad2)
                        .to("mock:bad2")
                        .process(exchange -> {
                            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                            exchange.getIn().setBody("Not found");
                        });

                from(good)
                        .to("mock:good")
                        .process(exchange -> exchange.getIn().setBody("Good"));

                from(good2)
                        .to("mock:good2")
                        .process(exchange -> exchange.getIn().setBody("Also good"));
            }
        };
    }
}
