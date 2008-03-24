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

import org.apache.camel.model.FromType;
import org.apache.camel.model.MulticastType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;

/**
 * @version $Revision$
 */
public class XmlGraphGenerator extends GraphGeneratorSupport {
    private boolean addUrl = true;

    public XmlGraphGenerator(String dir) {
        super(dir, ".xml");
    }

    protected void generateFile(PrintWriter writer, Map<String, List<RouteType>> map) {
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

    protected void printRoutes(PrintWriter writer, Map<String, List<RouteType>> map) {
        Set<Map.Entry<String, List<RouteType>>> entries = map.entrySet();
        for (Map.Entry<String, List<RouteType>> entry : entries) {
            String group = entry.getKey();
            printRoutes(writer, group, entry.getValue());
        }
    }

    protected void printRoutes(PrintWriter writer, String group, List<RouteType> routes) {
        group = encode(group);
        if (group != null) {
            int idx = group.lastIndexOf('.');
            String name = group;
            if (idx > 0 && idx < group.length() - 1) {
                name = group.substring(idx + 1);
            }
            writer.println("<Node id='" + group + "' name='" + name + "' description='" + group + "' nodeType='group'/>");
            writer.println("<Edge fromID='root' toID='" + group + "'/>");
        }
        for (RouteType route : routes) {
            List<FromType> inputs = route.getInputs();
            boolean first = true;
            for (FromType input : inputs) {
                NodeData nodeData = getNodeData(input);
                if (first) {
                    first = false;
                    if (group != null) {
                        writer.println("<Edge fromID='" + group + "' toID='" + encode(nodeData.id) + "'/>");
                    }
                }
                printRoute(writer, route, nodeData);
            }
            writer.println();
        }
    }

    protected void printRoute(PrintWriter writer, final RouteType route, NodeData nodeData) {
        printNode(writer, nodeData);

        // TODO we should add a transactional client / event driven consumer / polling client

        NodeData from = nodeData;
        for (ProcessorType output : route.getOutputs()) {
            NodeData newData = printNode(writer, from, output);
            from = newData;
        }
    }

    protected NodeData printNode(PrintWriter writer, NodeData fromData, ProcessorType node) {
        if (node instanceof MulticastType) {
            // no need for a multicast node
            List<ProcessorType> outputs = node.getOutputs();
            for (ProcessorType output : outputs) {
                printNode(writer, fromData, output);
            }
            return fromData;
        }
        NodeData toData = getNodeData(node);

        printNode(writer, toData);

        if (fromData != null) {
            writer.print("<Edge fromID=\"");
            writer.print(encode(fromData.id));
            writer.print("\" toID=\"");
            writer.print(encode(toData.id));
            String association = toData.edgeLabel;
            if (isNullOrBlank(association)) {
                writer.print("\" association=\"");
                writer.print(encode(association));
            }
            writer.println("\"/>");
        }

        // now lets write any children
        List<ProcessorType> outputs = toData.outputs;
        if (outputs != null) {
            for (ProcessorType output : outputs) {
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
            writer.print(encode(data.id));
            writer.print("\" name=\"");
            String name = data.label;
            if (isNullOrBlank(name)) {
                name = data.tooltop;
            }
            writer.print(encode(name));
            writer.print("\" nodeType=\"");
            String nodeType = data.image;
            if (isNullOrBlank(nodeType)) {
                nodeType = data.shape;
                if (isNullOrBlank(nodeType)) {
                    nodeType = "node";
                }
            }
            writer.print(encode(nodeType));
            writer.print("\" description=\"");
            writer.print(encode(data.tooltop));
            if (addUrl) {
                writer.print("\" url=\"");
                writer.print(encode(data.url));
            }
            writer.println("\"/>");
        }
    }

    protected String encode(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\"", "&quot;").replaceAll("<", "&lt;").
                replaceAll(">", "&gt;").replaceAll("&", "&amp;");
    }
}
