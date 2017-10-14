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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;

import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CamelNodeDetailsFactory;

public final class CamelXmlTreeParserHelper {

    private final CamelCatalog camelCatalog = new DefaultCamelCatalog(true);

    public List<CamelNodeDetails> parseCamelRouteTree(Node xmlNode, String routeId, CamelNodeDetails route,
                                                             String baseDir, String fullyQualifiedFileName) {

        CamelNodeDetailsFactory nodeFactory = CamelNodeDetailsFactory.newInstance();
        List<CamelNodeDetails> answer = new ArrayList<>();

        walkXmlTree(nodeFactory, xmlNode, route);

        // now parse the route node and build the correct model/tree structure of the EIPs
        // re-create factory as we rebuild the tree
        nodeFactory = CamelNodeDetailsFactory.newInstance();
        CamelNodeDetails parent = route.getOutputs().get(0);

        // we dont want the route element and only start with from
        for (int i = 0; i < route.getOutputs().size(); i++) {
            CamelNodeDetails node = route.getOutputs().get(i);
            String name = node.getName();

            if ("from".equals(name)) {
                CamelNodeDetails from = nodeFactory.copyNode(null, "from", node);
                from.setFileName(fullyQualifiedFileName);
                answer.add(from);
                parent = from;
            } else {
                // add straight to parent
                parent.addOutput(node);
                node.setFileName(fullyQualifiedFileName);
            }
        }

        return answer;
    }

    private void walkXmlTree(CamelNodeDetailsFactory nodeFactory, Node node, CamelNodeDetails parent) {
        CamelNodeDetails newNode = null;

        String name = node.getNodeName();

        boolean isRoute = "route".equals(name) || "from".equals(name);
        // must be an eip model that has either input or output as we only want to track processors (also accept from)
        boolean isEip = camelCatalog.findModelNames().contains(name) && (hasInput(name) || hasOutput(name));

        // only include if its a known Camel model (dont include languages)
        if (isRoute || isEip) {
            // skip route as we just keep from
            if (!"route".equals(name)) {
                String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                newNode = nodeFactory.newNode(parent, name);
                newNode.setRouteId(parent.getRouteId());
                newNode.setFileName(parent.getFileName());
                newNode.setLineNumber(lineNumber);
                newNode.setLineNumberEnd(lineNumberEnd);

                parent.addOutput(newNode);
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walkXmlTree(nodeFactory, child, newNode != null ? newNode : parent);
            }
        }

    }

    private boolean hasOutput(String name) {
        String json = camelCatalog.modelJSonSchema(name);
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);
        return isModelOutput(rows);
    }

    private static boolean isModelOutput(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("output")) {
                return "true".equals(row.get("output"));
            }
        }
        return false;
    }

    private boolean hasInput(String name) {
        String json = camelCatalog.modelJSonSchema(name);
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);
        return isModelInput(rows);
    }

    private static boolean isModelInput(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("input")) {
                return "true".equals(row.get("input"));
            }
        }
        return false;
    }

}
