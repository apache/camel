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
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class AdviceWithOnCompletionRemoveTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAdviceOnCompletionRemove() throws Exception {
        context.addRoutes(createRouteBuilder());

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:done").expectedMessageCount(0);

        AdviceWith.adviceWith(context.getRouteDefinition("foo"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("myCompletion").remove();
            }
        });

        context.start();

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceOnCompletionReplace() throws Exception {
        context.addRoutes(createRouteBuilder());

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:done").expectedMessageCount(0);
        getMockEndpoint("mock:done2").expectedMessageCount(1);

        AdviceWith.adviceWith(context.getRouteDefinition("foo"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("myCompletion").replace().onCompletion().to("mock:done2");
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
                onCompletion().id("myCompletion").transform(constant("Bye World")).to("mock:done");

                from("direct:bar").routeId("bar").to("mock:c").to("mock:d");

                from("direct:foo").routeId("foo").to("mock:a").to("mock:b");

            }
        };
    }

}
