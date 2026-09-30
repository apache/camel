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
package org.apache.camel.semantic;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.RoutesBuilderLoaderSupport;
import org.apache.camel.xml.in.ModelParser;

/** Loads standalone declarations or declarations alongside routes from {@code *.semantic.xml} resources. */
@RoutesLoader("semantic.xml")
public class SemanticXmlRoutesBuilderLoader extends RoutesBuilderLoaderSupport {
    private static final Set<String> NAMESPACES = Set.of("", "http://camel.apache.org/schema/semantic",
            "http://camel.apache.org/schema/xml-io", "http://camel.apache.org/schema/spring");

    @Override
    public String getSupportedExtension() {
        return "semantic.xml";
    }

    @Override
    public void preParseRoute(Resource resource) throws Exception {
        // Register before any consuming route (including another DSL/resource) is configured.
        parse(resource);
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        RoutesDefinition routes = parse(resource);
        RouteBuilder builder = new RouteBuilder(getCamelContext()) {
            @Override
            public void configure() {
                routes.getRoutes().forEach(route -> {
                    CamelContextAware.trySetCamelContext(route, getContext());
                    getRouteCollection().route(route);
                });
            }
        };
        builder.setResource(resource);
        return builder;
    }

    private RoutesDefinition parse(Resource resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root;
        try (InputStream stream = resource.getInputStream()) {
            root = factory.newDocumentBuilder().parse(stream).getDocumentElement();
        }
        String namespace = namespace(root);
        if (!NAMESPACES.contains(namespace)) {
            throw new IllegalArgumentException("Unsupported semantic XML namespace: " + namespace);
        }
        SemanticQuestionsBuilder questions
                = new SemanticQuestionsBuilder(getCamelContext(), resource, resource.getLocation(), null);
        RoutesDefinition routes = new RoutesDefinition();
        if ("semantic".equals(root.getLocalName())) {
            declarations(root, questions);
        } else if ("routes".equals(root.getLocalName())) {
            boolean found = false;
            for (Element child : children(root)) {
                if ("semantic".equals(child.getLocalName())) {
                    if (found) {
                        throw new IllegalArgumentException("Only one semantic declaration block is allowed");
                    }
                    found = true;
                    declarations(child, questions);
                    root.removeChild(child);
                } else if (!"route".equals(child.getLocalName())) {
                    throw new IllegalArgumentException("Unexpected element in routes: " + child.getTagName());
                }
            }
            TransformerFactory transformer = TransformerFactory.newInstance();
            transformer.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformer.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformer.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            StringWriter xml = new StringWriter();
            transformer.newTransformer().transform(new DOMSource(root), new StreamResult(xml));
            routes = new ModelParser(new StringReader(xml.toString()), namespace).parseRoutesDefinition()
                    .orElseThrow(() -> new IllegalArgumentException("Expected XML routes"));
        } else {
            throw new IllegalArgumentException("Expected semantic or routes root element");
        }
        // Parse the complete document and validate every question before publishing any definitions.
        questions.register();
        return routes;
    }

    private static void declarations(Element semantic, SemanticQuestionsBuilder questions) {
        attributes(semantic, Set.of());
        for (Element element : children(semantic)) {
            if (!"question".equals(element.getLocalName())) {
                throw new IllegalArgumentException("Unexpected semantic element: " + element.getTagName());
            }
            attributes(element, Set.of("name", "type", "state", "threshold", "uncertainty", "uncertaintyPolicy"));
            SemanticQuestionBuilder question = questions.question(element.getAttribute("name"));
            if (element.hasAttribute("type")) {
                question.type(element.getAttribute("type"));
            }
            if (element.hasAttribute("state")) {
                question.state(element.getAttribute("state"));
            }
            if (element.hasAttribute("threshold")) {
                question.threshold(element.getAttribute("threshold"));
            }
            if (element.hasAttribute("uncertainty")) {
                question.uncertainty(element.getAttribute("uncertainty"));
            }
            if (element.hasAttribute("uncertaintyPolicy")) {
                question.uncertaintyPolicy(element.getAttribute("uncertaintyPolicy"));
            }
            boolean instructions = false;
            for (Element child : children(element)) {
                switch (child.getLocalName()) {
                    case "instructions" -> {
                        if (instructions) {
                            throw new IllegalArgumentException("Duplicate instructions for semantic question");
                        }
                        instructions = true;
                        attributes(child, Set.of());
                        question.instructions(text(child));
                    }
                    case "criterion" -> {
                        attributes(child, Set.of("key", "value"));
                        if (!children(child).isEmpty()) {
                            throw new IllegalArgumentException("Semantic criterion must not contain elements");
                        }
                        question.criterion(child.getAttribute("key"), child.getAttribute("value"));
                    }
                    case "level" -> {
                        attributes(child, Set.of());
                        question.level(text(child));
                    }
                    default -> throw new IllegalArgumentException("Unexpected question element: " + child.getTagName());
                }
            }
        }
    }

    private static String text(Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                throw new IllegalArgumentException("Unexpected element in " + element.getTagName());
            }
        }
        return element.getTextContent();
    }

    private static List<Element> children(Element parent) {
        List<Element> answer = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (!namespace(parent).equals(namespace(element))) {
                    throw new IllegalArgumentException("Unexpected namespace on " + element.getTagName());
                }
                answer.add(element);
            } else if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !child.getTextContent().isBlank()) {
                throw new IllegalArgumentException("Unexpected text in " + parent.getTagName());
            }
        }
        return answer;
    }

    private static void attributes(Element element, Set<String> allowed) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attribute = element.getAttributes().item(i);
            if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())
                    && (attribute.getNamespaceURI() != null || !allowed.contains(attribute.getNodeName()))) {
                throw new IllegalArgumentException("Unexpected attribute: " + attribute.getNodeName());
            }
        }
    }

    private static String namespace(Element element) {
        return element.getNamespaceURI() == null ? "" : element.getNamespaceURI();
    }
}
