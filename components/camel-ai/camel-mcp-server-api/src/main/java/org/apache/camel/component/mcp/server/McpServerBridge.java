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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.StaticService;
import org.apache.camel.component.ai.tool.AiToolExecutor;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.ai.tool.AiToolRegistry;
import org.apache.camel.component.ai.tool.AiToolRegistryListener;
import org.apache.camel.component.ai.tool.AiToolResult;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the {@link AiToolRegistry} to an {@link McpServerEngine}: selects {@code ai-tool} routes by tag, publishes
 * them as MCP tools, and executes calls with a bounded timeout and sanitized error mapping.
 * <p>
 * Security notes:
 * <ul>
 * <li>Only tools whose tags intersect the configured {@code tags} are exposed. The untagged default pool is never
 * exposed — external MCP clients are untrusted senders and crossing that trust boundary is an explicit per-tool
 * opt-in.</li>
 * <li>MCP has a flat tool namespace: a tool whose name collides with an already published tool is refused with an ERROR
 * log, never silently replaced.</li>
 * <li>Raw route exception messages never reach the engine: execution failures map to a generic error message and the
 * cause is logged server-side.</li>
 * </ul>
 */
public class McpServerBridge extends ServiceSupport implements CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerBridge.class);

    private static final String GENERIC_EXECUTION_ERROR = "Tool execution failed";
    private static final String GENERIC_TIMEOUT_ERROR = "Tool execution timed out";

    private final McpServerConfiguration configuration;
    private final RegistryListener listener = new RegistryListener();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, AiToolSpec> published = new HashMap<>();

    private CamelContext camelContext;
    private McpServerEngine engine;
    private AiToolRegistry registry;
    private Set<String> selectedTags = Set.of();
    private ExecutorService executor;

    public McpServerBridge(McpServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public McpServerConfiguration getConfiguration() {
        return configuration;
    }

    public McpServerEngine getEngine() {
        return engine;
    }

    @Override
    protected void doInit() throws Exception {
        if (configuration.getTags() != null) {
            selectedTags = new LinkedHashSet<>(Arrays.asList(AiToolParameterHelper.splitTags(configuration.getTags())));
        }
        if (selectedTags.isEmpty()) {
            LOG.warn("No MCP tags configured: no ai-tool routes will be exposed as MCP tools. "
                     + "Set tags to opt-in the tools to expose.");
        }

        engine = resolveEngine();
        CamelContextAware.trySetCamelContext(engine, camelContext);

        String serverName = configuration.getServerName() != null ? configuration.getServerName() : camelContext.getName();
        engine.initialize(new McpServerInfo(serverName, camelContext.getVersion(), configuration.getPath()));

        if (!engine.consumesServingConfiguration()) {
            if (!McpServerConstants.DEFAULT_PATH.equals(configuration.getPath())) {
                LOG.warn("The MCP path option is ignored by engine {}: the runtime's native MCP server configuration "
                         + "decides the endpoint path",
                        engine.getClass().getSimpleName());
            }
            if (configuration.getServerName() != null) {
                LOG.warn("The MCP serverName option may be ignored by engine {}: the runtime's native MCP server "
                         + "configuration decides the server identity",
                        engine.getClass().getSimpleName());
            }
        }

        ServiceHelper.initService(engine);
    }

    @Override
    protected void doStart() throws Exception {
        executor = camelContext.getExecutorServiceManager().newCachedThreadPool(this, "McpServerToolCall");
        ServiceHelper.startService(engine);

        registry = AiToolRegistry.getOrCreate(camelContext);
        // subscribe before snapshotting so no concurrent registration is missed; publishing is idempotent
        registry.addListener(listener);
        registry.getTools().forEach((tag, specs) -> {
            if (selectedTags.contains(tag)) {
                specs.forEach(this::publish);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        if (registry != null) {
            registry.removeListener(listener);
        }
        lock.lock();
        try {
            published.clear();
        } finally {
            lock.unlock();
        }
        ServiceHelper.stopService(engine);
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(executor);
            executor = null;
        }
    }

    private McpServerEngine resolveEngine() {
        McpServerEngine answer = camelContext.getRegistry().findSingleByType(McpServerEngine.class);
        if (answer == null) {
            answer = ResolverHelper.resolveMandatoryService(camelContext, McpServerConstants.MCP_SERVER_ENGINE_FACTORY,
                    McpServerEngine.class, "camel-mcp-server-engine-vertx");
        }
        return answer;
    }

    private void publish(AiToolSpec spec) {
        McpServerTool tool = null;
        lock.lock();
        try {
            AiToolSpec existing = published.get(spec.getName());
            if (existing == spec) {
                return;
            }
            if (existing != null) {
                LOG.error("Refusing to expose MCP tool '{}': the name collides with an already exposed tool. "
                          + "MCP has a flat tool namespace - rename one of the ai-tool routes.",
                        spec.getName());
                return;
            }
            published.put(spec.getName(), spec);
            tool = createTool(spec);
        } finally {
            lock.unlock();
        }
        engine.toolAdded(tool);
    }

    private void unpublish(AiToolSpec spec) {
        boolean removed = false;
        lock.lock();
        try {
            if (published.get(spec.getName()) != spec) {
                return;
            }
            // the same spec may be registered under several selected tags; only remove when it is gone from all
            boolean stillSelected = registry.getTools().entrySet().stream()
                    .anyMatch(e -> selectedTags.contains(e.getKey()) && e.getValue().contains(spec));
            if (!stillSelected) {
                published.remove(spec.getName());
                removed = true;
            }
        } finally {
            lock.unlock();
        }
        if (removed) {
            engine.toolRemoved(spec.getName());
        }
    }

    private McpServerTool createTool(AiToolSpec spec) {
        McpToolCallHandler handler = arguments -> execute(spec, arguments);
        return new McpServerTool() {
            @Override
            public String name() {
                return spec.getName();
            }

            @Override
            public String description() {
                return spec.getDescription();
            }

            @Override
            public String inputSchemaJson() {
                return spec.getParametersJsonSchema();
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return spec.getParameterDefs();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }
        };
    }

    private McpToolCallResult execute(AiToolSpec spec, Map<String, Object> arguments) {
        Exchange exchange = spec.getConsumer().getEndpoint().createExchange();
        boolean release = true;
        try {
            Future<AiToolResult> future = executor.submit(() -> AiToolExecutor.execute(spec, arguments, exchange));
            AiToolResult result;
            try {
                result = future.get(configuration.getToolTimeout(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                // the route may still be using the exchange; do not return it to the pool
                release = false;
                LOG.warn("MCP tool '{}' did not complete within {} ms; returning a timeout error to the client. "
                         + "The route keeps running until it completes on its own.",
                        spec.getName(), configuration.getToolTimeout());
                return new McpToolCallResult(GENERIC_TIMEOUT_ERROR, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                release = false;
                return new McpToolCallResult(GENERIC_EXECUTION_ERROR, true);
            } catch (ExecutionException e) {
                LOG.warn("MCP tool '{}' execution failed", spec.getName(), e.getCause());
                return new McpToolCallResult(GENERIC_EXECUTION_ERROR, true);
            }
            if (result instanceof AiToolResult.Success success) {
                return new McpToolCallResult(success.value(), false);
            } else if (result instanceof AiToolResult.ArgumentError error) {
                return new McpToolCallResult(error.message(), true);
            } else {
                AiToolResult.ExecutionError error = (AiToolResult.ExecutionError) result;
                // never leak raw route exception messages to remote MCP clients
                LOG.warn("MCP tool '{}' execution failed: {}", spec.getName(), error.message(), error.cause());
                return new McpToolCallResult(GENERIC_EXECUTION_ERROR, true);
            }
        } finally {
            if (release) {
                spec.getConsumer().releaseExchange(exchange, false);
            }
        }
    }

    private final class RegistryListener implements AiToolRegistryListener {

        @Override
        public void toolRegistered(String tag, AiToolSpec spec) {
            // the untagged default pool (tag == null) is never exposed
            if (tag != null && selectedTags.contains(tag) && isStartingOrStarted()) {
                publish(spec);
            }
        }

        @Override
        public void toolDeregistered(String tag, AiToolSpec spec) {
            if (tag != null && selectedTags.contains(tag) && isStartingOrStarted()) {
                unpublish(spec);
            }
        }
    }
}
