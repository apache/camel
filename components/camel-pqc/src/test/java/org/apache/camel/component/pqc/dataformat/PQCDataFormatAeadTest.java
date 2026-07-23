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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;

import org.apache.camel.Exchange;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the authenticated-encryption (AEAD) behaviour of {@link PQCDataFormat}: GCM and ChaCha20-Poly1305 round-trips,
 * tamper detection, and rejection of non-AEAD symmetric algorithms.
 */
public class PQCDataFormatAeadTest extends CamelTestSupport {

    private static final String ORIGINAL = "Authenticated post-quantum encryption payload";

    @BeforeAll
    public static void startup() {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    private KeyPair mlkemKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        return kpg.generateKeyPair();
    }

    private byte[] marshal(PQCDataFormat df, String body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        df.marshal(exchange, body, bos);
        return bos.toByteArray();
    }

    private String unmarshalToString(PQCDataFormat df, byte[] data) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Object out = df.unmarshal(exchange, new ByteArrayInputStream(data));
        assertNotNull(out);
        return new String((byte[]) out, StandardCharsets.UTF_8);
    }

    @Test
    void testAesGcmRoundTrip() throws Exception {
        PQCDataFormat df = new PQCDataFormat("MLKEM", "AES", mlkemKeyPair());
        df.setSymmetricKeyLength(256);
        df.start();
        byte[] enc = marshal(df, ORIGINAL);
        assertEquals(ORIGINAL, unmarshalToString(df, enc));
    }

    @Test
    void testAriaGcmRoundTrip() throws Exception {
        PQCDataFormat df = new PQCDataFormat("MLKEM", "ARIA", mlkemKeyPair());
        df.setSymmetricKeyLength(128);
        df.start();
        byte[] enc = marshal(df, ORIGINAL);
        assertEquals(ORIGINAL, unmarshalToString(df, enc));
    }

    @Test
    void testChaCha20Poly1305RoundTrip() throws Exception {
        PQCDataFormat df = new PQCDataFormat("MLKEM", "CHACHA7539", mlkemKeyPair());
        // ChaCha20-Poly1305 requires a 256-bit key
        df.setSymmetricKeyLength(256);
        df.start();
        byte[] enc = marshal(df, ORIGINAL);
        assertEquals(ORIGINAL, unmarshalToString(df, enc));
    }

    @Test
    void testTamperedCiphertextIsRejected() throws Exception {
        PQCDataFormat df = new PQCDataFormat("MLKEM", "AES", mlkemKeyPair());
        df.setSymmetricKeyLength(256);
        df.start();
        byte[] enc = marshal(df, ORIGINAL);

        // Flip the last byte, which is part of the GCM authentication tag
        enc[enc.length - 1] ^= 0x01;

        Exchange exchange = new DefaultExchange(context);
        ByteArrayInputStream in = new ByteArrayInputStream(enc);
        IOException ex = assertThrows(IOException.class, () -> df.unmarshal(exchange, in));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("authentication") || msg.contains("tamper"),
                "Expected an authentication/tamper error but got: " + ex.getMessage());
    }

    @Test
    void testNonAeadAlgorithmRejectedAtStart() throws Exception {
        PQCDataFormat df = new PQCDataFormat("MLKEM", "DESEDE", mlkemKeyPair());
        df.setSymmetricKeyLength(192);
        Exception ex = assertThrows(Exception.class, df::start);
        String msg = ex.getMessage() != null
                ? ex.getMessage()
                : (ex.getCause() != null ? ex.getCause().getMessage() : "");
        assertTrue(msg != null && msg.contains("AEAD"), "Expected an AEAD rejection but got: " + ex);
    }

    @Test
    void testNonAeadAlgorithmRejectedAtMarshal() throws Exception {
        PQCDataFormat df = new PQCDataFormat("MLKEM", "RC2", mlkemKeyPair());
        Exchange exchange = new DefaultExchange(context);
        assertThrows(IllegalArgumentException.class,
                () -> df.marshal(exchange, ORIGINAL, new ByteArrayOutputStream()));
    }
}
