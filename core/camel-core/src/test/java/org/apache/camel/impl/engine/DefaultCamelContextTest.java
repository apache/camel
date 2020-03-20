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
package org.apache.camel.impl.engine;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

public class DefaultCamelContextTest extends TestSupport {

    @Test
    public void testStartDate() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        assertNull(ctx.getStartDate());
        ctx.start();
        assertNotNull(ctx.getStartDate());
    }

    @Test
    public void testAutoCreateComponentsOn() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        Component component = ctx.getComponent("bean");
        assertNotNull(component);
        assertEquals(component.getClass(), BeanComponent.class);
    }

    @Test
    public void testAutoCreateComponentsOff() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        ctx.setAutoCreateComponents(false);
        Component component = ctx.getComponent("bean");
        assertNull(component);
    }

    @Test
    public void testAutoStartComponentsOff() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        ctx.start();

        BeanComponent component = (BeanComponent)ctx.getComponent("bean", true, false);
        // should be stopped
        assertTrue(component.getStatus().isStopped());
    }

    @Test
    public void testAutoStartComponentsOn() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.start();

        BeanComponent component = (BeanComponent)ctx.getComponent("bean", true, true);
        // should be started
        assertTrue(component.getStatus().isStarted());
    }

    @Test
    public void testCreateDefaultUuidGenerator() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        ctx.init();
        UuidGenerator uuidGenerator = ctx.getUuidGenerator();
        assertNotNull(uuidGenerator);
        assertEquals(uuidGenerator.getClass(), DefaultUuidGenerator.class);
    }

    @Test
    public void testGetComponents() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        Component component = ctx.getComponent("bean");
        assertNotNull(component);

        List<String> list = ctx.getComponentNames();
        assertEquals(1, list.size());
        assertEquals("bean", list.get(0));
    }

    @Test
    public void testGetEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        Endpoint endpoint = ctx.getEndpoint("log:foo");
        assertNotNull(endpoint);

        try {
            ctx.getEndpoint((String) null);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetEndPointByTypeUnknown() {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        try {
            camelContext.getEndpoint("xxx", Endpoint.class);
            fail();
        } catch (NoSuchEndpointException e) {
            assertEquals("No endpoint could be found for: xxx, please check your classpath contains the needed Camel component jar.", e.getMessage());
        }
    }

    @Test
    public void testRemoveEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        ctx.getEndpoint("log:foo");
        ctx.getEndpoint("log:bar");
        ctx.start();

        Collection<Endpoint> list = ctx.removeEndpoints("log:foo");
        assertEquals(1, list.size());
        assertEquals("log://foo", list.iterator().next().getEndpointUri());

        ctx.getEndpoint("log:baz");
        ctx.getEndpoint("seda:cool");

        list = ctx.removeEndpoints("log:*");
        assertEquals(2, list.size());

        Iterator<Endpoint> it = list.iterator();
        String s1 = it.next().getEndpointUri();
        String s2 = it.next().getEndpointUri();
        assertTrue("log://bar".equals(s1) || "log://bar".equals(s2));
        assertTrue("log://baz".equals(s1) || "log://baz".equals(s2));
        assertTrue("log://baz".equals(s1) || "log://baz".equals(s2));
        assertTrue("log://baz".equals(s1) || "log://baz".equals(s2));

        assertEquals(1, ctx.getEndpoints().size());
    }

    @Test
    public void testGetEndpointNotFound() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        try {
            ctx.getEndpoint("xxx:foo");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (NoSuchEndpointException e) {
            assertTrue(e.getMessage().contains("No endpoint could be found for: xxx:"));
        }
    }

    @Test
    public void testGetEndpointUnknownComponentNoScheme() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        try {
            CamelContextHelper.getMandatoryEndpoint(ctx, "unknownname");
            fail("Should have thrown a NoSuchEndpointException");
        } catch (NoSuchEndpointException e) {
            // expected
        }
    }

    @Test
    public void testRestartCamelContext() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
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

    @Test
    public void testName() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        ctx.init();
        assertNotNull("Should have a default name", ctx.getName());
        ctx.setName("foo");
        assertEquals("foo", ctx.getName());

        assertNotNull(ctx.toString());
        assertTrue(ctx.isAutoStartup());
    }

    @Test
    public void testVersion() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        assertNotNull("Should have a version", ctx.getVersion());
    }

    @Test
    public void testHasComponent() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        assertNull(ctx.hasComponent("log"));

        ctx.addComponent("log", new LogComponent());
        assertNotNull(ctx.hasComponent("log"));
    }

    @Test
    public void testGetComponent() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
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

    @Test
    public void testHasEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();
        ctx.getEndpoint("mock://foo");

        assertNotNull(ctx.hasEndpoint("mock://foo"));
        assertNull(ctx.hasEndpoint("mock://bar"));

        Map<String, Endpoint> map = ctx.getEndpointMap();
        assertEquals(1, map.size());

        try {
            ctx.hasEndpoint((String) null);
            fail("Should have thrown exception");
        } catch (ResolveEndpointFailedException e) {
            // expected
        }
    }

    @Test
    public void testGetRouteById() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();

        // should not throw NPE (CAMEL-3198)
        Route route = ctx.getRoute("coolRoute");
        assertNull(route);

        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("coolRoute").to("mock:result");
            }
        });
        ctx.start();

        route = ctx.getRoute("coolRoute");
        assertNotNull(route);
        assertEquals("coolRoute", route.getId());
        assertEquals("direct://start", route.getConsumer().getEndpoint().getEndpointUri());

        assertNull(ctx.getRoute("unknown"));
        ctx.stop();
    }

    @Test
    public void testSuspend() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();

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

    @Test
    public void testResume() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        ctx.disableJMX();

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

    @Test
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

    @Test
    public void testAddServiceInjectCamelContext() throws Exception {
        MyService my = new MyService();

        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.addService(my);
        ctx.start();

        assertEquals(ctx, my.getCamelContext());
        assertEquals("Started", my.getStatus().name());

        ctx.stop();
        assertEquals("Stopped", my.getStatus().name());
    }

    @Test
    public void testAddServiceType() throws Exception {
        MyService my = new MyService();

        DefaultCamelContext ctx = new DefaultCamelContext();
        assertNull(ctx.hasService(MyService.class));

        ctx.addService(my);
        assertSame(my, ctx.hasService(MyService.class));

        ctx.stop();
        assertNull(ctx.hasService(MyService.class));
    }

    private static class MyService extends ServiceSupport implements CamelContextAware {

        private CamelContext camelContext;

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }

}
