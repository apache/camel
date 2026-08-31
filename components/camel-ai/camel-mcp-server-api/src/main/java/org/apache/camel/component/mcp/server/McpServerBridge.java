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

import java.util.HashMap;
import java.util.Map;
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
import org.apache.camel.component.ai.resource.AiResourceExecutor;
import org.apache.camel.component.ai.resource.AiResourceRegistry;
import org.apache.camel.component.ai.resource.AiResourceRegistryListener;
import org.apache.camel.component.ai.resource.AiResourceResult;
import org.apache.camel.component.ai.resource.AiResourceSpec;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.component.ai.tool.AiToolExecutor;
import org.apache.camel.component.ai.tool.AiToolParameterHelper;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.ai.tool.AiToolRegistry;
import org.apache.camel.component.ai.tool.AiToolRegistryListener;
import org.apache.camel.component.ai.tool.AiToolResult;
import org.apache.camel.component.ai.tool.AiToolSpec;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the {@link AiToolRegistry} and the {@link AiResourceRegistry} to an {@link McpServerEngine}: selects
 * {@code ai-tool} and {@code ai-resource} routes by tag, publishes them as MCP tools and resources, and executes calls
 * and reads with a bounded timeout and sanitized error mapping.
 * <p>
 * Security notes:
 * <ul>
 * <li>Only tools and resources whose tags match the configured tag patterns are exposed. Tag patterns support exact
 * match, wildcard prefix ({@code foo*}), and {@code *} to match all tags. The untagged default pools are never exposed
 * — external MCP clients are untrusted senders and crossing that trust boundary is an explicit per-route opt-in.</li>
 * <li>MCP has a flat tool namespace: a tool whose name collides with an already published tool is refused with an ERROR
 * log, never silently replaced. Resources are addressed by uri and follow the same rule.</li>
 * <li>Raw route exception messages never reach the engine: execution failures map to a generic error message and the
 * cause is logged server-side.</li>
 * </ul>
 *
 * @since 4.22
 */
public class McpServerBridge extends ServiceSupport implements CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerBridge.class);

    private static final String GENERIC_EXECUTION_ERROR = "Tool execution failed";
    private static final String GENERIC_TIMEOUT_ERROR = "Tool execution timed out";
    private static final String GENERIC_READ_ERROR = "Resource read failed";
    private static final String GENERIC_READ_TIMEOUT_ERROR = "Resource read timed out";

    private final McpServerConfiguration configuration;
    private final RegistryListener listener = new RegistryListener();
    private final ResourceRegistryListener resourceListener = new ResourceRegistryListener();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, AiToolSpec> published = new HashMap<>();
    private final Map<String, AiResourceSpec> publishedResources = new HashMap<>();

    private CamelContext camelContext;
    private McpServerEngine engine;
    private AiToolRegistry registry;
    private AiResourceRegistry resourceRegistry;
    private String[] tagPatterns = new String[0];
    private ExecutorService executor;
    private boolean resourcesUnsupportedWarned;

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
            tagPatterns = AiToolParameterHelper.splitTags(configuration.getTags());
        }
        if (tagPatterns.length == 0) {
            LOG.warn("No MCP tags configured: no ai-tool or ai-resource routes will be exposed. "
                     + "Set tags to opt-in the tools and resources to expose.");
        }

        engine = resolveEngine();
        CamelContextAware.trySetCamelContext(engine, camelContext);

        String serverName = configuration.getServerName() != null ? configuration.getServerName() : camelContext.getName();
        String version = camelContext.getVersion();
        if (version == null || version.isBlank()) {
            version = "1.0";
        }
        engine.initialize(new McpServerInfo(
                serverName, version, configuration.getPath(),
                configuration.getSessionKeepAliveInterval(), configuration.getSessionIdleTtl(),
                configuration.getServerTitle(), configuration.getServerDescription(),
                configuration.getServerWebsiteUrl(), configuration.getInstructions(),
                configuration.getServerIcons()));

        if (!engine.consumesServingConfiguration()) {
            if (!McpServerConstants.DEFAULT_PATH.equals(configuration.getPath())) {
                LOG.warn("The MCP path option is ignored by engine {}: the runtime's native MCP server configuration "
                         + "decides the endpoint path",
                        engine.getClass().getSimpleName());
            }
            if (hasServerMetadataConfiguration()) {
                LOG.warn("The MCP server metadata options may be ignored by engine {}: the runtime's native MCP "
                         + "server configuration decides the server identity and initialize metadata",
                        engine.getClass().getSimpleName());
            }
        }

        ServiceHelper.initService(engine);
    }

    @Override
    protected void doStart() throws Exception {
        executor = camelContext.getExecutorServiceManager().newCachedThreadPool(this, "McpServerCall");
        ServiceHelper.startService(engine);

        registry = AiToolRegistry.getOrCreate(camelContext);
        resourceRegistry = AiResourceRegistry.getOrCreate(camelContext);
        // subscribe before snapshotting so no concurrent registration is missed; publishing is idempotent
        registry.addListener(listener);
        resourceRegistry.addListener(resourceListener);
        registry.getTools().forEach((tag, specs) -> {
            if (matchesTag(tag)) {
                specs.forEach(this::publish);
            }
        });
        resourceRegistry.getResources().forEach((tag, specs) -> {
            if (matchesTag(tag)) {
                specs.forEach(this::publishResource);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        if (registry != null) {
            registry.removeListener(listener);
        }
        if (resourceRegistry != null) {
            resourceRegistry.removeListener(resourceListener);
        }
        lock.lock();
        try {
            published.clear();
            publishedResources.clear();
        } finally {
            lock.unlock();
        }
        ServiceHelper.stopService(engine);
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(executor);
            executor = null;
        }
    }

    private boolean hasServerMetadataConfiguration() {
        return isSet(configuration.getServerName())
                || isSet(configuration.getServerTitle())
                || isSet(configuration.getServerDescription())
                || isSet(configuration.getServerWebsiteUrl())
                || isSet(configuration.getInstructions())
                || (configuration.getServerIcons() != null && !configuration.getServerIcons().isEmpty());
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private McpServerEngine resolveEngine() {
        McpServerEngine answer = camelContext.getRegistry().findSingleByType(McpServerEngine.class);
        if (answer == null) {
            answer = ResolverHelper.resolveMandatoryService(camelContext, McpServerConstants.MCP_SERVER_ENGINE_FACTORY,
                    McpServerEngine.class, "camel-mcp-server");
        }
        return answer;
    }

    private void publish(AiToolSpec spec) {
        // the engine is notified while holding the lock so publish/unpublish for the same tool cannot
        // interleave between the map update and the engine call (which would orphan the tool in the engine)
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
            engine.toolAdded(createTool(spec));
        } finally {
            lock.unlock();
        }
    }

    private void unpublish(AiToolSpec spec) {
        lock.lock();
        try {
            if (published.get(spec.getName()) != spec) {
                return;
            }
            // the same spec may be registered under several selected tags; only remove when it is gone from all
            boolean stillSelected = registry.getTools().entrySet().stream()
                    .anyMatch(e -> matchesTag(e.getKey()) && e.getValue().contains(spec));
            if (!stillSelected) {
                published.remove(spec.getName());
                engine.toolRemoved(spec.getName());
            }
        } finally {
            lock.unlock();
        }
    }

    private void publishResource(AiResourceSpec spec) {
        lock.lock();
        try {
            AiResourceSpec existing = publishedResources.get(spec.getUri());
            if (existing == spec) {
                return;
            }
            if (existing != null) {
                LOG.error("Refusing to expose MCP resource '{}': the uri collides with an already exposed resource. "
                          + "Resource uris must be unique - change the resourceUri option on one of the "
                          + "ai-resource routes.",
                        spec.getUri());
                return;
            }
            if (!engine.supportsResources()) {
                if (!resourcesUnsupportedWarned) {
                    resourcesUnsupportedWarned = true;
                    LOG.warn("Engine {} does not serve MCP resources: ai-resource routes matching the configured tags "
                             + "are not exposed",
                            engine.getClass().getSimpleName());
                }
                return;
            }
            publishedResources.put(spec.getUri(), spec);
            engine.resourceAdded(createResource(spec));
        } finally {
            lock.unlock();
        }
    }

    private void unpublishResource(AiResourceSpec spec) {
        lock.lock();
        try {
            if (publishedResources.get(spec.getUri()) != spec) {
                return;
            }
            // the same spec may be registered under several selected tags; only remove when it is gone from all
            boolean stillSelected = resourceRegistry.getResources().entrySet().stream()
                    .anyMatch(e -> matchesTag(e.getKey()) && e.getValue().contains(spec));
            if (!stillSelected) {
                publishedResources.remove(spec.getUri());
                engine.resourceRemoved(spec.getUri());
            }
        } finally {
            lock.unlock();
        }
    }

    private McpServerResource createResource(AiResourceSpec spec) {
        McpResourceReadHandler handler = () -> read(spec);
        return new McpServerResource() {
            @Override
            public String uri() {
                return spec.getUri();
            }

            @Override
            public String name() {
                return spec.getName();
            }

            @Override
            public String description() {
                return spec.getDescription();
            }

            @Override
            public String mimeType() {
                return spec.getMimeType();
            }

            @Override
            public McpResourceReadHandler handler() {
                return handler;
            }

            @Override
            public String title() {
                return spec.getTitle();
            }
        };
    }

    private McpResourceReadResult read(AiResourceSpec spec) {
        Exchange exchange = spec.getConsumer().getEndpoint().createExchange();
        boolean release = true;
        try {
            Future<AiResourceResult> future = executor.submit(() -> AiResourceExecutor.execute(spec, exchange));
            AiResourceResult result;
            try {
                result = future.get(configuration.getResourceTimeout(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                // the route may still be using the exchange; do not return it to the pool
                release = false;
                LOG.warn("MCP resource '{}' did not complete within {} ms; returning a timeout error to the client. "
                         + "The route keeps running until it completes on its own, and exchange {} is not returned "
                         + "to the pool.",
                        spec.getUri(), configuration.getResourceTimeout(), exchange.getExchangeId());
                return McpResourceReadResult.error(GENERIC_READ_TIMEOUT_ERROR);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                release = false;
                return McpResourceReadResult.error(GENERIC_READ_ERROR);
            } catch (ExecutionException e) {
                LOG.warn("MCP resource '{}' read failed", spec.getUri(), e.getCause());
                return McpResourceReadResult.error(GENERIC_READ_ERROR);
            }
            if (result instanceof AiResourceResult.Text text) {
                return McpResourceReadResult.text(text.value());
            } else if (result instanceof AiResourceResult.Binary binary) {
                return McpResourceReadResult.blob(binary.value());
            } else {
                AiResourceResult.ExecutionError error = (AiResourceResult.ExecutionError) result;
                // never leak raw route exception messages to remote MCP clients
                LOG.warn("MCP resource '{}' read failed: {}", spec.getUri(), error.message(), error.cause());
                return McpResourceReadResult.error(GENERIC_READ_ERROR);
            }
        } finally {
            if (release) {
                spec.getConsumer().releaseExchange(exchange, false);
            }
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

            @Override
            public AiToolAnnotations annotations() {
                return spec.getAnnotations();
            }

            @Override
            public String outputSchemaJson() {
                return spec.getOutputJsonSchema();
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
                         + "The route keeps running until it completes on its own, and exchange {} is not returned "
                         + "to the pool.",
                        spec.getName(), configuration.getToolTimeout(), exchange.getExchangeId());
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
                return new McpToolCallResult(success.value(), false, success.structuredContent());
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

    private boolean matchesTag(String tag) {
        return tag != null && PatternHelper.matchSimplePatterns(tag, tagPatterns);
    }

    private final class RegistryListener implements AiToolRegistryListener {

        @Override
        public void toolRegistered(String tag, AiToolSpec spec) {
            if (matchesTag(tag) && isStartingOrStarted()) {
                publish(spec);
            }
        }

        @Override
        public void toolDeregistered(String tag, AiToolSpec spec) {
            if (matchesTag(tag) && isStartingOrStarted()) {
                unpublish(spec);
            }
        }
    }

    private final class ResourceRegistryListener implements AiResourceRegistryListener {

        @Override
        public void resourceRegistered(String tag, AiResourceSpec spec) {
            if (matchesTag(tag) && isStartingOrStarted()) {
                publishResource(spec);
            }
        }

        @Override
        public void resourceDeregistered(String tag, AiResourceSpec spec) {
            if (matchesTag(tag) && isStartingOrStarted()) {
                unpublishResource(spec);
            }
        }
    }
}
