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
import org.apache.camel.model.InterceptorRef;
import org.apache.camel.model.MulticastType;
import org.apache.camel.model.PipelineType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.ToType;

import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
/**
 * A <a href="http://www.graphviz.org/">DOT</a> file creator plugin which
 * creates a DOT file showing the current routes
 *
 * @version $Revision$
 */
public class RouteDotGenerator extends GraphGeneratorSupport {

    public RouteDotGenerator(String dir) {
        super(dir, ".dot");
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void printRoutes(PrintWriter writer, Map<String, List<RouteType>> map) {
        Set<Map.Entry<String, List<RouteType>>> entries = map.entrySet();
        for (Map.Entry<String, List<RouteType>> entry : entries) {
            String group = entry.getKey();
            printRoutes(writer, group, entry.getValue());
        }
    }

    protected void printRoutes(PrintWriter writer, String group, List<RouteType> routes) {
        if (group != null) {
            writer.println("subgraph cluster_" + (clusterCounter++) + " {");
            writer.println("label = \"" + group + "\";");
            writer.println("color = grey;");
            writer.println("style = \"dashed\";");
            writer.println("URL = \"" + group + ".html\";");
            writer.println();
        }
        for (RouteType route : routes) {
            List<FromType> inputs = route.getInputs();
            for (FromType input : inputs) {
                printRoute(writer, route, input);
            }
            writer.println();
        }
        if (group != null) {
            writer.println("}");
            writer.println();
        }
    }

    protected String escapeNodeId(String text) {
        return text.replace('.', '_').replace("$", "_");
    }

    protected void printRoute(PrintWriter writer, final RouteType route, FromType input) {
        NodeData nodeData = getNodeData(input);

        printNode(writer, nodeData);

        // TODO we should add a transactional client / event driven consumer / polling client

        NodeData from = nodeData;
        for (ProcessorType output : route.getOutputs()) {
            NodeData newData = printNode(writer, from, output);
            from = newData;
        }
    }

    protected NodeData printNode(PrintWriter writer, NodeData fromData, ProcessorType node) {
        if (node instanceof MulticastType || node instanceof InterceptorRef) {
            // no need for a multicast or interceptor node
            List<ProcessorType> outputs = node.getOutputs();
            boolean isPipeline = isPipeline(node);
            for (ProcessorType output : outputs) {
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
            if (isNotNullAndNonEmpty(label)) {
                writer.println("label = \"" + label + "\"");
            }
            writer.println("];");
        }

        // now lets write any children
        //List<ProcessorType> outputs = node.getOutputs();
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

    protected void generateFile(PrintWriter writer, Map<String, List<RouteType>> map) {
        writer.println("digraph CamelRoutes {");
        writer.println();

        writer.println("node [style = \"rounded,filled\", fillcolor = yellow, "
                + "fontname=\"Helvetica-Oblique\"];");
        writer.println();
        printRoutes(writer, map);

        writer.println("}");
    }

    /**
     * Is the given node a pipeline
     */
    private static boolean isPipeline(ProcessorType node) {
        if (node instanceof MulticastType) {
            return false;
        }
        if (node instanceof PipelineType) {
            return true;
        }
        if (node.getOutputs().size() > 1) {
            // is pipeline if there is more than 1 output and they are all To types
            for (Object type : node.getOutputs()) {
                if (!(type instanceof ToType)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

}
