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
package org.apache.camel.support.builder.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Adapter to turn a StAX {@link XMLStreamReader} into a {@link SAXSource}.
 */
public class StAX2SAXSource extends SAXSource implements XMLReader {

    private XMLStreamReader streamReader;

    private ContentHandler contentHandler;

    private LexicalHandler lexicalHandler;

    public StAX2SAXSource(XMLStreamReader streamReader) {
        this.streamReader = streamReader;
        setInputSource(new InputSource());
    }

    @Override
    public XMLReader getXMLReader() {
        return this;
    }

    public XMLStreamReader getXMLStreamReader() {
        return streamReader;
    }

    protected void parse() throws SAXException {
        final StAX2SAXAttributes attributes = new StAX2SAXAttributes();
        try {
            while (true) {
                switch (streamReader.getEventType()) {
                    // Attributes are handled in START_ELEMENT
                    case XMLStreamConstants.ATTRIBUTE:
                        break;
                    case XMLStreamConstants.CDATA: {
                        if (lexicalHandler != null) {
                            lexicalHandler.startCDATA();
                        }
                        int length = streamReader.getTextLength();
                        int start = streamReader.getTextStart();
                        char[] chars = streamReader.getTextCharacters();
                        contentHandler.characters(chars, start, length);
                        if (lexicalHandler != null) {
                            lexicalHandler.endCDATA();
                        }
                        break;
                    }
                    case XMLStreamConstants.CHARACTERS: {
                        int length = streamReader.getTextLength();
                        int start = streamReader.getTextStart();
                        char[] chars = streamReader.getTextCharacters();
                        contentHandler.characters(chars, start, length);
                        break;
                    }
                    case XMLStreamConstants.SPACE: {
                        int length = streamReader.getTextLength();
                        int start = streamReader.getTextStart();
                        char[] chars = streamReader.getTextCharacters();
                        contentHandler.ignorableWhitespace(chars, start, length);
                        break;
                    }
                    case XMLStreamConstants.COMMENT:
                        if (lexicalHandler != null) {
                            int length = streamReader.getTextLength();
                            int start = streamReader.getTextStart();
                            char[] chars = streamReader.getTextCharacters();
                            lexicalHandler.comment(chars, start, length);
                        }
                        break;
                    case XMLStreamConstants.DTD:
                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        contentHandler.endDocument();
                        return;
                    case XMLStreamConstants.END_ELEMENT: {
                        String uri = nullToEmpty(streamReader.getNamespaceURI());
                        String localName = streamReader.getLocalName();
                        String qname = getPrefixedName(streamReader.getPrefix(), localName);
                        contentHandler.endElement(uri, localName, qname);

                        // namespaces
                        for (int i = 0; i < streamReader.getNamespaceCount(); i++) {
                            String nsPrefix = streamReader.getNamespacePrefix(i);
                            contentHandler.endPrefixMapping(nsPrefix);
                        }
                        break;
                    }
                    case XMLStreamConstants.ENTITY_DECLARATION:
                    case XMLStreamConstants.ENTITY_REFERENCE:
                    case XMLStreamConstants.NAMESPACE:
                    case XMLStreamConstants.NOTATION_DECLARATION:
                        break;
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        break;
                    case XMLStreamConstants.START_DOCUMENT:
                        contentHandler.startDocument();
                        break;
                    case XMLStreamConstants.START_ELEMENT: {
                        // namespaces
                        for (int i = 0; i < streamReader.getNamespaceCount(); i++) {
                            String nsPrefix = nullToEmpty(streamReader.getNamespacePrefix(i));
                            String nsUri = nullToEmpty(streamReader.getNamespaceURI(i));
                            contentHandler.startPrefixMapping(nsPrefix, nsUri);
                        }

                        String uri = nullToEmpty(streamReader.getNamespaceURI());
                        String localName = streamReader.getLocalName();
                        String qname = getPrefixedName(streamReader.getPrefix(), localName);
                        attributes.init();
                        contentHandler.startElement(uri, localName, qname, attributes);
                        attributes.reset();
                        break;
                    }
                    default:
                        break;
                }
                if (!streamReader.hasNext()) {
                    return;
                }
                streamReader.next();
            }
        } catch (XMLStreamException e) {
            SAXParseException spe;
            if (e.getLocation() != null) {
                spe = new SAXParseException(e.getMessage(), null, null,
                        e.getLocation().getLineNumber(),
                        e.getLocation().getColumnNumber(), e);
            } else {
                spe = new SAXParseException(e.getMessage(), null, null, -1, -1, e);
            }
            spe.initCause(e);
            throw spe;
        }
    }

    private String getPrefixedName(String prefix, String localName) {
        if (prefix == null || prefix.length() == 0) {
            return localName;
        }
        return prefix + ":" + localName;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    class StAX2SAXAttributes implements Attributes {

        private int attributeCount;

        void init() {
            attributeCount = streamReader.getAttributeCount();
        }

        void reset() {
            attributeCount = 0;
        }

        @Override
        public int getLength() {
            return attributeCount;
        }

        private boolean checkIndex(int index) {
            return index >= 0 && index < attributeCount;
        }

        @Override
        public String getURI(int index) {
            if (!checkIndex(index)) {
                return null;
            }
            return nullToEmpty(streamReader.getAttributeNamespace(index));
        }

        @Override
        public String getLocalName(int index) {
            if (!checkIndex(index)) {
                return null;
            }
            return streamReader.getAttributeLocalName(index);
        }

        @Override
        public String getQName(int index) {
            if (!checkIndex(index)) {
                return null;
            }
            String localName = streamReader.getAttributeLocalName(index);
            String prefix = streamReader.getAttributePrefix(index);
            return getPrefixedName(prefix, localName);
        }

        @Override
        public String getType(int index) {
            if (!checkIndex(index)) {
                return null;
            }
            return streamReader.getAttributeType(index);
        }

        @Override
        public String getValue(int index) {
            if (!checkIndex(index)) {
                return null;
            }
            return nullToEmpty(streamReader.getAttributeValue(index));
        }

        @Override
        public int getIndex(String searchUri, String searchLocalName) {
            for (int i = 0; i < attributeCount; i++) {
                if (getURI(i).equals(searchUri) && getLocalName(i).equals(searchLocalName)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int getIndex(String searchQName) {
            for (int i = 0; i < attributeCount; i++) {
                if (getQName(i).equals(searchQName)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public String getType(String uri, String localName) {
            return getType(getIndex(uri, localName));
        }

        @Override
        public String getType(String qName) {
            return getType(getIndex(qName));
        }

        @Override
        public String getValue(String uri, String localName) {
            return getValue(getIndex(uri, localName));
        }

        @Override
        public String getValue(String qName) {
            return getValue(getIndex(qName));
        }
    }

    @Override
    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }

    @Override
    public void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
    }

    @Override
    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return null;
    }

    @Override
    public void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            lexicalHandler = (LexicalHandler) value;
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }

    @Override
    public void setEntityResolver(EntityResolver resolver) {
    }

    @Override
    public EntityResolver getEntityResolver() {
        return null;
    }

    @Override
    public void setDTDHandler(DTDHandler handler) {
    }

    @Override
    public DTDHandler getDTDHandler() {
        return null;
    }

    @Override
    public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
        if (handler instanceof LexicalHandler
                && lexicalHandler == null) {
            lexicalHandler = (LexicalHandler)handler;
        }
    }

    @Override
    public ContentHandler getContentHandler() {
        return this.contentHandler;
    }

    @Override
    public void setErrorHandler(ErrorHandler handler) {
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return null;
    }

    @Override
    public void parse(InputSource input) throws SAXException {
        parse();
    }

    @Override
    public void parse(String systemId) throws SAXException {
        parse();
    }

}
