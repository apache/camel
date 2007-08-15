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

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.model.*;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A <a href="http://www.graphviz.org/">DOT</a> file creator plugin which
 * creates a DOT file showing the current routes
 *
 * @version $Revision: 523881 $
 */
public class RouteDotGenerator {
    private static final transient Log LOG = LogFactory.getLog(RouteDotGenerator.class);
    private String file = "CamelRoutes.dot";
    private String imagePrefix = "http://www.enterpriseintegrationpatterns.com/img/";
    private Map<Object, String> idMap = new HashMap<Object, String>();

    public String getFile() {
        return file;
    }

    /**
     * Sets the destination file name to create the destination diagram
     */
    public void setFile(String file) {
        this.file = file;
    }

    public void drawRoutes(CamelContext context) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(file));
        try {
            generateFile(writer, context);
        }
        finally {
            writer.close();
        }
    }

    protected void generateFile(PrintWriter writer, CamelContext context) {
        writer.println("digraph \"Camel Routes\" {");
        writer.println();
        /*writer.println("label=\"Camel Context: " + context + "\"];");
        writer.println();
        */
        writer.println("node [style = \"rounded,filled\", fillcolor = yellow, "
                + "fontname=\"Helvetica-Oblique\"];");
        writer.println();
        printRoutes(writer, context.getRouteDefinitions());

        writer.println("}");
    }

    protected void printRoutes(PrintWriter writer, List<RouteType> routes) {
        for (RouteType route : routes) {
            List<FromType> inputs = route.getInputs();
            for (FromType input : inputs) {
                printRoute(writer, route, input);
            }
            writer.println();
        }
    }

    protected void printRoute(PrintWriter writer, RouteType route, FromType input) {
        String ref = input.getRef();
        if (isNullOrBlank(ref)) {
            ref = input.getUri();
        }
        String fromID = getID(ref);

        writer.println();
        writer.print(fromID);
        writer.println(" [");
        writer.println("label = \"" + ref + "\"");
        writer.println("];");
        writer.println();

        // TODO we should add a transactional client / event driven consumer / polling client

        List<ProcessorType> outputs = route.getOutputs();
        for (ProcessorType output : outputs) {
            printNode(writer, fromID, output);
        }
    }

    protected String printNode(PrintWriter writer, String fromID, ProcessorType node) {
        String toID = getID(node);

        writer.println();
        writer.print(toID);
        writer.println(" [");
        printNodeAttributes(writer, node, fromID);
        writer.println("];");
        writer.println();

        writer.print(fromID);
        writer.print(" -> ");
        writer.print(toID);
        writer.println(" [");
        // TODO customize the line!
        writer.println("];");

        // now lets write any children
        List<ProcessorType> outputs = node.getOutputs();
        for (ProcessorType output : outputs) {
            printNode(writer, toID, output);
        }
        return toID;
    }

    protected class NodeData {
        public String image;
        public String label;
    }

    protected void printNodeAttributes(PrintWriter writer, ProcessorType node, String id) {
        NodeData nodeData = new NodeData();
        configureNodeData(node, nodeData);

        String label = nodeData.label;
        if (label == null) {
            label = node.toString();
        }
        writer.println("label = \"" + label + "\"");

        String image = nodeData.image;
        if (image != null) {
            writer.println("shapefile = \"" + image + "\"");
            writer.println("shape = custom");
        }
    }

    protected void configureNodeData(ProcessorType node, NodeData nodeData) {
        if (node instanceof ToType) {
            ToType toType = (ToType) node;
            String ref = toType.getRef();
            if (isNullOrBlank(ref)) {
                ref = toType.getUri();
            }
            nodeData.label = ref;
        }
        else if (node instanceof FilterType) {
            FilterType filterType = (FilterType) node;
            nodeData.image = imagePrefix + "MessageFilterIcon.gif";
            nodeData.label = getLabel(filterType.getExpression());
        }
        else if (node instanceof ChoiceType) {
            ChoiceType choiceType = (ChoiceType) node;
            nodeData.image = imagePrefix + "ContentBasedRouterIcon.gif";
            CollectionStringBuffer buffer = new CollectionStringBuffer();
            List<WhenType> list = choiceType.getWhenClauses();
            for (WhenType whenType : list) {
                buffer.append(getLabel(whenType.getExpression()));
            }
            nodeData.label = buffer.toString();
        }
        else if (node instanceof RecipientListType) {
            RecipientListType recipientListType = (RecipientListType) node;
            nodeData.image = imagePrefix + "RecipientListIcon.gif";
            nodeData.label = getLabel(recipientListType.getExpression());
        }
        else if (node instanceof SplitterType) {
            SplitterType splitterType = (SplitterType) node;
            nodeData.image = imagePrefix + "SplitterIcon.gif";
            nodeData.label = getLabel(splitterType.getExpression());
        }
        else if (node instanceof AggregatorType) {
            AggregatorType aggregatorType = (AggregatorType) node;
            nodeData.image = imagePrefix + "AggregatorIcon.gif";
            nodeData.label = getLabel(aggregatorType.getExpression());
        }
        else if (node instanceof ResequencerType) {
            ResequencerType resequencerType = (ResequencerType) node;
            nodeData.image = imagePrefix + "ResequencerIcon.gif";
            nodeData.label = getLabel(resequencerType.getExpressions());
        }
    }

    protected String getLabel(List<ExpressionType> expressions) {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        for (ExpressionType expression : expressions) {
            buffer.append(getLabel(expression));
        }
        return buffer.toString();
    }

    protected String getLabel(ExpressionType expression) {
        if (expression != null) {
            String language = expression.getExpression();
            if (ObjectHelper.isNullOrBlank(language)) {
                Predicate predicate = expression.getPredicate();
                if (predicate != null) {
                    return predicate.toString();
                }
            }
            else {
                return language;
            }
        }
        return "";
    }

    protected String getID(Object node) {
        String answer = idMap.get(node);
        if (answer == null) {
            answer = "node" + (idMap.size() + 1);
            idMap.put(node, answer);
        }
        return answer;
    }
}
