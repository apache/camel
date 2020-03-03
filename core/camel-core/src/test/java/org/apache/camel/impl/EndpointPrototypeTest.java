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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.LifecycleStrategySupport;
import org.junit.Test;

public class EndpointPrototypeTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGetPrototype() throws Exception {
        context.start();

        assertEquals(0, context.getEndpointRegistry().size());

        context.getEndpoint("mock:foo");

        assertEquals(1, context.getEndpointRegistry().size());

        // now get a prototype which should not be added

        Endpoint prototype = context.adapt(ExtendedCamelContext.class).getPrototypeEndpoint("mock:bar");
        assertNotNull(prototype);

        // and should be started
        MockEndpoint bar = assertIsInstanceOf(MockEndpoint.class, prototype);
        assertTrue(bar.getStatus().isStarted());

        // but registry is still at 1
        assertEquals(1, context.getEndpointRegistry().size());

        // and now at 2
        context.getEndpoint("mock:foo2");
        assertEquals(2, context.getEndpointRegistry().size());

        context.stop();

        // should not be stopped as we need to handle that ourselves due to prototype scoped
        assertFalse(bar.getStatus().isStopped());
        bar.stop();
        assertTrue(bar.getStatus().isStopped());
    }

    @Test
    public void testGetPrototypeNoLifecycleStrategy() throws Exception {
        final List<Endpoint> endpoints = new ArrayList<>();

        LifecycleStrategySupport dummy = new LifecycleStrategySupport() {
            @Override
            public void onEndpointAdd(Endpoint endpoint) {
                endpoints.add(endpoint);
            }
        };

        context.addLifecycleStrategy(dummy);
        context.start();

        assertEquals(0, context.getEndpointRegistry().size());

        context.getEndpoint("mock:foo");

        assertEquals(1, context.getEndpointRegistry().size());

        // now get a prototype which should not be added

        Endpoint prototype = context.adapt(ExtendedCamelContext.class).getPrototypeEndpoint("mock:bar");
        assertNotNull(prototype);

        // and should be started
        MockEndpoint bar = assertIsInstanceOf(MockEndpoint.class, prototype);
        assertTrue(bar.getStatus().isStarted());

        // but registry is still at 1
        assertEquals(1, context.getEndpointRegistry().size());

        // and now at 2
        context.getEndpoint("mock:foo2");
        assertEquals(2, context.getEndpointRegistry().size());

        context.stop();

        // should not be stopped as we need to handle that ourselves due to prototype scoped
        assertFalse(bar.getStatus().isStopped());
        bar.stop();
        assertTrue(bar.getStatus().isStopped());

        // should only be mock:foo, mock:foo2, and no mock:bar
        assertEquals(2, endpoints.size());
        assertEquals("mock://foo", endpoints.get(0).getEndpointUri());
        assertEquals("mock://foo2", endpoints.get(1).getEndpointUri());
    }

}
