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
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;

/**
 * @version 
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
    
    public void testAutoStartComponentsOff() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.start();

        BeanComponent component = (BeanComponent) ctx.getComponent("bean", true, false);
        // should be stopped
        assertTrue(component.getStatus().isStopped());
    }

    public void testAutoStartComponentsOn() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.start();

        BeanComponent component = (BeanComponent) ctx.getComponent("bean", true, true);
        // should be started
        assertTrue(component.getStatus().isStarted());
    }

    public void testCreateDefaultUuidGenerator() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        UuidGenerator uuidGenerator = ctx.getUuidGenerator();
        assertNotNull(uuidGenerator);
        assertEquals(uuidGenerator.getClass(), ActiveMQUuidGenerator.class);
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
    
    public void testGetEndPointByTypeUnknown() {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        try {
            camelContext.getEndpoint("xxx", Endpoint.class);
            fail();
        } catch (NoSuchEndpointException e) {
            assertEquals("No endpoint could be found for: xxx, please check your classpath contains the needed Camel component jar.", e.getMessage());
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
        String s1 = it.next().getEndpointUri();
        String s2 = it.next().getEndpointUri();
        assertTrue("log://bar".equals(s1) || "log://bar".equals(s2));
        assertTrue("log://baz".equals(s1) || "log://baz".equals(s2));
        assertTrue("log://baz".equals(s1) || "log://baz".equals(s2));
        assertTrue("log://baz".equals(s1) || "log://baz".equals(s2));

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

    public void testHasEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.disableJMX();
        ctx.getEndpoint("mock://foo");

        assertNotNull(ctx.hasEndpoint("mock://foo"));
        assertNull(ctx.hasEndpoint("mock://bar"));

        Map<String, Endpoint> map = ctx.getEndpointMap();
        assertEquals(1, map.size());
        
        try {
            ctx.hasEndpoint(null);
            fail("Should have thrown exception");
        } catch (ResolveEndpointFailedException e) {
            // expected
        }
    }

    public void testGetRouteById() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
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

        public CamelContext getCamelContext() {
            return camelContext;
        }

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
