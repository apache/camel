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
package org.apache.camel.component.resilience4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The thread pools that Camel creates for a circuit breaker with a timeout live as long as its route: a stopped route
 * is started again with them, so stopping the route must not shut them down, and removing the route must.
 */
class ResilienceThreadPoolLifecycleTest extends CamelTestSupport {

    /** The scheduled thread pools created by name: the calls and the timeouts of the asynchronous mode. */
    private final Map<String, ScheduledExecutorService> scheduledPools = new ConcurrentHashMap<>();
    /** The thread pools created by name: the calls of the synchronous mode. */
    private final Map<String, ExecutorService> pools = new ConcurrentHashMap<>();
    /** The cached thread pools created by name: what runs after a timeout in asynchronous mode. */
    private final Map<String, ExecutorService> cachedPools = new ConcurrentHashMap<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
            @Override
            public ScheduledExecutorService newDefaultScheduledThreadPool(Object source, String name) {
                ScheduledExecutorService answer = super.newDefaultScheduledThreadPool(source, name);
                scheduledPools.put(name, answer);
                return answer;
            }

            @Override
            public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                ExecutorService answer = super.newThreadPool(source, name, profile);
                pools.put(name, answer);
                return answer;
            }

            @Override
            public ExecutorService newCachedThreadPool(Object source, String name) {
                ExecutorService answer = super.newCachedThreadPool(source, name);
                cachedPools.put(name, answer);
                return answer;
            }
        });
        return context;
    }

    @Test
    void testThreadPoolsAreShutDownWhenTheRouteIsRemoved() throws Exception {
        assertEquals("Bye World", request("direct:async"));
        assertEquals("Bye World", request("direct:sync"));
        ExecutorService asyncCalls = scheduledPools.get("CircuitBreaker");
        ExecutorService syncCalls = pools.get("CircuitBreaker");
        assertNotNull(asyncCalls);
        assertNotNull(syncCalls);

        context.getRouteController().stopRoute("async");
        context.getRouteController().stopRoute("sync");
        assertFalse(asyncCalls.isShutdown(), "the route is started again with this thread pool");
        assertFalse(syncCalls.isShutdown(), "the route is started again with this thread pool");
        ExecutorService timeouts = scheduledPools.get("CircuitBreakerTimeout");
        assertNotNull(timeouts);
        assertFalse(timeouts.isShutdown(), "the route is started again with this thread pool");
        ExecutorService fallbacks = cachedPools.get("CircuitBreakerFallback");
        assertNotNull(fallbacks);
        assertFalse(fallbacks.isShutdown(), "the route is started again with this thread pool");

        context.removeRoute("async");
        context.removeRoute("sync");
        assertTrue(asyncCalls.isShutdown());
        assertTrue(syncCalls.isShutdown());
        assertTrue(timeouts.isShutdown());
        assertTrue(fallbacks.isShutdown());
    }

    private Object request(String uri) throws Exception {
        // on a thread of the template, so a call that never completes fails the test instead of blocking it
        return template.asyncRequestBody(uri, "Hello").get(10, TimeUnit.SECONDS);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sync").routeId("sync")
                        .circuitBreaker()
                            .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(2000).end()
                            .setBody(constant("Bye World"))
                        .end();

                from("direct:async").routeId("async")
                        .circuitBreaker()
                            .resilience4jConfiguration().asynchronous(true).timeoutEnabled(true).timeoutDuration(2000).end()
                            .setBody(constant("Bye World"))
                        .end();
            }
        };
    }
}
