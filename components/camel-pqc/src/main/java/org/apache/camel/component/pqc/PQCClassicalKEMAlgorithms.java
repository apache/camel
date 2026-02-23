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
 * Classical (pre-quantum) key encapsulation/agreement algorithms for use in hybrid cryptography. These algorithms are
 * combined with PQC KEM algorithms to provide defense-in-depth during the transition to post-quantum cryptography.
 */
public enum PQCClassicalKEMAlgorithms {

    // ECDH variants
    ECDH_P256("ECDH", "secp256r1", 256),
    ECDH_P384("ECDH", "secp384r1", 384),
    ECDH_P521("ECDH", "secp521r1", 521),

    // X25519/X448 key agreement
    X25519("X25519", null, 256),
    X448("X448", null, 448);

    private final String algorithm;
    private final String curveSpec;
    private final int keySize;

    PQCClassicalKEMAlgorithms(String algorithm, String curveSpec, int keySize) {
        this.algorithm = algorithm;
        this.curveSpec = curveSpec;
        this.keySize = keySize;
    }

    /**
     * Gets the key agreement algorithm name (e.g., "ECDH", "X25519").
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Gets the curve specification (e.g., "secp256r1", or null for X25519/X448).
     */
    public String getCurveSpec() {
        return curveSpec;
    }

    /**
     * Gets the key size in bits.
     */
    public int getKeySize() {
        return keySize;
    }

    /**
     * Checks if this algorithm uses standard ECDH with named curves.
     */
    public boolean isECDH() {
        return "ECDH".equals(algorithm);
    }

    /**
     * Checks if this algorithm uses X25519 or X448.
     */
    public boolean isXDH() {
        return "X25519".equals(algorithm) || "X448".equals(algorithm);
    }

    /**
     * Gets the key algorithm for key pair generation.
     */
    public String getKeyAlgorithm() {
        if (isXDH()) {
            return algorithm;
        }
        return "EC";
    }

    /**
     * Gets the expected shared secret size in bytes.
     */
    public int getSharedSecretSize() {
        return switch (this) {
            case X25519 -> 32;
            case X448 -> 56;
            case ECDH_P256 -> 32;
            case ECDH_P384 -> 48;
            case ECDH_P521 -> 66;
        };
    }
}
