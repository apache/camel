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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit test for intercepting sending to endpoint with dynamic endpoints and uri matching
 */
public class InterceptSendToEndpointDynamicTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSendToWildcard() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: e1
                // intercept sending to any file endpoint, send it to our mock
                // instead
                // and do not send it to the original intended endpoint
                interceptSendToEndpoint("file:*").skipSendToOriginalEndpoint().to("mock:detour");

                from("direct:first").to("file://foo").to("file://bar").to("mock:result");
                // END SNIPPET: e1

            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:first", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendToWildcardHeaderUri() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("file:*").skipSendToOriginalEndpoint().to("mock:detour");

                from("direct:first").to("file://foo").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedMessageCount(1);
        getMockEndpoint("mock:detour").expectedPropertyReceived(Exchange.INTERCEPTED_ENDPOINT, "file://foo");
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:first", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendToRegex() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: e1
                // intercept sending to, either ./foo or ./bar directory
                interceptSendToEndpoint("^file:.*(foo|bar)$").skipSendToOriginalEndpoint().to("mock:detour");

                from("direct:first").to(fileUri("foo")).to(fileUri("bar")).to(fileUri("cheese"))
                        .to("mock:result");
                // END SNIPPET: e1
            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedFileExists(testFile("cheese/cheese.txt"));

        template.sendBodyAndHeader("direct:first", "Hello World", Exchange.FILE_NAME, "cheese.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendToDynamicEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("file:*").to("mock:detour");

                from("direct:first").process(new Processor() {
                    public void process(Exchange exchange) {
                        // we use a dynamic endpoint URI that Camel does not
                        // know beforehand
                        // but it should still be intercepted as we intercept
                        // all file endpoints
                        template.sendBodyAndHeader(fileUri("foo"), "Hello Foo", Exchange.FILE_NAME, "foo.txt");
                    }
                }).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:detour").expectedBodiesReceived("Hello Foo");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedFileExists(testFile("foo/foo.txt"));

        template.sendBody("direct:first", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
