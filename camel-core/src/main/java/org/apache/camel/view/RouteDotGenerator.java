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
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.*;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
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
    private String file;
    private String imagePrefix = "http://www.enterpriseintegrationpatterns.com/img/";
    private Map<Object, NodeData> nodeMap = new HashMap<Object, NodeData>();
    private boolean makeParentDirs = true;

    public RouteDotGenerator() {
        this("CamelRoutes.dot");
    }

    public RouteDotGenerator(String file) {
        this.file = file;
    }

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
        File fileValue = new File(file);
        if (makeParentDirs) {
            fileValue.getParentFile().mkdirs();
        }
        PrintWriter writer = new PrintWriter(new FileWriter(fileValue));
        try {
            generateFile(writer, context);
        }
        finally {
            writer.close();
        }
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected class NodeData {
        public String id;
        public String image;
        public String label;
        public String edgeLabel;
        public String tooltop;
        public String nodeType;
        public boolean nodeWritten;
        public String url;
    }

    protected void generateFile(PrintWriter writer, CamelContext context) {
        writer.println("digraph \"CamelRoutes\" {");
        writer.println();

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

    protected void printRoute(PrintWriter writer, final RouteType route, FromType input) {
        NodeData nodeData = getNodeData(input);

        printNode(writer, nodeData);

        // TODO we should add a transactional client / event driven consumer / polling client

        List<ProcessorType> outputs = route.getOutputs();
        for (ProcessorType output : outputs) {
            printNode(writer, nodeData, output);
        }
    }

    protected NodeData printNode(PrintWriter writer, NodeData fromData, ProcessorType node) {
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
        List<ProcessorType> outputs = node.getOutputs();
        for (ProcessorType output : outputs) {
            NodeData newData = printNode(writer, toData, output);
            if (!(node instanceof MulticastType)) {
                toData = newData;
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
                writer.println("shape = custom");
                writer.println("peripheries=0");
            }
            writer.println("];");
            writer.println();
        }
    }

    protected void configureNodeData(Object node, NodeData data) {
        if (node instanceof FromType) {
            FromType fromType = (FromType) node;
            data.label = fromType.getRef();
            if (isNullOrBlank(data.label)) {
                data.label = fromType.getUri();
            }
            data.url = "http://activemq.apache.org/camel/message-endpoint.html";
        }
        else if (node instanceof ToType) {
            ToType toType = (ToType) node;
            String ref = toType.getRef();
            if (isNullOrBlank(ref)) {
                ref = toType.getUri();
            }
            data.label = ref;
            data.url = "http://activemq.apache.org/camel/message-endpoint.html";
        }
        else if (node instanceof FilterType) {
            FilterType filterType = (FilterType) node;
            data.image = imagePrefix + "MessageFilterIcon.gif";
            data.edgeLabel = getLabel(filterType.getExpression());
            data.nodeType = "Message Filter";
            data.url = "";
        }
        else if (node instanceof ChoiceType) {
            ChoiceType choiceType = (ChoiceType) node;
            data.image = imagePrefix + "ContentBasedRouterIcon.gif";
            CollectionStringBuffer buffer = new CollectionStringBuffer();
            List<WhenType> list = choiceType.getWhenClauses();
            for (WhenType whenType : list) {
                buffer.append(getLabel(whenType.getExpression()));
            }
            data.edgeLabel = buffer.toString();
            data.nodeType = "Content Based Router";
        }
        else if (node instanceof RecipientListType) {
            RecipientListType recipientListType = (RecipientListType) node;
            data.image = imagePrefix + "RecipientListIcon.gif";
            data.edgeLabel = getLabel(recipientListType.getExpression());
            data.nodeType = "Recipient List";
        }
        else if (node instanceof SplitterType) {
            SplitterType splitterType = (SplitterType) node;
            data.image = imagePrefix + "SplitterIcon.gif";
            data.edgeLabel = getLabel(splitterType.getExpression());
            data.nodeType = "Splitter";
        }
        else if (node instanceof AggregatorType) {
            AggregatorType aggregatorType = (AggregatorType) node;
            data.image = imagePrefix + "AggregatorIcon.gif";
            data.edgeLabel = getLabel(aggregatorType.getExpression());
            data.nodeType = "Aggregator";
        }
        else if (node instanceof ResequencerType) {
            ResequencerType resequencerType = (ResequencerType) node;
            data.image = imagePrefix + "ResequencerIcon.gif";
            data.edgeLabel = getLabel(resequencerType.getExpressions());
            data.nodeType = "Resequencer";
        }

        // lets auto-default as many values as we can
        if (data.label == null) {
            if (isNotNullAndNonEmpty(data.edgeLabel)) {
                data.label = "";
            }
            else {
                data.label = node.toString();
            }
        }
        if (isNullOrBlank(data.tooltop)) {
            if (isNotNullAndNonEmpty(data.nodeType)) {
                String description = isNotNullAndNonEmpty(data.edgeLabel) ? data.edgeLabel : data.label;
                data.tooltop = data.nodeType + ": " + description;
            }
            else {
                data.tooltop = data.label;
            }
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
                Expression expressionValue = expression.getExpressionValue();
                if (expressionValue != null) {
                    return expressionValue.toString();
                }
            }
            else {
                return language;
            }
        }
        return "";
    }

    protected NodeData getNodeData(Object node) {
        Object key = node;
        if (node instanceof FromType) {
            FromType fromType = (FromType) node;
            key = fromType.getUriOrRef();
        }
        else if (node instanceof ToType) {
            ToType toType = (ToType) node;
            key = toType.getUriOrRef();
        }
        NodeData answer = nodeMap.get(key);
        if (answer == null) {
            answer = new NodeData();
            answer.id = "node" + (nodeMap.size() + 1);
            configureNodeData(node, answer);
            nodeMap.put(key, answer);
        }
        return answer;
    }
}
