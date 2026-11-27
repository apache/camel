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

public enum PQCSignatureAlgorithms {

    // Standardized and implemented
    MLDSA("ML-DSA"),
    SLHDSA("SLH-DSA"),
    LMS("LMS"),
    HSS("LMS"),
    XMSS("XMSS"),
    XMSSMT("XMSSMT"),
    DILITHIUM("ML-DSA"),

    // Experimental and non-standardized
    FALCON("FALCON"),
    SNOVA("Snova"),
    MAYO("Mayo"),
    SPHINCSPLUS("SLH-DSA");

    private final String algorithm;

    PQCSignatureAlgorithms(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /** Resolves the provider which implements the requested JCA service for this algorithm. */
    public String getBcProvider(String serviceType) {
        try {
            return BouncyCastleSupport.getProviderName(serviceType, algorithm);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No Bouncy Castle provider for " + serviceType + "/" + algorithm, e);
        }
    }

    /** @deprecated use {@link #getBcProvider(String)} with the JCA service being requested. */
    @Deprecated
    public String getBcProvider() {
        return getBcProvider("KeyPairGenerator");
    }

}
