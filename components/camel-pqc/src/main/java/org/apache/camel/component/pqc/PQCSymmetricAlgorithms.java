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

public enum PQCSymmetricAlgorithms {

    // Standardized and implemented
    AES("AES"),
    RC2("RC2"),
    RC5("RC5"),
    ARIA("ARIA"),
    CAMELLIA("CAMELLIA"),
    CAST5("CAST5"),
    CAST6("CAST6"),
    CHACHA7539("CHACHA7539"),
    DSTU7624("DSTU7624"),
    GOST28147("GOST28147"),
    GOST3412_2015("GOST3412-2015"),
    GRAIN128("GRAIN128"),
    HC128("HC128"),
    HC256("HC256"),
    SALSA20("SALSA20"),
    SEED("SEED"),
    SM4("SM4"),
    DESEDE("DESede");

    private final String algorithm;

    PQCSymmetricAlgorithms(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Maps a configured symmetric algorithm name to the JCE name a {@link javax.crypto.Cipher} resolves.
     * <p/>
     * Two constants spell the two differently - {@code GOST3412_2015} is {@code GOST3412-2015} and {@code DESEDE} is
     * {@code DESede} - so a key labelled with the constant name cannot be handed to {@code Cipher.getInstance}. A name
     * that is not one of the constants, which includes a JCE name that has already been mapped, is answered unchanged
     * rather than rejected, so this is safe to apply to a value of unknown provenance.
     *
     * @param  name the configured name, or a JCE name
     * @return      the JCE name, or {@code name} when it is not one of the constants
     */
    public static String toJceName(String name) {
        if (name == null) {
            return null;
        }
        for (PQCSymmetricAlgorithms candidate : values()) {
            if (candidate.name().equals(name)) {
                return candidate.getAlgorithm();
            }
        }
        return name;
    }

}
