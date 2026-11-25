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
package org.apache.camel.component.pqc.crypto.hybrid;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

import org.apache.camel.component.pqc.PQCAlgorithmId;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HybridSignatureTest {

    @BeforeAll
    static void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    void testSignAndVerifyWithECDSAAndMLDSA() throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        // Generate ML-DSA key pair
        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "Hello, hybrid cryptography!".getBytes();

        // Sign
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        assertNotNull(hybridSig);
        assertTrue(hybridSig.length > 0);

        // Verify with same keys
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        boolean valid = HybridSignature.verify(
                data,
                hybridSig,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertTrue(valid);
    }

    @Test
    void testSignProducesV2WireFormat() throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        // Generate ML-DSA key pair
        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "v2 format test".getBytes();
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Check magic bytes
        assertEquals(HybridSignature.MAGIC_BYTE_1, hybridSig[0]);
        assertEquals(HybridSignature.MAGIC_BYTE_2, hybridSig[1]);
        assertEquals(HybridSignature.VERSION_V2, hybridSig[2]);
        assertEquals(HybridSignature.FORMAT_HYBRID_SIGNATURE, hybridSig[3]);

        // Parse and verify algorithm IDs are populated
        HybridSignature.HybridSignatureComponents components = HybridSignature.parse(hybridSig);
        assertEquals(2, components.version());
        assertEquals(PQCAlgorithmId.SHA256_WITH_ECDSA, components.classicalAlgorithmId());
        assertEquals(PQCAlgorithmId.ML_DSA, components.pqcAlgorithmId());
    }

    @Test
    void testParseV1BackwardCompatibility() {
        // Build a hand-crafted v1 payload (no magic bytes)
        byte[] classicalSig = new byte[] { 1, 2, 3, 4, 5 };
        byte[] pqcSig = new byte[] { 10, 20, 30, 40, 50, 60 };
        ByteBuffer v1 = ByteBuffer.allocate(4 + classicalSig.length + pqcSig.length);
        v1.putInt(classicalSig.length);
        v1.put(classicalSig);
        v1.put(pqcSig);

        HybridSignature.HybridSignatureComponents components = HybridSignature.parse(v1.array());

        assertEquals(1, components.version());
        assertNull(components.classicalAlgorithmId(), "v1 payloads carry no algorithm IDs");
        assertNull(components.pqcAlgorithmId(), "v1 payloads carry no algorithm IDs");
        assertArrayEquals(classicalSig, components.classicalSignature());
        assertArrayEquals(pqcSig, components.pqcSignature());
    }

    @Test
    void testVerifyFailsWithWrongData() throws Exception {
        // Generate key pairs
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "Original data".getBytes();
        byte[] tamperedData = "Tampered data".getBytes();

        // Sign original data
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Verify with tampered data
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        boolean valid = HybridSignature.verify(
                tamperedData,
                hybridSig,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertFalse(valid);
    }

    @Test
    void testParseHybridSignature() throws Exception {
        // Create a simple hybrid signature manually using the public API
        byte[] classicalSig = new byte[] { 1, 2, 3, 4, 5 };
        byte[] pqcSig = new byte[] { 10, 20, 30, 40, 50, 60 };

        byte[] combined = HybridSignature.combineSignatures(classicalSig, pqcSig);

        // combineSignatures now produces v2 format
        HybridSignature.HybridSignatureComponents components = HybridSignature.parse(combined);

        assertArrayEquals(classicalSig, components.classicalSignature());
        assertArrayEquals(pqcSig, components.pqcSignature());
        assertEquals(2, components.version());
    }

    @Test
    void testDetailedVerification() throws Exception {
        // Generate key pairs
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "Test data".getBytes();

        // Sign
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Verify with detailed results
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        HybridSignature.HybridVerificationResult result = HybridSignature.verifyDetailed(
                data,
                hybridSig,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertTrue(result.classicalValid());
        assertTrue(result.pqcValid());
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidSignatureFormat() {
        byte[] invalidSig = new byte[] { 0, 0, 0, 0 }; // too short, not v2 magic

        assertThrows(IllegalArgumentException.class, () -> HybridSignature.parse(invalidSig));
    }

    @Test
    void testMalformedV2Input_unknownVersion() {
        ByteBuffer buf = ByteBuffer.allocate(HybridSignature.V2_HEADER_SIZE + 4);
        buf.put(HybridSignature.MAGIC_BYTE_1);
        buf.put(HybridSignature.MAGIC_BYTE_2);
        buf.put((byte) 0x99); // unknown version
        buf.put(HybridSignature.FORMAT_HYBRID_SIGNATURE);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putInt(1);
        buf.put(new byte[] { 1, 2, 3, 4 });
        assertThrows(IllegalArgumentException.class, () -> HybridSignature.parse(buf.array()));
    }

    @Test
    void testMalformedV2Input_wrongFormatType() {
        ByteBuffer buf = ByteBuffer.allocate(HybridSignature.V2_HEADER_SIZE + 4);
        buf.put(HybridSignature.MAGIC_BYTE_1);
        buf.put(HybridSignature.MAGIC_BYTE_2);
        buf.put(HybridSignature.VERSION_V2);
        buf.put((byte) 0x02); // KEM format type, not signature
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putInt(1);
        buf.put(new byte[] { 1, 2, 3, 4 });
        assertThrows(IllegalArgumentException.class, () -> HybridSignature.parse(buf.array()));
    }

    @Test
    void testMalformedV2Input_classicalLengthTooLarge() {
        ByteBuffer buf = ByteBuffer.allocate(HybridSignature.V2_HEADER_SIZE + 4);
        buf.put(HybridSignature.MAGIC_BYTE_1);
        buf.put(HybridSignature.MAGIC_BYTE_2);
        buf.put(HybridSignature.VERSION_V2);
        buf.put(HybridSignature.FORMAT_HYBRID_SIGNATURE);
        buf.putShort((short) PQCAlgorithmId.SHA256_WITH_ECDSA.getId());
        buf.putShort((short) PQCAlgorithmId.ML_DSA.getId());
        buf.putInt(Integer.MAX_VALUE); // classicalLength absurdly large
        buf.put(new byte[] { 1, 2, 3, 4 });
        assertThrows(IllegalArgumentException.class, () -> HybridSignature.parse(buf.array()));
    }

    @Test
    void testV1BlobThroughVerifyEndToEnd() throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();

        // Generate ML-DSA key pair
        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();

        byte[] data = "v1 end-to-end test".getBytes();

        // Sign with both algorithms individually to get the raw signatures
        Signature ecSign = Signature.getInstance("SHA256withECDSA", "BC");
        ecSign.initSign(ecKeyPair.getPrivate());
        ecSign.update(data);
        byte[] classicalSig = ecSign.sign();

        Signature pqcSign = Signature.getInstance("ML-DSA", "BC");
        pqcSign.initSign(mldsaKeyPair.getPrivate());
        pqcSign.update(data);
        byte[] pqcSig = pqcSign.sign();

        // Build a v1-format blob: [4 bytes: classical len][classical sig][pqc sig]
        ByteBuffer v1 = ByteBuffer.allocate(4 + classicalSig.length + pqcSig.length);
        v1.putInt(classicalSig.length);
        v1.put(classicalSig);
        v1.put(pqcSig);

        // Verify via HybridSignature.verify()
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        boolean valid = HybridSignature.verify(
                data,
                v1.array(),
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertTrue(valid);
    }

    @Test
    void testValidClassicalHalfGarbagePqcHalf() throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();

        // Generate ML-DSA key pair
        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();

        byte[] data = "garbage pqc test".getBytes();

        // Sign to get a valid hybrid signature
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Parse to get components, replace PQC signature with garbage
        HybridSignature.HybridSignatureComponents components = HybridSignature.parse(hybridSig);
        byte[] garbagePqc = new byte[components.pqcSignature().length];
        new SecureRandom().nextBytes(garbagePqc);

        // Rebuild v2 blob with garbage PQC part
        byte[] tampered = HybridSignature.combineSignatures(
                components.classicalSignature(), garbagePqc,
                components.classicalAlgorithmId(), components.pqcAlgorithmId());

        // Verify should return false (not throw)
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        boolean valid = HybridSignature.verify(
                data,
                tampered,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertFalse(valid, "Verification should fail when PQC signature is garbage");
    }

    @Test
    void testAlgorithmIdMismatchRejected() throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();

        // Generate ML-DSA key pair
        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();

        byte[] data = "algorithm mismatch test".getBytes();

        // Sign
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Tamper the classical algorithm ID in the header (bytes 4-5) to Ed25519 instead of SHA256withECDSA
        byte[] tampered = hybridSig.clone();
        ByteBuffer buf = ByteBuffer.wrap(tampered);
        buf.position(4);
        buf.putShort((short) PQCAlgorithmId.ED25519.getId());

        // Verify should throw due to algorithm ID mismatch
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        assertThrows(IllegalArgumentException.class, () -> HybridSignature.verify(
                data,
                tampered,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier));
    }
}
