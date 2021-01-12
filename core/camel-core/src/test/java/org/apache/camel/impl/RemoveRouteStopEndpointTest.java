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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RemoveRouteStopEndpointTest extends ContextTestSupport {

    @Test
    public void testEndpointRegistryStopRouteEndpoints() throws Exception {
        Endpoint seda = context.hasEndpoint("seda://foo");
        assertNotNull(seda);
        Endpoint log = context.hasEndpoint("log://bar");
        assertNotNull(log);
        assertTrue(((ServiceSupport) seda).isStarted(), "Should be started");
        assertTrue(((ServiceSupport) log).isStarted(), "Should be started");

        assertNotNull(context.hasEndpoint("seda:foo"));
        assertNotNull(context.hasEndpoint("seda:bar"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNotNull(context.hasEndpoint("log://bar"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNotNull(context.hasEndpoint("seda://stop"));
        assertNotNull(context.hasEndpoint("mock://stop"));

        // stop and remove bar route
        context.getRouteController().stopRoute("bar");
        context.removeRoute("bar");

        assertNotNull(context.hasEndpoint("seda://foo"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNull(context.hasEndpoint("seda://bar"));
        assertNull(context.hasEndpoint("log://bar"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNotNull(context.hasEndpoint("seda://stop"));
        assertNotNull(context.hasEndpoint("mock://stop"));

        assertTrue(((ServiceSupport) seda).isStarted(), "Should be started");
        assertTrue(((ServiceSupport) log).isStopped(), "Should be stopped");

        // stop and remove baz route
        context.getRouteController().stopRoute("baz");
        context.removeRoute("baz");

        assertNotNull(context.hasEndpoint("seda://foo"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNull(context.hasEndpoint("seda://bar"));
        assertNull(context.hasEndpoint("log://bar"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNull(context.hasEndpoint("seda://stop"));
        assertNull(context.hasEndpoint("mock://stop"));
        // stop and remove foo route
        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");

        assertNull(context.hasEndpoint("seda://foo"));
        assertNull(context.hasEndpoint("log://foo"));
        assertNull(context.hasEndpoint("seda://bar"));
        assertNull(context.hasEndpoint("log://bar"));
        assertNull(context.hasEndpoint("mock://result"));
        assertNull(context.hasEndpoint("seda://stop"));
        assertNull(context.hasEndpoint("mock://stop"));

        assertFalse(((ServiceSupport) seda).isStarted(), "Should not be started");
        assertFalse(((ServiceSupport) log).isStarted(), "Should not be started");
    }

    @Test
    public void testEndpointRegistryStopRouteEndpointsContextStop() throws Exception {
        Endpoint seda = context.hasEndpoint("seda://foo");
        assertNotNull(seda);
        Endpoint log = context.hasEndpoint("log://bar");
        assertNotNull(log);
        assertTrue(((ServiceSupport) seda).isStarted(), "Should be started");
        assertTrue(((ServiceSupport) log).isStarted(), "Should be started");

        assertNotNull(context.hasEndpoint("seda://foo"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNotNull(context.hasEndpoint("seda://bar"));
        assertNotNull(context.hasEndpoint("log://bar"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNotNull(context.hasEndpoint("seda://stop"));
        assertNotNull(context.hasEndpoint("mock://stop"));

        // stop and remove bar route
        context.getRouteController().stopRoute("bar");
        context.removeRoute("bar");

        assertTrue(((ServiceSupport) seda).isStarted(), "Should be started");
        assertTrue(((ServiceSupport) log).isStopped(), "Should be stopped");

        assertNotNull(context.hasEndpoint("seda:foo"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNull(context.hasEndpoint("seda://bar"));
        assertNull(context.hasEndpoint("log://bar"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNotNull(context.hasEndpoint("seda://stop"));
        assertNotNull(context.hasEndpoint("mock://stop"));

        // stop and remove baz route
        context.getRouteController().stopRoute("baz");
        context.removeRoute("baz");

        assertNotNull(context.hasEndpoint("seda://foo"));
        assertNotNull(context.hasEndpoint("log://foo"));
        assertNull(context.hasEndpoint("seda://bar"));
        assertNull(context.hasEndpoint("log://bar"));
        assertNotNull(context.hasEndpoint("mock://result"));
        assertNull(context.hasEndpoint("seda://stop"));
        assertNull(context.hasEndpoint("mock://stop"));

        // stop camel which should stop the endpoint

        context.stop();

        assertFalse(((ServiceSupport) seda).isStarted(), "Should not be started");
        assertFalse(((ServiceSupport) log).isStarted(), "Should not be started");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").to("log:foo").to("mock:result");

                from("seda:bar").routeId("bar").to("log:bar").to("log:bar").to("mock:result");

                from("seda:stop").routeId("baz").to("mock:stop");
            }
        };
    }

}
