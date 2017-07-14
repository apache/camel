/**
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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RuntimeEndpointRegistry;

public class RuntimeEndpointRegistryTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setRuntimeEndpointRegistry(new DefaultRuntimeEndpointRegistry());
        return camelContext;
    }

    public void testRuntimeEndpointRegistry() throws Exception {
        RuntimeEndpointRegistry registry = context.getRuntimeEndpointRegistry();

        assertEquals(0, registry.getAllEndpoints(false).size());
        // we have 2 at the start as we have all endpoints for the route consumers
        assertEquals(2, registry.getAllEndpoints(true).size());

        MockEndpoint mock = getMockEndpoint("mock:foo2");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("seda:foo", "Hello World", "slip", "mock:foo2");
        mock.assertIsSatisfied();

        assertEquals(4, registry.getAllEndpoints(true).size());
        assertEquals(3, registry.getEndpointsPerRoute("foo", true).size());
        assertEquals(1, registry.getEndpointsPerRoute("bar", true).size());

        mock = getMockEndpoint("mock:bar2");
        mock.expectedMessageCount(1);
        template.sendBodyAndHeader("seda:bar", "Bye World", "slip", "mock:bar2");
        mock.assertIsSatisfied();

        assertEquals(6, registry.getAllEndpoints(true).size());
        assertEquals(3, registry.getEndpointsPerRoute("foo", true).size());
        assertEquals(3, registry.getEndpointsPerRoute("bar", true).size());

        // lets check the json
        String json = context.createRouteStaticEndpointJson(null);
        assertNotNull(json);
        log.info(json);

        assertTrue("Should have outputs", json.contains(" { \"uri\": \"mock://foo\" }"));
        assertTrue("Should have outputs", json.contains(" { \"uri\": \"mock://foo2\" }"));
        assertTrue("Should have outputs", json.contains(" { \"uri\": \"mock://bar\" }"));
        assertTrue("Should have outputs", json.contains(" { \"uri\": \"mock://bar2\" }"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo")
                    .to("mock:foo")
                    .recipientList(header("slip"));

                from("seda:bar").routeId("bar")
                    .to("mock:bar")
                    .recipientList(header("slip"));
            }
        };
    }
}
