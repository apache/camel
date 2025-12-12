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

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PQCSignatureWithKeyStoreTest extends CamelTestSupport {

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @EndpointInject("mock:verify")
    protected MockEndpoint resultVerify;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    public PQCSignatureWithKeyStoreTest() throws NoSuchAlgorithmException {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sign").to("pqc:sign?operation=sign").to("mock:sign").to("pqc:verify?operation=verify")
                        .to("mock:verify");
            }
        };
    }

    @BeforeAll
    public static void startup() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
    }

    @AfterAll
    public static void teardown() throws Exception {
        Files.deleteIfExists(Path.of("keystore.jks"));
    }

    @Test
    void testSignAndVerify() throws Exception {
        resultSign.expectedMessageCount(1);
        resultVerify.expectedMessageCount(1);
        templateSign.sendBody("Hello");
        resultSign.assertIsSatisfied();
        resultVerify.assertIsSatisfied();
        assertTrue(resultVerify.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class));
    }

    @BindToRegistry("Keypair")
    public KeyPair setKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, KeyStoreException,
            CertificateException, IOException, OperatorCreationException, UnrecoverableKeyException {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
        kpGen.initialize(MLDSAParameterSpec.ml_dsa_65);
        KeyPair kp = kpGen.generateKeyPair();

        // Validity
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + 365L * 24 * 60 * 60 * 1000); // 1 year

        // Serial Number
        BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());

        X500Name dnName = new X500Name("CN=Test User");
        // Build the certificate
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName,
                serialNumber,
                startDate,
                endDate,
                dnName,
                kp.getPublic());

        ContentSigner contentSigner = new JcaContentSignerBuilder(PQCSignatureAlgorithms.MLDSA.getAlgorithm())
                .setProvider(PQCSignatureAlgorithms.MLDSA.getBcProvider())
                .build(kp.getPrivate());

        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(contentSigner));

        KeyStore keyStore = KeyStore.getInstance("JKS");
        char[] password = "changeit".toCharArray();
        keyStore.load(null, password); // initialize new keystore
        keyStore.setKeyEntry("mykey", kp.getPrivate(), password, new java.security.cert.Certificate[] { certificate });

        // Save keystore to file
        try (FileOutputStream fos = new FileOutputStream("keystore.jks")) {
            keyStore.store(fos, password);
        }

        // Retrieve the Private Key
        String alias = "mykey";
        Key key = keyStore.getKey(alias, password);
        if (!(key instanceof PrivateKey)) {
            throw new RuntimeException("Key under alias is not a private key");
        }
        PrivateKey privateKey = (PrivateKey) key;

        Certificate cert = keyStore.getCertificate(alias);
        if (cert == null) {
            throw new RuntimeException("No certificate found for alias: " + alias);
        }
        PublicKey publicKey = cert.getPublicKey();
        return new KeyPair(publicKey, privateKey);
    }

    @BindToRegistry("Signer")
    public Signature getSigner() throws NoSuchAlgorithmException, NoSuchProviderException {
        Signature mlDsa = Signature.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
        return mlDsa;
    }
}
