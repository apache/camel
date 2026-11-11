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
package org.apache.camel.component.mtconnect;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * Copies one asset element of an MTConnectAssets document into a standalone XML document: the element as the agent
 * wrote it, with the namespace declarations of its ancestors declared on its root, so that vendor extensions and
 * prefixed attribute values keep their meaning. Comments and processing instructions inside the asset are dropped.
 */
final class AssetWriter {

    private final XMLOutputFactory factory = XMLOutputFactory.newFactory();

    /**
     * Copies the element the reader is positioned on, up to its end tag.
     *
     * @param  inherited the namespace declarations in scope from the ancestors, by prefix ("" for the default
     *                   namespace)
     * @return           the element as a standalone document
     */
    String copy(XMLStreamReader reader, Map<String, String> inherited) throws XMLStreamException {
        StringWriter out = new StringWriter();
        XMLStreamWriter writer = factory.createXMLStreamWriter(out);
        int depth = 0;
        while (true) {
            switch (reader.getEventType()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    startElement(reader, writer, depth == 0 ? inherited : Map.of());
                    depth++;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    writer.writeEndElement();
                    depth--;
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE -> writer.writeCharacters(reader.getText());
                case XMLStreamConstants.CDATA -> writer.writeCData(reader.getText());
                default -> {
                    // comments and processing instructions carry no asset data
                }
            }
            if (depth == 0) {
                break;
            }
            reader.next();
        }
        writer.close();
        return out.toString();
    }

    private static void startElement(XMLStreamReader reader, XMLStreamWriter writer, Map<String, String> inherited)
            throws XMLStreamException {
        writer.writeStartElement(nonNull(reader.getPrefix()), reader.getLocalName(), nonNull(reader.getNamespaceURI()));
        Map<String, String> declared = new LinkedHashMap<>();
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            declared.put(nonNull(reader.getNamespacePrefix(i)), nonNull(reader.getNamespaceURI(i)));
        }
        inherited.forEach(declared::putIfAbsent);
        for (Map.Entry<String, String> namespace : declared.entrySet()) {
            if (namespace.getKey().isEmpty()) {
                writer.writeDefaultNamespace(namespace.getValue());
            } else {
                writer.writeNamespace(namespace.getKey(), namespace.getValue());
            }
        }
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String prefix = nonNull(reader.getAttributePrefix(i));
            if (prefix.isEmpty()) {
                writer.writeAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            } else {
                writer.writeAttribute(prefix, reader.getAttributeNamespace(i), reader.getAttributeLocalName(i),
                        reader.getAttributeValue(i));
            }
        }
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
