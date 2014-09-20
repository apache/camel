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
package org.apache.camel.jaxb;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.camel.converter.jaxb.JaxbXmlStreamWriterWrapper;

public class TestXmlStreamWriter implements JaxbXmlStreamWriterWrapper {
    @Override
    public XMLStreamWriter wrapWriter(final XMLStreamWriter writer) {
        return new XMLStreamWriter() {
            @Override
            public void writeStartElement(String s) throws XMLStreamException {
                writer.writeStartElement(s + "-Foo");
            }

            @Override
            public void writeStartElement(String s, String s2) throws XMLStreamException {
                writer.writeStartElement(s, s2 + "-Foo");
            }

            @Override
            public void writeStartElement(String s, String s2, String s3) throws XMLStreamException {
                writer.writeStartElement(s, s2 + "-Foo", s3);
            }

            @Override
            public void writeEmptyElement(String s, String s2) throws XMLStreamException {
                writer.writeEmptyElement(s, s2);
            }

            @Override
            public void writeEmptyElement(String s, String s2, String s3) throws XMLStreamException {
                writer.writeEmptyElement(s, s2, s3);
            }

            @Override
            public void writeEmptyElement(String s) throws XMLStreamException {
                writer.writeEmptyElement(s);
            }

            @Override
            public void writeEndElement() throws XMLStreamException {
                writer.writeEndElement();
            }

            @Override
            public void writeEndDocument() throws XMLStreamException {
                writer.writeEndDocument();
            }

            @Override
            public void close() throws XMLStreamException {
                writer.close();
            }

            @Override
            public void flush() throws XMLStreamException {
                writer.flush();
            }

            @Override
            public void writeAttribute(String s, String s2) throws XMLStreamException {
                writer.writeAttribute(s, s2);
            }

            @Override
            public void writeAttribute(String s, String s2, String s3, String s4) throws XMLStreamException {
                writer.writeAttribute(s, s2, s3, s4);
            }

            @Override
            public void writeAttribute(String s, String s2, String s3) throws XMLStreamException {
                writer.writeAttribute(s, s2, s3);
            }

            @Override
            public void writeNamespace(String s, String s2) throws XMLStreamException {
                writer.writeNamespace(s, s2);
            }

            @Override
            public void writeDefaultNamespace(String s) throws XMLStreamException {
                writer.writeDefaultNamespace(s);
            }

            @Override
            public void writeComment(String s) throws XMLStreamException {
                writer.writeComment(s);
            }

            @Override
            public void writeProcessingInstruction(String s) throws XMLStreamException {
                writer.writeProcessingInstruction(s);
            }

            @Override
            public void writeProcessingInstruction(String s, String s2) throws XMLStreamException {
                writer.writeProcessingInstruction(s, s2);
            }

            @Override
            public void writeCData(String s) throws XMLStreamException {
                writer.writeCData(s);
            }

            @Override
            public void writeDTD(String s) throws XMLStreamException {
                writer.writeDTD(s);
            }

            @Override
            public void writeEntityRef(String s) throws XMLStreamException {
                writer.writeEntityRef(s);
            }

            @Override
            public void writeStartDocument() throws XMLStreamException {
                writer.writeStartDocument();
            }

            @Override
            public void writeStartDocument(String s) throws XMLStreamException {
                writer.writeStartDocument(s);
            }

            @Override
            public void writeStartDocument(String s, String s2) throws XMLStreamException {
                writer.writeStartDocument(s, s2);
            }

            @Override
            public void writeCharacters(String s) throws XMLStreamException {
                writer.writeCharacters(s);
            }

            @Override
            public void writeCharacters(char[] chars, int i, int i2) throws XMLStreamException {
                writer.writeCharacters(chars, i, i2);
            }

            @Override
            public String getPrefix(String s) throws XMLStreamException {
                return writer.getPrefix(s);
            }

            @Override
            public void setPrefix(String s, String s2) throws XMLStreamException {
                writer.setPrefix(s, s2);
            }

            @Override
            public void setDefaultNamespace(String s) throws XMLStreamException {
                writer.setDefaultNamespace(s);
            }

            @Override
            public void setNamespaceContext(NamespaceContext namespaceContext) throws XMLStreamException {
                writer.setNamespaceContext(namespaceContext);
            }

            @Override
            public NamespaceContext getNamespaceContext() {
                return writer.getNamespaceContext();
            }

            @Override
            public Object getProperty(String s) throws IllegalArgumentException {
                return writer.getProperty(s);
            }
        };  //To change body of implemented methods use File | Settings | File Templates.
    }
}
