/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;

/**
 * @version $Revision: 1.1 $
 */
public class XmlGraphGenerator extends GraphGeneratorSupport {
    public XmlGraphGenerator(String dir) {
        super(dir, ".xml");
    }

    protected void generateFile(PrintWriter writer, Map<String, List<RouteType>> map) {
        writer.println("<Graph>");
        writer.println();

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
        if (group != null) {
            // TODO
        }
        for (RouteType route : routes) {
            List<FromType> inputs = route.getInputs();
            for (FromType input : inputs) {
                printRoute(writer, route, input);
            }
            writer.println();
        }
    }



    protected void printRoute(PrintWriter writer, final RouteType route, FromType input) {
        NodeData nodeData = getNodeData(input);

        printNode(writer, nodeData);

        // TODO we should add a transactional client / event driven consumer / polling client

        List<ProcessorType> outputs = route.getOutputs();
        NodeData from = nodeData;
        for (ProcessorType output : outputs) {
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
            writer.print("<Edge fromID='");
            writer.print(fromData.id);
            writer.print("' toID='");
            writer.print(toData.id);
            writer.print("' association='");
            writer.print(toData.association);
            writer.println("'/>");
        }

        // now lets write any children
        List<ProcessorType> outputs = toData.outputs;
        for (ProcessorType output : outputs) {
            NodeData newData = printNode(writer, toData, output);
            if (!isMulticastNode(node)) {
                toData = newData;
            }
        }
        return toData;
    }

    protected void printNode(PrintWriter writer, NodeData data) {
        if (!data.nodeWritten) {
            data.nodeWritten = true;

            // <Node id="iedge" name="IEdge" nodeType="PrimitiveCircle" description=""   />
            writer.println();
            writer.print("<Node id='");
            writer.print(data.id);
            writer.print("' name='");
            writer.print(data.label);
            writer.print("' nodeType='");
            String nodeType = data.image;
            if (isNullOrBlank(nodeType)) {
                nodeType = data.shape;
                if (isNullOrBlank(nodeType)) {
                    nodeType = "PrimitiveReverseBurst";
                }            }
            writer.print(nodeType);
            writer.print("' description='");
            writer.print(data.tooltop);
            writer.print("' url='");
            writer.print(data.url);
            writer.println("'/>");
        }
    }

}
