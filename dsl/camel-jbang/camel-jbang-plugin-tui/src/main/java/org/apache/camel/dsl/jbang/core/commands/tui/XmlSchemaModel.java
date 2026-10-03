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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import org.apache.camel.dsl.jbang.core.common.XmlHelper;

/**
 * The elements and attributes of the XML DSL, read from the camel-xml-io XSD of the catalog (CAMEL-25240): which
 * elements go inside an element (each type lists them, outputs and expression languages included), and its attributes
 * with whether they are required and their documentation, inherited ones (id, description...) included.
 */
final class XmlSchemaModel {

    private static final String XS = "http://www.w3.org/2001/XMLSchema";

    /** An attribute of an element. */
    record Attribute(String name, boolean required, String doc) {
    }

    /** An element that can go inside another one, with its type (to go further down) and documentation. */
    record Child(String name, String type, String doc) {
    }

    private record Type(String base, List<Attribute> attributes, List<Child> children, boolean text) {
    }

    private final Map<String, String> elementTypes = new LinkedHashMap<>();
    private final Map<String, String> elementDocs = new HashMap<>();
    private final Map<String, Type> types = new HashMap<>();

    private XmlSchemaModel() {
    }

    /** Reads the schema; null when it cannot be read. */
    static XmlSchemaModel parse(String xsd) {
        if (xsd == null || xsd.isEmpty()) {
            return null;
        }
        try {
            DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
            dbf.setNamespaceAware(true);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Element root = builder.parse(new InputSource(new StringReader(xsd))).getDocumentElement();
            XmlSchemaModel model = new XmlSchemaModel();
            // the top-level elements first: the children that refer to them take their type and documentation
            for (Element e : children(root, "element")) {
                model.elementTypes.put(e.getAttribute("name"), localName(e.getAttribute("type")));
                model.elementDocs.put(e.getAttribute("name"), doc(e));
            }
            for (Element ct : children(root, "complexType")) {
                model.types.put(ct.getAttribute("name"), model.readType(ct.getAttribute("name"), ct));
            }
            return model;
        } catch (Exception e) {
            return null;
        }
    }

    private Type readType(String name, Element complexType) {
        String base = null;
        boolean text = false;
        List<Attribute> attributes = new ArrayList<>();
        List<Child> children = new ArrayList<>();
        Element content = complexType;
        for (Element c : children(complexType, null)) {
            if ("complexContent".equals(c.getLocalName()) || "simpleContent".equals(c.getLocalName())) {
                text = "simpleContent".equals(c.getLocalName());
                for (Element ext : children(c, "extension")) {
                    String b = localName(ext.getAttribute("base"));
                    // xs:string of simpleContent is the text of the element, not a type of the schema
                    base = ext.getAttribute("base").startsWith("xs:") ? null : b;
                    content = ext;
                }
            }
        }
        collect(name, content, attributes, children);
        return new Type(base, attributes, children, text);
    }

    /**
     * The attributes and child elements under a type's content, through its sequences and choices. A local element with
     * its type inline (allowableValues of param) gets that type under the name owner/element.
     */
    private void collect(String owner, Element parent, List<Attribute> attributes, List<Child> children) {
        for (Element c : children(parent, null)) {
            switch (c.getLocalName()) {
                case "attribute" -> attributes.add(new Attribute(
                        c.getAttribute("name"), "required".equals(c.getAttribute("use")), doc(c)));
                case "element" -> {
                    if (c.hasAttribute("ref")) {
                        String name = localName(c.getAttribute("ref"));
                        children.add(new Child(name, elementTypes.get(name), elementDocs.get(name)));
                    } else if (c.hasAttribute("type")) {
                        children.add(new Child(c.getAttribute("name"), localName(c.getAttribute("type")), doc(c)));
                    } else {
                        String inline = owner + "/" + c.getAttribute("name");
                        for (Element ct : children(c, "complexType")) {
                            types.put(inline, readType(inline, ct));
                        }
                        children.add(new Child(c.getAttribute("name"), types.containsKey(inline) ? inline : null, doc(c)));
                    }
                }
                case "sequence", "choice" -> collect(owner, c, attributes, children);
                default -> {
                }
            }
        }
    }

    /** The type of a top-level element, null when there is none. */
    String elementType(String element) {
        return elementTypes.get(element);
    }

    /** The documentation of a top-level element. */
    String elementDoc(String element) {
        return elementDocs.get(element);
    }

    /** The top-level elements, in schema order. */
    List<String> elements() {
        return new ArrayList<>(elementTypes.keySet());
    }

    /**
     * The type of the innermost element of a path from the outermost one (routes, route, choice, when): each one is
     * looked up among the children of the one before it, else as a top-level element, so a path that starts in a Spring
     * XML file or another wrapper still finds its Camel elements.
     */
    String typeOf(List<String> path) {
        String type = null;
        for (String name : path) {
            String next = null;
            if (type != null) {
                for (Child c : children(type)) {
                    if (c.name().equals(name)) {
                        next = c.type();
                        break;
                    }
                }
            }
            type = next != null ? next : elementTypes.get(name);
        }
        return type;
    }

    /** The elements that go inside one of the type, inherited ones first, without repeats. */
    List<Child> children(String type) {
        Map<String, Child> found = new LinkedHashMap<>();
        for (Type t : hierarchy(type)) {
            for (Child c : t.children()) {
                found.putIfAbsent(c.name(), c);
            }
        }
        return new ArrayList<>(found.values());
    }

    /** The attributes of the type, its own first, then the inherited ones (id, description...). */
    List<Attribute> attributes(String type) {
        Map<String, Attribute> found = new LinkedHashMap<>();
        List<Type> chain = hierarchy(type);
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (Attribute a : chain.get(i).attributes()) {
                found.putIfAbsent(a.name(), a);
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Whether the element of the type has text, such as the expression of simple. */
    boolean hasText(String type) {
        for (Type t : hierarchy(type)) {
            if (t.text()) {
                return true;
            }
        }
        return false;
    }

    /** The type and its base types, the outermost base first. */
    private List<Type> hierarchy(String type) {
        List<Type> chain = new ArrayList<>();
        String name = type;
        while (name != null && chain.size() < 20) {
            Type t = types.get(name);
            if (t == null) {
                break;
            }
            chain.add(0, t);
            name = t.base();
        }
        return chain;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && XS.equals(e.getNamespaceURI())
                    && (localName == null || localName.equals(e.getLocalName()))) {
                found.add(e);
            }
        }
        return found;
    }

    private static String doc(Element e) {
        for (Element annotation : children(e, "annotation")) {
            for (Element d : children(annotation, "documentation")) {
                String text = d.getTextContent().strip().replaceAll("\\s+", " ");
                return text.isEmpty() ? null : text;
            }
        }
        return null;
    }

    private static String localName(String qname) {
        if (qname == null || qname.isEmpty()) {
            return null;
        }
        int colon = qname.indexOf(':');
        return colon >= 0 ? qname.substring(colon + 1) : qname;
    }
}
