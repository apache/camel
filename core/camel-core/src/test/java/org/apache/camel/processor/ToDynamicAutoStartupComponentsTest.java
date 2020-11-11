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
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.model.ToDynamicDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ToDynamicAutoStartupComponentsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAutoStartupFalse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ToDynamicDefinition toD = new ToDynamicDefinition("mock:${header.foo}");
                toD.setAutoStartComponents("false");

                from("direct:start").getOutputs().add(toD);
            }
        });
        context.start();

        assertNull(context.hasComponent("mock"));

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Camel");

        template.sendBodyAndHeader("direct:start", "Hello Camel", "foo", "foo");

        assertMockEndpointsSatisfied();

        MockComponent comp = (MockComponent) context.hasComponent("mock");
        assertNotNull(comp);
        assertTrue(comp.getStatus().isStarted());
    }

    @Test
    public void testAutoStartupTrue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ToDynamicDefinition toD = new ToDynamicDefinition("mock:${header.foo}");
                toD.setAutoStartComponents("true");

                from("direct:start").getOutputs().add(toD);
            }
        });
        context.start();

        MockComponent comp = (MockComponent) context.hasComponent("mock");
        assertNotNull(comp);
        assertTrue(comp.getStatus().isStarted());

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Camel");

        template.sendBodyAndHeader("direct:start", "Hello Camel", "foo", "foo");

        assertMockEndpointsSatisfied();
    }

}
