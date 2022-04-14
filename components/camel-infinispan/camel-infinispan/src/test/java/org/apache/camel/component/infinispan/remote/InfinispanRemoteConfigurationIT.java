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
package org.apache.camel.component.infinispan.remote;

import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.configuration.cache.CacheMode;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanRemoteConfigurationIT {
    @RegisterExtension
    static InfinispanService service = InfinispanServiceFactory.createService();

    @Test
    public void remoteCacheWithoutProperties() throws Exception {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setHosts(service.host() + ":" + service.port());
        configuration.setSecure(true);
        configuration.setUsername(service.username());
        configuration.setPassword(service.password());
        configuration.setSecurityServerName("infinispan");
        configuration.setSaslMechanism("DIGEST-MD5");
        configuration.setSecurityRealm("default");
        if (SystemUtils.IS_OS_MAC) {
            configuration.addConfigurationProperty(
                    "infinispan.client.hotrod.client_intelligence", "BASIC");
        }

        try (InfinispanRemoteManager manager = new InfinispanRemoteManager(configuration)) {
            manager.start();
            manager.getCacheContainer().administration()
                    .getOrCreateCache(
                            "misc_cache",
                            new org.infinispan.configuration.cache.ConfigurationBuilder()
                                    .clustering()
                                    .cacheMode(CacheMode.DIST_SYNC).build());

            BasicCache<Object, Object> cache = manager.getCache("misc_cache");
            assertNotNull(cache);
            assertTrue(cache instanceof RemoteCache);

            String key = UUID.randomUUID().toString();
            assertNull(cache.put(key, "val1"));
            assertNull(cache.put(key, "val2"));
        }
    }

    @Test
    public void remoteCacheWithProperties() throws Exception {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setHosts(service.host() + ":" + service.port());
        configuration.setSecure(true);
        configuration.setUsername(service.username());
        configuration.setPassword(service.password());
        configuration.setSecurityServerName("infinispan");
        configuration.setSaslMechanism("DIGEST-MD5");
        configuration.setSecurityRealm("default");
        if (SystemUtils.IS_OS_MAC) {
            configuration.setConfigurationUri("infinispan/client-mac.properties");
        } else {
            configuration.setConfigurationUri("infinispan/client.properties");
        }

        try (InfinispanRemoteManager manager = new InfinispanRemoteManager(configuration)) {
            manager.start();
            manager.getCacheContainer().administration()
                    .getOrCreateCache(
                            "misc_cache",
                            new org.infinispan.configuration.cache.ConfigurationBuilder()
                                    .clustering()
                                    .cacheMode(CacheMode.DIST_SYNC).build());

            BasicCache<Object, Object> cache = manager.getCache("misc_cache");
            assertNotNull(cache);
            assertTrue(cache instanceof RemoteCache);

            String key = UUID.randomUUID().toString();
            assertNull(cache.put(key, "val1"));
            assertNotNull(cache.put(key, "val2"));
        }
    }
}
