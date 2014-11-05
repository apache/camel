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
import static org.apache.camel.util.ObjectHelper.isNotEmpty;
/**
 * A <a href="http://www.graphviz.org/">DOT</a> file creator plugin which
 * creates a DOT file showing the current routes
 *
 * @version 
 */
@Deprecated
public class RouteDotGenerator extends GraphGeneratorSupport {

    public RouteDotGenerator(String dir) {
        super(dir, ".dot");
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void printRoutes(PrintWriter writer, Map<String, List<RouteDefinition>> map) {
        Set<Map.Entry<String, List<RouteDefinition>>> entries = map.entrySet();
        for (Map.Entry<String, List<RouteDefinition>> entry : entries) {
            String group = entry.getKey();
            printRoutes(writer, group, entry.getValue());
        }
    }

    protected void printRoutes(PrintWriter writer, String group, List<RouteDefinition> routes) {
        if (group != null) {
            writer.println("subgraph cluster_" + (clusterCounter++) + " {");
            writer.println("label = \"" + group + "\";");
            writer.println("color = grey;");
            writer.println("style = \"dashed\";");
            writer.println("URL = \"" + group + ".html\";");
            writer.println();
        }
        for (RouteDefinition route : routes) {
            List<FromDefinition> inputs = route.getInputs();
            for (FromDefinition input : inputs) {
                printRoute(writer, route, input);
            }
            writer.println();
        }
        if (group != null) {
            writer.println("}");
            writer.println();
        }
    }

    protected void printRoute(PrintWriter writer, final RouteDefinition route, FromDefinition input) {
        NodeData nodeData = getNodeData(input);

        printNode(writer, nodeData);

        NodeData from = nodeData;
        for (ProcessorDefinition<?> output : route.getOutputs()) {
            NodeData newData = printNode(writer, from, output);
            from = newData;
        }
    }

    protected NodeData printNode(PrintWriter writer, NodeData fromData, ProcessorDefinition<?> node) {
        if (node instanceof MulticastDefinition) {
            // no need for a multicast or interceptor node
            List<ProcessorDefinition<?>> outputs = node.getOutputs();
            boolean isPipeline = isPipeline(node);
            for (ProcessorDefinition<?> output : outputs) {
                NodeData out = printNode(writer, fromData, output);
                // if in pipeline then we should move the from node to the next in the pipeline
                if (isPipeline) {
                    fromData = out;
                }
            }
            return fromData;
        }
        NodeData toData = getNodeData(node);

        printNode(writer, toData);

        if (fromData != null) {
            writer.print(fromData.id);
            writer.print(" -> ");
            writer.print(toData.id);
            writer.println(" [");

            String label = fromData.edgeLabel;
            if (isNotEmpty(label)) {
                writer.println("label = \"" + label + "\"");
            }
            writer.println("];");
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
            writer.print(data.id);
            writer.println(" [");
            writer.println("label = \"" + data.label + "\"");
            writer.println("tooltip = \"" + data.tooltop + "\"");
            if (data.url != null) {
                writer.println("URL = \"" + data.url + "\"");
            }

            String image = data.image;
            if (image != null) {
                writer.println("shapefile = \"" + image + "\"");
                writer.println("peripheries=0");
            }
            String shape = data.shape;
            if (shape == null && image != null) {
                shape = "custom";
            }
            if (shape != null) {
                writer.println("shape = \"" + shape + "\"");
            }
            writer.println("];");
            writer.println();
        }
    }

    protected void generateFile(PrintWriter writer, Map<String, List<RouteDefinition>> map) {
        writer.println("digraph CamelRoutes {");
        writer.println();

        writer.println("node [style = \"rounded,filled\", fillcolor = yellow, "
                + "fontname=\"Helvetica-Oblique\"];");
        writer.println();
        printRoutes(writer, map);

        writer.println("}");
    }


}
