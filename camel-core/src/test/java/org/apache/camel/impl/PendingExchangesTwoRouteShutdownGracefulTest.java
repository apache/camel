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
package org.apache.camel.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class PendingExchangesTwoRouteShutdownGracefulTest extends ContextTestSupport {

    private static String foo = "";
    private static String bar = "";
    private static CountDownLatch latch = new CountDownLatch(2);

    public void testShutdownGraceful() throws Exception {
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:bar").expectedMinimumMessageCount(1);

        template.sendBody("seda:foo", "A");
        template.sendBody("seda:foo", "B");
        template.sendBody("seda:foo", "C");
        template.sendBody("seda:foo", "D");
        template.sendBody("seda:foo", "E");

        template.sendBody("seda:bar", "A");
        template.sendBody("seda:bar", "B");
        template.sendBody("seda:bar", "C");
        template.sendBody("seda:bar", "D");
        template.sendBody("seda:bar", "E");

        assertMockEndpointsSatisfied();

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        context.stop();

        // it should wait as there were 2 inflight exchanges and 8 pending messages left
        assertEquals("Should graceful shutdown", "ABCDE", foo);
        assertEquals("Should graceful shutdown", "ABCDE", bar);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").to("mock:foo").delay(100).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        foo = foo + exchange.getIn().getBody(String.class);
                        latch.countDown();
                    }
                });

                from("seda:bar").to("mock:bar").delay(50).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        bar = bar + exchange.getIn().getBody(String.class);
                        latch.countDown();
                    }
                });
            }
        };
    }
}