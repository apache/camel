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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class OnCompletionBeforeConsumerModeIssueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOnCompletionTopMode() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .onCompletion().modeBeforeConsumer()
                        .to("mock:end")
                    .end()
                    .transform(constant("a"))
                    .to("mock:a")
                    .to("direct:sub")
                    .transform(constant("c"))
                    .to("mock:c");

                from("direct:sub")
                        .transform(constant("b"))
                        .to("mock:b");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("a");
        getMockEndpoint("mock:b").expectedBodiesReceived("b");
        getMockEndpoint("mock:c").expectedBodiesReceived("c");
        getMockEndpoint("mock:end").expectedBodiesReceived("c");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnCompletionEndMode() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transform(constant("a"))
                    .to("mock:a")
                    .to("direct:sub")
                    .transform(constant("c"))
                    .to("mock:c")
                    .onCompletion().modeBeforeConsumer()
                        .to("mock:end")
                    .end();

                from("direct:sub")
                        .transform(constant("b"))
                        .to("mock:b");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("a");
        getMockEndpoint("mock:b").expectedBodiesReceived("b");
        getMockEndpoint("mock:c").expectedBodiesReceived("c");
        getMockEndpoint("mock:end").expectedBodiesReceived("c");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnCompletionTop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .onCompletion()
                        .to("mock:end")
                    .end()
                    .transform(constant("a"))
                    .to("mock:a")
                    .to("direct:sub")
                    .transform(constant("c"))
                    .to("mock:c");

                from("direct:sub")
                        .transform(constant("b"))
                        .to("mock:b");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("a");
        getMockEndpoint("mock:b").expectedBodiesReceived("b");
        getMockEndpoint("mock:c").expectedBodiesReceived("c");
        getMockEndpoint("mock:end").expectedBodiesReceived("c");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnCompletionEnd() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .transform(constant("a"))
                    .to("mock:a")
                    .to("direct:sub")
                    .transform(constant("c"))
                    .to("mock:c")
                    .onCompletion()
                        .to("mock:end")
                    .end();

                from("direct:sub")
                        .transform(constant("b"))
                        .to("mock:b");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("a");
        getMockEndpoint("mock:b").expectedBodiesReceived("b");
        getMockEndpoint("mock:c").expectedBodiesReceived("c");
        getMockEndpoint("mock:end").expectedBodiesReceived("c");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnCompletionGlobalMode() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().modeBeforeConsumer().to("mock:end");

                from("direct:start")
                        .transform(constant("a"))
                        .to("mock:a")
                        .to("direct:sub")
                        .transform(constant("c"))
                        .to("mock:c");

                from("direct:sub")
                        .transform(constant("b"))
                        .to("mock:b");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("a");
        getMockEndpoint("mock:b").expectedBodiesReceived("b");
        getMockEndpoint("mock:c").expectedBodiesReceived("c");
        getMockEndpoint("mock:end").expectedBodiesReceived("c");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnCompletionGlobal() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onCompletion().to("mock:end");

                from("direct:start")
                        .transform(constant("a"))
                        .to("mock:a")
                        .to("direct:sub")
                        .transform(constant("c"))
                        .to("mock:c");

                from("direct:sub")
                        .transform(constant("b"))
                        .to("mock:b");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedBodiesReceived("a");
        getMockEndpoint("mock:b").expectedBodiesReceived("b");
        getMockEndpoint("mock:c").expectedBodiesReceived("c");
        getMockEndpoint("mock:end").expectedBodiesReceived("c");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
