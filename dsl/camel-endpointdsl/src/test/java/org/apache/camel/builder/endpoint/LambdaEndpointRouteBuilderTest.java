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
package org.apache.camel.builder.endpoint;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaEndpointRouteBuilderTest extends BaseEndpointDslTest {

    @Test
    public void testLambda() throws Exception {
        assertEquals(0, context.getRoutesSize());

        LambdaEndpointRouteBuilder builder = rb -> rb.from(rb.direct("start")).to(rb.mock("result"));
        context.addRoutes(new EndpointRouteBuilder(context) {
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

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testLambdaTwo() throws Exception {
        assertEquals(0, context.getRoutesSize());

        EndpointRouteBuilder.addEndpointRoutes(context, rb -> rb.from(rb.direct("start")).to(rb.mock("result")));

        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testLambdaSimple() throws Exception {
        assertEquals(0, context.getRoutesSize());

        EndpointRouteBuilder.addEndpointRoutes(context,
                rb -> rb.from(rb.direct("start")).transform(rb.simple("Hello ${body}")).to(rb.mock("result")));

        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

}
