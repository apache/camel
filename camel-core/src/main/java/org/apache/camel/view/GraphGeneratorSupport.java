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

import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.MulticastType;
import org.apache.camel.model.ChoiceType;
import org.apache.camel.model.FromType;
import org.apache.camel.model.ToType;
import org.apache.camel.model.language.ExpressionType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision: 1.1 $
 */
public abstract class GraphGeneratorSupport {
    private static final transient Log LOG = LogFactory.getLog(RouteDotGenerator.class);
    protected String dir;
    private String imagePrefix = "http://www.enterpriseintegrationpatterns.com/img/";
    private Map<Object, NodeData> nodeMap = new HashMap<Object, NodeData>();
    private boolean makeParentDirs = true;
    protected int clusterCounter;
    private Map<String, List<RouteType>> routeGroupMap;
    protected String extension;

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

    public void drawRoutes(CamelContext context) throws IOException {
        File parent = new File(dir);
        if (makeParentDirs) {
            parent.mkdirs();
        }
        List<RouteType> routes = context.getRouteDefinitions();
        routeGroupMap = createRouteGroupMap(routes);

        // generate the global file
        generateFile(parent, "routes" + extension, routeGroupMap);

        if (routeGroupMap.size() >= 1) {
            Set<Map.Entry<String, List<RouteType>>> entries = routeGroupMap.entrySet();
            for (Map.Entry<String, List<RouteType>> entry : entries) {

                Map<String, List<RouteType>> map = new HashMap<String, List<RouteType>>();
                String group = entry.getKey();
                map.put(group, entry.getValue());

                // generate the file containing just the routes in this group
                generateFile(parent, group + extension, map);
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

    protected abstract void generateFile(PrintWriter writer, Map<String, List<RouteType>> map);

    protected boolean isMulticastNode(ProcessorType node) {
        return node instanceof MulticastType || node instanceof ChoiceType;
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
            String id = "node" + (nodeMap.size() + 1);
            answer = new NodeData(id, node, imagePrefix);
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
