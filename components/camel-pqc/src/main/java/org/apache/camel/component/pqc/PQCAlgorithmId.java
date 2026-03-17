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

import java.util.HashMap;
import java.util.Map;

/**
 * Algorithm identifiers for PQC wire format v2. Each algorithm is assigned a unique 16-bit identifier used in the wire
 * format header to make hybrid cryptographic outputs self-describing.
 *
 * <p>
 * Algorithm ID ranges:
 * <ul>
 * <li>{@code 0x0100-0x01FF} — Classical signature algorithms</li>
 * <li>{@code 0x0200-0x02FF} — Post-quantum signature algorithms</li>
 * <li>{@code 0x0300-0x03FF} — Classical KEM / key agreement algorithms</li>
 * <li>{@code 0x0400-0x04FF} — Post-quantum KEM algorithms</li>
 * <li>{@code 0x0500-0x05FF} — Symmetric encryption algorithms</li>
 * </ul>
 */
public enum PQCAlgorithmId {

    // Unknown / unrecognized
    UNKNOWN(0x0000, "Unknown"),

    // Classical signature algorithms (0x0100-0x01FF)
    SHA256_WITH_ECDSA(0x0101, "SHA256withECDSA"),
    SHA384_WITH_ECDSA(0x0102, "SHA384withECDSA"),
    SHA512_WITH_ECDSA(0x0103, "SHA512withECDSA"),
    ED25519(0x0104, "Ed25519"),
    ED448(0x0105, "Ed448"),
    SHA256_WITH_RSA(0x0106, "SHA256withRSA"),
    SHA384_WITH_RSA(0x0107, "SHA384withRSA"),
    SHA512_WITH_RSA(0x0108, "SHA512withRSA"),

    // PQC signature algorithms (0x0200-0x02FF)
    ML_DSA(0x0201, "ML-DSA"),
    SLH_DSA(0x0202, "SLH-DSA"),
    LMS(0x0203, "LMS"),
    XMSS(0x0204, "XMSS"),
    XMSSMT(0x0205, "XMSSMT"),
    DILITHIUM(0x0206, "DILITHIUM"),
    FALCON(0x0207, "FALCON"),
    SPHINCSPLUS(0x0208, "SPHINCSPLUS"),
    PICNIC(0x0209, "PICNIC"),
    MAYO(0x020A, "Mayo"),
    SNOVA(0x020B, "Snova"),

    // Classical KEM / key agreement algorithms (0x0300-0x03FF)
    EC(0x0301, "EC"),
    ECDH(0x0302, "ECDH"),
    X25519(0x0303, "X25519"),
    XDH(0x0304, "XDH"),
    X448(0x0305, "X448"),

    // PQC KEM algorithms (0x0400-0x04FF)
    ML_KEM(0x0401, "ML-KEM"),
    BIKE(0x0402, "BIKE"),
    HQC(0x0403, "HQC"),
    CMCE(0x0404, "CMCE"),
    SABER(0x0405, "SABER"),
    FRODO(0x0406, "FRODO"),
    NTRU(0x0407, "NTRU"),
    NTRU_LPRIME(0x0408, "NTRULPRime"),
    SNTRU_PRIME(0x0409, "SNTRUPrime"),
    KYBER(0x040A, "KYBER"),

    // Symmetric encryption algorithms (0x0500-0x05FF)
    AES(0x0501, "AES"),
    ARIA(0x0502, "ARIA"),
    CAMELLIA(0x0503, "Camellia"),
    CAST5(0x0504, "CAST5"),
    CAST6(0x0505, "CAST6"),
    CHACHA7539(0x0506, "CHACHA7539"),
    DESEDE(0x0507, "DESede"),
    RC2(0x0508, "RC2"),
    RC5(0x0509, "RC5"),
    SEED(0x050A, "SEED"),
    SM4(0x050B, "SM4");

    private static final Map<Integer, PQCAlgorithmId> BY_ID = new HashMap<>();
    private static final Map<String, PQCAlgorithmId> BY_JCA_NAME = new HashMap<>();

    static {
        for (PQCAlgorithmId alg : values()) {
            BY_ID.put(alg.id, alg);
            BY_JCA_NAME.put(alg.jcaName.toLowerCase(), alg);
        }
    }

    private final int id;
    private final String jcaName;

    PQCAlgorithmId(int id, String jcaName) {
        this.id = id;
        this.jcaName = jcaName;
    }

    /**
     * Returns the 16-bit algorithm identifier used in the wire format.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the JCA algorithm name associated with this identifier.
     */
    public String getJcaName() {
        return jcaName;
    }

    /**
     * Looks up an algorithm identifier by its 16-bit wire format ID.
     *
     * @param  id the wire format identifier
     * @return    the matching algorithm, or {@link #UNKNOWN} if not found
     */
    public static PQCAlgorithmId fromId(int id) {
        return BY_ID.getOrDefault(id, UNKNOWN);
    }

    /**
     * Looks up an algorithm identifier by JCA algorithm name (case-insensitive).
     *
     * @param  jcaName the JCA algorithm name (e.g., "ML-DSA", "SHA256withECDSA", "AES")
     * @return         the matching algorithm, or {@link #UNKNOWN} if not found
     */
    public static PQCAlgorithmId fromJcaName(String jcaName) {
        if (jcaName == null) {
            return UNKNOWN;
        }
        return BY_JCA_NAME.getOrDefault(jcaName.toLowerCase(), UNKNOWN);
    }
}
