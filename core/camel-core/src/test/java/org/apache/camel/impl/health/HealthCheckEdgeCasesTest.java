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
package org.apache.camel.impl.health;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthCheckEdgeCasesTest {

    private static class MyCheck extends AbstractHealthCheck {
        private final RuntimeException error;

        MyCheck(String id, RuntimeException error) {
            super("my", id);
            this.error = error;
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            if (error != null) {
                throw error;
            }
            builder.up();
        }
    }

    private static CamelContext createContext(HealthCheckRegistry registry) throws Exception {
        CamelContext context = new DefaultCamelContext();
        registry.setCamelContext(context);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").to("mock:foo");
            }
        });
        return context;
    }

    @Test
    public void testExposureLevelDoesNotChangeReadiness() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        CamelContext context = createContext(registry);
        context.start();

        // an up check that comes first (such as the context check)
        registry.register(new MyCheck("first", null) {
            @Override
            public int getOrder() {
                return Ordered.HIGHEST;
            }
        });
        MyCheck check = new MyCheck("disabled", null);
        check.setEnabled(false);
        registry.register(check);

        Collection<HealthCheck.Result> full = HealthCheckHelper.invokeReadiness(context, "full");
        Collection<HealthCheck.Result> def = HealthCheckHelper.invokeReadiness(context, "default");
        Collection<HealthCheck.Result> oneline = HealthCheckHelper.invokeReadiness(context, "oneline");
        assertFalse(HealthCheckHelper.isResultsUp(full, true));
        assertFalse(HealthCheckHelper.isResultsUp(def, true));
        assertFalse(HealthCheckHelper.isResultsUp(oneline, true));

        context.stop();
    }

    @Test
    public void testThrowingCheckIsDown() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        CamelContext context = createContext(registry);
        context.start();

        registry.register(new MyCheck("boom", new IllegalStateException("boom")));

        Collection<HealthCheck.Result> results = HealthCheckHelper.invokeReadiness(context, "full");
        HealthCheck.Result result = results.stream().filter(r -> "boom".equals(r.getCheck().getId())).findFirst().get();
        assertEquals(HealthCheck.State.DOWN, result.getState());
        assertTrue(result.getError().isPresent());

        context.stop();
    }

    @Test
    public void testRegisterCheckWithSameNameAsRoute() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        CamelContext context = createContext(registry);
        context.start();

        assertTrue(registry.register(new MyCheck("foo", null)));
        assertTrue(registry.register(new MyCheck("x-health-check", null)));
        assertTrue(registry.register(new MyCheck("x", null)));
        assertFalse(registry.register(new MyCheck("x", null)));

        context.stop();
    }

    @Test
    public void testExcludePatternWithSpaces() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        CamelContext context = createContext(registry);
        registry.setExcludePattern("foo, bar");
        context.start();

        assertTrue(registry.isExcluded(new MyCheck("bar", null)));
        assertTrue(registry.isExcluded(new MyCheck("foo", null)));

        context.stop();
    }

    @Test
    public void testRouteCheckOfRemovedRoute() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        CamelContext context = createContext(registry);
        context.start();

        Route route = context.getRoute("foo");
        RouteHealthCheck check = new RouteHealthCheck(route);
        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");

        HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(check);
        check.doCall(builder, Collections.emptyMap());
        assertEquals(HealthCheck.State.UNKNOWN, builder.build().getState());

        context.stop();
    }

    @Test
    public void testSuspendedRouteIsDown() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setInitialState(HealthCheck.State.UP);
        CamelContext context = createContext(registry);
        context.start();

        context.getRouteController().suspendRoute("foo");
        RouteHealthCheck check = new RouteHealthCheck(context.getRoute("foo"));
        check.setCamelContext(context);
        assertEquals(HealthCheck.State.DOWN, check.call().getState());

        context.stop();
    }

    @Test
    public void testRemovedRoutesAreNotKept() throws Exception {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        CamelContext context = createContext(registry);
        context.start();

        RoutesHealthCheckRepository routes = new RoutesHealthCheckRepository();
        routes.setCamelContext(context);
        ConsumersHealthCheckRepository consumers = new ConsumersHealthCheckRepository();
        consumers.setCamelContext(context);

        for (int i = 0; i < 5; i++) {
            String id = "r" + i;
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:" + id).routeId(id).to("mock:" + id);
                }
            });
            routes.stream().forEach(c -> c.call());
            consumers.stream().forEach(c -> c.call());
            context.getRouteController().stopRoute(id);
            context.removeRoute(id);
        }
        routes.stream().forEach(c -> c.call());
        consumers.stream().forEach(c -> c.call());

        assertEquals(1, checksOf(routes).size());
        assertEquals(1, checksOf(consumers).size());

        context.stop();
    }

    private static Map<?, ?> checksOf(Object repository) throws Exception {
        Field field = repository.getClass().getDeclaredField("checks");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(repository);
    }
}
