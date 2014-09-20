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
package org.apache.camel.converter.jaxp;

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
import org.xml.sax.helpers.AttributesImpl;

/**
 * A streaming {@link javax.xml.transform.sax.SAXSource}
 */
public class StaxSource extends SAXSource implements XMLReader {

    private XMLStreamReader streamReader;

    private ContentHandler contentHandler;

    private LexicalHandler lexicalHandler;

    public StaxSource(XMLStreamReader streamReader) {
        this.streamReader = streamReader;
        setInputSource(new InputSource());
    }

    public XMLReader getXMLReader() {
        return this;
    }

    public XMLStreamReader getXMLStreamReader() {
        return streamReader;
    }

    protected void parse() throws SAXException {
        try {
            while (true) {
                switch (streamReader.getEventType()) {
                // Attributes are handled in START_ELEMENT
                case XMLStreamConstants.ATTRIBUTE:
                    break;
                case XMLStreamConstants.CDATA:
                {
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
                case XMLStreamConstants.CHARACTERS:
                {
                    int length = streamReader.getTextLength();
                    int start = streamReader.getTextStart();
                    char[] chars = streamReader.getTextCharacters();
                    contentHandler.characters(chars, start, length);
                    break;
                }
                case XMLStreamConstants.SPACE:
                {
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
                    String uri = streamReader.getNamespaceURI();
                    String localName = streamReader.getLocalName();
                    String prefix = streamReader.getPrefix();
                    String qname = prefix != null && prefix.length() > 0
                        ? prefix + ":" + localName : localName;
                    contentHandler.endElement(uri, localName, qname);
                    // namespaces
                    for (int i = 0; i < streamReader.getNamespaceCount(); i++) {
                        String nsPrefix = streamReader.getNamespacePrefix(i);
                        String nsUri = streamReader.getNamespaceURI(i);
                        if (nsUri == null) {
                            nsUri = "";
                        }
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
                    String uri = streamReader.getNamespaceURI();
                    String localName = streamReader.getLocalName();
                    String prefix = streamReader.getPrefix();
                    String qname = prefix != null && prefix.length() > 0
                        ? prefix + ":" + localName : localName;
                    // namespaces
                    for (int i = 0; i < streamReader.getNamespaceCount(); i++) {
                        String nsPrefix = streamReader.getNamespacePrefix(i);
                        String nsUri = streamReader.getNamespaceURI(i);
                        if (nsUri == null) {
                            nsUri = "";
                        }
                        contentHandler.startPrefixMapping(nsPrefix, nsUri);
                    }
                    contentHandler.startElement(uri == null ? "" : uri, localName, qname, getAttributes());
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

    protected String getQualifiedName() {
        String prefix = streamReader.getPrefix();
        if (prefix != null && prefix.length() > 0) {
            return prefix + ":" + streamReader.getLocalName();
        } else {
            return streamReader.getLocalName();
        }
    }

    protected Attributes getAttributes() {
        AttributesImpl attrs = new AttributesImpl();

        for (int i = 0; i < streamReader.getAttributeCount(); i++) {
            String uri = streamReader.getAttributeNamespace(i);
            String localName = streamReader.getAttributeLocalName(i);
            String prefix = streamReader.getAttributePrefix(i);
            String qName;
            if (prefix != null && prefix.length() > 0) {
                qName = prefix + ':' + localName;
            } else {
                qName = localName;
            }
            String type = streamReader.getAttributeType(i);
            String value = streamReader.getAttributeValue(i);
            if (value == null) {
                value = "";
            }

            attrs.addAttribute(uri == null ? "" : uri, localName, qName, type, value);
        }
        return attrs;
    }

    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }

    public void setFeature(String name, boolean value)
        throws SAXNotRecognizedException, SAXNotSupportedException {
    }

    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return null;
    }

    public void setProperty(String name, Object value)
        throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            lexicalHandler = (LexicalHandler) value;
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }

    public void setEntityResolver(EntityResolver resolver) {
    }

    public EntityResolver getEntityResolver() {
        return null;
    }

    public void setDTDHandler(DTDHandler handler) {
    }

    public DTDHandler getDTDHandler() {
        return null;
    }

    public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
        if (handler instanceof LexicalHandler
            && lexicalHandler == null) {
            lexicalHandler = (LexicalHandler)handler;
        }
    }

    public ContentHandler getContentHandler() {
        return this.contentHandler;
    }

    public void setErrorHandler(ErrorHandler handler) {
    }

    public ErrorHandler getErrorHandler() {
        return null;
    }

    public void parse(InputSource input) throws SAXException {
        StaxSource.this.parse();
    }

    public void parse(String systemId) throws SAXException {
        StaxSource.this.parse();
    }

}
