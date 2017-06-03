/**
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.jboss.forge.roaster.model.util.Strings;

/**
 * Various XML helper methods used for parsing XML routes.
 */
public final class CamelXmlHelper {

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

        NodeList list = dom.getElementsByTagName("endpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("endpoint".equals(child.getNodeName())) {
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

        list = dom.getElementsByTagName("onException");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("onCompletion");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("intercept");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("interceptFrom");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("interceptSendToEndpoint");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            findAllUrisRecursive(child, nodes);
        }
        list = dom.getElementsByTagName("rest");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName()) || "to".equals(child.getNodeName())) {
                findAllUrisRecursive(child, nodes);
            }
        }
        list = dom.getElementsByTagName("route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName())) {
                findAllUrisRecursive(child, nodes);
            }
        }

        return nodes;
    }

    private static void findAllUrisRecursive(Node node, List<Node> nodes) {
        // okay its a route so grab all uri attributes we can find
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

        NodeList list = dom.getElementsByTagName("route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName())) {
                nodes.add(child);
            }
        }

        return nodes;
    }

    public static List<Node> findAllSimpleExpressions(Document dom) {
        List<Node> nodes = new ArrayList<>();

        NodeList list = dom.getElementsByTagName("route");
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if ("route".equals(child.getNodeName())) {
                findAllSimpleExpressionsRecursive(child, nodes);
            }
        }

        return nodes;
    }

    private static void findAllSimpleExpressionsRecursive(Node node, List<Node> nodes) {
        // okay its a route so grab if its <simple>
        if ("simple".equals(node.getNodeName())) {
            nodes.add(node);
        }

        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    findAllSimpleExpressionsRecursive(child, nodes);
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

    private static Document loadCamelXmlFileAsDom(InputStream resourceInputStream) throws Exception {
        // must enforce the namespace to be http://camel.apache.org/schema/spring which is what the camel-core JAXB model uses
        Document root = XmlLineNumberParser.parseXml(resourceInputStream, "camelContext,routes,rests", "http://camel.apache.org/schema/spring");
        return root;
    }

    private static Node findCamelNodeInDocument(Document root, String key) {
        Node selectedNode = null;
        if (root != null && !Strings.isBlank(key)) {
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
                            if (!equal(actual, path)) {
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
                    if (equal(actual, path)) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    private static String getIdOrIndex(Node node, Map<String, Integer> nodeCounts) {
        String answer = null;
        if (node instanceof Element) {
            Element element = (Element) node;
            String elementName = element.getTagName();
            if ("routes".equals(elementName)) {
                elementName = "camelContext";
            }
            Integer countObject = nodeCounts.get(elementName);
            int count = countObject != null ? countObject.intValue() : 0;
            nodeCounts.put(elementName, ++count);
            answer = element.getAttribute("id");
            if (Strings.isBlank(answer)) {
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

    private static boolean equal(Object a, Object b) {
        return a == b ? true : a != null && b != null && a.equals(b);
    }

}
