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

import java.util.Properties;

import org.apache.camel.BindToRegistry;
import org.apache.camel.component.infinispan.InfinispanTestSupport;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.configuration.cache.CacheMode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class InfinispanRemoteTestSupport extends InfinispanTestSupport {
    @RegisterExtension
    public static InfinispanService service = InfinispanServiceFactory.createService();

    protected RemoteCacheManager cacheContainer;

    @Override
    protected void setupResources() throws Exception {
        LoggerFactory.getLogger(getClass()).info("setupResources");

        cacheContainer = new RemoteCacheManager(getConfiguration().build());
        cacheContainer.administration()
                .getOrCreateCache(
                        InfinispanTestSupport.TEST_CACHE,
                        new org.infinispan.configuration.cache.ConfigurationBuilder()
                                .clustering()
                                .cacheMode(CacheMode.DIST_SYNC).build());

        super.setupResources();
    }

    @Override
    protected void cleanupResources() throws Exception {
        LoggerFactory.getLogger(getClass()).info("setupResources");

        if (cacheContainer != null) {
            cacheContainer.stop();
        }

        super.cleanupResources();
    }

    @Override
    protected BasicCache<Object, Object> getCache(String name) {
        return cacheContainer.getCache(name);
    }

    protected ConfigurationBuilder getConfiguration() {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();

        // for default tests, we force return value for all the
        // operations
        clientBuilder
                .forceReturnValues(true);

        // add server from the test infra service
        clientBuilder
                .addServer()
                .host(service.host())
                .port(service.port());

        // add security info
        clientBuilder
                .security()
                .authentication()
                .username(service.username())
                .password(service.password())
                .serverName("infinispan")
                .saslMechanism("DIGEST-MD5")
                .realm("default");

        if (SystemUtils.IS_OS_MAC) {
            Properties properties = new Properties();
            properties.put("infinispan.client.hotrod.client_intelligence", "BASIC");
            clientBuilder.withProperties(properties);
        }
        return clientBuilder;
    }

    @BindToRegistry
    public ComponentCustomizer infinispanComponentCustomizer() {
        return ComponentCustomizer.forType(
                InfinispanRemoteComponent.class,
                component -> component.getConfiguration().setCacheContainer(cacheContainer));
    }
}
