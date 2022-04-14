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
package org.apache.camel.component.infinispan.remote.cluster;

import java.util.Properties;

import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.configuration.cache.CacheMode;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

public final class InfinispanRemoteClusteredTestSupport {
    private InfinispanRemoteClusteredTestSupport() {
    }

    public static Configuration createConfiguration(InfinispanService service) {
        if (SystemUtils.IS_OS_MAC) {
            Properties properties = new Properties();
            properties.put("infinispan.client.hotrod.client_intelligence", "BASIC");
            return new ConfigurationBuilder()
                    .withProperties(properties)
                    .addServer()
                    .host(service.host())
                    .port(service.port())
                    .security()
                    .authentication()
                    .username(service.username())
                    .password(service.password())
                    .serverName("infinispan")
                    .saslMechanism("DIGEST-MD5")
                    .realm("default")
                    .build();
        } else {
            return new ConfigurationBuilder()
                    .addServer()
                    .host(service.host())
                    .port(service.port())
                    .security()
                    .authentication()
                    .username(service.username())
                    .password(service.password())
                    .serverName("infinispan")
                    .saslMechanism("DIGEST-MD5")
                    .realm("default")
                    .build();
        }

    }

    public static void createCache(RemoteCacheManager cacheContainer, String cacheName) {
        cacheContainer.administration()
                .getOrCreateCache(
                        cacheName,
                        new org.infinispan.configuration.cache.ConfigurationBuilder()
                                .clustering()
                                .cacheMode(CacheMode.DIST_SYNC).build());
    }
}
