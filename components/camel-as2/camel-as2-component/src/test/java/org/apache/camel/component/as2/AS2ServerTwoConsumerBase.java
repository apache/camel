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
package org.apache.camel.component.as2;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.*;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

public class AS2ServerTwoConsumerBase extends AS2ServerSecTestBase {

    protected static final String AS2_TO_A = "AS2_SERVER_A";
    protected static final String AS2_TO_B = "AS2_SERVER_B";

    protected static final String DECRYPTING_KEY_A = "keyPairA";
    protected static final String DECRYPTING_KEY_B = "keyPairB";

    private KeyPair decryptingKPA;
    private KeyPair decryptingKPB;
    private X509Certificate signingCertA;
    private X509Certificate signingCertB;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // 1. Let the base class create the context instance
        CamelContext context = super.createCamelContext();

        // 2. Generate and assign distinct keys for Consumer A
        Object[] setA = generateNewKeyPairSet(AS2_TO_A);
        decryptingKPA = (KeyPair) setA[1];
        signingCertA = (X509Certificate) setA[2];

        // 3. Generate and assign distinct keys for Consumer B
        Object[] setB = generateNewKeyPairSet(AS2_TO_B);
        decryptingKPB = (KeyPair) setB[1];
        signingCertB = (X509Certificate) setB[2];

        // 4. Register private keys in the registry for the AS2 consumers to find.
        // The 'context' object is guaranteed to be non-null here.
        context.getRegistry().bind(DECRYPTING_KEY_A, decryptingKPA.getPrivate());
        context.getRegistry().bind(DECRYPTING_KEY_B, decryptingKPB.getPrivate());

        // 5. Return the configured context
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Define both consumers listening on the same component but different URIs
        return new RouteBuilder() {
            public void configure() {
                // Consumer A: Uses keys registered as 'keyPairA'
                from("as2://server/listen?requestUriPattern=/consumerA&decryptingPrivateKey=#" + DECRYPTING_KEY_A)
                        .to("mock:consumerA");

                // Consumer B: Uses keys registered as 'keyPairB'
                from("as2://server/listen?requestUriPattern=/consumerB&decryptingPrivateKey=#" + DECRYPTING_KEY_B)
                        .to("mock:consumerB");
            }
        };
    }

    protected HttpCoreContext sendToConsumerA(AS2MessageStructure structure) throws Exception {
        // For testing, we use Consumer A's signing key as the sender's key (spk)
        // and Consumer A's cert as the encryption cert (ec).
        return sendWithIsolatedKeys(
                structure,
                "/consumerA",
                AS2_TO_A,
                signingCertA,
                decryptingKPA.getPrivate(),
                signingCertA);
    }

    protected HttpCoreContext sendToConsumerB(AS2MessageStructure structure) throws Exception {
        // For testing, we use Consumer B's signing key as the sender's key (spk)
        // and Consumer B's cert as the encryption cert (ec).
        return sendWithIsolatedKeys(
                structure,
                "/consumerB",
                AS2_TO_B,
                signingCertB,
                decryptingKPB.getPrivate(),
                signingCertB);
    }

    protected HttpCoreContext sendWithIsolatedKeys(
            AS2MessageStructure structure,
            String targetUri,
            String as2To,
            X509Certificate signingCert,
            PrivateKey signingPrivateKey,
            X509Certificate encryptionCert)
            throws Exception {

        // This relies on the flexible send method (with requestUri, as2To, as2From params)
        // being present in AS2ServerSecTestBase
        return send(
                structure,
                targetUri,
                as2To,
                AS2_NAME, // AS2-From header can safely reuse the base's static AS2_NAME
                new Certificate[] { signingCert },
                signingPrivateKey,
                new Certificate[] { encryptionCert });
    }

    protected HttpCoreContext send(
            AS2MessageStructure structure,
            String requestUri,
            String as2To,
            String as2From,
            Certificate[] sc,
            PrivateKey spk,
            Certificate[] ec)
            throws Exception {

        // Use provided arguments or fall back to base class statics for non-overridden parts
        Certificate[] signingCertificate = sc == null ? new Certificate[] { this.signingCert } : sc;
        PrivateKey signingPrivateKey = spk == null ? this.signingKP.getPrivate() : spk;
        Certificate[] encryptingCertificate = ec == null ? new Certificate[] { this.signingCert } : ec;

        AS2SignatureAlgorithm signingAlgorithm = structure.isSigned() ? AS2SignatureAlgorithm.SHA256WITHRSA : null;
        signingCertificate = structure.isSigned() ? signingCertificate : null;
        signingPrivateKey = structure.isSigned() ? signingPrivateKey : null;
        AS2EncryptionAlgorithm encryptionAlgorithm = structure.isEncrypted() ? AS2EncryptionAlgorithm.AES128_CBC : null;
        encryptingCertificate = structure.isEncrypted() ? encryptingCertificate : null;
        AS2CompressionAlgorithm compressionAlgorithm = structure.isCompressed() ? AS2CompressionAlgorithm.ZLIB : null;

        return clientConnection().send(
                EDI_MESSAGE,
                requestUri,                 // Use argument
                SUBJECT,                    // Use static
                FROM,                       // Use static
                as2To,                      // Use argument
                as2From,                    // Use argument
                structure,
                ContentType.create(AS2MediaType.APPLICATION_EDIFACT, StandardCharsets.US_ASCII),
                null,
                signingAlgorithm,
                signingCertificate,
                signingPrivateKey,
                compressionAlgorithm,
                DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS,
                encryptionAlgorithm,
                encryptingCertificate,
                null,
                null);
    }

    protected Object[] generateNewKeyPairSet(String commonName) throws Exception {
        // set up our certificates
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=AS2 Test Issuer, C=US";
        KeyPair issueKeyPair = kpg.generateKeyPair();

        String signingDN = "CN=" + commonName + ", E=test@example.org, O=AS2 Test, C=US";
        KeyPair signingKeyPair = kpg.generateKeyPair();

        X509Certificate signingCert = Utils.makeCertificate(
                signingKeyPair,
                signingDN,
                issueKeyPair,
                issueDN);

        return new Object[] { issueKeyPair, signingKeyPair, signingCert };
    }

}
