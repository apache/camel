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
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

public class AdviceWithErrorHandlerRemoveTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAdviceErrorHandlerRemove() throws Exception {
        context.addRoutes(createRouteBuilder());

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        RouteReifier.adviceWith(context.getRouteDefinition("foo"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                getOriginalRoute().errorHandler(noErrorHandler());
            }
        });

        context.start();

        try {
            template.sendBody("direct:foo", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            assertEquals("Forced", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceErrorHandlerReplace() throws Exception {
        context.addRoutes(createRouteBuilder());

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:dead2").expectedMessageCount(1);

        RouteReifier.adviceWith(context.getRouteDefinition("foo"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // override errorHandler by using on exception
                weaveAddFirst().onException(Exception.class).handled(true).to("mock:dead2");
            }
        });

        context.start();

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:c").to("mock:d");

                from("direct:foo").routeId("foo").errorHandler(deadLetterChannel("mock:dead")).to("mock:a").throwException(new IllegalArgumentException("Forced")).to("mock:b");
            }
        };
    }

}
