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
package org.apache.camel.component.pqc.crypto.kem;

import java.security.*;

import javax.crypto.KeyGenerator;

import org.apache.camel.component.pqc.BouncyCastleSupport;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCParameterSpecResolver;
import org.apache.camel.util.SecureRandomHelper;

public class PQCDefaultBIKEMaterial {

    private static KeyPair keyPair;
    private static KeyGenerator keyGenerator;
    private static KeyPairGenerator generator;

    private static boolean initialized;

    /** Initializes the default key material on first use and preserves the original failure as an exception. */
    public static synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }
        BouncyCastleSupport.ensureProviders();
        try {
            generator = prepareKeyPair();
            keyPair = generator.generateKeyPair();
            keyGenerator = prepareKeyGenerator();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        initialized = true;
    }

    protected static KeyPairGenerator prepareKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(PQCKeyEncapsulationAlgorithms.BIKE.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.BIKE.getBcProvider("KeyPairGenerator"));
        kpg.initialize(PQCParameterSpecResolver.resolve("BIKE", "bike192"), SecureRandomHelper.getSecureRandom());
        return kpg;
    }

    protected static KeyGenerator prepareKeyGenerator() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyGenerator kg = KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.BIKE.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.BIKE.getBcProvider("KeyGenerator"));
        return kg;
    }

    public static KeyPair getKeyPair() throws Exception {
        initialize();
        return keyPair;
    }

    public static KeyGenerator getKeyGenerator() throws Exception {
        initialize();
        return keyGenerator;
    }

    public static KeyPairGenerator getGenerator() throws Exception {
        initialize();
        return generator;
    }
}
