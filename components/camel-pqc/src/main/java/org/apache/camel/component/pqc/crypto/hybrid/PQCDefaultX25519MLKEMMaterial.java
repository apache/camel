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

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;

import org.apache.camel.component.pqc.BouncyCastleSupport;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCParameterSpecResolver;
import org.apache.camel.util.SecureRandomHelper;

/**
 * Default hybrid KEM material combining X25519 with ML-KEM-768. This is the recommended hybrid combination per NIST
 * guidelines, providing both classical security from X25519 and quantum-resistant security from ML-KEM.
 * <p>
 * X25519 + ML-KEM is recommended for:
 * <ul>
 * <li>TLS 1.3 hybrid key exchange</li>
 * <li>General purpose hybrid key encapsulation</li>
 * <li>Protection against "harvest now, decrypt later" attacks</li>
 * </ul>
 */
public class PQCDefaultX25519MLKEMMaterial {

    private static KeyPair classicalKeyPair;
    private static KeyAgreement classicalKeyAgreement;
    private static KeyPair pqcKeyPair;
    private static KeyGenerator pqcKeyGenerator;
    private static PQCHybridKEMMaterial material;

    private static boolean initialized;

    /** Initializes the default key material on first use and preserves the original failure as an exception. */
    public static synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }
        BouncyCastleSupport.ensureProviders();
        try {
            // Generate X25519 key pair
            KeyPairGenerator x25519Kpg
                    = KeyPairGenerator.getInstance("X25519", BouncyCastleSupport.getProvider("KeyPairGenerator", "X25519"));
            classicalKeyPair = x25519Kpg.generateKeyPair();
            classicalKeyAgreement
                    = KeyAgreement.getInstance("X25519", BouncyCastleSupport.getProvider("KeyAgreement", "X25519"));

            // Generate ML-KEM-768 key pair
            KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance(
                    PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                    PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider("KeyPairGenerator"));
            mlkemKpg.initialize(PQCParameterSpecResolver.resolve("MLKEM", "ML-KEM-768"), SecureRandomHelper.getSecureRandom());
            pqcKeyPair = mlkemKpg.generateKeyPair();
            pqcKeyGenerator = KeyGenerator.getInstance(
                    PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                    PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider("KeyGenerator"));

            // Create the material container
            material = new PQCHybridKEMMaterial(
                    classicalKeyPair,
                    classicalKeyAgreement,
                    pqcKeyPair,
                    pqcKeyGenerator,
                    "X25519",
                    "MLKEM");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize hybrid X25519 + ML-KEM material", e);
        }
        initialized = true;
    }

    private PQCDefaultX25519MLKEMMaterial() {
        // Static access only
    }

    public static KeyPair getClassicalKeyPair() throws Exception {
        initialize();
        return classicalKeyPair;
    }

    public static KeyAgreement getClassicalKeyAgreement() throws Exception {
        initialize();
        return classicalKeyAgreement;
    }

    public static KeyPair getPqcKeyPair() throws Exception {
        initialize();
        return pqcKeyPair;
    }

    public static KeyGenerator getPqcKeyGenerator() throws Exception {
        initialize();
        return pqcKeyGenerator;
    }

    public static PQCHybridKEMMaterial getMaterial() throws Exception {
        initialize();
        return material;
    }
}
