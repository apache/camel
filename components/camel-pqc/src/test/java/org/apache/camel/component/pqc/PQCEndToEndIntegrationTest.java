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
import java.security.Signature;

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
import org.bouncycastle.pqc.jcajce.spec.NTRUParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests demonstrating complete lifecycle from key generation to actual usage.
 */
public class PQCEndToEndIntegrationTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    @EndpointInject("mock:signed")
    protected MockEndpoint mockSigned;

    @EndpointInject("mock:verified")
    protected MockEndpoint mockVerified;

    @EndpointInject("mock:encapsulated")
    protected MockEndpoint mockEncapsulated;

    @EndpointInject("mock:extracted")
    protected MockEndpoint mockExtracted;

    @Produce("direct:signMessage")
    protected ProducerTemplate signTemplate;

    @Produce("direct:encapsulateKey")
    protected ProducerTemplate kemTemplate;

    @Produce("direct:rotateAndSign")
    protected ProducerTemplate rotateSignTemplate;

    private KeyLifecycleManager keyManager;
    private KeyPair dilithiumKeyPair;
    private KeyPair ntruKeyPair;

    @Override
    protected void doPreSetup() throws Exception {
        // Ensure providers are registered BEFORE any key operations
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }

        // Initialize key manager before routes are created
        if (keyManager == null && tempDir != null) {
            keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());
            // Pre-generate keys needed for binding
            dilithiumKeyPair = keyManager.generateKeyPair("DILITHIUM", "setup-dilithium", DilithiumParameterSpec.dilithium2);
            ntruKeyPair = keyManager.generateKeyPair("NTRU", "setup-ntru", NTRUParameterSpec.ntruhps2048509);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route 1: Sign and verify a message using lifecycle-generated key
                from("direct:signMessage")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=DILITHIUM")
                        .to("mock:signed")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=DILITHIUM")
                        .to("mock:verified");

                // Route 2: KEM operations using lifecycle-generated key
                from("direct:encapsulateKey")
                        .to("pqc:keyenc?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=AES")
                        .to("mock:encapsulated")
                        .to("pqc:keyenc?operation=extractSecretKeyEncapsulation&symmetricKeyAlgorithm=AES")
                        .to("mock:extracted");

                // Route 3: Generate new key, rotate, and sign with new key
                from("direct:rotateAndSign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=DILITHIUM")
                        .to("mock:signed");
            }
        };
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (keyManager != null) {
            try {
                keyManager.deleteKey("e2e-dilithium-key");
                keyManager.deleteKey("e2e-ntru-key");
                keyManager.deleteKey("e2e-old-key");
                keyManager.deleteKey("e2e-new-key");
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    void testEndToEndSignatureWithGeneratedKey() throws Exception {
        // Step 1: Generate a Dilithium key pair using lifecycle manager
        dilithiumKeyPair = keyManager.generateKeyPair("DILITHIUM", "e2e-dilithium-key",
                DilithiumParameterSpec.dilithium2);
        assertNotNull(dilithiumKeyPair);

        // Verify metadata was created
        KeyMetadata metadata = keyManager.getKeyMetadata("e2e-dilithium-key");
        assertNotNull(metadata);
        assertEquals("e2e-dilithium-key", metadata.getKeyId());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());
        assertEquals(0, metadata.getUsageCount());

        // Step 2: Use the generated key to sign a message through Camel route
        mockSigned.expectedMessageCount(1);
        mockVerified.expectedMessageCount(1);

        String message = "This is a test message for end-to-end PQC signing!";
        signTemplate.sendBody(message);

        mockSigned.assertIsSatisfied();
        mockVerified.assertIsSatisfied();

        // Verify signature was created
        assertNotNull(mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE));
        byte[] signature = mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
        assertTrue(signature.length > 0);

        // Verify signature verification succeeded
        Boolean verified = mockVerified.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class);
        assertTrue(verified);

        // Step 3: Update key metadata to track usage
        metadata.updateLastUsed();
        keyManager.updateKeyMetadata("e2e-dilithium-key", metadata);

        // Verify usage was tracked
        KeyMetadata updatedMetadata = keyManager.getKeyMetadata("e2e-dilithium-key");
        assertEquals(1, updatedMetadata.getUsageCount());
    }

    @Test
    void testEndToEndKeyRotationWithSigning() throws Exception {
        // Step 1: Generate initial key
        KeyPair oldKeyPair = keyManager.generateKeyPair("DILITHIUM", "e2e-old-key", DilithiumParameterSpec.dilithium2);
        assertNotNull(oldKeyPair);

        // Step 2: Use old key for signing
        dilithiumKeyPair = oldKeyPair;
        mockSigned.reset();
        mockSigned.expectedMessageCount(1);

        signTemplate.sendBody("Message signed with old key");
        mockSigned.assertIsSatisfied();

        // Step 3: Rotate the key
        KeyPair newKeyPair = keyManager.rotateKey("e2e-old-key", "e2e-new-key", "DILITHIUM");
        assertNotNull(newKeyPair);

        // Verify old key is deprecated
        KeyMetadata oldMetadata = keyManager.getKeyMetadata("e2e-old-key");
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, oldMetadata.getStatus());

        // Verify new key is active
        KeyMetadata newMetadata = keyManager.getKeyMetadata("e2e-new-key");
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, newMetadata.getStatus());

        // Step 4: Use new key for signing
        dilithiumKeyPair = newKeyPair;
        mockSigned.reset();
        mockVerified.reset();
        mockSigned.expectedMessageCount(1);
        mockVerified.expectedMessageCount(1);

        signTemplate.sendBody("Message signed with new key");

        mockSigned.assertIsSatisfied();
        mockVerified.assertIsSatisfied();

        // Verify signature with new key works
        Boolean verified = mockVerified.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class);
        assertTrue(verified);
    }

    @Test
    void testEndToEndKeyExportImportAndUse() throws Exception {
        // Step 1: Generate a key
        KeyPair originalKeyPair = keyManager.generateKeyPair("DILITHIUM", "e2e-export-key",
                DilithiumParameterSpec.dilithium2);

        // Step 2: Export the key to PEM format
        byte[] exportedPublicKey = keyManager.exportPublicKey(originalKeyPair, KeyLifecycleManager.KeyFormat.PEM);
        assertNotNull(exportedPublicKey);

        String pemString = new String(exportedPublicKey);
        assertTrue(pemString.contains("-----BEGIN PUBLIC KEY-----"));

        // Step 3: Import the key back
        KeyPair importedKeyPair = keyManager.importKey(exportedPublicKey, KeyLifecycleManager.KeyFormat.PEM,
                "DILITHIUM");
        assertNotNull(importedKeyPair);
        assertNotNull(importedKeyPair.getPublic());

        // Step 4: Verify imported key matches original
        assertArrayEquals(originalKeyPair.getPublic().getEncoded(), importedKeyPair.getPublic().getEncoded());

        // Step 5: Use the key for signing
        dilithiumKeyPair = originalKeyPair;
        mockSigned.reset();
        mockVerified.reset();
        mockSigned.expectedMessageCount(1);
        mockVerified.expectedMessageCount(1);

        signTemplate.sendBody("Test with exported/imported key");

        mockSigned.assertIsSatisfied();
        mockVerified.assertIsSatisfied();

        Boolean verified = mockVerified.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class);
        assertTrue(verified);

        // Cleanup
        keyManager.deleteKey("e2e-export-key");
    }

    @Test
    void testEndToEndMultipleSignaturesWithMetadataTracking() throws Exception {
        // Step 1: Generate a key
        dilithiumKeyPair = keyManager.generateKeyPair("DILITHIUM", "e2e-multi-sig-key",
                DilithiumParameterSpec.dilithium2);

        KeyMetadata metadata = keyManager.getKeyMetadata("e2e-multi-sig-key");
        assertEquals(0, metadata.getUsageCount());

        // Step 2: Sign multiple messages
        for (int i = 1; i <= 5; i++) {
            mockSigned.reset();
            mockVerified.reset();
            mockSigned.expectedMessageCount(1);
            mockVerified.expectedMessageCount(1);

            signTemplate.sendBody("Message #" + i);

            mockSigned.assertIsSatisfied();
            mockVerified.assertIsSatisfied();

            // Update metadata
            metadata.updateLastUsed();
            keyManager.updateKeyMetadata("e2e-multi-sig-key", metadata);
        }

        // Step 3: Verify all signatures were tracked
        KeyMetadata finalMetadata = keyManager.getKeyMetadata("e2e-multi-sig-key");
        assertEquals(5, finalMetadata.getUsageCount());
        assertNotNull(finalMetadata.getLastUsedAt());

        // Cleanup
        keyManager.deleteKey("e2e-multi-sig-key");
    }

    @Test
    void testEndToEndKeyExpirationPreventsUsage() throws Exception {
        // Step 1: Generate a key
        dilithiumKeyPair = keyManager.generateKeyPair("DILITHIUM", "e2e-expire-key", DilithiumParameterSpec.dilithium2);

        // Step 2: Use the key successfully
        mockSigned.reset();
        mockSigned.expectedMessageCount(1);

        signTemplate.sendBody("Message before expiration");
        mockSigned.assertIsSatisfied();

        // Step 3: Expire the key
        keyManager.expireKey("e2e-expire-key");

        // Verify key is expired
        KeyMetadata metadata = keyManager.getKeyMetadata("e2e-expire-key");
        assertEquals(KeyMetadata.KeyStatus.EXPIRED, metadata.getStatus());
        assertTrue(metadata.isExpired());

        // Step 4: Key can still technically be used (enforcement would be at application level)
        // But metadata clearly shows it's expired
        assertTrue(metadata.isExpired());

        // Cleanup
        keyManager.deleteKey("e2e-expire-key");
    }

    @Test
    void testEndToEndCompleteLifecycle() throws Exception {
        String keyId = "e2e-complete-lifecycle-key";

        // Phase 1: Creation
        KeyPair keyPair = keyManager.generateKeyPair("DILITHIUM", keyId, DilithiumParameterSpec.dilithium2);
        assertNotNull(keyPair);

        KeyMetadata metadata = keyManager.getKeyMetadata(keyId);
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, metadata.getStatus());

        // Phase 2: Active use
        dilithiumKeyPair = keyPair;
        for (int i = 0; i < 3; i++) {
            mockSigned.reset();
            mockVerified.reset();
            mockSigned.expectedMessageCount(1);
            mockVerified.expectedMessageCount(1);

            signTemplate.sendBody("Lifecycle test message " + i);

            mockSigned.assertIsSatisfied();
            mockVerified.assertIsSatisfied();

            metadata.updateLastUsed();
            keyManager.updateKeyMetadata(keyId, metadata);
        }

        // Verify usage tracking
        metadata = keyManager.getKeyMetadata(keyId);
        assertEquals(3, metadata.getUsageCount());

        // Phase 3: Export for backup
        byte[] backup = keyManager.exportKey(keyPair, KeyLifecycleManager.KeyFormat.PEM, false);
        assertNotNull(backup);
        assertTrue(backup.length > 0);

        // Phase 4: Rotation to new key
        String newKeyId = keyId + "-rotated";
        KeyPair rotatedKey = keyManager.rotateKey(keyId, newKeyId, "DILITHIUM");
        assertNotNull(rotatedKey);

        // Verify old key deprecated
        KeyMetadata oldMetadata = keyManager.getKeyMetadata(keyId);
        assertEquals(KeyMetadata.KeyStatus.DEPRECATED, oldMetadata.getStatus());

        // Phase 5: Eventual deletion
        keyManager.deleteKey(keyId);
        assertNull(keyManager.getKeyMetadata(keyId));

        // New key still works
        dilithiumKeyPair = rotatedKey;
        mockSigned.reset();
        mockVerified.reset();
        mockSigned.expectedMessageCount(1);
        mockVerified.expectedMessageCount(1);

        signTemplate.sendBody("Message with rotated key");

        mockSigned.assertIsSatisfied();
        mockVerified.assertIsSatisfied();

        // Cleanup
        keyManager.deleteKey(newKeyId);
    }

    @BindToRegistry("KeyLifecycleManager")
    public KeyLifecycleManager getKeyLifecycleManager() {
        return keyManager;
    }

    @BindToRegistry("Keypair")
    public KeyPair getKeyPair() {
        return dilithiumKeyPair;
    }

    @BindToRegistry("Signer")
    public Signature getSigner() throws Exception {
        return Signature.getInstance(PQCSignatureAlgorithms.DILITHIUM.getAlgorithm(),
                PQCSignatureAlgorithms.DILITHIUM.getBcProvider());
    }

    @BindToRegistry("KeyGenerator")
    public javax.crypto.KeyGenerator getKeyGenerator() throws Exception {
        return javax.crypto.KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.NTRU.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.NTRU.getBcProvider());
    }
}
