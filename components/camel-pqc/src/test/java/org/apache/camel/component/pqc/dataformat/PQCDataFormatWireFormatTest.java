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
package org.apache.camel.component.pqc.dataformat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Exchange;
import org.apache.camel.component.pqc.PQCAlgorithmId;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link PQCDataFormat}:
 * <ul>
 * <li>writes wire format v2 on marshal (8-byte self-describing header)</li>
 * <li>reads wire format v2 on unmarshal (round-trip)</li>
 * <li>reads legacy wire format v1 on unmarshal (backward compatibility)</li>
 * <li>rejects truncated, wrong-version, and wrong-format-type headers</li>
 * <li>rejects algorithm mismatches between header and configuration (KEM and symmetric)</li>
 * </ul>
 */
public class PQCDataFormatWireFormatTest extends CamelTestSupport {

    private static final String PAYLOAD = "CAMEL-27548 wire format v2 test payload";

    private static KeyPair keyPair;

    @BeforeAll
    static void startup() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        keyPair = kpg.generateKeyPair();
    }

    // -------------------------------------------------------------------------
    // Helper: build a PQCDataFormat with the shared key pair
    // -------------------------------------------------------------------------

    private PQCDataFormat newFormat() {
        PQCDataFormat fmt = new PQCDataFormat();
        fmt.setKeyEncapsulationAlgorithm("MLKEM");
        fmt.setSymmetricKeyAlgorithm("AES");
        fmt.setKeyPair(keyPair);
        return fmt;
    }

    // -------------------------------------------------------------------------
    // Test: marshal writes v2 header
    // -------------------------------------------------------------------------

    @Test
    void marshalWritesV2Header() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fmt.marshal(exchange, PAYLOAD.getBytes(StandardCharsets.UTF_8), out);

            byte[] ciphertext = out.toByteArray();
            assertNotNull(ciphertext);

            // First two bytes must be magic bytes 0x50, 0x51
            assertEquals(PQCDataFormat.MAGIC_BYTE_0, ciphertext[0],
                    "Expected magic byte 0 (0x50) but got 0x" + Integer.toHexString(ciphertext[0] & 0xFF));
            assertEquals(PQCDataFormat.MAGIC_BYTE_1, ciphertext[1],
                    "Expected magic byte 1 (0x51) but got 0x" + Integer.toHexString(ciphertext[1] & 0xFF));

            // Byte 2: version
            assertEquals(PQCDataFormat.WIRE_VERSION, ciphertext[2], "Expected wire format version 0x02");

            // Byte 3: format type
            assertEquals(PQCDataFormat.FORMAT_TYPE_KEM, ciphertext[3], "Expected format type 0x03 (KEM)");

            // Bytes 4-5: KEM algorithm ID — should resolve to ML_KEM (0x0401)
            int kemAlgId = ((ciphertext[4] & 0xFF) << 8) | (ciphertext[5] & 0xFF);
            assertEquals(PQCAlgorithmId.ML_KEM.getId(), kemAlgId,
                    "Expected KEM alg ID for ML-KEM (0x" + Integer.toHexString(PQCAlgorithmId.ML_KEM.getId()) + ")"
                                                                  + " but got 0x" + Integer.toHexString(kemAlgId));

            // Bytes 6-7: symmetric algorithm ID — should resolve to AES (0x0501)
            int symAlgId = ((ciphertext[6] & 0xFF) << 8) | (ciphertext[7] & 0xFF);
            assertEquals(PQCAlgorithmId.AES.getId(), symAlgId,
                    "Expected sym alg ID for AES (0x" + Integer.toHexString(PQCAlgorithmId.AES.getId()) + ")"
                                                               + " but got 0x" + Integer.toHexString(symAlgId));
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Test: marshal/unmarshal round-trip using v2 format
    // -------------------------------------------------------------------------

    @Test
    void v2RoundTrip() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            byte[] original = PAYLOAD.getBytes(StandardCharsets.UTF_8);

            // Marshal
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fmt.marshal(exchange, original, out);

            // Unmarshal
            byte[] decrypted = (byte[]) fmt.unmarshal(exchange, new ByteArrayInputStream(out.toByteArray()));
            assertArrayEquals(original, decrypted, "Decrypted bytes must match original");
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Test: unmarshal reads legacy v1 format (backward compatibility)
    // -------------------------------------------------------------------------

    @Test
    void v1BackwardCompatibility() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            byte[] original = PAYLOAD.getBytes(StandardCharsets.UTF_8);

            // Build a v1 message manually:
            // [4B encap len][encap][12B nonce][ciphertext+tag]
            byte[] v1Bytes = buildV1Message(original, keyPair);

            // The first two bytes of a v1 message must NOT be 0x50 0x51
            assertEquals(0x00, v1Bytes[0] & 0xFF,
                    "v1 test message must not start with 0x50 (would be mis-detected as v2)");

            // Unmarshal must succeed and return the original plaintext
            Exchange exchange = new DefaultExchange(context());
            byte[] decrypted = (byte[]) fmt.unmarshal(exchange, new ByteArrayInputStream(v1Bytes));
            assertArrayEquals(original, decrypted, "v1 backward-compat: decrypted bytes must match original");
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Test: tampered header is rejected (AAD binding)
    // -------------------------------------------------------------------------

    @Test
    void tamperedHeaderIsRejected() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            byte[] original = PAYLOAD.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fmt.marshal(exchange, original, out);
            byte[] ciphertext = out.toByteArray();

            // Flip a bit in the KEM algorithm ID field (byte 4) — this is part of the AAD-bound header
            ciphertext[4] ^= 0x01;

            ByteArrayInputStream in = new ByteArrayInputStream(ciphertext);
            IOException ex = assertThrows(IOException.class, () -> fmt.unmarshal(exchange, in));
            String msg = ex.getMessage().toLowerCase();
            assertTrue(msg.contains("authentication") || msg.contains("tamper") || msg.contains("algorithm"),
                    "Expected an AAD/authentication/algorithm error but got: " + ex.getMessage());
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Error-path tests: truncated headers
    // -------------------------------------------------------------------------

    @Test
    void truncatedStream1Byte() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            ByteArrayInputStream in = new ByteArrayInputStream(new byte[] { 0x50 });
            IOException ex = assertThrows(IOException.class, () -> fmt.unmarshal(exchange, in));
            assertTrue(ex.getMessage().contains("too short"),
                    "Expected 'too short' error but got: " + ex.getMessage());
        } finally {
            fmt.stop();
        }
    }

    @Test
    void truncatedStream2BytesMagicOnly() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            // Only magic bytes, no rest of header
            ByteArrayInputStream in = new ByteArrayInputStream(new byte[] { 0x50, 0x51 });
            assertThrows(Exception.class, () -> fmt.unmarshal(exchange, in));
        } finally {
            fmt.stop();
        }
    }

    @Test
    void truncatedStream7Bytes() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            // 7 bytes: header is 8 bytes, so this is truncated
            byte[] partial = new byte[] { 0x50, 0x51, 0x02, 0x03, 0x04, 0x01, 0x05 };
            ByteArrayInputStream in = new ByteArrayInputStream(partial);
            assertThrows(Exception.class, () -> fmt.unmarshal(exchange, in));
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Error-path tests: wrong version and wrong format type
    // -------------------------------------------------------------------------

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            // Build a full 8-byte header with wrong version (0xFF)
            byte[] badMsg = buildFakeV2Message((byte) 0xFF, PQCDataFormat.FORMAT_TYPE_KEM);
            ByteArrayInputStream in = new ByteArrayInputStream(badMsg);
            IOException ex = assertThrows(IOException.class, () -> fmt.unmarshal(exchange, in));
            assertTrue(ex.getMessage().contains("Unsupported PQC wire format version"),
                    "Expected version error but got: " + ex.getMessage());
        } finally {
            fmt.stop();
        }
    }

    @Test
    void unsupportedFormatTypeIsRejected() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            // Build a full 8-byte header with wrong format type (0xFF)
            byte[] badMsg = buildFakeV2Message(PQCDataFormat.WIRE_VERSION, (byte) 0xFF);
            ByteArrayInputStream in = new ByteArrayInputStream(badMsg);
            IOException ex = assertThrows(IOException.class, () -> fmt.unmarshal(exchange, in));
            assertTrue(ex.getMessage().contains("Unsupported PQC wire format type"),
                    "Expected format type error but got: " + ex.getMessage());
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Error-path test: algorithm mismatch between header and config
    // -------------------------------------------------------------------------

    @Test
    void kemAlgorithmMismatchIsRejected() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            byte[] original = PAYLOAD.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fmt.marshal(exchange, original, out);
            byte[] ciphertext = out.toByteArray();

            // Overwrite the KEM algorithm ID in the header with BIKE (0x0402) instead of ML-KEM (0x0401)
            int bikeId = PQCAlgorithmId.BIKE.getId();
            ciphertext[4] = (byte) (bikeId >> 8);
            ciphertext[5] = (byte) (bikeId & 0xFF);

            ByteArrayInputStream in = new ByteArrayInputStream(ciphertext);
            IOException ex = assertThrows(IOException.class, () -> fmt.unmarshal(exchange, in));
            assertTrue(ex.getMessage().contains("encrypted with KEM algorithm"),
                    "Expected KEM algorithm mismatch error but got: " + ex.getMessage());
        } finally {
            fmt.stop();
        }
    }

    @Test
    void symAlgorithmMismatchIsRejected() throws Exception {
        PQCDataFormat fmt = newFormat();
        fmt.start();
        try {
            Exchange exchange = new DefaultExchange(context());
            byte[] original = PAYLOAD.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fmt.marshal(exchange, original, out);
            byte[] ciphertext = out.toByteArray();

            // Overwrite the symmetric algorithm ID in the header with ARIA (0x0502) instead of AES (0x0501)
            int ariaId = PQCAlgorithmId.ARIA.getId();
            ciphertext[6] = (byte) (ariaId >> 8);
            ciphertext[7] = (byte) (ariaId & 0xFF);

            ByteArrayInputStream in = new ByteArrayInputStream(ciphertext);
            IOException ex = assertThrows(IOException.class, () -> fmt.unmarshal(exchange, in));
            assertTrue(ex.getMessage().contains("encrypted with symmetric algorithm"),
                    "Expected symmetric algorithm mismatch error but got: " + ex.getMessage());
        } finally {
            fmt.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helper: craft a v1 wire-format message
    // -------------------------------------------------------------------------

    /**
     * Constructs a wire-format v1 message:
     *
     * <pre>
     * [4B encap len][encap][12B nonce][ciphertext+tag]
     * </pre>
     */
    private static byte[] buildV1Message(byte[] plaintext, KeyPair kp) throws Exception {
        // Generate KEM encapsulation
        KeyGenerator kg = KeyGenerator.getInstance("ML-KEM", "BC");
        kg.init(new KEMGenerateSpec(kp.getPublic(), "AES", 256), new SecureRandom());
        SecretKeyWithEncapsulation secretKey = (SecretKeyWithEncapsulation) kg.generateKey();
        byte[] encapsulation = secretKey.getEncapsulation();

        // Encrypt with AES-GCM (no AAD for v1)
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        SecretKeySpec aesKey = new SecretKeySpec(secretKey.getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Write v1 layout
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(encapsulation.length);
        dos.write(encapsulation);
        dos.write(nonce);
        dos.write(ciphertext);
        dos.flush();
        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Helper: build a fake v2 message with given version and format type
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal v2-shaped byte array with the given version and format type bytes. The rest of the content is
     * garbage — just enough to trigger header validation without hitting EOF on the magic peek.
     */
    private static byte[] buildFakeV2Message(byte version, byte formatType) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeByte(PQCDataFormat.MAGIC_BYTE_0);
            dos.writeByte(PQCDataFormat.MAGIC_BYTE_1);
            dos.writeByte(version);
            dos.writeByte(formatType);
            dos.writeShort(PQCAlgorithmId.ML_KEM.getId());
            dos.writeShort(PQCAlgorithmId.AES.getId());
            // Pad with enough bytes so readFully/readInt don't fail before the version/format check
            dos.writeInt(0);
            dos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
