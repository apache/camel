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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;

/**
 * The XML Schema compiled into the elements and types that the mapping needs. It is built once, when the data format
 * starts, and is not changed afterwards, so one instance is shared by all messages.
 */
final class XsdModel {

    /**
     * How the text of a simple type is mapped to JSON.
     */
    enum SimpleKind {
        STRING,
        BOOLEAN,
        INTEGER,
        DECIMAL,
        FLOAT
    }

    enum GroupKind {
        SEQUENCE,
        CHOICE,
        ALL
    }

    /**
     * A simple type: the JSON kind of its text, and whether it is an xs:list (a JSON array). The built-in type that it
     * derives from (such as dateTime or int) and its first enumeration value (or null) are only used for samples.
     */
    record SimpleType(SimpleKind kind, boolean list, String builtin, String enumeration) {

        SimpleType withEnumeration(String value) {
            return new SimpleType(kind, list, builtin, value);
        }
    }

    /**
     * An attribute of a complex type.
     */
    static final class Attribute {
        final QName name;
        final String jsonName;
        final SimpleType type;

        Attribute(QName name, String jsonName, SimpleType type) {
            this.name = name;
            this.jsonName = jsonName;
            this.type = type;
        }
    }

    /**
     * An element declaration as used in one place: a global element, or a particle of a content model.
     */
    static final class Element {
        final QName name;
        final Type type;
        final boolean array;
        final boolean optional;
        final boolean nillable;
        final String unsupported;

        Element(QName name, Type type, boolean array, boolean optional, boolean nillable, String unsupported) {
            this.name = name;
            this.type = type;
            this.array = array;
            this.optional = optional;
            this.nillable = nillable;
            this.unsupported = unsupported;
        }

        String jsonName() {
            return name.getLocalPart();
        }
    }

    /**
     * A model group (xs:sequence, xs:choice or xs:all) with elements and nested groups as members.
     */
    static final class Group {
        final GroupKind kind;
        final boolean repeating;
        final List<Object> members = new ArrayList<>();

        Group(GroupKind kind, boolean repeating) {
            this.kind = kind;
            this.repeating = repeating;
        }

        /**
         * The elements of this group, including those of nested groups.
         */
        void collectElements(List<Element> answer) {
            for (Object member : members) {
                if (member instanceof Element e) {
                    answer.add(e);
                } else if (member instanceof Group g) {
                    g.collectElements(answer);
                }
            }
        }
    }

    /**
     * A type: a simple type, a complex type with simple content (simple is set), or a complex type with element content
     * (content is set, or none for an empty type).
     */
    static final class Type {
        final QName name;
        SimpleType simple;
        Group content;
        boolean complex;
        boolean wildcard;
        boolean anyAttribute;
        String unsupported;
        String conflict;
        final List<Attribute> attributes = new ArrayList<>();
        final Map<String, Attribute> attributesByLocalName = new LinkedHashMap<>();
        final Map<String, Attribute> attributesByJsonName = new LinkedHashMap<>();
        final Map<String, Element> children = new LinkedHashMap<>();

        Type(QName name) {
            this.name = name;
        }

        String displayName() {
            return name != null ? name.toString() : "(anonymous type)";
        }

        boolean hasSimpleContent() {
            return simple != null;
        }
    }

    private final Map<QName, Element> globalElements;
    private final Map<QName, Element> substitutes;
    private final Map<String, String> prefixes;

    XsdModel(Map<QName, Element> globalElements, Map<QName, Element> substitutes, Map<String, String> prefixes) {
        this.globalElements = Collections.unmodifiableMap(globalElements);
        this.substitutes = Collections.unmodifiableMap(substitutes);
        this.prefixes = Collections.unmodifiableMap(prefixes);
    }

    Map<QName, Element> getGlobalElements() {
        return globalElements;
    }

    Element getGlobalElement(QName name) {
        return globalElements.get(name);
    }

    /**
     * Whether the element is a member of a substitution group, which phase 1 does not map.
     */
    boolean isSubstitute(QName name) {
        return substitutes.containsKey(name);
    }

    /**
     * The prefix to use for the namespace when writing XML.
     */
    String prefix(String namespace) {
        return prefixes.get(namespace);
    }

    /**
     * Finds a global element by {namespace}name or by a local name that is unique in the schema.
     */
    Element findElement(String name) {
        String n = name.trim();
        if (n.startsWith("{")) {
            Element e = globalElements.get(QName.valueOf(n));
            if (e == null) {
                throw new IllegalArgumentException(
                        "The schema has no global element " + n + ". The global elements are: " + elementNames());
            }
            return e;
        }
        Element found = null;
        for (Element e : globalElements.values()) {
            if (e.name.getLocalPart().equals(n)) {
                if (found != null) {
                    throw new IllegalArgumentException(
                            "The element name " + n + " is ambiguous: " + found.name + " and " + e.name
                                                       + " both match. Use {namespace}name.");
                }
                found = e;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "The schema has no global element " + n + ". The global elements are: " + elementNames());
        }
        return found;
    }

    String elementNames() {
        Set<String> names = new TreeSet<>();
        for (QName q : globalElements.keySet()) {
            names.add(q.toString());
        }
        return String.join(", ", names);
    }

    /**
     * Fails when a type reachable from the element has a naming conflict, so a configured root element fails when the
     * data format starts instead of on the first message.
     */
    void checkConflicts(Element root) {
        checkConflicts(root.type, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void checkConflicts(Type type, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type.conflict != null) {
            throw new IllegalArgumentException(type.conflict);
        }
        for (Element child : type.children.values()) {
            checkConflicts(child.type, visited);
        }
    }
}
