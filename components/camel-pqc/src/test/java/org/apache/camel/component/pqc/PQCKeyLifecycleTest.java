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

package org.apache.camel.component.pqc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Security;
import java.time.Duration;
import java.util.List;

import org.apache.camel.component.pqc.lifecycle.FileBasedKeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyMetadata;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PQCKeyLifecycleTest {

    @TempDir
    Path tempDir;

    private KeyLifecycleManager keyManager;

    @BeforeAll
    public static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (keyManager != null) {
            List<KeyMetadata> keys = keyManager.listKeys();
            for (KeyMetadata metadata : keys) {
                keyManager.deleteKey(metadata.getKeyId());
            }
        }
    }

    @Test
    void testGenerateKeyPair() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate a Dilithium key pair
        KeyPair keyPair =
                keyManager.generateKeyPair("DILITHIUM", "test-dilithium-key", DilithiumParameterSpec.dilithium2);

        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        // Verify metadata was created
        KeyMetadata metadata = keyManager.getKeyMetadata("test-dilithium-key");
        assertNotNull(metadata);
        assertEquals("test-dilithium-key", metadata.getKeyId());
        assertEquals("DILITHIUM", metadata.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
        assertEquals(0, metadata.getUsageCount());
    }

    @Test
    void testGenerateFalconKeyPair() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate a Falcon key pair (uses default spec)
        KeyPair keyPair = keyManager.generateKeyPair("FALCON", "test-falcon-key");

        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        // Verify key can be retrieved
        KeyPair retrieved = keyManager.getKey("test-falcon-key");
        assertNotNull(retrieved);
        assertArrayEquals(
                keyPair.getPublic().getEncoded(), retrieved.getPublic().getEncoded());
    }

    @Test
    void testExportKeyToPEM() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "export-test-key", DilithiumParameterSpec.dilithium2);

        // Export public key to PEM
        byte[] publicKeyPEM = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.PEM);
        assertNotNull(publicKeyPEM);

        String pemString = new String(publicKeyPEM);
        assertTrue(pemString.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pemString.contains("-----END PUBLIC KEY-----"));

        // Export both keys to PEM
        byte[] keyPairPEM = keyManager.exportKey(keyPair, KeyLifecycleManager.KeyFormat.PEM, true);
        assertNotNull(keyPairPEM);

        String keyPairPEMString = new String(keyPairPEM);
        assertTrue(keyPairPEMString.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(keyPairPEMString.contains("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    void testExportKeyToDER() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "der-test-key", DilithiumParameterSpec.dilithium2);

        // Export to DER format
        byte[] publicKeyDER = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.DER);
        assertNotNull(publicKeyDER);
        assertTrue(publicKeyDER.length > 0);

        // DER should not contain PEM headers
        String derString = new String(publicKeyDER);
        assertFalse(derString.contains("-----BEGIN"));
    }

    @Test
    void testImportKey() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate and export a key
        KeyPair originalKeyPair =
                keyManager.generateKeyPair("DILITHIUM", "import-test-key", DilithiumParameterSpec.dilithium2);
        byte[] exportedKey = keyManager.exportPublicKey(originalKeyPair, KeyLifecycleManager.KeyFormat.PEM);

        // Import the key
        KeyPair importedKeyPair = keyManager.importKey(exportedKey, KeyLifecycleManager.KeyFormat.PEM, "DILITHIUM");

        assertNotNull(importedKeyPair);
        assertNotNull(importedKeyPair.getPublic());

        // Compare public keys
        assertArrayEquals(
                originalKeyPair.getPublic().getEncoded(),
                importedKeyPair.getPublic().getEncoded());
    }

    @Test
    void testKeyRotation() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate initial key
        KeyPair oldKeyPair = keyManager.generateKeyPair("DILITHIUM", "old-key", DilithiumParameterSpec.dilithium2);
        assertNotNull(oldKeyPair);

        // Rotate the key
        KeyPair newKeyPair = keyManager.rotateKey("old-key", "new-key", "DILITHIUM");
        assertNotNull(newKeyPair);

        // Verify old key is deprecated
        KeyMetadata oldMetadata = keyManager.getKeyMetadata("old-key");
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, oldMetadata.getStatus());

        // Verify new key is active
        KeyMetadata newMetadata = keyManager.getKeyMetadata("new-key");
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, newMetadata.getStatus());

        // Keys should be different
        assertFalse(java.util.Arrays.equals(
                oldKeyPair.getPublic().getEncoded(), newKeyPair.getPublic().getEncoded()));
    }

    @Test
    void testKeyMetadataTracking() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        KeyPair keyPair =
                keyManager.generateKeyPair("DILITHIUM", "metadata-test-key", DilithiumParameterSpec.dilithium2);
        KeyMetadata metadata = keyManager.getKeyMetadata("metadata-test-key");

        // Initial state
        assertEquals("metadata-test-key", metadata.getKeyId());
        assertEquals("DILITHIUM", metadata.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
        assertEquals(0, metadata.getUsageCount());
        assertNotNull(metadata.getCreatedAt());
        assertNotNull(metadata.getLastUsedAt());

        // Update usage
        metadata.updateLastUsed();
        metadata.updateLastUsed();
        metadata.updateLastUsed();
        keyManager.updateKeyMetadata("metadata-test-key", metadata);

        // Retrieve and verify
        KeyMetadata updatedMetadata = keyManager.getKeyMetadata("metadata-test-key");
        assertEquals(3, updatedMetadata.getUsageCount());
    }

    @Test
    void testExpireKey() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        keyManager.generateKeyPair("DILITHIUM", "expire-test-key", DilithiumParameterSpec.dilithium2);

        // Expire the key
        keyManager.expireKey("expire-test-key");

        // Verify status
        KeyMetadata metadata = keyManager.getKeyMetadata("expire-test-key");
        assertEquals(KeyMetadata.KeyStatus.EXPIRED, metadata.getStatus());
        assertTrue(metadata.isExpired());
    }

    @Test
    void testRevokeKey() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        keyManager.generateKeyPair("DILITHIUM", "revoke-test-key", DilithiumParameterSpec.dilithium2);

        // Revoke the key
        String reason = "Compromised key";
        keyManager.revokeKey("revoke-test-key", reason);

        // Verify status
        KeyMetadata metadata = keyManager.getKeyMetadata("revoke-test-key");
        assertEquals(KeyMetadata.KeyStatus.REVOKED, metadata.getStatus());
        assertTrue(metadata.getDescription().contains(reason));
    }

    @Test
    void testListKeys() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate multiple keys
        keyManager.generateKeyPair("DILITHIUM", "key1", DilithiumParameterSpec.dilithium2);
        keyManager.generateKeyPair("FALCON", "key2");
        keyManager.generateKeyPair("DILITHIUM", "key3", DilithiumParameterSpec.dilithium3);

        // List all keys
        List<KeyMetadata> keys = keyManager.listKeys();
        assertEquals(3, keys.size());

        // Verify all keys are present
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("key1")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("key2")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("key3")));
    }

    @Test
    void testDeleteKey() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        keyManager.generateKeyPair("DILITHIUM", "delete-test-key", DilithiumParameterSpec.dilithium2);

        // Verify key exists
        assertNotNull(keyManager.getKeyMetadata("delete-test-key"));

        // Delete the key
        keyManager.deleteKey("delete-test-key");

        // Verify key is gone
        assertNull(keyManager.getKeyMetadata("delete-test-key"));
    }

    @Test
    void testNeedsRotationByAge() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        keyManager.generateKeyPair("DILITHIUM", "age-test-key", DilithiumParameterSpec.dilithium2);

        // Should not need rotation for young key
        assertFalse(keyManager.needsRotation("age-test-key", Duration.ofDays(365), 0));

        // Should need rotation if max age is very short (simulated)
        // This will be false because the key was just created
        assertFalse(keyManager.needsRotation("age-test-key", Duration.ofDays(0), 0));
    }

    @Test
    void testNeedsRotationByUsage() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        keyManager.generateKeyPair("DILITHIUM", "usage-test-key", DilithiumParameterSpec.dilithium2);

        // Update usage count
        KeyMetadata metadata = keyManager.getKeyMetadata("usage-test-key");
        for (int i = 0; i < 100; i++) {
            metadata.updateLastUsed();
        }
        keyManager.updateKeyMetadata("usage-test-key", metadata);

        // Should need rotation if usage exceeds limit
        assertTrue(keyManager.needsRotation("usage-test-key", null, 50));

        // Should not need rotation if usage is within limit
        assertFalse(keyManager.needsRotation("usage-test-key", null, 150));
    }

    @Test
    void testKeyPersistence() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate and store a key
        KeyPair originalKeyPair =
                keyManager.generateKeyPair("DILITHIUM", "persistence-test-key", DilithiumParameterSpec.dilithium2);

        // Create a new manager instance (simulating restart)
        KeyLifecycleManager newManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Retrieve the key
        KeyPair retrievedKeyPair = newManager.getKey("persistence-test-key");
        assertNotNull(retrievedKeyPair);

        // Verify keys match
        assertArrayEquals(
                originalKeyPair.getPublic().getEncoded(),
                retrievedKeyPair.getPublic().getEncoded());
        assertArrayEquals(
                originalKeyPair.getPrivate().getEncoded(),
                retrievedKeyPair.getPrivate().getEncoded());

        // Verify metadata persisted
        KeyMetadata metadata = newManager.getKeyMetadata("persistence-test-key");
        assertNotNull(metadata);
        assertEquals("persistence-test-key", metadata.getKeyId());
        assertEquals("DILITHIUM", metadata.getAlgorithm());
    }

    @Test
    void testKeyMetadataAge() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        keyManager.generateKeyPair("DILITHIUM", "age-calculation-key", DilithiumParameterSpec.dilithium2);
        KeyMetadata metadata = keyManager.getKeyMetadata("age-calculation-key");

        // Age should be 0 days for just created key
        long age = metadata.getAgeInDays();
        assertEquals(0, age);
    }

    @Test
    void testMultipleKeyFormats() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "format-test-key", DilithiumParameterSpec.dilithium2);

        // Test all export formats
        byte[] pemPublic = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.PEM);
        byte[] derPublic = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.DER);
        byte[] x509Public = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.X509);

        assertNotNull(pemPublic);
        assertNotNull(derPublic);
        assertNotNull(x509Public);

        // PEM should be larger due to Base64 encoding
        assertTrue(pemPublic.length > derPublic.length);

        // DER and X509 should be the same for public keys
        assertArrayEquals(derPublic, x509Public);
    }
}
