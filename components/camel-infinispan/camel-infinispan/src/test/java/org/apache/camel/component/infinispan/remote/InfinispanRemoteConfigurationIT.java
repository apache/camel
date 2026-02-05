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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.apache.commons.lang3.SystemUtils;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.api.BasicCache;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanRemoteConfigurationIT {
    @RegisterExtension
    static InfinispanService service = InfinispanServiceFactory.createSingletonInfinispanService();

    @Test
    public void remoteCacheWithoutProperties() throws Exception {
        final InfinispanRemoteConfiguration configuration = getBaseConfiguration();
        if (SystemUtils.IS_OS_MAC) {
            configuration.addConfigurationProperty(
                    "infinispan.client.hotrod.client_intelligence", "BASIC");
        }

        try (CamelContext context = new DefaultCamelContext();
             InfinispanRemoteManager manager = new InfinispanRemoteManager(context, configuration)) {
            manager.start();
            InfinispanRemoteTestSupport.waitForCacheReady(manager.getCacheContainer(), "misc_cache", 5000);

            BasicCache<Object, Object> cache = manager.getCache("misc_cache");
            assertNotNull(cache);
            assertTrue(cache instanceof RemoteCache);

            String key = UUID.randomUUID().toString();
            assertNull(cache.put(key, "val1"));
            assertNull(cache.put(key, "val2"));
        }
    }

    private static InfinispanRemoteConfiguration getBaseConfiguration() {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        // We better control the timeout as it can become flaky on CI envs.
        Map<String, String> cacheContConf = new HashMap<>();
        cacheContConf.put("socket_timeout", "15000");
        cacheContConf.put("connection_timeout", "15000");
        configuration.setConfigurationProperties(cacheContConf);

        configuration.setHosts(service.host() + ":" + service.port());
        configuration.setSecure(true);
        configuration.setUsername(service.username());
        configuration.setPassword(service.password());
        configuration.setSecurityServerName("infinispan");
        configuration.setSaslMechanism("SCRAM-SHA-512");
        configuration.setSecurityRealm("default");
        return configuration;
    }

    @Test
    public void remoteCacheWithProperties() throws Exception {
        final InfinispanRemoteConfiguration configuration = getBaseConfiguration();
        if (SystemUtils.IS_OS_MAC) {
            configuration.setConfigurationUri("infinispan/client-mac.properties");
        } else {
            configuration.setConfigurationUri("infinispan/client.properties");
        }

        try (CamelContext context = new DefaultCamelContext();
             InfinispanRemoteManager manager = new InfinispanRemoteManager(context, configuration)) {
            manager.start();
            InfinispanRemoteTestSupport.waitForCacheReady(manager.getCacheContainer(), "misc_cache", 5000);

            BasicCache<Object, Object> cache = manager.getCache("misc_cache");
            assertNotNull(cache);
            assertTrue(cache instanceof RemoteCache);

            String key = UUID.randomUUID().toString();
            assertNull(cache.put(key, "val1"));
            assertNotNull(cache.put(key, "val2"));
        }
    }

}
