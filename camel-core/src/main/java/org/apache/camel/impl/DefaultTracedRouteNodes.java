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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.RouteNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.TracedRouteNodes;

/**
 * Default {@link org.apache.camel.spi.TracedRouteNodes}
 *
 * @deprecated use {@link Exchange#MESSAGE_HISTORY} instead.
 */
@Deprecated
public class DefaultTracedRouteNodes implements TracedRouteNodes {
    private final Stack<List<RouteNode>> routeNodes = new Stack<List<RouteNode>>();
    private final Map<ProcessorDefinition<?>, AtomicInteger> nodeCounter = new HashMap<ProcessorDefinition<?>, AtomicInteger>();

    public DefaultTracedRouteNodes() {
        // create an empty list to start with
        routeNodes.push(new ArrayList<RouteNode>());
    }

    public void addTraced(RouteNode entry) {
        List<RouteNode> list = routeNodes.isEmpty() ? null : routeNodes.peek();
        if (list == null) {
            list = new ArrayList<RouteNode>();
            routeNodes.push(list);
        }
        list.add(entry);
    }

    public RouteNode getLastNode() {
        List<RouteNode> list = routeNodes.isEmpty() ? null : routeNodes.peek();
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    public RouteNode getSecondLastNode() {
        List<RouteNode> list = routeNodes.isEmpty() ? null : routeNodes.peek();
        if (list == null || list.isEmpty() || list.size() == 1) {
            return null;
        }
        return list.get(list.size() - 2);
    }

    public List<RouteNode> getNodes() {
        List<RouteNode> answer = new ArrayList<RouteNode>();
        for (List<RouteNode> list : routeNodes) {
            answer.addAll(list);
        }
        return Collections.unmodifiableList(answer);
    }

    public void popBlock() {
        if (!routeNodes.isEmpty()) {
            routeNodes.pop();
        }
    }

    public void pushBlock() {
        // push a new block and add the last node as starting point
        RouteNode last = getLastNode();
        routeNodes.push(new ArrayList<RouteNode>());
        if (last != null) {
            addTraced(last);
        }
    }

    public void clear() {
        routeNodes.clear();
    }

    public int getAndIncrementCounter(ProcessorDefinition<?> node) {
        AtomicInteger count = nodeCounter.get(node);
        if (count == null) {
            count = new AtomicInteger();
            nodeCounter.put(node, count);
        }
        return count.getAndIncrement();
    }

}
