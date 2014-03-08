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
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureFormatException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidKeyException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureNoKeyException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Creates from the message body a XML signature element which is returned in
 * the message body of the output message. Enveloped and enveloping XML
 * signatures are supported.
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
 * In both cases, the digest algorithm is either read from the configuration
 * method {@link XmlSignerConfiguration#getDigestAlgorithm()} or calculated from
 * the signature algorithm (
 * {@link XmlSignerConfiguration#getSignatureAlgorithm()}. The optional
 * transforms are read from {@link XmlSignerConfiguration#getTransformMethods()}
 * .
 * <p>
 * In both cases, you can add additional references and objects which contain
 * properties for the XML signature, see
 * {@link XmlSignerConfiguration#setProperties(XmlSignatureProperties)}.
 */

public class XmlSignerProcessor extends XmlSignatureProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(XmlSignerProcessor.class);

    private static final String SHA512 = "sha512";

    private static final String SHA384 = "sha384";

    private static final String SHA256 = "sha256";

    private static final String SHA1 = "sha1";

    private static final String HTTP_WWW_W3_ORG_2001_04_XMLDSIG_MORE_SHA384 = "http://www.w3.org/2001/04/xmldsig-more#sha384";

    private final XmlSignerConfiguration config;

    public XmlSignerProcessor(XmlSignerConfiguration config) {
        super();
        this.config = config;
    }

    @Override
    public XmlSignerConfiguration getConfiguration() {
        return config;
    }

    @Override
    public void process(Exchange exchange) throws Exception { //NOPMD

        try {
            LOG.debug("XML signature generation started using algorithm {} and canonicalization method {}", getConfiguration()
                    .getSignatureAlgorithm(), getConfiguration().getCanonicalizationMethod().getAlgorithm());

            // lets setup the out message before we invoke the signing
            // so that it can mutate it if necessary
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());

            Document outputDoc = sign(out);

            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            XmlSignatureHelper.transformNonTextNodeToOutputStream(outputDoc, outStream, omitXmlDeclaration(out));
            byte[] data = outStream.toByteArray();
            out.setBody(data);
            clearMessageHeaders(out);
            LOG.debug("XML signature generation finished");
        } catch (Exception e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        }
    }

    protected Document sign(final Message out) throws Exception { //NOPMD

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

            Node parent = getParentOfSignature(out, node);

            final KeySelector keySelector = getConfiguration().getKeyAccessor().getKeySelector(out);
            if (keySelector == null) {
                throw new XmlSignatureNoKeyException(
                        "Key selector is missing for XML signature generation. Specify a key selector in the configuration.");
            }

            // the method KeyAccessor.getKeyInfo must be called after the method KeyAccessor.getKeySelector, this is part of the interface contract!
            final KeyInfo keyInfo = getConfiguration().getKeyAccessor().getKeyInfo(out, node, fac.getKeyInfoFactory());

            final String signatureId = "_" + UUID.randomUUID().toString();
            LOG.debug("Signature Id {}", signatureId);

            XmlSignatureProperties.Input input = new InputBuilder().contentDigestAlgorithm(getDigestAlgorithmUri()).keyInfo(keyInfo)
                    .message(out).messageBodyNode(node).parent(parent).signatureAlgorithm(getConfiguration().getSignatureAlgorithm())
                    .signatureFactory(fac).signatureId(signatureId).build();

            XmlSignatureProperties.Output properties = getSignatureProperties(input);

            List<? extends XMLObject> objects = getObjects(input, properties);
            List<? extends Reference> refs = getReferences(input, properties, getKeyInfoId(keyInfo));

            SignedInfo si = createSignedInfo(fac, refs);

            if (parent == null) {
                // for enveloping signature, create new document 
                parent = XmlSignatureHelper.newDocumentBuilder(Boolean.TRUE).newDocument();
            }

            DOMSignContext dsc = createAndConfigureSignContext(parent, keySelector);

            XMLSignature signature = fac.newXMLSignature(si, keyInfo, objects, signatureId, null);
            // generate the signature
            signature.sign(dsc);

            return XmlSignatureHelper.getDocument(parent);

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

    protected XmlSignatureProperties.Output getSignatureProperties(XmlSignatureProperties.Input input) throws Exception { //NOPMD
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

    protected SignedInfo createSignedInfo(XMLSignatureFactory fac, List<? extends Reference> refs) throws Exception { //NOPMD
        return fac.newSignedInfo(fac.newCanonicalizationMethod(getConfiguration().getCanonicalizationMethod().getAlgorithm(),
                (C14NMethodParameterSpec) getConfiguration().getCanonicalizationMethod().getParameterSpec()),
                getSignatureMethod(getConfiguration().getSignatureAlgorithm(), fac), refs);
    }

    private SignatureMethod getSignatureMethod(String signatureAlgorithm, XMLSignatureFactory fac) throws NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {
        return fac.newSignatureMethod(signatureAlgorithm, null);
    }

    protected Node getMessageBodyNode(Message message) throws Exception { //NOPMD
        InputStream is = message.getMandatoryBody(InputStream.class);

        Boolean isPlainText = isPlainText(message);

        Node node;
        if (isPlainText != null && isPlainText) {
            node = getTextNode(message, is);
        } else {
            Document doc = parseInput(is, getConfiguration().getDisallowDoctypeDecl());
            node = doc.getDocumentElement();
            LOG.debug("Root element of document to be signed: {}", node);
        }
        return node;
    }

    protected Boolean isPlainText(Message message) {
        Boolean isPlainText = message.getHeader(XmlSignatureConstants.HEADER_MESSAGE_IS_PLAIN_TEXT, Boolean.class);
        if (isPlainText == null) {
            isPlainText = getConfiguration().getPlainText();
        }
        LOG.debug("Is plain text: {}", isPlainText);
        return isPlainText;
    }

    protected Element getParentOfSignature(Message inMessage, Node messageBodyNode) throws Exception { //NOPMD
        if (getConfiguration().getParentLocalName() == null) {
            return null;
        }
        if (messageBodyNode.getParentNode() == null || messageBodyNode.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            throw new XmlSignatureFormatException(
                    "Incomming message has wrong format: It is not an XML document. Cannot create an enveloped XML signature.");
        }

        Document doc = (Document) messageBodyNode.getParentNode();
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

    protected List<? extends Reference> getReferences(XmlSignatureProperties.Input input, XmlSignatureProperties.Output properties,
            String keyInfoId) throws Exception { //NOPMD

        // Create Reference with URI="#<objectId>" for enveloping signature or URI="" for enveloped signature and the transforms
        Reference ref = createReference(input.getSignatureFactory(), getContentReferenceUri(input.getMessage()),
                getContentReferenceType(input.getMessage()));
        Reference keyInfoRef = createKeyInfoReference(input.getSignatureFactory(), keyInfoId, input.getContentDigestAlgorithm());

        int propsRefsSize = properties == null || properties.getReferences() == null || properties.getReferences().isEmpty() ? 0
                : properties.getReferences().size();
        int size = keyInfoRef == null ? propsRefsSize + 1 : propsRefsSize + 2;
        List<Reference> referenceList = new ArrayList<Reference>(size);
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
        throws Exception { //NOPMD
        
        if (isEnveloped()) {
            if (properties == null || properties.getObjects() == null) {
                return Collections.emptyList();
            }
            return properties.getObjects();
        }
        
        final String objectId = getConfiguration().getContentObjectId();
        LOG.debug("Object Content Id {}", objectId);
        
        XMLObject obj = createXMLObject(input.getSignatureFactory(), input.getMessageBodyNode(), objectId);
        if (properties == null || properties.getObjects() == null || properties.getObjects().isEmpty()) {
            return Collections.singletonList(obj);
        }
        List<XMLObject> result = new ArrayList<XMLObject>(properties.getObjects().size() + 1);
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
            String text = new String(bos.toByteArray(), encoding);
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

    protected Document parseInput(InputStream is, Boolean disallowDoctypeDecl) throws XmlSignatureFormatException,
            ParserConfigurationException, IOException {
        try {
            return XmlSignatureHelper.newDocumentBuilder(disallowDoctypeDecl).parse(is);
        } catch (SAXException e) {
            throw new XmlSignatureFormatException(
                    "XML signature generation not possible. Sent message is not an XML document. Check the sent message.", e);
        } finally {
            IOHelper.close(is, "input stream");
        }
    }

    protected Reference createReference(XMLSignatureFactory fac, String uri, String type) throws InvalidAlgorithmParameterException,
            XmlSignatureException {
        try {
            List<Transform> transforms = getTransforms(fac);
            Reference ref = fac.newReference(uri, fac.newDigestMethod(getDigestAlgorithmUri(), null), transforms, type, null);
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

    protected String getContentReferenceUri(Message message) {
        String uri = message.getHeader(XmlSignatureConstants.HEADER_CONTENT_REFERENCE_URI, String.class);
        if (uri == null) {
            uri = getConfiguration().getContentReferenceUri();
        }
        if (uri == null) {
            uri = isEnveloped() ? "" : "#" + getConfiguration().getContentObjectId();
        }
        LOG.debug("Content reference uri: {}", uri);
        return uri;
    }

    protected XMLObject createXMLObject(XMLSignatureFactory fac, Node node, String id) {
        return fac.newXMLObject(Collections.singletonList(new DOMStructure(node)), id, null, null);
    }

    private List<Transform> getTransforms(XMLSignatureFactory fac) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        boolean isEnveloped = isEnveloped();
        List<AlgorithmMethod> configuredTrafos = getConfiguration().getTransformMethods();
        if (isEnveloped) {
            // add enveloped transform if necessary
            if (configuredTrafos.size() > 0) {
                if (!containsEnvelopedTransform(configuredTrafos)) {
                    configuredTrafos = new ArrayList<AlgorithmMethod>(configuredTrafos.size() + 1);
                    configuredTrafos.add(XmlSignatureHelper.getEnvelopedTransform());
                    configuredTrafos.addAll(getConfiguration().getTransformMethods());
                }
            } else {
                // add enveloped and C14N trafo
                configuredTrafos = new ArrayList<AlgorithmMethod>(2);
                configuredTrafos.add(XmlSignatureHelper.getEnvelopedTransform());
                configuredTrafos.add(XmlSignatureHelper.getCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE));
            }
        }

        List<Transform> transforms = new ArrayList<Transform>(configuredTrafos.size());
        for (AlgorithmMethod trafo : configuredTrafos) {
            Transform transform = fac.newTransform(trafo.getAlgorithm(), (TransformParameterSpec) trafo.getParameterSpec());
            transforms.add(transform);
            LOG.debug("Transform method: {}", trafo.getAlgorithm());
        }
        return transforms;
    }

    protected boolean isEnveloped() {
        return getConfiguration().getParentLocalName() != null;
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
                } else if (signatureAlgorithm.contains(SHA256)) {
                    result = DigestMethod.SHA256;
                } else if (signatureAlgorithm.contains(SHA384)) {
                    result = HTTP_WWW_W3_ORG_2001_04_XMLDSIG_MORE_SHA384;
                } else if (signatureAlgorithm.contains(SHA512)) {
                    result = DigestMethod.SHA512;
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

    protected Reference createKeyInfoReference(XMLSignatureFactory fac, String keyInfoId, String digestAlgorithm) throws Exception { //NOPMD

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
        List<Transform> transforms = new ArrayList<Transform>(1);
        Transform transform = fac.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null);
        transforms.add(transform);
        return fac.newReference("#" + keyInfoId, fac.newDigestMethod(digestAlgorithm, null), transforms, null, null);
    }

    private String getKeyInfoId(KeyInfo keyInfo) throws Exception { //NOPMD
        if (keyInfo == null) {
            return null;
        }
        return keyInfo.getId();
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

            };
        }

    }
}
