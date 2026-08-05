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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.mcp.server.vertx.VertxMcpServerEngine;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Dev/diagnostics MCP server on the management HTTP port, exposing shared {@link ToolRegistry} tools through
 * {@link VertxMcpServerEngine}.
 */
public class JbangDevMcpServer extends ServiceSupport implements CamelContextAware {

    private static final String SERVER_NAME = "camel-jbang-dev-tools";

    private CamelContext camelContext;
    private String path = "/mcp";
    private VertxMcpServerEngine engine;
    private ToolContext toolContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    protected void doStart() throws Exception {
        toolContext = new ToolContext();
        toolContext.selectProcess(ProcessHandle.current().pid());

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.setTargetServerType(VertxPlatformHttpRouter.SERVER_TYPE_MANAGEMENT);
        String version = camelContext.getVersion();
        if (version == null || version.isBlank()) {
            version = "unknown";
        }
        engine.initialize(new McpServerInfo(SERVER_NAME, version, path));
        engine.start();

        for (ToolDescriptor descriptor : ToolRegistry.allTools()) {
            engine.toolAdded(toMcpTool(descriptor));
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        toolContext = null;
    }

    private McpServerTool toMcpTool(ToolDescriptor descriptor) {
        McpToolCallHandler handler = arguments -> {
            try {
                Object result = ToolRegistry.execute(descriptor.name(), toolContext, stringArguments(arguments));
                return new McpToolCallResult(result != null ? result.toString() : "", false);
            } catch (ToolExecutionException e) {
                return new McpToolCallResult(e.getMessage(), true);
            }
        };
        return new McpServerTool() {
            @Override
            public String name() {
                return descriptor.name();
            }

            @Override
            public String description() {
                return descriptor.description();
            }

            @Override
            public String inputSchemaJson() {
                return descriptor.params().isEmpty() ? null : ToolMcpSchemas.inputSchemaJson(descriptor);
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }
        };
    }

    private static Map<String, String> stringArguments(Map<String, Object> args) {
        Map<String, String> out = new LinkedHashMap<>();
        if (args == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getValue() != null) {
                out.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return out;
    }
}
