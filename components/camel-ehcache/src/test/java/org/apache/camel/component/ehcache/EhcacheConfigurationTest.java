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

package org.apache.camel.component.ehcache;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.ehcache.Cache;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.ResourceType;
import org.ehcache.config.SizedResourcePool;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhcacheConfigurationTest extends CamelTestSupport {
    public static final Logger LOGGER = LoggerFactory.getLogger(EhcacheConfigurationTest.class);

    @EndpointInject(uri = "ehcache://myCacheConf?configuration=#myConf")
    private EhcacheEndpoint ehcacheConf;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind(
            "myConf",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                String.class,
                String.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder()
                    .heap(100, EntryUnit.ENTRIES)
                    .offheap(1, MemoryUnit.MB))
            .build()
        );

        return registry;
    }

    @Test
    public void testProgrammaticConfiguration() throws Exception {
        EhcacheManager manager = ehcacheConf.getManager();
        Cache<String, String> cache = manager.getCache("myCacheConf", String.class, String.class);

        ResourcePools pools = cache.getRuntimeConfiguration().getResourcePools();

        SizedResourcePool h = pools.getPoolForResource(ResourceType.Core.HEAP);
        assertNotNull(h);
        assertEquals(100, h.getSize());
        assertEquals(EntryUnit.ENTRIES, h.getUnit());

        SizedResourcePool o = pools.getPoolForResource(ResourceType.Core.OFFHEAP);
        assertNotNull(o);
        assertEquals(1, o.getSize());
        assertEquals(MemoryUnit.MB, o.getUnit());
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start").to(ehcacheConf);
            }
        };
    }
}
