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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.model.*;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.util.CollectionStringBuffer;
import static org.apache.camel.util.ObjectHelper.isNotNullAndNonEmpty;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://www.graphviz.org/">DOT</a> file creator plugin which
 * creates a DOT file showing the current routes
 *
 * @version $Revision: 523881 $
 */
public class RouteDotGenerator {
    private static final transient Log LOG = LogFactory.getLog(RouteDotGenerator.class);
    private String dir;
    private String imagePrefix = "http://www.enterpriseintegrationpatterns.com/img/";
    private Map<Object, NodeData> nodeMap = new HashMap<Object, NodeData>();
    private boolean makeParentDirs = true;
    private int clusterCounter;
    private Map<String, List<RouteType>> routeGroupMap;

    /**
     * lets insert a space before each upper case letter after a lowercase
     *
     * @param name
     * @return
     */
    public static String insertSpacesBetweenCamelCase(String name) {
        boolean lastCharacterLowerCase = false;
        StringBuffer buffer = new StringBuffer();
        for (int i = 0, size = name.length(); i < size; i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (lastCharacterLowerCase) {
                    buffer.append(' ');
                }
                lastCharacterLowerCase = false;
            }
            else {
                lastCharacterLowerCase = true;
            }
            buffer.append(ch);
        }
        return buffer.toString();
    }

    public RouteDotGenerator() {
        this("CamelRoutes.dot");
    }

    public RouteDotGenerator(String dir) {
        this.dir = dir;
    }

    public String getDir() {
        return dir;
    }

    /**
     * Sets the destination directory in which to create the diagrams
     */
    public void setDir(String dir) {
        this.dir = dir;
    }

    public void drawRoutes(CamelContext context) throws IOException {
        File parent = new File(dir);
        if (makeParentDirs) {
            parent.mkdirs();
        }
        List<RouteType> routes = context.getRouteDefinitions();
        routeGroupMap = createRouteGroupMap(routes);

        // generate the global file
        generateFile(parent, "routes.dot", routeGroupMap);

        if (routeGroupMap.size() >= 1) {
            Set<Map.Entry<String, List<RouteType>>> entries = routeGroupMap.entrySet();
            for (Map.Entry<String, List<RouteType>> entry : entries) {

                Map<String, List<RouteType>> map = new HashMap<String, List<RouteType>>();
                String group = entry.getKey();
                map.put(group, entry.getValue());

                // generate the file containing just the routes in this group
                generateFile(parent, group + ".dot", map);
            }
        }
    }

    private void generateFile(File parent, String fileName, Map<String, List<RouteType>> map) throws IOException {
        nodeMap.clear();
        clusterCounter = 0;
                     
        PrintWriter writer = new PrintWriter(new FileWriter(new File(parent, fileName)));
        try {
            generateFile(writer, map);
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
        public String shape;
        public String edgeLabel;
        public String tooltop;
        public String nodeType;
        public boolean nodeWritten;
        public String url;
        public List<ProcessorType> outputs;
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
        for (ProcessorType output : outputs) {
            NodeData newData = printNode(writer, toData, output);
            if (!isMulticastNode(node)) {
                toData = newData;
            }
        }
        return toData;
    }

    protected boolean isMulticastNode(ProcessorType node) {
        return node instanceof MulticastType || node instanceof ChoiceType;
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

    protected void configureNodeData(Object node, NodeData data) {
        if (node instanceof ProcessorType) {
            ProcessorType processorType = (ProcessorType) node;
            data.edgeLabel = processorType.getLabel();
        }
        if (node instanceof FromType) {
            FromType fromType = (FromType) node;
            data.tooltop = fromType.getLabel();
            data.label = removeQueryString(data.tooltop);
            data.url = "http://activemq.apache.org/camel/message-endpoint.html";
        }
        else if (node instanceof ToType) {
            ToType toType = (ToType) node;
            data.tooltop = toType.getLabel();
            data.label = removeQueryString(data.tooltop);
            data.edgeLabel = "";
            data.url = "http://activemq.apache.org/camel/message-endpoint.html";
        }
        else if (node instanceof FilterType) {
            data.image = imagePrefix + "MessageFilterIcon.gif";
            data.nodeType = "Message Filter";
        }
        else if (node instanceof WhenType) {
            data.image = imagePrefix + "MessageFilterIcon.gif";
            data.nodeType = "When Filter";
            data.url = "http://activemq.apache.org/camel/content-based-router.html";
        }
        else if (node instanceof OtherwiseType) {
            data.nodeType = "Otherwise";
            data.edgeLabel = "";
            data.url = "http://activemq.apache.org/camel/content-based-router.html";
            data.tooltop = "Otherwise";
        }
        else if (node instanceof ChoiceType) {
            data.image = imagePrefix + "ContentBasedRouterIcon.gif";
            data.nodeType = "Content Based Router";
            data.label = "";
            data.edgeLabel = "";

            ChoiceType choice = (ChoiceType) node;
            List<ProcessorType> outputs = new ArrayList<ProcessorType>(choice.getWhenClauses());
            outputs.add(choice.getOtherwise());
            data.outputs = outputs;
        }
        else if (node instanceof RecipientListType) {
            data.image = imagePrefix + "RecipientListIcon.gif";
            data.nodeType = "Recipient List";
        }
        else if (node instanceof SplitterType) {
            data.image = imagePrefix + "SplitterIcon.gif";
            data.nodeType = "Splitter";
        }
        else if (node instanceof AggregatorType) {
            data.image = imagePrefix + "AggregatorIcon.gif";
            data.nodeType = "Aggregator";
        }
        else if (node instanceof ResequencerType) {
            data.image = imagePrefix + "ResequencerIcon.gif";
            data.nodeType = "Resequencer";
        }

        // lets auto-default as many values as we can
        if (isNullOrBlank(data.nodeType)) {
            // TODO we could add this to the model?
            String name = node.getClass().getName();
            int idx = name.lastIndexOf('.');
            if (idx > 0) {
                name = name.substring(idx + 1);
            }
            if (name.endsWith("Type")) {
                name = name.substring(0, name.length() - 4);
            }
            data.nodeType = insertSpacesBetweenCamelCase(name);
        }
        if (data.label == null) {
            if (isNullOrBlank(data.image)) {
                data.label = data.nodeType;
                data.shape = "box";
            }
            else if (isNotNullAndNonEmpty(data.edgeLabel)) {
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
        if (isNullOrBlank(data.url) && isNotNullAndNonEmpty(data.nodeType)) {
            data.url = "http://activemq.apache.org/camel/" + data.nodeType.toLowerCase().replace(' ', '-') + ".html";
        }
        if (node instanceof ProcessorType && data.outputs == null) {
            ProcessorType processorType = (ProcessorType) node;
            data.outputs = processorType.getOutputs();
        }
    }

    protected String removeQueryString(String text) {
        int idx = text.indexOf("?");
        if (idx <= 0) {
            return text;
        }
        else {
            return text.substring(0, idx);
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
            return expression.getLabel();
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



    protected Map<String, List<RouteType>> createRouteGroupMap(List<RouteType> routes) {
        Map<String, List<RouteType>> map = new HashMap<String, List<RouteType>>();
        for (RouteType route : routes) {
            String group = route.getGroup();
            if (group == null) {
                group = "Camel Routes";
            }
            List<RouteType> list = map.get(group);
            if (list == null) {
                list = new ArrayList<RouteType>();
                map.put(group, list);
            }
            list.add(route);
        }
        return map;
    }
}
