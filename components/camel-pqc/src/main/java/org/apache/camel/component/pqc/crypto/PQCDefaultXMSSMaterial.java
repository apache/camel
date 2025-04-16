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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Signature;

import org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec;

public class PQCDefaultXMSSMaterial {
    public static final KeyPair keyPair;
    public static final Signature signer;

    static {
        KeyPairGenerator generator;
        try {
            generator = prepareKeyPair();
            keyPair = generator.generateKeyPair();
            signer = Signature.getInstance("XMSS");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static KeyPairGenerator prepareKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("XMSS", "BCPQC");
        kpGen.initialize(new XMSSParameterSpec(10, XMSSParameterSpec.SHA256), new SecureRandom());
        return kpGen;
    }
}
