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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.StaticService;
import org.apache.camel.component.ai.prompt.AiPromptExecutor;
import org.apache.camel.component.ai.prompt.AiPromptRegistry;
import org.apache.camel.component.ai.prompt.AiPromptRegistryListener;
import org.apache.camel.component.ai.prompt.AiPromptResult;
import org.apache.camel.component.ai.prompt.AiPromptSpec;
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
 * Bridges the {@link AiToolRegistry}, {@link AiResourceRegistry} and {@link AiPromptRegistry} to an
 * {@link McpServerEngine}: selects {@code ai-tool}, {@code ai-resource} and {@code ai-prompt} routes by tag, publishes
 * them as MCP tools, resources and prompts, and executes requests with bounded timeouts and sanitized error mapping.
 * <p>
 * Security notes:
 * <ul>
 * <li>Only tools, resources and prompts whose tags match the configured tag patterns are exposed. Tag patterns support
 * exact match, wildcard prefix ({@code foo*}), and {@code *} to match all tags. The untagged default pools are never
 * exposed — external MCP clients are untrusted senders and crossing that trust boundary is an explicit per-route
 * opt-in.</li>
 * <li>MCP has flat tool and prompt namespaces: an item whose name collides with an already published item is refused
 * with an ERROR log, never silently replaced. Resources are addressed by uri and follow the same rule.</li>
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
    private static final String GENERIC_PROMPT_ERROR = "Prompt execution failed";
    private static final String GENERIC_PROMPT_TIMEOUT_ERROR = "Prompt execution timed out";

    private final McpServerConfiguration configuration;
    private final RegistryListener listener = new RegistryListener();
    private final ResourceRegistryListener resourceListener = new ResourceRegistryListener();
    private final PromptRegistryListener promptListener = new PromptRegistryListener();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, AiToolSpec> published = new HashMap<>();
    private final Map<String, AiResourceSpec> publishedResources = new HashMap<>();
    private final Map<String, AiPromptSpec> publishedPrompts = new HashMap<>();

    private CamelContext camelContext;
    private McpServerEngine engine;
    private AiToolRegistry registry;
    private AiResourceRegistry resourceRegistry;
    private AiPromptRegistry promptRegistry;
    private String[] tagPatterns = new String[0];
    private ExecutorService executor;
    private boolean resourcesUnsupportedWarned;
    private boolean promptsUnsupportedWarned;

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
            LOG.warn("No MCP tags configured: no ai-tool, ai-resource or ai-prompt routes will be exposed. "
                     + "Set tags to opt-in the tools, resources and prompts to expose.");
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
                configuration.getServerIcons(),
                McpServerOrigins.parseAllowedOrigins(configuration.getAllowedOrigins())));

        if (!engine.consumesServingConfiguration()) {
            if (!McpServerConstants.DEFAULT_PATH.equals(configuration.getPath())) {
                LOG.warn("The MCP path option is ignored by engine {}: the runtime's native MCP server configuration "
                         + "decides the endpoint path",
                        engine.getClass().getSimpleName());
            }
            if (isSet(configuration.getAllowedOrigins())) {
                LOG.warn("The MCP allowed origins option is ignored by engine {}: the runtime's native MCP server "
                         + "configuration decides which origins are allowed",
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
        // subscribe before snapshotting so no concurrent registration is missed: publishing a published spec again is
        // a no-op, a refused one is offered to the engine again. publish is called for each selected tag of a spec, so
        // a refusal is logged once per tag, as a name clash is
        registry.addListener(listener);
        resourceRegistry.addListener(resourceListener);
        promptRegistry = AiPromptRegistry.getOrCreate(camelContext);
        promptRegistry.addListener(promptListener);
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
        promptRegistry.getPrompts().forEach((tag, specs) -> {
            if (matchesTag(tag)) {
                specs.forEach(this::publishPrompt);
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
        if (promptRegistry != null) {
            promptRegistry.removeListener(promptListener);
        }
        lock.lock();
        try {
            published.clear();
            publishedResources.clear();
            publishedPrompts.clear();
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
            try {
                engine.toolAdded(createTool(spec));
            } catch (McpServerRefusedException e) {
                LOG.error("Refusing to expose MCP tool '{}': {}", spec.getName(), e.getMessage(), e);
                return;
            }
            // track only what the engine accepted: a refused name stays free and is never passed to toolRemoved
            published.put(spec.getName(), spec);
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
            try {
                engine.resourceAdded(createResource(spec));
            } catch (McpServerRefusedException e) {
                LOG.error("Refusing to expose MCP resource '{}': {}", spec.getUri(), e.getMessage(), e);
                return;
            }
            publishedResources.put(spec.getUri(), spec);
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

    private void publishPrompt(AiPromptSpec spec) {
        lock.lock();
        try {
            AiPromptSpec existing = publishedPrompts.get(spec.getName());
            if (existing == spec) {
                return;
            }
            if (existing != null) {
                LOG.error("Refusing to expose MCP prompt '{}': the name collides with an already exposed prompt.",
                        spec.getName());
                return;
            }
            if (!engine.supportsPrompts()) {
                if (!promptsUnsupportedWarned) {
                    promptsUnsupportedWarned = true;
                    LOG.warn("Engine {} does not serve MCP prompts: ai-prompt routes matching the configured tags "
                             + "are not exposed",
                            engine.getClass().getSimpleName());
                }
                return;
            }
            // Publish to the engine before recording the prompt. If the engine refuses
            // the name, keep the route running but do not track it as published.
            try {
                engine.promptAdded(createPrompt(spec));
                publishedPrompts.put(spec.getName(), spec);
            } catch (McpServerRefusedException e) {
                LOG.error("Refusing to expose MCP prompt '{}': {}", spec.getName(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    private void unpublishPrompt(AiPromptSpec spec) {
        lock.lock();
        try {
            if (publishedPrompts.get(spec.getName()) != spec) {
                return;
            }
            boolean stillSelected = promptRegistry.getPrompts().entrySet().stream()
                    .anyMatch(entry -> matchesTag(entry.getKey()) && entry.getValue().contains(spec));
            if (!stillSelected) {
                publishedPrompts.remove(spec.getName());
                engine.promptRemoved(spec.getName());
            }
        } finally {
            lock.unlock();
        }
    }

    private McpServerPrompt createPrompt(AiPromptSpec spec) {
        McpPromptHandler handler = arguments -> executePrompt(spec, arguments);
        return new McpServerPrompt() {
            @Override
            public String name() {
                return spec.getName();
            }

            @Override
            public String description() {
                return spec.getDescription();
            }

            @Override
            public String title() {
                return spec.getTitle();
            }

            @Override
            public List<McpPromptArgument> arguments() {
                return spec.getArguments().stream()
                        .map(argument -> new McpPromptArgument(
                                argument.name(), argument.description(), argument.required()))
                        .toList();
            }

            @Override
            public McpPromptHandler handler() {
                return handler;
            }
        };
    }

    private McpPromptResult executePrompt(AiPromptSpec spec, Map<String, String> arguments) {
        Exchange exchange = spec.getConsumer().getEndpoint().createExchange();

        // 0 = queued/not started, 1 = running, 2 = exchange ownership completed.
        // The worker releases the exchange after route execution. The caller only
        // releases it when cancellation wins before the worker starts.
        AtomicInteger executionState = new AtomicInteger();

        Future<AiPromptResult> future;
        try {
            future = executor.submit(() -> {
                if (!executionState.compareAndSet(0, 1)) {
                    throw new CancellationException("Prompt execution cancelled before start");
                }
                try {
                    return AiPromptExecutor.execute(spec, arguments, exchange);
                } finally {
                    if (executionState.compareAndSet(1, 2)) {
                        spec.getConsumer().releaseExchange(exchange, false);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (executionState.compareAndSet(0, 2)) {
                spec.getConsumer().releaseExchange(exchange, false);
            }
            LOG.warn("MCP prompt '{}' could not be scheduled for execution", spec.getName(), e);
            return McpPromptResult.error(GENERIC_PROMPT_ERROR);
        }

        AiPromptResult result;
        try {
            result = future.get(configuration.getPromptTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            if (executionState.compareAndSet(0, 2)) {
                spec.getConsumer().releaseExchange(exchange, false);
            }
            LOG.warn("MCP prompt '{}' did not complete within {} ms; returning a timeout error. "
                     + "The exchange will be released when execution ends, or immediately if it had not started.",
                    spec.getName(), configuration.getPromptTimeout());
            return McpPromptResult.error(GENERIC_PROMPT_TIMEOUT_ERROR);
        } catch (InterruptedException e) {
            future.cancel(true);
            if (executionState.compareAndSet(0, 2)) {
                spec.getConsumer().releaseExchange(exchange, false);
            }
            Thread.currentThread().interrupt();
            return McpPromptResult.error(GENERIC_PROMPT_ERROR);
        } catch (ExecutionException e) {
            LOG.warn("MCP prompt '{}' execution failed", spec.getName(), e.getCause());
            return McpPromptResult.error(GENERIC_PROMPT_ERROR);
        }

        if (result instanceof AiPromptResult.ArgumentError error) {
            return McpPromptResult.argumentError(error.message());
        }
        if (result instanceof AiPromptResult.ExecutionError error) {
            LOG.warn("MCP prompt '{}' execution failed: {}", spec.getName(), error.message(), error.cause());
            return McpPromptResult.error(GENERIC_PROMPT_ERROR);
        }
        Object value = ((AiPromptResult.Success) result).value();
        try {
            return toPromptResult(spec, value);
        } catch (IllegalArgumentException e) {
            LOG.warn("MCP prompt '{}' returned an invalid result: {}", spec.getName(), e.getMessage());
            return McpPromptResult.error("Prompt returned an invalid result");
        }
    }

    private McpPromptResult toPromptResult(AiPromptSpec spec, Object value) {
        List<McpPromptMessage> messages = new ArrayList<>();
        if (value instanceof String text) {
            messages.add(new McpPromptMessage("user", text));
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text) {
                    messages.add(new McpPromptMessage("user", text));
                } else if (item instanceof Map<?, ?> map) {
                    Object role = map.get("role");
                    Object content = map.get("content");
                    if (!(role instanceof String roleName)
                            || !("user".equals(roleName) || "assistant".equals(roleName))
                            || content == null
                            || (!(content instanceof String)
                                    && !(content instanceof Map<?, ?>)
                                    && !content.getClass().getName()
                                            .startsWith("io.modelcontextprotocol.spec.McpSchema$"))) {
                        throw new IllegalArgumentException("Expected a user/assistant role and String or MCP content");
                    }
                    messages.add(new McpPromptMessage(roleName, content));
                } else {
                    throw new IllegalArgumentException("Expected a String or message Map in prompt result list");
                }
            }
        } else {
            throw new IllegalArgumentException("Expected a String or List prompt route result");
        }
        return McpPromptResult.success(spec.getDescription(), messages);
    }

    private McpServerTool createTool(AiToolSpec spec) {
        McpToolCallHandler handler = new McpToolCallHandler() {
            @Override
            public McpToolCallResult call(Map<String, Object> arguments) {
                return execute(spec, arguments, null);
            }

            @Override
            public McpToolCallResult call(Map<String, Object> arguments, McpToolCallContext context) {
                return execute(spec, arguments, context);
            }
        };
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

    private McpToolCallResult execute(AiToolSpec spec, Map<String, Object> arguments, McpToolCallContext context) {
        Exchange exchange = spec.getConsumer().getEndpoint().createExchange();
        if (context != null) {
            stampCallerIdentity(exchange, context);
        }
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
            } else if (result instanceof AiToolResult.AuthorizationDenied denied) {
                // The denial message is already generic and caller-safe, so it is safe to return to the MCP client.
                LOG.warn("MCP tool '{}' call denied by authorization policy", spec.getName());
                return new McpToolCallResult(denied.message(), true);
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

    /**
     * Stamps the MCP caller identity onto the tool exchange as exchange properties the model cannot set: the raw
     * transport principal, and the runtime-neutral name and roles when the engine could derive them. A tool route's
     * {@link org.apache.camel.spi.AuthorizationPolicy} authorizes on these.
     */
    private static void stampCallerIdentity(Exchange exchange, McpToolCallContext context) {
        Object principal = context.securityPrincipal();
        if (principal != null) {
            exchange.setProperty(McpToolCallContext.SECURITY_PRINCIPAL_PROPERTY, principal);
        }
        String name = context.principalName();
        if (name != null && !name.isBlank()) {
            exchange.setProperty(McpToolCallContext.SECURITY_PRINCIPAL_NAME_PROPERTY, name);
        }
        Collection<String> roles = context.principalRoles();
        if (roles != null && !roles.isEmpty()) {
            exchange.setProperty(McpToolCallContext.SECURITY_PRINCIPAL_ROLES_PROPERTY, roles);
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

    private final class PromptRegistryListener implements AiPromptRegistryListener {

        @Override
        public void promptRegistered(String tag, AiPromptSpec spec) {
            if (matchesTag(tag) && isStartingOrStarted()) {
                publishPrompt(spec);
            }
        }

        @Override
        public void promptDeregistered(String tag, AiPromptSpec spec) {
            if (matchesTag(tag) && isStartingOrStarted()) {
                unpublishPrompt(spec);
            }
        }
    }
}
