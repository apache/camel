/*
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
package org.apache.camel.parser.model;

import java.util.List;

/**
 * Represents coverage data of a given node in the Camel route.
 */
public class CoverageData {

    private final String nodeId;
    private final String node;
    private final int count;
    private String routeId;
    private int lineNumber;
    private List<String> bodyTypes = List.of();
    private List<String> bodyKinds = List.of();

    public CoverageData(String nodeId, String node, int count, String routeId) {
        this.nodeId = nodeId;
        this.node = node;
        this.count = count;
        this.routeId = routeId;
    }

    public String getNode() {
        return node;
    }

    public int getCount() {
        return count;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * The body types the node has been reached with, in the order first seen (empty when the route coverage dump has
     * none, as when the tests ran without the message history).
     */
    public List<String> getBodyTypes() {
        return bodyTypes;
    }

    public void setBodyTypes(List<String> bodyTypes) {
        this.bodyTypes = bodyTypes != null ? bodyTypes : List.of();
    }

    /**
     * The kinds of the bodies the node has been reached with: null, payload, data or collection. More than one means
     * the node needs attention.
     */
    public List<String> getBodyKinds() {
        return bodyKinds;
    }

    public void setBodyKinds(List<String> bodyKinds) {
        this.bodyKinds = bodyKinds != null ? bodyKinds : List.of();
    }
}
