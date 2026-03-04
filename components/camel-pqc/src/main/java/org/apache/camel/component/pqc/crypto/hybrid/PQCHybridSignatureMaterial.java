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

import java.security.KeyPair;
import java.security.Signature;

/**
 * Container class for hybrid signature cryptographic material. Holds both classical and PQC key pairs and signature
 * instances needed for hybrid signature operations.
 */
public class PQCHybridSignatureMaterial {

    private final KeyPair classicalKeyPair;
    private final Signature classicalSigner;
    private final KeyPair pqcKeyPair;
    private final Signature pqcSigner;
    private final String classicalAlgorithm;
    private final String pqcAlgorithm;

    /**
     * Creates a new hybrid signature material.
     *
     * @param classicalKeyPair   the classical key pair (e.g., ECDSA, RSA)
     * @param classicalSigner    the classical signature instance
     * @param pqcKeyPair         the PQC key pair (e.g., ML-DSA, Dilithium)
     * @param pqcSigner          the PQC signature instance
     * @param classicalAlgorithm the classical algorithm name
     * @param pqcAlgorithm       the PQC algorithm name
     */
    public PQCHybridSignatureMaterial(
                                      KeyPair classicalKeyPair,
                                      Signature classicalSigner,
                                      KeyPair pqcKeyPair,
                                      Signature pqcSigner,
                                      String classicalAlgorithm,
                                      String pqcAlgorithm) {
        this.classicalKeyPair = classicalKeyPair;
        this.classicalSigner = classicalSigner;
        this.pqcKeyPair = pqcKeyPair;
        this.pqcSigner = pqcSigner;
        this.classicalAlgorithm = classicalAlgorithm;
        this.pqcAlgorithm = pqcAlgorithm;
    }

    public KeyPair getClassicalKeyPair() {
        return classicalKeyPair;
    }

    public Signature getClassicalSigner() {
        return classicalSigner;
    }

    public KeyPair getPqcKeyPair() {
        return pqcKeyPair;
    }

    public Signature getPqcSigner() {
        return pqcSigner;
    }

    public String getClassicalAlgorithm() {
        return classicalAlgorithm;
    }

    public String getPqcAlgorithm() {
        return pqcAlgorithm;
    }

    /**
     * Returns a description of this hybrid material.
     */
    public String getDescription() {
        return classicalAlgorithm + " + " + pqcAlgorithm;
    }
}
