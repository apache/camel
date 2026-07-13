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
package org.apache.camel.component.pqc;

import java.security.spec.AlgorithmParameterSpec;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jcajce.spec.SLHDSAParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.BIKEParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.CMCEParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FrodoParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.HQCParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRULPRimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRUParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.PicnicParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SABERParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SNTRUPrimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusParameterSpec;

/**
 * Resolves the BouncyCastle {@link AlgorithmParameterSpec} for a PQC algorithm from the parameter-set name configured
 * on the endpoint, for example {@code ML-DSA-87} for {@code MLDSA} or {@code ML-KEM-1024} for {@code MLKEM}.
 * <p/>
 * Names are resolved case-insensitively, and the underscore form used by the BouncyCastle constants ({@code ml_dsa_87})
 * is accepted as an alias of the canonical dashed name ({@code ML-DSA-87}).
 * <p/>
 * The parameter set determines the NIST security level of the generated key material. The stateful hash-based signature
 * algorithms (XMSS, XMSSMT, LMS, HSS) and MAYO/SNOVA have no name-addressable BouncyCastle parameter spec and are
 * therefore not supported here - for those, register a KeyPair/Signature/KeyGenerator bean in the registry instead.
 */
public final class PQCParameterSpecResolver {

    /**
     * The algorithms for which a parameter set can be selected by name.
     */
    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of(
            // Signature algorithms
            "MLDSA", "SLHDSA", "FALCON", "DILITHIUM", "SPHINCSPLUS", "PICNIC",
            // Key encapsulation algorithms
            "MLKEM", "KYBER", "NTRU", "NTRULPRime", "SNTRUPrime", "BIKE", "HQC", "CMCE", "FRODO", "SABER");

    private PQCParameterSpecResolver() {
    }

    /**
     * Whether a parameter set can be selected by name for the given algorithm.
     */
    public static boolean isSupported(String algorithm) {
        return algorithm != null && SUPPORTED_ALGORITHMS.contains(algorithm);
    }

    /**
     * Resolves the parameter spec for the given algorithm.
     *
     * @param  algorithm                the PQC signature or key encapsulation algorithm
     * @param  parameterSpec            the parameter-set name as defined by BouncyCastle, for example {@code ml_dsa_65}
     * @return                          the resolved parameter spec
     * @throws IllegalArgumentException if the algorithm has no name-addressable parameter set, or the name is unknown
     */
    public static AlgorithmParameterSpec resolve(String algorithm, String parameterSpec) {
        ObjectHelper.notNull(algorithm, "algorithm");
        ObjectHelper.notNull(parameterSpec, "parameterSpec");

        if (!isSupported(algorithm)) {
            throw new IllegalArgumentException(
                    "The parameterSpec option is not supported for algorithm " + algorithm
                                               + ". Supported algorithms are: " + SUPPORTED_ALGORITHMS
                                               + ". For the stateful signature algorithms (XMSS, XMSSMT, LMS, HSS) and"
                                               + " for MAYO/SNOVA, register a KeyPair bean in the registry instead.");
        }

        // BouncyCastle resolves parameter-set names case-insensitively but never uses underscores in them, so accept
        // the underscore form (ml_dsa_87), which matches the BouncyCastle constant names, as an alias.
        String name = parameterSpec.replace('_', '-');

        AlgorithmParameterSpec resolved;
        try {
            resolved = doResolve(algorithm, name);
        } catch (IllegalArgumentException e) {
            // The ML-DSA/ML-KEM/SLH-DSA specs throw for an unknown name
            throw new IllegalArgumentException(
                    "Unknown parameterSpec '" + parameterSpec + "' for algorithm " + algorithm, e);
        }
        if (resolved == null) {
            // The older BouncyCastle specs return null instead of throwing for an unknown name
            throw new IllegalArgumentException(
                    "Unknown parameterSpec '" + parameterSpec + "' for algorithm " + algorithm);
        }
        return resolved;
    }

    private static AlgorithmParameterSpec doResolve(String algorithm, String parameterSpec) {
        switch (algorithm) {
            // Signature algorithms
            case "MLDSA":
                return MLDSAParameterSpec.fromName(parameterSpec);
            case "SLHDSA":
                return SLHDSAParameterSpec.fromName(parameterSpec);
            case "FALCON":
                return FalconParameterSpec.fromName(parameterSpec);
            case "DILITHIUM":
                return DilithiumParameterSpec.fromName(parameterSpec);
            case "SPHINCSPLUS":
                return SPHINCSPlusParameterSpec.fromName(parameterSpec);
            case "PICNIC":
                return PicnicParameterSpec.fromName(parameterSpec);
            // Key encapsulation algorithms
            case "MLKEM":
                return MLKEMParameterSpec.fromName(parameterSpec);
            case "KYBER":
                return KyberParameterSpec.fromName(parameterSpec);
            case "NTRU":
                return NTRUParameterSpec.fromName(parameterSpec);
            case "NTRULPRime":
                return NTRULPRimeParameterSpec.fromName(parameterSpec);
            case "SNTRUPrime":
                return SNTRUPrimeParameterSpec.fromName(parameterSpec);
            case "BIKE":
                return BIKEParameterSpec.fromName(parameterSpec);
            case "HQC":
                return HQCParameterSpec.fromName(parameterSpec);
            case "CMCE":
                return CMCEParameterSpec.fromName(parameterSpec);
            case "FRODO":
                return FrodoParameterSpec.fromName(parameterSpec);
            case "SABER":
                return SABERParameterSpec.fromName(parameterSpec);
            default:
                // Guarded by isSupported above
                throw new IllegalStateException("Unsupported algorithm: " + algorithm);
        }
    }
}
