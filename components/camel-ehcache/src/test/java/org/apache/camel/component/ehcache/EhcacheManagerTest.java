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
package org.apache.camel.component.ehcache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.ehcache.CacheManager;
import org.ehcache.Status;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EhcacheManagerTest {

    @Test
    void testCacheManagerFromFile() throws Exception {

        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:ehcache").to("ehcache:myCache1?configurationUri=classpath:ehcache/ehcache-file-config.xml")
                            .to("ehcache:myCache2?configurationUri=classpath:ehcache/ehcache-file-config.xml");
                }
            });

            context.start();

            EhcacheEndpoint e1 = context.getEndpoint(
                    "ehcache:myCache1?configurationUri=classpath:ehcache/ehcache-file-config.xml", EhcacheEndpoint.class);
            EhcacheEndpoint e2 = context.getEndpoint(
                    "ehcache:myCache2?configurationUri=classpath:ehcache/ehcache-file-config.xml", EhcacheEndpoint.class);

            assertEquals(e1.getManager(), e2.getManager());
            assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            // the two endpoints and their producers
            assertEquals(4, e1.getManager().getReferenceCount().get());
            assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

            context.stop();

            assertEquals(e1.getManager(), e2.getManager());
            assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            assertEquals(0, e1.getManager().getReferenceCount().get());
            assertEquals(Status.UNINITIALIZED, e1.getManager().getCacheManager().getStatus());

        }
    }

    @Test
    void testCacheManagerFromConfiguration() throws Exception {

        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("myConf", new XmlConfiguration(getClass().getResource("/ehcache/ehcache-file-config.xml")));

        try (CamelContext context = new DefaultCamelContext(registry)) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:ehcache").to("ehcache:myCache1?cacheManagerConfiguration=#myConf")
                            .to("ehcache:myCache2?cacheManagerConfiguration=#myConf");
                }
            });

            context.start();

            EhcacheEndpoint e1
                    = context.getEndpoint("ehcache:myCache1?cacheManagerConfiguration=#myConf", EhcacheEndpoint.class);
            EhcacheEndpoint e2
                    = context.getEndpoint("ehcache:myCache2?cacheManagerConfiguration=#myConf", EhcacheEndpoint.class);

            assertEquals(e1.getManager(), e2.getManager());
            assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            // the two endpoints and their producers
            assertEquals(4, e1.getManager().getReferenceCount().get());
            assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

            context.stop();

            assertEquals(e1.getManager(), e2.getManager());
            assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            assertEquals(0, e1.getManager().getReferenceCount().get());
            assertEquals(Status.UNINITIALIZED, e1.getManager().getCacheManager().getStatus());
        }
    }

    @Test
    void testCacheManager() throws Exception {

        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true)) {

            SimpleRegistry registry = new SimpleRegistry();
            registry.bind("myManager", cacheManager);

            try (CamelContext context = new DefaultCamelContext(registry)) {
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:ehcache").to("ehcache:myCache1?cacheManager=#myManager")
                                .to("ehcache:myCache2?cacheManager=#myManager");
                    }
                });

                context.start();

                EhcacheEndpoint e1 = context.getEndpoint("ehcache:myCache1?cacheManager=#myManager", EhcacheEndpoint.class);
                EhcacheEndpoint e2 = context.getEndpoint("ehcache:myCache2?cacheManager=#myManager", EhcacheEndpoint.class);

                assertSame(e1.getManager(), e2.getManager());
                assertSame(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
                // the two endpoints and their producers
                assertEquals(4, e1.getManager().getReferenceCount().get());
                assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

                context.stop();

                assertSame(e1.getManager(), e2.getManager());
                assertSame(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
                assertEquals(0, e1.getManager().getReferenceCount().get());
                assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());
            }

        }
    }

    @Test
    void theLastUserStopsTheManagerAlsoWhenItIsNotTheEndpoint() {
        EhcacheManager manager = new EhcacheManager(CacheManagerBuilder.newCacheManagerBuilder().build(), true, null);
        // the endpoint and its producer
        manager.incRef();
        manager.incRef();
        assertTrue(manager.isStarted());
        assertEquals(Status.AVAILABLE, manager.getCacheManager().getStatus());

        // the endpoint stops first, as when another route that uses it is removed
        assertEquals(1, manager.decRef());
        assertTrue(manager.isStarted());

        assertEquals(0, manager.decRef());
        assertTrue(manager.isStopped());
        assertEquals(Status.UNINITIALIZED, manager.getCacheManager().getStatus());
    }

    @Test
    void exactlyOneOfTheLastUsersThatStopTogetherStopsTheManager() throws Exception {
        int users = 16;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        try {
            for (int round = 0; round < 100; round++) {
                EhcacheManager manager
                        = new EhcacheManager(CacheManagerBuilder.newCacheManagerBuilder().build(), true, null);
                for (int i = 0; i < users; i++) {
                    manager.incRef();
                }
                CyclicBarrier start = new CyclicBarrier(users);
                List<Future<Long>> releases = new ArrayList<>();
                for (int i = 0; i < users; i++) {
                    releases.add(pool.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        return manager.decRef();
                    }));
                }
                int last = 0;
                for (Future<Long> release : releases) {
                    if (release.get(10, TimeUnit.SECONDS) == 0) {
                        last++;
                    }
                }

                assertEquals(1, last, "the users that saw the count reach 0 in round " + round);
                assertTrue(manager.isStopped());
                assertEquals(Status.UNINITIALIZED, manager.getCacheManager().getStatus());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aUserThatStartsWhileTheLastOneClosesTheCacheManagerWaitsForTheClose() throws Exception {
        CacheManager real = CacheManagerBuilder.newCacheManagerBuilder().build();
        AtomicReference<EhcacheManager> manager = new AtomicReference<>();
        AtomicReference<Thread> next = new AtomicReference<>();
        CacheManager cacheManager = (CacheManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { CacheManager.class }, (proxy, method, args) -> {
                    if ("close".equals(method.getName()) && next.get() == null) {
                        // another user starts while the last one closes the cache manager: it must wait for the
                        // close (it takes the lock), and not take a reference on the cache manager being closed
                        Thread user = new Thread(() -> manager.get().incRef());
                        next.set(user);
                        user.start();
                        await().atMost(10, TimeUnit.SECONDS)
                                .until(() -> user.getState() == Thread.State.WAITING || !user.isAlive());
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        manager.set(new EhcacheManager(cacheManager, true, null));
        manager.get().incRef();

        assertEquals(0, manager.get().decRef());
        next.get().join(TimeUnit.SECONDS.toMillis(10));

        // closed by the last user, then opened again by the next one
        assertEquals(1, manager.get().getReferenceCount().get());
        assertTrue(manager.get().isStarted());
        assertEquals(Status.AVAILABLE, real.getStatus());
        manager.get().decRef();
        assertEquals(Status.UNINITIALIZED, real.getStatus());
    }
}
