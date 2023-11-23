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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.crypto.dsig.spec.XPathType;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.ValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureFormatException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper.XPathAndFilter;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidContentHashException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidKeyException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidValueException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.processor.XmlSignatureConfiguration;
import org.apache.camel.component.xmlsecurity.util.EnvelopingXmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.util.SameDocumentUriDereferencer;
import org.apache.camel.component.xmlsecurity.util.TestKeystore;
import org.apache.camel.component.xmlsecurity.util.TimestampProperty;
import org.apache.camel.component.xmlsecurity.util.ValidationFailedHandlerIgnoreManifestFailures;
import org.apache.camel.component.xmlsecurity.util.XmlSignature2Message2MessageWithTimestampProperty;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.processor.validation.SchemaValidationException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class XmlSignatureTest extends CamelTestSupport {

    protected static String payload;
    private static boolean includeNewLine;
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
        registry.bind("canonicalizationMethod1", getCanonicalizationMethod());
        registry.bind("selector", KeySelector.singletonKeySelector(keyPair.getPublic()));
        registry.bind("selectorKeyValue", getKeyValueKeySelector());
        registry.bind("transformsXPath2", getTransformsXPath2());
        registry.bind("transformsXsltXPath", getTransformsXsltXpath());
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
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[] { new RouteBuilder() {
            public void configure() {
                // START SNIPPET: enveloping XML signature
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:enveloping").to(getSignerEndpointURIEnveloping()).to("mock:signed")
                        .to(getVerifierEndpointURIEnveloping())
                        .to("mock:result");
                // END SNIPPET: enveloping XML signature
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: enveloping XML signature with plain text
                // message body
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:plaintext")
                        .to("xmlsecurity-sign:plaintext?keyAccessor=#accessor&plainText=true&plainTextEncoding=UTF-8")
                        .to("xmlsecurity-verify:plaintext?keySelector=#selector").to("mock:result");
                // END SNIPPET: enveloping XML signature with plain text message
                // body
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: enveloped XML signature
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:enveloped").to(getSignerEndpointURIEnveloped()).to("mock:signed")
                        .to(getVerifierEndpointURIEnveloped())
                        .to("mock:result");
                // END SNIPPET: enveloped XML signature
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: canonicalization
                // we can set the configuration properties explicitly on the
                // endpoint instances.
                context.getEndpoint("xmlsecurity-sign:canonicalization?canonicalizationMethod=#canonicalizationMethod1",
                        XmlSignerEndpoint.class).getConfiguration().setKeyAccessor(getKeyAccessor(keyPair.getPrivate()));
                context.getEndpoint("xmlsecurity-sign:canonicalization?canonicalizationMethod=#canonicalizationMethod1",
                        XmlSignerEndpoint.class).getConfiguration()
                        .setSignatureAlgorithm("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
                context.getEndpoint("xmlsecurity-verify:canonicalization", XmlVerifierEndpoint.class).getConfiguration()
                        .setKeySelector(
                                KeySelector.singletonKeySelector(keyPair.getPublic()));
                from("direct:canonicalization").to(
                        "xmlsecurity-sign:canonicalization?canonicalizationMethod=#canonicalizationMethod1",
                        "xmlsecurity-verify:canonicalization", "mock:result");
                // END SNIPPET: canonicalization
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: signature and digest algorithm
                from("direct:signaturedigestalgorithm")
                        .to("xmlsecurity-sign:signaturedigestalgorithm?keyAccessor=#accessor"
                            + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512&digestAlgorithm=http://www.w3.org/2001/04/xmlenc#sha512",
                                "xmlsecurity-verify:signaturedigestalgorithm?keySelector=#selector")
                        .to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: transforms XPath2
                from("direct:transformsXPath2").to(
                        "xmlsecurity-sign:transformsXPath2?keyAccessor=#accessor&transformMethods=#transformsXPath2",
                        "xmlsecurity-verify:transformsXPath2?keySelector=#selector").to("mock:result");
                // END SNIPPET: transform XPath
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: transforms XSLT,XPath
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:transformsXsltXPath").to(
                        "xmlsecurity-sign:transformsXsltXPath?keyAccessor=#accessor&transformMethods=#transformsXsltXPath",
                        "xmlsecurity-verify:transformsXsltXPath?keySelector=#selector").to("mock:result");
                // END SNIPPET: transforms XSLT,XPath
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: cryptocontextprops
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:cryptocontextprops")
                        .to("xmlsecurity-verify:cryptocontextprops?keySelector=#selectorKeyValue&cryptoContextProperties=#cryptoContextProperties")
                        .to("mock:result");
                // END SNIPPET: cryptocontextprops
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: URI dereferencer
                from("direct:uridereferencer")
                        .to("xmlsecurity-sign:uriderferencer?keyAccessor=#accessor&uriDereferencer=#uriDereferencer")
                        .to("xmlsecurity-verify:uridereferencer?keySelector=#selector&uriDereferencer=#uriDereferencer")
                        .to("mock:result");
                // END SNIPPET: URI dereferencer
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: keyAccessorKeySelectorDefault
                from("direct:keyAccessorKeySelectorDefault")
                        .to("xmlsecurity-sign:keyAccessorKeySelectorDefault?keyAccessor=#keyAccessorDefault&addKeyInfoReference=true")
                        .to("xmlsecurity-verify:keyAccessorKeySelectorDefault?keySelector=#keySelectorDefault")
                        .to("mock:result");
                // END SNIPPET: keyAccessorKeySelectorDefault
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: xmlSignatureChecker
                onException(XmlSignatureInvalidException.class).handled(false).to("mock:exception");
                from("direct:xmlSignatureChecker")
                        .to("xmlsecurity-verify:xmlSignatureChecker?keySelector=#selectorKeyValue&xmlSignatureChecker=#envelopingSignatureChecker")
                        .to("mock:result");
                // END SNIPPET: xmlSignatureChecker
            }
        }, new RouteBuilder() {
            public void configure() { //
                // START SNIPPET: properties
                from("direct:props")
                        .to("xmlsecurity-sign:properties?keyAccessor=#accessor&properties=#signatureProperties")
                        .to("xmlsecurity-verify:properties?keySelector=#selector&xmlSignature2Message=#xmlSignature2MessageWithTimestampProperty")
                        .to("mock:result");
                // END SNIPPET: properties
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: verify output node search element name
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:outputnodesearchelementname").to(
                        "xmlsecurity-verify:outputnodesearchelementname?keySelector=#selectorKeyValue"
                                                              + "&outputNodeSearchType=ElementName&outputNodeSearch={http://test/test}root&removeSignatureElements=true")
                        .to("mock:result");
                // END SNIPPET: verify output node search element name
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: verify output node search xpath
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:outputnodesearchxpath")
                        .to("xmlsecurity-verify:outputnodesearchxpath?keySelector=#selectorKeyValue&outputNodeSearchType=XPath&outputNodeSearch=#nodesearchxpath&removeSignatureElements=true")
                        .to("mock:result");
                // END SNIPPET: verify output node search xpath
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: validationFailedHandler
                from("direct:validationFailedHandler")
                        .to("xmlsecurity-verify:validationFailedHandler?keySelector=#selectorKeyValue&validationFailedHandler=#validationFailedHandlerIgnoreManifestFailures")
                        .to("mock:result");
                // END SNIPPET: validationFailedHandler
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: further parameters
                from("direct:furtherparams")
                        .to("xmlsecurity-sign:furtherparams?keyAccessor=#accessor&prefixForXmlSignatureNamespace=digsig&disallowDoctypeDecl=false")
                        .to("xmlsecurity-verify:bfurtherparams?keySelector=#selector&disallowDoctypeDecl=false")
                        .to("mock:result");
                // END SNIPPET: further parameters
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: signer invalid keyexception
                onException(XmlSignatureInvalidKeyException.class).handled(true).to("mock:exception");
                from("direct:signexceptioninvalidkey").to(
                        "xmlsecurity-sign:signexceptioninvalidkey?signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512")
                        .to("mock:result");
                // END SNIPPET: signer invalid keyexception
            }
        }, new RouteBuilder() {
            public void configure() {
                // START SNIPPET: signer exceptions
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:signexceptions")
                        .to("xmlsecurity-sign:signexceptions?keyAccessor=#accessor&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512")
                        .to("mock:result");
                // END SNIPPET: signer exceptions
            }
        }, new RouteBuilder() {
            public void configure() {
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:noSuchAlgorithmException")
                        .to("xmlsecurity-sign:noSuchAlgorithmException?keyAccessor=#accessor&signatureAlgorithm=wrongalgorithm&digestAlgorithm=http://www.w3.org/2001/04/xmlenc#sha512")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() {
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:verifyexceptions").to("xmlsecurity-verify:verifyexceptions?keySelector=#selector")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() {
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:verifyInvalidKeyException")
                        .to("xmlsecurity-verify:verifyInvalidKeyException?keySelector=#selector").to(
                                "mock:result");
            }
        }, new RouteBuilder() {
            public void configure() {
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:invalidhash").to(
                        "xmlsecurity-verify:invalidhash?keySelector=#selectorKeyValue&baseUri=#baseUri&secureValidation=false")
                        .to(
                                "mock:result");
            }
        }, createDetachedRoute(), createRouteForEnvelopedWithParentXpath() };
    }

    RouteBuilder createDetachedRoute() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: detached XML signature
                onException(Exception.class).handled(false).to("mock:exception");
                from("direct:detached")
                        .to("xmlsecurity-sign:detached?keyAccessor=#keyAccessorDefault&xpathsToIdAttributes=#xpathsToIdAttributes&"//
                            + "schemaResourceUri=org/apache/camel/component/xmlsecurity/Test.xsd&signatureId=&clearHeaders=false")
                        .to("mock:result")
                        .to("xmlsecurity-verify:detached?keySelector=#keySelectorDefault&schemaResourceUri=org/apache/camel/component/xmlsecurity/Test.xsd")
                        .to("mock:verified");
                // END SNIPPET: detached XML signature
            }
        };
    }

    private RouteBuilder createRouteForEnvelopedWithParentXpath() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: enveloped XML signature with parent XPath
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:envelopedParentXpath")
                        .to("xmlsecurity-sign:enveloped?keyAccessor=#accessor&parentXpath=#parentXpathBean")
                        .to("mock:signed").to(getVerifierEndpointURIEnveloped()).to("mock:result");
                // END SNIPPET: enveloped XML signature with parent XPath
            }
        };
    }

    @Test
    public void testEnvelopingSignature() throws Exception {
        setupMock();
        sendBody("direct:enveloping", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testEnvelopedSignatureWithTransformHeader() throws Exception {
        setupMock(payload);
        sendBody("direct:enveloped", payload, Collections.<String, Object> singletonMap(
                XmlSignatureConstants.HEADER_TRANSFORM_METHODS,
                "http://www.w3.org/2000/09/xmldsig#enveloped-signature,http://www.w3.org/TR/2001/REC-xml-c14n-20010315"));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testEnvelopingSignatureWithPlainText() throws Exception {
        String text = "plain test text";
        setupMock(text);
        sendBody("direct:plaintext", text);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testEnvelopingSignatureWithPlainTextSetByHeaders() throws Exception {
        String text = "plain test text";
        setupMock(text);
        Map<String, Object> headers = new TreeMap<>();
        headers.put(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT, Boolean.TRUE);
        headers.put(XmlSignatureConstants.HEADER_PLAIN_TEXT_ENCODING, "UTF-8");
        sendBody("direct:enveloping", text, headers);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExceptionSignatureForPlainTextWithWrongEncoding() throws Exception {
        String text = "plain test text";
        MockEndpoint mock = setupExceptionMock();
        Map<String, Object> headers = new TreeMap<>();
        headers.put(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT, Boolean.TRUE);
        headers.put(XmlSignatureConstants.HEADER_PLAIN_TEXT_ENCODING, "wrongEncoding");
        sendBody("direct:enveloping", text, headers);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, UnsupportedEncodingException.class);
    }

    @Test
    public void testEnvelopedSignature() throws Exception {
        setupMock(payload);
        sendBody("direct:enveloped", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExceptionEnvelopedSignatureWithWrongParent() throws Exception {
        // payload root element renamed to a -> parent name in route definition
        // does not fit
        String payload
                = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a xmlns=\"http://test/test\"><test>Test Message</test></a>";

        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:enveloped", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testExceptionEnvelopedSignatureWithPlainTextPayload() throws Exception {
        // payload root element renamed to a -> parent name in route definition
        // does not fit
        String payload = "plain text Message";
        Map<String, Object> headers = new HashMap<>(1);
        headers.put(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT, Boolean.TRUE);
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:enveloped", payload, headers);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    /**
     * The parameter can also be configured via {@link XmlSignatureConfiguration#setOmitXmlDeclaration(Boolean)}
     */
    @Test
    public void testOmitXmlDeclarationViaHeader() throws Exception {
        String payloadOut = "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
        setupMock(payloadOut);
        Map<String, Object> headers = new TreeMap<>();
        headers.put(XmlSignatureConstants.HEADER_OMIT_XML_DECLARATION, Boolean.TRUE);
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchelementname", payload, headers);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testkeyAccessorKeySelectorDefault() throws Exception {
        setupMock();
        sendBody("direct:keyAccessorKeySelectorDefault", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetCanonicalizationMethodInRouteDefinition() throws Exception {
        setupMock();
        sendBody("direct:canonicalization", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetDigestAlgorithmInRouteDefinition() throws Exception {

        setupMock();
        sendBody("direct:signaturedigestalgorithm", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSetTransformMethodXpath2InRouteDefinition() throws Exception {
        // example from http://www.w3.org/TR/2002/REC-xmldsig-filter2-20021108/
        String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                         + (includeNewLine ? "\n" : "")
                         + "<Document xmlns=\"http://test/test\">                             "
                         + "<ToBeSigned>                                                     "
                         + "   <!-- comment -->                                              "
                         + "   <Data>1</Data>                                                "
                         + "   <NotToBeSigned>                                               "
                         + "     <ReallyToBeSigned>                                          "
                         + "       <!-- comment -->                                          "
                         + "       <Data>2</Data>                                            "
                         + "     </ReallyToBeSigned>                                         "
                         + "   </NotToBeSigned>                                              "
                         + " </ToBeSigned>                                                   "
                         + " <ToBeSigned>                                                    "
                         + "   <Data>3</Data>                                                "
                         + "   <NotToBeSigned>                                               "
                         + "     <Data>4</Data>                                              "
                         + "   </NotToBeSigned>                                              "
                         + " </ToBeSigned>                                                   " + "</Document>";

        setupMock(payload);
        sendBody("direct:transformsXPath2", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    // Secure Validation is enabled and so this should fail
    @Test
    public void testSetTransformMethodXsltXpathInRouteDefinition() throws Exception {
        // byte[] encoded = Base64.encode("Test Message".getBytes("UTF-8"));
        // String contentBase64 = new String(encoded, "UTF-8");
        // String payload =
        // "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root xmlns=\"http://test/test\"><test></test></root>";
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:transformsXsltXPath", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testProperties() throws Exception {
        setupMock();
        sendBody("direct:props", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testVerifyOutputNodeSearchElementName() throws Exception {
        setupMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchelementname", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testVerifyExceptionOutputNodeSearchElementNameInvalidFormat1() throws Exception {
        XmlVerifierEndpoint endpoint = context
                .getEndpoint("xmlsecurity-verify:outputnodesearchelementname?keySelector=#selectorKeyValue"
                             + "&outputNodeSearchType=ElementName&outputNodeSearch={http://test/test}root&removeSignatureElements=true",
                        XmlVerifierEndpoint.class);
        endpoint.getConfiguration().setOutputNodeSearch("{wrongformat"); // closing '}' missing
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchelementname", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testVerifyExceptionOutputNodeSearchElementNameInvalidFormat2() throws Exception {
        context.getEndpoint(
                "xmlsecurity-verify:outputnodesearchelementname?keySelector=#selectorKeyValue"
                            + "&outputNodeSearchType=ElementName&outputNodeSearch={http://test/test}root&removeSignatureElements=true",
                XmlVerifierEndpoint.class).getConfiguration().setOutputNodeSearch("{wrongformat}");
        // local name missing
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchelementname", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchWrongElementName() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchelementname", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchElementNameMoreThanOneOutputElement() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream(
                        "/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSigWithSeveralElementsWithNameRoot.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchelementname", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testVerifyOutputNodeSearchXPath() throws Exception {
        setupMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchxpath", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchXPathWithNoResultNode() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchxpath", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchXPathMoreThanOneOutputElement() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream(
                        "/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSigWithSeveralElementsWithNameRoot.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:outputnodesearchxpath", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testInvalidKeyException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        // wrong key type
        setUpKeys("DSA", 512);
        context.getEndpoint(
                "xmlsecurity-sign:signexceptioninvalidkey?signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512",
                XmlSignerEndpoint.class).getConfiguration().setKeyAccessor(getKeyAccessor(keyPair.getPrivate()));
        sendBody("direct:signexceptioninvalidkey", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureInvalidKeyException.class, null);
    }

    @Test
    public void testSignatureFormatException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:signexceptions", "wrongFormatedPayload");
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testNoSuchAlgorithmException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:noSuchAlgorithmException", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class, NoSuchAlgorithmException.class);
    }

    @Test
    public void testVerifyFormatExceptionNoXml() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:verifyexceptions", "wrongFormatedPayload");
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testVerifyFormatExceptionNoXmlWithoutSignatureElement() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:verifyexceptions", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NoSignature></NoSignature>");
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    @Disabled("Cannot resolve <Reference URI=\"testFile.txt\">")
    public void testVerifyInvalidContentHashException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload
                = XmlSignatureTest.class.getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleDetached.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:invalidhash", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureInvalidContentHashException.class, null);
    }

    @Test
    public void testVerifyMantifestInvalidContentHashException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ManifestTest_TamperedContent.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:invalidhash", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureInvalidContentHashException.class, null);
    }

    @Test
    public void testVerifySetCryptoContextProperties() throws Exception {
        // although the content referenced by the manifest was tempered, this is
        // not detected by
        // the core validation because the manifest validation is switched off
        // by the crypto context properties
        setupMock("some text tampered");
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ManifestTest_TamperedContent.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:cryptocontextprops", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    @Disabled("Not all JDKs have provider to verify this key")
    public void testVerifySignatureInvalidValueException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        setUpKeys("DSA", 512);
        context.getEndpoint("xmlsecurity-verify:verifyexceptions", XmlVerifierEndpoint.class).getConfiguration().setKeySelector(
                KeySelector.singletonKeySelector(keyPair.getPublic()));
        // payload needs DSA key
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:verifyexceptions", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureInvalidValueException.class, null);
    }

    @Test
    public void testVerifyInvalidKeyException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:verifyInvalidKeyException", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureInvalidKeyException.class, null);
    }

    @Test
    public void testUriDereferencerAndBaseUri() throws Exception {
        setupMock();
        sendBody("direct:uridereferencer", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testVerifyXmlSignatureChecker() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:xmlSignatureChecker", payload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureInvalidException.class, null);
    }

    @Test
    public void testVerifyValidationFailedHandler() throws Exception {
        setupMock("some text tampered");
        InputStream payload = XmlSignatureTest.class
                .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ManifestTest_TamperedContent.xml");
        assertNotNull(payload, "Cannot load payload");
        sendBody("direct:validationFailedHandler", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFurtherParameters() throws Exception {
        setupMock(payload);
        String payloadWithDTDoctype = "<?xml version=\'1.0\'?>" + "<!DOCTYPE Signature SYSTEM "
                                      + "\"src/test/resources/org/apache/camel/component/xmlsecurity/xmldsig-core-schema.dtd\" [ <!ENTITY dsig "
                                      + "\"http://www.w3.org/2000/09/xmldsig#\"> ]>"
                                      + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";

        sendBody("direct:furtherparams", payloadWithDTDoctype);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testReferenceUriWithIdAttributeInTheEnvelopedCase() throws Exception {

        XmlSignerEndpoint endpoint = getDetachedSignerEndpoint();
        endpoint.getConfiguration().setParentLocalName("root");
        endpoint.getConfiguration().setParentNamespace("http://test");
        endpoint.getConfiguration().setXpathsToIdAttributes(null);

        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        sendBody("direct:detached", detachedPayload,
                Collections.singletonMap(XmlSignatureConstants.HEADER_CONTENT_REFERENCE_URI, (Object) "#myID"));
        MockEndpoint.assertIsSatisfied(context);
        String expectedPartContent = "<ds:Reference URI=\"#myID\">";
        checkBodyContains(mock, expectedPartContent);
    }

    @Test
    public void testDetachedSignature() throws Exception {
        testDetachedSignatureInternal();
    }

    @Test
    public void testDetachedSignatureWitTransformHeader() throws Exception {
        testDetachedSignatureInternal(Collections.singletonMap(XmlSignatureConstants.HEADER_TRANSFORM_METHODS,
                (Object) "http://www.w3.org/2000/09/xmldsig#enveloped-signature,http://www.w3.org/TR/2001/REC-xml-c14n-20010315"));
    }

    @Test
    public void testSignatureIdAtributeNull() throws Exception {
        // the signature Id parameter must be empty, this is set in the URI
        // already
        Element sigEle = testDetachedSignatureInternal();
        Attr attr = sigEle.getAttributeNode("Id");
        assertNull(attr, "Signature element contains Id attribute");
    }

    @Test
    public void testSignatureIdAttribute() throws Exception {
        String signatureId = "sigId";
        XmlSignerEndpoint endpoint = getDetachedSignerEndpoint();
        endpoint.getConfiguration().setSignatureId(signatureId);
        Element sigEle = testDetachedSignatureInternal();
        String value = sigEle.getAttribute("Id");
        assertNotNull("Signature Id is null", value);
        assertEquals(signatureId, value);
    }

    @Test
    public void testSignatureIdAttributeGenerated() throws Exception {
        String signatureId = null;
        XmlSignerEndpoint endpoint = getDetachedSignerEndpoint();
        endpoint.getConfiguration().setSignatureId(signatureId);
        Element sigEle = testDetachedSignatureInternal();
        String value = sigEle.getAttribute("Id");
        assertNotNull("Signature Id is null", value);
        assertTrue(value.startsWith("_"), "Signature Id value does not start with '_'");
    }

    private Element testDetachedSignatureInternal()
            throws InterruptedException, XPathExpressionException, SAXException, IOException,
            ParserConfigurationException {
        return testDetachedSignatureInternal(Collections.<String, Object> emptyMap());
    }

    private Element testDetachedSignatureInternal(Map<String, Object> headers)
            throws InterruptedException, XPathExpressionException, SAXException, IOException,
            ParserConfigurationException {

        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MockEndpoint mockVerified = getMockEndpoint("mock:verified");
        mockVerified.expectedBodiesReceived(detachedPayload);
        sendBody("direct:detached", detachedPayload, headers);
        MockEndpoint.assertIsSatisfied(context);
        Map<String, String> namespaceMap = new TreeMap<>();
        namespaceMap.put("ns", "http://test");
        namespaceMap.put("ds", XMLSignature.XMLNS);
        Object obj = checkXpath(mock, "ns:root/ds:Signature", namespaceMap);
        Element sigEle = (Element) obj;
        return sigEle;
    }

    @Test
    public void testDetachedSignatureComplexSchema() throws Exception {

        String xpath1exp = "/ns:root/test/ns1:B/C/@ID";
        String xpath2exp = "/ns:root/test/@ID";
        testDetached2Xpaths(xpath1exp, xpath2exp);
    }

    /**
     * Checks that the processor sorts the xpath expressions in such a way that elements with deeper hierarchy level are
     * signed first.
     *
     */
    @Test
    public void testDetachedSignatureWrongXPathOrder() throws Exception {

        String xpath2exp = "/ns:root/test/ns1:B/C/@ID";
        String xpath1exp = "/ns:root/test/@ID";
        testDetached2Xpaths(xpath1exp, xpath2exp);
    }

    void testDetached2Xpaths(String xpath1exp, String xpath2exp)
            throws InterruptedException, XPathExpressionException, SAXException,
            IOException, ParserConfigurationException {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><test ID=\"myID\"><b>bValue</b><ts:B xmlns:ts=\"http://testB\"><C ID=\"cID\"><D>dvalue</D></C></ts:B></test></ns:root>";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MockEndpoint mockVerified = getMockEndpoint("mock:verified");
        mockVerified.expectedBodiesReceived(detachedPayload);
        Map<String, Object> headers = new TreeMap<>();
        headers.put(XmlSignatureConstants.HEADER_SCHEMA_RESOURCE_URI, "org/apache/camel/component/xmlsecurity/TestComplex.xsd");
        Map<String, String> namespaceMap = new TreeMap<>();
        namespaceMap.put("ns", "http://test");
        namespaceMap.put("ns1", "http://testB");
        XPathFilterParameterSpec xpath1 = XmlSignatureHelper.getXpathFilter(xpath1exp, namespaceMap);
        XPathFilterParameterSpec xpath2 = XmlSignatureHelper.getXpathFilter(xpath2exp, namespaceMap);

        List<XPathFilterParameterSpec> xpaths = new ArrayList<>();
        xpaths.add(xpath1);
        xpaths.add(xpath2);
        headers.put(XmlSignatureConstants.HEADER_XPATHS_TO_ID_ATTRIBUTES, xpaths);
        sendBody("direct:detached", detachedPayload, headers);
        MockEndpoint.assertIsSatisfied(context);
        Map<String, String> namespaceMap2 = new TreeMap<>();
        namespaceMap2.put("ns", "http://test");
        namespaceMap2.put("ds", XMLSignature.XMLNS);
        namespaceMap2.put("nsB", "http://testB");
        checkXpath(mock, "ns:root/test/nsB:B/ds:Signature", namespaceMap2);
        checkXpath(mock, "ns:root/ds:Signature", namespaceMap2);
    }

    @Test
    public void testExceptionEnvelopedAndDetached() throws Exception {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + //
                                 "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        XmlSignerEndpoint endpoint = getDetachedSignerEndpoint();
        String parentLocalName = "parent";
        endpoint.getConfiguration().setParentLocalName(parentLocalName);
        MockEndpoint mock = setupExceptionMock();
        mock.expectedMessageCount(1);
        sendBody("direct:detached", detachedPayload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(
                mock,
                XmlSignatureException.class,
                "The configuration of the XML signer component is wrong. The parent local name "
                                             + parentLocalName
                                             + " for an enveloped signature and the XPATHs to ID attributes for a detached signature are specified. You must not specify both parameters.",
                null);
    }

    @Test
    public void testExceptionSchemaValidation() throws Exception {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><error>bValue</error></a></ns:root>";
        MockEndpoint mock = setupExceptionMock();
        mock.expectedMessageCount(1);
        sendBody("direct:detached", detachedPayload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, SchemaValidationException.class, null);
    }

    @Test
    public void testEceptionDetachedNoXmlSchema() throws Exception {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        XmlSignerEndpoint endpoint = getDetachedSignerEndpoint();
        endpoint.getConfiguration().setSchemaResourceUri(null);
        MockEndpoint mock = setupExceptionMock();
        mock.expectedMessageCount(1);
        sendBody("direct:detached", detachedPayload);
        MockEndpoint.assertIsSatisfied(context);
        checkThrownException(mock, XmlSignatureException.class,
                "The configruation of the XML Signature component is wrong: No XML schema specified in the detached case",
                null);
    }

    @Test
    public void testExceptionDetachedXpathInvalid() throws Exception {
        String wrongXPath = "n1:p/a"; // namespace prefix is not defined
        MockEndpoint mock = testXpath(wrongXPath);
        checkThrownException(mock, XmlSignatureException.class,
                "The configured xpath expression " + wrongXPath + " is invalid.",
                XPathExpressionException.class);
    }

    @Test
    public void testExceptionDetachedXPathNoIdAttribute() throws Exception {
        String value = "not id";
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\" stringAttr=\"" + value
                                 + "\"><b>bValue</b></a></ns:root>";
        String xPath = "a/@stringAttr";

        MockEndpoint mock = testXpath(xPath, detachedPayload);
        checkThrownException(mock, XmlSignatureException.class,
                "Wrong configured xpath expression for ID attributes: The evaluation of the xpath expression " + xPath
                                                                + " resulted in an attribute which is not of type ID. The attribute value is "
                                                                + value + ".",
                null);
    }

    @Test
    public void testExceptionDetachedXpathNoAttribute() throws Exception {
        String xPath = "a"; // Element a
        MockEndpoint mock = testXpath(xPath);
        checkThrownException(mock, XmlSignatureException.class,
                "Wrong configured xpath expression for ID attributes: The evaluation of the xpath expression " + xPath
                                                                + " returned a node which was not of type Attribute.",
                null);
    }

    @Test
    public void testExceptionDetachedXPathNoResult() throws Exception {
        String xPath = "a/@stringAttr"; // for this xpath there is no result
        MockEndpoint mock = testXpath(xPath);
        checkThrownException(
                mock,
                XmlSignatureException.class,
                "No element to sign found in the detached case. No node found for the configured xpath expressions "
                                             + xPath
                                             + ". Either the configuration of the XML signature component is wrong or the incoming message has not the correct structure.",
                null);
    }

    private MockEndpoint testXpath(String xPath) throws InterruptedException {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        return testXpath(xPath, detachedPayload);
    }

    private MockEndpoint testXpath(String xPath, String detachedPayload) throws InterruptedException {
        MockEndpoint mock = setupExceptionMock();
        mock.expectedMessageCount(1);
        List<XPathFilterParameterSpec> list = Collections.singletonList(XmlSignatureHelper.getXpathFilter(xPath, null));
        sendBody("direct:detached", detachedPayload,
                Collections.singletonMap(XmlSignatureConstants.HEADER_XPATHS_TO_ID_ATTRIBUTES, (Object) list));
        MockEndpoint.assertIsSatisfied(context);
        return mock;
    }

    @Test
    public void testExceptionDetachedNoParent() throws Exception {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                 + (includeNewLine ? "\n" : "")
                                 + "<ns:root ID=\"rootId\" xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        String xPath = "//@ID";
        String localName = "root";
        String namespaceURI = "http://test";
        String referenceUri = "#rootId";
        MockEndpoint mock = testXpath(xPath, detachedPayload);
        checkThrownException(mock, XmlSignatureException.class,
                "Either the configuration of the XML Signature component is wrong or the incoming document has an invalid structure: The element "
                                                                + localName + "{" + namespaceURI
                                                                + "} which is referenced by the reference URI " + referenceUri
                                                                + " has no parent element. The element must have a parent element in the configured detached case.",
                null);
    }

    @Test
    public void testOutputXmlEncodingEnveloping() throws Exception {

        String inputEncoding = "UTF-8";
        String signerEncoding = "UTF-16";
        String outputEncoding = "ISO-8859-1"; // latin 1

        String signerEndpointUri = getSignerEndpointURIEnveloping();
        String verifierEndpointUri = getVerifierEndpointURIEnveloping();

        String directStart = "direct:enveloping";

        checkOutputEncoding(inputEncoding, signerEncoding, outputEncoding, signerEndpointUri, verifierEndpointUri, directStart);
    }

    String getVerifierEndpointURIEnveloping() {
        return "xmlsecurity-verify:enveloping?keySelector=#selector";
    }

    String getSignerEndpointURIEnveloping() {
        return "xmlsecurity-sign:enveloping?keyAccessor=#accessor&schemaResourceUri=";
    }

    @Test
    public void testOutputXmlEncodingEnveloped() throws Exception {

        String inputEncoding = "UTF-8";
        String signerEncoding = "UTF-16";
        String outputEncoding = "ISO-8859-1"; // latin 1

        String signerEndpointUri = getSignerEndpointURIEnveloped();
        String verifierEndpointUri = getVerifierEndpointURIEnveloped();

        String directStart = "direct:enveloped";

        checkOutputEncoding(inputEncoding, signerEncoding, outputEncoding, signerEndpointUri, verifierEndpointUri, directStart);
    }

    String getVerifierEndpointURIEnveloped() {
        return "xmlsecurity-verify:enveloped?keySelector=#selector";
    }

    String getSignerEndpointURIEnveloped() {
        return "xmlsecurity-sign:enveloped?keyAccessor=#accessor&parentLocalName=root&parentNamespace=http://test/test";
    }

    private byte[] getPayloadForEncoding(String encoding) {
        String s = "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>"
                   + (includeNewLine ? "\n" : "")
                   + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
        return s.getBytes(Charset.forName(encoding));
    }

    @Test
    public void testExceptionParentLocalNameAndXPathSet() throws Exception {

        XmlSignerEndpoint endpoint = getSignatureEncpointForSignException();
        MockEndpoint mock = setupExceptionMock();
        try {
            endpoint.getConfiguration().setParentXpath(getNodeSerachXPath());
            endpoint.getConfiguration().setParentLocalName("root");
            sendBody("direct:signexceptions", payload);
            MockEndpoint.assertIsSatisfied(context);
            checkThrownException(mock, XmlSignatureException.class, "The configuration of the XML signer component is wrong. " + //
                                                                    "The parent local name root and the parent XPath //pre:root are specified. You must not specify both parameters.",
                    null);
        } finally {
            endpoint.getConfiguration().setParentXpath(null);
            endpoint.getConfiguration().setParentLocalName(null);
        }
    }

    @Test
    public void testExceptionXpathsToIdAttributesNameAndXPathSet() throws Exception {

        XmlSignerEndpoint endpoint = getSignatureEncpointForSignException();
        MockEndpoint mock = setupExceptionMock();
        try {
            endpoint.getConfiguration().setParentXpath(getNodeSerachXPath());
            List<XPathFilterParameterSpec> xpaths
                    = Collections.singletonList(XmlSignatureHelper.getXpathFilter("/ns:root/a/@ID", null));
            endpoint.getConfiguration().setXpathsToIdAttributes(xpaths);
            sendBody("direct:signexceptions", payload);
            MockEndpoint.assertIsSatisfied(context);
            checkThrownException(
                    mock,
                    XmlSignatureException.class,
                    "The configuration of the XML signer component is wrong. " + //
                                                 "The parent XPath //pre:root for an enveloped signature and the XPATHs to ID attributes for a detached signature are specified. You must not specify both parameters.",
                    null);
        } finally {
            endpoint.getConfiguration().setParentXpath(null);
            endpoint.getConfiguration().setXpathsToIdAttributes(null);
        }
    }

    @Test
    public void testExceptionInvalidParentXpath() throws Exception {

        XmlSignerEndpoint endpoint = getSignatureEncpointForSignException();
        MockEndpoint mock = setupExceptionMock();
        try {
            endpoint.getConfiguration().setParentXpath(XmlSignatureHelper.getXpathFilter("//pre:root", null)); // invalid xpath: namespace-prefix mapping is missing
            sendBody("direct:signexceptions", payload);
            MockEndpoint.assertIsSatisfied(context);
            checkThrownException(mock, XmlSignatureException.class,
                    "The parent XPath //pre:root is wrongly configured: The XPath //pre:root is invalid.", null);
        } finally {
            endpoint.getConfiguration().setParentXpath(null);
        }
    }

    @Test
    public void testExceptionParentXpathWithNoResult() throws Exception {

        XmlSignerEndpoint endpoint = getSignatureEncpointForSignException();
        MockEndpoint mock = setupExceptionMock();
        try {
            endpoint.getConfiguration().setParentXpath(XmlSignatureHelper.getXpathFilter("//root", null)); // xpath with no result
            sendBody("direct:signexceptions", payload);
            MockEndpoint.assertIsSatisfied(context);
            checkThrownException(mock, XmlSignatureException.class,
                    "The parent XPath //root returned no result. Check the configuration of the XML signer component.", null);
        } finally {
            endpoint.getConfiguration().setParentXpath(null);
        }
    }

    XmlSignerEndpoint getSignatureEncpointForSignException() {
        XmlSignerEndpoint endpoint = (XmlSignerEndpoint) context()
                .getEndpoint("xmlsecurity-sign:signexceptions?keyAccessor=#accessor" + //
                             "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512");
        return endpoint;
    }

    @Test
    public void testExceptionParentXpathWithNoElementResult() throws Exception {

        XmlSignerEndpoint endpoint = getSignatureEncpointForSignException();
        MockEndpoint mock = setupExceptionMock();
        try {
            String myPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                               + (includeNewLine ? "\n" : "")
                               + "<ns:root ID=\"rootId\" xmlns:ns=\"http://test\"></ns:root>";
            endpoint.getConfiguration().setParentXpath(
                    XmlSignatureHelper.getXpathFilter("/pre:root/@ID", Collections.singletonMap("pre", "http://test"))); // xpath with no element result
            sendBody("direct:signexceptions", myPayload);
            MockEndpoint.assertIsSatisfied(context);
            checkThrownException(mock, XmlSignatureException.class,
                    "The parent XPath /pre:root/@ID returned no element. Check the configuration of the XML signer component.",
                    null);
        } finally {
            endpoint.getConfiguration().setParentXpath(null);
        }
    }

    @Test
    public void testEnvelopedSignatureWithParentXpath() throws Exception {
        String myPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                           + (includeNewLine ? "\n" : "")
                           + "<ns:root xmlns:ns=\"http://test\"><a>a1</a><a/><test>Test Message</test></ns:root>";
        setupMock(myPayload);
        sendBody("direct:envelopedParentXpath", myPayload);
        MockEndpoint.assertIsSatisfied(context);
    }

    XmlSignerEndpoint getDetachedSignerEndpoint() {
        XmlSignerEndpoint endpoint = (XmlSignerEndpoint) context().getEndpoint(
                "xmlsecurity-sign:detached?keyAccessor=#keyAccessorDefault&xpathsToIdAttributes=#xpathsToIdAttributes&"//
                                                                               + "schemaResourceUri=org/apache/camel/component/xmlsecurity/Test.xsd&signatureId=&clearHeaders=false");
        return endpoint;
    }

    private void checkOutputEncoding(
            String inputEncoding, String signerEncoding, String outputEncoding, String signerEndpointUri,
            String verifierEndpointUri, String directStart)
            throws InterruptedException, UnsupportedEncodingException {
        byte[] inputPayload = getPayloadForEncoding(inputEncoding);
        byte[] expectedPayload = getPayloadForEncoding(outputEncoding);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(expectedPayload);

        MockEndpoint mockSigned = getMockEndpoint("mock:signed");
        mock.expectedMessageCount(1);

        XmlSignerEndpoint endpointSigner = (XmlSignerEndpoint) context().getEndpoint(signerEndpointUri);

        XmlVerifierEndpoint endpoinVerifier = (XmlVerifierEndpoint) context().getEndpoint(verifierEndpointUri);
        try {
            endpointSigner.getConfiguration().setOutputXmlEncoding(signerEncoding);
            endpoinVerifier.getConfiguration().setOutputXmlEncoding(outputEncoding);
            sendBody(directStart, inputPayload);
            MockEndpoint.assertIsSatisfied(context);
            Message signedMessage = mockSigned.getExchanges().get(0).getIn();
            byte[] signedBytes = signedMessage.getBody(byte[].class);
            String signedPayload = new String(signedBytes, signerEncoding);
            assertTrue(signedPayload.contains(signerEncoding));
            String charsetHeaderSigner = signedMessage.getHeader(Exchange.CHARSET_NAME, String.class);
            assertEquals(signerEncoding, charsetHeaderSigner);
            String charsetHeaderVerifier = mock.getExchanges().get(0).getIn().getHeader(Exchange.CHARSET_NAME, String.class);
            assertEquals(outputEncoding, charsetHeaderVerifier);
        } finally {
            endpointSigner.getConfiguration().setOutputXmlEncoding(null);
            endpoinVerifier.getConfiguration().setOutputXmlEncoding(null);
        }
    }

    private void checkBodyContains(MockEndpoint mock, String expectedPartContent) {
        Message message = getMessage(mock);
        String body = message.getBody(String.class);
        assertNotNull(body);
        assertTrue(body.contains(expectedPartContent),
                "The message body " + body + " does not contain the expected string " + expectedPartContent);
    }

    private Object checkXpath(MockEndpoint mock, String xpathString, final Map<String, String> prefix2Namespace)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        Message mess = getMessage(mock);
        InputStream body = mess.getBody(InputStream.class);
        assertNotNull(body);
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        NamespaceContext nc = new NamespaceContext() {

            @SuppressWarnings("rawtypes")
            @Override
            public Iterator getPrefixes(String namespaceURI) {
                return null;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public String getNamespaceURI(String prefix) {
                return prefix2Namespace.get(prefix);
            }
        };
        xpath.setNamespaceContext(nc);
        XPathExpression expr = xpath.compile(xpathString);
        Object result = expr.evaluate(XmlSignatureHelper.newDocumentBuilder(true).parse(body), XPathConstants.NODE);
        assertNotNull(result, "The xpath " + xpathString + " returned a null value");
        return result;
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

    private void checkThrownException(
            MockEndpoint mock, Class<? extends Exception> cl, Class<? extends Exception> expectedCauseClass)
            throws Exception {
        checkThrownException(mock, cl, null, expectedCauseClass);
    }

    static void checkThrownException(
            MockEndpoint mock, Class<? extends Exception> cl, String expectedMessage,
            Class<? extends Exception> expectedCauseClass)
            throws Exception {
        Exception e = (Exception) mock.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull(e, "Expected excpetion " + cl.getName() + " missing");
        if (e.getClass() != cl) {
            String stackTrace = getStrackTrace(e);
            fail("Exception  " + cl.getName() + " excpected, but was " + e.getClass().getName() + ": " + stackTrace);
        }
        if (expectedMessage != null) {
            assertEquals(expectedMessage, e.getMessage());
        }
        if (expectedCauseClass != null) {
            Throwable cause = e.getCause();
            assertNotNull(cause, "Expected cause exception" + expectedCauseClass.getName() + " missing");
            if (expectedCauseClass != cause.getClass()) {
                fail("Cause exception " + expectedCauseClass.getName() + " expected, but was " + cause.getClass().getName()
                     + ": "
                     + getStrackTrace(e));
            }
        }
    }

    private static String getStrackTrace(Exception e) throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter w = new PrintWriter(os);
        e.printStackTrace(w);
        w.close();
        String stackTrace = new String(os.toByteArray(), "UTF-8");
        return stackTrace;
    }

    private MockEndpoint setupExceptionMock() {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.setExpectedMessageCount(1);
        MockEndpoint mockResult = getMockEndpoint("mock:result");
        mockResult.setExpectedMessageCount(0);
        return mock;
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
            MockEndpoint.assertIsSatisfied(XmlSignatureTest.this.context);
            return mock.getReceivedExchanges().get(0);
        } finally {
            context.stop();
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        setUpKeys("RSA", 1024);
        disableJMX();
        super.setUp();
    }

    public void setUpKeys(String algorithm, int keylength) {
        keyPair = getKeyPair(algorithm, keylength);
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

    public static KeyStore loadKeystore() throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream in = XmlSignatureTest.class.getResourceAsStream("/bob.keystore");
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
        List<String> inclusivePrefixes = new ArrayList<>(1);
        inclusivePrefixes.add("ds");
        return XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, inclusivePrefixes);
    }

    private List<AlgorithmMethod> getTransformsXPath2() {

        List<XPathAndFilter> list = new ArrayList<>(3);
        XPathAndFilter xpath1 = new XPathAndFilter("//n0:ToBeSigned", XPathType.Filter.INTERSECT.toString());
        list.add(xpath1);
        XPathAndFilter xpath2 = new XPathAndFilter("//n0:NotToBeSigned", XPathType.Filter.SUBTRACT.toString());
        list.add(xpath2);
        XPathAndFilter xpath3 = new XPathAndFilter("//n0:ReallyToBeSigned", XPathType.Filter.UNION.toString());
        list.add(xpath3);
        List<AlgorithmMethod> result = new ArrayList<>(2);
        result.add(XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
        result.add(XmlSignatureHelper.getXPath2Transform(list, getNamespaceMap()));
        return result;
    }

    private Map<String, String> getNamespaceMap() {
        Map<String, String> result = new HashMap<>(1);
        result.put("n0", "http://test/test");
        return result;
    }

    private List<AlgorithmMethod> getTransformsXsltXpath() {
        try {
            AlgorithmMethod transformXslt
                    = XmlSignatureHelper.getXslTransform("/org/apache/camel/component/xmlsecurity/xslt_test.xsl");
            Map<String, String> namespaceMap = new HashMap<>(1);
            namespaceMap.put("n0", "https://org.apache/camel/xmlsecurity/test");
            AlgorithmMethod transformXpath = XmlSignatureHelper.getXPathTransform("//n0:XMLSecurity/n0:Content", namespaceMap);
            // I removed base 64 transform because the JDK implementation does
            // not correctly support this transformation
            // AlgorithmMethod transformBase64 = helper.getBase64Transform();
            List<AlgorithmMethod> result = new ArrayList<>(3);
            result.add(XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
            result.add(transformXslt);
            result.add(transformXpath);
            // result.add(transformBase64);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

}
