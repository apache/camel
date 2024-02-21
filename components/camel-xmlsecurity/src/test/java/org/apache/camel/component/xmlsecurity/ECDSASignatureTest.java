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
package org.apache.camel.component.xmlsecurity;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;

import org.w3c.dom.Node;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.util.SameDocumentUriDereferencer;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.isJavaVendor;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test for the ECDSA algorithms
 */
@Disabled("Cannot run on newer JDKs like JDK11+ and org.apache.santuario:xmlsec:2.2.4 onwards (see also https://issues.apache.org/jira/browse/SANTUARIO-581)")
public class ECDSASignatureTest extends CamelTestSupport {

    private static String payload;
    private Logger log = LoggerFactory.getLogger(getClass());
    private boolean canTest = true;

    static {
        boolean includeNewLine = true;
        if (!TestSupport.isJavaVendor("Azul")) {
            includeNewLine = false;
        }
        payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                  + (includeNewLine ? "\n" : "")
                  + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
    }

    public ECDSASignatureTest() {
        try {
            // BouncyCastle is required for some algorithms
            if (Security.getProvider("BC") == null) {
                Constructor<?> cons;
                Class<?> c = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
                cons = c.getConstructor(new Class[] {});

                Provider provider = (java.security.Provider) cons.newInstance();
                Security.insertProviderAt(provider, 2);
            }

            // This test fails with the IBM JDK
            if (isJavaVendor("IBM")) {
                canTest = false;
            }
        } catch (Exception e) {
            System.err.println("Cannot test due " + e.getMessage());
            log.warn("Cannot test due {}", e.getMessage(), e);
            canTest = false;
        }
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();

        // This test fails with the IBM JDK
        if (canTest) {
            registry.bind("accessor", getKeyAccessor());
            registry.bind("selector", KeySelector.singletonKeySelector(getCertificateFromKeyStore().getPublicKey()));
            registry.bind("uriDereferencer", getSameDocumentUriDereferencer());
        }

        return registry;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        if (!canTest) {
            return new RouteBuilder[] {};
        }

        return new RouteBuilder[] { new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha1")
                        .to("xmlsecurity-sign:ecdsa_sha1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1")
                        // .log("Body: + ${body}")
                        .to("xmlsecurity-verify:ecdsa?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha224")
                        .to("xmlsecurity-sign:ecdsa_sha224?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha224")
                        .to("xmlsecurity-verify:ecdsa?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha256")
                        .to("xmlsecurity-sign:ecdsa_sha256?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256")
                        .to("xmlsecurity-verify:ecdsa?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha384")
                        .to("xmlsecurity-sign:ecdsa_sha384?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384")
                        .to("xmlsecurity-verify:ecdsa?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha512")
                        .to("xmlsecurity-sign:ecdsa_sha512?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512")
                        .to("xmlsecurity-verify:ecdsa?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_ripemd160")
                        .to("xmlsecurity-sign:ecdsa_ripemd160?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#ecdsa-ripemd160")
                        .to("xmlsecurity-verify:ecdsa?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }

        };
    }

    @Test
    public void testECDSASHA1() throws Exception {
        assumeTrue(canTest, "Test preconditions failed: canTest=" + canTest);
        setupMock();
        sendBody("direct:ecdsa_sha1", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testECDSASHA224() throws Exception {
        assumeTrue(canTest, "Test preconditions failed: canTest=" + canTest);
        setupMock();
        sendBody("direct:ecdsa_sha224", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testECDSASHA256() throws Exception {
        assumeTrue(canTest, "Test preconditions failed: canTest=" + canTest);
        setupMock();
        sendBody("direct:ecdsa_sha256", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testECDSASHA384() throws Exception {
        assumeTrue(canTest, "Test preconditions failed: canTest=" + canTest);
        setupMock();
        sendBody("direct:ecdsa_sha384", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testECDSASHA512() throws Exception {
        assumeTrue(canTest, "Test preconditions failed: canTest=" + canTest);
        setupMock();
        sendBody("direct:ecdsa_sha512", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testECDSARIPEMD160() throws Exception {
        assumeTrue(canTest, "Test preconditions failed: canTest=" + canTest);
        setupMock();
        sendBody("direct:ecdsa_ripemd160", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    private MockEndpoint setupMock() {
        return setupMock(payload);
    }

    private MockEndpoint setupMock(String payload) {
        String payload2;
        int pos = payload.indexOf('\n');
        if (pos != -1) {
            payload2 = payload.substring(0, pos) + payload.substring(pos + 1);
        } else {
            payload2 = payload.replaceFirst("\\?>", "\\?>\n");
        }
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).in(payload, payload2);
        return mock;
    }

    @Override
    @BeforeEach
    public void setUp() {
        disableJMX();
        try {
            super.setUp();
        } catch (Exception e) {
            System.err.println("Cannot test due " + e.getMessage());
            log.warn("Cannot test due {}", e.getMessage(), e);
            canTest = false;
        }
    }

    private static KeyStore loadKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream in = ECDSASignatureTest.class.getResourceAsStream("/org/apache/camel/component/xmlsecurity/ecdsa.jks");
        keyStore.load(in, "security".toCharArray());
        return keyStore;
    }

    private static Certificate getCertificateFromKeyStore() throws Exception {
        return loadKeystore().getCertificate("ECDSA");
    }

    private static PrivateKey getKeyFromKeystore() throws Exception {
        return (PrivateKey) loadKeystore().getKey("ECDSA", "security".toCharArray());
    }

    static KeyAccessor getKeyAccessor() {
        KeyAccessor accessor = new KeyAccessor() {

            @Override
            public KeySelector getKeySelector(Message message) throws Exception {
                return KeySelector.singletonKeySelector(getKeyFromKeystore());
            }

            @Override
            public KeyInfo getKeyInfo(
                    Message mess, Node messageBody,
                    KeyInfoFactory keyInfoFactory) {
                return null;
            }
        };
        return accessor;
    }

    public static URIDereferencer getSameDocumentUriDereferencer() {
        return SameDocumentUriDereferencer.getInstance();
    }

}
