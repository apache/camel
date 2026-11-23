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
package org.apache.camel.dataformat.xsdjson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.camel.dataformat.xsdjson.XsdModel.Attribute;
import org.apache.camel.dataformat.xsdjson.XsdModel.Element;
import org.apache.camel.dataformat.xsdjson.XsdModel.SimpleType;
import org.apache.camel.dataformat.xsdjson.XsdModel.Type;

/**
 * Reads XML with StAX into a JSON tree (a Map, a List, or a value) following the schema.
 */
final class XmlToJson {

    static final int MAX_DEPTH = 512;

    private final XsdModel model;
    private final boolean ignoreUnknown;
    private final String textName;
    private int deepest;

    XmlToJson(XsdModel model, boolean ignoreUnknown, String textName) {
        this.model = model;
        this.ignoreUnknown = ignoreUnknown;
        this.textName = textName;
    }

    /**
     * The error when the stack of the thread overflows before the limit of {@link #MAX_DEPTH} levels is reached.
     *
     * @param format the format of the message, JSON or XML
     * @param level  the deepest level reached, the root element being level 1
     */
    static IllegalArgumentException tooDeepForTheStack(String format, int level, StackOverflowError cause) {
        return new IllegalArgumentException(
                "The " + format + " is nested too deep for the thread stack, which overflowed after the conversion"
                                            + " reached level " + level + " (the data format allows " + MAX_DEPTH + " levels)",
                cause);
    }

    /**
     * The deepest level of element read, the root element being level 1.
     */
    int deepest() {
        return deepest;
    }

    /**
     * A StAX factory for message XML: no DTD, no external entities.
     */
    static XMLInputFactory newInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    /**
     * Reads the document.
     *
     * @param  reader   the reader, before the root element
     * @param  expected the root element to require, or null to take the global element that the document names
     * @return          the root element and its JSON value
     */
    Map.Entry<Element, Object> read(XMLStreamReader reader, Element expected) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD) {
                throw new IllegalArgumentException("The XML has a DOCTYPE, which is not allowed");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                QName name = reader.getName();
                Element root = model.getGlobalElement(name);
                if (expected != null && !expected.name.equals(name)) {
                    throw new IllegalArgumentException(
                            "The XML root element is " + name + " but the element " + expected.name + " is expected");
                }
                if (root == null) {
                    throw new IllegalArgumentException(
                            "The XML root element " + name + " is not a global element of the schema. The global elements are: "
                                                       + model.elementNames());
                }
                Object value = readElement(reader, root, "/" + name.getLocalPart(), 1);
                return Map.entry(root, value == null ? NULL : value);
            }
        }
        throw new IllegalArgumentException("The XML has no root element");
    }

    /**
     * Stands for a JSON null as the value of the root element (a Map entry cannot hold null).
     */
    static final Object NULL = new Object();

    private Object readElement(XMLStreamReader reader, Element element, String path, int depth) throws XMLStreamException {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("The XML is nested deeper than " + MAX_DEPTH + " elements at " + path);
        }
        if (depth > deepest) {
            deepest = depth;
        }
        if (element.unsupported != null) {
            throw unsupported(element.unsupported, path);
        }
        Type type = element.type;
        if (type.unsupported != null) {
            throw unsupported(type.unsupported, path);
        }
        if (type.conflict != null) {
            throw new IllegalArgumentException(type.conflict);
        }
        String xsiType = reader.getAttributeValue(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        if (xsiType != null && !isDeclaredType(xsiType, reader.getNamespaceContext(), type)) {
            throw unsupported("xsi:type=\"" + xsiType + "\" is not supported (only the declared type of the element)", path);
        }
        String nil = reader.getAttributeValue(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "nil");
        if ("true".equals(nil) || "1".equals(nil)) {
            skipElement(reader, path, true);
            return null;
        }
        if (!type.complex) {
            return value(type.simple, readText(reader, path), path);
        }
        Map<String, Object> object = new LinkedHashMap<>();
        readAttributes(reader, type, object, path);
        if (type.hasSimpleContent()) {
            String text = readText(reader, path);
            object.put(textName, value(type.simple, text, path));
            return object;
        }
        while (true) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                QName name = reader.getName();
                Element child = type.children.get(name.getLocalPart());
                if (child == null || !child.name.getNamespaceURI().equals(name.getNamespaceURI())) {
                    unknownElement(reader, type, child, name, path);
                    continue;
                }
                String childPath = path + "/" + name.getLocalPart();
                Object value = readElement(reader, child, childPath, depth + 1);
                if (child.array) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) object.computeIfAbsent(child.jsonName(), k -> new ArrayList<>());
                    list.add(value);
                } else if (object.containsKey(child.jsonName())) {
                    throw new IllegalArgumentException(
                            "The element " + childPath + " occurs more than once, but the schema allows it once");
                } else {
                    object.put(child.jsonName(), value);
                }
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                if (!reader.isWhiteSpace() && !reader.getText().isBlank()) {
                    throw unsupported("text between the child elements is mixed content, which is not supported", path);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return object;
            } else if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new IllegalArgumentException("The XML ends inside " + path);
            }
        }
    }

    private void unknownElement(XMLStreamReader reader, Type type, Element child, QName name, String path)
            throws XMLStreamException {
        String childPath = path + "/" + name.getLocalPart();
        if (ignoreUnknown) {
            skipElement(reader, childPath, false);
            return;
        }
        if (child != null) {
            throw new IllegalArgumentException(
                    "The element " + childPath + " is in the namespace '" + name.getNamespaceURI()
                                               + "' but the schema declares it in '" + child.name.getNamespaceURI() + "'");
        }
        if (model.isSubstitute(name)) {
            throw unsupported("the element " + name + " is a member of a substitution group, which is not supported",
                    childPath);
        }
        if (type.wildcard) {
            throw unsupported("the element " + name + " matches xs:any, and xs:any content is not supported", childPath);
        }
        throw new IllegalArgumentException(
                "The element " + name + " at " + childPath + " is not declared by the type " + type.displayName()
                                           + " (set ignoreUnknown to skip it). The type has: " + type.children.keySet());
    }

    private void readAttributes(XMLStreamReader reader, Type type, Map<String, Object> object, String path) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String ns = reader.getAttributeNamespace(i);
            String local = reader.getAttributeLocalName(i);
            if (XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(ns)) {
                // xsi:nil, xsi:type, xsi:schemaLocation
                continue;
            }
            Attribute attribute = type.attributesByLocalName.get(local);
            String namespace = ns != null ? ns : "";
            if (attribute == null || !attribute.name.getNamespaceURI().equals(namespace)) {
                if (type.anyAttribute || ignoreUnknown) {
                    continue;
                }
                throw new IllegalArgumentException(
                        "The attribute " + new QName(namespace, local) + " of " + path + " is not declared by the type "
                                                   + type.displayName() + " (set ignoreUnknown to skip it)");
            }
            object.put(attribute.jsonName, value(attribute.type, reader.getAttributeValue(i), path + "/@" + local));
        }
    }

    private static boolean isDeclaredType(String xsiType, NamespaceContext context, Type type) {
        if (type.name == null) {
            return false;
        }
        int colon = xsiType.indexOf(':');
        String prefix = colon > 0 ? xsiType.substring(0, colon) : "";
        String local = colon > 0 ? xsiType.substring(colon + 1) : xsiType;
        String ns = context.getNamespaceURI(prefix);
        return type.name.getLocalPart().equals(local) && type.name.getNamespaceURI().equals(ns != null ? ns : "");
    }

    private static String readText(XMLStreamReader reader, String path) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE,
                        XMLStreamConstants.ENTITY_REFERENCE ->
                    sb.append(reader.getText());
                case XMLStreamConstants.START_ELEMENT -> throw new IllegalArgumentException(
                        "The element " + path + " has a simple type but contains the element " + reader.getName());
                case XMLStreamConstants.END_ELEMENT -> {
                    return sb.toString();
                }
                case XMLStreamConstants.END_DOCUMENT -> throw new IllegalArgumentException("The XML ends inside " + path);
                default -> {
                    // comments and processing instructions
                }
            }
        }
    }

    private static void skipElement(XMLStreamReader reader, String path, boolean nil) throws XMLStreamException {
        int level = 1;
        while (level > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (nil) {
                    throw new IllegalArgumentException("The element " + path + " has xsi:nil=\"true\" but has content");
                }
                level++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                level--;
            } else if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new IllegalArgumentException("The XML ends inside " + path);
            } else if (nil && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                    && !reader.getText().isBlank()) {
                throw new IllegalArgumentException("The element " + path + " has xsi:nil=\"true\" but has content");
            }
        }
    }

    /**
     * The JSON value of the text of a simple type.
     */
    static Object value(SimpleType type, String text, String path) {
        if (type.list()) {
            List<Object> items = new ArrayList<>();
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                for (String item : trimmed.split("\\s+")) {
                    items.add(scalar(type, item, path));
                }
            }
            return items;
        }
        return scalar(type, text, path);
    }

    private static Object scalar(SimpleType type, String text, String path) {
        String t = text.trim();
        try {
            switch (type.kind()) {
                case BOOLEAN:
                    if ("true".equals(t) || "1".equals(t)) {
                        return Boolean.TRUE;
                    } else if ("false".equals(t) || "0".equals(t)) {
                        return Boolean.FALSE;
                    }
                    throw new IllegalArgumentException(
                            "The value '" + text + "' at " + path + " is not an xs:boolean (true, false, 1 or 0)");
                case INTEGER:
                    BigInteger i = new BigInteger(t);
                    return i.bitLength() < 64 ? (Object) i.longValue() : i;
                case DECIMAL:
                    return new BigDecimal(t);
                case FLOAT:
                    if ("INF".equals(t) || "-INF".equals(t) || "+INF".equals(t) || "NaN".equals(t)) {
                        // not a JSON number
                        return t;
                    }
                    return Double.valueOf(t);
                default:
                    return text;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The value '" + text + "' at " + path + " is not a number, as its schema type needs", e);
        }
    }

    private static IllegalArgumentException unsupported(String what, String path) {
        return new IllegalArgumentException("Cannot map " + path + ": " + what);
    }
}
