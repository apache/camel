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
package org.apache.camel.component.xmlsecurity.processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.SignatureType;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureFormatException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidKeyException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureNoKeyException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.support.processor.validation.DefaultValidationErrorHandler;
import org.apache.camel.support.processor.validation.ValidatorErrorHandler;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates from the message body a XML signature element which is returned in
 * the message body of the output message. Enveloped, enveloping XML, and
 * detached signatures are supported.
 * <p>
 * In the enveloped XML signature case, the method
 * {@link XmlSignerConfiguration#getParentLocalName()} must not return
 * <code>null</code>. In this case the parent element must be contained in the
 * XML document provided by the message body and the signature element is added
 * as last child element of the parent element. If a KeyInfo instance is
 * provided by the {@link KeyAccessor} and
 * {@link XmlSignerConfiguration#getAddKeyInfoReference()} is <code>true</code>,
 * then also a reference to the KeyInfo element is added. The generated XML
 * signature has the following structure:
 * 
 * <pre>
 * {@code
 * <[parent element]>
 *     ...
 *      <Signature Id="[signature_id]">
 *          <SignedInfo>
 *                <Reference URI=""> 
 *                      <Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
 *                      (<Transform>)*
 *                      <DigestMethod>
 *                      <DigestValue>
 *                </Reference>
 *                (<Reference URI="#[keyinfo_Id]">
 *                      <Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
 *                      <DigestMethod>
 *                      <DigestValue>
 *                </Reference>)?
 *                <!-- further references possible, see XmlSignerConfiguration#setProperties(XmlSignatureProperties) -->
 *         </SignedInfo>
 *         <SignatureValue>
 *         (<KeyInfo Id="[keyinfo_id]">)?
 *         <!-- Object elements possible, see XmlSignerConfiguration#setProperties(XmlSignatureProperties) -->
 *     </Signature>
 * </[parent element]>
 * }
 * </pre>
 * <p>
 * In the enveloping XML signature case, the generated XML signature has the
 * following structure:
 * 
 * <pre>
 *  {@code
 *  <Signature Id="[signature_id]">
 *     <SignedInfo>
 *            <Reference URI="#[object_id]" type="[optional_type_value]"> 
 *                  (<Transform>)*
 *                  <DigestMethod>
 *                  <DigestValue>
 *            </Reference>
 *            (<Reference URI="#[keyinfo_id]">
 *                  <Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
 *                  <DigestMethod>
 *                  <DigestValue>
 *            </Reference>)?
 *             <!-- further references possible, see XmlSignerConfiguration#setProperties(XmlSignatureProperties) -->
 *     </SignedInfo>
 *     <SignatureValue>
 *     (<KeyInfo Id="[keyinfo_id]">)?
 *     <Object Id="[object_id]"/>
 *     <!-- further Object elements possible, see XmlSignerConfiguration#setProperties(XmlSignatureProperties) -->
 * </Signature>   
 *  }
 * </pre>
 * 
 * In the enveloping XML signature case, also message bodies containing plain
 * text are supported. This must be indicated via the header
 * {@link XmlSignatureConstants#HEADER_MESSAGE_IS_PLAIN_TEXT} or via the
 * configuration {@link XmlSignerConfiguration#getPlainText()}.
 * 
 * <p>
 * Detached signatures where the signature element is a sibling element to the
 * signed element are supported. Those elements can be signed which have ID
 * attributes. The elements to be signed must be specified via xpath expressions
 * (see {@link XmlSignerConfiguration#setXpathsToIdAttributes(List)}) and the
 * XML schema must be provided via the schema resource URI (see method
 * {@link XmlSignerConfiguration#setSchemaResourceUri(String)}. Elements with
 * deeper hierarchy level are signed first. This procedure can result in nested
 * signatures.
 * 
 * <p>
 * In all cases, the digest algorithm is either read from the configuration
 * method {@link XmlSignerConfiguration#getDigestAlgorithm()} or calculated from
 * the signature algorithm (
 * {@link XmlSignerConfiguration#getSignatureAlgorithm()}. The optional
 * transforms are read from {@link XmlSignerConfiguration#getTransformMethods()}
 * .
 * <p>
 * In all cases, you can add additional references and objects which contain
 * properties for the XML signature, see
 * {@link XmlSignerConfiguration#setProperties(XmlSignatureProperties)}.
 */

public class XmlSignerProcessor extends XmlSignatureProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(XmlSignerProcessor.class);

    private static final String SHA512 = "sha512";
    private static final String SHA384 = "sha384";
    private static final String SHA256 = "sha256";
    private static final String SHA224 = "sha224";
    private static final String SHA1 = "sha1";
    private static final String RIPEMD160 = "ripemd160";

    private static final String HTTP_WWW_W3_ORG_2001_04_XMLDSIG_MORE_SHA224 = 
        "http://www.w3.org/2001/04/xmldsig-more#sha224"; // see RFC 4051
    
    private static final String HTTP_WWW_W3_ORG_2001_04_XMLDSIG_MORE_SHA384 = 
        "http://www.w3.org/2001/04/xmldsig-more#sha384"; // see RFC 4051

    private final XmlSignerConfiguration config;
    
    public XmlSignerProcessor(CamelContext context, XmlSignerConfiguration config) {
        super(context);
        this.config = config;
    }

    @Override
    public XmlSignerConfiguration getConfiguration() {
        return config;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        try {
            LOG.debug("XML signature generation started using algorithm {} and canonicalization method {}", getConfiguration()
                    .getSignatureAlgorithm(), getConfiguration().getCanonicalizationMethod().getAlgorithm());

            // lets setup the out message before we invoke the signing
            // so that it can mutate it if necessary
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());

            Document outputDoc = sign(out);

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            XmlSignatureHelper.transformNonTextNodeToOutputStream(outputDoc, outStream, omitXmlDeclaration(out), getConfiguration().getOutputXmlEncoding());
            byte[] data = outStream.toByteArray();
            out.setBody(data);
            setOutputEncodingToMessageHeader(out);
            clearMessageHeaders(out);
            LOG.debug("XML signature generation finished");
        } catch (Exception e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        }
    }

    protected Document sign(final Message out) throws Exception {

        try {
            XMLSignatureFactory fac;
            // Try to install the Santuario Provider - fall back to the JDK provider if this does
            // not work
            try {
                fac = XMLSignatureFactory.getInstance("DOM", "ApacheXMLDSig");
            } catch (NoSuchProviderException ex) {
                fac = XMLSignatureFactory.getInstance("DOM");
            }

            final Node node = getMessageBodyNode(out);

            if (getConfiguration().getKeyAccessor() == null) {
                throw new XmlSignatureNoKeyException(
                    "Key accessor is missing for XML signature generation. Specify a key accessor in the configuration.");
            }
            
            final KeySelector keySelector = getConfiguration().getKeyAccessor().getKeySelector(out);
            if (keySelector == null) {
                throw new XmlSignatureNoKeyException(
                        "Key selector is missing for XML signature generation. Specify a key selector in the configuration.");
            }

            SignatureType signatureType = determineSignatureType(out);

            final List<String> contentReferenceUris = getContentReferenceUris(out, signatureType, node);

            Node lastParent = null;
            // per content reference URI a signature is built; for enveloped and enveloping there is only one content reference URI;
            // only in the detached case there can be several
            for (final String contentReferenceUri : contentReferenceUris) {

                // the method KeyAccessor.getKeyInfo must be called after the method KeyAccessor.getKeySelector, this is part of the interface contract!
                // and this method must be called within the loop over the content reference URIs, because for each signature the key info ID must be different
                final KeyInfo keyInfo = getConfiguration().getKeyAccessor().getKeyInfo(out, node, fac.getKeyInfoFactory());

                String signatureId = getConfiguration().getSignatureId();
                if (signatureId == null) {
                    signatureId = "_" + UUID.randomUUID().toString();
                } else if (signatureId.isEmpty()) {
                    // indicator that no signature Id attribute shall be generated
                    signatureId = null;
                }

                // parent only relevant for enveloped or detached signature
                Node parent = getParentOfSignature(out, node, contentReferenceUri, signatureType);

                if (parent == null) {
                    // for enveloping signature, create new document 
                    parent = XmlSignatureHelper.newDocumentBuilder(Boolean.TRUE).newDocument();
                }
                lastParent = parent;

                XmlSignatureProperties.Input input = new InputBuilder().contentDigestAlgorithm(getDigestAlgorithmUri()).keyInfo(keyInfo)
                        .message(out).messageBodyNode(node).parent(parent).signatureAlgorithm(getConfiguration().getSignatureAlgorithm())
                        .signatureFactory(fac).signatureId(signatureId).contentReferenceUri(contentReferenceUri)
                        .signatureType(signatureType)
                        .prefixForXmlSignatureNamespace(getConfiguration().getPrefixForXmlSignatureNamespace()).build();

                XmlSignatureProperties.Output properties = getSignatureProperties(input);

                
                // the signature properties can overwrite the signature Id
                if (properties != null && properties.getSignatureId() != null && !properties.getSignatureId().isEmpty()) {
                    signatureId = properties.getSignatureId();
                }

                List<? extends XMLObject> objects = getObjects(input, properties);
                List<? extends Reference> refs = getReferences(input, properties, getKeyInfoId(keyInfo));

                SignedInfo si = createSignedInfo(fac, refs);

                DOMSignContext dsc = createAndConfigureSignContext(parent, keySelector);

                XMLSignature signature = fac.newXMLSignature(si, keyInfo, objects, signatureId, null);
                // generate the signature
                signature.sign(dsc);
            }

            return XmlSignatureHelper.getDocument(lastParent);

        } catch (XMLSignatureException se) {
            if (se.getCause() instanceof InvalidKeyException) {
                throw new XmlSignatureInvalidKeyException(se.getMessage(), se);
            } else {
                throw new XmlSignatureException(se);
            }
        } catch (GeneralSecurityException e) {
            // like NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException
            throw new XmlSignatureException(e);
        }

    }

    private SignatureType determineSignatureType(Message message) throws XmlSignatureException {
        if (getConfiguration().getParentLocalName() != null && getConfiguration().getParentXpath() != null) {
            throw new XmlSignatureException(
                    "The configuration of the XML signer component is wrong. The parent local name "
                            + getConfiguration().getParentLocalName()
                            + " and the parent XPath " + getConfiguration().getParentXpath().getXPath() + " are specified. You must not specify both parameters.");

        }

        boolean isEnveloped = getConfiguration().getParentLocalName() != null || getConfiguration().getParentXpath() != null;

        boolean isDetached = getXpathToIdAttributes(message).size() > 0;

        if (isEnveloped && isDetached) {
            if (getConfiguration().getParentLocalName() != null) {
                throw new XmlSignatureException(
                    "The configuration of the XML signer component is wrong. The parent local name "
                            + getConfiguration().getParentLocalName()
                            + " for an enveloped signature and the XPATHs to ID attributes for a detached signature are specified. You must not specify both parameters.");
            } else {
                throw new XmlSignatureException(
                        "The configuration of the XML signer component is wrong. The parent XPath "
                                + getConfiguration().getParentXpath().getXPath()
                                + " for an enveloped signature and the XPATHs to ID attributes for a detached signature are specified. You must not specify both parameters.");

            }
        }

        SignatureType result;
        if (isEnveloped) {
            result = SignatureType.enveloped;
        } else if (isDetached) {
            if (getSchemaResourceUri(message) == null) {
                throw new XmlSignatureException(
                        "The configruation of the XML Signature component is wrong: No XML schema specified in the detached case");
            }
            result = SignatureType.detached;
        } else {
            result = SignatureType.enveloping;
        }

        LOG.debug("Signature type: {}", result);
        return result;

    }

    protected List<XPathFilterParameterSpec> getXpathToIdAttributes(Message message) {

        @SuppressWarnings("unchecked")
        List<XPathFilterParameterSpec> result = (List<XPathFilterParameterSpec>) message
                .getHeader(XmlSignatureConstants.HEADER_XPATHS_TO_ID_ATTRIBUTES);
        if (result == null) {
            result = getConfiguration().getXpathsToIdAttributes();
        }
        return result;
    }

    protected XmlSignatureProperties.Output getSignatureProperties(XmlSignatureProperties.Input input) throws Exception {
        XmlSignatureProperties propGetter = getConfiguration().getProperties();
        XmlSignatureProperties.Output propsOutput = null;
        if (propGetter != null) {
            propsOutput = propGetter.get(input);
        }
        return propsOutput;
    }

    private DOMSignContext createAndConfigureSignContext(Node parent, KeySelector keySelector) {
        DOMSignContext dsc = new DOMSignContext(keySelector, parent);
        // set namespace prefix for "http://www.w3.org/2000/09/xmldsig#" according to best practice described in http://www.w3.org/TR/xmldsig-bestpractices/#signing-xml-without-namespaces
        if (getConfiguration().getPrefixForXmlSignatureNamespace() != null
                && !getConfiguration().getPrefixForXmlSignatureNamespace().isEmpty()) {
            dsc.putNamespacePrefix("http://www.w3.org/2000/09/xmldsig#", getConfiguration().getPrefixForXmlSignatureNamespace());
        }
        dsc.putNamespacePrefix("http://www.w3.org/2001/10/xml-exc-c14n#", "ec");
        setCryptoContextProperties(dsc);
        setUriDereferencerAndBaseUri(dsc);
        return dsc;
    }

    protected Boolean omitXmlDeclaration(Message message) {
        Boolean omitXmlDeclaration = message.getHeader(XmlSignatureConstants.HEADER_OMIT_XML_DECLARATION, Boolean.class);
        if (omitXmlDeclaration == null) {
            omitXmlDeclaration = getConfiguration().getOmitXmlDeclaration();
        }
        if (omitXmlDeclaration == null) {
            omitXmlDeclaration = Boolean.FALSE;
        }
        LOG.debug("Omit XML declaration: {}", omitXmlDeclaration);
        return omitXmlDeclaration;
    }

    protected SignedInfo createSignedInfo(XMLSignatureFactory fac, List<? extends Reference> refs) throws Exception {
        return fac.newSignedInfo(fac.newCanonicalizationMethod(getConfiguration().getCanonicalizationMethod().getAlgorithm(),
                (C14NMethodParameterSpec) getConfiguration().getCanonicalizationMethod().getParameterSpec()),
                getSignatureMethod(getConfiguration().getSignatureAlgorithm(), fac), refs);
    }

    private SignatureMethod getSignatureMethod(String signatureAlgorithm, XMLSignatureFactory fac) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {
        return fac.newSignatureMethod(signatureAlgorithm, null);
    }

    protected Node getMessageBodyNode(Message message) throws Exception {
        InputStream is = message.getMandatoryBody(InputStream.class);

        Boolean isPlainText = isPlainText(message);

        Node node;
        if (isPlainText != null && isPlainText) {
            node = getTextNode(message, is);
        } else {
            ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
            Schema schema = getSchemaForSigner(message, errorHandler);
            Document doc = parseInput(is, getConfiguration().getDisallowDoctypeDecl(), schema, errorHandler);
            errorHandler.handleErrors(message.getExchange(), schema, null); // throws ValidationException
            node = doc.getDocumentElement();
            LOG.debug("Root element of document to be signed: {}", node);
        }
        return node;
    }

    protected Schema getSchemaForSigner(Message message, ValidatorErrorHandler errorHandler) throws XmlSignatureException, SAXException,
            IOException {
        Schema schema;
        String schemaResourceUri = getSchemaResourceUri(message);
        if (schemaResourceUri == null) {
            schema = null;
        } else {
            schema = getSchema(message);
        }
        return schema;
    }

    protected Boolean isPlainText(Message message) {
        Boolean isPlainText = message.getHeader(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT, Boolean.class);
        if (isPlainText == null) {
            isPlainText = getConfiguration().getPlainText();
        }
        LOG.debug("Is plain text: {}", isPlainText);
        return isPlainText;
    }

    protected Element getParentOfSignature(Message inMessage, Node messageBodyNode, String contentReferenceURI, SignatureType sigType)
        throws Exception {
        if (SignatureType.enveloping == sigType) {
            // enveloping case
            return null;
        }
        if (messageBodyNode.getParentNode() == null || messageBodyNode.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            throw new XmlSignatureFormatException(
                    "Incomming message has wrong format: It is not an XML document. Cannot create an enveloped or detached XML signature.");
        }
        Document doc = (Document) messageBodyNode.getParentNode();
        if (SignatureType.detached == sigType) {
            return getParentForDetachedCase(doc, inMessage, contentReferenceURI);
        } else {
            // enveloped case
            return getParentForEnvelopedCase(doc, inMessage);
        }

    }
    
    protected Element getParentForEnvelopedCase(Document doc, Message inMessage) throws Exception {
        if (getConfiguration().getParentXpath() != null) {
            XPathFilterParameterSpec xp = getConfiguration().getParentXpath();
            XPathExpression exp;
            try {
                exp = XmlSignatureHelper.getXPathExpression(xp);
            } catch (XPathExpressionException e) {
                throw new XmlSignatureException("The parent XPath " + getConfiguration().getParentXpath().getXPath() + " is wrongly configured: The XPath " + xp.getXPath() + " is invalid.", e);
            }
            NodeList list = (NodeList) exp.evaluate(doc.getDocumentElement(), XPathConstants.NODESET);
            if (list == null || list.getLength() == 0) {
                throw new XmlSignatureException("The parent XPath " + xp.getXPath() + " returned no result. Check the configuration of the XML signer component.");
            }
            int length = list.getLength();
            for (int i = 0; i < length; i++) {
                Node node = list.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    // return the first element
                    return (Element)node;
                }
            }
            throw new XmlSignatureException("The parent XPath " + xp.getXPath() + " returned no element. Check the configuration of the XML signer component.");
        } else {
            // parent local name is not null!
            NodeList parents = doc.getElementsByTagNameNS(getConfiguration().getParentNamespace(), getConfiguration().getParentLocalName());
            if (parents == null || parents.getLength() == 0) {
                throw new XmlSignatureFormatException(
                        String.format(
                                "Incoming message has wrong format: The parent element with the local name %s and the namespace %s was not found in the message to build an enveloped XML signature.",
                                getConfiguration().getParentLocalName(), getConfiguration().getParentNamespace()));
            }
            // return the first element
            return (Element) parents.item(0);
        }
    }

    private Element getParentForDetachedCase(Document doc, Message inMessage, String referenceUri) throws XmlSignatureException {
        String elementId = referenceUri;
        if (elementId.startsWith("#")) {
            elementId = elementId.substring(1);
        }
        Element el = doc.getElementById(elementId);
        if (el == null) {
            // should not happen because has been checked before
            throw new IllegalStateException("No element found for element ID " + elementId);
        }
        LOG.debug("Sibling element of the detached XML Signature with reference URI {}: {}  {}",
                new Object[] {referenceUri, el.getLocalName(), el.getNamespaceURI() });
        Element result = getParentElement(el);
        if (result != null) {
            return result;
        } else {
            throw new XmlSignatureException(
                    "Either the configuration of the XML Signature component is wrong or the incoming document has an invalid structure: The element "
                            + el.getLocalName() + "{" + el.getNamespaceURI() + "} which is referenced by the reference URI " + referenceUri
                            + " has no parent element. The element must have a parent element in the configured detached case.");
        }
    }

    private Element getParentElement(Node node) {
        int counter = 0;
        while (node != null && counter < 10000) {
            // counter is for avoiding security attacks
            Node parent = node.getParentNode();
            if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) parent;
            }
            node = parent;
            counter++;
        }
        return null;
    }

    protected List<? extends Reference> getReferences(XmlSignatureProperties.Input input, XmlSignatureProperties.Output properties,
            String keyInfoId) throws Exception {

        String referenceId = properties == null ? null : properties.getContentReferenceId();
        // Create Reference with URI="#<objectId>" for enveloping signature, URI="" for enveloped signature, and URI = <value from configuration> for detached signature and the transforms
        Reference ref = createReference(input.getSignatureFactory(), input.getContentReferenceUri(),
                getContentReferenceType(input.getMessage()), input.getSignatureType(), referenceId, input.getMessage());
        Reference keyInfoRef = createKeyInfoReference(input.getSignatureFactory(), keyInfoId, input.getContentDigestAlgorithm());

        int propsRefsSize = properties == null || properties.getReferences() == null || properties.getReferences().isEmpty() ? 0
                : properties.getReferences().size();
        int size = keyInfoRef == null ? propsRefsSize + 1 : propsRefsSize + 2;
        List<Reference> referenceList = new ArrayList<>(size);
        referenceList.add(ref);
        if (keyInfoRef != null) {
            referenceList.add(keyInfoRef);
        }
        if (properties != null && properties.getReferences() != null && !properties.getReferences().isEmpty()) {
            referenceList.addAll(properties.getReferences());
        }
        return referenceList;
    }

    protected List<? extends XMLObject> getObjects(XmlSignatureProperties.Input input, XmlSignatureProperties.Output properties)
        throws Exception {

        if (SignatureType.enveloped == input.getSignatureType() || SignatureType.detached == input.getSignatureType()) {
            if (properties == null || properties.getObjects() == null) {
                return Collections.emptyList();
            }
            return properties.getObjects();
        }

        // enveloping signature --> add additional object
        final String objectId = getConfiguration().getContentObjectId();
        LOG.debug("Object Content Id {}", objectId);

        XMLObject obj = createXMLObject(input.getSignatureFactory(), input.getMessageBodyNode(), objectId);
        if (properties == null || properties.getObjects() == null || properties.getObjects().isEmpty()) {
            return Collections.singletonList(obj);
        }
        List<XMLObject> result = new ArrayList<>(properties.getObjects().size() + 1);
        result.add(obj);
        result.addAll(properties.getObjects());
        return result;
    }

    private Node getTextNode(Message inMessage, InputStream is) throws IOException, ParserConfigurationException, XmlSignatureException {
        LOG.debug("Message body to be signed is plain text");
        String encoding = getMessageEncoding(inMessage);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, bos);
        try {
            String text = bos.toString(encoding);
            return XmlSignatureHelper.newDocumentBuilder(true).newDocument().createTextNode(text);
        } catch (UnsupportedEncodingException e) {
            throw new XmlSignatureException(String.format("The message encoding %s is not supported.", encoding), e);
        }
    }

    protected String getMessageEncoding(Message inMessage) {
        String encoding = inMessage.getHeader(XmlSignatureConstants.HEADER_PLAIN_TEXT_ENCODING, String.class);
        if (encoding == null) {
            encoding = getConfiguration().getPlainTextEncoding();
        }
        LOG.debug("Messge encoding: {}", encoding);
        return encoding;
    }

    protected Document parseInput(InputStream is, Boolean disallowDoctypeDecl, Schema schema, ErrorHandler errorHandler)
        throws ParserConfigurationException, IOException, XmlSignatureFormatException {
        try {
            DocumentBuilder db = XmlSignatureHelper.newDocumentBuilder(disallowDoctypeDecl, schema);
            db.setErrorHandler(errorHandler);
            return db.parse(is);
        } catch (SAXException e) {
            throw new XmlSignatureFormatException(
                    "XML signature generation not possible. Sent message is not an XML document. Check the sent message.", e);
        } finally {
            IOHelper.close(is, "input stream");
        }
    }

    protected Reference createReference(XMLSignatureFactory fac, String uri, String type, SignatureType sigType, String id, Message message)
        throws InvalidAlgorithmParameterException, XmlSignatureException {
        try {
            List<Transform> transforms = getTransforms(fac, sigType, message);
            Reference ref = fac.newReference(uri, fac.newDigestMethod(getDigestAlgorithmUri(), null), transforms, type, id);
            return ref;
        } catch (NoSuchAlgorithmException e) {
            throw new XmlSignatureException("Wrong algorithm specified in the configuration.", e);
        }
    }

    protected String getContentReferenceType(Message message) {
        String type = message.getHeader(XmlSignatureConstants.HEADER_CONTENT_REFERENCE_TYPE, String.class);
        if (type == null) {
            type = getConfiguration().getContentReferenceType();
        }
        LOG.debug("Content reference type: {}", type);
        return type;
    }

    protected List<String> getContentReferenceUris(Message message, SignatureType signatureType, Node messageBodyNode)
        throws XmlSignatureException, XPathExpressionException {

        List<String> result;
        if (SignatureType.enveloping == signatureType) {
            String uri = "#" + getConfiguration().getContentObjectId();
            result = Collections.singletonList(uri);
        } else if (SignatureType.enveloped == signatureType) {
            // only for enveloped the parameter content reference URI is used
            String uri = message.getHeader(XmlSignatureConstants.HEADER_CONTENT_REFERENCE_URI, String.class);
            if (uri == null) {
                uri = getConfiguration().getContentReferenceUri();
            }
            if (uri == null) {
                uri = "";
            }
            result = Collections.singletonList(uri);
        } else if (SignatureType.detached == signatureType) {
            result = getContentReferenceUrisForDetachedCase(message, messageBodyNode);
        } else {
            // should not occur
            throw new IllegalStateException("Signature type " + signatureType + " not supported");
        }

        LOG.debug("Content reference URI(s): {}", result);
        return result;
    }

    private List<String> getContentReferenceUrisForDetachedCase(Message message, Node messageBodyNode) throws XmlSignatureException,
            XPathExpressionException {
        List<XPathFilterParameterSpec> xpathsToIdAttributes = getXpathToIdAttributes(message);
        if (xpathsToIdAttributes.isEmpty()) {
            // should not happen, has already been checked earlier
            throw new IllegalStateException("List of XPATHs to ID attributes is empty in detached signature case");
        }
        List<ComparableNode> result = new ArrayList<>(xpathsToIdAttributes.size());
        for (XPathFilterParameterSpec xp : xpathsToIdAttributes) {
            XPathExpression exp;
            try {
                exp = XmlSignatureHelper.getXPathExpression(xp);
            } catch (XPathExpressionException e) {
                throw new XmlSignatureException("The configured xpath expression " + xp.getXPath() + " is invalid.", e);
            }
            NodeList list = (NodeList) exp.evaluate(messageBodyNode, XPathConstants.NODESET);
            if (list == null) {
                //assume optional element, XSD validation has been done before
                LOG.warn("No ID attribute found for xpath expression {}. Therfore this xpath expression will be ignored.", xp.getXPath());
                continue;
            }
            int length = list.getLength();
            for (int i = 0; i < length; i++) {
                Node node = list.item(i);
                if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    Attr attr = (Attr) node;
                    String value = attr.getValue();
                    // check that attribute is ID attribute
                    Element element = messageBodyNode.getOwnerDocument().getElementById(value);
                    if (element == null) {
                        throw new XmlSignatureException(
                                "Wrong configured xpath expression for ID attributes: The evaluation of the xpath expression "
                                        + xp.getXPath() + " resulted in an attribute which is not of type ID. The attribute value is "
                                        + value + ".");
                    }
                    result.add(new ComparableNode(element, "#" + value));
                    LOG.debug("ID attribute with value {} found for xpath {}", value, xp.getXPath());
                } else {
                    throw new XmlSignatureException(
                            "Wrong configured xpath expression for ID attributes: The evaluation of the xpath expression " + xp.getXPath()
                                    + " returned a node which was not of type Attribute.");
                }
            }
        }
        if (result.size() == 0) {
            throw new XmlSignatureException(
                    "No element to sign found in the detached case. No node found for the configured xpath expressions "
                            + toString(xpathsToIdAttributes)
                            + ". Either the configuration of the XML signature component is wrong or the incoming message has not the correct structure.");
        }
        // sort so that elements with deeper hierarchy level are treated first
        Collections.sort(result);
        return ComparableNode.getReferenceUris(result);
    }

    private String toString(List<XPathFilterParameterSpec> xpathsToIdAttributes) {
        StringBuilder result = new StringBuilder();
        int counter = 0;
        for (XPathFilterParameterSpec xp : xpathsToIdAttributes) {
            counter++;
            result.append(xp.getXPath());
            if (counter < xpathsToIdAttributes.size()) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    protected XMLObject createXMLObject(XMLSignatureFactory fac, Node node, String id) {
        return fac.newXMLObject(Collections.singletonList(new DOMStructure(node)), id, null, null);
    }

    private List<Transform> getTransforms(XMLSignatureFactory fac, SignatureType sigType, Message message) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {
        String transformMethodsHeaderValue = message.getHeader(XmlSignatureConstants.HEADER_TRANSFORM_METHODS, String.class);
        if (transformMethodsHeaderValue == null) {
            List<AlgorithmMethod> configuredTrafos = getConfiguration().getTransformMethods();
            if (SignatureType.enveloped == sigType) {
                // add enveloped transform if necessary
                if (configuredTrafos.size() > 0) {
                    if (!containsEnvelopedTransform(configuredTrafos)) {
                        configuredTrafos = new ArrayList<>(configuredTrafos.size() + 1);
                        configuredTrafos.add(XmlSignatureHelper.getEnvelopedTransform());
                        configuredTrafos.addAll(getConfiguration().getTransformMethods());
                    }
                } else {
                    // add enveloped and C14N trafo
                    configuredTrafos = new ArrayList<>(2);
                    configuredTrafos.add(XmlSignatureHelper.getEnvelopedTransform());
                    configuredTrafos.add(XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
                }
            }

            List<Transform> transforms = new ArrayList<>(configuredTrafos.size());
            for (AlgorithmMethod trafo : configuredTrafos) {
                Transform transform = fac.newTransform(trafo.getAlgorithm(), (TransformParameterSpec) trafo.getParameterSpec());
                transforms.add(transform);
                LOG.debug("Transform method: {}", trafo.getAlgorithm());
            }
            return transforms;
        } else {
            LOG.debug("Header {} with value '{}' found", XmlSignatureConstants.HEADER_TRANSFORM_METHODS, transformMethodsHeaderValue);
            String[] transformAlgorithms = transformMethodsHeaderValue.split(",");
            List<Transform> transforms = new ArrayList<>(transformAlgorithms.length);
            for (String transformAlgorithm : transformAlgorithms) {
                transformAlgorithm = transformAlgorithm.trim();
                Transform transform = fac.newTransform(transformAlgorithm, (TransformParameterSpec) null);
                transforms.add(transform);
                LOG.debug("Transform method: {}", transformAlgorithm);
            }
            return transforms;
        }
    }

    private boolean containsEnvelopedTransform(List<AlgorithmMethod> configuredTrafos) {
        for (AlgorithmMethod m : configuredTrafos) {
            if (Transform.ENVELOPED.equals(m.getAlgorithm())) {
                return true;
            }
        }
        return false;
    }

    protected String getDigestAlgorithmUri() throws XmlSignatureException {

        String result = getConfiguration().getDigestAlgorithm();
        if (result == null) {
            String signatureAlgorithm = getConfiguration().getSignatureAlgorithm();
            if (signatureAlgorithm != null) {
                if (signatureAlgorithm.contains(SHA1)) {
                    result = DigestMethod.SHA1;
                } else if (signatureAlgorithm.contains(SHA224)) {
                    result = HTTP_WWW_W3_ORG_2001_04_XMLDSIG_MORE_SHA224;
                } else if (signatureAlgorithm.contains(SHA256)) {
                    result = DigestMethod.SHA256;
                } else if (signatureAlgorithm.contains(SHA384)) {
                    result = HTTP_WWW_W3_ORG_2001_04_XMLDSIG_MORE_SHA384;
                } else if (signatureAlgorithm.contains(SHA512)) {
                    result = DigestMethod.SHA512;
                } else if (signatureAlgorithm.contains(RIPEMD160)) {
                    return DigestMethod.RIPEMD160;
                }
            }
        }
        if (result != null) {
            LOG.debug("Digest algorithm: {}", result);
            return result;
        }
        throw new XmlSignatureException(
                "Digest algorithm missing for XML signature generation. Specify the digest algorithm in the configuration.");
    }

    protected Reference createKeyInfoReference(XMLSignatureFactory fac, String keyInfoId, String digestAlgorithm) throws Exception {

        if (keyInfoId == null) {
            return null;
        }
        if (getConfiguration().getAddKeyInfoReference() == null) {
            return null;
        }

        if (!getConfiguration().getAddKeyInfoReference()) {
            return null;
        }

        LOG.debug("Creating reference to key info element with Id: {}", keyInfoId);
        List<Transform> transforms = new ArrayList<>(1);
        Transform transform = fac.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null);
        transforms.add(transform);
        return fac.newReference("#" + keyInfoId, fac.newDigestMethod(digestAlgorithm, null), transforms, null, null);
    }

    private String getKeyInfoId(KeyInfo keyInfo) throws Exception {
        if (keyInfo == null) {
            return null;
        }
        return keyInfo.getId();
    }
    
    protected void setOutputEncodingToMessageHeader(Message message) {
        if (getConfiguration().getOutputXmlEncoding() != null) {
            message.setHeader(Exchange.CHARSET_NAME, getConfiguration().getOutputXmlEncoding());
        }
    }


    private static class InputBuilder {

        private XMLSignatureFactory signatureFactory;

        private String signatureAlgorithm;

        private Node parent;

        private Node messageBodyNode;

        private Message message;

        private KeyInfo keyInfo;

        private String contentDigestAlgorithm;

        private String signatureId;

        private String contentReferenceUri;

        private SignatureType signatureType;

        private String prefixForXmlSignatureNamespace;

        public InputBuilder signatureFactory(XMLSignatureFactory signatureFactory) {
            this.signatureFactory = signatureFactory;
            return this;
        }

        public InputBuilder signatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
            return this;
        }

        public InputBuilder parent(Node parent) {
            this.parent = parent;
            return this;
        }

        public InputBuilder messageBodyNode(Node messageBodyNode) {
            this.messageBodyNode = messageBodyNode;
            return this;
        }

        public InputBuilder message(Message message) {
            this.message = message;
            return this;
        }

        public InputBuilder keyInfo(KeyInfo keyInfo) {
            this.keyInfo = keyInfo;
            return this;
        }

        public InputBuilder contentDigestAlgorithm(String contentDigestAlgorithm) {
            this.contentDigestAlgorithm = contentDigestAlgorithm;
            return this;
        }

        public InputBuilder signatureId(String signatureId) {
            this.signatureId = signatureId;
            return this;
        }

        public InputBuilder contentReferenceUri(String contentReferenceUri) {
            this.contentReferenceUri = contentReferenceUri;
            return this;
        }

        public InputBuilder signatureType(SignatureType signatureType) {
            this.signatureType = signatureType;
            return this;
        }

        public InputBuilder prefixForXmlSignatureNamespace(String prefixForXmlSignatureNamespace) {
            this.prefixForXmlSignatureNamespace = prefixForXmlSignatureNamespace;
            return this;
        }

        public XmlSignatureProperties.Input build() {
            return new XmlSignatureProperties.Input() {

                @Override
                public XMLSignatureFactory getSignatureFactory() {
                    return signatureFactory;
                }

                @Override
                public String getSignatureAlgorithm() {
                    return signatureAlgorithm;
                }

                @Override
                public Node getParent() {
                    return parent;
                }

                @Override
                public Node getMessageBodyNode() {
                    return messageBodyNode;
                }

                @Override
                public Message getMessage() {
                    return message;
                }

                @Override
                public KeyInfo getKeyInfo() {
                    return keyInfo;
                }

                @Override
                public String getContentDigestAlgorithm() {
                    return contentDigestAlgorithm;
                }

                @Override
                public String getSignatureId() {
                    return signatureId;
                }

                @Override
                public String getContentReferenceUri() {
                    return contentReferenceUri;
                }

                @Override
                public SignatureType getSignatureType() {
                    return signatureType;
                }

                @Override
                public String getPrefixForXmlSignatureNamespace() {
                    return prefixForXmlSignatureNamespace;
                }

            };
        }

    }

    /** Compares nodes by their hierarchy level. */
    static class ComparableNode implements Comparable<ComparableNode> {

        private final String referenceUri;
        private final int level;

        ComparableNode(Element node, String referenceUri) {
            this.referenceUri = referenceUri;
            level = calculateLevel(node);
        }

        private int calculateLevel(Element node) {
            int counter = 0;
            for (Node n = node; n != null; n = n.getParentNode()) {
                if (Node.ELEMENT_NODE == n.getNodeType()) {
                    counter++;
                    if (counter > 10000) {
                        // prevent security attack
                        throw new IllegalStateException("Hierachy level is limited to 10000");
                    }
                }
            }
            return counter;
        }

        @Override
        public int compareTo(ComparableNode o) {
            return o.level - level;
        }

        String getReferenceUri() {
            return referenceUri;
        }

        static List<String> getReferenceUris(List<ComparableNode> input) {
            List<String> result = new ArrayList<>(input.size());
            for (ComparableNode cn : input) {
                result.add(cn.getReferenceUri());
            }
            return result;
        }

    }

}
