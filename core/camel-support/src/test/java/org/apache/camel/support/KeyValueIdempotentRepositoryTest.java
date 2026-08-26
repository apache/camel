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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueIdempotentRepositoryTest {

    private MemoryKeyValueRepository kvRepository;
    private KeyValueIdempotentRepository idempotentRepository;

    @BeforeEach
    void setUp() throws Exception {
        kvRepository = new MemoryKeyValueRepository();
        kvRepository.start();
        idempotentRepository = new KeyValueIdempotentRepository(kvRepository);
        idempotentRepository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        idempotentRepository.stop();
    }

    @Test
    void testAddNewKey() {
        assertThat(idempotentRepository.add("msg-001")).isTrue();
    }

    @Test
    void testAddDuplicateKey() {
        idempotentRepository.add("msg-001");

        assertThat(idempotentRepository.add("msg-001")).isFalse();
    }

    @Test
    void testContains() {
        assertThat(idempotentRepository.contains("msg-001")).isFalse();

        idempotentRepository.add("msg-001");

        assertThat(idempotentRepository.contains("msg-001")).isTrue();
    }

    @Test
    void testRemove() {
        idempotentRepository.add("msg-001");

        assertThat(idempotentRepository.remove("msg-001")).isTrue();
        assertThat(idempotentRepository.contains("msg-001")).isFalse();
    }

    @Test
    void testRemoveNonExistentKey() {
        assertThat(idempotentRepository.remove("msg-001")).isFalse();
    }

    @Test
    void testConfirmIsNoop() {
        idempotentRepository.add("msg-001");

        assertThat(idempotentRepository.confirm("msg-001")).isTrue();
        // Key should still be present after confirm
        assertThat(idempotentRepository.contains("msg-001")).isTrue();
    }

    @Test
    void testClear() {
        idempotentRepository.add("msg-001");
        idempotentRepository.add("msg-002");
        idempotentRepository.add("msg-003");

        idempotentRepository.clear();

        assertThat(idempotentRepository.contains("msg-001")).isFalse();
        assertThat(idempotentRepository.contains("msg-002")).isFalse();
        assertThat(idempotentRepository.contains("msg-003")).isFalse();
    }

    @Test
    void testAddAfterRemoveSucceeds() {
        idempotentRepository.add("msg-001");
        idempotentRepository.remove("msg-001");

        // Should be able to add the key again
        assertThat(idempotentRepository.add("msg-001")).isTrue();
    }

    @Test
    void testMultipleKeys() {
        assertThat(idempotentRepository.add("msg-001")).isTrue();
        assertThat(idempotentRepository.add("msg-002")).isTrue();
        assertThat(idempotentRepository.add("msg-003")).isTrue();

        assertThat(idempotentRepository.contains("msg-001")).isTrue();
        assertThat(idempotentRepository.contains("msg-002")).isTrue();
        assertThat(idempotentRepository.contains("msg-003")).isTrue();

        // Adding duplicates should fail
        assertThat(idempotentRepository.add("msg-001")).isFalse();
        assertThat(idempotentRepository.add("msg-002")).isFalse();
    }

    @Test
    void testStoresMarkerValueInUnderlyingRepository() {
        idempotentRepository.add("msg-001");

        // The underlying KV repository should have the key with Boolean.TRUE as value
        assertThat(kvRepository.get("msg-001")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void testGetRepository() {
        assertThat(idempotentRepository.getRepository()).isSameAs(kvRepository);
    }

    @Test
    void testFactoryMethod() {
        KeyValueIdempotentRepository created
                = KeyValueIdempotentRepository.keyValueIdempotentRepository(kvRepository);

        assertThat(created).isNotNull();
        assertThat(created.getRepository()).isSameAs(kvRepository);
    }
}
