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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.camel.component.pqc.PQCSymmetricAlgorithms;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The hybrid KEM must stamp the JCE algorithm name on the secret key it returns, as the non-hybrid operations do, so
 * both ends of an exchange agree on the key's identity and the key resolves through {@code Cipher.getInstance}.
 * <p/>
 * The same configured name is also the HKDF info of {@code combineSecrets}, which must keep its original value: a
 * change there would derive a different key and make every encapsulation produced by an earlier version undecryptable.
 */
class HybridKEMKeyLabelTest {

    @BeforeAll
    static void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    private static KeyPair x25519() throws Exception {
        return KeyPairGenerator.getInstance("X25519", "BC").generateKeyPair();
    }

    private static KeyPair mlkem() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        kpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        return kpg.generateKeyPair();
    }

    @ParameterizedTest
    @CsvSource({
            "AES,AES,256",
            // the two whose configured name is not the JCE name
            "DESEDE,DESede,192",
            "GOST3412_2015,GOST3412-2015,256" })
    void bothEndsStampTheJceName(String configured, String jceName, int keyLength) throws Exception {
        KeyPair classical = x25519();
        KeyPair pqc = mlkem();

        HybridKEM.HybridEncapsulationResult encapsulated = HybridKEM.encapsulate(
                classical.getPublic(), pqc.getPublic(),
                KeyAgreement.getInstance("X25519", "BC"), KeyGenerator.getInstance("ML-KEM", "BC"),
                configured, keyLength, "HKDF-SHA256");

        SecretKey extracted = HybridKEM.extract(
                encapsulated.encapsulation(), classical.getPrivate(), pqc.getPrivate(),
                KeyAgreement.getInstance("X25519", "BC"), KeyGenerator.getInstance("ML-KEM", "BC"),
                configured, keyLength, "HKDF-SHA256");

        assertEquals(jceName, encapsulated.sharedSecret().getAlgorithm(),
                "the generated key must carry the JCE name");
        assertEquals(jceName, extracted.getAlgorithm(), "the extracted key must carry the JCE name");
        assertArrayEquals(encapsulated.sharedSecret().getEncoded(), extracted.getEncoded(),
                "both ends must still derive the same secret");

        // the point of the JCE name: the key is directly usable
        assertDoesNotThrow(() -> Cipher.getInstance(extracted.getAlgorithm()),
                "the stamped name must resolve as a cipher algorithm");
    }

    @Test
    void theDerivedSecretIsUnchangedByTheRelabelling() throws Exception {
        KeyPair classical = x25519();
        KeyPair pqc = mlkem();

        // The HKDF info is the configured name, so deriving with the configured name and with its JCE name must
        // produce different secrets. If they matched, this test could not tell whether the info had been remapped.
        HybridKEM.HybridEncapsulationResult viaConfigured = HybridKEM.encapsulate(
                classical.getPublic(), pqc.getPublic(),
                KeyAgreement.getInstance("X25519", "BC"), KeyGenerator.getInstance("ML-KEM", "BC"),
                "DESEDE", 192, "HKDF-SHA256");

        // Extracting the same encapsulation with the JCE spelling must fail to reproduce the secret, which is what
        // shows the info still carries the configured name rather than the mapped one.
        SecretKey viaJceName = HybridKEM.extract(
                viaConfigured.encapsulation(), classical.getPrivate(), pqc.getPrivate(),
                KeyAgreement.getInstance("X25519", "BC"), KeyGenerator.getInstance("ML-KEM", "BC"),
                "DESede", 192, "HKDF-SHA256");

        assertFalse(
                Arrays.equals(viaConfigured.sharedSecret().getEncoded(), viaJceName.getEncoded()),
                "the HKDF info must still be the configured name: if remapping it made these equal, the wire format"
                                                                                                   + " would have changed and older encapsulations would no longer decrypt");
    }

    @Test
    void anAlreadyJceNameIsLeftAlone() {
        assertEquals("AES", PQCSymmetricAlgorithms.toJceName("AES"));
        assertEquals("DESede", PQCSymmetricAlgorithms.toJceName("DESEDE"));
        assertEquals("DESede", PQCSymmetricAlgorithms.toJceName("DESede"));
        assertEquals("GOST3412-2015", PQCSymmetricAlgorithms.toJceName("GOST3412-2015"));
        assertEquals("Unknown", PQCSymmetricAlgorithms.toJceName("Unknown"));
    }
}
