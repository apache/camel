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
import org.junit.Test;

public class RemoveRouteStopEndpointTest extends ContextTestSupport {

    @Test
    public void testEndpointRegistryStopRouteEndpoints() throws Exception {
        Endpoint seda = context.hasEndpoint("seda://foo");
        assertNotNull(seda);
        Endpoint log = context.hasEndpoint("log://bar");
        assertNotNull(log);
        assertTrue("Should be started", ((ServiceSupport)seda).isStarted());
        assertTrue("Should be started", ((ServiceSupport)log).isStarted());

        assertTrue(context.hasEndpoint("seda:foo") != null);
        assertTrue(context.hasEndpoint("seda:bar") != null);
        assertTrue(context.hasEndpoint("log://foo") != null);
        assertTrue(context.hasEndpoint("log://bar") != null);
        assertTrue(context.hasEndpoint("mock://result") != null);
        assertTrue(context.hasEndpoint("seda://stop") != null);
        assertTrue(context.hasEndpoint("mock://stop") != null);

        // stop and remove bar route
        context.getRouteController().stopRoute("bar");
        context.removeRoute("bar");

        assertTrue(context.hasEndpoint("seda://foo") != null);
        assertTrue(context.hasEndpoint("log://foo") != null);
        assertFalse(context.hasEndpoint("seda://bar") != null);
        assertFalse(context.hasEndpoint("log://bar") != null);
        assertTrue(context.hasEndpoint("mock://result") != null);
        assertTrue(context.hasEndpoint("seda://stop") != null);
        assertTrue(context.hasEndpoint("mock://stop") != null);

        assertTrue("Should be started", ((ServiceSupport)seda).isStarted());
        assertTrue("Should be stopped", ((ServiceSupport)log).isStopped());

        // stop and remove baz route
        context.getRouteController().stopRoute("baz");
        context.removeRoute("baz");

        assertTrue(context.hasEndpoint("seda://foo") != null);
        assertTrue(context.hasEndpoint("log://foo") != null);
        assertFalse(context.hasEndpoint("seda://bar") != null);
        assertFalse(context.hasEndpoint("log://bar") != null);
        assertTrue(context.hasEndpoint("mock://result") != null);
        assertFalse(context.hasEndpoint("seda://stop") != null);
        assertFalse(context.hasEndpoint("mock://stop") != null);
        // stop and remove foo route
        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");

        assertFalse(context.hasEndpoint("seda://foo") != null);
        assertFalse(context.hasEndpoint("log://foo") != null);
        assertFalse(context.hasEndpoint("seda://bar") != null);
        assertFalse(context.hasEndpoint("log://bar") != null);
        assertFalse(context.hasEndpoint("mock://result") != null);
        assertFalse(context.hasEndpoint("seda://stop") != null);
        assertFalse(context.hasEndpoint("mock://stop") != null);

        assertFalse("Should not be started", ((ServiceSupport)seda).isStarted());
        assertFalse("Should not be started", ((ServiceSupport)log).isStarted());
    }

    @Test
    public void testEndpointRegistryStopRouteEndpointsContextStop() throws Exception {
        Endpoint seda = context.hasEndpoint("seda://foo");
        assertNotNull(seda);
        Endpoint log = context.hasEndpoint("log://bar");
        assertNotNull(log);
        assertTrue("Should be started", ((ServiceSupport)seda).isStarted());
        assertTrue("Should be started", ((ServiceSupport)log).isStarted());

        assertTrue(context.hasEndpoint("seda://foo") != null);
        assertTrue(context.hasEndpoint("log://foo") != null);
        assertTrue(context.hasEndpoint("seda://bar") != null);
        assertTrue(context.hasEndpoint("log://bar") != null);
        assertTrue(context.hasEndpoint("mock://result") != null);
        assertTrue(context.hasEndpoint("seda://stop") != null);
        assertTrue(context.hasEndpoint("mock://stop") != null);

        // stop and remove bar route
        context.getRouteController().stopRoute("bar");
        context.removeRoute("bar");

        assertTrue("Should be started", ((ServiceSupport)seda).isStarted());
        assertTrue("Should be stopped", ((ServiceSupport)log).isStopped());

        assertTrue(context.hasEndpoint("seda:foo") != null);
        assertTrue(context.hasEndpoint("log://foo") != null);
        assertFalse(context.hasEndpoint("seda://bar") != null);
        assertFalse(context.hasEndpoint("log://bar") != null);
        assertTrue(context.hasEndpoint("mock://result") != null);
        assertTrue(context.hasEndpoint("seda://stop") != null);
        assertTrue(context.hasEndpoint("mock://stop") != null);

        // stop and remove baz route
        context.getRouteController().stopRoute("baz");
        context.removeRoute("baz");

        assertTrue(context.hasEndpoint("seda://foo") != null);
        assertTrue(context.hasEndpoint("log://foo") != null);
        assertFalse(context.hasEndpoint("seda://bar") != null);
        assertFalse(context.hasEndpoint("log://bar") != null);
        assertTrue(context.hasEndpoint("mock://result") != null);
        assertFalse(context.hasEndpoint("seda://stop") != null);
        assertFalse(context.hasEndpoint("mock://stop") != null);

        // stop camel which should stop the endpoint

        context.stop();

        assertFalse("Should not be started", ((ServiceSupport)seda).isStarted());
        assertFalse("Should not be started", ((ServiceSupport)log).isStarted());
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
