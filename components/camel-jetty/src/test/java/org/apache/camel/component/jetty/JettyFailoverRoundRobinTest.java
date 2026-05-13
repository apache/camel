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

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JettyFailoverRoundRobinTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port1 = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port2 = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port3 = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port4 = AvailablePortFinder.find();

    @Test
    void testJettyFailoverRoundRobin() throws Exception {
        // Send two requests through the failover round-robin load balancer.
        // The round-robin starting index is not guaranteed, so we verify
        // that both good endpoints are reached across the two requests
        // (one each), confirming both failover and round-robin behavior.
        String reply1 = template.requestBody("direct:start", null, String.class);
        assertTrue("Good".equals(reply1) || "Also good".equals(reply1),
                "Expected 'Good' or 'Also good' but was: " + reply1);

        String reply2 = template.requestBody("direct:start", null, String.class);
        assertTrue("Good".equals(reply2) || "Also good".equals(reply2),
                "Expected 'Good' or 'Also good' but was: " + reply2);

        Set<String> replies = new HashSet<>();
        replies.add(reply1);
        replies.add(reply2);
        assertEquals(2, replies.size(),
                "Round robin should hit both good endpoints, but got: " + reply1 + " and " + reply2);
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
                        .to("http://localhost:" + port1.getPort() + "/bad",
                                "http://localhost:" + port2.getPort() + "/bad2",
                                "http://localhost:" + port3.getPort() + "/good",
                                "http://localhost:" + port4.getPort() + "/good2");
                // END SNIPPET: e1

                from("jetty:http://localhost:" + port1.getPort() + "/bad")
                        .to("mock:bad")
                        .process(exchange -> {
                            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                            exchange.getIn().setBody("Something bad happened");
                        });

                from("jetty:http://localhost:" + port2.getPort() + "/bad2")
                        .to("mock:bad2")
                        .process(exchange -> {
                            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                            exchange.getIn().setBody("Not found");
                        });

                from("jetty:http://localhost:" + port3.getPort() + "/good")
                        .to("mock:good")
                        .process(exchange -> exchange.getIn().setBody("Good"));

                from("jetty:http://localhost:" + port4.getPort() + "/good2")
                        .to("mock:good2")
                        .process(exchange -> exchange.getIn().setBody("Also good"));
            }
        };
    }
}
