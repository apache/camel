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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public abstract class GraphGeneratorSupport {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected String dir;
    protected int clusterCounter;
    protected String extension;

    //private String imagePrefix = "http://www.enterpriseintegrationpatterns.com/img/";
    private String imagePrefix = "http://camel.apache.org/images/eip/";
    private Map<Object, NodeData> nodeMap = new HashMap<Object, NodeData>();
    private boolean makeParentDirs = true;
    private Map<String, List<RouteDefinition>> routeGroupMap;

    protected GraphGeneratorSupport(String dir, String extension) {
        this.dir = dir;
        this.extension = extension;
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

    public String getImagePrefix() {
        return imagePrefix;
    }

    public void setImagePrefix(String imagePrefix) {
        this.imagePrefix = imagePrefix;
    }

    public String getRoutesText(CamelContext context) throws IOException {
        List<RouteDefinition> routes = context.getRouteDefinitions();
        routeGroupMap = createRouteGroupMap(routes);
        return createRouteMapText();
    }

    public String getRouteText(RouteDefinition route) throws IOException {
        routeGroupMap = createRouteGroupMap(route);
        return createRouteMapText();
    }

    private String createRouteMapText() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        generateFile(writer, routeGroupMap);
        writer.close();
        return buffer.toString();
    }

    public void drawRoutes(CamelContext context) throws IOException {
        File parent = new File(dir);
        if (makeParentDirs) {
            parent.mkdirs();
        }
        List<RouteDefinition> routes = context.getRouteDefinitions();
        routeGroupMap = createRouteGroupMap(routes);

        // generate the global file
        generateFile(parent, "routes" + extension, routeGroupMap);

        if (routeGroupMap.size() >= 1) {
            Set<Map.Entry<String, List<RouteDefinition>>> entries = routeGroupMap.entrySet();
            for (Map.Entry<String, List<RouteDefinition>> entry : entries) {

                Map<String, List<RouteDefinition>> map = new HashMap<String, List<RouteDefinition>>();
                String group = entry.getKey();
                map.put(group, entry.getValue());

                // generate the file containing just the routes in this group
                generateFile(parent, group + extension, map);
            }
        }
    }

    private void generateFile(File parent, String fileName, Map<String, List<RouteDefinition>> map) throws IOException {
        nodeMap.clear();
        clusterCounter = 0;

        PrintWriter writer = new PrintWriter(new FileWriter(new File(parent, fileName)));
        try {
            generateFile(writer, map);
        } finally {
            writer.close();
        }
    }

    protected abstract void generateFile(PrintWriter writer, Map<String, List<RouteDefinition>> map);

    protected boolean isMulticastNode(ProcessorDefinition node) {
        return node instanceof MulticastDefinition || node instanceof ChoiceDefinition;
    }

    protected String getLabel(List<ExpressionDefinition> expressions) {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        for (ExpressionDefinition expression : expressions) {
            buffer.append(getLabel(expression));
        }
        return buffer.toString();
    }

    protected String getLabel(ExpressionDefinition expression) {
        if (expression != null) {
            return expression.getLabel();
        }
        return "";
    }

    protected NodeData getNodeData(Object node) {
        Object key = node;
        if (node instanceof FromDefinition) {
            FromDefinition fromType = (FromDefinition) node;
            key = fromType.getUriOrRef();
        } else if (node instanceof ToDefinition) {
            ToDefinition toType = (ToDefinition) node;
            key = toType.getUriOrRef();
        }
        NodeData answer = nodeMap.get(key);
        if (answer == null) {
            String id = "node" + (nodeMap.size() + 1);
            answer = new NodeData(id, node, imagePrefix);
            nodeMap.put(key, answer);
        }
        return answer;
    }

    protected Map<String, List<RouteDefinition>> createRouteGroupMap(List<RouteDefinition> routes) {
        Map<String, List<RouteDefinition>> map = new HashMap<String, List<RouteDefinition>>();
        for (RouteDefinition route : routes) {
            addRouteToMap(map, route);
        }
        return map;
    }

    protected Map<String, List<RouteDefinition>> createRouteGroupMap(RouteDefinition route) {
        Map<String, List<RouteDefinition>> map = new HashMap<String, List<RouteDefinition>>();
        addRouteToMap(map, route);
        return map;
    }

    protected void addRouteToMap(Map<String, List<RouteDefinition>> map, RouteDefinition route) {
        String group = route.getGroup();
        if (group == null) {
            group = "Camel Routes";
        }
        List<RouteDefinition> list = map.get(group);
        if (list == null) {
            list = new ArrayList<RouteDefinition>();
            map.put(group, list);
        }
        list.add(route);
    }
}
