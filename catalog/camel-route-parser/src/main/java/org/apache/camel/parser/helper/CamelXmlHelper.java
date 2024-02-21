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
package org.apache.camel.parser.helper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.tooling.util.Strings;

/**
 * Various XML helper methods used for parsing XML routes.
 */
public final class CamelXmlHelper {

    private static final String CAMEL_NS_SPRING = "http://camel.apache.org/schema/spring";
    private static final String CAMEL_NS_BLUEPRINT = "http://camel.apache.org/schema/blueprint";

    private CamelXmlHelper() {
        // utility class
    }

    public static String getSafeAttribute(Node node, String key) {
        if (node != null) {
            Node attr = node.getAttributes().getNamedItem(key);
            if (attr != null) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    public static List<Node> findAllEndpoints(Document dom) {
        List<Node> nodes = new ArrayList<>();

        NodeList list = getElementsByTagName(dom, "endpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if (isNodeName("endpoint", child)) {
                // it may not be a camel namespace, so skip those
                String ns = child.getNamespaceURI();
                if (ns == null) {
                    NamedNodeMap attrs = child.getAttributes();
                    if (attrs != null) {
                        Node node = attrs.getNamedItem("xmlns");
                        if (node != null) {
                            ns = node.getNodeValue();
                        }
                    }
                }
                // assume no namespace its for camel
                if (ns == null || ns.contains("camel")) {
                    nodes.add(child);
                }
            }
        }

        list = getElementsByTagName(dom, "onException");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = getElementsByTagName(dom, "onCompletion");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = getElementsByTagName(dom, "intercept");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = getElementsByTagName(dom, "interceptFrom");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = getElementsByTagName(dom, "interceptSendToEndpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = getElementsByTagName(dom, "rest");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if (isNodeName("route", child) || isNodeName("to", child)) {
                findAllUrisRecursive(child, nodes);
            }
        }
        list = getElementsByTagName(dom, "route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if (isNodeName("route", child)) {
                findAllUrisRecursive(child, nodes);
            }
        }

        return nodes;
    }

    private static void findAllUrisRecursive(Node node, List<Node> nodes) {
        // okay it's a route so grab all uri attributes we can find
        String url = getSafeAttribute(node, "uri");
        if (url != null) {
            nodes.add(node);
        }

        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    findAllUrisRecursive(child, nodes);
                }
            }
        }
    }

    public static List<Node> findAllRoutes(Document dom) {
        List<Node> nodes = new ArrayList<>();

        NodeList list = getElementsByTagName(dom, "route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if (isNodeName("route", child)) {
                nodes.add(child);
            }
        }

        return nodes;
    }

    public static List<Node> findAllLanguageExpressions(Document dom, String language) {
        List<Node> nodes = new ArrayList<>();

        NodeList list = getElementsByTagName(dom, "route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if (isNodeName("route", child)) {
                findAllLanguageExpressionsRecursive(child, nodes, language);
            }
        }

        return nodes;
    }

    private static void findAllLanguageExpressionsRecursive(Node node, List<Node> nodes, String language) {
        // okay it's a route so grab if it's the language
        if (isNodeName(language, node)) {
            nodes.add(node);
        }

        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    findAllLanguageExpressionsRecursive(child, nodes, language);
                }
            }
        }
    }

    public static Element getSelectedCamelElementNode(String key, InputStream resourceInputStream) throws Exception {
        Document root = loadCamelXmlFileAsDom(resourceInputStream);
        Element selectedElement = null;
        if (root != null) {
            Node selectedNode = findCamelNodeInDocument(root, key);
            if (selectedNode instanceof Element) {
                selectedElement = (Element) selectedNode;
            }
        }
        return selectedElement;
    }

    private static Document loadCamelXmlFileAsDom(InputStream resourceInputStream) {
        // must enforce the namespace to be http://camel.apache.org/schema/spring which is what the camel-core JAXB model uses
        return XmlLineNumberParser.parseXml(resourceInputStream, "camelContext,routes,rests",
                "http://camel.apache.org/schema/spring");
    }

    private static Node findCamelNodeInDocument(Document root, String key) {
        Node selectedNode = null;
        if (root != null && !Strings.isNullOrEmpty(key)) {
            String[] paths = key.split("/");
            NodeList camels = getCamelContextElements(root);
            if (camels != null) {
                Map<String, Integer> rootNodeCounts = new HashMap<>();
                for (int i = 0, size = camels.getLength(); i < size; i++) {
                    Node node = camels.item(i);
                    boolean first = true;
                    for (String path : paths) {
                        if (first) {
                            first = false;
                            String actual = getIdOrIndex(node, rootNodeCounts);
                            if (!Objects.equals(actual, path)) {
                                node = null;
                            }
                        } else {
                            node = findCamelNodeForPath(node, path);
                        }
                        if (node == null) {
                            break;
                        }
                    }
                    if (node != null) {
                        return node;
                    }
                }
            }
        }
        //FIXME : selectedNode is always null
        return selectedNode;
    }

    private static Node findCamelNodeForPath(Node node, String path) {
        NodeList childNodes = node.getChildNodes();
        if (childNodes != null) {
            Map<String, Integer> nodeCounts = new HashMap<>();
            for (int i = 0, size = childNodes.getLength(); i < size; i++) {
                Node child = childNodes.item(i);
                if (child instanceof Element) {
                    String actual = getIdOrIndex(child, nodeCounts);
                    if (Objects.equals(actual, path)) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    private static String getIdOrIndex(Node node, Map<String, Integer> nodeCounts) {
        String answer = null;
        if (node instanceof Element element) {
            String elementName = element.getTagName();
            if ("routes".equals(elementName)) {
                elementName = "camelContext";
            }
            Integer countObject = nodeCounts.get(elementName);
            int count = countObject != null ? countObject : 0;
            nodeCounts.put(elementName, ++count);
            answer = element.getAttribute("id");
            if (Strings.isNullOrEmpty(answer)) {
                answer = "_" + elementName + count;
            }
        }
        return answer;
    }

    private static NodeList getCamelContextElements(Document dom) {
        NodeList camels = dom.getElementsByTagName("camelContext");
        if (camels == null || camels.getLength() == 0) {
            camels = dom.getElementsByTagName("routes");
        }
        return camels;
    }

    private static NodeList getElementsByTagName(Document dom, String tagName) {
        NodeList list = dom.getElementsByTagName(tagName);
        if (list.getLength() == 0) {
            list = dom.getElementsByTagNameNS(CAMEL_NS_SPRING, tagName);
        }
        if (list.getLength() == 0) {
            list = dom.getElementsByTagNameNS(CAMEL_NS_BLUEPRINT, tagName);
        }
        return list;
    }

    private static boolean isNodeName(String name, Node node) {
        return name.equals(node.getLocalName()) || name.equals(node.getNodeName());
    }

}
