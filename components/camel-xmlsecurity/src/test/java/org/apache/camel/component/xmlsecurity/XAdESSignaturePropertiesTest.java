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
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmlsecurity.api.DefaultXAdESSignatureProperties;
import org.apache.camel.component.xmlsecurity.api.XAdESEncapsulatedPKIData;
import org.apache.camel.component.xmlsecurity.api.XAdESSignatureProperties;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.util.TestKeystore;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.xmlsecurity.XmlSignatureTest.checkThrownException;

public class XAdESSignaturePropertiesTest extends CamelTestSupport {

    private static final String NOT_EMPTY = "NOT_EMPTY";
    private static String payload;
    
    static {
        boolean includeNewLine = true;
        if (TestSupport.getJavaMajorVersion() >= 9) {
            includeNewLine = false;
        }
        payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + (includeNewLine ? "\n" : "")
            + "<root xmlns=\"http://test/test\"><test>Test Message</test></root>";
    }

    @Override
    @Before
    public void setUp() throws Exception {
        disableJMX();
        super.setUp();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();
        registry.bind("keyAccessorDefault", TestKeystore.getKeyAccessor("bob"));
        registry.bind("xmlSignatureProperties", getXmlSignatureProperties("bob"));

        Map<String, String> namespaceMap = Collections.singletonMap("ns", "http://test");
        List<XPathFilterParameterSpec> xpaths = Collections
                .singletonList(XmlSignatureHelper.getXpathFilter("/ns:root/a/@ID", namespaceMap));
        registry.bind("xpathsToIdAttributes", xpaths);

        return registry;
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:enveloped")
                        .to("xmlsecurity-sign:xades?keyAccessor=#keyAccessorDefault&properties=#xmlSignatureProperties&parentLocalName=root&parentNamespace=http://test/test")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:enveloping").to("xmlsecurity-sign:xades?keyAccessor=#keyAccessorDefault&properties=#xmlSignatureProperties")
                        .to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                onException(XmlSignatureException.class).handled(true).to("mock:exception");
                from("direct:emptySignatureId").to(
                        "xmlsecurity-sign:xades?keyAccessor=#keyAccessorDefault&properties=#xmlSignatureProperties&signatureId=").to(
                        "mock:result");
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                onException(Exception.class).handled(false).to("mock:exception");
                from("direct:detached").to(
                        "xmlsecurity-sign:detached?keyAccessor=#keyAccessorDefault&xpathsToIdAttributes=#xpathsToIdAttributes&"//
                                + "schemaResourceUri=org/apache/camel/component/xmlsecurity/Test.xsd&properties=#xmlSignatureProperties")
                        .to("mock:result");
            }
        } };
    }

    @Test
    public void envelopingAllParameters() throws Exception {

        Document doc = testEnveloping();

        Map<String, String> prefix2Namespace = getPrefix2NamespaceMap();
        String pathToSignatureProperties = getPathToSignatureProperties();
        // signing time
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningTime/text()", prefix2Namespace, NOT_EMPTY);
        // signing certificate
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:CertDigest/ds:DigestMethod/@Algorithm",
                prefix2Namespace, DigestMethod.SHA256);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:CertDigest/ds:DigestValue/text()",
                prefix2Namespace, NOT_EMPTY);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:IssuerSerial/ds:X509IssuerName/text()",
                prefix2Namespace, NOT_EMPTY);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:IssuerSerial/ds:X509SerialNumber/text()",
                prefix2Namespace, NOT_EMPTY);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/@URI", prefix2Namespace, "http://certuri");

        // signature policy
        checkXpath(doc, pathToSignatureProperties
                + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyId/etsi:Identifier/text()", prefix2Namespace,
                "1.2.840.113549.1.9.16.6.1");
        checkXpath(doc, pathToSignatureProperties
                + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyId/etsi:Identifier/@Qualifier", prefix2Namespace,
                "OIDAsURN");
        checkXpath(doc, pathToSignatureProperties
                + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyId/etsi:Description/text()", prefix2Namespace,
                "invoice version 3.1");
        checkXpath(doc, pathToSignatureProperties
                + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyHash/ds:DigestMethod/@Algorithm", prefix2Namespace,
                DigestMethod.SHA256);
        checkXpath(doc, pathToSignatureProperties
                + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyHash/ds:DigestValue/text()", prefix2Namespace,
                "Ohixl6upD6av8N7pEvDABhEL6hM=");
        checkXpath(
                doc,
                pathToSignatureProperties
                        + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyQualifiers/etsi:SigPolicyQualifier[1]/etsi:SPURI/text()",
                prefix2Namespace, "http://test.com/sig.policy.pdf");
        checkXpath(
                doc,
                pathToSignatureProperties
                        + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyQualifiers/etsi:SigPolicyQualifier[1]/etsi:SPUserNotice/etsi:ExplicitText/text()",
                prefix2Namespace, "display text");
        checkXpath(doc, pathToSignatureProperties
                + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyQualifiers/etsi:SigPolicyQualifier[2]/text()",
                prefix2Namespace, "category B");
        checkXpath(
                doc,
                pathToSignatureProperties
                        + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyId/etsi:DocumentationReferences/etsi:DocumentationReference[1]/text()",
                prefix2Namespace, "http://test.com/policy.doc.ref1.txt");
        checkXpath(
                doc,
                pathToSignatureProperties
                        + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId/etsi:SigPolicyId/etsi:DocumentationReferences/etsi:DocumentationReference[2]/text()",
                prefix2Namespace, "http://test.com/policy.doc.ref2.txt");

        // production place
        checkXpath(doc, pathToSignatureProperties + "etsi:SignatureProductionPlace/etsi:City/text()", prefix2Namespace, "Munich");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignatureProductionPlace/etsi:StateOrProvince/text()", prefix2Namespace,
                "Bavaria");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignatureProductionPlace/etsi:PostalCode/text()", prefix2Namespace, "80331");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignatureProductionPlace/etsi:CountryName/text()", prefix2Namespace, "Germany");

        // signer role 
        checkXpath(doc, pathToSignatureProperties + "etsi:SignerRole/etsi:ClaimedRoles/etsi:ClaimedRole[1]/text()", prefix2Namespace,
                "test");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignerRole/etsi:ClaimedRoles/etsi:ClaimedRole[2]/TestRole/text()",
                prefix2Namespace, "TestRole");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignerRole/etsi:CertifiedRoles/etsi:CertifiedRole/text()", prefix2Namespace,
                "Ahixl6upD6av8N7pEvDABhEL6hM=");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignerRole/etsi:CertifiedRoles/etsi:CertifiedRole/@Encoding", prefix2Namespace,
                "http://uri.etsi.org/01903/v1.2.2#DER");
        checkXpath(doc, pathToSignatureProperties + "etsi:SignerRole/etsi:CertifiedRoles/etsi:CertifiedRole/@Id", prefix2Namespace,
                "IdCertifiedRole");

        String pathToDataObjectProperties = "/ds:Signature/ds:Object/etsi:QualifyingProperties/etsi:SignedProperties/etsi:SignedDataObjectProperties/";
        //DataObjectFormat
        checkXpath(doc, pathToDataObjectProperties + "etsi:DataObjectFormat/etsi:Description/text()", prefix2Namespace, "invoice");
        checkXpath(doc, pathToDataObjectProperties + "etsi:DataObjectFormat/etsi:MimeType/text()", prefix2Namespace, "text/xml");
        checkXpath(doc, pathToDataObjectProperties + "etsi:DataObjectFormat/@ObjectReference", prefix2Namespace, "#", true);
        checkXpath(doc, pathToDataObjectProperties + "etsi:DataObjectFormat/etsi:ObjectIdentifier/etsi:Identifier/text()",
                prefix2Namespace, "1.2.840.113549.1.9.16.6.2");
        checkXpath(doc, pathToDataObjectProperties + "etsi:DataObjectFormat/etsi:ObjectIdentifier/etsi:Identifier/@Qualifier",
                prefix2Namespace, "OIDAsURN");
        checkXpath(doc, pathToDataObjectProperties + "etsi:DataObjectFormat/etsi:ObjectIdentifier/etsi:Description/text()",
                prefix2Namespace, "identifier desc");
        checkXpath(doc, pathToDataObjectProperties
                + "etsi:DataObjectFormat/etsi:ObjectIdentifier/etsi:DocumentationReferences/etsi:DocumentationReference[1]/text()",
                prefix2Namespace, "http://test.com/dataobject.format.doc.ref1.txt");
        checkXpath(doc, pathToDataObjectProperties
                + "etsi:DataObjectFormat/etsi:ObjectIdentifier/etsi:DocumentationReferences/etsi:DocumentationReference[2]/text()",
                prefix2Namespace, "http://test.com/dataobject.format.doc.ref2.txt");

        //commitment 
        checkXpath(doc, pathToDataObjectProperties + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeId/etsi:Identifier/text()",
                prefix2Namespace, "1.2.840.113549.1.9.16.6.4");
        checkXpath(doc, pathToDataObjectProperties + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeId/etsi:Identifier/@Qualifier",
                prefix2Namespace, "OIDAsURI");
        checkXpath(doc, pathToDataObjectProperties + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeId/etsi:Description/text()",
                prefix2Namespace, "description for commitment type ID");
        checkXpath(doc, pathToDataObjectProperties
                + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeId/etsi:DocumentationReferences/etsi:DocumentationReference[1]/text()",
                prefix2Namespace, "http://test.com/commitment.ref1.txt");
        checkXpath(doc, pathToDataObjectProperties
                + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeId/etsi:DocumentationReferences/etsi:DocumentationReference[2]/text()",
                prefix2Namespace, "http://test.com/commitment.ref2.txt");
        checkXpath(doc, pathToDataObjectProperties
                + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeQualifiers/etsi:CommitmentTypeQualifier[1]/text()", prefix2Namespace,
                "commitment qualifier");
        checkXpath(doc, pathToDataObjectProperties
                + "etsi:CommitmentTypeIndication/etsi:CommitmentTypeQualifiers/etsi:CommitmentTypeQualifier[2]/C/text()", prefix2Namespace,
                "c");
    }

    @Test
    public void noSigningTime() throws Exception {

        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setAddSigningTime(false);

        Document doc = testEnveloping();

        Map<String, String> prefix2Namespace = getPrefix2NamespaceMap();
        String pathToSignatureProperties = getPathToSignatureProperties();

        checkNode(doc, pathToSignatureProperties + "etsi:SigningTime", prefix2Namespace, false);

    }

    @Test
    public void noSigningCertificate() throws Exception {

        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties newProps = new XAdESSignatureProperties();
        newProps.setAddSigningTime(true);
        endpoint.getConfiguration().setProperties(newProps);

        Document doc = testEnveloping();

        Map<String, String> prefix2Namespace = getPrefix2NamespaceMap();
        String pathToSignatureProperties = getPathToSignatureProperties();

        checkNode(doc, pathToSignatureProperties + "etsi:SigningTime", prefix2Namespace, true);

        checkNode(doc, pathToSignatureProperties + "etsi:SigningCertificate", prefix2Namespace, false);
    }

    @Test
    public void certificateChain() throws Exception {

        XmlSignerEndpoint endpoint = getSignerEndpoint();
        endpoint.getConfiguration().setProperties(new CertChainXAdESSignatureProperties());

        Document doc = testEnveloping();

        Map<String, String> prefix2Namespace = getPrefix2NamespaceMap();
        String pathToSignatureProperties = getPathToSignatureProperties();

        // signing certificate
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:CertDigest/ds:DigestMethod/@Algorithm",
                prefix2Namespace, DigestMethod.SHA256);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:CertDigest/ds:DigestValue/text()",
                prefix2Namespace, NOT_EMPTY);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:IssuerSerial/ds:X509IssuerName/text()",
                prefix2Namespace, NOT_EMPTY);
        checkXpath(doc, pathToSignatureProperties + "etsi:SigningCertificate/etsi:Cert/etsi:IssuerSerial/ds:X509SerialNumber/text()",
                prefix2Namespace, NOT_EMPTY);
    }

    @Test
    public void noPropertiesSpecified() throws Exception {
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = new XAdESSignatureProperties();
        props.setAddSigningTime(false);
        endpoint.getConfiguration().setProperties(props);
        Document doc = testEnveloping();
        // expecting no Qualifying Properties
        checkNode(doc, "/ds:Signature/ds:Object/etsi:QualifyingProperties", getPrefix2NamespaceMap(), false);

    }

    @Test
    public void policyImplied() throws Exception {
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSignaturePolicy(XAdESSignatureProperties.SIG_POLICY_IMPLIED);
        Document doc = testEnveloping();

        String pathToSignatureProperties = getPathToSignatureProperties();
        checkNode(doc, pathToSignatureProperties + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyId", getPrefix2NamespaceMap(), false);
        checkNode(doc, pathToSignatureProperties + "etsi:SignaturePolicyIdentifier/etsi:SignaturePolicyImplied", getPrefix2NamespaceMap(),
                true);
    }

    @Test
    public void policyNone() throws Exception {
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSignaturePolicy(XAdESSignatureProperties.SIG_POLICY_NONE);
        Document doc = testEnveloping();

        String pathToSignatureProperties = getPathToSignatureProperties();
        checkNode(doc, pathToSignatureProperties + "etsi:SignaturePolicyIdentifier", getPrefix2NamespaceMap(), false);
    }

    @Test
    public void allPropertiesEmpty() throws Exception {
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = new XAdESSignatureProperties();
        props.setAddSigningTime(false);
        props.setCommitmentTypeId("");
        props.setCommitmentTypeIdDescription("");
        props.setCommitmentTypeIdQualifier("");
        props.setDataObjectFormatDescription("");
        props.setDataObjectFormatIdentifier("");
        props.setDataObjectFormatIdentifierDescription("");
        props.setDataObjectFormatIdentifierQualifier("");
        props.setDataObjectFormatMimeType("");
        props.setDigestAlgorithmForSigningCertificate("");
        props.setSignaturePolicy("None");
        props.setSigPolicyId("");
        props.setSigPolicyIdDescription("");
        props.setSigPolicyIdQualifier("");
        props.setSignaturePolicyDigestAlgorithm("");
        props.setSignaturePolicyDigestValue("");
        props.setSignatureProductionPlaceCity("");
        props.setSignatureProductionPlaceCountryName("");
        props.setSignatureProductionPlacePostalCode("");
        props.setSignatureProductionPlaceStateOrProvince("");

        endpoint.getConfiguration().setProperties(props);
        Document doc = testEnveloping();
        // expecting no Qualifying Properties
        checkNode(doc, "/ds:Signature/ds:Object/etsi:QualifyingProperties", getPrefix2NamespaceMap(), false);

    }

    @Test
    public void emptySignatureId() throws Exception {
        Document doc = testEnveloping("direct:emptySignatureId");
        checkNode(doc, "/ds:Signature/ds:Object/etsi:QualifyingProperties", getPrefix2NamespaceMap(), true);
    }

    @Test
    public void prefixAndNamespace() throws Exception {
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setPrefix("p");
        props.setNamespace(XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_1_1);
        props.setCommitmentTypeIdDescription(null);
        props.setCommitmentTypeIdDocumentationReferences(Collections.emptyList());
        props.setCommitmentTypeIdQualifier(null);
        props.setDataObjectFormatIdentifierDescription(null);
        props.setDataObjectFormatIdentifierDocumentationReferences(Collections.emptyList());
        props.setDataObjectFormatIdentifierQualifier(null);
        props.setSigPolicyIdDescription(null);
        props.setSigPolicyIdDocumentationReferences(Collections.emptyList());
        props.setSigPolicyIdQualifier(null);
        // the following lists must be set to empty because otherwise they would contain XML fragments with a wrong namespace
        props.setSigPolicyQualifiers(Collections.emptyList());
        props.setSignerClaimedRoles(Collections.emptyList());
        props.setCommitmentTypeQualifiers(Collections.emptyList());

        Document doc = testEnveloping();

        Map<String, String> prefix2Namespace = new TreeMap<>();
        prefix2Namespace.put("ds", XMLSignature.XMLNS);
        prefix2Namespace.put("etsi", XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_1_1);

        XPathExpression expr = getXpath("/ds:Signature/ds:Object/etsi:QualifyingProperties", prefix2Namespace);
        Object result = expr.evaluate(doc, XPathConstants.NODE);
        assertNotNull(result);
        Node node = (Node) result;
        assertEquals("p", node.getPrefix());
        assertEquals(XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_1_1, node.getNamespaceURI());
    }

    @Test
    public void headers() throws Exception {
        Map<String, Object> header = new TreeMap<>();

        header.put(XmlSignatureConstants.HEADER_XADES_PREFIX, "ns1");
        header.put(XmlSignatureConstants.HEADER_XADES_NAMESPACE, XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_2_2);

        header.put(XmlSignatureConstants.HEADER_XADES_QUALIFYING_PROPERTIES_ID, "QualId");
        header.put(XmlSignatureConstants.HEADER_XADES_SIGNED_DATA_OBJECT_PROPERTIES_ID, "ObjId");
        header.put(XmlSignatureConstants.HEADER_XADES_SIGNED_SIGNATURE_PROPERTIES_ID, "SigId");
        header.put(XmlSignatureConstants.HEADER_XADES_DATA_OBJECT_FORMAT_ENCODING, "base64");

        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        // the following lists must be set to empty because otherwise they would contain XML fragments with a wrong namespace
        props.setSigPolicyQualifiers(Collections.emptyList());
        props.setSignerClaimedRoles(Collections.emptyList());
        props.setCommitmentTypeQualifiers(Collections.emptyList());

        Document doc = testEnveloping("direct:enveloping", header);

        Map<String, String> prefix2Namespace = new TreeMap<>();
        prefix2Namespace.put("ds", XMLSignature.XMLNS);
        prefix2Namespace.put("etsi", XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_2_2);

        XPathExpression expr = getXpath("/ds:Signature/ds:Object/etsi:QualifyingProperties", prefix2Namespace);
        Object result = expr.evaluate(doc, XPathConstants.NODE);
        assertNotNull(result);
        Node node = (Node) result;
        assertEquals("ns1", node.getPrefix());
        assertEquals(XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_2_2, node.getNamespaceURI());

        checkXpath(doc, "/ds:Signature/ds:Object/etsi:QualifyingProperties/@Id", prefix2Namespace, "QualId");

        checkXpath(doc, "/ds:Signature/ds:Object/etsi:QualifyingProperties/etsi:SignedProperties/etsi:SignedDataObjectProperties/@Id",
                prefix2Namespace, "ObjId");

        checkXpath(doc, "/ds:Signature/ds:Object/etsi:QualifyingProperties/etsi:SignedProperties/etsi:SignedSignatureProperties/@Id",
                prefix2Namespace, "SigId");

        checkXpath(
                doc,
                "/ds:Signature/ds:Object/etsi:QualifyingProperties/etsi:SignedProperties/etsi:SignedDataObjectProperties/etsi:DataObjectFormat/etsi:Encoding/text()",
                prefix2Namespace, "base64");

    }

    @Test
    public void enveloped() throws Exception {
        setupMock();
        sendBody("direct:enveloped", payload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void detached() throws Exception {
        String detachedPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
                "<ns:root xmlns:ns=\"http://test\"><a ID=\"myID\"><b>bValue</b></a></ns:root>";
        setupMock();
        sendBody("direct:detached", detachedPayload);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void sigPolicyIdEmpty() throws Exception {
        testExceptionSigPolicyIdMissing("");
    }

    @Test
    public void sigPolicyIdNull() throws Exception {
        testExceptionSigPolicyIdMissing(null);
    }

    private void testExceptionSigPolicyIdMissing(String value) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSigPolicyId(value);
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class,
                "The XAdES-EPES configuration is invalid. The signature policy identifier is missing.", null);
    }

    @Test
    public void sigPolicyDigestEmpty() throws Exception {
        testExceptionSigPolicyDigestMissing("");
    }

    @Test
    public void sigPolicyDigestNull() throws Exception {
        testExceptionSigPolicyDigestMissing(null);
    }

    private void testExceptionSigPolicyDigestMissing(String value) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSignaturePolicyDigestValue(value);
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class,
                "The XAdES-EPES configuration is invalid. The digest value for the signature policy is missing.", null);
    }

    @Test
    public void sigPolicyDigestAlgoEmpty() throws Exception {
        testExceptionSigPolicyDigestAlgoMissing("");
    }

    @Test
    public void sigPolicyDigestAlgoNull() throws Exception {
        testExceptionSigPolicyDigestAlgoMissing(null);
    }

    private void testExceptionSigPolicyDigestAlgoMissing(String value) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSignaturePolicyDigestAlgorithm(value);
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(mock, XmlSignatureException.class,
                "The XAdES-EPES configuration is invalid. The digest algorithm for the signature policy is missing.", null);
    }

    @Test
    public void invalidXmlFragmentForClaimedRole() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSignerClaimedRoles(Collections.singletonList("<ClaimedRole>wrong XML fragment<ClaimedRole>")); // Element 'ClaimedRole' is not closed correctly
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(
                mock,
                XmlSignatureException.class,
                "The XAdES configuration is invalid. The list of the claimed roles contains the invalid entry '<ClaimedRole>wrong XML fragment<ClaimedRole>'. An entry must either be a text or"
                        + " an XML fragment with the root element 'ClaimedRole' with the namespace 'http://uri.etsi.org/01903/v1.3.2#'.",
                null);
    }

    @Test
    public void invalidXmlFragmentForCommitmentTypeQualifier() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setCommitmentTypeQualifiers(Collections.singletonList("<CommitmentTypeQualifier>wrong XML fragment<CommitmentTypeQualifier>")); // end tag is not correct
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(
                mock,
                XmlSignatureException.class,
                "The XAdES configuration is invalid. The list of the commitment type qualifiers contains the invalid entry '<CommitmentTypeQualifier>wrong XML fragment<CommitmentTypeQualifier>'."
                        + " An entry must either be a text or an XML fragment with the root element 'CommitmentTypeQualifier' with the namespace 'http://uri.etsi.org/01903/v1.3.2#'.",
                null);
    }

    @Test
    public void invalidXmlFragmentForSigPolicyQualifier() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSigPolicyQualifiers(Collections.singletonList("<SigPolicyQualifier>wrong XML fragment<SigPolicyQualifier>")); // end tag is not correct
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(
                mock,
                XmlSignatureException.class,
                "The XAdES configuration is invalid. The list of the signatue policy qualifiers contains the invalid entry '<SigPolicyQualifier>wrong XML fragment<SigPolicyQualifier>'."
                        + " An entry must either be a text or an XML fragment with the root element 'SigPolicyQualifier' with the namespace 'http://uri.etsi.org/01903/v1.3.2#'.",
                null);
    }

    @Test
    public void invalidNamespaceForTheRootElementInXmlFragmentForSigPolicyQualifier() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:exception");
        mock.expectedMessageCount(1);
        XmlSignerEndpoint endpoint = getSignerEndpoint();
        XAdESSignatureProperties props = (XAdESSignatureProperties) endpoint.getConfiguration().getProperties();
        props.setSigPolicyQualifiers(Collections
                .singletonList("<SigPolicyQualifier xmlns=\"http://invalid.com\">XML fragment with wrong namespace for root element</SigPolicyQualifier>"));
        sendBody("direct:enveloping", payload, Collections.emptyMap());
        assertMockEndpointsSatisfied();
        checkThrownException(
                mock,
                XmlSignatureException.class,
                "The XAdES configuration is invalid. The root element 'SigPolicyQualifier' of the provided XML fragment "
                        + "'<SigPolicyQualifier xmlns=\"http://invalid.com\">XML fragment with wrong namespace for root element</SigPolicyQualifier>' has the invalid namespace 'http://invalid.com'."
                        + " The correct namespace is 'http://uri.etsi.org/01903/v1.3.2#'.", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void namespaceNull() throws Exception {
        new XAdESSignatureProperties().setNamespace(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void signingCertificateURIsNull() throws Exception {
        new XAdESSignatureProperties().setSigningCertificateURIs(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sigPolicyInvalid() throws Exception {
        new XAdESSignatureProperties().setSignaturePolicy("invalid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void sigPolicyIdDocumentationReferencesNull() throws Exception {
        new XAdESSignatureProperties().setSigPolicyIdDocumentationReferences(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sigPolicyIdDocumentationReferencesNullEntry() throws Exception {
        new XAdESSignatureProperties().setSigPolicyIdDocumentationReferences(Collections.<String> singletonList(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sigPolicyIdDocumentationReferencesEmptyEntry() throws Exception {
        new XAdESSignatureProperties().setSigPolicyIdDocumentationReferences(Collections.<String> singletonList(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void dataObjectFormatIdentifierDocumentationReferencesNull() throws Exception {
        new XAdESSignatureProperties().setDataObjectFormatIdentifierDocumentationReferences(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void dataObjectFormatIdentifierDocumentationReferencesNullEntry() throws Exception {
        new XAdESSignatureProperties().setDataObjectFormatIdentifierDocumentationReferences(Collections.<String> singletonList(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void dataObjectFormatIdentifierDocumentationReferencesEmptyEntry() throws Exception {
        new XAdESSignatureProperties().setDataObjectFormatIdentifierDocumentationReferences(Collections.<String> singletonList(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void signerClaimedRolesNull() throws Exception {
        new XAdESSignatureProperties().setSignerClaimedRoles(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void signerClaimedRolesNullEntry() throws Exception {
        new XAdESSignatureProperties().setSignerClaimedRoles(Collections.<String> singletonList(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void signerClaimedRolesEmptyEntry() throws Exception {
        new XAdESSignatureProperties().setSignerClaimedRoles(Collections.<String> singletonList(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void signerCertifiedRolesNull() throws Exception {
        new XAdESSignatureProperties().setSignerCertifiedRoles(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void signerCertifiedRolesNullEntry() throws Exception {
        new XAdESSignatureProperties().setSignerCertifiedRoles(Collections.<XAdESEncapsulatedPKIData> singletonList(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void commitmentTypeIdDocumentationReferencesNull() throws Exception {
        new XAdESSignatureProperties().setCommitmentTypeIdDocumentationReferences(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void commitmentTypeIdDocumentationReferencesNullEntry() throws Exception {
        new XAdESSignatureProperties().setCommitmentTypeIdDocumentationReferences(Collections.<String> singletonList(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void commitmentTypeIdDocumentationReferencesEmptyEntry() throws Exception {
        new XAdESSignatureProperties().setCommitmentTypeIdDocumentationReferences(Collections.<String> singletonList(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void commitmentTypeQualifiersNull() throws Exception {
        new XAdESSignatureProperties().setCommitmentTypeQualifiers(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void commitmentTypeQualifiersNullEntry() throws Exception {
        new XAdESSignatureProperties().setCommitmentTypeQualifiers(Collections.<String> singletonList(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void commitmentTypeQualifiersEmptyEntry() throws Exception {
        new XAdESSignatureProperties().setCommitmentTypeQualifiers(Collections.<String> singletonList(""));
    }

    //

    private XmlSignerEndpoint getSignerEndpoint() {
        return (XmlSignerEndpoint) context().getEndpoint(
                "xmlsecurity-sign:xades?keyAccessor=#keyAccessorDefault&properties=#xmlSignatureProperties");
    }

    private String getPathToSignatureProperties() {
        return "/ds:Signature/ds:Object/etsi:QualifyingProperties/etsi:SignedProperties/etsi:SignedSignatureProperties/";
    }

    static Map<String, String> getPrefix2NamespaceMap() {
        Map<String, String> prefix2Namespace = new TreeMap<>();
        prefix2Namespace.put("ds", XMLSignature.XMLNS);
        prefix2Namespace.put("etsi", XAdESSignatureProperties.HTTP_URI_ETSI_ORG_01903_V1_3_2);
        return prefix2Namespace;
    }

    private Document testEnveloping() throws InterruptedException, SAXException, IOException, ParserConfigurationException, Exception {
        return testEnveloping("direct:enveloping");
    }

    protected Document testEnveloping(String fromUri) throws InterruptedException, SAXException, IOException, ParserConfigurationException,
            Exception {
        return testEnveloping(fromUri, Collections.<String, Object> emptyMap());
    }

    protected Document testEnveloping(String fromUri, Map<String, Object> headers) throws InterruptedException, SAXException, IOException,
            ParserConfigurationException, Exception {
        MockEndpoint mock = setupMock();
        sendBody(fromUri, payload, headers);
        assertMockEndpointsSatisfied();
        Message message = getMessage(mock);
        byte[] body = message.getBody(byte[].class);
        Document doc = XmlSignatureHelper.newDocumentBuilder(true).parse(new ByteArrayInputStream(body));
        validateAgainstSchema(doc);
        return doc;
    }

    private MockEndpoint setupMock() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        return mock;
    }

    private static XmlSignatureProperties getXmlSignatureProperties(String alias) throws IOException, GeneralSecurityException {
        DefaultXAdESSignatureProperties props = new DefaultXAdESSignatureProperties();
        props.setKeystore(TestKeystore.getKeyStore());
        props.setAlias(alias);
        props.setAddSigningTime(true);
        props.setDigestAlgorithmForSigningCertificate(DigestMethod.SHA256);
        props.setSigningCertificateURIs(Collections.singletonList("http://certuri"));

        // policy
        props.setSignaturePolicy(XAdESSignatureProperties.SIG_POLICY_EXPLICIT_ID);
        props.setSigPolicyId("1.2.840.113549.1.9.16.6.1");
        props.setSigPolicyIdQualifier("OIDAsURN");
        props.setSigPolicyIdDescription("invoice version 3.1");
        props.setSignaturePolicyDigestAlgorithm(DigestMethod.SHA256);
        props.setSignaturePolicyDigestValue("Ohixl6upD6av8N7pEvDABhEL6hM=");
        props.setSigPolicyQualifiers(Arrays
            .asList(new String[] {
                "<SigPolicyQualifier xmlns=\"http://uri.etsi.org/01903/v1.3.2#\"><SPURI>http://test.com/sig.policy.pdf</SPURI><SPUserNotice><ExplicitText>display text</ExplicitText>"
                    + "</SPUserNotice></SigPolicyQualifier>", "category B" }));
        props.setSigPolicyIdDocumentationReferences(Arrays.asList(new String[] {"http://test.com/policy.doc.ref1.txt",
            "http://test.com/policy.doc.ref2.txt" }));
        // production place
        props.setSignatureProductionPlaceCity("Munich");
        props.setSignatureProductionPlaceCountryName("Germany");
        props.setSignatureProductionPlacePostalCode("80331");
        props.setSignatureProductionPlaceStateOrProvince("Bavaria");

        //role
        props.setSignerClaimedRoles(Arrays.asList(new String[] {"test",
            "<a:ClaimedRole xmlns:a=\"http://uri.etsi.org/01903/v1.3.2#\"><TestRole>TestRole</TestRole></a:ClaimedRole>" }));
        props.setSignerCertifiedRoles(Collections.singletonList(new XAdESEncapsulatedPKIData("Ahixl6upD6av8N7pEvDABhEL6hM=",
            "http://uri.etsi.org/01903/v1.2.2#DER", "IdCertifiedRole")));

        // data object format
        props.setDataObjectFormatDescription("invoice");
        props.setDataObjectFormatMimeType("text/xml");
        props.setDataObjectFormatIdentifier("1.2.840.113549.1.9.16.6.2");
        props.setDataObjectFormatIdentifierQualifier("OIDAsURN");
        props.setDataObjectFormatIdentifierDescription("identifier desc");
        props.setDataObjectFormatIdentifierDocumentationReferences(Arrays.asList(new String[] {
            "http://test.com/dataobject.format.doc.ref1.txt", "http://test.com/dataobject.format.doc.ref2.txt" }));

        //commitment
        props.setCommitmentTypeId("1.2.840.113549.1.9.16.6.4");
        props.setCommitmentTypeIdQualifier("OIDAsURI");
        props.setCommitmentTypeIdDescription("description for commitment type ID");
        props.setCommitmentTypeIdDocumentationReferences(Arrays.asList(new String[] {"http://test.com/commitment.ref1.txt",
            "http://test.com/commitment.ref2.txt" }));
        props.setCommitmentTypeQualifiers(Arrays.asList(new String[] {"commitment qualifier",
            "<c:CommitmentTypeQualifier xmlns:c=\"http://uri.etsi.org/01903/v1.3.2#\"><C>c</C></c:CommitmentTypeQualifier>" }));
        return props;
    }

    private void validateAgainstSchema(Document doc) throws Exception {

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        Source schema1 = new StreamSource(new File("target/test-classes/org/apache/camel/component/xmlsecurity/xades/XAdES.xsd"));
        Source schema2 = new StreamSource(new File(
                "target/test-classes/org/apache/camel/component/xmlsecurity/xades/xmldsig-core-schema.xsd"));
        Schema schema = factory.newSchema(new Source[] {schema2, schema1 });

        Validator validator = schema.newValidator();

        validator.validate(new DOMSource(doc));

    }

    static void checkXpath(Document doc, String xpathString, final Map<String, String> prefix2Namespace, String expectedResult)
        throws XPathExpressionException {
        checkXpath(doc, xpathString, prefix2Namespace, expectedResult, false);
    }
        
    static void checkXpath(Document doc, String xpathString, final Map<String, String> prefix2Namespace, String expectedResult, boolean startsWith)
            throws XPathExpressionException {

        XPathExpression expr = getXpath(xpathString, prefix2Namespace);
        String result = (String) expr.evaluate(doc, XPathConstants.STRING);
        assertNotNull("The xpath " + xpathString + " returned a null value", result);
        if (startsWith) {
            assertTrue(result.startsWith(expectedResult));
        } else if (NOT_EMPTY.equals(expectedResult)) {
            assertTrue("Not empty result for xpath " + xpathString + " expected", !result.isEmpty());
        } else {
            assertEquals(expectedResult, result);
        }
    }

    private void checkNode(Document doc, String xpathString, final Map<String, String> prefix2Namespace, boolean exists)
        throws XPathExpressionException {

        XPathExpression expr = getXpath(xpathString, prefix2Namespace);
        Object result = expr.evaluate(doc, XPathConstants.NODE);
        if (exists) {
            assertNotNull("The xpath " + xpathString + " returned null, expected was a node", result);
        } else {
            assertNull("The xpath " + xpathString + " returned a node, expected was none: ", result);
        }
    }

    static XPathExpression getXpath(String xpathString, final Map<String, String> prefix2Namespace) throws XPathExpressionException {
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
        return expr;
    }

    private Message getMessage(MockEndpoint mock) {
        List<Exchange> exs = mock.getExchanges();
        assertNotNull(exs);
        assertEquals(1, exs.size());
        Exchange ex = exs.get(0);
        Message mess = ex.getIn();
        assertNotNull(mess);
        return mess;
    }

    private static class CertChainXAdESSignatureProperties extends XAdESSignatureProperties {

        private KeyStore keystore = getKeystore();

        private String alias = "bob";

        CertChainXAdESSignatureProperties() {
            setAddSigningTime(false);
        }

        @Override
        protected X509Certificate getSigningCertificate() throws Exception {
            return null;
        }

        @Override
        protected X509Certificate[] getSigningCertificateChain() throws Exception {
            Certificate[] certs = keystore.getCertificateChain(alias);
            X509Certificate[] result = new X509Certificate[certs.length];
            int counter = 0;
            for (Certificate cert : certs) {
                result[counter] = (X509Certificate) cert;
                counter++;
            }
            return result;
        }

        private static KeyStore getKeystore() {

            try {
                return TestKeystore.getKeyStore();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

    }
}
