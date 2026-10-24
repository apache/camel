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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pqc.crypto.hybrid.HybridSignature;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bodies that are not a {@code byte[]}, an {@code InputStream} or a file are signed and verified over their bytes when
 * they have a binary type ({@link ByteBuffer}, {@link ByteArrayOutputStream}, and, through their own type converters,
 * Netty {@code ByteBuf} and Vert.x {@code Buffer}), a {@code String} is signed as UTF-8, and a body with no binary
 * representation (a {@link Map} or a POJO) is rejected rather than signed over a text rendering.
 */
public class PQCBinaryBodySignatureTest extends CamelTestSupport {

    private static final String PQC_KEY = "&signatureAlgorithm=MLDSA&keyPair=#pqcKeyPair";
    private static final String CLASSICAL_KEY = "&classicalSignatureAlgorithm=ECDSA_P256&classicalKeyPair=#classicalKeyPair";
    private static final String SIGN = "pqc:sign?operation=sign" + PQC_KEY;
    private static final String VERIFY = "pqc:verify?operation=verify" + PQC_KEY;

    private static final byte[] CONTENT = binaryContent();

    private static KeyPair pqcKeyPair;
    private static KeyPair classicalKeyPair;

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
            }
        };
    }

    @Test
    void byteBufferBodyIsSignedOverItsBytes() throws Exception {
        assertTrue(verifiesContent(sign(ByteBuffer.wrap(CONTENT.clone()))),
                "the signature must cover the bytes of the ByteBuffer");
    }

    @Test
    void byteBufferBodyIsVerifiedOverItsBytes() throws Exception {
        assertTrue(verify(ByteBuffer.wrap(CONTENT.clone()), signContent()),
                "a signature over the bytes must verify a ByteBuffer body");
    }

    @Test
    void byteArrayOutputStreamBodyIsSignedOverItsBytes() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(CONTENT);

        assertTrue(verifiesContent(sign(body)), "the signature must cover the bytes of the ByteArrayOutputStream");
    }

    @Test
    void stringBodyIsSignedAsUtf8() throws Exception {
        String body = "post-quantum café éè";

        Signature verifier = pqcSignature();
        verifier.initVerify(pqcKeyPair.getPublic());
        verifier.update(body.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(sign(body)), "a String body must be signed as UTF-8");
    }

    @Test
    void hybridSignatureOfByteBufferBodyCoversItsBytes() throws Exception {
        Exchange out = request("direct:hybridSign", ByteBuffer.wrap(CONTENT.clone()), null, null);
        byte[] hybridSignature = out.getMessage().getHeader(PQCConstants.HYBRID_SIGNATURE, byte[].class);

        assertNotNull(hybridSignature);
        assertTrue(HybridSignature.verify(CONTENT, hybridSignature, classicalKeyPair.getPublic(), classicalSignature(),
                pqcKeyPair.getPublic(), pqcSignature()), "the hybrid signature must cover the bytes of the ByteBuffer");
    }

    @Test
    void mapBodyIsRejected() {
        // a Map has no binary representation, so there is no well-defined set of bytes to sign
        assertThrows(InvalidPayloadException.class, () -> sign(Map.of("amount", "10", "currency", "EUR")));
    }

    @Test
    void pojoBodyIsRejected() {
        assertThrows(InvalidPayloadException.class, () -> sign(new Document("secret.pdf", 10)));
    }

    private static byte[] binaryContent() {
        // every byte value, so the content is binary data rather than text
        byte[] content = new byte[1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
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

    private record Document(String name, int size) {
    }
}
