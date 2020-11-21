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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointRegistryKeepRouteEndpointsRemoteRouteTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getGlobalOptions().put(Exchange.MAXIMUM_ENDPOINT_CACHE_SIZE, "20");
        return context;
    }

    @Test
    public void testEndpointRegistryKeepRouteEndpointsRemoveRoute() throws Exception {
        assertNotNull(context.hasEndpoint("direct://start"));
        assertNotNull(context.hasEndpoint("log://start"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNotNull(context.hasEndpoint("direct://bar"));
        assertNotNull(context.hasEndpoint("log://bar"));

        assertEquals(6, context.getEndpointRegistry().staticSize());

        // we dont have this endpoint yet
        assertNull(context.hasEndpoint("mock://unknown0"));

        for (int i = 0; i < 50; i++) {
            template.sendBody("mock:unknown" + i, "Hello " + i);
        }

        assertEquals(6, context.getEndpointRegistry().staticSize());
        assertNotNull(context.hasEndpoint("direct://start"));
        assertNotNull(context.hasEndpoint("log://start"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNotNull(context.hasEndpoint("direct://bar"));
        assertNotNull(context.hasEndpoint("log://bar"));

        // now stop and remove the bar route
        context.getRouteController().stopRoute("bar");
        context.removeRoute("bar");

        assertEquals(4, context.getEndpointRegistry().staticSize());
        assertNotNull(context.hasEndpoint("direct://start"));
        assertNotNull(context.hasEndpoint("log://start"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNull(context.hasEndpoint("direct://bar"));
        assertNull(context.hasEndpoint("log://bar"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").to("log:start").to("log:foo").to("mock:result");

                from("direct:bar").routeId("bar").to("log:bar");
            }
        };
    }

}
