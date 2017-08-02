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
package org.apache.camel.component.infinispan;

import org.infinispan.cache.impl.DecoratedCache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.jgroups.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InfinispanConfigurationTestIT {

    @Test
    public void embeddedCacheWithFlagsTest() throws Exception {
        InfinispanConfiguration configuration = new InfinispanConfiguration();
        configuration.setHosts("localhost");
        configuration.setCacheContainer(new DefaultCacheManager(new ConfigurationBuilder().build(), true));

        InfinispanManager manager = new InfinispanManager(configuration);
        manager.start();

        BasicCache<Object, Object> cache = manager.getCache("misc_cache");
        assertNotNull(cache);

        manager.getCacheContainer().stop();
        manager.stop();
    }

    @Test
    public void remoteCacheWithoutProperties() throws Exception {
        InfinispanConfiguration configuration = new InfinispanConfiguration();
        configuration.setHosts("localhost");

        InfinispanManager manager = new InfinispanManager(configuration);
        manager.start();

        BasicCache<Object, Object> cache = manager.getCache("misc_cache");
        assertNotNull(cache);
        assertTrue(cache instanceof RemoteCache);

        RemoteCache<Object, Object> remoteCache = InfinispanUtil.asRemote(cache);

        String key = UUID.randomUUID().toString();
        assertNull(remoteCache.put(key, "val1"));
        assertNull(remoteCache.put(key, "val2"));

        manager.stop();
    }

    @Test
    public void remoteCacheWithPropertiesTest() throws Exception {
        InfinispanConfiguration configuration = new InfinispanConfiguration();
        configuration.setHosts("localhost");
        configuration.setConfigurationUri("infinispan/client.properties");

        InfinispanManager manager = new InfinispanManager(configuration);
        manager.start();

        BasicCache<Object, Object> cache = manager.getCache("misc_cache");
        assertNotNull(cache);
        assertTrue(cache instanceof RemoteCache);

        String key = UUID.randomUUID().toString();
        assertNull(cache.put(key, "val1"));
        assertNotNull(cache.put(key, "val2"));

        manager.stop();
    }
}
