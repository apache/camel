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

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.Manifest;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignature.SignatureValue;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.xmlsecurity.api.ValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureFormatException;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidException;
import org.apache.camel.support.processor.validation.DefaultValidationErrorHandler;
import org.apache.camel.support.processor.validation.ValidatorErrorHandler;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XML signature verifier. Assumes that the input XML contains exactly one
 * Signature element.
 */
public class XmlVerifierProcessor extends XmlSignatureProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(XmlVerifierProcessor.class);

    private final XmlVerifierConfiguration config;

    public XmlVerifierProcessor(CamelContext context, XmlVerifierConfiguration config) {
        super(context);
        this.config = config;
    }

    @Override
    public XmlVerifierConfiguration getConfiguration() {
        return config;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        InputStream stream = exchange.getIn().getMandatoryBody(InputStream.class);
        try {
            // lets setup the out message before we invoke the signing
            // so that it can mutate it if necessary
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            verify(stream, out);
            clearMessageHeaders(out);
        } catch (Exception e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        } finally {
            IOHelper.close(stream, "input stream");
        }
    }

    @SuppressWarnings("unchecked")
    protected void verify(InputStream input, final Message out) throws Exception {
        LOG.debug("Verification of XML signature document started");
        final Document doc = parseInput(input, out);

        XMLSignatureFactory fac;
        // Try to install the Santuario Provider - fall back to the JDK provider if this does
        // not work
        try {
            fac = XMLSignatureFactory.getInstance("DOM", "ApacheXMLDSig");
        } catch (NoSuchProviderException ex) {
            fac = XMLSignatureFactory.getInstance("DOM");
        }

        KeySelector selector = getConfiguration().getKeySelector();
        if (selector == null) {
            throw new IllegalStateException("Wrong configuration. Key selector is missing.");
        }

        DOMValidateContext valContext = new DOMValidateContext(selector, doc);
        valContext.setProperty("javax.xml.crypto.dsig.cacheReference", Boolean.TRUE);
        valContext.setProperty("org.jcp.xml.dsig.validateManifests", Boolean.TRUE);

        if (getConfiguration().getSecureValidation() == Boolean.TRUE) {
            valContext.setProperty("org.apache.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        }
        setUriDereferencerAndBaseUri(valContext);

        setCryptoContextProperties(valContext);

        NodeList signatureNodes = getSignatureNodes(doc);

        List<XMLObject> collectedObjects = new ArrayList<>(3);
        List<Reference> collectedReferences = new ArrayList<>(3);
        int totalCount = signatureNodes.getLength();
        for (int i = 0; i < totalCount; i++) {

            Element signatureNode = (Element) signatureNodes.item(i);

            valContext.setNode(signatureNode);
            final XMLSignature signature = fac.unmarshalXMLSignature(valContext);

            if (getConfiguration().getXmlSignatureChecker() != null) {
                XmlSignatureChecker.Input checkerInput = new CheckerInputBuilder().message(out).messageBodyDocument(doc)
                        .keyInfo(signature.getKeyInfo()).currentCountOfSignatures(i + 1).currentSignatureElement(signatureNode)
                        .objects(signature.getObjects()).signatureValue(signature.getSignatureValue())
                        .signedInfo(signature.getSignedInfo()).totalCountOfSignatures(totalCount)
                        .xmlSchemaValidationExecuted(getSchemaResourceUri(out) != null).build();
                getConfiguration().getXmlSignatureChecker().checkBeforeCoreValidation(checkerInput);
            }

            boolean coreValidity;
            try {
                coreValidity = signature.validate(valContext);
            } catch (XMLSignatureException se) {
                throw getConfiguration().getValidationFailedHandler().onXMLSignatureException(se);
            }
            // Check core validation status
            boolean goon = coreValidity;
            if (!coreValidity) {
                goon = handleSignatureValidationFailed(valContext, signature);
            }
            if (goon) {
                LOG.debug("XML signature {} verified", i + 1);
            } else {
                throw new XmlSignatureInvalidException("XML signature validation failed");
            }
            collectedObjects.addAll(signature.getObjects());
            collectedReferences.addAll(signature.getSignedInfo().getReferences());
        }
        map2Message(collectedReferences, collectedObjects, out, doc);
    }

    private void map2Message(final List<Reference> refs, final List<XMLObject> objs, Message out, final Document messageBodyDocument)
        throws Exception {

        XmlSignature2Message.Input refsAndObjects = new XmlSignature2Message.Input() {

            @Override
            public List<Reference> getReferences() {
                return refs;
            }

            @Override
            public List<XMLObject> getObjects() {
                return objs;
            }

            @Override
            public Document getMessageBodyDocument() {
                return messageBodyDocument;
            }

            @Override
            public Boolean omitXmlDeclaration() {
                return getConfiguration().getOmitXmlDeclaration();
            }

            @Override
            public Object getOutputNodeSearch() {
                return getConfiguration().getOutputNodeSearch();
            }

            @Override
            public String getOutputNodeSearchType() {
                return getConfiguration().getOutputNodeSearchType();
            }

            @Override
            public Boolean getRemoveSignatureElements() {
                return getConfiguration().getRemoveSignatureElements();
            }
            
            @Override
            public String getOutputXmlEncoding() {
                return getConfiguration().getOutputXmlEncoding();
            }

        };
        getConfiguration().getXmlSignature2Message().mapToMessage(refsAndObjects, out);
    }

    private NodeList getSignatureNodes(Document doc) throws IOException, ParserConfigurationException, XmlSignatureFormatException {

        // Find Signature element
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nl.getLength() == 0) {
            throw new XmlSignatureFormatException(
                    "Message is not a correct XML signature document: 'Signature' element is missing. Check the sent message.");
        }

        LOG.debug("{} signature elements found", nl.getLength());
        return nl;
    }

    @SuppressWarnings("unchecked")
    protected boolean handleSignatureValidationFailed(DOMValidateContext valContext, XMLSignature signature) throws Exception {
        ValidationFailedHandler handler = getConfiguration().getValidationFailedHandler();
        LOG.debug("handleSignatureValidationFailed called");
        try {
            handler.start();

            // first check signature value, see
            // https://www.isecpartners.com/media/12012/XMLDSIG_Command_Injection.pdf
            SignatureValue sigValue = signature.getSignatureValue();
            boolean sv = sigValue.validate(valContext);
            if (!sv) {
                handler.signatureValueValidationFailed(sigValue);
            }

            // then the references!
            // check the validation status of each Reference
            for (Reference ref : (List<Reference>) signature.getSignedInfo().getReferences()) {
                boolean refValid = ref.validate(valContext);
                if (!refValid) {
                    handler.referenceValidationFailed(ref);
                }
            }

            // validate Manifests, if property set
            if (Boolean.TRUE.equals(valContext.getProperty("org.jcp.xml.dsig.validateManifests"))) {
                for (XMLObject xo : (List<XMLObject>) signature.getObjects()) {
                    List<XMLStructure> content = xo.getContent();
                    for (XMLStructure xs : content) {
                        if (xs instanceof Manifest) {
                            Manifest man = (Manifest) xs;
                            for (Reference ref : (List<Reference>) man.getReferences()) {
                                boolean refValid = ref.validate(valContext);
                                if (!refValid) {
                                    handler.manifestReferenceValidationFailed(ref);
                                }
                            }
                        }
                    }
                }
            }
            boolean goon = handler.ignoreCoreValidationFailure();
            LOG.debug("Ignore Core Validation failure: {}", goon);
            return goon;
        } finally {
            handler.end();
        }

    }

    protected Document parseInput(InputStream is, Message message) throws Exception {
        try {
            ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
            Schema schema = getSchema(message);
            DocumentBuilder db = XmlSignatureHelper.newDocumentBuilder(getConfiguration().getDisallowDoctypeDecl(), schema);
            db.setErrorHandler(errorHandler);
            Document doc = db.parse(is);
            errorHandler.handleErrors(message.getExchange(), schema, null); // throws ValidationException
            return doc;
        } catch (SAXException e) {
            throw new XmlSignatureFormatException("Message has wrong format, it is not a XML signature document. Check the sent message.",
                    e);
        }
    }

    static class CheckerInputBuilder {

        private boolean xmlSchemaValidationExecuted;

        private int totalCountOfSignatures;

        private SignedInfo signedInfo;

        private SignatureValue signatureValue;

        private List<? extends XMLObject> objects;

        private Document messageBodyDocument;

        private Message message;

        private KeyInfo keyInfo;

        private Element currentSignatureElement;

        private int currentCountOfSignatures;

        CheckerInputBuilder xmlSchemaValidationExecuted(boolean xmlSchemaValidationExecuted) {
            this.xmlSchemaValidationExecuted = xmlSchemaValidationExecuted;
            return this;
        }

        CheckerInputBuilder totalCountOfSignatures(int totalCountOfSignatures) {
            this.totalCountOfSignatures = totalCountOfSignatures;
            return this;
        }

        CheckerInputBuilder signedInfo(SignedInfo signedInfo) {
            this.signedInfo = signedInfo;
            return this;
        }

        CheckerInputBuilder signatureValue(SignatureValue signatureValue) {
            this.signatureValue = signatureValue;
            return this;
        }

        CheckerInputBuilder objects(List<? extends XMLObject> objects) {
            this.objects = objects;
            return this;
        }

        CheckerInputBuilder messageBodyDocument(Document messageBodyDocument) {
            this.messageBodyDocument = messageBodyDocument;
            return this;
        }

        CheckerInputBuilder message(Message message) {
            this.message = message;
            return this;
        }

        CheckerInputBuilder keyInfo(KeyInfo keyInfo) {
            this.keyInfo = keyInfo;
            return this;
        }

        CheckerInputBuilder currentSignatureElement(Element currentSignatureElement) {
            this.currentSignatureElement = currentSignatureElement;
            return this;
        }

        CheckerInputBuilder currentCountOfSignatures(int currentCountOfSignatures) {
            this.currentCountOfSignatures = currentCountOfSignatures;
            return this;
        }

        XmlSignatureChecker.Input build() {
            return new XmlSignatureChecker.Input() {

                @Override
                public boolean isXmlSchemaValidationExecuted() {
                    return xmlSchemaValidationExecuted;
                }

                @Override
                public int getTotalCountOfSignatures() {
                    return totalCountOfSignatures;
                }

                @Override
                public SignedInfo getSignedInfo() {
                    return signedInfo;
                }

                @Override
                public SignatureValue getSignatureValue() {
                    return signatureValue;
                }

                @Override
                public List<? extends XMLObject> getObjects() {
                    return objects;
                }

                @Override
                public Document getMessageBodyDocument() {
                    return messageBodyDocument;
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
                public Element getCurrentSignatureElement() {
                    return currentSignatureElement;
                }

                @Override
                public int getCurrentCountOfSignatures() {
                    return currentCountOfSignatures;
                }
            };
        }
    }
}
