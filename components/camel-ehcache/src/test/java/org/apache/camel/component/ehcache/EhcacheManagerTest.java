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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.ehcache.CacheManager;
import org.ehcache.Status;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
            assertEquals(2, e1.getManager().getReferenceCount().get());
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
            assertEquals(2, e1.getManager().getReferenceCount().get());
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
                assertEquals(2, e1.getManager().getReferenceCount().get());
                assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

                context.stop();

                assertSame(e1.getManager(), e2.getManager());
                assertSame(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
                assertEquals(0, e1.getManager().getReferenceCount().get());
                assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());
            }

        }
    }
}
