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

import java.security.*;
import java.security.spec.ECGenParameterSpec;

import org.apache.camel.component.pqc.BouncyCastleSupport;
import org.apache.camel.component.pqc.PQCParameterSpecResolver;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.apache.camel.util.SecureRandomHelper;

/**
 * Default hybrid signature material combining ECDSA P-256 with ML-DSA-65. This is the recommended hybrid combination
 * providing both classical security from ECDSA and quantum-resistant security from ML-DSA.
 * <p>
 * NIST recommends this combination for the transition period to post-quantum cryptography.
 */
public class PQCDefaultECDSAMLDSAMaterial {

    private static KeyPair classicalKeyPair;
    private static Signature classicalSigner;
    private static KeyPair pqcKeyPair;
    private static Signature pqcSigner;
    private static PQCHybridSignatureMaterial material;

    private static boolean initialized;

    /** Initializes the default key material on first use and preserves the original failure as an exception. */
    public static synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }
        BouncyCastleSupport.ensureProviders();
        try {
            // Generate ECDSA P-256 key pair
            KeyPairGenerator ecKpg
                    = KeyPairGenerator.getInstance("EC", BouncyCastleSupport.getProvider("KeyPairGenerator", "EC"));
            ecKpg.initialize(new ECGenParameterSpec("secp256r1"), SecureRandomHelper.getSecureRandom());
            classicalKeyPair = ecKpg.generateKeyPair();
            classicalSigner
                    = Signature.getInstance("SHA256withECDSA", BouncyCastleSupport.getProvider("Signature", "SHA256withECDSA"));

            // Generate ML-DSA-65 key pair
            KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance(
                    PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                    PQCSignatureAlgorithms.MLDSA.getBcProvider("KeyPairGenerator"));
            mldsaKpg.initialize(PQCParameterSpecResolver.resolve("MLDSA", "ML-DSA-65"), SecureRandomHelper.getSecureRandom());
            pqcKeyPair = mldsaKpg.generateKeyPair();
            pqcSigner = Signature.getInstance(
                    PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                    PQCSignatureAlgorithms.MLDSA.getBcProvider("Signature"));

            // Create the material container
            material = new PQCHybridSignatureMaterial(
                    classicalKeyPair,
                    classicalSigner,
                    pqcKeyPair,
                    pqcSigner,
                    "ECDSA_P256",
                    "MLDSA");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize hybrid ECDSA + ML-DSA material", e);
        }
        initialized = true;
    }

    private PQCDefaultECDSAMLDSAMaterial() {
        // Static access only
    }

    public static KeyPair getClassicalKeyPair() throws Exception {
        initialize();
        return classicalKeyPair;
    }

    public static Signature getClassicalSigner() throws Exception {
        initialize();
        return classicalSigner;
    }

    public static KeyPair getPqcKeyPair() throws Exception {
        initialize();
        return pqcKeyPair;
    }

    public static Signature getPqcSigner() throws Exception {
        initialize();
        return pqcSigner;
    }

    public static PQCHybridSignatureMaterial getMaterial() throws Exception {
        initialize();
        return material;
    }
}
