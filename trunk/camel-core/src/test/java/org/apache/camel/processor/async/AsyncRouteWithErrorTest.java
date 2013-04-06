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
package org.apache.camel.processor.async;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class AsyncRouteWithErrorTest extends ContextTestSupport {

    private static String route = "";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        route = "";
    }

    public void testAsyncRouteWithError() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        // send a request reply to the direct start endpoint
        try {
            template.requestBody("direct:start", "Hello");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            // expected an execution exception
            assertEquals("Damn forced by unit test", e.getCause().getMessage());
        }

        // we should run before the async processor that sets B
        route += "A";

        assertMockEndpointsSatisfied();

        assertEquals("BA", route);
    }

    public void testAsyncRouteWithTypeConverted() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        // send a request reply to the direct start endpoint, but will use
        // future type converter that will wait for the response
        try {
            template.requestBody("direct:start", "Hello", String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // expected an execution exception
            assertEquals("Damn forced by unit test", e.getCause().getMessage());
        }

        // we should wait for the async response as we ask for the result as a String body
        route += "A";

        assertMockEndpointsSatisfied();

        assertEquals("BA", route);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we start this route async
                from("direct:start")
                            // we play a bit with the message
                        .transform(body().append(" World"))
                            // now turn the route into async from this point forward
                            // the caller will have a Future<Exchange> returned as response in OUT
                            // to be used to grap the async response when he fell like it
                        .threads()
                            // from this point forward this is the async route doing its work
                            // so we do a bit of delay to simulate heavy work that takes time
                        .to("mock:foo")
                        .delay(100)
                            // and we also work with the message so we can prepare a response
                        .process(new MyProcessor())
                            // and we use mocks for unit testing
                        .to("mock:result");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public MyProcessor() {
        }

        public void process(Exchange exchange) throws Exception {
            route += "B";
            assertEquals("Hello World", exchange.getIn().getBody());
            throw new IllegalArgumentException("Damn forced by unit test");
        }
    }
}