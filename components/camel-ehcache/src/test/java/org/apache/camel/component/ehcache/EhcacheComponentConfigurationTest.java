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
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.Test;

public class EhcacheComponentConfigurationTest extends CamelTestSupport {
    @EndpointInject(uri = "ehcache:myCache")
    private EhcacheEndpoint endpoint;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        EhcacheComponent component = new EhcacheComponent();
        component.getConfiguration().setKeyType(String.class);
        component.getConfiguration().setValueType(String.class);
        component.getConfiguration().setCacheManager(
            CacheManagerBuilder.newCacheManagerBuilder()
                .withCache(
                    "myCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class,
                        String.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder()
                            .heap(100, EntryUnit.ENTRIES)
                            .offheap(1, MemoryUnit.MB))
                ).build(true)
        );

        JndiRegistry registry = super.createRegistry();
        registry.bind("ehcache", component);

        return registry;
    }

    // *****************************
    // Test
    // *****************************

    @Test
    public void testCacheManager() throws Exception {
        assertEquals(
            context().getRegistry().lookupByNameAndType("ehcache", EhcacheComponent.class).getCacheManager(),
            endpoint.getManager().getCacheManager()
        );

        Cache<String, String> cache =  endpoint.getManager().getCache("myCache", String.class, String.class);
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
                from("direct:ehcache")
                    .to(endpoint);
            }
        };
    }
}
