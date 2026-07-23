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

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.component.pqc.PQCComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyRotationSchedulerTest {

    @TempDir
    Path tempDir;

    private FileBasedKeyLifecycleManager keyManager;
    private KeyRotationScheduler scheduler;

    @BeforeAll
    static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (scheduler != null && scheduler.isStarted()) {
            scheduler.stop();
        }
        if (keyManager != null) {
            for (KeyMetadata metadata : keyManager.listKeys()) {
                keyManager.deleteKey(metadata.getKeyId());
            }
        }
    }

    private FileBasedKeyLifecycleManager newManager() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());
        return keyManager;
    }

    /** Generates a Dilithium key and drives its recorded usage count to the given value. */
    private void generateKeyWithUsage(String keyId, long usage) throws Exception {
        keyManager.generateKeyPair("DILITHIUM", keyId, MLDSAParameterSpec.ml_dsa_44);
        KeyMetadata metadata = keyManager.getKeyMetadata(keyId);
        for (long i = 0; i < usage; i++) {
            metadata.updateLastUsed();
        }
        keyManager.updateKeyMetadata(keyId, metadata);
    }

    @Test
    void testRotatesKeyThatExceedsUsagePolicy() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 100);

        scheduler = new KeyRotationScheduler(keyManager).setMaxKeyUsage(50);
        int rotated = scheduler.checkAndRotate();

        assertEquals(1, rotated);
        assertEquals(1, scheduler.getChecksPerformed());
        assertEquals(1, scheduler.getRotationsPerformed());
        assertEquals(0, scheduler.getRotationFailures());
        assertNotNull(scheduler.getLastCheckAt());

        // The old key is deprecated and a single fresh ACTIVE replacement exists
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, keyManager.getKeyMetadata("app-key").getStatus());
        List<KeyMetadata> active = keyManager.listKeys().stream()
                .filter(m -> m.getStatus() == KeyMetadata.KeyStatus.ACTIVE)
                .toList();
        assertEquals(1, active.size());
        assertNotEquals("app-key", active.get(0).getKeyId());
        assertEquals("DILITHIUM", active.get(0).getAlgorithm());
    }

    @Test
    void testDoesNotRotateWhenPolicyNotBreached() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 10);

        scheduler = new KeyRotationScheduler(keyManager).setMaxKeyUsage(1000);
        int rotated = scheduler.checkAndRotate();

        assertEquals(0, rotated);
        assertEquals(0, scheduler.getRotationsPerformed());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, keyManager.getKeyMetadata("app-key").getStatus());
        assertEquals(1, keyManager.listKeys().size());
    }

    @Test
    void testKeyFilterExcludesNonActiveKeys() throws Exception {
        newManager();
        // Would breach the usage policy, but the key is not ACTIVE so the default filter skips it
        generateKeyWithUsage("app-key", 100);
        KeyMetadata metadata = keyManager.getKeyMetadata("app-key");
        metadata.setStatus(KeyMetadata.KeyStatus.DEPRECATED);
        keyManager.updateKeyMetadata("app-key", metadata);

        scheduler = new KeyRotationScheduler(keyManager).setMaxKeyUsage(50);
        int rotated = scheduler.checkAndRotate();

        assertEquals(0, rotated);
        assertEquals(0, scheduler.getRotationsPerformed());
        assertEquals(1, keyManager.listKeys().size());
    }

    @Test
    void testCustomKeyIdStrategy() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 100);

        scheduler = new KeyRotationScheduler(keyManager)
                .setMaxKeyUsage(50)
                .setKeyIdStrategy(m -> m.getKeyId() + "-v2");
        scheduler.checkAndRotate();

        KeyMetadata rotated = keyManager.getKeyMetadata("app-key-v2");
        assertNotNull(rotated);
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, rotated.getStatus());
    }

    @Test
    void testListenerInvokedOnRotation() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 100);

        AtomicReference<String> oldId = new AtomicReference<>();
        AtomicReference<String> newId = new AtomicReference<>();
        AtomicReference<KeyPair> newPair = new AtomicReference<>();
        scheduler = new KeyRotationScheduler(keyManager)
                .setMaxKeyUsage(50)
                .setListener(new KeyRotationScheduler.KeyRotationListener() {
                    @Override
                    public void onRotated(
                            String oldKeyId, String newKeyId, KeyMetadata previousMetadata, KeyPair newKeyPair) {
                        oldId.set(oldKeyId);
                        newId.set(newKeyId);
                        newPair.set(newKeyPair);
                    }
                });
        scheduler.checkAndRotate();

        assertEquals("app-key", oldId.get());
        assertNotNull(newId.get());
        assertNotEquals("app-key", newId.get());
        assertNotNull(newPair.get());
        assertNotNull(newPair.get().getPrivate());
    }

    @Test
    void testScheduledRotation() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 100);

        CountDownLatch rotated = new CountDownLatch(1);
        scheduler = new KeyRotationScheduler(keyManager)
                .setCheckInterval(Duration.ofMillis(50))
                .setMaxKeyUsage(50)
                .setListener(new KeyRotationScheduler.KeyRotationListener() {
                    @Override
                    public void onRotated(
                            String oldKeyId, String newKeyId, KeyMetadata previousMetadata, KeyPair newKeyPair) {
                        rotated.countDown();
                    }
                });

        scheduler.start();
        assertTrue(rotated.await(10, TimeUnit.SECONDS), "Scheduled rotation did not run in time");
        scheduler.stop();

        assertFalse(scheduler.isStarted());
        assertTrue(scheduler.getRotationsPerformed() >= 1);
        assertTrue(scheduler.getChecksPerformed() >= 1);
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, keyManager.getKeyMetadata("app-key").getStatus());
    }

    @Test
    void testStartRejectsNonPositiveInterval() throws Exception {
        newManager();
        scheduler = new KeyRotationScheduler(keyManager).setCheckInterval(Duration.ZERO);
        assertThrows(RuntimeException.class, () -> scheduler.start());
    }

    @Test
    void testScheduledRotationWithCamelContext() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 100);

        CountDownLatch rotated = new CountDownLatch(1);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            // With a CamelContext set, the scheduler obtains its executor from the ExecutorServiceManager
            scheduler = new KeyRotationScheduler(keyManager)
                    .setCheckInterval(Duration.ofMillis(50))
                    .setMaxKeyUsage(50)
                    .setListener(new KeyRotationScheduler.KeyRotationListener() {
                        @Override
                        public void onRotated(
                                String oldKeyId, String newKeyId, KeyMetadata previousMetadata, KeyPair newKeyPair) {
                            rotated.countDown();
                        }
                    });
            scheduler.setCamelContext(context);
            scheduler.start();

            assertTrue(rotated.await(10, TimeUnit.SECONDS), "Scheduled rotation did not run in time");
            assertSame(context, scheduler.getCamelContext());
            scheduler.stop();
        }

        assertTrue(scheduler.getRotationsPerformed() >= 1);
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, keyManager.getKeyMetadata("app-key").getStatus());
    }

    @Test
    void testComponentOptionsStartScheduler() throws Exception {
        newManager();
        generateKeyWithUsage("app-key", 100);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PQCComponent component = new PQCComponent();
            component.setCamelContext(context);
            component.getConfiguration().setKeyLifecycleManager(keyManager);
            component.setKeyRotationSchedulerEnabled(true);
            component.setKeyRotationCheckInterval(3_600_000L); // 1h - won't auto-fire during the test
            component.setKeyRotationMaxUsage(50);
            component.start();

            KeyRotationScheduler s = component.getKeyRotationScheduler();
            assertNotNull(s);
            assertSame(context, s.getCamelContext());
            assertTrue(s.isStarted());

            // Deterministic manual trigger: the component wired the manager and policy correctly
            assertEquals(1, s.checkAndRotate());
            assertEquals(KeyMetadata.KeyStatus.DEPRECATED, keyManager.getKeyMetadata("app-key").getStatus());

            component.stop();
            assertFalse(s.isStarted());
            assertNull(component.getKeyRotationScheduler());
        }
    }
}
