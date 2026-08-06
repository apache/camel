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
package org.apache.camel.component.mcp.server.jbang;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.mcp.server.vertx.VertxMcpServerEngine;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev/diagnostics MCP server on the management HTTP port, exposing shared JBang {@code ToolRegistry} tools through
 * {@link VertxMcpServerEngine}. JBang classes are resolved reflectively so {@code camel-jbang-core} is not required at
 * compile time.
 */
public class JbangDevMcpServer extends ServiceSupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(JbangDevMcpServer.class);
    private static final String SERVER_NAME = "camel-jbang-dev-tools";
    private static final String TOOL_REGISTRY = "org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry";
    private static final String TOOL_CONTEXT = "org.apache.camel.dsl.jbang.core.commands.ai.ToolContext";

    private CamelContext camelContext;
    private String path = "/mcp";
    private VertxMcpServerEngine engine;
    private Object toolContext;

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
        toolContext = createToolContext();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.setTargetServerType(resolveTargetServerType());
        String version = camelContext.getVersion();
        if (version == null || version.isBlank()) {
            version = "unknown";
        }
        engine.initialize(new McpServerInfo(SERVER_NAME, version, path));
        engine.start();

        for (Object descriptor : allToolDescriptors()) {
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

    private McpServerTool toMcpTool(Object descriptor) {
        String toolName = invokeString(descriptor, "name");
        List<?> params = invokeList(descriptor, "params");
        Map<String, ParameterDef> parameterDefs = buildParameterDefs(params);
        McpToolCallHandler handler = arguments -> {
            try {
                Object result = executeTool(toolName, stringArguments(arguments));
                return new McpToolCallResult(result != null ? result.toString() : "", false);
            } catch (Exception e) {
                Throwable failure = e;
                if (e instanceof InvocationTargetException ite && ite.getCause() != null) {
                    failure = ite.getCause();
                }
                String message = failure.getMessage();
                if (message == null || message.isBlank()) {
                    message = failure.getClass().getSimpleName();
                }
                return new McpToolCallResult(message, true);
            }
        };
        return new McpServerTool() {
            @Override
            public String name() {
                return toolName;
            }

            @Override
            public String description() {
                return invokeString(descriptor, "description");
            }

            @Override
            public String inputSchemaJson() {
                return parameterDefs.isEmpty() ? null : AiToolParameterHelper.buildJsonSchemaFromDefs(parameterDefs);
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return parameterDefs;
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }
        };
    }

    private Object createToolContext() throws ReflectiveOperationException {
        Class<?> contextClass = mandatoryClass(TOOL_CONTEXT);
        Object context = contextClass.getDeclaredConstructor().newInstance();
        Method selectProcess = contextClass.getMethod("selectProcess", long.class);
        selectProcess.invoke(context, ProcessHandle.current().pid());
        return context;
    }

    @SuppressWarnings("unchecked")
    private List<Object> allToolDescriptors() throws ReflectiveOperationException {
        Class<?> registry = mandatoryClass(TOOL_REGISTRY);
        Method allTools = registry.getMethod("allTools");
        return (List<Object>) allTools.invoke(null);
    }

    private Object executeTool(String name, Map<String, String> args) throws ReflectiveOperationException {
        Class<?> registry = mandatoryClass(TOOL_REGISTRY);
        Class<?> contextClass = mandatoryClass(TOOL_CONTEXT);
        Method execute = registry.getMethod("execute", String.class, contextClass, Map.class);
        return execute.invoke(null, name, toolContext, args);
    }

    private Class<?> mandatoryClass(String name) throws ClassNotFoundException {
        return camelContext.getClassResolver().resolveMandatoryClass(name);
    }

    private static String invokeString(Object target, String method) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeList(Object target, String method) {
        try {
            return (List<?>) target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    private static Map<String, ParameterDef> buildParameterDefs(List<?> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, String> flat = new LinkedHashMap<>();
        for (Object param : params) {
            String name = invokeString(param, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String type = invokeString(param, "type");
            if (type != null && !type.isBlank()) {
                flat.put(name, type);
            }
            String description = invokeString(param, "description");
            if (description != null && !description.isBlank()) {
                flat.put(name + ".description", description);
            }
            if (Boolean.TRUE.equals(invokeBoolean(param, "required"))) {
                flat.put(name + ".required", "true");
            }
        }
        return AiToolParameterHelper.parseParameterMetadata(flat);
    }

    private static Boolean invokeBoolean(Object target, String method) {
        try {
            return (Boolean) target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return false;
        }
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

    private String resolveTargetServerType() {
        Set<VertxPlatformHttpRouter> routers = camelContext.getRegistry().findByType(VertxPlatformHttpRouter.class);
        boolean hasManagementRouter = routers.stream()
                .anyMatch(r -> VertxPlatformHttpRouter.SERVER_TYPE_MANAGEMENT.equals(r.getServerType()));
        if (hasManagementRouter) {
            return VertxPlatformHttpRouter.SERVER_TYPE_MANAGEMENT;
        }
        LOG.warn("No management HTTP router found; attaching JBang dev MCP endpoint to the main HTTP server. "
                 + "When management shares the main server port, MCP is served on the main server bind address "
                 + "(for example 0.0.0.0) rather than 127.0.0.1.");
        return VertxPlatformHttpRouter.SERVER_TYPE_SERVER;
    }
}
