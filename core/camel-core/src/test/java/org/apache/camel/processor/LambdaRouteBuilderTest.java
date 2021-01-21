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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaRouteBuilderTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLambda() throws Exception {
        assertEquals(0, context.getRoutesSize());

        LambdaRouteBuilder builder = rb -> rb.from("direct:start").to("mock:result");
        context.addRoutes(new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                builder.accept(this);
            }
        });
        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLambdaTwo() throws Exception {
        assertEquals(0, context.getRoutesSize());

        RouteBuilder.addRoutes(context, rb -> rb.from("direct:start").to("mock:result"));

        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLambdaSimple() throws Exception {
        assertEquals(0, context.getRoutesSize());

        RouteBuilder.addRoutes(context, rb -> rb.from("direct:start").transform(rb.simple("Hello ${body}")).to("mock:result"));

        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

}
