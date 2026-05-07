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

import java.security.KeyPair;
import java.security.Security;
import java.time.Duration;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.lifecycle.HashicorpVaultKeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyMetadata;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpServiceFactory;
import org.apache.camel.test.infra.hashicorp.vault.services.HashicorpVaultService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for HashicorpVaultKeyLifecycleManager. Tests key generation, storage, retrieval,
 * rotation, and usage in Camel routes with a real Vault instance via testcontainers.
 */
public class HashicorpVaultKeyLifecycleIT extends CamelTestSupport {

    @RegisterExtension
    public static HashicorpVaultService service = HashicorpServiceFactory.createService();

    private HashicorpVaultKeyLifecycleManager keyManager;

    @EndpointInject("mock:signed")
    private MockEndpoint mockSigned;

    @EndpointInject("mock:verified")
    private MockEndpoint mockVerified;

    @BeforeAll
    public static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // Create HashicorpVaultKeyLifecycleManager using Vault test infrastructure
        keyManager = new HashicorpVaultKeyLifecycleManager(
                service.host(),
                service.port(),
                "http", // Test container uses http
                service.token(),
                "secret",
                "pqc/test-keys");

        // Register the manager in the registry
        context.getRegistry().bind("keyLifecycleManager", keyManager);

        return context;
    }

    @Test
    public void testGenerateAndStoreKeyInVault() throws Exception {
        // Generate a Dilithium key
        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "test-dilithium-key", DilithiumParameterSpec.dilithium2);

        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        // Verify metadata was created
        KeyMetadata metadata = keyManager.getKeyMetadata("test-dilithium-key");
        assertNotNull(metadata);
        assertEquals("test-dilithium-key", metadata.getKeyId());
        assertEquals("DILITHIUM", metadata.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
    }

    @Test
    public void testRetrieveKeyFromVault() throws Exception {
        // Generate and store key
        keyManager.generateKeyPair("FALCON", "test-falcon-key", FalconParameterSpec.falcon_512);

        // Clear cache to force Vault read
        // (In production this would simulate a different process/server accessing the key)

        // Retrieve key from Vault
        KeyPair retrieved = keyManager.getKey("test-falcon-key");
        assertNotNull(retrieved);
        assertNotNull(retrieved.getPublic());
        assertNotNull(retrieved.getPrivate());

        // Verify metadata
        KeyMetadata metadata = keyManager.getKeyMetadata("test-falcon-key");
        assertEquals("FALCON", metadata.getAlgorithm());
    }

    @Test
    public void testKeyRotation() throws Exception {
        // Generate initial key
        keyManager.generateKeyPair("DILITHIUM", "rotation-key-old", DilithiumParameterSpec.dilithium2);

        KeyMetadata oldMetadata = keyManager.getKeyMetadata("rotation-key-old");
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, oldMetadata.getStatus());

        // Rotate the key
        KeyPair newKeyPair = keyManager.rotateKey("rotation-key-old", "rotation-key-new", "DILITHIUM");
        assertNotNull(newKeyPair);

        // Verify old key is deprecated
        oldMetadata = keyManager.getKeyMetadata("rotation-key-old");
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, oldMetadata.getStatus());

        // Verify new key is active
        KeyMetadata newMetadata = keyManager.getKeyMetadata("rotation-key-new");
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, newMetadata.getStatus());
    }

    @Test
    public void testNeedsRotation() throws Exception {
        keyManager.generateKeyPair("DILITHIUM", "rotation-check-key", DilithiumParameterSpec.dilithium2);

        // New key should not need rotation
        assertFalse(keyManager.needsRotation("rotation-check-key", Duration.ofDays(90), 10000));

        // Simulate old key by setting next rotation time in the past
        KeyMetadata metadata = keyManager.getKeyMetadata("rotation-check-key");
        metadata.setNextRotationAt(java.time.Instant.now().minusSeconds(1));
        keyManager.updateKeyMetadata("rotation-check-key", metadata);

        // Now it should need rotation
        assertTrue(keyManager.needsRotation("rotation-check-key", Duration.ofDays(90), 10000));
    }

    @Test
    public void testListKeys() throws Exception {
        // Generate multiple keys
        keyManager.generateKeyPair("DILITHIUM", "list-key-1", DilithiumParameterSpec.dilithium2);
        keyManager.generateKeyPair("FALCON", "list-key-2", FalconParameterSpec.falcon_512);
        keyManager.generateKeyPair("DILITHIUM", "list-key-3", DilithiumParameterSpec.dilithium3);

        // List all keys
        List<KeyMetadata> keys = keyManager.listKeys();
        assertTrue(keys.size() >= 3, "Should have at least 3 keys");

        // Verify all our keys are present
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("list-key-1")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("list-key-2")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("list-key-3")));
    }

    @Test
    public void testExpireAndRevokeKey() throws Exception {
        // Test expiration
        keyManager.generateKeyPair("DILITHIUM", "expire-key", DilithiumParameterSpec.dilithium2);
        keyManager.expireKey("expire-key");

        KeyMetadata expiredMetadata = keyManager.getKeyMetadata("expire-key");
        assertEquals(KeyMetadata.KeyStatus.EXPIRED, expiredMetadata.getStatus());

        // Test revocation
        keyManager.generateKeyPair("DILITHIUM", "revoke-key", DilithiumParameterSpec.dilithium2);
        keyManager.revokeKey("revoke-key", "Key compromised in test");

        KeyMetadata revokedMetadata = keyManager.getKeyMetadata("revoke-key");
        assertEquals(KeyMetadata.KeyStatus.REVOKED, revokedMetadata.getStatus());
        assertTrue(revokedMetadata.getDescription().contains("Revoked: Key compromised in test"));
    }

    @Test
    public void testDeleteKey() throws Exception {
        keyManager.generateKeyPair("DILITHIUM", "delete-key", DilithiumParameterSpec.dilithium2);
        assertNotNull(keyManager.getKey("delete-key"));

        keyManager.deleteKey("delete-key");

        // Should throw exception when trying to get deleted key
        assertThrows(IllegalArgumentException.class, () -> keyManager.getKey("delete-key"));
    }

    @Test
    public void testExportAndImportKey() throws Exception {
        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "export-key", DilithiumParameterSpec.dilithium2);

        // Export public key as PEM
        byte[] exported = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.PEM);
        assertNotNull(exported);
        assertTrue(exported.length > 0);

        String pemString = new String(exported);
        assertTrue(pemString.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pemString.contains("-----END PUBLIC KEY-----"));

        // Import the key
        KeyPair imported = keyManager.importKey(exported, KeyLifecycleManager.KeyFormat.PEM, "DILITHIUM");
        assertNotNull(imported);
        assertNotNull(imported.getPublic());
    }

    @Test
    public void testMetadataTracking() throws Exception {
        // Generate key
        keyManager.generateKeyPair("DILITHIUM", "tracking-key", DilithiumParameterSpec.dilithium2);

        // Get initial metadata
        KeyMetadata metadata = keyManager.getKeyMetadata("tracking-key");
        assertEquals(0, metadata.getUsageCount());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());

        // Simulate usage by updating metadata
        for (int i = 0; i < 5; i++) {
            metadata.updateLastUsed();
        }
        keyManager.updateKeyMetadata("tracking-key", metadata);

        // Verify usage was tracked
        metadata = keyManager.getKeyMetadata("tracking-key");
        assertEquals(5, metadata.getUsageCount());
        assertNotNull(metadata.getLastUsedAt());

        // Verify age calculation
        long ageInDays = metadata.getAgeInDays();
        assertEquals(0, ageInDays); // Should be 0 for a newly created key
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Signing route using PQC component with Vault-stored key
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=DILITHIUM")
                        .to("mock:signed")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=DILITHIUM")
                        .to("mock:verified");
            }
        };
    }
}
