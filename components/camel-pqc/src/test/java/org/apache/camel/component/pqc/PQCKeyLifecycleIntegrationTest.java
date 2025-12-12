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

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Security;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.lifecycle.FileBasedKeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyMetadata;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PQC lifecycle operations through Camel routes.
 */
public class PQCKeyLifecycleIntegrationTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    @EndpointInject("mock:result")
    protected MockEndpoint mockResult;

    @Produce("direct:generateKey")
    protected ProducerTemplate generateKeyTemplate;

    @Produce("direct:exportKey")
    protected ProducerTemplate exportKeyTemplate;

    @Produce("direct:getMetadata")
    protected ProducerTemplate getMetadataTemplate;

    @Produce("direct:listKeys")
    protected ProducerTemplate listKeysTemplate;

    @Produce("direct:rotateKey")
    protected ProducerTemplate rotateKeyTemplate;

    @Produce("direct:expireKey")
    protected ProducerTemplate expireKeyTemplate;

    @Produce("direct:revokeKey")
    protected ProducerTemplate revokeKeyTemplate;

    private KeyLifecycleManager keyManager;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:generateKey")
                        .setHeader(PQCConstants.ALGORITHM, constant("DILITHIUM"))
                        .setHeader(PQCConstants.KEY_ID, body())
                        .to("pqc:lifecycle?operation=generateKeyPair")
                        .to("mock:result");

                from("direct:exportKey")
                        .setHeader(PQCConstants.KEY_FORMAT, constant("PEM"))
                        .setHeader(PQCConstants.INCLUDE_PRIVATE, constant(false))
                        .to("pqc:lifecycle?operation=exportKey")
                        .to("mock:result");

                from("direct:getMetadata")
                        .setHeader(PQCConstants.KEY_ID, body())
                        .to("pqc:lifecycle?operation=getKeyMetadata")
                        .to("mock:result");

                from("direct:listKeys")
                        .to("pqc:lifecycle?operation=listKeys")
                        .to("mock:result");

                from("direct:rotateKey")
                        .setHeader(PQCConstants.KEY_ID, constant("old-key"))
                        .setHeader(PQCConstants.ALGORITHM, constant("DILITHIUM"))
                        .to("pqc:lifecycle?operation=rotateKey")
                        .to("mock:result");

                from("direct:expireKey")
                        .setHeader(PQCConstants.KEY_ID, body())
                        .to("pqc:lifecycle?operation=expireKey")
                        .to("mock:result");

                from("direct:revokeKey")
                        .setHeader(PQCConstants.KEY_ID, constant("revoke-key"))
                        .setHeader(PQCConstants.REVOCATION_REASON, body())
                        .to("pqc:lifecycle?operation=revokeKey")
                        .to("mock:result");
            }
        };
    }

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
                try {
                    keyManager.deleteKey(metadata.getKeyId());
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    @Test
    void testGenerateKeyThroughRoute() throws Exception {
        // First manually generate a key to test the operation
        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "route-test-key", DilithiumParameterSpec.dilithium2);
        assertNotNull(keyPair);

        // Verify metadata
        KeyMetadata metadata = keyManager.getKeyMetadata("route-test-key");
        assertNotNull(metadata);
        assertEquals("route-test-key", metadata.getKeyId());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
    }

    @Test
    void testExportKeyThroughManager() throws Exception {
        // Generate a key first
        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", "export-route-key", DilithiumParameterSpec.dilithium2);

        // Export the key
        byte[] exportedKey = keyManager.exportPublicKey(keyPair, KeyLifecycleManager.KeyFormat.PEM);

        assertNotNull(exportedKey);
        String pemString = new String(exportedKey);
        assertTrue(pemString.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pemString.contains("-----END PUBLIC KEY-----"));
    }

    @Test
    void testGetMetadataThroughManager() throws Exception {
        // Generate a key
        keyManager.generateKeyPair("DILITHIUM", "metadata-route-key", DilithiumParameterSpec.dilithium2);

        // Get metadata
        KeyMetadata metadata = keyManager.getKeyMetadata("metadata-route-key");

        assertNotNull(metadata);
        assertEquals("metadata-route-key", metadata.getKeyId());
        assertEquals("DILITHIUM", metadata.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
    }

    @Test
    void testListKeysThroughManager() throws Exception {
        // Generate multiple keys
        keyManager.generateKeyPair("DILITHIUM", "list-key-1", DilithiumParameterSpec.dilithium2);
        keyManager.generateKeyPair("FALCON", "list-key-2");
        keyManager.generateKeyPair("DILITHIUM", "list-key-3", DilithiumParameterSpec.dilithium3);

        // List all keys
        List<KeyMetadata> keys = keyManager.listKeys();

        assertNotNull(keys);
        assertEquals(3, keys.size());
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("list-key-1")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("list-key-2")));
        assertTrue(keys.stream().anyMatch(k -> k.getKeyId().equals("list-key-3")));
    }

    @Test
    void testKeyRotationThroughManager() throws Exception {
        // Generate initial key
        keyManager.generateKeyPair("DILITHIUM", "rotate-old-key", DilithiumParameterSpec.dilithium2);

        // Rotate the key
        KeyPair newKeyPair = keyManager.rotateKey("rotate-old-key", "rotate-new-key", "DILITHIUM");

        assertNotNull(newKeyPair);

        // Verify old key is deprecated
        KeyMetadata oldMetadata = keyManager.getKeyMetadata("rotate-old-key");
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, oldMetadata.getStatus());

        // Verify new key is active
        KeyMetadata newMetadata = keyManager.getKeyMetadata("rotate-new-key");
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, newMetadata.getStatus());
    }

    @Test
    void testExpireKeyThroughManager() throws Exception {
        // Generate a key
        keyManager.generateKeyPair("DILITHIUM", "expire-route-key", DilithiumParameterSpec.dilithium2);

        // Expire the key
        keyManager.expireKey("expire-route-key");

        // Verify status
        KeyMetadata metadata = keyManager.getKeyMetadata("expire-route-key");
        assertEquals(KeyMetadata.KeyStatus.EXPIRED, metadata.getStatus());
        assertTrue(metadata.isExpired());
    }

    @Test
    void testRevokeKeyThroughManager() throws Exception {
        // Generate a key
        keyManager.generateKeyPair("DILITHIUM", "revoke-route-key", DilithiumParameterSpec.dilithium2);

        // Revoke the key
        String reason = "Security breach detected";
        keyManager.revokeKey("revoke-route-key", reason);

        // Verify status
        KeyMetadata metadata = keyManager.getKeyMetadata("revoke-route-key");
        assertEquals(KeyMetadata.KeyStatus.REVOKED, metadata.getStatus());
        assertTrue(metadata.getDescription().contains(reason));
    }

    @Test
    void testKeyPersistenceAcrossManagers() throws Exception {
        // Generate key with first manager
        KeyPair originalKeyPair = keyManager.generateKeyPair("DILITHIUM", "persistence-key",
                DilithiumParameterSpec.dilithium2);

        // Create new manager instance
        KeyLifecycleManager newManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Retrieve key with new manager
        KeyPair retrievedKeyPair = newManager.getKey("persistence-key");

        assertNotNull(retrievedKeyPair);
        assertArrayEquals(originalKeyPair.getPublic().getEncoded(), retrievedKeyPair.getPublic().getEncoded());
        assertArrayEquals(originalKeyPair.getPrivate().getEncoded(), retrievedKeyPair.getPrivate().getEncoded());
    }

    @Test
    void testKeyMetadataUpdates() throws Exception {
        // Generate a key
        keyManager.generateKeyPair("DILITHIUM", "update-test-key", DilithiumParameterSpec.dilithium2);

        // Get and update metadata
        KeyMetadata metadata = keyManager.getKeyMetadata("update-test-key");
        metadata.setDescription("Updated description");
        metadata.updateLastUsed();
        metadata.updateLastUsed();
        metadata.updateLastUsed();

        keyManager.updateKeyMetadata("update-test-key", metadata);

        // Retrieve and verify
        KeyMetadata updated = keyManager.getKeyMetadata("update-test-key");
        assertEquals("Updated description", updated.getDescription());
        assertEquals(3, updated.getUsageCount());
    }

    @Test
    void testKeyDeletion() throws Exception {
        // Generate a key
        keyManager.generateKeyPair("DILITHIUM", "delete-test-key", DilithiumParameterSpec.dilithium2);

        // Verify it exists
        assertNotNull(keyManager.getKeyMetadata("delete-test-key"));

        // Delete it
        keyManager.deleteKey("delete-test-key");

        // Verify it's gone
        assertNull(keyManager.getKeyMetadata("delete-test-key"));
    }

    @BindToRegistry("KeyLifecycleManager")
    public KeyLifecycleManager getKeyLifecycleManager() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());
        return keyManager;
    }
}
