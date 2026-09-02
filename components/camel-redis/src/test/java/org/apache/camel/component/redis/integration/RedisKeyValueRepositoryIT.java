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
package org.apache.camel.component.redis.integration;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.redis.RedisKeyValueRepository;
import org.apache.camel.test.infra.redis.services.RedisService;
import org.apache.camel.test.infra.redis.services.RedisServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class RedisKeyValueRepositoryIT {

    @RegisterExtension
    static RedisService service = RedisServiceFactory.createSingletonService();

    private RedisKeyValueRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisKeyValueRepository(service.getServiceAddress(), "test-kvr:");
        repository.start();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.clear();
            repository.stop();
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
        // Store with a short TTL
        repository.put("expiring", "value", Duration.ofMillis(500));
        assertThat(repository.contains("expiring")).isTrue();

        // Wait for it to expire
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(repository.contains("expiring")).isFalse());
    }

    @Test
    void testTtlPutIfAbsentExpiry() {
        repository.putIfAbsent("expiring", "value", Duration.ofMillis(500));
        assertThat(repository.contains("expiring")).isTrue();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(repository.contains("expiring")).isFalse());
    }

    @Test
    void testSerializableValues() {
        // Test that non-String serializable objects work
        repository.put("int-key", 42, null);
        assertThat(repository.get("int-key")).isEqualTo(42);

        repository.put("bool-key", Boolean.TRUE, null);
        assertThat(repository.get("bool-key")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void testKeyPrefixIsolation() {
        // Create a second repository with a different prefix
        RedisKeyValueRepository other = new RedisKeyValueRepository(service.getServiceAddress(), "other-kvr:");
        other.start();
        try {
            repository.put("shared-key", "from-repo1", null);
            other.put("shared-key", "from-repo2", null);

            assertThat(repository.get("shared-key")).isEqualTo("from-repo1");
            assertThat(other.get("shared-key")).isEqualTo("from-repo2");

            assertThat(repository.keys()).containsExactly("shared-key");
            assertThat(other.keys()).containsExactly("shared-key");

            // Clearing one should not affect the other
            repository.clear();
            assertThat(repository.keys()).isEmpty();
            assertThat(other.get("shared-key")).isEqualTo("from-repo2");
        } finally {
            other.clear();
            other.stop();
        }
    }

    @Test
    void testCustomRedissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://%s", service.getServiceAddress()));
        RedissonClient customClient = Redisson.create(config);

        try {
            RedisKeyValueRepository customRepo = new RedisKeyValueRepository();
            customRepo.setKeyPrefix("custom-kvr:");
            customRepo.setRedisson(customClient);
            customRepo.start();

            customRepo.put("key1", "value1", null);
            assertThat(customRepo.get("key1")).isEqualTo("value1");

            customRepo.clear();
            customRepo.stop();

            // Custom client should not be shut down by the repository
            assertThat(customClient.isShutdown()).isFalse();
        } finally {
            customClient.shutdown();
        }
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
        boolean replaced = repository.replace("key1", "value1", "value2", Duration.ofMillis(500));
        assertThat(replaced).isTrue();
        assertThat(repository.get("key1")).isEqualTo("value2");

        await().atMost(5, TimeUnit.SECONDS)
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
    void testEndpointValidation() {
        RedisKeyValueRepository repo = new RedisKeyValueRepository();
        assertThatThrownBy(repo::init).isInstanceOf(IllegalArgumentException.class);
    }
}
