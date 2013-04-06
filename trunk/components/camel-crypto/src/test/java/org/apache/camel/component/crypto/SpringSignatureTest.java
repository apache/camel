/**
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
package org.apache.camel.component.crypto;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spring.SpringCamelContext;


public class SpringSignatureTest extends SignatureTests {

    private static KeyPair rsaPair;

    protected CamelContext createCamelContext() throws Exception {
        rsaPair = getKeyPair("RSA");
        return SpringCamelContext.springCamelContext("org/apache/camel/component/crypto/SpringSignatureTests.xml");
    }

    public static KeyStore keystore() throws Exception {
        return loadKeystore();
    }

    public static PrivateKey privateKeyFromKeystore() throws Exception {
        return new SignatureTests().getKeyFromKeystore();
    }

    public static Certificate certificateFromKeystore() throws Exception {
        KeyStore keystore = loadKeystore();
        return keystore.getCertificate("bob");
    }

    public static PrivateKey privateKey() throws Exception {
        KeyStore keystore = loadKeystore();
        return (PrivateKey)keystore.getKey("bob", "letmein".toCharArray());
    }

    public static PublicKey publicKey() throws Exception {
        KeyStore keystore = loadKeystore();
        Certificate cert = keystore.getCertificate("bob");
        return cert.getPublicKey();
    }

    public static PrivateKey privateRSAKey() throws Exception {
        return rsaPair.getPrivate();
    }

    public static PublicKey publicRSAKey() throws Exception {
        return rsaPair.getPublic();
    }

    public static SecureRandom random() throws Exception {
        return new SecureRandom();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        return super.createRegistry();
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {};
    }
}
