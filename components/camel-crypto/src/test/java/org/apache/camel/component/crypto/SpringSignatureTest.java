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
package org.apache.camel.component.crypto;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringSignatureTest extends SignatureTest {

    private static KeyPair rsaPair;
    private static KeyPair dsaPair;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        rsaPair = getKeyPair("RSA");
        dsaPair = getKeyPair("DSA");
        return SpringCamelContext.springCamelContext(new ClassPathXmlApplicationContext("org/apache/camel/component/crypto/SpringSignatureTest.xml"), true);
    }

    public static KeyStore keystore() throws Exception {
        return loadKeystore();
    }

    public static PrivateKey privateKeyFromKeystore() throws Exception {
        return new SignatureTest().getKeyFromKeystore();
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

    public static PrivateKey privateDSAKey() throws Exception {
        return dsaPair.getPrivate();
    }

    public static PublicKey publicRSAKey() throws Exception {
        return rsaPair.getPublic();
    }

    public static PublicKey publicDSAKey() throws Exception {
        return dsaPair.getPublic();
    }

    public static SecureRandom random() throws Exception {
        return new SecureRandom();
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {};
    }
}
