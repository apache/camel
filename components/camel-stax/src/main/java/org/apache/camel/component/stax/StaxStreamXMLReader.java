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
package org.apache.camel.component.stax;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.camel.util.ObjectHelper;

/**
 * class extracted from spring 3.0.6.RELEASE.
 */
public class StaxStreamXMLReader implements XMLReader {

    private static final String DEFAULT_XML_VERSION = "1.0";

    private static final String NAMESPACES_FEATURE_NAME = "http://xml.org/sax/features/namespaces";

    private static final String NAMESPACE_PREFIXES_FEATURE_NAME = "http://xml.org/sax/features/namespace-prefixes";

    private static final String IS_STANDALONE_FEATURE_NAME = "http://xml.org/sax/features/is-standalone";

    private DTDHandler dtdHandler;

    private ContentHandler contentHandler;

    private EntityResolver entityResolver;

    private ErrorHandler errorHandler;

    private LexicalHandler lexicalHandler;

    private boolean namespacesFeature = true;

    private boolean namespacePrefixesFeature = false;

    private Boolean isStandalone;

    private final Map<String, String> namespaces = new LinkedHashMap<>();

    private final XMLStreamReader reader;

    private String xmlVersion = DEFAULT_XML_VERSION;

    private String encoding;

    /**
     * Constructs a new instance of the <code>StaxStreamXmlReader</code> that reads from the given
     * <code>XMLStreamReader</code>. The supplied stream reader must be in
     * <code>XMLStreamConstants.START_DOCUMENT</code> or <code>XMLStreamConstants.START_ELEMENT</code> state.
     *
     * @param  reader                the <code>XMLEventReader</code> to read from
     * @throws IllegalStateException if the reader is not at the start of a document or element
     */
    StaxStreamXMLReader(XMLStreamReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("'reader' must not be null");
        }
        int event = reader.getEventType();
        if (!(event == XMLStreamConstants.START_DOCUMENT || event == XMLStreamConstants.START_ELEMENT)) {
            throw new IllegalStateException("XMLEventReader not at start of document or element");
        }
        this.reader = reader;
    }

    protected void parseInternal() throws SAXException, XMLStreamException {
        boolean documentStarted = false;
        boolean documentEnded = false;
        int elementDepth = 0;
        int eventType = reader.getEventType();
        while (true) {
            if (eventType != XMLStreamConstants.START_DOCUMENT && eventType != XMLStreamConstants.END_DOCUMENT &&
                    !documentStarted) {
                handleStartDocument();
                documentStarted = true;
            }
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    elementDepth++;
                    handleStartElement();
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    elementDepth--;
                    if (elementDepth >= 0) {
                        handleEndElement();
                    }
                    break;
                case XMLStreamConstants.PROCESSING_INSTRUCTION:
                    handleProcessingInstruction();
                    break;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.SPACE:
                case XMLStreamConstants.CDATA:
                    handleCharacters();
                    break;
                case XMLStreamConstants.START_DOCUMENT:
                    handleStartDocument();
                    documentStarted = true;
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    handleEndDocument();
                    documentEnded = true;
                    break;
                case XMLStreamConstants.COMMENT:
                    handleComment();
                    break;
                case XMLStreamConstants.DTD:
                    handleDtd();
                    break;
                case XMLStreamConstants.ENTITY_REFERENCE:
                    handleEntityReference();
                    break;
                default:
                    throw new IllegalStateException("unexpected eventType " + eventType);
            }
            if (reader.hasNext() && elementDepth >= 0) {
                eventType = reader.next();
            } else {
                break;
            }
        }
        if (!documentEnded) {
            handleEndDocument();
        }
    }

    private void handleStartDocument() throws SAXException {
        if (XMLStreamConstants.START_DOCUMENT == reader.getEventType()) {
            String xmlVersion = reader.getVersion();
            if (ObjectHelper.isNotEmpty(xmlVersion)) {
                this.xmlVersion = xmlVersion;
            }
            this.encoding = reader.getCharacterEncodingScheme();
        }

        if (getContentHandler() != null) {
            final Location location = reader.getLocation();

            getContentHandler().setDocumentLocator(new Locator2() {

                public int getColumnNumber() {
                    return location != null ? location.getColumnNumber() : -1;
                }

                public int getLineNumber() {
                    return location != null ? location.getLineNumber() : -1;
                }

                public String getPublicId() {
                    return location != null ? location.getPublicId() : null;
                }

                public String getSystemId() {
                    return location != null ? location.getSystemId() : null;
                }

                public String getXMLVersion() {
                    return xmlVersion;
                }

                public String getEncoding() {
                    return encoding;
                }
            });
            getContentHandler().startDocument();
            if (reader.standaloneSet()) {
                setStandalone(reader.isStandalone());
            }
        }
    }

    private void handleStartElement() throws SAXException {
        if (getContentHandler() != null) {
            QName qName = reader.getName();
            if (hasNamespacesFeature()) {
                for (int i = 0; i < reader.getNamespaceCount(); i++) {
                    startPrefixMapping(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
                }
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    String prefix = reader.getAttributePrefix(i);
                    String namespace = reader.getAttributeNamespace(i);
                    if (ObjectHelper.isNotEmpty(namespace)) {
                        startPrefixMapping(prefix, namespace);
                    }
                }
                getContentHandler().startElement(qName.getNamespaceURI(), qName.getLocalPart(), toQualifiedName(qName),
                        getAttributes());
            } else {
                getContentHandler().startElement("", "", toQualifiedName(qName), getAttributes());
            }
        }
    }

    private void handleEndElement() throws SAXException {
        if (getContentHandler() != null) {
            QName qName = reader.getName();
            if (hasNamespacesFeature()) {
                getContentHandler().endElement(qName.getNamespaceURI(), qName.getLocalPart(), toQualifiedName(qName));
                for (int i = 0; i < reader.getNamespaceCount(); i++) {
                    String prefix = reader.getNamespacePrefix(i);
                    if (prefix == null) {
                        prefix = "";
                    }
                    endPrefixMapping(prefix);
                }
            } else {
                getContentHandler().endElement("", "", toQualifiedName(qName));
            }
        }
    }

    private void handleCharacters() throws SAXException {
        if (getContentHandler() != null && reader.isWhiteSpace()) {
            getContentHandler()
                    .ignorableWhitespace(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
            return;
        }
        if (XMLStreamConstants.CDATA == reader.getEventType() && getLexicalHandler() != null) {
            getLexicalHandler().startCDATA();
        }
        if (getContentHandler() != null) {
            getContentHandler().characters(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
        }
        if (XMLStreamConstants.CDATA == reader.getEventType() && getLexicalHandler() != null) {
            getLexicalHandler().endCDATA();
        }
    }

    private void handleComment() throws SAXException {
        if (getLexicalHandler() != null) {
            getLexicalHandler().comment(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
        }
    }

    private void handleDtd() throws SAXException {
        if (getLexicalHandler() != null) {
            javax.xml.stream.Location location = reader.getLocation();
            getLexicalHandler().startDTD(null, location.getPublicId(), location.getSystemId());
        }
        if (getLexicalHandler() != null) {
            getLexicalHandler().endDTD();
        }
    }

    private void handleEntityReference() throws SAXException {
        if (getLexicalHandler() != null) {
            getLexicalHandler().startEntity(reader.getLocalName());
        }
        if (getLexicalHandler() != null) {
            getLexicalHandler().endEntity(reader.getLocalName());
        }
    }

    private void handleEndDocument() throws SAXException {
        if (getContentHandler() != null) {
            getContentHandler().endDocument();
        }
    }

    private void handleProcessingInstruction() throws SAXException {
        if (getContentHandler() != null) {
            getContentHandler().processingInstruction(reader.getPITarget(), reader.getPIData());
        }
    }

    private Attributes getAttributes() {
        AttributesImpl attributes = new AttributesImpl();

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            if (namespace == null || !hasNamespacesFeature()) {
                namespace = "";
            }
            String type = reader.getAttributeType(i);
            if (type == null) {
                type = "CDATA";
            }
            attributes.addAttribute(namespace, reader.getAttributeLocalName(i),
                    toQualifiedName(reader.getAttributeName(i)), type, reader.getAttributeValue(i));
        }
        if (hasNamespacePrefixesFeature()) {
            for (int i = 0; i < reader.getNamespaceCount(); i++) {
                String prefix = reader.getNamespacePrefix(i);
                String namespaceUri = reader.getNamespaceURI(i);
                String qName;
                if (ObjectHelper.isNotEmpty(prefix)) {
                    qName = "xmlns:" + prefix;
                } else {
                    qName = "xmlns";
                }
                attributes.addAttribute("", "", qName, "CDATA", namespaceUri);
            }
        }

        return attributes;
    }

    // AbstractStaxXmlReader
    @Override
    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (NAMESPACES_FEATURE_NAME.equals(name)) {
            return this.namespacesFeature;
        } else if (NAMESPACE_PREFIXES_FEATURE_NAME.equals(name)) {
            return this.namespacePrefixesFeature;
        } else if (IS_STANDALONE_FEATURE_NAME.equals(name)) {
            if (this.isStandalone != null) {
                return this.isStandalone;
            } else {
                throw new SAXNotSupportedException("startDocument() callback not completed yet");
            }
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }

    @Override
    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (NAMESPACES_FEATURE_NAME.equals(name)) {
            this.namespacesFeature = value;
        } else if (NAMESPACE_PREFIXES_FEATURE_NAME.equals(name)) {
            this.namespacePrefixesFeature = value;
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }

    protected void setStandalone(boolean standalone) {
        this.isStandalone = standalone;
    }

    /**
     * Indicates whether the SAX feature <code>http://xml.org/sax/features/namespaces</code> is turned on.
     */
    protected boolean hasNamespacesFeature() {
        return this.namespacesFeature;
    }

    /**
     * Indicates whether the SAX feature <code>http://xml.org/sax/features/namespaces-prefixes</code> is turned on.
     */
    protected boolean hasNamespacePrefixesFeature() {
        return this.namespacePrefixesFeature;
    }

    /**
     * Convert a <code>QName</code> to a qualified name, as used by DOM and SAX. The returned string has a format of
     * <code>prefix:localName</code> if the prefix is set, or just <code>localName</code> if not.
     *
     * @param  qName the <code>QName</code>
     * @return       the qualified name
     */
    protected String toQualifiedName(QName qName) {
        String prefix = qName.getPrefix();
        if (!ObjectHelper.isNotEmpty(prefix)) {
            return qName.getLocalPart();
        } else {
            return prefix + ":" + qName.getLocalPart();
        }
    }

    /**
     * Parse the StAX XML reader passed at construction-time.
     * <p>
     * <b>NOTE:</b>: The given <code>InputSource</code> is not read, but ignored.
     *
     * @param  ignored      is ignored
     * @throws SAXException a SAX exception, possibly wrapping a <code>XMLStreamException</code>
     */
    @Override
    public final void parse(InputSource ignored) throws SAXException {
        parse();
    }

    /**
     * Parse the StAX XML reader passed at construction-time.
     * <p>
     * <b>NOTE:</b>: The given system identifier is not read, but ignored.
     *
     * @param  ignored      is ignored
     * @throws SAXException A SAX exception, possibly wrapping a <code>XMLStreamException</code>
     */
    @Override
    public final void parse(String ignored) throws SAXException {
        parse();
    }

    private void parse() throws SAXException {
        try {
            parseInternal();
        } catch (XMLStreamException ex) {
            Locator locator = null;
            if (ex.getLocation() != null) {
                locator = new StaxLocator(ex.getLocation());
            }
            SAXParseException saxException = new SAXParseException(ex.getMessage(), locator, ex);
            if (getErrorHandler() != null) {
                getErrorHandler().fatalError(saxException);
            } else {
                throw saxException;
            }
        }
    }

    /**
     * Starts the prefix mapping for the given prefix.
     *
     * @see org.xml.sax.ContentHandler#startPrefixMapping(String, String)
     */
    protected void startPrefixMapping(String prefix, String namespace) throws SAXException {
        if (getContentHandler() != null) {
            if (prefix == null) {
                prefix = "";
            }
            if (!ObjectHelper.isNotEmpty(namespace)) {
                return;
            }
            if (!namespace.equals(namespaces.get(prefix))) {
                getContentHandler().startPrefixMapping(prefix, namespace);
                namespaces.put(prefix, namespace);
            }
        }
    }

    /**
     * Ends the prefix mapping for the given prefix.
     *
     * @see org.xml.sax.ContentHandler#endPrefixMapping(String)
     */
    protected void endPrefixMapping(String prefix) throws SAXException {
        if (getContentHandler() != null) {
            if (namespaces.containsKey(prefix)) {
                getContentHandler().endPrefixMapping(prefix);
                namespaces.remove(prefix);
            }
        }
    }

    /**
     * Implementation of the <code>Locator</code> interface that is based on a StAX <code>Location</code>.
     *
     * @see Locator
     * @see Location
     */
    private static class StaxLocator implements Locator {

        private Location location;

        protected StaxLocator(Location location) {
            this.location = location;
        }

        @Override
        public String getPublicId() {
            return location.getPublicId();
        }

        @Override
        public String getSystemId() {
            return location.getSystemId();
        }

        @Override
        public int getLineNumber() {
            return location.getLineNumber();
        }

        @Override
        public int getColumnNumber() {
            return location.getColumnNumber();
        }
    }

    // AbstractXMLReader

    @Override
    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    @Override
    public void setContentHandler(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    @Override
    public void setDTDHandler(DTDHandler dtdHandler) {
        this.dtdHandler = dtdHandler;
    }

    @Override
    public DTDHandler getDTDHandler() {
        return dtdHandler;
    }

    @Override
    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    @Override
    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @Override
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    protected LexicalHandler getLexicalHandler() {
        return lexicalHandler;
    }

    /**
     * Throws a <code>SAXNotRecognizedException</code> exception when the given property does not signify a lexical
     * handler. The property name for a lexical handler is <code>http://xml.org/sax/properties/lexical-handler</code>.
     */
    @Override
    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            return lexicalHandler;
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }

    /**
     * Throws a <code>SAXNotRecognizedException</code> exception when the given property does not signify a lexical
     * handler. The property name for a lexical handler is <code>http://xml.org/sax/properties/lexical-handler</code>.
     */
    @Override
    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            lexicalHandler = (LexicalHandler) value;
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }
}
