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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

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
import org.apache.camel.xml.io.XmlPullParserException;

/** Parses standalone semantic declarations or declarations alongside XML routes. */
@RoutesLoader("semantic.xml")
public class SemanticXmlRoutesBuilderLoader extends RoutesBuilderLoaderSupport {
    static final String NAMESPACE = "http://camel.apache.org/schema/semantic";
    private static final Set<String> NAMESPACES = Set.of("", NAMESPACE,
            "http://camel.apache.org/schema/xml-io", "http://camel.apache.org/schema/spring");

    @Override
    public String getSupportedExtension() {
        return "semantic.xml";
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        // Resource-set bean preparation is complete; declarations still precede every route configuration.
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
                } else if (!"route".equals(child.getLocalName())) {
                    throw new IllegalArgumentException("Unexpected element in routes: " + child.getTagName());
                }
            }
            try (InputStream stream = resource.getInputStream()) {
                routes = new SemanticModelParser(resource, stream, namespace).parseRoutesDefinition()
                        .orElseThrow(() -> new IllegalArgumentException("Expected XML routes"));
            }
        } else {
            throw new IllegalArgumentException("Expected semantic or routes root element");
        }
        // Parse the complete document and validate every question before publishing any definitions.
        questions.register();
        return routes;
    }

    private static final class SemanticModelParser extends ModelParser {
        private SemanticModelParser(
                                    Resource resource, InputStream stream, String namespace)
                                                                                             throws IOException,
                                                                                             XmlPullParserException {
            super(stream, namespace);
            this.resource = resource;
        }

        @Override
        protected boolean handleUnexpectedElement(String namespace, String name) throws XmlPullParserException {
            if ("semantic".equals(name) && parser.getDepth() == 2) {
                // The DOM pass already validated this block. Keep the original route bytes and source positions.
                try {
                    parser.skipSubTree();
                } catch (IOException e) {
                    throw new XmlPullParserException("Cannot read semantic declaration", parser, e);
                }
                return true;
            }
            return super.handleUnexpectedElement(namespace, name);
        }
    }

    private static void declarations(Element semantic, SemanticQuestionsBuilder questions) {
        attributes(semantic, Set.of("expert", "state"));
        if (semantic.hasAttribute("expert")) {
            questions.expert(semantic.getAttribute("expert"));
        }
        if (semantic.hasAttribute("state")) {
            questions.state(semantic.getAttribute("state"));
        }
        for (Element element : children(semantic)) {
            if (!"question".equals(element.getLocalName()) && !"evaluation".equals(element.getLocalName())) {
                throw new IllegalArgumentException("Unexpected semantic element: " + element.getTagName());
            }
            try {
                question(element, questions);
            } catch (IllegalArgumentException e) {
                String expert = element.hasAttribute("expert") ? element.getAttribute("expert")
                        : questions.getExpert() != null ? questions.getExpert() : "default/automatic";
                throw new IllegalArgumentException(
                        "Invalid semantic question '" + element.getAttribute("name") + "': " + e.getMessage()
                                                   + " (expert '" + expert + "')",
                        e);
            }
        }
    }

    private static void question(Element element, SemanticQuestionsBuilder questions) {
        attributes(element,
                Set.of("name", "type", "operation", "expert", "state", "threshold", "uncertainty", "uncertaintyPolicy"));
        SemanticQuestionBuilder question = questions.question(element.getAttribute("name"));
        if (element.hasAttribute("type") && element.hasAttribute("operation")) {
            throw new IllegalArgumentException("Specify exactly one operation or type");
        }
        if (element.hasAttribute("operation")) {
            question.operation(element.getAttribute("operation"));
        }
        if (element.hasAttribute("type")) {
            question.type(element.getAttribute("type"));
        }
        if (element.hasAttribute("expert")) {
            question.expert(element.getAttribute("expert"));
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
                case "parameters" -> {
                    attributes(child, Set.of());
                    for (Element parameter : children(child)) {
                        if (!"parameter".equals(parameter.getLocalName())) {
                            throw new IllegalArgumentException("Expected parameter element");
                        }
                        attributes(parameter, Set.of("name"));
                        question.parameter(parameter.getAttribute("name"), parameterValue(parameter));
                    }
                }
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

    private static Object parameterValue(Element parent) {
        List<Element> values = children(parent);
        if (values.size() != 1) {
            throw new IllegalArgumentException("Parameter or map entry requires exactly one typed value");
        }
        return value(values.get(0));
    }

    private static Object value(Element element) {
        attributes(element, Set.of());
        return switch (element.getLocalName()) {
            case "string" -> text(element);
            case "null" -> {
                if (!text(element).isBlank()) {
                    throw new IllegalArgumentException("Null parameter value must be empty");
                }
                yield null;
            }
            case "number" -> {
                try {
                    yield new BigDecimal(text(element).strip());
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Invalid numeric parameter");
                }
            }
            case "boolean" -> {
                String text = text(element).strip();
                if (!"true".equals(text) && !"false".equals(text)) {
                    throw new IllegalArgumentException("Invalid boolean parameter");
                }
                yield Boolean.valueOf(text);
            }
            case "list" -> children(element).stream().map(SemanticXmlRoutesBuilderLoader::value).toList();
            case "map" -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Element entry : children(element)) {
                    if (!"entry".equals(entry.getLocalName())) {
                        throw new IllegalArgumentException("Expected map entry");
                    }
                    attributes(entry, Set.of("key"));
                    String key = entry.getAttribute("key");
                    if (map.containsKey(key)) {
                        throw new IllegalArgumentException("Duplicate parameter map key: " + key);
                    }
                    map.put(key, parameterValue(entry));
                }
                yield map;
            }
            default -> throw new IllegalArgumentException("Unknown parameter value type: " + element.getLocalName());
        };
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
                boolean semanticBlock = "routes".equals(parent.getLocalName())
                        && "semantic".equals(element.getLocalName()) && NAMESPACE.equals(namespace(element));
                if (!semanticBlock && !namespace(parent).equals(namespace(element))) {
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
