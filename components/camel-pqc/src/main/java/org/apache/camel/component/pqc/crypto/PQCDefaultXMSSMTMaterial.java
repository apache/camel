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
package org.apache.camel.component.pqc.crypto;

import java.security.*;

import org.apache.camel.component.pqc.BouncyCastlePqcCompatibility;
import org.apache.camel.component.pqc.BouncyCastleSupport;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;
import org.apache.camel.util.SecureRandomHelper;

public class PQCDefaultXMSSMTMaterial {
    private static KeyPair keyPair;
    private static Signature signer;

    private static boolean initialized;

    /** Initializes the default key material on first use and preserves the original failure as an exception. */
    public static synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }
        BouncyCastleSupport.ensureProviders();
        KeyPairGenerator generator;
        try {
            generator = prepareKeyPair();
            keyPair = generator.generateKeyPair();
            signer = Signature.getInstance(PQCSignatureAlgorithms.XMSSMT.getAlgorithm(),
                    PQCSignatureAlgorithms.XMSSMT.getBcProvider("Signature"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        initialized = true;
    }

    protected static KeyPairGenerator prepareKeyPair()
            throws GeneralSecurityException {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(PQCSignatureAlgorithms.XMSSMT.getAlgorithm(),
                PQCSignatureAlgorithms.XMSSMT.getBcProvider("KeyPairGenerator"));
        kpGen.initialize(BouncyCastlePqcCompatibility.xmssMtParameterSpec(20, 2, "SHA256"),
                SecureRandomHelper.getSecureRandom());
        return kpGen;
    }

    public static KeyPair getKeyPair() throws Exception {
        initialize();
        return keyPair;
    }

    public static Signature getSigner() throws Exception {
        initialize();
        return signer;
    }
}
