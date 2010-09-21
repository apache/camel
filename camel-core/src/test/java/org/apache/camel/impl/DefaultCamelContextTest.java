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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.CamelContextHelper;

/**
 * @version $Revision$
 */
public class DefaultCamelContextTest extends TestSupport {

    public void testAutoCreateComponentsOn() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        Component component = ctx.getComponent("bean");
        assertNotNull(component);
        assertEquals(component.getClass(), BeanComponent.class);
    }

    public void testAutoCreateComponentsOff() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.setAutoCreateComponents(false);
        Component component = ctx.getComponent("bean");
        assertNull(component);
    }
    
    public void testCreateDefaultUuidGenerator() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        UuidGenerator uuidGenerator = ctx.getUuidGenerator();
        assertNotNull(uuidGenerator);
        assertEquals(uuidGenerator.getClass(), JavaUuidGenerator.class);
    }

    public void testGetComponents() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        Component component = ctx.getComponent("bean");
        assertNotNull(component);

        List<String> list = ctx.getComponentNames();
        assertEquals(1, list.size());
        assertEquals("bean", list.get(0));
    }

    public void testGetEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        Endpoint endpoint = ctx.getEndpoint("log:foo");
        assertNotNull(endpoint);

        try {
            ctx.getEndpoint(null);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testRemoveEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.getEndpoint("log:foo");
        ctx.getEndpoint("log:bar");

        Collection<Endpoint> list = ctx.removeEndpoints("log:foo");
        assertEquals(1, list.size());
        assertEquals("log://foo", list.iterator().next().getEndpointUri());

        ctx.getEndpoint("log:baz");
        ctx.getEndpoint("seda:cool");

        list = ctx.removeEndpoints("log:*");
        assertEquals(2, list.size());
        Iterator<Endpoint> it = list.iterator();
        assertEquals("log://bar", it.next().getEndpointUri());
        assertEquals("log://baz", it.next().getEndpointUri());

        assertEquals(1, ctx.getEndpoints().size());
    }

    public void testGetEndpointNotFound() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        try {
            ctx.getEndpoint("xxx:foo");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().contains("No component found with scheme: xxx"));
        }
    }

    public void testGetEndpointNoScheme() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        try {
            CamelContextHelper.getMandatoryEndpoint(ctx, "log.foo");
            fail("Should have thrown a NoSuchEndpointException");
        } catch (NoSuchEndpointException e) {
            // expected
        }
    }

    public void testRestartCamelContext() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:endpointA").to("mock:endpointB");
            }
        });
        ctx.start();
        assertEquals("Should have one RouteService", 1, ctx.getRouteServices().size());
        String routesString = ctx.getRoutes().toString();
        ctx.stop();
        assertEquals("The RouteService should NOT be removed even when we stop", 1, ctx.getRouteServices().size());
        ctx.start();
        assertEquals("Should have one RouteService", 1, ctx.getRouteServices().size());
        assertEquals("The Routes should be same", routesString, ctx.getRoutes().toString());
        ctx.stop();
        assertEquals("The RouteService should NOT be removed even when we stop", 1, ctx.getRouteServices().size());
    }

    public void testName() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        assertNotNull("Should have a default name", ctx.getName());
        ctx.setName("foo");
        assertEquals("foo", ctx.getName());

        assertNotNull(ctx.toString());
        assertTrue(ctx.isAutoStartup());
    }

    public void testVersion() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        assertNotNull("Should have a version", ctx.getVersion());
    }

    public void testHasComponent() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        assertNull(ctx.hasComponent("log"));

        ctx.addComponent("log", new LogComponent());
        assertNotNull(ctx.hasComponent("log"));
    }

    public void testGetComponent() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.addComponent("log", new LogComponent());

        LogComponent log = ctx.getComponent("log", LogComponent.class);
        assertNotNull(log);
        try {
            ctx.addComponent("direct", new DirectComponent());
            ctx.getComponent("log", DirectComponent.class);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetEndpointMap() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.addEndpoint("mock://foo", new MockEndpoint("mock://foo"));

        Map<String, Endpoint> map = ctx.getEndpointMap();
        assertEquals(1, map.size());
    }

    public void testHasEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.addEndpoint("mock://foo", new MockEndpoint("mock://foo"));

        assertNotNull(ctx.hasEndpoint("mock://foo"));
        assertNull(ctx.hasEndpoint("mock://bar"));

        try {
            Endpoint endpoint = ctx.hasEndpoint(null);
            assertNull("Should not have endpoint", endpoint);
        } catch (ResolveEndpointFailedException e) {
            // expected
        }
    }

    public void testGetRouteById() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("coolRoute").to("mock:result");
            }
        });
        ctx.start();

        Route route = ctx.getRoute("coolRoute");
        assertNotNull(route);
        assertEquals("coolRoute", route.getId());
        assertEquals("direct://start", route.getConsumer().getEndpoint().getEndpointUri());

        assertNull(ctx.getRoute("unknown"));
        ctx.stop();
    }

    public void testSuspend() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();

        assertEquals(false, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.start();
        assertEquals(true, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.suspend();
        assertEquals(false, ctx.isStarted());
        assertEquals(true, ctx.isSuspended());

        ctx.suspend();
        assertEquals(false, ctx.isStarted());
        assertEquals(true, ctx.isSuspended());

        ctx.stop();
        assertEquals(false, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());
    }

    public void testResume() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();

        assertEquals(false, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.start();
        assertEquals(true, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.resume();
        assertEquals(true, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.resume();
        assertEquals(true, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.stop();
        assertEquals(false, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());
    }

    public void testSuspendResume() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();

        assertEquals(false, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.start();
        assertEquals(true, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.suspend();
        assertEquals(false, ctx.isStarted());
        assertEquals(true, ctx.isSuspended());

        ctx.resume();
        assertEquals(true, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());

        ctx.stop();
        assertEquals(false, ctx.isStarted());
        assertEquals(false, ctx.isSuspended());
    }

}
