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

import java.time.Duration;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.component.infinispan.InfinispanManager;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.support.task.ForegroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.exceptions.RemoteIllegalLifecycleStateException;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.infinispan.InfinispanConstants.CACHE_MANAGER_CURRENT;

public class InfinispanRemoteManager extends ServiceSupport implements InfinispanManager<RemoteCacheManager> {
    private static final Logger LOG = LoggerFactory.getLogger(InfinispanRemoteManager.class);

    private final InfinispanRemoteConfiguration configuration;
    private CamelContext camelContext;
    private RemoteCacheManager cacheContainer;
    private boolean isManagedCacheContainer;

    public InfinispanRemoteManager(CamelContext camelContext, InfinispanRemoteConfiguration configuration) {
        this.camelContext = camelContext;
        this.configuration = configuration;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void doStart() throws Exception {
        cacheContainer = configuration.getCacheContainer();

        boolean embeddingStoreEnabled = EmbeddingStoreUtil.isEmbeddingStoreEnabled(camelContext, configuration);
        if (embeddingStoreEnabled && configuration.getEmbeddingStoreDimension() <= 0) {
            throw new IllegalArgumentException("embeddingStoreDimension must be configured");
        }

        if (cacheContainer == null) {
            final Configuration containerConf = configuration.getCacheContainerConfiguration();
            // Check if a container configuration object has been provided so use
            // it and discard any other additional configuration.
            if (containerConf != null) {
                cacheContainer = new RemoteCacheManager(containerConf, true);
            }

            // If the hosts properties has been configured, it means we want to
            // connect to a remote cache so set-up a RemoteCacheManager
            if (cacheContainer == null) {
                ConfigurationBuilder builder = new ConfigurationBuilder();
                builder.addServers(configuration.getHosts());

                if (configuration.isSecure()) {
                    if (ObjectHelper.isNotEmpty(configuration.getUsername())
                            && ObjectHelper.isNotEmpty(configuration.getPassword())) {
                        builder.security().authentication().username(configuration.getUsername())
                                .password(configuration.getPassword());
                    } else {
                        throw new IllegalArgumentException(
                                "If the Infinispan instance is secured, username and password are needed");
                    }
                    if (ObjectHelper.isNotEmpty(configuration.getSaslMechanism())) {
                        builder.security().authentication().saslMechanism(configuration.getSaslMechanism());
                    }
                    if (ObjectHelper.isNotEmpty(configuration.getSecurityRealm())) {
                        builder.security().authentication().realm(configuration.getSecurityRealm());
                    }
                    if (ObjectHelper.isNotEmpty(configuration.getSecurityServerName())) {
                        builder.security().authentication().serverName(configuration.getSecurityServerName());
                    }
                }
                Properties properties = new Properties();

                // Properties can be set either via a properties file or via
                // properties on configuration, if you set both they are merged
                // with properties defined on configuration overriding those from
                // file.
                if (ObjectHelper.isNotEmpty(configuration.getConfigurationUri())) {
                    properties.putAll(InfinispanUtil.loadProperties(camelContext, configuration.getConfigurationUri()));
                }
                if (ObjectHelper.isNotEmpty(configuration.getConfigurationProperties())) {
                    configuration.getConfigurationProperties().forEach((k, v) -> {
                        properties.put(
                                k.startsWith("infinispan.client.hotrod.") ? k : "infinispan.client.hotrod." + k,
                                v);
                    });
                }
                if (!properties.isEmpty()) {
                    builder.withProperties(properties);
                }

                if (embeddingStoreEnabled) {
                    EmbeddingStoreUtil.configureMarshaller(configuration, builder);
                }

                cacheContainer = new RemoteCacheManager(builder.build(), true);
            }

            isManagedCacheContainer = true;
        }

        if (embeddingStoreEnabled && configuration.isEmbeddingStoreRegisterSchema()) {
            registerSchemaWithRetry();
        }
    }

    /**
     * Registers the embedding store schema with retry logic to handle the case where Camel and the remote Infinispan
     * server start up concurrently (e.g., in Kubernetes). The server's internal {@code ___protobuf_metadata} cache may
     * not be ready yet, causing a {@link RemoteIllegalLifecycleStateException}.
     */
    private void registerSchemaWithRetry() throws Exception {
        Duration timeout = configuration.getEmbeddingStoreSchemaRegistrationTimeout();
        ForegroundTask task = Tasks.foregroundTask()
                .withName("infinispan-schema-registration")
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofSeconds(1))
                        .withMaxDuration(timeout)
                        .build())
                .build();

        final boolean[] firstAttempt = { true };
        boolean registered = task.run(camelContext, () -> {
            try {
                EmbeddingStoreUtil.registerSchema(configuration, cacheContainer);
                return true;
            } catch (RemoteIllegalLifecycleStateException e) {
                if (firstAttempt[0]) {
                    firstAttempt[0] = false;
                    LOG.info("Infinispan server not ready for schema registration, will retry for up to {}: {}",
                            timeout, e.getMessage());
                } else {
                    LOG.debug("Schema registration failed (server not ready), retrying: {}", e.getMessage());
                }
                return false;
            }
        });

        if (!registered) {
            throw new IllegalStateException(
                    "Failed to register Infinispan schema after " + timeout
                                            + " of retries. The server may not be fully started.");
        }
    }

    @Override
    public void doStop() throws Exception {
        if (isManagedCacheContainer) {
            cacheContainer.stop();
        }
    }

    @Override
    public RemoteCacheManager getCacheContainer() {
        return cacheContainer;
    }

    @Override
    public <K, V> BasicCache<K, V> getCache() {
        RemoteCache<K, V> cache = cacheContainer.getCache();

        return configuration.hasFlags()
                ? cache.withFlags(configuration.getFlags())
                : cache;
    }

    @Override
    public <K, V> BasicCache<K, V> getCache(String cacheName) {
        RemoteCache<K, V> cache;
        if (ObjectHelper.isEmpty(cacheName) || CACHE_MANAGER_CURRENT.equals(cacheName)) {
            cache = cacheContainer.getCache();
        } else {
            cache = cacheContainer.getCache(cacheName);
        }

        return configuration.hasFlags()
                ? cache.withFlags(configuration.getFlags())
                : cache;
    }

    @Override
    public Set<String> getCacheNames() {
        return cacheContainer.getCacheNames();
    }

    @Override
    public void stopCache(String cacheName) {
        cacheContainer.stopCache(cacheName);
    }
}
