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
package org.apache.camel.converter.jaxb;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * {@link XMLStreamWriter} wrapper that filters out non-XML characters, see
 * {@link NonXmlCharFilterer} for details. Filtering applies to
 * <ul>
 * <li>Characters</li>
 * <li>CData</li>
 * <li>Attributes</li>
 * <li>Comments</li>
 * </ul>
 * 
 * @see XMLStreamWriter
 */
public class FilteringXmlStreamWriter implements XMLStreamWriter {
    NonXmlCharFilterer nonXmlCharFilterer = new NonXmlCharFilterer();

    private XMLStreamWriter writer;

    /**
     * @param writer
     *            target writer to wrap.
     */
    public FilteringXmlStreamWriter(XMLStreamWriter writer) {
        this.writer = writer;
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value)
        throws XMLStreamException {
        String filteredValue = nonXmlCharFilterer.filter(value);
        writer.writeAttribute(prefix, namespaceURI, localName, filteredValue);
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeAttribute(String namespaceURI, String localName, String value)
        throws XMLStreamException {
        String filteredValue = nonXmlCharFilterer.filter(value);
        writer.writeAttribute(namespaceURI, localName, filteredValue);
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeAttribute(String localName, String value) throws XMLStreamException {
        String filteredValue = nonXmlCharFilterer.filter(value);
        writer.writeAttribute(localName, filteredValue);
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeCData(String data) throws XMLStreamException {
        String filteredData = nonXmlCharFilterer.filter(data);
        writer.writeCData(filteredData);
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        nonXmlCharFilterer.filter(text, start, len);
        writer.writeCharacters(text, start, len);
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeCharacters(String text) throws XMLStreamException {
        String filteredText = nonXmlCharFilterer.filter(text);
        writer.writeCharacters(filteredText);
    }

    /**
     * This method applies filtering before delegating call to {@link #writer}.
     */
    public void writeComment(String data) throws XMLStreamException {
        String filteredData = nonXmlCharFilterer.filter(data);
        writer.writeComment(filteredData);
    }

    public void close() throws XMLStreamException {
        writer.close();
    }

    public void flush() throws XMLStreamException {
        writer.flush();
    }

    public NamespaceContext getNamespaceContext() {
        return writer.getNamespaceContext();
    }

    public String getPrefix(String uri) throws XMLStreamException {
        return writer.getPrefix(uri);
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        return writer.getProperty(name);
    }

    public void setDefaultNamespace(String uri) throws XMLStreamException {
        writer.setDefaultNamespace(uri);
    }

    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
        writer.setNamespaceContext(context);
    }

    public void setPrefix(String prefix, String uri) throws XMLStreamException {
        writer.setPrefix(prefix, uri);
    }

    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        writer.writeDefaultNamespace(namespaceURI);
    }

    public void writeDTD(String dtd) throws XMLStreamException {
        writer.writeDTD(dtd);
    }

    public void writeEmptyElement(String prefix, String localName, String namespaceURI)
        throws XMLStreamException {
        writer.writeEmptyElement(prefix, localName, namespaceURI);
    }

    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        writer.writeEmptyElement(namespaceURI, localName);
    }

    public void writeEmptyElement(String localName) throws XMLStreamException {
        writer.writeEmptyElement(localName);
    }

    public void writeEndDocument() throws XMLStreamException {
        writer.writeEndDocument();
    }

    public void writeEndElement() throws XMLStreamException {
        writer.writeEndElement();
    }

    public void writeEntityRef(String name) throws XMLStreamException {
        writer.writeEntityRef(name);
    }

    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        writer.writeNamespace(prefix, namespaceURI);
    }

    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        writer.writeProcessingInstruction(target, data);
    }

    public void writeProcessingInstruction(String target) throws XMLStreamException {
        writer.writeProcessingInstruction(target);
    }

    public void writeStartDocument() throws XMLStreamException {
        writer.writeStartDocument();
    }

    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        writer.writeStartDocument(encoding, version);
    }

    public void writeStartDocument(String version) throws XMLStreamException {
        writer.writeStartDocument(version);
    }

    public void writeStartElement(String prefix, String localName, String namespaceURI)
        throws XMLStreamException {
        writer.writeStartElement(prefix, localName, namespaceURI);
    }

    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        writer.writeStartElement(namespaceURI, localName);
    }

    public void writeStartElement(String localName) throws XMLStreamException {
        writer.writeStartElement(localName);
    }

}
