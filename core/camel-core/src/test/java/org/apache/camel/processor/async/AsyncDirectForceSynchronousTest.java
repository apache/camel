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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncDirectForceSynchronousTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAsyncEndpoint() throws Exception {
        context.addRoutes(createRouteBuilder(false));
        context.start();

        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should NOT use same threads");
    }

    @Test
    public void testAsyncEndpointForced() throws Exception {
        context.addRoutes(createRouteBuilder(true));
        context.start();

        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertTrue(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use same threads");
    }

    protected RouteBuilder createRouteBuilder(boolean sync) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        .to("mock:before")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                beforeThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("direct:foo?synchronous=" + sync)
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("mock:after");

                from("direct:foo")
                        .to("async:bye:camel");
            }
        };
    }

}
