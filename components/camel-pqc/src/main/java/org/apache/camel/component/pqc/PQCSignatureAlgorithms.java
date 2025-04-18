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
    MLDSA("ML-DSA", "BC"),
    SLHDSA("SLH-DSA", "BC"),
    LMS("LMS", "BC"),
    XMSS("XMSS", "BCPQC"),

    // Experimental and non-standardized
    FALCON("FALCON", "BCPQC"),
    PICNIC("PICNIC", "BCPQC"),
    RAINBOW("RAINBOW", "BCPQC");

    private final String algorithm;
    private final String bcProvider;

    PQCSignatureAlgorithms(String algorithm, String bcProvider) {
        this.algorithm = algorithm;
        this.bcProvider = bcProvider;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getBcProvider() {
        return bcProvider;
    }

}
