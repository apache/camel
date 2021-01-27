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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChoiceNoOtherwiseTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint end;

    @Test
    public void testNoOtherwise() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when().simple("${header.foo} == 'bar'").to("mock:x").end().to("mock:end");
            }
        });
        context.start();

        x.expectedBodiesReceived("a");
        end.expectedBodiesReceived("a", "b");

        sendMessage("bar", "a");
        sendMessage("cheese", "b");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoOtherwiseTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when(simple("${header.foo} == 'bar'")).to("mock:x").end().to("mock:end");
            }
        });
        context.start();

        x.expectedBodiesReceived("a");
        end.expectedBodiesReceived("a", "b");

        sendMessage("bar", "a");
        sendMessage("cheese", "b");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptyOtherwise() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when().simple("${header.foo} == 'bar'").to("mock:x").otherwise().end().to("mock:end");
            }
        });
        context.start();

        x.expectedBodiesReceived("a");
        end.expectedBodiesReceived("a", "b");

        sendMessage("bar", "a");
        sendMessage("cheese", "b");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptyOtherwiseTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when(simple("${header.foo} == 'bar'")).to("mock:x").otherwise().end().to("mock:end");
            }
        });
        context.start();

        x.expectedBodiesReceived("a");
        end.expectedBodiesReceived("a", "b");

        sendMessage("bar", "a");
        sendMessage("cheese", "b");

        assertMockEndpointsSatisfied();
    }

    protected void sendMessage(final Object headerValue, final Object body) throws Exception {
        template.sendBodyAndHeader("direct:start", body, "foo", headerValue);
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        end = getMockEndpoint("mock:end");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
