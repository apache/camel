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

import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

/**
 * Default hybrid signature material combining ECDSA P-256 with ML-DSA-65. This is the recommended hybrid combination
 * providing both classical security from ECDSA and quantum-resistant security from ML-DSA.
 * <p>
 * NIST recommends this combination for the transition period to post-quantum cryptography.
 */
public class PQCDefaultECDSAMLDSAMaterial {

    public static final KeyPair classicalKeyPair;
    public static final Signature classicalSigner;
    public static final KeyPair pqcKeyPair;
    public static final Signature pqcSigner;
    public static final PQCHybridSignatureMaterial material;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }

        try {
            // Generate ECDSA P-256 key pair
            KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
            ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            classicalKeyPair = ecKpg.generateKeyPair();
            classicalSigner = Signature.getInstance("SHA256withECDSA", "BC");

            // Generate ML-DSA-65 key pair
            KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance(
                    PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                    PQCSignatureAlgorithms.MLDSA.getBcProvider());
            mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
            pqcKeyPair = mldsaKpg.generateKeyPair();
            pqcSigner = Signature.getInstance(
                    PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                    PQCSignatureAlgorithms.MLDSA.getBcProvider());

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
    }

    private PQCDefaultECDSAMLDSAMaterial() {
        // Static access only
    }
}
