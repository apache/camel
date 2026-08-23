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
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the Java 25+ JKS KeyStore key conversion fix.
 * <p>
 * On Java 25+, JKS KeyStore deserialises ML-DSA keys as JDK-native objects (via JEP 497) rather than Bouncy Castle
 * objects. BC's Signature SPI does not recognise the JDK-native key types and throws {@link InvalidKeyException
 * InvalidKeyException: unknown private key passed to ML-DSA}. The fix in {@code PQCProducer.ensureBcKeyPair()}
 * re-encodes such keys through BC's {@link KeyFactory} transparently.
 * <p>
 * This test is only meaningful on Java 25+ where the JDK provides a native ML-DSA {@link KeyFactory}. On earlier JVMs,
 * JKS always returns BC key objects and the conversion is a no-op.
 */
@EnabledForJreRange(min = JRE.JAVA_25)
class PQCKeyStoreJdk25KeyConversionTest extends CamelTestSupport {

    private static final String KEYSTORE_FILE = "keystore-jdk25-test.jks";

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @EndpointInject("mock:verify")
    protected MockEndpoint resultVerify;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    PQCKeyStoreJdk25KeyConversionTest() throws NoSuchAlgorithmException {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sign")
                        .to("pqc:sign?operation=sign&keyPairAlias=mykey&keyStorePassword=changeit")
                        .to("mock:sign")
                        .to("pqc:verify?operation=verify&keyPairAlias=mykey&keyStorePassword=changeit")
                        .to("mock:verify");
            }
        };
    }

    @BeforeAll
    static void startup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @AfterAll
    static void teardown() throws Exception {
        Files.deleteIfExists(Path.of(KEYSTORE_FILE));
    }

    /**
     * Verifies that ML-DSA sign + verify works via a JKS KeyStore on Java 25+, where retrieved keys are JDK-native and
     * must be converted to BC types by PQCProducer.
     */
    @Test
    void testSignAndVerifyWithJdkNativeKeysFromKeyStore() throws Exception {
        resultSign.expectedMessageCount(1);
        resultVerify.expectedMessageCount(1);
        templateSign.sendBody("Hello from Java 25");
        resultSign.assertIsSatisfied();
        resultVerify.assertIsSatisfied();
        assertThat(resultVerify.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class))
                .as("Signature verification should succeed after JDK-native key conversion")
                .isTrue();
    }

    @BindToRegistry("Keystore")
    public KeyStore setKeyStore()
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
        keyStore.setKeyEntry("mykey", kp.getPrivate(), password, new Certificate[] { certificate });

        // Save keystore to file
        try (FileOutputStream fos = new FileOutputStream(KEYSTORE_FILE)) {
            keyStore.store(fos, password);
        }
        return keyStore;
    }

    @BindToRegistry("Signer")
    public Signature getSigner() throws NoSuchAlgorithmException, NoSuchProviderException {
        return Signature.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
    }
}
