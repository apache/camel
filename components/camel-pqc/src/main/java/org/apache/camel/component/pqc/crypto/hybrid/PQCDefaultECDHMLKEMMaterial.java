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

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;

import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

/**
 * Default hybrid KEM material combining ECDH P-256 with ML-KEM-768. This combination provides compatibility with
 * existing ECDH-based systems while adding quantum-resistant security from ML-KEM.
 */
public class PQCDefaultECDHMLKEMMaterial {

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
            // Generate ECDH P-256 key pair
            KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
            ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            classicalKeyPair = ecKpg.generateKeyPair();
            classicalKeyAgreement = KeyAgreement.getInstance("ECDH", "BC");

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
                    "ECDH_P256",
                    "MLKEM");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize hybrid ECDH + ML-KEM material", e);
        }
    }

    private PQCDefaultECDHMLKEMMaterial() {
        // Static access only
    }
}
