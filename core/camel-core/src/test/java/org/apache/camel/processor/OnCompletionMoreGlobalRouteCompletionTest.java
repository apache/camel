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
import org.junit.Test;

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
            template.sendBody("direct:start", "Kabom");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertEquals("Kabom", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSynchronizeOtherComplete() throws Exception {
        getMockEndpoint("mock:complete").expectedMessageCount(0);
        getMockEndpoint("mock:failure").expectedMessageCount(0);
        getMockEndpoint("mock:two").expectedMessageCount(0);
        getMockEndpoint("mock:sync").expectedMessageCount(0);
        getMockEndpoint("mock:route").expectedBodiesReceived("Bye World");

        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:other", "Hello World");

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
                    // this route completion should override the global
                    .onCompletion().to("mock:route").end().process(new MyProcessor()).to("mock:other");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public MyProcessor() {
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            if ("Kabom".equals(exchange.getIn().getBody())) {
                throw new IllegalArgumentException("Kabom");
            }
            exchange.getIn().setBody("Bye World");
        }
    }
}
