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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MulticastParallelStopOnExceptionTest extends ContextTestSupport {
    private ExecutorService service;

    @Override
    @Before
    public void setUp() throws Exception {
        // use a pool with 2 concurrent tasks so we cannot run too fast
        service = Executors.newFixedThreadPool(2);
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        service.shutdownNow();
    }

    @Test
    public void testMulticastParallelStopOnExceptionOk() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:baz").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMulticastParalllelStopOnExceptionStop() throws Exception {
        // we run in parallel so we may get 0 or 1 messages
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(0);
        getMockEndpoint("mock:bar").expectedMinimumMessageCount(0);
        getMockEndpoint("mock:baz").expectedMinimumMessageCount(0);
        // we should not complete and thus 0
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            CamelExchangeException cause = assertIsInstanceOf(CamelExchangeException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Multicast processing failed for number "));
            assertEquals("Forced", cause.getCause().getMessage());

            String body = cause.getExchange().getIn().getBody(String.class);
            assertTrue(body.contains("Kaboom"));
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start").multicast().parallelProcessing().stopOnException().executorService(service).to("direct:foo", "direct:bar", "direct:baz").end()
                    .to("mock:result");

                // need a little delay to slow these okays down so we better can
                // test stop when parallel
                from("direct:foo").delay(1000).to("mock:foo");

                from("direct:bar").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if ("Kaboom".equals(body)) {
                            throw new IllegalArgumentException("Forced");
                        }
                    }
                }).to("mock:bar");

                // need a little delay to slow these okays down so we better can
                // test stop when parallel
                from("direct:baz").delay(1000).to("mock:baz");
            }
        };
    }
}
