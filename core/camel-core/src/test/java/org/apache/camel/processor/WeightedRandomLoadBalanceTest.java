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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class WeightedRandomLoadBalanceTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRandom() throws Exception {
        x.expectedMessageCount(4);
        y.expectedMessageCount(2);
        z.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            public void configure() {

                // START SNIPPET: example
                from("direct:start").loadBalance().weighted(false, "4,2,1").to("mock:x", "mock:y", "mock:z");
                // END SNIPPET: example
            }
        });
        context.start();

        sendMessages(1, 2, 3, 4, 5, 6, 7);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRandom2() throws Exception {
        x.expectedMessageCount(2);
        y.expectedMessageCount(1);
        z.expectedMessageCount(3);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:start").loadBalance().weighted(false, "2, 1, 3", ",").to("mock:x", "mock:y", "mock:z");
                // END SNIPPET: example
            }
        });
        context.start();

        sendMessages(1, 2, 3, 4, 5, 6);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRandomBulk() throws Exception {
        x.expectedMessageCount(10);
        y.expectedMessageCount(15);
        z.expectedMessageCount(25);

        context.addRoutes(new RouteBuilder() {
            public void configure() {

                // START SNIPPET: example
                from("direct:start").loadBalance().weighted(false, "2-3-5", "-").to("mock:x", "mock:y", "mock:z");
                // END SNIPPET: example
            }
        });
        context.start();

        sendBulkMessages(50);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmatchedRatiosToProcessors() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    // START SNIPPET: example
                    from("direct:start").loadBalance().weighted(false, "2,3").to("mock:x", "mock:y", "mock:z");
                    // END SNIPPET: example
                }
            });
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("Loadbalacing with 3 should match number of distributions 2", iae.getMessage());
        }
    }

    protected void sendBulkMessages(int number) {
        for (int i = 0; i < number; i++) {
            template.sendBodyAndHeader("direct:start", createTestMessage(i), "counter", i);
        }
    }

    protected void sendMessages(int... counters) {
        for (int counter : counters) {
            template.sendBodyAndHeader("direct:start", createTestMessage(counter), "counter", counter);
        }
    }

    private String createTestMessage(int counter) {
        return "<message>" + counter + "</message>";
    }

}
