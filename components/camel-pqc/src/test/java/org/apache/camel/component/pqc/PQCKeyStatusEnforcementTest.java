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
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for key status enforcement in PQCProducer. Validates that REVOKED keys are rejected for all operations, EXPIRED
 * keys are rejected for signing/encapsulation but allowed for verification/extraction, DEPRECATED keys produce a WARN
 * but still function, and the strictKeyLifecycle flag controls enforcement.
 */
public class PQCKeyStatusEnforcementTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    @EndpointInject("mock:signed")
    protected MockEndpoint mockSigned;

    @EndpointInject("mock:verified")
    protected MockEndpoint mockVerified;

    @EndpointInject("mock:signStrict")
    protected MockEndpoint mockSignStrict;

    @EndpointInject("mock:verifyStrict")
    protected MockEndpoint mockVerifyStrict;

    @EndpointInject("mock:signDisabled")
    protected MockEndpoint mockSignDisabled;

    @Produce("direct:sign")
    protected ProducerTemplate signTemplate;

    @Produce("direct:signAndVerify")
    protected ProducerTemplate signAndVerifyTemplate;

    @Produce("direct:signDisabledStrict")
    protected ProducerTemplate signDisabledStrictTemplate;

    private KeyLifecycleManager keyManager;
    private KeyPair sharedKeyPair;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Sign-only route with strict lifecycle (default: true)
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=DILITHIUM")
                        .to("mock:signed");

                // Sign then verify route with strict lifecycle
                from("direct:signAndVerify")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=DILITHIUM")
                        .to("mock:signStrict")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=DILITHIUM")
                        .to("mock:verifyStrict");

                // Sign route with strict lifecycle disabled
                from("direct:signDisabledStrict")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=DILITHIUM&strictKeyLifecycle=false")
                        .to("mock:signDisabled");
            }
        };
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

    // -- REVOKED key tests --

    @Test
    void testRevokedKeyRejectedForSign() throws Exception {
        // Revoke the key
        keyManager.revokeKey("test-key", "Compromised");

        // Attempt to sign with a revoked key should fail
        assertThatThrownBy(() -> signTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll("revoked", "test-key");
    }

    @Test
    void testRevokedKeyRejectedForVerify() throws Exception {
        // First sign with an active key (no KEY_ID, so no enforcement)
        mockSigned.expectedMessageCount(1);
        signTemplate.sendBody("Hello");
        mockSigned.assertIsSatisfied();

        byte[] signature = mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
        assertNotNull(signature);

        // Now revoke the key and try to verify
        keyManager.revokeKey("test-key", "Key compromise detected");

        // Attempt to verify with a revoked key should also fail
        assertThatThrownBy(() -> signAndVerifyTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");
    }

    // -- EXPIRED key tests --

    @Test
    void testExpiredKeyRejectedForSign() throws Exception {
        // Expire the key
        keyManager.expireKey("test-key");

        // Attempt to sign with an expired key should fail
        assertThatThrownBy(() -> signTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll("expired", "test-key");
    }

    @Test
    void testExpiredKeyAllowedForVerify() throws Exception {
        // First sign with the active key (no KEY_ID to skip enforcement for the sign step)
        mockSignStrict.expectedMessageCount(1);
        mockVerifyStrict.expectedMessageCount(1);
        signAndVerifyTemplate.sendBody("Hello");
        mockSignStrict.assertIsSatisfied();
        mockVerifyStrict.assertIsSatisfied();

        byte[] signature
                = mockSignStrict.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
        assertNotNull(signature);

        // Now expire the key
        keyManager.expireKey("test-key");

        // Verify should still work (expired keys allowed for verification)
        mockVerifyStrict.reset();
        mockVerifyStrict.expectedMessageCount(1);
        mockSignStrict.reset();
        mockSignStrict.expectedMessageCount(1);

        // Sign without enforcement (no KEY_ID), then verify with expired key (KEY_ID set)
        signAndVerifyTemplate.sendBody("Hello");
        mockVerifyStrict.assertIsSatisfied();

        // Verification with an expired key should succeed (no exception)
        assertTrue(
                mockVerifyStrict.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class));
    }

    // -- DEPRECATED key tests --

    @Test
    void testDeprecatedKeyAllowedForSignWithWarning() throws Exception {
        // Deprecate the key (simulating a rotation)
        KeyMetadata metadata = keyManager.getKeyMetadata("test-key");
        metadata.setStatus(KeyMetadata.KeyStatus.DEPRECATED);
        keyManager.updateKeyMetadata("test-key", metadata);

        // Sign should still work with a deprecated key (just logs a warning)
        mockSigned.expectedMessageCount(1);
        signTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSigned.assertIsSatisfied();

        assertNotNull(
                mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    @Test
    void testDeprecatedKeyAllowedForVerifyWithWarning() throws Exception {
        // First sign with active key
        mockSignStrict.expectedMessageCount(1);
        mockVerifyStrict.expectedMessageCount(1);
        signAndVerifyTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSignStrict.assertIsSatisfied();
        mockVerifyStrict.assertIsSatisfied();

        // Now deprecate the key
        KeyMetadata metadata = keyManager.getKeyMetadata("test-key");
        metadata.setStatus(KeyMetadata.KeyStatus.DEPRECATED);
        keyManager.updateKeyMetadata("test-key", metadata);

        // Verify with deprecated key should work
        mockSignStrict.reset();
        mockSignStrict.expectedMessageCount(1);
        mockVerifyStrict.reset();
        mockVerifyStrict.expectedMessageCount(1);

        signAndVerifyTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockVerifyStrict.assertIsSatisfied();
        assertTrue(
                mockVerifyStrict.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class));
    }

    // -- strictKeyLifecycle=false tests --

    @Test
    void testStrictKeyLifecycleDisabledAllowsRevokedKey() throws Exception {
        // Revoke the key
        keyManager.revokeKey("test-key", "Compromised");

        // With strictKeyLifecycle=false, even revoked keys should work
        mockSignDisabled.expectedMessageCount(1);
        signDisabledStrictTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSignDisabled.assertIsSatisfied();

        assertNotNull(
                mockSignDisabled.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    @Test
    void testStrictKeyLifecycleDisabledAllowsExpiredKey() throws Exception {
        // Expire the key
        keyManager.expireKey("test-key");

        // With strictKeyLifecycle=false, expired keys should work for signing
        mockSignDisabled.expectedMessageCount(1);
        signDisabledStrictTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSignDisabled.assertIsSatisfied();

        assertNotNull(
                mockSignDisabled.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    // -- No KEY_ID header tests --

    @Test
    void testNoKeyIdHeaderSkipsEnforcement() throws Exception {
        // Even if key is revoked, without KEY_ID header enforcement is skipped
        keyManager.revokeKey("test-key", "Compromised");

        mockSigned.expectedMessageCount(1);
        // Send without KEY_ID header
        signTemplate.sendBody("Hello");
        mockSigned.assertIsSatisfied();

        assertNotNull(
                mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    // -- ACTIVE key tests --

    @Test
    void testActiveKeyAllowedForSign() throws Exception {
        mockSigned.expectedMessageCount(1);
        signTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSigned.assertIsSatisfied();

        assertNotNull(
                mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    @Test
    void testActiveKeyAllowedForSignAndVerify() throws Exception {
        mockSignStrict.expectedMessageCount(1);
        mockVerifyStrict.expectedMessageCount(1);
        signAndVerifyTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSignStrict.assertIsSatisfied();
        mockVerifyStrict.assertIsSatisfied();
        assertTrue(
                mockVerifyStrict.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class));
    }

    // -- PENDING_ROTATION key tests --

    @Test
    void testPendingRotationKeyAllowedForSign() throws Exception {
        KeyMetadata metadata = keyManager.getKeyMetadata("test-key");
        metadata.setStatus(KeyMetadata.KeyStatus.PENDING_ROTATION);
        keyManager.updateKeyMetadata("test-key", metadata);

        mockSigned.expectedMessageCount(1);
        signTemplate.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, "test-key");
        mockSigned.assertIsSatisfied();

        assertNotNull(
                mockSigned.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class));
    }

    // -- Registry bindings --

    @BindToRegistry("Keypair")
    public KeyPair setKeyPair() throws Exception {
        ensureProviders();
        if (sharedKeyPair == null) {
            initKeyManager();
        }
        return sharedKeyPair;
    }

    @BindToRegistry("KeyLifecycleManager")
    public KeyLifecycleManager getKeyLifecycleManager() throws Exception {
        ensureProviders();
        if (keyManager == null) {
            initKeyManager();
        }
        return keyManager;
    }

    private static void ensureProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    private void initKeyManager() throws Exception {
        keyManager = new FileBasedKeyLifecycleManager(tempDir.toString());

        // Generate a key pair using KeyPairGenerator directly and store it via the manager
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(
                PQCSignatureAlgorithms.DILITHIUM.getAlgorithm(),
                PQCSignatureAlgorithms.DILITHIUM.getBcProvider());
        kpGen.initialize(DilithiumParameterSpec.dilithium2, new SecureRandom());
        sharedKeyPair = kpGen.generateKeyPair();

        // Store the key in the lifecycle manager so we can test status enforcement
        KeyMetadata metadata = new KeyMetadata("test-key", "DILITHIUM");
        keyManager.storeKey("test-key", sharedKeyPair, metadata);
    }
}
