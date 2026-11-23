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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.camel.dataformat.xsdjson.XsdModel.Attribute;
import org.apache.camel.dataformat.xsdjson.XsdModel.Element;
import org.apache.camel.dataformat.xsdjson.XsdModel.Group;
import org.apache.camel.dataformat.xsdjson.XsdModel.GroupKind;
import org.apache.camel.dataformat.xsdjson.XsdModel.SimpleKind;
import org.apache.camel.dataformat.xsdjson.XsdModel.SimpleType;
import org.apache.camel.dataformat.xsdjson.XsdModel.Type;

/**
 * Writes a JSON tree (a Map, a List, or a value) as XML with StAX, in the order and namespaces of the schema.
 */
final class JsonToXml {

    private static final String XSI_PREFIX = "xsi";

    private final XsdModel model;
    private final boolean ignoreUnknown;
    private final String textName;
    private final boolean pretty;
    private final XMLStreamWriter writer;
    private final Set<String> inScope = new HashSet<>();
    private final Deque<List<String>> declared = new ArrayDeque<>();
    private int generated;

    JsonToXml(XsdModel model, boolean ignoreUnknown, String textName, boolean pretty, XMLStreamWriter writer) {
        this.model = model;
        this.ignoreUnknown = ignoreUnknown;
        this.textName = textName;
        this.pretty = pretty;
        this.writer = writer;
    }

    void write(Element root, Object value) throws XMLStreamException {
        if (value == null) {
            if (!root.nillable) {
                throw new IllegalArgumentException(
                        "The JSON is null, but the root element " + root.name + " is not nillable");
            }
        }
        try {
            writeElement(root, value, "/" + root.jsonName(), 0);
        } catch (StackOverflowError e) {
            // the depth limit is checked per element, but a small thread stack can be exhausted before that limit
            throw new IllegalArgumentException(
                    "The JSON is nested too deep for the thread stack (the configured maximum is " + XmlToJson.MAX_DEPTH
                                               + " levels)",
                    e);
        }
    }

    private void writeElement(Element element, Object value, String path, int depth) throws XMLStreamException {
        if (depth > XmlToJson.MAX_DEPTH) {
            throw new IllegalArgumentException("The JSON is nested deeper than " + XmlToJson.MAX_DEPTH + " at " + path);
        }
        if (element.unsupported != null) {
            throw new IllegalArgumentException("Cannot map " + path + ": " + element.unsupported);
        }
        Type type = element.type;
        if (type.unsupported != null) {
            throw new IllegalArgumentException("Cannot map " + path + ": " + type.unsupported);
        }
        if (type.conflict != null) {
            throw new IllegalArgumentException(type.conflict);
        }
        if (value == null) {
            if (!element.nillable) {
                if (element.optional) {
                    return;
                }
                throw new IllegalArgumentException(
                        "The JSON field " + path + " is null, but the element is neither nillable nor optional");
            }
            indent(depth);
            startElement(element, List.of(), true);
            writer.writeAttribute(XSI_PREFIX, XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "nil", "true");
            endElement();
            return;
        }
        indent(depth);
        if (!type.complex) {
            startElement(element, List.of(), false);
            writer.writeCharacters(text(type.simple, value, path));
            endElement();
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            if (type.hasSimpleContent() && !(value instanceof Collection)) {
                // simple content given as its value only, without attributes
                startElement(element, List.of(), false);
                writer.writeCharacters(text(type.simple, value, path));
                endElement();
                return;
            }
            throw new IllegalArgumentException(
                    "The JSON field " + path + " must be an object for the element " + element.name + ", but is "
                                               + describe(value));
        }
        checkUnknown(type, map, path);
        List<Attribute> attributes = new ArrayList<>();
        for (Attribute a : type.attributes) {
            if (map.get(a.jsonName) != null) {
                attributes.add(a);
            }
        }
        startElement(element, attributes, false);
        for (Attribute a : attributes) {
            String text = text(a.type, map.get(a.jsonName), path + "/" + a.jsonName);
            String ns = a.name.getNamespaceURI();
            if (ns.isEmpty()) {
                writer.writeAttribute(a.name.getLocalPart(), text);
            } else {
                writer.writeAttribute(prefix(ns), ns, a.name.getLocalPart(), text);
            }
        }
        if (type.hasSimpleContent()) {
            Object text = map.get(textName);
            if (text != null) {
                writer.writeCharacters(text(type.simple, text, path + "/" + textName));
            }
            endElement();
            return;
        }
        boolean children = false;
        if (type.content != null) {
            children = writeGroup(type.content, map, path, depth + 1);
        }
        if (children && pretty) {
            writer.writeCharacters("\n" + "  ".repeat(depth));
        }
        endElement();
    }

    private void checkUnknown(Type type, Map<?, ?> map, String path) {
        if (ignoreUnknown) {
            return;
        }
        for (Object key : map.keySet()) {
            String k = String.valueOf(key);
            if (type.children.containsKey(k) || type.attributesByJsonName.containsKey(k)
                    || type.hasSimpleContent() && textName.equals(k)) {
                continue;
            }
            if (type.wildcard) {
                throw new IllegalArgumentException(
                        "Cannot map " + path + "/" + k + ": the field would match xs:any, and xs:any content is not supported");
            }
            throw new IllegalArgumentException(
                    "The JSON field " + path + "/" + k + " is not declared by the type " + type.displayName()
                                               + " (set ignoreUnknown to skip it). The type has: " + names(type));
        }
    }

    private static String names(Type type) {
        List<String> names = new ArrayList<>(type.attributesByJsonName.keySet());
        names.addAll(type.children.keySet());
        return names.toString();
    }

    /**
     * Writes the members of a group in schema order. Returns whether an element was written.
     */
    private boolean writeGroup(Group group, Map<?, ?> map, String path, int depth) throws XMLStreamException {
        boolean written = false;
        if (group.kind == GroupKind.ALL) {
            // xs:all has no order: keep the order of the JSON
            for (Object key : map.keySet()) {
                for (Object member : group.members) {
                    if (member instanceof Element e && e.jsonName().equals(key)) {
                        written |= writeMember(e, map, path, depth, -1);
                    }
                }
            }
            return written;
        }
        if (group.kind == GroupKind.CHOICE) {
            List<Object> present = new ArrayList<>();
            for (Object member : group.members) {
                if (isPresent(member, map)) {
                    present.add(member);
                }
            }
            if (present.size() > 1 && !group.repeating) {
                throw new IllegalArgumentException(
                        "The JSON at " + path + " has " + describeMembers(present)
                                                   + ", but the schema has an xs:choice that allows only one of them");
            }
            for (Object member : present) {
                written |= writeMemberOrGroup(member, map, path, depth);
            }
            return written;
        }
        if (group.repeating) {
            // a repeating sequence: the items of its elements are written by index, a[0] b[0] a[1] b[1] ...
            int size = 0;
            for (Object member : group.members) {
                if (member instanceof Element e) {
                    size = Math.max(size, asList(map.get(e.jsonName())).size());
                }
            }
            for (int i = 0; i < size; i++) {
                for (Object member : group.members) {
                    if (member instanceof Element e) {
                        written |= writeMember(e, map, path, depth, i);
                    } else if (i == 0) {
                        written |= writeMemberOrGroup(member, map, path, depth);
                    }
                }
            }
            return written;
        }
        for (Object member : group.members) {
            written |= writeMemberOrGroup(member, map, path, depth);
        }
        return written;
    }

    private boolean writeMemberOrGroup(Object member, Map<?, ?> map, String path, int depth) throws XMLStreamException {
        if (member instanceof Element e) {
            return writeMember(e, map, path, depth, -1);
        }
        return writeGroup((Group) member, map, path, depth);
    }

    /**
     * Writes an element of a group, all its items, or only the item at index when index is 0 or more.
     */
    private boolean writeMember(Element e, Map<?, ?> map, String path, int depth, int index) throws XMLStreamException {
        if (!map.containsKey(e.jsonName())) {
            return false;
        }
        Object value = map.get(e.jsonName());
        String childPath = path + "/" + e.jsonName();
        if (e.array) {
            List<?> items = asList(value);
            if (index >= 0) {
                if (index >= items.size()) {
                    return false;
                }
                writeElement(e, items.get(index), childPath + "[" + index + "]", depth);
                return true;
            }
            for (int i = 0; i < items.size(); i++) {
                writeElement(e, items.get(i), childPath + "[" + i + "]", depth);
            }
            return !items.isEmpty();
        }
        if (value instanceof Collection && !(e.type != null && !e.type.complex && e.type.simple.list())) {
            throw new IllegalArgumentException(
                    "The JSON field " + childPath + " is an array, but the element " + e.name
                                               + " occurs at most once (maxOccurs=1)");
        }
        if (value == null && !e.nillable && e.optional) {
            return false;
        }
        writeElement(e, value, childPath, depth);
        return true;
    }

    /**
     * The items of an element that repeats. A JSON null means no items, the same as a missing field; a null item inside
     * the array (such as [null]) is one element, written as xsi:nil when the element is nillable.
     */
    private static List<?> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        // a single item where the schema has an array
        return List.of(value);
    }

    private static boolean isPresent(Object member, Map<?, ?> map) {
        if (member instanceof Element e) {
            if (!map.containsKey(e.jsonName())) {
                return false;
            }
            Object v = map.get(e.jsonName());
            if (v == null && e.array) {
                // a JSON null for an element that repeats writes no items
                return false;
            }
            return !(v instanceof Collection<?> c && c.isEmpty());
        }
        List<Element> elements = new ArrayList<>();
        ((Group) member).collectElements(elements);
        for (Element e : elements) {
            if (isPresent(e, map)) {
                return true;
            }
        }
        return false;
    }

    private static String describeMembers(List<Object> members) {
        List<String> names = new ArrayList<>();
        for (Object m : members) {
            if (m instanceof Element e) {
                names.add(e.jsonName());
            } else {
                List<Element> elements = new ArrayList<>();
                ((Group) m).collectElements(elements);
                List<String> group = new ArrayList<>();
                elements.forEach(e -> group.add(e.jsonName()));
                names.add(String.join("+", group));
            }
        }
        return String.join(" and ", names);
    }

    private void startElement(Element element, List<Attribute> attributes, boolean nil) throws XMLStreamException {
        List<String> declaredHere = new ArrayList<>(2);
        String ns = element.name.getNamespaceURI();
        if (ns.isEmpty()) {
            writer.writeStartElement(element.name.getLocalPart());
        } else {
            String prefix = prefix(ns);
            writer.writeStartElement(prefix, element.name.getLocalPart(), ns);
            declare(prefix, ns, declaredHere);
        }
        for (Attribute a : attributes) {
            String ans = a.name.getNamespaceURI();
            if (!ans.isEmpty()) {
                declare(prefix(ans), ans, declaredHere);
            }
        }
        if (nil) {
            declare(XSI_PREFIX, XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, declaredHere);
        }
        declared.push(declaredHere);
    }

    private void declare(String prefix, String ns, List<String> declaredHere) throws XMLStreamException {
        if (inScope.add(ns)) {
            writer.writeNamespace(prefix, ns);
            declaredHere.add(ns);
        }
    }

    private void endElement() throws XMLStreamException {
        writer.writeEndElement();
        for (String ns : declared.pop()) {
            inScope.remove(ns);
        }
    }

    private String prefix(String ns) {
        String prefix = model.prefix(ns);
        if (prefix == null) {
            prefix = "ns" + (100 + ++generated);
        }
        return prefix;
    }

    private void indent(int depth) throws XMLStreamException {
        if (pretty && depth > 0) {
            writer.writeCharacters("\n" + "  ".repeat(depth));
        }
    }

    /**
     * The XML text of a JSON value of a simple type.
     */
    private static String text(SimpleType type, Object value, String path) {
        if (type.list()) {
            if (value instanceof Collection<?> items) {
                List<String> texts = new ArrayList<>();
                for (Object item : items) {
                    texts.add(scalar(type.kind(), item, path));
                }
                return String.join(" ", texts);
            }
            return scalar(type.kind(), value, path);
        }
        if (value instanceof Collection || value instanceof Map) {
            throw new IllegalArgumentException(
                    "The JSON field " + path + " must be a value for a simple type, but is " + describe(value));
        }
        return scalar(type.kind(), value, path);
    }

    private static String scalar(SimpleKind kind, Object value, String path) {
        if (value instanceof Collection || value instanceof Map) {
            throw new IllegalArgumentException(
                    "The JSON field " + path + " must be a value for a simple type, but is " + describe(value));
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d)) {
                return "NaN";
            } else if (Double.isInfinite(d)) {
                return d > 0 ? "INF" : "-INF";
            }
            if (kind == SimpleKind.FLOAT) {
                return value.toString();
            }
            value = BigDecimal.valueOf(d);
        }
        if (value instanceof BigDecimal d) {
            if (kind == SimpleKind.INTEGER) {
                BigDecimal stripped = d.stripTrailingZeros();
                if (stripped.scale() <= 0) {
                    return stripped.toBigInteger().toString();
                }
            }
            return d.toPlainString();
        }
        return value.toString();
    }

    private static String describe(Object value) {
        if (value instanceof Map) {
            return "an object";
        } else if (value instanceof Collection) {
            return "an array";
        } else if (value instanceof String) {
            return "a string";
        } else if (value instanceof Number) {
            return "a number";
        } else if (value instanceof Boolean) {
            return "a boolean";
        }
        return value.getClass().getName();
    }
}
