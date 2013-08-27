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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
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
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.crypto.dsig.spec.XPathType;

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
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;



public class XmlSignatureTest extends CamelTestSupport {
    
    private static String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
    private KeyPair keyPair;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

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
        registry.bind("xmlSignature2MessageWithTimestampProperty",
                      getXmlSignature2MessageWithTimestampdProperty());
        registry.bind("validationFailedHandlerIgnoreManifestFailures",
                      getValidationFailedHandlerIgnoreManifestFailures());
        registry.bind("signatureProperties", getSignatureProperties());
        registry.bind("nodesearchxpath", getNodeSerachXPath());

        return registry;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: enveloping XML signature
                onException(XmlSignatureException.class).handled(true).to(
                    "mock:exception");
                from("direct:enveloping")
                    .to("xmlsecurity:sign://enveloping?keyAccessor=#accessor")
                    .to("xmlsecurity:verify://enveloping?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: enveloping XML signature
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: enveloping XML signature with plain text
                // message body
                onException(XmlSignatureException.class).handled(true).to(
                    "mock:exception");
                from("direct:plaintext")
                    .to("xmlsecurity:sign://plaintext?keyAccessor=#accessor&plainText=true&plainTextEncoding=UTF-8")
                    .to("xmlsecurity:verify://plaintext?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: enveloping XML signature with plain text message
                // body
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: enveloped XML signature
                onException(XmlSignatureException.class).handled(true).to(
                    "mock:exception");
                from("direct:enveloped")
                    .to("xmlsecurity:sign://enveloped?keyAccessor=#accessor&parentLocalName=root&parentNamespace=http://test/test")
                    .to("xmlsecurity:verify://enveloped?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: enveloped XML signature
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: canonicalization
                // we can set the configuration properties explicitly on the
                // endpoint instances.
                context.getEndpoint("xmlsecurity:sign://canonicalization?canonicalizationMethod=#canonicalizationMethod1",
                                    XmlSignerEndpoint.class).setKeyAccessor(getKeyAccessor(keyPair.getPrivate()));
                context.getEndpoint("xmlsecurity:sign://canonicalization?canonicalizationMethod=#canonicalizationMethod1",
                                    XmlSignerEndpoint.class).setSignatureAlgorithm(
                                        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
                context.getEndpoint("xmlsecurity:verify://canonicalization",
                                    XmlVerifierEndpoint.class).setKeySelector(KeySelector.singletonKeySelector(keyPair.getPublic()));
                from("direct:canonicalization")
                    .to("xmlsecurity:sign://canonicalization?canonicalizationMethod=#canonicalizationMethod1",
                        "xmlsecurity:verify://canonicalization",
                        "mock:result");
                // END SNIPPET: canonicalization
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signature and digest algorithm
                from("direct:signaturedigestalgorithm")
                    .to("xmlsecurity:sign://signaturedigestalgorithm?keyAccessor=#accessor"
                        + "&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512&digestAlgorithm=http://www.w3.org/2001/04/xmlenc#sha512",
                        "xmlsecurity:verify://signaturedigestalgorithm?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: signature and digest algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: transforms XPath2
                from("direct:transformsXPath2")
                    .to("xmlsecurity:sign://transformsXPath2?keyAccessor=#accessor&transformMethods=#transformsXPath2",
                        "xmlsecurity:verify://transformsXPath2?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: transform XPath
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: transforms XSLT,XPath
                onException(XmlSignatureException.class).handled(false)
                .to("mock:exception");
                from("direct:transformsXsltXPath")
                    .to("xmlsecurity:sign://transformsXsltXPath?keyAccessor=#accessor&transformMethods=#transformsXsltXPath",
                        "xmlsecurity:verify://transformsXsltXPath?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: transforms XSLT,XPath
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: transforms XSLT,XPath - secure Validation disabled
                from("direct:transformsXsltXPathSecureValDisabled")
                    .to("xmlsecurity:sign://transformsXsltXPathSecureValDisabled?keyAccessor=#accessor&transformMethods=#transformsXsltXPath",
                        "xmlsecurity:verify://transformsXsltXPathSecureValDisabled?keySelector=#selector&secureValidation=false")
                    .to("mock:result");
                // END SNIPPET: transforms XSLT,XPath - secure Validation disabled
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: cryptocontextprops
                onException(XmlSignatureException.class).handled(false).to(
                    "mock:exception");
                from("direct:cryptocontextprops")
                    .to("xmlsecurity:verify://cryptocontextprops?keySelector=#selectorKeyValue&cryptoContextProperties=#cryptoContextProperties")
                    .to("mock:result");
                // END SNIPPET: cryptocontextprops
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: URI dereferencer
                from("direct:uridereferencer")
                    .to("xmlsecurity:sign://uriderferencer?keyAccessor=#accessor&uriDereferencer=#uriDereferencer")
                    .to("xmlsecurity:verify://uridereferencer?keySelector=#selector&uriDereferencer=#uriDereferencer")
                    .to("mock:result");
                // END SNIPPET: URI dereferencer
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: keyAccessorKeySelectorDefault
                from("direct:keyAccessorKeySelectorDefault")
                    .to("xmlsecurity:sign://keyAccessorKeySelectorDefault?keyAccessor=#keyAccessorDefault&addKeyInfoReference=true")
                    .to("xmlsecurity:verify://keyAccessorKeySelectorDefault?keySelector=#keySelectorDefault")
                    .to("mock:result");
                // END SNIPPET: keyAccessorKeySelectorDefault
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: xmlSignatureChecker
                onException(XmlSignatureInvalidException.class).handled(false).to("mock:exception");
                from("direct:xmlSignatureChecker")
                    .to("xmlsecurity:verify://xmlSignatureChecker?keySelector=#selectorKeyValue&xmlSignatureChecker=#envelopingSignatureChecker")
                    .to("mock:result");
                // END SNIPPET: xmlSignatureChecker
            }
        }, new RouteBuilder() {
            public void configure() throws Exception { //
                // START SNIPPET: properties
                from("direct:props")
                    .to("xmlsecurity:sign://properties?keyAccessor=#accessor&properties=#signatureProperties")
                    .to("xmlsecurity:verify://properties?keySelector=#selector&xmlSignature2Message=#xmlSignature2MessageWithTimestampProperty")
                    .to("mock:result");
                // END SNIPPET: properties
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: verify output node search element name
                onException(XmlSignatureException.class).handled(true).to(
                    "mock:exception");
                from("direct:outputnodesearchelementname")
                    .to("xmlsecurity:verify://outputnodesearchelementname?keySelector=#selectorKeyValue"
                        + "&outputNodeSearchType=ElementName&outputNodeSearch={http://test/test}root&removeSignatureElements=true")
                    .to("mock:result");
                // END SNIPPET: verify output node search element name
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: verify output node search xpath
                onException(XmlSignatureException.class).handled(true).to(
                    "mock:exception");
                from("direct:outputnodesearchxpath")
                    .to("xmlsecurity:verify://outputnodesearchxpath?keySelector=#selectorKeyValue&outputNodeSearchType=XPath&outputNodeSearch=#nodesearchxpath&removeSignatureElements=true")
                    .to("mock:result");
                // END SNIPPET: verify output node search xpath
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: validationFailedHandler
                from("direct:validationFailedHandler")
                    .to("xmlsecurity:verify://validationFailedHandler?keySelector=#selectorKeyValue&validationFailedHandler=validationFailedHandlerIgnoreManifestFailures")
                    .to("mock:result");
                // END SNIPPET: validationFailedHandler
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: further parameters
                from("direct:furtherparams")
                    .to("xmlsecurity:sign://furtherparams?keyAccessor=#accessor&prefixForXmlSignatureNamespace=digsig&disallowDoctypeDecl=false")
                    .to("xmlsecurity:verify://bfurtherparams?keySelector=#selector&disallowDoctypeDecl=false")
                    .to("mock:result");
                // END SNIPPET: further parameters
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signer invalid keyexception
                onException(XmlSignatureInvalidKeyException.class).handled(true).to("mock:exception");
                from("direct:signexceptioninvalidkey")
                    .to("xmlsecurity:sign://signexceptioninvalidkey?signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512")
                    .to("mock:result");
                // END SNIPPET: signer invalid keyexception
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: signer exceptions
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:signexceptions")
                    .to("xmlsecurity:sign://signexceptions?keyAccessor=#accessor&signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512")
                    .to("mock:result");
                // END SNIPPET: signer exceptions
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: NoSuchAlgorithmException
                onException(XmlSignatureException.class).handled(true).to(
                    "mock:exception");
                from("direct:noSuchAlgorithmException")
                    .to("xmlsecurity:sign://noSuchAlgorithmException?keyAccessor=#accessor&signatureAlgorithm=wrongalgorithm&digestAlgorithm=http://www.w3.org/2001/04/xmlenc#sha512")
                    .to("mock:result");
                // END SNIPPET: NoSuchAlgorithmException
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: verifier exceptions
                onException(XmlSignatureException.class).handled(false).to(
                    "mock:exception");
                from("direct:verifyexceptions")
                    .to("xmlsecurity:verify://verifyexceptions?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: verifier exceptions
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: verifier InvalidKeyException
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:verifyInvalidKeyException")
                    .to("xmlsecurity:verify://verifyInvalidKeyException?keySelector=#selector")
                    .to("mock:result");
                // END SNIPPET: verifier exceptions
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: verifier InvalidHashException
                onException(XmlSignatureException.class).handled(false).to("mock:exception");
                from("direct:invalidhash")
                    .to("xmlsecurity:verify://invalidhash?keySelector=#selectorKeyValue&baseUri=#baseUri&secureValidation=false")
                    .to("mock:result");
                // END SNIPPET: verifier InvalidHashException
            }
        }

        };
    }

    @Test
    public void testEnvelopingSignature() throws Exception {
        setupMock();
        sendBody("direct:enveloping", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEnvelopingSignatureWithPlainText() throws Exception {
        String text = "plain test text";
        setupMock(text);
        sendBody("direct:plaintext", text);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEnvelopingSignatureWithPlainTextSetByHeaders()
        throws Exception {
        String text = "plain test text";
        setupMock(text);
        Map<String, Object> headers = new TreeMap<String, Object>();
        headers.put(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT,
                    Boolean.TRUE);
        headers.put(XmlSignatureConstants.HEADER_PLAIN_TEXT_ENCODING, "UTF-8");
        sendBody("direct:enveloping", text, headers);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionSignatureForPlainTextWithWrongEncoding()
        throws Exception {
        String text = "plain test text";
        MockEndpoint mock = setupExceptionMock();
        Map<String, Object> headers = new TreeMap<String, Object>();
        headers.put(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT,
                    Boolean.TRUE);
        headers.put(XmlSignatureConstants.HEADER_PLAIN_TEXT_ENCODING,
            "wrongEncoding");
        sendBody("direct:enveloping", text, headers);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class,
                             UnsupportedEncodingException.class);
    }

    @Test
    public void testEnvelopedSignature() throws Exception {
        setupMock(payload);
        sendBody("direct:enveloped", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionEnvelopedSignatureWithWrongParent()
        throws Exception {
        // payload root element renamed to a -> parent name in route definition
        // does not fit
        String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a xmlns=\"http://test/test\"><test>Test Message</test></a>";

        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:enveloped", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testExceptionEnvelopedSignatureWithPlainTextPayload()
        throws Exception {
        // payload root element renamed to a -> parent name in route definition
        // does not fit
        String payload = "plain text Message";
        Map<String, Object> headers = new HashMap<String, Object>(1);
        headers.put(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT,
                    Boolean.TRUE);
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:enveloped", payload, headers);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    /**
     * The parameter can also be configured via
     * {@link XmlSignatureConfiguration#setOmitXmlDeclaration(Boolean)}
     */
    @Test
    public void testOmitXmlDeclarationViaHeader() throws Exception {
        String payloadOut = "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
        setupMock(payloadOut);
        Map<String, Object> headers = new TreeMap<String, Object>();
        headers.put(XmlSignatureConstants.HEADER_OMIT_XML_DECLARATION,
                    Boolean.TRUE);
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchelementname", payload, headers);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testkeyAccessorKeySelectorDefault() throws Exception {
        setupMock();
        sendBody("direct:keyAccessorKeySelectorDefault", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetCanonicalizationMethodInRouteDefinition()
        throws Exception {
        setupMock();
        sendBody("direct:canonicalization", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetDigestAlgorithmInRouteDefinition() throws Exception {

        setupMock();
        sendBody("direct:signaturedigestalgorithm", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSetTransformMethodXpath2InRouteDefinition()
        throws Exception {
        // example from http://www.w3.org/TR/2002/REC-xmldsig-filter2-20021108/
        String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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
            + " </ToBeSigned>                                                   "
            + "</Document>";

        setupMock(payload);
        sendBody("direct:transformsXPath2", payload);
        assertMockEndpointsSatisfied();
    }

    // Secure Validation is enabled and so this should fail
    @Test
    public void testSetTransformMethodXsltXpathInRouteDefinition()
        throws Exception {
        // byte[] encoded = Base64.encode("Test Message".getBytes("UTF-8"));
        // String contentBase64 = new String(encoded, "UTF-8");
        // String payload =
        // "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root xmlns=\"http://test/test\"><test></test></root>";
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:transformsXsltXPath", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }
    
    @Test
    public void testSetTransformMethodXsltXpathInRouteDefinitionSecValDisabled()
        throws Exception {
        setupMock();
        sendBody("direct:transformsXsltXPathSecureValDisabled", payload);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testProperties() throws Exception {
        setupMock();
        sendBody("direct:props", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVerifyOutputNodeSearchElementName() throws Exception {
        setupMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchelementname", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVerifyExceptionOutputNodeSearchElementNameInvalidFormat1()
        throws Exception {
        XmlVerifierEndpoint endpoint = context
            .getEndpoint(
                         "xmlsecurity:verify://outputnodesearchelementname?keySelector=#selectorKeyValue"
                         + "&outputNodeSearchType=ElementName&outputNodeSearch={http://test/test}root&removeSignatureElements=true",
                         XmlVerifierEndpoint.class);
        endpoint.setOutputNodeSearch("{wrongformat"); // closing '}' missing
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchelementname", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testVerifyExceptionOutputNodeSearchElementNameInvalidFormat2()
        throws Exception {
        context.getEndpoint("xmlsecurity:verify://outputnodesearchelementname?keySelector=#selectorKeyValue"
                            + "&outputNodeSearchType=ElementName&outputNodeSearch={http://test/test}root&removeSignatureElements=true",
                            XmlVerifierEndpoint.class).setOutputNodeSearch("{wrongformat}"); 
        // local name missing
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchelementname", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchWrongElementName()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchelementname", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchElementNameMoreThanOneOutputElement()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSigWithSeveralElementsWithNameRoot.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchelementname", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testVerifyOutputNodeSearchXPath() throws Exception {
        setupMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchxpath", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchXPathWithNoResultNode()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchxpath", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testExceptionVerifyOutputNodeSearchXPathMoreThanOneOutputElement()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSigWithSeveralElementsWithNameRoot.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:outputnodesearchxpath", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, null);
    }

    @Test
    public void testInvalidKeyException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        // wrong key type
        setUpKeys("DSA", 512);
        context.getEndpoint("xmlsecurity:sign://signexceptioninvalidkey?signatureAlgorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha512",
                            XmlSignerEndpoint.class).setKeyAccessor(getKeyAccessor(keyPair.getPrivate()));
        sendBody("direct:signexceptioninvalidkey", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureInvalidKeyException.class, null);
    }

    @Test
    public void testSignatureFormatException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:signexceptions", "wrongFormatedPayload");
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testNoSuchAlgorithmException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:noSuchAlgorithmException", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class, NoSuchAlgorithmException.class);
    }

    @Test
    public void testVerifyFormatExceptionNoXml() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:verifyexceptions", "wrongFormatedPayload");
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testVerifyFormatExceptionNoXmlWithoutSignatureElement()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:verifyexceptions",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><NoSignature></NoSignature>");
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testVerifyFormatExceptionMoreThanOneSignatureElement()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        sendBody("direct:verifyexceptions",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\"/><Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\"/></root>");
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureFormatException.class, null);
    }

    @Test
    public void testVerifyInvalidContentHashException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleDetached.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:invalidhash", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureInvalidContentHashException.class, null);
    }

    @Test
    public void testVerifyMantifestInvalidContentHashException()
        throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ManifestTest_TamperedContent.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:invalidhash", payload);
        assertMockEndpointsSatisfied();
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
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:cryptocontextprops", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVerifySignatureInvalidValueException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        setUpKeys("DSA", 512);
        context.getEndpoint("xmlsecurity:verify://verifyexceptions?keySelector=#selector",
                            XmlVerifierEndpoint.class).setKeySelector(KeySelector.singletonKeySelector(keyPair.getPublic()));
        // payload needs DSA key
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:verifyexceptions", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureInvalidValueException.class, null);
    }

    @Test
    public void testVerifyInvalidKeyException() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopingDigSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:verifyInvalidKeyException", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureInvalidKeyException.class, null);
    }

    @Test
    public void testUriDereferencerAndBaseUri() throws Exception {
        setupMock();
        sendBody("direct:uridereferencer", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testVerifyXmlSignatureChecker() throws Exception {
        MockEndpoint mock = setupExceptionMock();
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ExampleEnvelopedXmlSig.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:xmlSignatureChecker", payload);
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureInvalidException.class, null);
    }

    @Test
    public void testVerifyValidationFailedHandler() throws Exception {
        setupMock("some text tampered");
        InputStream payload = XmlSignatureTest.class
            .getResourceAsStream("/org/apache/camel/component/xmlsecurity/ManifestTest_TamperedContent.xml");
        assertNotNull("Cannot load payload", payload);
        sendBody("direct:validationFailedHandler", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFurtherParameters() throws Exception {
        setupMock(payload);
        String payloadWithDTDoctype = "<?xml version=\'1.0\'?>"
            + "<!DOCTYPE Signature SYSTEM "
            + "\"src/test/resources/org/apache/camel/component/xmlsecurity/xmldsig-core-schema.dtd\" [ <!ENTITY dsig "
            + "\"http://www.w3.org/2000/09/xmldsig#\"> ]>"
            + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";

        sendBody("direct:furtherparams", payloadWithDTDoctype);
        assertMockEndpointsSatisfied();
    }

    private void checkThrownException(MockEndpoint mock,
                                      Class<? extends XmlSignatureException> cl,
                                      Class<? extends Exception> expectedCauseClass) throws Exception {
        Exception e = (Exception) mock.getExchanges().get(0)
            .getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull("Expected excpetion " + cl.getName() + " missing", e);
        if (e.getClass() != cl) {
            String stackTrace = getStrackTrace(e);
            fail("Exception  " + cl.getName() + " excpected, but was "
                + e.getClass().getName() + ": " + stackTrace);
        }
        if (expectedCauseClass != null) {
            Throwable cause = e.getCause();
            assertNotNull(
                          "Expected cause exception" + expectedCauseClass.getName()
                          + " missing", cause);
            if (expectedCauseClass != cause.getClass()) {
                fail("Cause exception " + expectedCauseClass.getName()
                     + " expected, but was " + cause.getClass().getName()
                     + ": " + getStrackTrace(e));
            }
        }
    }

    private String getStrackTrace(Exception e)
        throws UnsupportedEncodingException {
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
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(payload);
        return mock;
    }

    @SuppressWarnings("unchecked")
    public Exchange doTestSignatureRoute(RouteBuilder builder) throws Exception {
        return doSignatureRouteTest(builder, null, Collections.EMPTY_MAP);
    }

    public Exchange doSignatureRouteTest(RouteBuilder builder, Exchange e,
                                         Map<String, Object> headers) throws Exception {
        CamelContext context = new DefaultCamelContext();
        try {
            context.addRoutes(builder);
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:result",
                                                    MockEndpoint.class);
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
        setUpKeys("RSA", 1024);
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
        InputStream in = XmlSignatureTest.class.getResourceAsStream("/bob.keystore");
        keystore.load(in, "letmein".toCharArray());
        return keystore;
    }

    public Certificate getCertificateFromKeyStore() throws Exception {
        Certificate c = loadKeystore().getCertificate("bob");
        return c;
    }

    public PrivateKey getKeyFromKeystore() throws Exception {
        return (PrivateKey) loadKeystore().getKey("bob",
                                                  "letmein".toCharArray());
    }

    private AlgorithmMethod getCanonicalizationMethod() {
        List<String> inclusivePrefixes = new ArrayList<String>(1);
        inclusivePrefixes.add("ds");
        return XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, inclusivePrefixes);
    }

    private List<AlgorithmMethod> getTransformsXPath2() {

        List<XPathAndFilter> list = new ArrayList<XPathAndFilter>(3);
        XPathAndFilter xpath1 = new XPathAndFilter("//n0:ToBeSigned",
                                                   XPathType.Filter.INTERSECT.toString());
        list.add(xpath1);
        XPathAndFilter xpath2 = new XPathAndFilter("//n0:NotToBeSigned",
                                                   XPathType.Filter.SUBTRACT.toString());
        list.add(xpath2);
        XPathAndFilter xpath3 = new XPathAndFilter("//n0:ReallyToBeSigned",
                                                   XPathType.Filter.UNION.toString());
        list.add(xpath3);
        List<AlgorithmMethod> result = new ArrayList<AlgorithmMethod>(2);
        result.add(XmlSignatureHelper
                   .getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
        result.add(XmlSignatureHelper.getXPath2Transform(list, getNamespaceMap()));
        return result;
    }

    private Map<String, String> getNamespaceMap() {
        Map<String, String> result = new HashMap<String, String>(1);
        result.put("n0", "http://test/test");
        return result;
    }

    private List<AlgorithmMethod> getTransformsXsltXpath() {
        try {
            AlgorithmMethod transformXslt = XmlSignatureHelper
                .getXslTransform("/org/apache/camel/component/xmlsecurity/xslt_test.xsl");
            Map<String, String> namespaceMap = new HashMap<String, String>(1);
            namespaceMap.put("n0", "https://org.apache/camel/xmlsecurity/test");
            AlgorithmMethod transformXpath = XmlSignatureHelper
                .getXPathTransform("//n0:XMLSecurity/n0:Content",
                                   namespaceMap);
            // I removed base 64 transform because the JDK implementation does
            // not correctly support this transformation
            // AlgorithmMethod transformBase64 = helper.getBase64Transform();
            List<AlgorithmMethod> result = new ArrayList<AlgorithmMethod>(3);
            result.add(XmlSignatureHelper
                       .getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
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
            public KeySelector getKeySelector(Message message) throws Exception {
                return KeySelector.singletonKeySelector(privateKey);
            }

            @Override
            public KeyInfo getKeyInfo(Message mess, Node messageBody,
                                      KeyInfoFactory keyInfoFactory) throws Exception {
                return null;
            }
        };
        return accessor;
    }

    public static String getBaseUri() {
        String uri = "file:/" + System.getProperty("user.dir")
            + "/src/test/resources/org/apache/camel/component/xmlsecurity/";
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
        public KeySelectorResult select(KeyInfo keyInfo,
                                        KeySelector.Purpose purpose, AlgorithmMethod method,
                                        XMLCryptoContext context) throws KeySelectorException {
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
        return Collections.singletonMap("org.jcp.xml.dsig.validateManifests",
                                        Boolean.FALSE);
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
        Map<String, String> prefix2Namespace = Collections.singletonMap("pre",
            "http://test/test");
        return XmlSignatureHelper.getXpathFilter("//pre:root", prefix2Namespace);
    }

    public static URIDereferencer getSameDocumentUriDereferencer() {
        return SameDocumentUriDereferencer.getInstance();
    }

}