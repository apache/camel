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
import org.ehcache.UserManagedCache;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.ResourceType;
import org.ehcache.config.SizedResourcePool;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.Test;

public class EhcacheConfigurationTest extends CamelTestSupport {
    @EndpointInject(uri = "ehcache:myProgrammaticCacheConf?configuration=#myProgrammaticConfiguration")
    private EhcacheEndpoint ehcacheProgrammaticConf;
    @EndpointInject(uri = "ehcache:myFileCacheConf?keyType=java.lang.String&valueType=java.lang.String&configUri=classpath:ehcache/ehcache-file-config.xml")
    private EhcacheEndpoint ehcacheFileConf;
    @EndpointInject(uri = "ehcache:myUserCacheConf")
    private EhcacheEndpoint ehcacheUserConf;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind(
            "myProgrammaticConfiguration",
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

    // *****************************
    // Test
    // *****************************

    @Test
    public void testProgrammaticConfiguration() throws Exception {
        Cache<String, String> cache = getCache(ehcacheProgrammaticConf, "myProgrammaticCacheConf");
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

    @Test
    public void testFileConfiguration() throws Exception {
        Cache<String, String> cache = getCache(ehcacheFileConf, "myFileCacheConf");
        ResourcePools pools = cache.getRuntimeConfiguration().getResourcePools();

        SizedResourcePool h = pools.getPoolForResource(ResourceType.Core.HEAP);
        assertNotNull(h);
        assertEquals(150, h.getSize());
        assertEquals(EntryUnit.ENTRIES, h.getUnit());
    }

    @Test
    public void testUserConfiguration() throws Exception {
        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT)
            .withHeader(EhcacheConstants.KEY, "user-key")
            .withBody("user-val")
            .to("direct:ehcacheUserConf")
            .send();

        Cache<Object, Object> cache = ehcacheUserConf.getManager().getCache("myUserCacheConf", Object.class, Object.class);

        assertTrue(cache instanceof UserManagedCache);
        assertEquals("user-val", cache.get("user-key"));
    }

    protected Cache<String, String> getCache(EhcacheEndpoint endpoint, String cacheName) throws Exception {
        return endpoint.getManager().getCache(cacheName, String.class, String.class);
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:ehcacheProgrammaticConf")
                    .to(ehcacheProgrammaticConf);
                from("direct:ehcacheFileConf")
                    .to(ehcacheFileConf);
                from("direct:ehcacheUserConf")
                    .to(ehcacheUserConf);
            }
        };
    }
}
