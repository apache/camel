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
package org.apache.camel.view;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.StringHelper.xmlEncode;

/**
 * @version 
 */
@Deprecated
public class XmlGraphGenerator extends GraphGeneratorSupport {
    private boolean addUrl = true;

    public XmlGraphGenerator(String dir) {
        super(dir, ".xml");
    }

    protected void generateFile(PrintWriter writer, Map<String, List<RouteDefinition>> map) {
        writer.println("<?xml version='1.0' encoding='UTF-8'?>");
        writer.println("<Graph>");
        writer.println();

        if (map.size() > 0) {
            writer.println("<Node id='root' name='Camel Routes' description='Collection of Camel Routes' nodeType='root'/>");
        }
        printRoutes(writer, map);

        writer.println();
        writer.println("</Graph>");
    }

    protected void printRoutes(PrintWriter writer, Map<String, List<RouteDefinition>> map) {
        Set<Map.Entry<String, List<RouteDefinition>>> entries = map.entrySet();
        for (Map.Entry<String, List<RouteDefinition>> entry : entries) {
            String group = entry.getKey();
            printRoutes(writer, group, entry.getValue());
        }
    }

    protected void printRoutes(PrintWriter writer, String group, List<RouteDefinition> routes) {
        group = xmlEncode(group);
        if (group != null) {
            int idx = group.lastIndexOf('.');
            String name = group;
            if (idx > 0 && idx < group.length() - 1) {
                name = group.substring(idx + 1);
            }
            writer.println("<Node id='" + group + "' name='" + name + "' description='" + group + "' nodeType='group'/>");
            writer.println("<Edge fromID='root' toID='" + group + "'/>");
        }
        for (RouteDefinition route : routes) {
            List<FromDefinition> inputs = route.getInputs();
            boolean first = true;
            for (FromDefinition input : inputs) {
                NodeData nodeData = getNodeData(input);
                if (first) {
                    first = false;
                    if (group != null) {
                        writer.println("<Edge fromID='" + group + "' toID='" + xmlEncode(nodeData.id) + "'/>");
                    }
                }
                printRoute(writer, route, nodeData);
            }
            writer.println();
        }
    }

    protected void printRoute(PrintWriter writer, final RouteDefinition route, NodeData nodeData) {
        printNode(writer, nodeData);

        NodeData from = nodeData;
        for (ProcessorDefinition<?> output : route.getOutputs()) {
            NodeData newData = printNode(writer, from, output);
            from = newData;
        }
    }

    protected NodeData printNode(PrintWriter writer, NodeData fromData, ProcessorDefinition<?> node) {
        if (node instanceof MulticastDefinition) {
            // no need for a multicast node
            List<ProcessorDefinition<?>> outputs = node.getOutputs();
            for (ProcessorDefinition<?> output : outputs) {
                printNode(writer, fromData, output);
            }
            return fromData;
        }
        NodeData toData = getNodeData(node);

        printNode(writer, toData);

        if (fromData != null) {
            writer.print("<Edge fromID=\"");
            writer.print(xmlEncode(fromData.id));
            writer.print("\" toID=\"");
            writer.print(xmlEncode(toData.id));
            String association = toData.edgeLabel;
            if (isEmpty(association)) {
                writer.print("\" association=\"");
                writer.print(xmlEncode(association));
            }
            writer.println("\"/>");
        }

        // now lets write any children
        List<ProcessorDefinition<?>> outputs = toData.outputs;
        if (outputs != null) {
            for (ProcessorDefinition<?> output : outputs) {
                NodeData newData = printNode(writer, toData, output);
                if (!isMulticastNode(node)) {
                    toData = newData;
                }
            }
        }
        return toData;
    }

    protected void printNode(PrintWriter writer, NodeData data) {
        if (!data.nodeWritten) {
            data.nodeWritten = true;

            writer.println();
            writer.print("<Node id=\"");
            writer.print(xmlEncode(data.id));
            writer.print("\" name=\"");
            String name = data.label;
            if (isEmpty(name)) {
                name = data.tooltop;
            }
            writer.print(xmlEncode(name));
            writer.print("\" nodeType=\"");
            String nodeType = data.image;
            if (isEmpty(nodeType)) {
                nodeType = data.shape;
                if (isEmpty(nodeType)) {
                    nodeType = "node";
                }
            }
            writer.print(xmlEncode(nodeType));
            writer.print("\" description=\"");
            writer.print(xmlEncode(data.tooltop));
            if (addUrl) {
                writer.print("\" url=\"");
                writer.print(xmlEncode(data.url));
            }
            writer.println("\"/>");
        }
    }

}
