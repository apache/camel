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

package org.apache.camel.xml.io.util;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * <p>
 * A utility class to determine as quickly as possible (without reading entire stream) important information about an
 * XML document. Most importantly we can collect:
 * <ul>
 * <li>name and namespace of root element</li>
 * <li>root element attributes and values</li>
 * <li>prefix:namespace mapping declared at root element</li>
 * <li>modeline declarations before root element</li>
 * </ul>
 * </p>
 *
 * <p>
 * While we can have any kind of XML document and the namespaced content may be available at various places in the
 * document, most <em>sane</em> documents can be examined simply by looking at the root element. This can help e.g.,
 * with <code>jbang run camel@camel run</code> to quickly detect what kind of XML document we're trying to <em>run</em>.
 * This can speed later, full parsing, because we know upfront what's in the doc.
 * </p>
 */
public class XmlStreamDetector {

    private final XMLStreamReader reader;
    private final XmlStreamInfo information = new XmlStreamInfo();

    /**
     * Creates a detector for XML stream. The {@link InputStream stream} should be managed (like try-resources)
     * externally.
     *
     * @param  xmlStream   XML to collect information from
     * @throws IOException thrown if there is a problem reading the file.
     */
    public XmlStreamDetector(final InputStream xmlStream) throws IOException {
        if (xmlStream == null) {
            reader = null;
            information.problem = new IllegalArgumentException("XML Stream is null");
            return;
        }
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            reader = factory.createXMLStreamReader(xmlStream);
        } catch (XMLStreamException e) {
            information.problem = e;
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Performs the analysis of the XML Stream and returns relevant {@link XmlStreamInfo XML stream information}.
     *
     * @return
     * @throws IOException
     */
    public XmlStreamInfo information() throws IOException {
        if (information.problem != null) {
            return information;
        }

        if (XMLStreamConstants.START_DOCUMENT != reader.getEventType()) {
            information.problem = new IllegalStateException("Expected START_DOCUMENT");
            return information;
        }

        boolean skipComments = false;
        try {
            while (reader.hasNext()) {
                int ev = reader.next();
                switch (ev) {
                    case XMLStreamConstants.COMMENT:
                        if (!skipComments) {
                            // search for modelines
                            String comment = reader.getText();
                            if (comment != null) {
                                comment.lines().map(String::trim).forEach(l -> {
                                    if (l.startsWith("camel-k:")) {
                                        information.modelines.add(l);
                                    }
                                });
                            }
                        }
                        break;
                    case XMLStreamConstants.START_ELEMENT:
                        if (information.rootElementName != null) {
                            // only root element is checked. No need to parse more
                            return information;
                        }
                        skipComments = true;
                        information.rootElementName = reader.getLocalName();
                        information.rootElementNamespace = reader.getNamespaceURI();

                        for (int ns = 0; ns < reader.getNamespaceCount(); ns++) {
                            String prefix = reader.getNamespacePrefix(ns);
                            information.namespaceMapping.put(prefix == null ? "" : prefix, reader.getNamespaceURI(ns));
                        }
                        for (int at = 0; at < reader.getAttributeCount(); at++) {
                            QName qn = reader.getAttributeName(at);
                            String prefix = qn.getPrefix() == null ? "" : qn.getPrefix().trim();
                            String nsURI = qn.getNamespaceURI() == null ? "" : qn.getNamespaceURI().trim();
                            String value = reader.getAttributeValue(at);
                            String localPart = qn.getLocalPart();
                            if (nsURI.isEmpty() || prefix.isEmpty()) {
                                // according to XML spec, this attribut is not namespaced, not in default namespace
                                // https://www.w3.org/TR/xml-names/#defaulting
                                // > The namespace name for an unprefixed attribute name always has no value.
                                information.attributes.put(localPart, value);
                            } else {
                                information.attributes.put("{" + nsURI + "}" + localPart, value);
                                information.attributes.put(prefix + ":" + localPart, value);
                            }
                        }
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                    case XMLStreamConstants.END_DOCUMENT:
                        if (information.rootElementName == null) {
                            information.problem = new IllegalArgumentException("XML Stream is empty");
                            return information;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (XMLStreamException e) {
            information.problem = e;
            return information;
        }

        return information;
    }

}
