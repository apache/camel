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

public enum PQCKeyEncapsulationAlgorithms {

    // Standardized and implemented
    MLKEM("ML-KEM", "BC"),

    // Experimental and non-standardized
    BIKE("BIKE", "BCPQC"),
    HQC("HQC", "BCPQC"),
    CMCE("CMCE", "BCPQC"),
    SABER("SABER", "BCPQC"),
    FRODO("FRODO", "BCPQC"),
    NTRU("NTRU", "BCPQC"),
    NTRULPRime("NTRULPRime", "BCPQC");

    private final String algorithm;
    private final String bcProvider;

    PQCKeyEncapsulationAlgorithms(String algorithm, String bcProvider) {
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
