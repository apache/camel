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

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.crypto.DigitalSignatureConstants.KEYSTORE_ALIAS;
import static org.apache.camel.component.crypto.DigitalSignatureConstants.SIGNATURE_PRIVATE_KEY;
import static org.apache.camel.component.crypto.DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT;

public class SignatureTest extends CamelTestSupport {

    private KeyPair keyPair;
    private KeyPair dsaKeyPair;
    private String payload = "Dear Alice, Rest assured it's me, signed Bob";

    @BindToRegistry("someRandom")
    private SecureRandom random = new SecureRandom();

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: basic
                from("direct:keypair").to("crypto:sign:basic?privateKey=#myPrivateKey", "crypto:verify:basic?publicKey=#myPublicKey", "mock:result");
                // END SNIPPET: basic
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: algorithm
                keyPair = getKeyPair("RSA");
                PrivateKey privateKey = keyPair.getPrivate();
                PublicKey publicKey = keyPair.getPublic();

                // we can set the keys explicitly on the endpoint instances.
                context.getEndpoint("crypto:sign:rsa?algorithm=MD5withRSA", DigitalSignatureEndpoint.class).setPrivateKey(privateKey);
                context.getEndpoint("crypto:verify:rsa?algorithm=MD5withRSA", DigitalSignatureEndpoint.class).setPublicKey(publicKey);
                from("direct:algorithm").to("crypto:sign:rsa?algorithm=MD5withRSA", "crypto:verify:rsa?algorithm=MD5withRSA", "mock:result");
                // END SNIPPET: algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: rsa-sha1
                keyPair = getKeyPair("RSA");
                PrivateKey privateKey = keyPair.getPrivate();
                PublicKey publicKey = keyPair.getPublic();

                // we can set the keys explicitly on the endpoint instances.
                context.getEndpoint("crypto:sign:rsa?algorithm=SHA1withRSA", DigitalSignatureEndpoint.class).setPrivateKey(privateKey);
                context.getEndpoint("crypto:verify:rsa?algorithm=SHA1withRSA", DigitalSignatureEndpoint.class).setPublicKey(publicKey);
                from("direct:rsa-sha1").to("crypto:sign:rsa?algorithm=SHA1withRSA", "crypto:verify:rsa?algorithm=SHA1withRSA", "mock:result");
                // END SNIPPET: rsa-sha1
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: rsa-sha256
                keyPair = getKeyPair("RSA");
                PrivateKey privateKey = keyPair.getPrivate();
                PublicKey publicKey = keyPair.getPublic();

                // we can set the keys explicitly on the endpoint instances.
                context.getEndpoint("crypto:sign:rsa?algorithm=SHA256withRSA", DigitalSignatureEndpoint.class).setPrivateKey(privateKey);
                context.getEndpoint("crypto:verify:rsa?algorithm=SHA256withRSA", DigitalSignatureEndpoint.class).setPublicKey(publicKey);
                from("direct:rsa-sha256").to("crypto:sign:rsa?algorithm=SHA256withRSA", "crypto:verify:rsa?algorithm=SHA256withRSA", "mock:result");
                // END SNIPPET: rsa-sha256
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: buffersize
                from("direct:buffersize").to("crypto:sign:buffer?privateKey=#myPrivateKey&buffersize=1024", "crypto:verify:buffer?publicKey=#myPublicKey&buffersize=1024",
                                             "mock:result");
                // END SNIPPET: buffersize
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: provider
                from("direct:provider").to("crypto:sign:provider?algorithm=SHA1withDSA&privateKey=#myDSAPrivateKey&provider=SUN",
                                           "crypto:verify:provider?algorithm=SHA1withDSA&publicKey=#myDSAPublicKey&provider=SUN",
                                           "mock:result");
                // END SNIPPET: provider
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: certificate
                from("direct:certificate").to("crypto:sign:withcert?privateKey=#myPrivateKey", "crypto:verify:withcert?certificate=#myCert", "mock:result");
                // END SNIPPET: certificate
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: keystore
                from("direct:keystore").to("crypto:sign:keystore?keystore=#keystore&alias=bob&password=letmein", "crypto:verify:keystore?keystore=#keystore&alias=bob",
                                           "mock:result");
                // END SNIPPET: keystore
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: keystore
                from("direct:keystoreParameters").to("crypto:sign:keyStoreParameters?keyStoreParameters=#signatureParams&alias=bob&password=letmein",
                                                     "crypto:verify:keyStoreParameters?keyStoreParameters=#signatureParams&alias=bob", "mock:result");
                // END SNIPPET: keystore
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature-header
                from("direct:signature-header").to("crypto:sign:another?privateKey=#myPrivateKey&signatureHeader=AnotherDigitalSignature",
                                                   "crypto:verify:another?publicKey=#myPublicKey&signatureHeader=AnotherDigitalSignature", "mock:result");
                // END SNIPPET: signature-header
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: random
                from("direct:random").to("crypto:sign:another?privateKey=#myPrivateKey&secureRandom=#someRandom",
                                         "crypto:verify:another?publicKey=#myPublicKey&secureRandom=#someRandom", "mock:result");
                // END SNIPPET: random
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: alias
                from("direct:alias-sign").to("crypto:sign:alias?keystore=#keystore");
                from("direct:alias-verify").to("crypto:verify:alias?keystore=#keystore", "mock:result");
                // END SNIPPET: alias
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: headerkey
                from("direct:headerkey-sign").to("crypto:sign:alias");
                from("direct:headerkey-verify").to("crypto:verify:alias", "mock:result");
                // END SNIPPET: headerkey
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: clearheaders
                from("direct:headers").to("crypto:sign:headers?privateKey=#myPrivateKey", "crypto:verify:headers?publicKey=#myPublicKey&clearHeaders=false", "mock:result");
                // END SNIPPET: clearheaders
            }
        }};
    }

    @Test
    public void testBasicSignatureRoute() throws Exception {
        setupMock();
        sendBody("direct:keypair", payload);
        assertMockEndpointsSatisfied();

        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange e = mock.getExchanges().get(0);
        Message result = e == null ? null : e.getMessage();
        assertNull(result.getHeader(DigitalSignatureConstants.SIGNATURE));
    }

    @Test
    public void testSetAlgorithmInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:algorithm", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRSASHA1() throws Exception {
        setupMock();
        sendBody("direct:rsa-sha1", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRSASHA256() throws Exception {
        setupMock();
        sendBody("direct:rsa-sha256", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetBufferInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:buffersize", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetRandomInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:random", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetProviderInRouteDefinition() throws Exception {
        if (isJavaVendor("ibm")) {
            return;
        }
        // can only be run on SUN JDK
        setupMock();
        sendBody("direct:provider", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetCertificateInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:certificate", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetKeystoreInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:keystore", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetKeystoreParametersInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:keystoreParameters", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSignatureHeaderInRouteDefinition() throws Exception {
        setupMock();
        Exchange signed = getMandatoryEndpoint("direct:signature-header").createExchange();
        signed.getIn().setBody(payload);
        template.send("direct:signature-header", signed);
        assertNotNull(signed.getIn().getHeader("AnotherDigitalSignature"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProvideAliasInHeader() throws Exception {
        setupMock();

        // START SNIPPET: alias-send
        Exchange unsigned = getMandatoryEndpoint("direct:alias-sign").createExchange();
        unsigned.getIn().setBody(payload);
        unsigned.getIn().setHeader(DigitalSignatureConstants.KEYSTORE_ALIAS, "bob");
        unsigned.getIn().setHeader(DigitalSignatureConstants.KEYSTORE_PASSWORD, "letmein".toCharArray());
        template.send("direct:alias-sign", unsigned);

        Exchange signed = getMandatoryEndpoint("direct:alias-sign").createExchange();
        signed.getIn().copyFrom(unsigned.getMessage());
        signed.getIn().setHeader(KEYSTORE_ALIAS, "bob");
        template.send("direct:alias-verify", signed);
        // START SNIPPET: alias-send

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProvideKeysInHeader() throws Exception {
        setupMock();
        Exchange unsigned = getMandatoryEndpoint("direct:headerkey-sign").createExchange();
        unsigned.getIn().setBody(payload);

        // create a keypair
        KeyPair pair = getKeyPair("RSA");

        // sign with the private key
        unsigned.getIn().setHeader(SIGNATURE_PRIVATE_KEY, pair.getPrivate());
        template.send("direct:headerkey-sign", unsigned);

        // verify with the public key
        Exchange signed = getMandatoryEndpoint("direct:alias-sign").createExchange();
        signed.getIn().copyFrom(unsigned.getMessage());
        signed.getIn().setHeader(SIGNATURE_PUBLIC_KEY_OR_CERT, pair.getPublic());
        template.send("direct:headerkey-verify", signed);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProvideCertificateInHeader() throws Exception {
        setupMock();
        Exchange unsigned = getMandatoryEndpoint("direct:signature-property").createExchange();
        unsigned.getIn().setBody(payload);

        // create a keypair
        KeyStore keystore = loadKeystore();
        Certificate certificate = keystore.getCertificate("bob");
        PrivateKey pk = (PrivateKey)keystore.getKey("bob", "letmein".toCharArray());

        // sign with the private key
        unsigned.getIn().setHeader(SIGNATURE_PRIVATE_KEY, pk);
        template.send("direct:headerkey-sign", unsigned);

        // verify with the public key
        Exchange signed = getMandatoryEndpoint("direct:alias-sign").createExchange();
        signed.getIn().copyFrom(unsigned.getMessage());
        signed.getIn().setHeader(SIGNATURE_PUBLIC_KEY_OR_CERT, certificate);
        template.send("direct:headerkey-verify", signed);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVerifyHeadersNotCleared() throws Exception {
        setupMock();
        template.requestBody("direct:headers", payload);
        assertMockEndpointsSatisfied();
        assertMockEndpointsSatisfied();

        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange e = mock.getExchanges().get(0);
        Message result = e == null ? null : e.getMessage();
        assertNotNull(result.getHeader(DigitalSignatureConstants.SIGNATURE));
    }

    private MockEndpoint setupMock() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(payload);
        return mock;
    }

    public Exchange doTestSignatureRoute(RouteBuilder builder) throws Exception {
        return doSignatureRouteTest(builder, null, Collections.<String, Object> emptyMap());
    }

    public Exchange doSignatureRouteTest(RouteBuilder builder, Exchange e, Map<String, Object> headers) throws Exception {
        CamelContext context = new DefaultCamelContext();
        try {
            context.addRoutes(builder);
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.setExpectedMessageCount(1);

            ProducerTemplate template = context.createProducerTemplate();
            if (e != null) {
                template.send("direct:in", e);
            } else {
                template.sendBodyAndHeaders("direct:in", payload, headers);
            }
            assertMockEndpointsSatisfied();
            return mock.getReceivedExchanges().get(0);
        } finally {
            context.stop();
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        setUpKeys();
        disableJMX();
        super.setUp();
    }

    public void setUpKeys() throws Exception {
        keyPair = getKeyPair("RSA");
        dsaKeyPair = getKeyPair("DSA");
    }

    public KeyPair getKeyPair(String algorithm) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
        keyGen.initialize(512, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    @BindToRegistry("keystore")
    public static KeyStore loadKeystore() throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream in = SignatureTest.class.getResourceAsStream("/ks.keystore");
        keystore.load(in, "letmein".toCharArray());
        return keystore;
    }

    @BindToRegistry("myCert")
    public Certificate getCertificateFromKeyStore() throws Exception {
        Certificate c = loadKeystore().getCertificate("bob");
        return c;
    }

    @BindToRegistry("myPublicKey")
    public PublicKey getPublicKey() throws Exception {
        Certificate c = loadKeystore().getCertificate("bob");
        return c.getPublicKey();
    }

    @BindToRegistry("myDSAPublicKey")
    public PublicKey getDSAPublicKey() throws Exception {
        return dsaKeyPair.getPublic();
    }

    @BindToRegistry("myPrivateKey")
    public PrivateKey getKeyFromKeystore() throws Exception {
        return (PrivateKey)loadKeystore().getKey("bob", "letmein".toCharArray());
    }

    @BindToRegistry("myDSAPrivateKey")
    public PrivateKey getDSAPrivateKey() throws Exception {
        return dsaKeyPair.getPrivate();
    }

    @BindToRegistry("signatureParams")
    public KeyStoreParameters getParams() {
        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setPassword("letmein");
        keystoreParameters.setResource("./ks.keystore");
        return keystoreParameters;
    }

}
