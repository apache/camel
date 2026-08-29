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
package org.apache.camel.component.mcp.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Mock {@link McpServerEngine} recording the tools and resources published by the bridge, for engine-less bridge tests.
 */
public class RecordingMcpServerEngine extends ServiceSupport implements McpServerEngine {

    private final Map<String, McpServerTool> tools = new ConcurrentHashMap<>();
    private final Map<String, McpServerResource> resources = new ConcurrentHashMap<>();
    private final List<String> removed = new CopyOnWriteArrayList<>();
    private final List<String> removedResources = new CopyOnWriteArrayList<>();
    private final boolean resourcesSupported;

    public RecordingMcpServerEngine() {
        this(true);
    }

    public RecordingMcpServerEngine(boolean resourcesSupported) {
        this.resourcesSupported = resourcesSupported;
    }

    private CamelContext camelContext;
    private McpServerInfo info;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void initialize(McpServerInfo info) {
        this.info = info;
    }

    @Override
    public void toolAdded(McpServerTool tool) {
        tools.put(tool.name(), tool);
    }

    @Override
    public void toolRemoved(String toolName) {
        tools.remove(toolName);
        removed.add(toolName);
    }

    @Override
    public void resourceAdded(McpServerResource resource) {
        resources.put(resource.uri(), resource);
    }

    @Override
    public void resourceRemoved(String resourceUri) {
        resources.remove(resourceUri);
        removedResources.add(resourceUri);
    }

    @Override
    public boolean supportsResources() {
        return resourcesSupported;
    }

    public Map<String, McpServerTool> tools() {
        return tools;
    }

    public Map<String, McpServerResource> resources() {
        return resources;
    }

    public List<String> removedResources() {
        return removedResources;
    }

    public List<String> removed() {
        return removed;
    }

    public McpServerInfo info() {
        return info;
    }
}
