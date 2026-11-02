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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.crypto.DigitalSignatureConstants.SIGNATURE;
import static org.apache.camel.component.crypto.DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sign and verify with stream caching turned off: a stream body is still there for the next step of the route.
 */
public class SignatureStreamBodyTest extends CamelTestSupport {

    private static final String PAYLOAD = "Dear Alice, Rest assured it's me, signed Bob";

    private KeyPair keyPair;

    @Override
    public void doPreSetup() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setStreamCaching(false);
        return context;
    }

    @BindToRegistry("myPrivateKey")
    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    @BindToRegistry("myPublicKey")
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    @BindToRegistry("bobCert")
    public Certificate getBobCertificate() throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = SignatureStreamBodyTest.class.getResourceAsStream("/ks.keystore")) {
            keystore.load(in, "letmein".toCharArray());
        }
        return keystore.getCertificate("bob");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:signature").to("crypto:sign:signature?privateKey=#myPrivateKey");
                from("direct:sign").to("crypto:sign:stream?privateKey=#myPrivateKey", "mock:signed");
                from("direct:verify").to("crypto:verify:stream?publicKey=#myPublicKey", "mock:verified");
                from("direct:verify-cert").to("crypto:verify:cert?certificate=#bobCert", "mock:verified");
            }
        };
    }

    @Test
    void signKeepsStreamBody() throws Exception {
        MockEndpoint signed = getMockEndpoint("mock:signed");
        signed.expectedBodiesReceived(PAYLOAD);

        template.sendBody("direct:sign", streamOf(PAYLOAD));

        MockEndpoint.assertIsSatisfied(context);
        assertNotNull(signed.getReceivedExchanges().get(0).getMessage().getHeader(SIGNATURE));
    }

    @Test
    void verifyKeepsStreamBody() throws Exception {
        getMockEndpoint("mock:verified").expectedBodiesReceived(PAYLOAD);

        template.sendBodyAndHeader("direct:verify", streamOf(PAYLOAD), SIGNATURE, signatureOf(PAYLOAD));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void verifyKeepsStreamCacheBody() throws Exception {
        getMockEndpoint("mock:verified").expectedBodiesReceived(PAYLOAD);

        InputStreamCache body = new InputStreamCache(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        template.sendBodyAndHeader("direct:verify", body, SIGNATURE, signatureOf(PAYLOAD));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void verifyFailsWithoutBody() throws Exception {
        getMockEndpoint("mock:verified").expectedMessageCount(0);

        // a signature of an empty payload
        String signature = signatureOf(null);
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:verify", null, SIGNATURE, signature));
        assertInstanceOf(SignatureException.class, e.getCause());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void headerPublicKeyWinsOverEndpointCertificate() throws Exception {
        getMockEndpoint("mock:verified").expectedBodiesReceived(PAYLOAD);

        Map<String, Object> headers = new HashMap<>();
        headers.put(SIGNATURE, signatureOf(PAYLOAD));
        headers.put(SIGNATURE_PUBLIC_KEY_OR_CERT, keyPair.getPublic());
        template.sendBodyAndHeaders("direct:verify-cert", PAYLOAD, headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    private String signatureOf(String body) {
        Exchange signed = template.request("direct:signature", e -> e.getIn().setBody(body));
        return signed.getMessage().getHeader(SIGNATURE, String.class);
    }

    private static InputStream streamOf(String text) {
        // a stream that is not a StreamCache, so it can be read only once
        return new FilterInputStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
        };
    }
}
