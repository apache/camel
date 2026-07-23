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
package org.apache.camel.component.pqc.lifecycle;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.AlgorithmParameterSpec;

import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.apache.camel.util.SecureRandomHelper;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jcajce.spec.SLHDSAParameterSpec;
import org.bouncycastle.pqc.crypto.lms.LMOtsParameters;
import org.bouncycastle.pqc.crypto.lms.LMSigParameters;
import org.bouncycastle.pqc.jcajce.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared key-generation support for the {@link KeyLifecycleManager} implementations: resolves the JCE algorithm name,
 * the BouncyCastle provider and the default parameter set for a PQC algorithm, and generates key pairs from them.
 */
public final class KeyAlgorithmSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KeyAlgorithmSupport.class);

    /**
     * Fallback key size, used only when an algorithm has no default parameter set. For PQC algorithms the key size is
     * determined by the parameter set.
     */
    private static final int DEFAULT_KEY_SIZE = 256;

    private KeyAlgorithmSupport() {
    }

    /**
     * Generates a key pair for the given PQC algorithm.
     *
     * @param  algorithm     the Camel PQC algorithm name (see PQCSignatureAlgorithms / PQCKeyEncapsulationAlgorithms)
     * @param  parameterSpec an {@link AlgorithmParameterSpec} or an {@link Integer} key size; when {@code null} the
     *                       algorithm's default parameter set is used
     * @return               the generated key pair
     */
    public static KeyPair generateKeyPair(String algorithm, Object parameterSpec) throws Exception {
        KeyPairGenerator generator;
        String provider = determineProvider(algorithm);
        if (provider != null) {
            generator = KeyPairGenerator.getInstance(getAlgorithmName(algorithm), provider);
        } else {
            generator = KeyPairGenerator.getInstance(getAlgorithmName(algorithm));
        }

        if (parameterSpec != null) {
            if (parameterSpec instanceof AlgorithmParameterSpec algorithmParamSpec) {
                generator.initialize(algorithmParamSpec, SecureRandomHelper.getSecureRandom());
            } else if (parameterSpec instanceof Integer keySize) {
                generator.initialize(keySize, SecureRandomHelper.getSecureRandom());
            }
        } else {
            AlgorithmParameterSpec defaultSpec = getDefaultParameterSpec(algorithm);
            if (defaultSpec != null) {
                generator.initialize(defaultSpec, SecureRandomHelper.getSecureRandom());
            } else {
                generator.initialize(DEFAULT_KEY_SIZE, SecureRandomHelper.getSecureRandom());
            }
        }

        return generator.generateKeyPair();
    }

    /**
     * The BouncyCastle provider for the given algorithm, or {@code null} when the algorithm is unknown.
     */
    public static String determineProvider(String algorithm) {
        try {
            return PQCSignatureAlgorithms.valueOf(algorithm).getBcProvider();
        } catch (IllegalArgumentException e1) {
            try {
                return PQCKeyEncapsulationAlgorithms.valueOf(algorithm).getBcProvider();
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    /**
     * The JCE algorithm name for the given algorithm, or the algorithm itself when it is unknown.
     */
    public static String getAlgorithmName(String algorithm) {
        try {
            return PQCSignatureAlgorithms.valueOf(algorithm).getAlgorithm();
        } catch (IllegalArgumentException e1) {
            try {
                return PQCKeyEncapsulationAlgorithms.valueOf(algorithm).getAlgorithm();
            } catch (IllegalArgumentException e2) {
                return algorithm;
            }
        }
    }

    /**
     * The default parameter set for the given algorithm, or {@code null} when it has none.
     */
    public static AlgorithmParameterSpec getDefaultParameterSpec(String algorithm) {
        try {
            switch (algorithm) {
                case "DILITHIUM":
                case "MLDSA":
                    return MLDSAParameterSpec.ml_dsa_44;
                case "SLHDSA":
                case "SPHINCSPLUS":
                    return SLHDSAParameterSpec.slh_dsa_sha2_128s;
                case "FALCON":
                    return FalconParameterSpec.falcon_512;
                case "XMSS":
                    return new XMSSParameterSpec(10, XMSSParameterSpec.SHA256);
                case "XMSSMT":
                    return XMSSMTParameterSpec.XMSSMT_SHA2_20d2_256;
                case "LMS":
                case "HSS":
                    return new LMSKeyGenParameterSpec(
                            LMSigParameters.lms_sha256_n32_h10,
                            LMOtsParameters.sha256_n32_w4);
                case "MLKEM":
                case "KYBER":
                    return MLKEMParameterSpec.ml_kem_768;
                case "NTRU":
                    return NTRUParameterSpec.ntruhps2048509;
                case "NTRULPRime":
                    return NTRULPRimeParameterSpec.ntrulpr653;
                case "SNTRUPrime":
                    return SNTRUPrimeParameterSpec.sntrup761;
                case "SABER":
                    return SABERParameterSpec.lightsaberkem128r3;
                case "FRODO":
                    return FrodoParameterSpec.frodokem640aes;
                case "BIKE":
                    return BIKEParameterSpec.bike128;
                case "HQC":
                    return HQCParameterSpec.hqc128;
                case "CMCE":
                    return CMCEParameterSpec.mceliece348864;
                default:
                    return null;
            }
        } catch (Exception e) {
            LOG.warn("Failed to create default parameter spec for algorithm: {}", algorithm, e);
            return null;
        }
    }
}
