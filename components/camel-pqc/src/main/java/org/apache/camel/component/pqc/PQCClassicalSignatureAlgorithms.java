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

/**
 * Classical (pre-quantum) signature algorithms for use in hybrid cryptography. These algorithms are combined with PQC
 * algorithms to provide defense-in-depth during the transition to post-quantum cryptography.
 */
public enum PQCClassicalSignatureAlgorithms {

    // ECDSA variants
    ECDSA_P256("SHA256withECDSA", "EC", "secp256r1"),
    ECDSA_P384("SHA384withECDSA", "EC", "secp384r1"),
    ECDSA_P521("SHA512withECDSA", "EC", "secp521r1"),

    // Edwards curve signatures
    ED25519("Ed25519", "Ed25519", null),
    ED448("Ed448", "Ed448", null),

    // RSA variants
    RSA_2048("SHA256withRSA", "RSA", "2048"),
    RSA_3072("SHA384withRSA", "RSA", "3072"),
    RSA_4096("SHA512withRSA", "RSA", "4096");

    private final String algorithm;
    private final String keyAlgorithm;
    private final String parameterSpec;

    PQCClassicalSignatureAlgorithms(String algorithm, String keyAlgorithm, String parameterSpec) {
        this.algorithm = algorithm;
        this.keyAlgorithm = keyAlgorithm;
        this.parameterSpec = parameterSpec;
    }

    /**
     * Gets the signature algorithm name (e.g., "SHA256withECDSA").
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Gets the key algorithm name (e.g., "EC", "RSA", "Ed25519").
     */
    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    /**
     * Gets the parameter specification (e.g., "secp256r1", "2048", or null for Ed25519/Ed448).
     */
    public String getParameterSpec() {
        return parameterSpec;
    }

    /**
     * Checks if this algorithm uses elliptic curves.
     */
    public boolean isEllipticCurve() {
        return "EC".equals(keyAlgorithm) || "Ed25519".equals(keyAlgorithm) || "Ed448".equals(keyAlgorithm);
    }

    /**
     * Checks if this algorithm uses RSA.
     */
    public boolean isRSA() {
        return "RSA".equals(keyAlgorithm);
    }

    /**
     * Gets the key size in bits for RSA algorithms, or curve bit size for EC algorithms.
     */
    public int getKeySize() {
        if (parameterSpec == null) {
            if ("Ed25519".equals(keyAlgorithm)) {
                return 256;
            } else if ("Ed448".equals(keyAlgorithm)) {
                return 448;
            }
            return 0;
        }

        if (isRSA()) {
            return Integer.parseInt(parameterSpec);
        }

        return switch (parameterSpec) {
            case "secp256r1" -> 256;
            case "secp384r1" -> 384;
            case "secp521r1" -> 521;
            default -> 0;
        };
    }
}
