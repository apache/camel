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
package org.apache.camel.component.cxf.util;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;

public final class CxfUtilsTestHelper {

    private CxfUtilsTestHelper() {
        // helper class
    }

    public static String elementToString(Element element) throws Exception {
        Map<String, String> namespaces = new HashMap<>();
        visitNodesForNameSpace(element, namespaces);
        W3CDOMStreamWriter writer = new W3CDOMStreamWriter();
        writeElement(element, writer, namespaces);
        XmlConverter converter = new XmlConverter();
        return converter.toString(converter.toDOMSource(writer.getDocument()), null);
    }

    private static void writeElement(Element e,
                                     XMLStreamWriter writer,
                                     Map<String, String> namespaces)
        throws XMLStreamException {
        String prefix = e.getPrefix();
        String ns = e.getNamespaceURI();
        String localName = e.getLocalName();

        if (prefix == null) {
            prefix = "";
        }
        if (localName == null) {
            localName = e.getNodeName();

            if (localName == null) {
                throw new IllegalStateException("Element's local name cannot be null!");
            }
        }

        String decUri = writer.getNamespaceContext().getNamespaceURI(prefix);
        boolean declareNamespace = decUri == null || !decUri.equals(ns);

        if (ns == null || ns.length() == 0) {
            writer.writeStartElement(localName);
            if (StringUtils.isEmpty(decUri)) {
                declareNamespace = false;
            }
        } else {
            writer.writeStartElement(prefix, localName, ns);
        }

        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);

            String name = attr.getLocalName();
            String attrPrefix = attr.getPrefix();
            if (attrPrefix == null) {
                attrPrefix = "";
            }
            if (name == null) {
                name = attr.getNodeName();
            }

            if ("xmlns".equals(attrPrefix)) {
                writer.writeNamespace(name, attr.getNodeValue());
                if (name.equals(prefix) && attr.getNodeValue().equals(ns)) {
                    declareNamespace = false;
                }
            } else {
                if ("xmlns".equals(name) && "".equals(attrPrefix)) {
                    writer.writeNamespace("", attr.getNodeValue());
                    if (attr.getNodeValue().equals(ns)) {
                        declareNamespace = false;
                    } else if (StringUtils.isEmpty(attr.getNodeValue())
                        && StringUtils.isEmpty(ns)) {
                        declareNamespace = false;
                    }
                } else {
                    String attns = attr.getNamespaceURI();
                    String value = attr.getNodeValue();
                    if (attns == null || attns.length() == 0) {
                        writer.writeAttribute(name, value);
                    } else if (attrPrefix == null || attrPrefix.length() == 0) {
                        writer.writeAttribute(attns, name, value);
                    } else {
                        writer.writeAttribute(attrPrefix, attns, name, value);
                    }
                }
            }
        }

        if (declareNamespace) {
            if (ns == null) {
                writer.writeNamespace(prefix, "");
            } else {
                writer.writeNamespace(prefix, ns);
            }
        }

        if (namespaces != null && namespaces.size() > 0) {
            for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                String namespaceURI = entry.getValue();
                writer.writeNamespace(entry.getKey(), namespaceURI);
            }
        }

        Node nd = e.getFirstChild();
        while (nd != null) {
            StaxUtils.writeNode(nd, writer, false);
            nd = nd.getNextSibling();
        }

        writer.writeEndElement();
    }

    private static void visitNodesForNameSpace(Node node, Map<String, String> namespaces) {
        if (node instanceof Element) {
            Element element = (Element)node;
            if (element.getPrefix() != null && element.getNamespaceURI() != null) {
                namespaces.put(element.getPrefix(), element.getNamespaceURI());
            }
            if (node.getChildNodes() != null) {
                NodeList nodelist = node.getChildNodes();
                for (int i = 0; i < nodelist.getLength(); i++) {
                    visitNodesForNameSpace(nodelist.item(i), namespaces);
                }
            }
        }
    }


    public static void closeCamelUnitOfWork(Message message) {
        Exchange cxfExchange = null;
        if ((cxfExchange = message.getExchange()) != null) {
            org.apache.camel.Exchange exchange = cxfExchange.get(org.apache.camel.Exchange.class);
            if (exchange != null) {
                UnitOfWorkHelper.doneUow(exchange.getUnitOfWork(), exchange);
            }
        }
    }

}
