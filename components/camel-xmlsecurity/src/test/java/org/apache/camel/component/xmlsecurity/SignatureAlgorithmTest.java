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
import java.security.Key;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.ValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.util.EnvelopingXmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.util.SameDocumentUriDereferencer;
import org.apache.camel.component.xmlsecurity.util.TestKeystore;
import org.apache.camel.component.xmlsecurity.util.TimestampProperty;
import org.apache.camel.component.xmlsecurity.util.ValidationFailedHandlerIgnoreManifestFailures;
import org.apache.camel.component.xmlsecurity.util.XmlSignature2Message2MessageWithTimestampProperty;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Before;
import org.junit.Test;


/**
 * Test signing using all available signature methods, apart from EC-algorithms which are
 * tested in ECDSASignatureTest.
 */
public class SignatureAlgorithmTest extends CamelTestSupport {

    private static String payload;
    private KeyPair keyPair;
    
    static {
        boolean includeNewLine = true;
        if (TestSupport.getJavaMajorVersion() >= 9) {
            includeNewLine = false;
        }
        payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + (includeNewLine ? "\n" : "")
            + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
    }
    
    public SignatureAlgorithmTest() throws Exception {
        // BouncyCastle is required for some algorithms
        if (Security.getProvider("BC") == null) {
            Constructor<?> cons;
            Class<?> c = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            cons = c.getConstructor(new Class[] {});

            Provider provider = (java.security.Provider)cons.newInstance();
            Security.insertProviderAt(provider, 2);
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        Key secretKey = getSecretKey("testkey".getBytes("ASCII"));

        registry.bind("accessor", getKeyAccessor(keyPair.getPrivate()));
        registry.bind("secretKeyAccessor", getKeyAccessor(secretKey));
        registry.bind("canonicalizationMethod1", getCanonicalizationMethod());
        registry.bind("selector", KeySelector.singletonKeySelector(keyPair.getPublic()));
        registry.bind("secretKeySelector", KeySelector.singletonKeySelector(secretKey));
        registry.bind("selectorKeyValue", getKeyValueKeySelector());
        registry.bind("uriDereferencer", getSameDocumentUriDereferencer());
        registry.bind("baseUri", getBaseUri());
        registry.bind("cryptoContextProperties", getCrytoContextProperties());
        registry.bind("keyAccessorDefault", getDefaultKeyAccessor());
        registry.bind("keySelectorDefault", getDefaultKeySelector());
        registry.bind("envelopingSignatureChecker", getEnvelopingXmlSignatureChecker());
        registry.bind("xmlSignature2MessageWithTimestampProperty", getXmlSignature2MessageWithTimestampdProperty());
        registry.bind("validationFailedHandlerIgnoreManifestFailures", getValidationFailedHandlerIgnoreManifestFailures());
        registry.bind("signatureProperties", getSignatureProperties());
        registry.bind("nodesearchxpath", getNodeSerachXPath());
        Map<String, String> namespaceMap = Collections.singletonMap("ns", "http://test");
        List<XPathFilterParameterSpec> xpaths = Collections
                .singletonList(XmlSignatureHelper.getXpathFilter("/ns:root/a/@ID", namespaceMap));
        registry.bind("xpathsToIdAttributes", xpaths);

        registry.bind("parentXpathBean", getParentXPathBean());

        return registry;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:hmacsha1")
                        .to("xmlsecurity:sign:hmacsha1?keyAccessor=#secretKeyAccessor"
                            + "&signatureAlgorithm=http://www.w3.org/2000/09/xmldsig#hmac-sha1")
                        // .log("Body: + ${body}")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#secretKeySelector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:hmacsha224")
                        .to("xmlsecurity:sign:hmacsha224?keyAccessor=#secretKeyAccessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#hmac-sha224")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#secretKeySelector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:hmacsha256")
                        .to("xmlsecurity:sign:hmacsha256?keyAccessor=#secretKeyAccessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#hmac-sha256")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#secretKeySelector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:hmacsha384")
                        .to("xmlsecurity:sign:hmacsha384?keyAccessor=#secretKeyAccessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#hmac-sha384")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#secretKeySelector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:hmacsha512")
                        .to("xmlsecurity:sign:hmacsha512?keyAccessor=#secretKeyAccessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#hmac-sha512")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#secretKeySelector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:hmacripemd160")
                        .to("xmlsecurity:sign:hmacripemd160?keyAccessor=#secretKeyAccessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#hmac-ripemd160")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#secretKeySelector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha1")
                        .to("xmlsecurity:sign:rsasha1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2000/09/xmldsig#rsa-sha1")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha224")
                        .to("xmlsecurity:sign:rsasha224?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha224")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha256")
                        .to("xmlsecurity:sign:rsasha256?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha256")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha384")
                        .to("xmlsecurity:sign:rsasha384?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha384")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha512")
                        .to("xmlsecurity:sign:rsasha512?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsaripemd160")
                        .to("xmlsecurity:sign:rsaripemd160?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-ripemd160")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha1_mgf1")
                        .to("xmlsecurity:sign:rsasha1_mgf1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#sha1-rsa-MGF1")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha224_mgf1")
                        .to("xmlsecurity:sign:rsasha224_mgf1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#sha224-rsa-MGF1")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha256_mgf1")
                        .to("xmlsecurity:sign:rsasha256_mgf1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha384_mgf1")
                        .to("xmlsecurity:sign:rsasha384_mgf1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#sha384-rsa-MGF1")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:rsasha512_mgf1")
                        .to("xmlsecurity:sign:rsasha512_mgf1?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1")
                        .to("xmlsecurity:verify:signaturedigestalgorithm?keySelector=#selector").to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }};
    }

    //
    // Secret Key algorithms
    //
    @Test
    public void testHMACSHA1() throws Exception {
        setupMock();
        sendBody("direct:hmacsha1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testHMACSHA224() throws Exception {
        setupMock();
        sendBody("direct:hmacsha224", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testHMACSHA256() throws Exception {
        setupMock();
        sendBody("direct:hmacsha256", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testHMACSHA384() throws Exception {
        setupMock();
        sendBody("direct:hmacsha384", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testHMACSHA512() throws Exception {
        setupMock();
        sendBody("direct:hmacsha512", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testHMACRIPEMD160() throws Exception {
        setupMock();
        sendBody("direct:hmacripemd160", payload);
        assertMockEndpointsSatisfied();
    }
    
    //
    // Public Key algorithms
    //
    @Test
    public void testRSASHA1() throws Exception {
        setupMock();
        sendBody("direct:rsasha1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA224() throws Exception {
        setupMock();
        sendBody("direct:rsasha224", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA256() throws Exception {
        setupMock();
        sendBody("direct:rsasha256", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA384() throws Exception {
        setupMock();
        sendBody("direct:rsasha384", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA512() throws Exception {
        setupMock();
        sendBody("direct:rsasha512", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSARIPEMD160() throws Exception {
        setupMock();
        sendBody("direct:rsaripemd160", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA1MGF1() throws Exception {
        setupMock();
        sendBody("direct:rsasha1_mgf1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA224MGF1() throws Exception {
        setupMock();
        sendBody("direct:rsasha224_mgf1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA256MGF1() throws Exception {
        setupMock();
        sendBody("direct:rsasha256_mgf1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA384MGF1() throws Exception {
        setupMock();
        sendBody("direct:rsasha384_mgf1", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testRSASHA512MGF1() throws Exception {
        setupMock();
        sendBody("direct:rsasha512_mgf1", payload);
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

    @Before
    public void setUp() throws Exception {
        setUpKeys("RSA", 2048);
        disableJMX();
        super.setUp();
    }

    public void setUpKeys(String algorithm, int keylength) throws Exception {
        keyPair = getKeyPair(algorithm, keylength);
    }

    public static KeyPair getKeyPair(String algorithm, int keylength) {
        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        keyGen.initialize(keylength, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    public static KeyStore loadKeystore() throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream in = SignatureAlgorithmTest.class.getResourceAsStream("/bob.keystore");
        keystore.load(in, "letmein".toCharArray());
        return keystore;
    }

    public Certificate getCertificateFromKeyStore() throws Exception {
        Certificate c = loadKeystore().getCertificate("bob");
        return c;
    }

    public PrivateKey getKeyFromKeystore() throws Exception {
        return (PrivateKey) loadKeystore().getKey("bob", "letmein".toCharArray());
    }

    private AlgorithmMethod getCanonicalizationMethod() {
        List<String> inclusivePrefixes = new ArrayList<String>(1);
        inclusivePrefixes.add("ds");
        return XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, inclusivePrefixes);
    }

    static KeyAccessor getKeyAccessor(final Key key) {
        KeyAccessor accessor = new KeyAccessor() {

            @Override
            public KeySelector getKeySelector(Message message) throws Exception {
                return KeySelector.singletonKeySelector(key);
            }

            @Override
            public KeyInfo getKeyInfo(Message mess, Node messageBody, KeyInfoFactory keyInfoFactory) throws Exception {
                return null;
            }
        };
        return accessor;
    }

    public static String getBaseUri() {
        String uri = "file:/" + System.getProperty("user.dir") + "/src/test/resources/org/apache/camel/component/xmlsecurity/";
        return uri.replace('\\', '/');
    }

    public static KeySelector getKeyValueKeySelector() {
        return new KeyValueKeySelector();
    }

    /**
     * KeySelector which retrieves the public key from the KeyValue element and
     * returns it. NOTE: If the key algorithm doesn't match signature algorithm,
     * then the public key will be ignored.
     */
    static class KeyValueKeySelector extends KeySelector {
        public KeySelectorResult select(KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
            throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Null KeyInfo object!");
            }

            SignatureMethod sm = (SignatureMethod) method;
            @SuppressWarnings("rawtypes")
            List list = keyInfo.getContent();

            for (int i = 0; i < list.size(); i++) {
                XMLStructure xmlStructure = (XMLStructure) list.get(i);
                if (xmlStructure instanceof KeyValue) {
                    PublicKey pk = null;
                    try {
                        pk = ((KeyValue) xmlStructure).getPublicKey();
                    } catch (KeyException ke) {
                        throw new KeySelectorException(ke);
                    }
                    // make sure algorithm is compatible with method
                    if (algEquals(sm.getAlgorithm(), pk.getAlgorithm())) {
                        return new SimpleKeySelectorResult(pk);
                    }
                }
            }
            throw new KeySelectorException("No KeyValue element found!");
        }

        static boolean algEquals(String algURI, String algName) {
            return (algName.equalsIgnoreCase("DSA") && algURI.equalsIgnoreCase(SignatureMethod.DSA_SHA1))
                    || (algName.equalsIgnoreCase("RSA") && algURI.equalsIgnoreCase(SignatureMethod.RSA_SHA1));
        }
    }

    private static class SimpleKeySelectorResult implements KeySelectorResult {
        private PublicKey pk;

        SimpleKeySelectorResult(PublicKey pk) {
            this.pk = pk;
        }

        public Key getKey() {
            return pk;
        }
    }

    public static Map<String, ? extends Object> getCrytoContextProperties() {
        return Collections.singletonMap("org.jcp.xml.dsig.validateManifests", Boolean.FALSE);
    }

    public static KeyAccessor getDefaultKeyAccessor() throws Exception {
        return TestKeystore.getKeyAccessor("bob");
    }

    public static KeySelector getDefaultKeySelector() throws Exception {
        return TestKeystore.getKeySelector("bob");
    }

    public static KeyAccessor getDefaultKeyAccessorDsa() throws Exception {
        return TestKeystore.getKeyAccessor("bobdsa");
    }

    public static KeySelector getDefaultKeySelectorDsa() throws Exception {
        return TestKeystore.getKeySelector("bobdsa");
    }
    
    public static XmlSignatureChecker getEnvelopingXmlSignatureChecker() {
        return new EnvelopingXmlSignatureChecker();
    }

    public static XmlSignature2Message getXmlSignature2MessageWithTimestampdProperty() {
        return new XmlSignature2Message2MessageWithTimestampProperty();
    }

    public static ValidationFailedHandler getValidationFailedHandlerIgnoreManifestFailures() {
        return new ValidationFailedHandlerIgnoreManifestFailures();
    }

    public static XmlSignatureProperties getSignatureProperties() {
        return new TimestampProperty();
    }

    public static XPathFilterParameterSpec getNodeSerachXPath() {
        Map<String, String> prefix2Namespace = Collections.singletonMap("pre", "http://test/test");
        return XmlSignatureHelper.getXpathFilter("//pre:root", prefix2Namespace);
    }

    public static URIDereferencer getSameDocumentUriDereferencer() {
        return SameDocumentUriDereferencer.getInstance();
    }

    public static XPathFilterParameterSpec getParentXPathBean() {
        Map<String, String> prefix2Namespace = Collections.singletonMap("ns", "http://test");
        return XmlSignatureHelper.getXpathFilter("/ns:root/a[last()]", prefix2Namespace);
    }

    public static SecretKey getSecretKey(final byte[] secret) {
        return new SecretKey() {
            private static final long serialVersionUID = 5629454124145851381L;
            
            public String getFormat()   {
                return "RAW";
            }
            public byte[] getEncoded()  {
                return secret;
            }
            public String getAlgorithm() {
                return "SECRET";
            }
        };
    }
}