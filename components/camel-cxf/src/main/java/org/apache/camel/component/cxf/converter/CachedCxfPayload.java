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
package org.apache.camel.component.cxf.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.converter.stream.StreamSourceCache;
import org.apache.cxf.staxutils.StaxSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CachedCxfPayload<T> extends CxfPayload<T> implements StreamCache {
    private static final Logger LOG = LoggerFactory.getLogger(CachedCxfPayload.class);
    private final XmlConverter xml;

    public CachedCxfPayload(CxfPayload<T> orig, Exchange exchange, XmlConverter xml) {
        super(orig.getHeaders(), new ArrayList<Source>(orig.getBodySources()), orig.getNsMap());
        ListIterator<Source> li = getBodySources().listIterator();
        this.xml = xml;
        while (li.hasNext()) {
            Source source = li.next();
            XMLStreamReader reader = null;
            // namespace definitions that are on the SOAP envelope can get lost, if this is 
            // not a DOM (there is special coding on the CXFPayload.getBody().get() method for 
            // this, that only works on DOM nodes.
            // We have to do some delegation on the XMLStreamReader for StAXSource and StaxSource
            // that re-injects the missing namespaces into the XMLStreamReader.
            // Replace all other Sources that are not DOMSources with DOMSources.
            if (source instanceof StaxSource) {
                reader = ((StaxSource) source).getXMLStreamReader();
            } else if (source instanceof StAXSource) {
                reader = ((StAXSource) source).getXMLStreamReader();
            }
            if (reader != null) {
                Map<String, String> nsmap = getNsMap();
                if (nsmap == null) {
                    nsmap = Collections.emptyMap();
                }
                source = new StAXSource(new DelegatingXMLStreamReader(reader, nsmap));
                StreamSource streamSource = exchange.getContext().getTypeConverter().convertTo(StreamSource.class, exchange, source);
                if (streamSource != null) {
                    try {
                        li.set(new StreamSourceCache(streamSource, exchange));
                    } catch (IOException e) {
                        LOG.error("Cannot Create StreamSourceCache ", e);
                    }
                }
            } else if (!(source instanceof DOMSource)) {
                Document document = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, source);
                if (document != null) {
                    li.set(new DOMSource(document));
                }
            }
        }
    }

    private CachedCxfPayload(CachedCxfPayload<T> orig) throws IOException {
        super(orig.getHeaders(), new ArrayList<Source>(orig.getBodySources()), orig.getNsMap());
        ListIterator<Source> li = getBodySources().listIterator();
        this.xml = orig.xml;
        while (li.hasNext()) {
            Source source = li.next();
            if (source instanceof StreamCache) {
                li.set((Source) (((StreamCache) source)).copy());
            }
        }
    }

    @Override
    public void reset() {
        for (Source source : getBodySources()) {
            if (source instanceof StreamCache) {
                ((StreamCache) source).reset();
            }
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        // no body no write
        if (getBodySources().size() == 0) {
            return;
        }
        Source body = getBodySources().get(0);
        if (body instanceof StreamCache) {
            ((StreamCache) body).writeTo(os);
        } else {
            StreamResult sr = new StreamResult(os);
            try {
                xml.toResult(body, sr);
            } catch (TransformerException e) {
                throw new IOException("Transformation failed", e);
            }
        }
    }

    @Override
    public boolean inMemory() {
        boolean inMemory = true;
        for (Source source : getBodySources()) {
            if (source instanceof StreamCache && !((StreamCache) source).inMemory()) {
                inMemory = false;
            }
        }
        return inMemory;
    }

    @Override
    public long length() {
        return 0;
    }

    @Override
    public StreamCache copy() throws IOException {
        return new CachedCxfPayload<T>(this);
    }

    private static class DelegatingXMLStreamReader implements XMLStreamReader {
        private XMLStreamReader reader;
        private final Map<String, String> nsmap;
        private final String[] prefixes;

        public DelegatingXMLStreamReader(XMLStreamReader reader, Map<String, String> nsmap) {
            this.reader = reader;
            this.nsmap = nsmap;
            this.prefixes = nsmap.keySet().toArray(new String[0]);
        }

        @Override
        public Object getProperty(String name) throws IllegalArgumentException {
            return reader.getProperty(name);
        }

        @Override
        public int next() throws XMLStreamException {
            return reader.next();
        }

        @Override
        public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
            reader.require(type, namespaceURI, localName);
        }

        @Override
        public String getElementText() throws XMLStreamException {
            return reader.getElementText();
        }

        @Override
        public int nextTag() throws XMLStreamException {
            return reader.nextTag();
        }

        @Override
        public boolean hasNext() throws XMLStreamException {
            return reader.hasNext();
        }

        @Override
        public void close() throws XMLStreamException {
            reader.close();
        }

        @Override
        public String getNamespaceURI(String prefix) {
            String nsuri = reader.getNamespaceURI();
            if (nsuri == null) {
                nsuri = nsmap.get(prefix);
            }
            return nsuri;
        }

        @Override
        public boolean isStartElement() {
            return reader.isStartElement();
        }

        @Override
        public boolean isEndElement() {
            return reader.isEndElement();
        }

        @Override
        public boolean isCharacters() {
            return reader.isCharacters();
        }

        public boolean isWhiteSpace() {
            return reader.isWhiteSpace();
        }

        public String getAttributeValue(String namespaceURI, String localName) {
            return reader.getAttributeValue(namespaceURI, localName);
        }

        public int getAttributeCount() {
            return reader.getAttributeCount();
        }

        public QName getAttributeName(int index) {
            return reader.getAttributeName(index);
        }

        public String getAttributeNamespace(int index) {
            return reader.getAttributeNamespace(index);
        }

        public String getAttributeLocalName(int index) {
            return reader.getAttributeLocalName(index);
        }

        public String getAttributePrefix(int index) {
            return reader.getAttributePrefix(index);
        }

        public String getAttributeType(int index) {
            return reader.getAttributeType(index);
        }

        public String getAttributeValue(int index) {
            return reader.getAttributeValue(index);
        }

        public boolean isAttributeSpecified(int index) {
            return reader.isAttributeSpecified(index);
        }

        public int getNamespaceCount() {
            return prefixes.length + reader.getNamespaceCount();
        }

        public String getNamespacePrefix(int index) {
            if (index < prefixes.length) {
                return prefixes[index];
            } else {
                return reader.getNamespacePrefix(index - prefixes.length);
            }
        }

        public String getNamespaceURI(int index) {
            if (index < prefixes.length) {
                return nsmap.get(prefixes[index]);
            } else {
                return reader.getNamespaceURI(index - prefixes.length);
            }
        }

        public NamespaceContext getNamespaceContext() {
            return reader.getNamespaceContext();
        }

        public int getEventType() {
            return reader.getEventType();
        }

        public String getText() {
            return reader.getText();
        }

        public char[] getTextCharacters() {
            return reader.getTextCharacters();
        }

        public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) throws XMLStreamException {
            return reader.getTextCharacters(sourceStart, target, targetStart, length);
        }

        public int getTextStart() {
            return reader.getTextStart();
        }

        public int getTextLength() {
            return reader.getTextLength();
        }

        public String getEncoding() {
            return reader.getEncoding();
        }

        public boolean hasText() {
            return reader.hasText();
        }

        public Location getLocation() {
            return reader.getLocation();
        }

        public QName getName() {
            return reader.getName();
        }

        public String getLocalName() {
            return reader.getLocalName();
        }

        public boolean hasName() {
            return reader.hasName();
        }

        public String getNamespaceURI() {
            return reader.getNamespaceURI();
        }

        public String getPrefix() {
            return reader.getPrefix();
        }

        public String getVersion() {
            return reader.getVersion();
        }

        public boolean isStandalone() {
            return reader.isStandalone();
        }

        public boolean standaloneSet() {
            return reader.standaloneSet();
        }

        public String getCharacterEncodingScheme() {
            return reader.getCharacterEncodingScheme();
        }

        public String getPITarget() {
            return reader.getPITarget();
        }

        public String getPIData() {
            return reader.getPIData();
        }

    }
}
