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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.util.CollectionStringBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for Graph processing code of Camel EIPs containing a number of helper methods
 *
 * @version 
 */
@Deprecated
public class GraphSupport {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Map<Object, NodeData> nodeMap = new HashMap<Object, NodeData>();
    private String imagePrefix = "http://camel.apache.org/images/eip/";

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
        NodeData answer = null;
        if (key != null) {
            answer = nodeMap.get(key);
        }
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

    protected boolean isMulticastNode(ProcessorDefinition<?> node) {
        return node instanceof MulticastDefinition || node instanceof ChoiceDefinition;
    }

    /**
     * Is the given node a pipeline
     */
    protected boolean isPipeline(ProcessorDefinition<?> node) {
        if (node instanceof MulticastDefinition) {
            return false;
        }
        if (node instanceof PipelineDefinition) {
            return true;
        }
        if (node.getOutputs().size() > 1) {
            // is pipeline if there is more than 1 output and they are all To types
            for (Object type : node.getOutputs()) {
                if (!(type instanceof ToDefinition)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public String getImagePrefix() {
        return imagePrefix;
    }

    public void setImagePrefix(String imagePrefix) {
        this.imagePrefix = imagePrefix;
    }
}
