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
package org.apache.camel.component.pqc.lifecycle;

import java.security.KeyPair;
import java.security.Security;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKeyLifecycleManagerTest {

    private InMemoryKeyLifecycleManager keyManager;

    @BeforeAll
    static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @BeforeEach
    void setup() {
        keyManager = new InMemoryKeyLifecycleManager();
    }

    @Test
    void testGenerateAndRetrieveKey() throws Exception {
        KeyPair keyPair = keyManager.generateKeyPair("MLDSA", "app-key");

        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
        assertArrayEquals(keyPair.getPublic().getEncoded(), keyManager.getKey("app-key").getPublic().getEncoded());

        KeyMetadata metadata = keyManager.getKeyMetadata("app-key");
        assertNotNull(metadata);
        assertEquals("app-key", metadata.getKeyId());
        assertEquals("MLDSA", metadata.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
        assertEquals(0, metadata.getUsageCount());
        assertEquals(1, keyManager.size());
    }

    @Test
    void testGenerateKeyForOtherAlgorithm() throws Exception {
        // the documented example uses FALCON
        assertNotNull(keyManager.generateKeyPair("FALCON", "test-key"));
        assertEquals(1, keyManager.size());
    }

    @Test
    void testMissingKey() {
        assertThrows(IllegalArgumentException.class, () -> keyManager.getKey("nope"));
        assertNull(keyManager.getKeyMetadata("nope"));
    }

    @Test
    void testRotateKey() throws Exception {
        KeyPair oldKey = keyManager.generateKeyPair("MLDSA", "old-key");
        KeyPair newKey = keyManager.rotateKey("old-key", "new-key", "MLDSA");

        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, keyManager.getKeyMetadata("old-key").getStatus());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, keyManager.getKeyMetadata("new-key").getStatus());
        assertFalse(Arrays.equals(oldKey.getPublic().getEncoded(), newKey.getPublic().getEncoded()));
        assertEquals(2, keyManager.size());
    }

    @Test
    void testRotateUnknownKeyFails() {
        assertThrows(IllegalArgumentException.class, () -> keyManager.rotateKey("nope", "new", "MLDSA"));
    }

    @Test
    void testExpireAndRevokeKey() throws Exception {
        keyManager.generateKeyPair("MLDSA", "expire-key");
        keyManager.expireKey("expire-key");
        assertEquals(KeyMetadata.KeyStatus.EXPIRED, keyManager.getKeyMetadata("expire-key").getStatus());

        keyManager.generateKeyPair("MLDSA", "revoke-key");
        keyManager.revokeKey("revoke-key", "compromised");
        KeyMetadata revoked = keyManager.getKeyMetadata("revoke-key");
        assertEquals(KeyMetadata.KeyStatus.REVOKED, revoked.getStatus());
        assertTrue(revoked.getDescription().contains("compromised"));
    }

    @Test
    void testListAndDeleteKeys() throws Exception {
        keyManager.generateKeyPair("MLDSA", "k1");
        keyManager.generateKeyPair("FALCON", "k2");

        List<KeyMetadata> keys = keyManager.listKeys();
        assertEquals(2, keys.size());
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("k1")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("k2")));

        keyManager.deleteKey("k1");
        assertEquals(1, keyManager.size());
        assertNull(keyManager.getKeyMetadata("k1"));
    }

    @Test
    void testNeedsRotation() throws Exception {
        keyManager.generateKeyPair("MLDSA", "usage-key");
        assertFalse(keyManager.needsRotation("usage-key", Duration.ofDays(365), 0));

        KeyMetadata metadata = keyManager.getKeyMetadata("usage-key");
        for (int i = 0; i < 100; i++) {
            metadata.updateLastUsed();
        }
        keyManager.updateKeyMetadata("usage-key", metadata);

        assertTrue(keyManager.needsRotation("usage-key", null, 50));
        assertFalse(keyManager.needsRotation("usage-key", null, 150));
        // an unknown key never needs rotation
        assertFalse(keyManager.needsRotation("nope", null, 1));
    }

    @Test
    void testExportAndImportPublicKey() throws Exception {
        KeyPair keyPair = keyManager.generateKeyPair("MLDSA", "export-key");

        byte[] pem = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.PEM);
        assertNotNull(pem);
        assertTrue(new String(pem).contains("-----BEGIN PUBLIC KEY-----"));

        KeyPair imported = keyManager.importKey(pem, KeyLifecycleManager.KeyFormat.PEM, "MLDSA");
        assertNotNull(imported);
        assertArrayEquals(keyPair.getPublic().getEncoded(), imported.getPublic().getEncoded());
    }

    @Test
    void testClearAndSize() throws Exception {
        assertEquals(0, keyManager.size());

        keyManager.generateKeyPair("MLDSA", "a");
        keyManager.generateKeyPair("MLDSA", "b");
        assertEquals(2, keyManager.size());

        keyManager.clear();
        assertEquals(0, keyManager.size());
        assertTrue(keyManager.listKeys().isEmpty());
        assertNull(keyManager.getKeyMetadata("a"));
    }

    @Test
    void testConcurrentKeyGenerationIsThreadSafe() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        keyManager.generateKeyPair("MLDSA", "key-" + index);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent key generation did not finish in time");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, failures.get(), "concurrent key generation should not fail");
        assertEquals(threads, keyManager.size());
    }
}
