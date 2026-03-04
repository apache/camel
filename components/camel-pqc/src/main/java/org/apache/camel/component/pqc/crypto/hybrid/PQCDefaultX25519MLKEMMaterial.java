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

import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

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

    public static final KeyPair classicalKeyPair;
    public static final KeyAgreement classicalKeyAgreement;
    public static final KeyPair pqcKeyPair;
    public static final KeyGenerator pqcKeyGenerator;
    public static final PQCHybridKEMMaterial material;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }

        try {
            // Generate X25519 key pair
            KeyPairGenerator x25519Kpg = KeyPairGenerator.getInstance("X25519", "BC");
            classicalKeyPair = x25519Kpg.generateKeyPair();
            classicalKeyAgreement = KeyAgreement.getInstance("X25519", "BC");

            // Generate ML-KEM-768 key pair
            KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance(
                    PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                    PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
            mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
            pqcKeyPair = mlkemKpg.generateKeyPair();
            pqcKeyGenerator = KeyGenerator.getInstance(
                    PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                    PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());

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
    }

    private PQCDefaultX25519MLKEMMaterial() {
        // Static access only
    }
}
