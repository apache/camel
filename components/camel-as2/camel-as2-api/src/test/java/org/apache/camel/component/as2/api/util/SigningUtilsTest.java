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
package org.apache.camel.component.as2.api.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.Utils;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SigningUtilsTest {

    private static KeyPair signingKP;
    private static X509Certificate signingCert;
    private static X509Certificate evilSigningCert;
    private static final String MESSAGE = "Test message to be signed";

    @BeforeEach
    public void setUp() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        setupKeysAndCertificates();
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void createSigningGeneratorTest() throws Exception {
        AS2SignedDataGenerator gen = SigningUtils.createSigningGenerator(AS2SignatureAlgorithm.SHA1WITHRSA,
                new Certificate[] { signingCert }, signingKP.getPrivate());
        CMSProcessableByteArray sData = new CMSProcessableByteArray(MESSAGE.getBytes(StandardCharsets.UTF_8));
        CMSSignedData signedData = gen.generate(sData, true);

        assertTrue(signedData.verifySignatures((SignerId sid) -> {
            return new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signingCert);
        }), "Message was wrongly signed");
    }

    @Test
    public void isValidSignedTest() throws Exception {
        AS2SignedDataGenerator gen = SigningUtils.createSigningGenerator(AS2SignatureAlgorithm.SHA1WITHRSA,
                new Certificate[] { signingCert }, signingKP.getPrivate());
        CMSProcessableByteArray sData = new CMSProcessableByteArray(MESSAGE.getBytes(StandardCharsets.UTF_8));
        CMSSignedData signedData = gen.generate(sData, true);
        assertTrue(SigningUtils.isValidSigned(MESSAGE.getBytes(StandardCharsets.UTF_8), signedData.getEncoded(),
                new Certificate[] { signingCert }), "Message must be valid");
        assertFalse(SigningUtils.isValidSigned(MESSAGE.getBytes(StandardCharsets.UTF_8), signedData.getEncoded(),
                new Certificate[] { evilSigningCert }), "Message must be invalid");
    }

    private static void setupKeysAndCertificates() throws Exception {
        //
        // set up our certificates
        //
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(1024, new SecureRandom());
        //
        // certificate we sign against
        //
        {
            String signingDN = "O=AS2 Test Orgaisation, C=US";
            signingKP = kpg.generateKeyPair();
            signingCert = Utils.makeCertificate(
                    signingKP, signingDN, signingKP, signingDN);
        }

        //
        // certificate some else signed against
        //
        {
            String evilSigningDN = "O=Evil Haxor Coorp, C=RU";
            KeyPair evilSigningKP = kpg.generateKeyPair();
            evilSigningCert = Utils.makeCertificate(
                    evilSigningKP, evilSigningDN, evilSigningKP, evilSigningDN);
        }
    }

}
