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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.infinispan.common.InfinispanProperties;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.configuration.cache.CacheMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class InfinispanRemoteKeyValueRepositoryIT {

    private static final String CACHE_NAME = "kvr-test";

    @RegisterExtension
    static InfinispanService service = InfinispanServiceFactory.createSingletonInfinispanService();

    private CamelContext camelContext;
    private RemoteCacheManager cacheManager;
    private InfinispanRemoteKeyValueRepository repository;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();

        cacheManager = new RemoteCacheManager(getConfiguration().build());

        // Create the cache on the server
        cacheManager.administration()
                .getOrCreateCache(
                        CACHE_NAME,
                        new org.infinispan.configuration.cache.ConfigurationBuilder()
                                .clustering()
                                .cacheMode(CacheMode.DIST_SYNC)
                                .build());

        InfinispanRemoteConfiguration config = new InfinispanRemoteConfiguration();
        config.setCacheContainer(cacheManager);

        repository = new InfinispanRemoteKeyValueRepository(CACHE_NAME);
        repository.setCamelContext(camelContext);
        repository.setConfiguration(config);
        repository.start();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            try {
                repository.clear();
            } catch (Exception e) {
                // ignore cleanup errors
            }
            repository.stop();
        }
        if (cacheManager != null) {
            cacheManager.stop();
        }
        if (camelContext != null) {
            try {
                camelContext.close();
            } catch (Exception e) {
                // ignore cleanup errors
            }
        }
    }

    @Test
    void testPutAndGet() {
        Object previous = repository.put("key1", "value1", null);
        assertThat(previous).isNull();

        Object result = repository.get("key1");
        assertThat(result).isEqualTo("value1");
    }

    @Test
    void testPutReturnsOldValue() {
        repository.put("key1", "first", null);
        Object previous = repository.put("key1", "second", null);
        assertThat(previous).isEqualTo("first");

        Object result = repository.get("key1");
        assertThat(result).isEqualTo("second");
    }

    @Test
    void testGetNonExistent() {
        Object result = repository.get("nonexistent");
        assertThat(result).isNull();
    }

    @Test
    void testDelete() {
        repository.put("key1", "value1", null);
        Object deleted = repository.delete("key1");
        assertThat(deleted).isEqualTo("value1");
        assertThat(repository.get("key1")).isNull();
    }

    @Test
    void testDeleteNonExistent() {
        Object deleted = repository.delete("nonexistent");
        assertThat(deleted).isNull();
    }

    @Test
    void testContains() {
        repository.put("key1", "value1", null);
        assertThat(repository.contains("key1")).isTrue();
        assertThat(repository.contains("nonexistent")).isFalse();
    }

    @Test
    void testKeys() {
        repository.put("a", "1", null);
        repository.put("b", "2", null);
        repository.put("c", "3", null);

        Set<String> keys = repository.keys();
        assertThat(keys).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void testKeysEmpty() {
        Set<String> keys = repository.keys();
        assertThat(keys).isEmpty();
    }

    @Test
    void testClear() {
        repository.put("a", "1", null);
        repository.put("b", "2", null);

        repository.clear();

        assertThat(repository.keys()).isEmpty();
        assertThat(repository.contains("a")).isFalse();
    }

    @Test
    void testPutIfAbsentNewKey() {
        Object existing = repository.putIfAbsent("key1", "value1", null);
        assertThat(existing).isNull();
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testPutIfAbsentExistingKey() {
        repository.put("key1", "original", null);
        Object existing = repository.putIfAbsent("key1", "replacement", null);
        assertThat(existing).isEqualTo("original");
        assertThat(repository.get("key1")).isEqualTo("original");
    }

    @Test
    void testSize() {
        assertThat(repository.size()).isZero();

        repository.put("a", "1", null);
        repository.put("b", "2", null);
        assertThat(repository.size()).isEqualTo(2);

        repository.delete("a");
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void testTtlExpiry() {
        // Store with a short TTL (Infinispan lifespan)
        repository.put("expiring", "value", Duration.ofSeconds(1));
        assertThat(repository.contains("expiring")).isTrue();

        // Wait for it to expire
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(repository.contains("expiring")).isFalse());
    }

    @Test
    void testTtlPutIfAbsentExpiry() {
        repository.putIfAbsent("expiring", "value", Duration.ofSeconds(1));
        assertThat(repository.contains("expiring")).isTrue();

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(repository.contains("expiring")).isFalse());
    }

    @Test
    void testReplaceMatchingValue() {
        repository.put("key1", "value1", null);
        boolean replaced = repository.replace("key1", "value1", "value2", null);
        assertThat(replaced).isTrue();
        assertThat(repository.get("key1")).isEqualTo("value2");
    }

    @Test
    void testReplaceNonMatchingValue() {
        repository.put("key1", "value1", null);
        boolean replaced = repository.replace("key1", "wrong", "value2", null);
        assertThat(replaced).isFalse();
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testReplaceMissingKey() {
        boolean replaced = repository.replace("nonexistent", "value1", "value2", null);
        assertThat(replaced).isFalse();
    }

    @Test
    void testReplaceWithTtl() {
        repository.put("key1", "value1", null);
        boolean replaced = repository.replace("key1", "value1", "value2", Duration.ofSeconds(1));
        assertThat(replaced).isTrue();
        assertThat(repository.get("key1")).isEqualTo("value2");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(repository.get("key1")).isNull());
    }

    @Test
    void testDeleteWithMatchingValue() {
        repository.put("key1", "value1", null);
        boolean deleted = repository.delete("key1", "value1");
        assertThat(deleted).isTrue();
        assertThat(repository.get("key1")).isNull();
    }

    @Test
    void testDeleteWithNonMatchingValue() {
        repository.put("key1", "value1", null);
        boolean deleted = repository.delete("key1", "wrong");
        assertThat(deleted).isFalse();
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testDeleteWithMissingKey() {
        boolean deleted = repository.delete("nonexistent", "value1");
        assertThat(deleted).isFalse();
    }

    @Test
    void testCacheNameRequired() {
        InfinispanRemoteKeyValueRepository repo = new InfinispanRemoteKeyValueRepository();
        repo.setCamelContext(camelContext);
        assertThatThrownBy(repo::start).isInstanceOf(IllegalArgumentException.class);
    }

    private ConfigurationBuilder getConfiguration() {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();

        clientBuilder.forceReturnValues(true);

        clientBuilder
                .addServer()
                .host(service.host())
                .port(service.port());

        clientBuilder
                .socketTimeout(15000)
                .connectionTimeout(15000)
                .security()
                .authentication()
                .username(service.username())
                .password(service.password())
                .serverName("infinispan")
                .saslMechanism("SCRAM-SHA-512")
                .realm("default");

        if (!Boolean.getBoolean(InfinispanProperties.INFINISPAN_CONTAINER_NETWORK_MODE_HOST)) {
            Properties properties = new Properties();
            properties.put("infinispan.client.hotrod.client_intelligence", "BASIC");
            clientBuilder.withProperties(properties);
        }
        return clientBuilder;
    }
}
