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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.crypto.hybrid.HybridSignature;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link File}, a {@link Path} and a file from a file consumer ({@link GenericFile}) are signed and verified over the
 * bytes of the file, by the single-algorithm and by the hybrid signature operations.
 */
public class PQCFileBodySignatureTest extends CamelTestSupport {

    private static final String PQC_KEY = "&signatureAlgorithm=MLDSA&keyPair=#pqcKeyPair";
    private static final String CLASSICAL_KEY = "&classicalSignatureAlgorithm=ECDSA_P256&classicalKeyPair=#classicalKeyPair";
    private static final String SIGN = "pqc:sign?operation=sign" + PQC_KEY;
    private static final String VERIFY = "pqc:verify?operation=verify" + PQC_KEY;

    private static final byte[] CONTENT = binaryContent();

    private static KeyPair pqcKeyPair;
    private static KeyPair classicalKeyPair;

    @TempDir
    Path folder;

    @EndpointInject("mock:consumed")
    MockEndpoint consumed;

    @BeforeAll
    public static void startup() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }

        KeyPairGenerator pqc = KeyPairGenerator.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
        pqc.initialize(MLDSAParameterSpec.ml_dsa_65);
        pqcKeyPair = pqc.generateKeyPair();

        KeyPairGenerator classical
                = KeyPairGenerator.getInstance(PQCClassicalSignatureAlgorithms.ECDSA_P256.getKeyAlgorithm());
        classical.initialize(new ECGenParameterSpec(PQCClassicalSignatureAlgorithms.ECDSA_P256.getParameterSpec()));
        classicalKeyPair = classical.generateKeyPair();
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("pqcKeyPair", pqcKeyPair);
        registry.bind("classicalKeyPair", classicalKeyPair);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sign").to(SIGN);
                from("direct:verify").to(VERIFY);
                from("direct:hybridSign").to("pqc:hybridSign?operation=hybridSign" + PQC_KEY + CLASSICAL_KEY);
                from("direct:hybridVerify").to("pqc:hybridVerify?operation=hybridVerify" + PQC_KEY + CLASSICAL_KEY);

                from("file:" + folder + "?noop=true&initialDelay=0&delay=10").routeId("fileConsumer").autoStartup(false)
                        .to(SIGN)
                        .to(VERIFY)
                        .to("mock:consumed");
            }
        };
    }

    @Test
    void fileBodyIsSignedOverItsBytes() throws Exception {
        File file = write("signed.dat").toFile();

        assertTrue(verifiesContent(sign(file)), "the signature must cover the bytes of the file");
    }

    @Test
    void pathBodyIsSignedOverItsBytes() throws Exception {
        Path path = write("signed.dat");

        assertTrue(verifiesContent(sign(path)), "the signature must cover the bytes of the file");
    }

    @Test
    void fileBodyIsVerifiedOverItsBytes() throws Exception {
        File file = write("verified.dat").toFile();

        assertTrue(verify(file, signContent()), "a signature over the bytes of the file must verify");
    }

    @Test
    void pathBodyIsVerifiedOverItsBytes() throws Exception {
        Path path = write("verified.dat");

        assertTrue(verify(path, signContent()), "a signature over the bytes of the file must verify");
    }

    @Test
    void fileFromFileConsumerIsSignedAndVerifiedOverItsBytes() throws Exception {
        write("consumed.dat");
        consumed.expectedMessageCount(1);

        context.getRouteController().startRoute("fileConsumer");

        consumed.assertIsSatisfied();
        Exchange exchange = consumed.getReceivedExchanges().get(0);
        assertTrue(exchange.getMessage().getHeader(PQCConstants.VERIFY, Boolean.class));
        assertTrue(verifiesContent(exchange.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class)),
                "the signature must cover the bytes of the consumed file");
    }

    @Test
    void genericFileWithLoadedContentIsSignedOverItsBytes() throws Exception {
        // a file whose content the consumer has already loaded into memory
        GenericFile<Object> file = new GenericFile<>();
        file.setFileName("loaded.dat");
        file.setBody(CONTENT.clone());

        assertTrue(verifiesContent(sign(file)), "the signature must cover the loaded content of the file");
    }

    @Test
    void hybridSignatureOfFileBodyCoversItsBytes() throws Exception {
        Path path = write("hybrid.dat");

        Exchange out = request("direct:hybridSign", path, null, null);
        byte[] hybridSignature = out.getMessage().getHeader(PQCConstants.HYBRID_SIGNATURE, byte[].class);

        assertNotNull(hybridSignature);
        assertTrue(HybridSignature.verify(CONTENT, hybridSignature, classicalKeyPair.getPublic(), classicalSignature(),
                pqcKeyPair.getPublic(), pqcSignature()), "the hybrid signature must cover the bytes of the file");
    }

    @Test
    void hybridVerificationOfFileBodyCoversItsBytes() throws Exception {
        File file = write("hybrid.dat").toFile();
        byte[] hybridSignature = HybridSignature.sign(CONTENT, classicalKeyPair.getPrivate(), classicalSignature(),
                pqcKeyPair.getPrivate(), pqcSignature());

        Exchange out = request("direct:hybridVerify", file, PQCConstants.HYBRID_SIGNATURE, hybridSignature);

        assertTrue(out.getMessage().getHeader(PQCConstants.HYBRID_VERIFY, Boolean.class),
                "a hybrid signature over the bytes of the file must verify");
    }

    private static byte[] binaryContent() {
        // every byte value, so the content is binary data rather than text
        byte[] content = new byte[1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }

    private Path write(String name) throws IOException {
        return Files.write(folder.resolve(name), CONTENT);
    }

    private byte[] sign(Object body) throws Exception {
        Exchange out = request("direct:sign", body, null, null);
        return out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
    }

    private boolean verify(Object body, byte[] signature) throws Exception {
        Exchange out = request("direct:verify", body, PQCConstants.SIGNATURE, signature);
        return out.getMessage().getHeader(PQCConstants.VERIFY, Boolean.class);
    }

    private Exchange request(String uri, Object body, String header, Object value) throws Exception {
        Exchange out = template.request(uri, e -> {
            e.getMessage().setBody(body);
            if (header != null) {
                e.getMessage().setHeader(header, value);
            }
        });
        if (out.getException() != null) {
            throw out.getException();
        }
        return out;
    }

    private static boolean verifiesContent(byte[] signature) throws Exception {
        Signature verifier = pqcSignature();
        verifier.initVerify(pqcKeyPair.getPublic());
        verifier.update(CONTENT);
        return verifier.verify(signature);
    }

    private static byte[] signContent() throws Exception {
        Signature signer = pqcSignature();
        signer.initSign(pqcKeyPair.getPrivate());
        signer.update(CONTENT);
        return signer.sign();
    }

    private static Signature pqcSignature() throws Exception {
        return Signature.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
    }

    private static Signature classicalSignature() throws Exception {
        return Signature.getInstance(PQCClassicalSignatureAlgorithms.ECDSA_P256.getAlgorithm());
    }
}
