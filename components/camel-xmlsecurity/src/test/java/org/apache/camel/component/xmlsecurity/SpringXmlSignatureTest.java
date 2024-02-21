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

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
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
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringXmlSignatureTest extends CamelTestSupport {

    protected static String payload;
    private static boolean includeNewLine;
    private static KeyPair rsaPair;
    private KeyPair keyPair;

    static {
        payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                  + (includeNewLine ? "\n" : "")
                  + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();

        registry.bind("accessor", getKeyAccessor(keyPair.getPrivate()));
        registry.bind("selector", KeySelector.singletonKeySelector(keyPair.getPublic()));
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

    Message getMessage(MockEndpoint mock) {
        List<Exchange> exs = mock.getExchanges();
        assertNotNull(exs);
        assertEquals(1, exs.size());
        Exchange ex = exs.get(0);
        Message mess = ex.getIn();
        assertNotNull(mess);
        return mess;
    }

    public static KeyPair getKeyPair(String algorithm, int keylength) {
        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeCamelException(e);
        }
        keyGen.initialize(keylength, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    static KeyAccessor getKeyAccessor(final PrivateKey privateKey) {
        KeyAccessor accessor = new KeyAccessor() {

            @Override
            public KeySelector getKeySelector(Message message) {
                return KeySelector.singletonKeySelector(privateKey);
            }

            @Override
            public KeyInfo getKeyInfo(Message mess, Node messageBody, KeyInfoFactory keyInfoFactory) {
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
     * KeySelector which retrieves the public key from the KeyValue element and returns it. NOTE: If the key algorithm
     * doesn't match signature algorithm, then the public key will be ignored.
     */
    static class KeyValueKeySelector extends KeySelector {
        @Override
        public KeySelectorResult select(
                KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
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
            return algName.equalsIgnoreCase("DSA") && algURI.equalsIgnoreCase(SignatureMethod.DSA_SHA1)
                    || algName.equalsIgnoreCase("RSA") && algURI.equalsIgnoreCase(SignatureMethod.RSA_SHA1);
        }
    }

    private static class SimpleKeySelectorResult implements KeySelectorResult {
        private PublicKey pk;

        SimpleKeySelectorResult(PublicKey pk) {
            this.pk = pk;
        }

        @Override
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

    @Override
    protected CamelContext createCamelContext() throws Exception {
        rsaPair = getKeyPair("RSA", 1024);
        return SpringCamelContext.springCamelContext(
                new ClassPathXmlApplicationContext("/org/apache/camel/component/xmlsecurity/SpringXmlSignatureTests.xml"),
                true);
    }

    public static KeyAccessor getDsaKeyAccessor() {
        return getKeyAccessor(getKeyPair("DSA", 1024).getPrivate());
    }

    public static KeyAccessor getRsaKeyAccessor() {
        return getKeyAccessor(rsaPair.getPrivate());
    }

    public static KeySelector getDsaKeySelector() {
        return KeySelector.singletonKeySelector(getKeyPair("DSA", 1024).getPublic());
    }

    public static KeySelector getRsaKeySelector() {
        return KeySelector.singletonKeySelector(rsaPair.getPublic());
    }

    XmlSignerEndpoint getDetachedSignerEndpoint() {
        XmlSignerEndpoint endpoint = (XmlSignerEndpoint) context()
                .getEndpoint(
                        "xmlsecurity-sign:detached?keyAccessor=#accessorRsa&xpathsToIdAttributes=#xpathsToIdAttributes&"//
                             + "schemaResourceUri=org/apache/camel/component/xmlsecurity/Test.xsd&signatureId=&clearHeaders=false");
        return endpoint;
    }

    XmlSignerEndpoint getSignatureEncpointForSignException() {
        XmlSignerEndpoint endpoint = (XmlSignerEndpoint) context().getEndpoint(//
                "xmlsecurity-sign:signexceptioninvalidkey?keyAccessor=#accessorRsa");
        return endpoint;
    }

    String getVerifierEndpointURIEnveloped() {
        return "xmlsecurity-verify:enveloped?keySelector=#selectorRsa";
    }

    String getSignerEndpointURIEnveloped() {
        return "xmlsecurity-sign:enveloped?keyAccessor=#accessorRsa&parentLocalName=root&parentNamespace=http://test/test";
    }

    String getVerifierEndpointURIEnveloping() {
        return "xmlsecurity-verify:enveloping?keySelector=#selectorRsa";
    }

    String getSignerEndpointURIEnveloping() {
        return "xmlsecurity-sign:enveloping?keyAccessor=#accessorRsa";
    }

    @Test
    public void xades() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        sendBody("direct:xades", payload);
        MockEndpoint.assertIsSatisfied(context);

        Message message = getMessage(mock);
        byte[] body = message.getBody(byte[].class);
        Document doc = XmlSignatureHelper.newDocumentBuilder(true).parse(new ByteArrayInputStream(body));
        Map<String, String> prefix2Ns = XAdESSignaturePropertiesTest.getPrefix2NamespaceMap();
        prefix2Ns.put("t", "http://test.com/");
        XAdESSignaturePropertiesTest
                .checkXpath(
                        doc,
                        "/ds:Signature/ds:Object/etsi:QualifyingProperties/etsi:SignedProperties/etsi:SignedSignatureProperties/etsi:SignerRole/etsi:ClaimedRoles/etsi:ClaimedRole/t:test",
                        prefix2Ns, "test");
    }

}
