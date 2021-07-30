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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChoiceNestedTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint x1;
    protected MockEndpoint x2;
    protected MockEndpoint y;
    protected MockEndpoint z;
    protected MockEndpoint end;

    @Test
    public void testNestedX() throws Exception {
        context.addRoutes(createNestedChoice());
        context.start();

        x.expectedBodiesReceived(800);
        end.expectedBodiesReceived(800);

        sendMessage(800);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedY() throws Exception {
        context.addRoutes(createNestedChoice());
        context.start();

        y.expectedBodiesReceived(600);
        end.expectedBodiesReceived(600);

        sendMessage(600);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedZ() throws Exception {
        context.addRoutes(createNestedChoice());
        context.start();

        z.expectedBodiesReceived(300);
        end.expectedBodiesReceived(300);

        sendMessage(300);

        assertMockEndpointsSatisfied();
    }


    @Test
    public void testNestedMulticastX() throws Exception {
        context.addRoutes(createNesteddMulticast());
        context.start();

        x1.expectedBodiesReceived(800);
        x2.expectedBodiesReceived(800);
        end.expectedBodiesReceived(800);

        sendMessage(800);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedMulticastY() throws Exception {
        context.addRoutes(createNesteddMulticast());
        context.start();

        y.expectedBodiesReceived(600);
        end.expectedBodiesReceived(600);

        sendMessage(600);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedMulticastZ() throws Exception {
        context.addRoutes(createNesteddMulticast());
        context.start();

        z.expectedBodiesReceived(300);
        end.expectedBodiesReceived(300);

        sendMessage(300);

        assertMockEndpointsSatisfied();
    }

    private RoutesBuilder createNesteddMulticast() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .choice()
                        .when(simple("${body} > 500"))
                        .choice()
                        .when(simple("${body} > 750")).multicast().to("mock:x1").to("mock:x2").endChoice()
                        .otherwise().to("mock:y").endChoice()
                        .end()
                        .endChoice()
                        .otherwise().to("mock:z").endChoice()
                        .end()
                        .to("mock:end");
            }
        };
    }

    private RoutesBuilder createNestedChoice() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .choice()
                        .when(simple("${body} > 500"))
                        .choice()
                        .when(simple("${body} > 750")).to("mock:x").endChoice()
                        .otherwise().to("mock:y").endChoice()
                        .end()
                        .endChoice()
                        .otherwise().to("mock:z").endChoice()
                        .end()
                        .to("mock:end");
            }
        };
    }


    protected void sendMessage(final Object body) throws Exception {
        template.sendBody("direct:start", body);
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        x1 = getMockEndpoint("mock:x1");
        x2 = getMockEndpoint("mock:x2");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
        end = getMockEndpoint("mock:end");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
