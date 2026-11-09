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
package org.apache.camel.support;

import java.time.Duration;
import java.util.Set;

import org.apache.camel.spi.KeyValueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespacedKeyValueRepositoryTest {

    private MemoryKeyValueRepository delegate;
    private NamespacedKeyValueRepository repoA;
    private NamespacedKeyValueRepository repoB;

    @BeforeEach
    void setUp() throws Exception {
        delegate = new MemoryKeyValueRepository();
        delegate.start();

        repoA = new NamespacedKeyValueRepository(delegate, "nsA");
        repoA.start();

        repoB = new NamespacedKeyValueRepository(delegate, "nsB:");
        repoB.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        repoA.stop();
        repoB.stop();
        delegate.stop();
    }

    @Test
    void testPutAndGet() {
        repoA.put("key1", "valueA");
        repoB.put("key1", "valueB");

        assertThat(repoA.get("key1")).isEqualTo("valueA");
        assertThat(repoB.get("key1")).isEqualTo("valueB");

        assertThat(delegate.get("nsA:key1")).isEqualTo("valueA");
        assertThat(delegate.get("nsB:key1")).isEqualTo("valueB");
    }

    @Test
    void testContains() {
        repoA.put("key1", "valueA");

        assertThat(repoA.contains("key1")).isTrue();
        assertThat(repoB.contains("key1")).isFalse();
        assertThat(repoA.contains("key2")).isFalse();
    }

    @Test
    void testDelete() {
        repoA.put("key1", "valueA");
        repoB.put("key1", "valueB");

        Object deleted = repoA.delete("key1");
        assertThat(deleted).isEqualTo("valueA");

        assertThat(repoA.get("key1")).isNull();
        assertThat(repoB.get("key1")).isEqualTo("valueB");
        assertThat(delegate.get("nsA:key1")).isNull();
        assertThat(delegate.get("nsB:key1")).isEqualTo("valueB");
    }

    @Test
    void testKeys() {
        repoA.put("key1", "v1");
        repoA.put("key2", "v2");
        repoB.put("key3", "v3");

        Set<String> keysA = repoA.keys();
        assertThat(keysA).containsExactlyInAnyOrder("key1", "key2");

        Set<String> keysB = repoB.keys();
        assertThat(keysB).containsExactly("key3");
    }

    @Test
    void testSize() {
        repoA.put("key1", "v1");
        repoA.put("key2", "v2");
        repoB.put("key3", "v3");

        assertThat(repoA.size()).isEqualTo(2);
        assertThat(repoB.size()).isEqualTo(1);
    }

    @Test
    void testClearOnlyClearsOwnNamespace() {
        repoA.put("key1", "v1");
        repoA.put("key2", "v2");
        repoB.put("key1", "vB1");
        repoB.put("key2", "vB2");

        repoA.clear();

        assertThat(repoA.keys()).isEmpty();
        assertThat(repoA.size()).isZero();

        assertThat(repoB.keys()).containsExactlyInAnyOrder("key1", "key2");
        assertThat(repoB.size()).isEqualTo(2);
        assertThat(repoB.get("key1")).isEqualTo("vB1");
    }

    @Test
    void testPutIfAbsent() {
        Object first = repoA.putIfAbsent("key1", "val1");
        assertThat(first).isNull();

        Object second = repoA.putIfAbsent("key1", "val2");
        assertThat(second).isEqualTo("val1");
        assertThat(repoA.get("key1")).isEqualTo("val1");

        Object bResult = repoB.putIfAbsent("key1", "valB");
        assertThat(bResult).isNull();
        assertThat(repoB.get("key1")).isEqualTo("valB");
    }

    @Test
    void testReplace() {
        repoA.put("key1", "oldVal");

        boolean failed = repoA.replace("key1", "wrong", "newVal", Duration.ofMinutes(1));
        assertThat(failed).isFalse();
        assertThat(repoA.get("key1")).isEqualTo("oldVal");

        boolean replaced = repoA.replace("key1", "oldVal", "newVal", Duration.ofMinutes(1));
        assertThat(replaced).isTrue();
        assertThat(repoA.get("key1")).isEqualTo("newVal");
    }

    @Test
    void testDeleteWithValue() {
        repoA.put("key1", "val1");

        boolean failed = repoA.delete("key1", "wrong");
        assertThat(failed).isFalse();
        assertThat(repoA.get("key1")).isEqualTo("val1");

        boolean deleted = repoA.delete("key1", "val1");
        assertThat(deleted).isTrue();
        assertThat(repoA.get("key1")).isNull();
    }

    @Test
    void testFactoryMethodAvoidsRedundantWrapping() {
        KeyValueRepository wrapped = NamespacedKeyValueRepository.namespaced(delegate, "myNs");
        KeyValueRepository wrappedAgain = NamespacedKeyValueRepository.namespaced(wrapped, "myNs");

        assertThat(wrappedAgain).isSameAs(wrapped);
    }

    @Test
    void testNullKeyThrows() {
        assertThatThrownBy(() -> repoA.get(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repoA.put(null, "val"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
