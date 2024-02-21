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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class OnCompletionMoreGlobalRouteCompletionTest extends ContextTestSupport {

    @Test
    public void testSynchronizeComplete() throws Exception {
        getMockEndpoint("mock:complete").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:failure").expectedMessageCount(0);
        getMockEndpoint("mock:two").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:sync").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:sync").expectedPropertyReceived(Exchange.ON_COMPLETION, true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSynchronizeFailure() throws Exception {
        getMockEndpoint("mock:complete").expectedMessageCount(0);
        getMockEndpoint("mock:failure").expectedMessageCount(1);
        getMockEndpoint("mock:two").expectedMessageCount(1);
        getMockEndpoint("mock:sync").expectedMessageCount(1);
        getMockEndpoint("mock:sync").expectedPropertyReceived(Exchange.ON_COMPLETION, true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertEquals("Kaboom", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSynchronizeOtherComplete() throws Exception {
        getMockEndpoint("mock:complete").expectedMessageCount(0);
        getMockEndpoint("mock:failure").expectedMessageCount(0);
        getMockEndpoint("mock:two").expectedMessageCount(0);
        getMockEndpoint("mock:sync").expectedMessageCount(0);
        getMockEndpoint("mock:routeComplete").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:routeFailure").expectedMessageCount(0);
        getMockEndpoint("mock:routeAll").expectedBodiesReceived("Bye World");

        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:other", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSynchronizeOtherFailure() throws Exception {
        // The global onComplete cases should not be invoked
        getMockEndpoint("mock:complete").expectedMessageCount(0);
        getMockEndpoint("mock:failure").expectedMessageCount(0);
        getMockEndpoint("mock:two").expectedMessageCount(0);
        getMockEndpoint("mock:sync").expectedMessageCount(0);
        // The onComplete cases on the route for any outcome and for failure should be invoked
        getMockEndpoint("mock:routeComplete").expectedMessageCount(0);
        getMockEndpoint("mock:routeFailure").expectedMessageCount(1);
        getMockEndpoint("mock:routeAll").expectedMessageCount(1);

        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:other", "Kaboom");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertEquals("Kaboom", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().to("log:global").to("mock:sync");

                onCompletion().to("log:global").to("mock:two");
                onCompletion().onCompleteOnly().to("log:global").to("mock:complete");
                onCompletion().onFailureOnly().to("log:global").to("mock:failure");

                from("direct:start")
                        // no route on completion so this one uses all the global
                        // ones
                        .process(new MyProcessor()).to("mock:result");

                from("direct:other")
                        // these route completions should override the global
                        .onCompletion().onCompleteOnly().to("mock:routeComplete").end()
                        .onCompletion().onFailureOnly().to("mock:routeFailure").end()
                        .onCompletion().to("mock:routeAll").end()
                        .process(new MyProcessor()).to("mock:other");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public MyProcessor() {
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            if ("Kaboom".equals(exchange.getIn().getBody())) {
                throw new IllegalArgumentException("Kaboom");
            }
            exchange.getIn().setBody("Bye World");
        }
    }
}
