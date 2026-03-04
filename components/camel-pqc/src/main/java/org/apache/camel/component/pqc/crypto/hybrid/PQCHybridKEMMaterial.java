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

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;

/**
 * Container class for hybrid KEM cryptographic material. Holds both classical key agreement and PQC KEM materials
 * needed for hybrid key encapsulation operations.
 */
public class PQCHybridKEMMaterial {

    private final KeyPair classicalKeyPair;
    private final KeyAgreement classicalKeyAgreement;
    private final KeyPair pqcKeyPair;
    private final KeyGenerator pqcKeyGenerator;
    private final String classicalAlgorithm;
    private final String pqcAlgorithm;

    /**
     * Creates a new hybrid KEM material.
     *
     * @param classicalKeyPair      the classical key pair (e.g., ECDH, X25519)
     * @param classicalKeyAgreement the classical key agreement instance
     * @param pqcKeyPair            the PQC key pair (e.g., ML-KEM)
     * @param pqcKeyGenerator       the PQC KEM key generator
     * @param classicalAlgorithm    the classical algorithm name
     * @param pqcAlgorithm          the PQC algorithm name
     */
    public PQCHybridKEMMaterial(
                                KeyPair classicalKeyPair,
                                KeyAgreement classicalKeyAgreement,
                                KeyPair pqcKeyPair,
                                KeyGenerator pqcKeyGenerator,
                                String classicalAlgorithm,
                                String pqcAlgorithm) {
        this.classicalKeyPair = classicalKeyPair;
        this.classicalKeyAgreement = classicalKeyAgreement;
        this.pqcKeyPair = pqcKeyPair;
        this.pqcKeyGenerator = pqcKeyGenerator;
        this.classicalAlgorithm = classicalAlgorithm;
        this.pqcAlgorithm = pqcAlgorithm;
    }

    public KeyPair getClassicalKeyPair() {
        return classicalKeyPair;
    }

    public KeyAgreement getClassicalKeyAgreement() {
        return classicalKeyAgreement;
    }

    public KeyPair getPqcKeyPair() {
        return pqcKeyPair;
    }

    public KeyGenerator getPqcKeyGenerator() {
        return pqcKeyGenerator;
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
