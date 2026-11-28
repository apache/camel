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

/**
 * Resolves the BouncyCastle {@link AlgorithmParameterSpec} for a PQC algorithm from the parameter-set name configured
 * on the endpoint, for example {@code ML-DSA-87} for {@code MLDSA} or {@code ML-KEM-1024} for {@code MLKEM}.
 * <p/>
 * Names are resolved case-insensitively, and the underscore form used by the BouncyCastle constants ({@code ml_dsa_87})
 * is accepted as an alias of the canonical dashed name ({@code ML-DSA-87}).
 * <p/>
 * The parameter set determines the NIST security level of the generated key material. The stateful hash-based signature
 * algorithms (XMSS, XMSSMT, LMS, HSS) have no name-addressable BouncyCastle parameter spec and are therefore not
 * supported here - for those, register a KeyPair/Signature/KeyGenerator bean in the registry instead.
 */
public final class PQCParameterSpecResolver {

    /**
     * The algorithms for which a parameter set can be selected by name.
     */
    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of(
            // Signature algorithms
            "MLDSA", "SLHDSA", "FALCON", "DILITHIUM", "SPHINCSPLUS", "MAYO", "SNOVA",
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
                                               + ". For the stateful signature algorithms (XMSS, XMSSMT, LMS, HSS),"
                                               + " register a KeyPair bean in the registry instead.");
        }

        // The name is tried as written first. Most BouncyCastle specs spell their parameter sets with dashes and
        // resolve the underscore form only through the alias below, but the SNOVA names keep their underscores
        // (SNOVA_24_5_5_SHAKE_SSK) and do not resolve in dashed form at all, so rewriting first would make every
        // SNOVA parameter set unreachable.
        AlgorithmParameterSpec resolved = tryResolve(algorithm, parameterSpec);

        if (resolved == null && parameterSpec.indexOf('_') >= 0) {
            // Accept the underscore form of a dashed name (ml_dsa_87 for ML-DSA-87), which matches how the
            // BouncyCastle constants are written
            resolved = tryResolve(algorithm, parameterSpec.replace('_', '-'));
        }

        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Unknown parameterSpec '" + parameterSpec + "' for algorithm " + algorithm);
        }
        return resolved;
    }

    /**
     * Resolves one spelling of a parameter-set name, answering {@code null} when it is not known. Some BouncyCastle
     * specs throw for an unknown name and others return {@code null}, so both are normalised here.
     */
    private static AlgorithmParameterSpec tryResolve(String algorithm, String parameterSpec) {
        try {
            return doResolve(algorithm, parameterSpec);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AlgorithmParameterSpec doResolve(String algorithm, String parameterSpec) {
        return BouncyCastlePqcCompatibility.namedParameterSpec(algorithm, parameterSpec);
    }

}
