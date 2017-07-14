/**
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
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for the ECDSA algorithms
 */
public class ECDSASignatureTest extends CamelTestSupport {
    
    private static String payload;
    private boolean canTest = true;
    
    static {
        boolean includeNewLine = true;
        if (TestSupport.getJavaMajorVersion() >= 9) {
            includeNewLine = false;
        }
        payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + (includeNewLine ? "\n" : "")
            + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
    }

    public ECDSASignatureTest() throws Exception {
        try {
            // BouncyCastle is required for some algorithms
            if (Security.getProvider("BC") == null) {
                Constructor<?> cons;
                Class<?> c = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
                cons = c.getConstructor(new Class[] {});

                Provider provider = (java.security.Provider)cons.newInstance();
                Security.insertProviderAt(provider, 2);
            }

            // This test fails with the IBM JDK
            if (isJavaVendor("IBM")) {
                canTest = false;
            }
        } catch (Exception e) {
            System.err.println("Cannot test due " + e.getMessage());
            log.warn("Cannot test due " + e.getMessage(), e);
            canTest = false;
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        // This test fails with the IBM JDK
        if (canTest) {
            registry.bind("accessor", getKeyAccessor());
            registry.bind("selector", KeySelector.singletonKeySelector(getCertificateFromKeyStore().getPublicKey()));
            registry.bind("uriDereferencer", getSameDocumentUriDereferencer());
        }

        return registry;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        if (!canTest) {
            return new RouteBuilder[] {};
        }
        
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha1")
                    .to("xmlsecurity:sign:ecdsa_sha1?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1")
                        // .log("Body: + ${body}")
                        .to("xmlsecurity:verify:ecdsa?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha224")
                    .to("xmlsecurity:sign:ecdsa_sha224?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha224")
                        .to("xmlsecurity:verify:ecdsa?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha256")
                    .to("xmlsecurity:sign:ecdsa_sha256?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256")
                        .to("xmlsecurity:verify:ecdsa?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha384")
                    .to("xmlsecurity:sign:ecdsa_sha384?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384")
                        .to("xmlsecurity:verify:ecdsa?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_sha512")
                    .to("xmlsecurity:sign:ecdsa_sha512?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512")
                        .to("xmlsecurity:verify:ecdsa?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ecdsa signature algorithm
                from("direct:ecdsa_ripemd160")
                    .to("xmlsecurity:sign:ecdsa_ripemd160?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#ecdsa-ripemd160")
                        .to("xmlsecurity:verify:ecdsa?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: ecdsa signature algorithm
            }
        }
        
        };
    }

    @Test
    public void testECDSASHA1() throws Exception {
        if (!canTest) {
            return;
        }
        setupMock();
        sendBody("direct:ecdsa_sha1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testECDSASHA224() throws Exception {
        if (!canTest) {
            return;
        }
        setupMock();
        sendBody("direct:ecdsa_sha224", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testECDSASHA256() throws Exception {
        if (!canTest) {
            return;
        }
        setupMock();
        sendBody("direct:ecdsa_sha256", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testECDSASHA384() throws Exception {
        if (!canTest) {
            return;
        }
        setupMock();
        sendBody("direct:ecdsa_sha384", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testECDSASHA512() throws Exception {
        if (!canTest) {
            return;
        }
        setupMock();
        sendBody("direct:ecdsa_sha512", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testECDSARIPEMD160() throws Exception {
        if (!canTest) {
            return;
        }
        setupMock();
        sendBody("direct:ecdsa_ripemd160", payload);
        assertMockEndpointsSatisfied();
    }

    private MockEndpoint setupMock() {
        return setupMock(payload);
    }

    private MockEndpoint setupMock(String payload) {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(payload);
        return mock;
    }

    @Before
    public void setUp() throws Exception {
        disableJMX();
        try {
            super.setUp();
        } catch (Exception e) {
            System.err.println("Cannot test due " + e.getMessage());
            log.warn("Cannot test due " + e.getMessage(), e);
            canTest = false;
        }
    }

    private static KeyStore loadKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream in = 
            ECDSASignatureTest.class.getResourceAsStream("/org/apache/camel/component/xmlsecurity/ecdsa.jks");
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
            public KeyInfo getKeyInfo(Message mess, Node messageBody,
                                      KeyInfoFactory keyInfoFactory) throws Exception {
                return null;
            }
        };
        return accessor;
    }

    public static URIDereferencer getSameDocumentUriDereferencer() {
        return SameDocumentUriDereferencer.getInstance();
    }

}