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
import org.junit.Assert;
import org.junit.Test;

public class EhcacheManagerTest {

    @Test
    public void testCacheManagerFromFile() throws Exception {
        CamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:ehcache")
                        .to("ehcache:myCache1?configurationUri=classpath:ehcache/ehcache-file-config.xml")
                        .to("ehcache:myCache2?configurationUri=classpath:ehcache/ehcache-file-config.xml");
                }
            });

            context.start();

            EhcacheEndpoint e1 = context.getEndpoint("ehcache:myCache1?configurationUri=classpath:ehcache/ehcache-file-config.xml", EhcacheEndpoint.class);
            EhcacheEndpoint e2 = context.getEndpoint("ehcache:myCache2?configurationUri=classpath:ehcache/ehcache-file-config.xml", EhcacheEndpoint.class);

            Assert.assertEquals(e1.getManager(), e2.getManager());
            Assert.assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            Assert.assertEquals(2, e1.getManager().getReferenceCount().get());
            Assert.assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

            context.stop();

            Assert.assertEquals(e1.getManager(), e2.getManager());
            Assert.assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            Assert.assertEquals(0, e1.getManager().getReferenceCount().get());
            Assert.assertEquals(Status.UNINITIALIZED, e1.getManager().getCacheManager().getStatus());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testCacheManagerFromConfiguration() throws Exception {
        CamelContext context = null;

        try {
            SimpleRegistry registry = new SimpleRegistry();
            registry.bind("myConf", new XmlConfiguration(getClass().getResource("/ehcache/ehcache-file-config.xml")));

            context = new DefaultCamelContext(registry);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:ehcache")
                        .to("ehcache:myCache1?cacheManagerConfiguration=#myConf")
                        .to("ehcache:myCache2?cacheManagerConfiguration=#myConf");
                }
            });

            context.start();

            EhcacheEndpoint e1 = context.getEndpoint("ehcache:myCache1?cacheManagerConfiguration=#myConf", EhcacheEndpoint.class);
            EhcacheEndpoint e2 = context.getEndpoint("ehcache:myCache2?cacheManagerConfiguration=#myConf", EhcacheEndpoint.class);

            Assert.assertEquals(e1.getManager(), e2.getManager());
            Assert.assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            Assert.assertEquals(2, e1.getManager().getReferenceCount().get());
            Assert.assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

            context.stop();

            Assert.assertEquals(e1.getManager(), e2.getManager());
            Assert.assertEquals(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            Assert.assertEquals(0, e1.getManager().getReferenceCount().get());
            Assert.assertEquals(Status.UNINITIALIZED, e1.getManager().getCacheManager().getStatus());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testCacheManager() throws Exception {
        CamelContext context = null;
        CacheManager cacheManager = null;

        try {
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);

            SimpleRegistry registry = new SimpleRegistry();
            registry.bind("myManager", cacheManager);

            context = new DefaultCamelContext(registry);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:ehcache")
                        .to("ehcache:myCache1?cacheManager=#myManager")
                        .to("ehcache:myCache2?cacheManager=#myManager");
                }
            });

            context.start();

            EhcacheEndpoint e1 = context.getEndpoint("ehcache:myCache1?cacheManager=#myManager", EhcacheEndpoint.class);
            EhcacheEndpoint e2 = context.getEndpoint("ehcache:myCache2?cacheManager=#myManager", EhcacheEndpoint.class);

            Assert.assertSame(e1.getManager(), e2.getManager());
            Assert.assertSame(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            Assert.assertEquals(2, e1.getManager().getReferenceCount().get());
            Assert.assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

            context.stop();

            Assert.assertSame(e1.getManager(), e2.getManager());
            Assert.assertSame(e1.getManager().getCacheManager(), e2.getManager().getCacheManager());
            Assert.assertEquals(0, e1.getManager().getReferenceCount().get());
            Assert.assertEquals(Status.AVAILABLE, e1.getManager().getCacheManager().getStatus());

        } finally {
            if (context != null) {
                context.stop();
            }
            if (cacheManager != null) {
                cacheManager.close();
            }
        }
    }
}
