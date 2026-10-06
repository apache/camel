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
package org.apache.camel.component.ai.tool;

import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Processor;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that registers a Camel route as an AI tool in the {@link AiToolRegistry} on start and deregisters on stop.
 *
 * @since 4.22
 */
public class AiToolConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolConsumer.class);

    private final String toolName;
    private final AiToolConfiguration configuration;
    private AiToolSpec registeredSpec;
    private String[] registeredTags;
    private boolean registeredInDefaultPool;
    private volatile Processor toolProcessor;

    public AiToolConsumer(AiToolEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.toolName = endpoint.getToolName();
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (registeredSpec == null) {
            prepare();
        }
        register();
    }

    /**
     * Wraps the tool route's processor with the configured {@link AuthorizationPolicy}, if any, so the call is
     * authorized before the route runs. The policy is applied via {@code beforeWrap} then {@code wrap} (as the
     * {@code .policy()} DSL does through {@code PolicyReifier}); the wrapped processor is started and stopped with this
     * consumer. Because {@code getProcessor()} is the route's <em>outer</em> processor, the guard runs in front of the
     * route (before its unit of work, tracing and error handling): a denied call is logged and returned to the model as
     * {@link AiToolResult.AuthorizationDenied}, but it does not produce a route span or metric. There is no
     * {@code ProcessorDefinition} here (the component owns the guard, not the DSL), so {@code beforeWrap} gets a
     * {@code null} definition, which Camel's {@link AuthorizationPolicy} implementations ignore (they use only the
     * route). Called from {@link #prepare()} before the tool is registered, so the tool is never discoverable
     * unguarded; idempotent (a policy already applied is not wrapped twice).
     */
    private void applyAuthorizationPolicy() throws Exception {
        if (toolProcessor != null) {
            return;
        }
        AuthorizationPolicy policy = configuration.getAuthorizationPolicy();
        Processor target = getProcessor();
        if (policy == null || target == null) {
            return;
        }
        policy.beforeWrap(getRoute(), null);
        Processor guarded = policy.wrap(getRoute(), target);
        ServiceHelper.startService(guarded);
        toolProcessor = guarded;
        LOG.debug("Tool '{}' is guarded by authorization policy {}", toolName, policy.getClass().getName());
    }

    /**
     * The processor {@link AiToolExecutor} invokes for this tool: the authorization-guarded processor when an
     * {@link AuthorizationPolicy} is configured, otherwise the plain route processor. Fails <em>closed</em>: if a
     * policy is configured but the guard is not yet in place, it returns a processor that denies the call rather than
     * exposing the unguarded route processor.
     */
    Processor getToolProcessor() {
        Processor guarded = toolProcessor;
        if (guarded != null) {
            return guarded;
        }
        if (configuration.getAuthorizationPolicy() != null) {
            // fail closed: never fall back to the unguarded route processor while a policy is configured
            return exchange -> {
                throw new CamelAuthorizationException(
                        "Authorization policy for tool '" + toolName + "' is not ready", exchange);
            };
        }
        return getProcessor();
    }

    /**
     * Registers during route warm-up, which Camel completes for all routes before it starts any route consumer, so a
     * route that sends a request as soon as its consumer starts (such as {@code stream:in}) sees every tool. Routes
     * that are not started automatically are registered when their consumer starts.
     */
    void registerEarly() throws Exception {
        if (registeredSpec == null && getRoute() != null && CamelContextHelper.isAutoStartup(getRoute())) {
            prepare();
            register();
        }
    }

    /**
     * Removes an early registration whose consumer never started, e.g. when another route failed to start.
     */
    void deregisterEarly() {
        if (registeredSpec != null && !isStarted()) {
            deregister();
            registeredSpec = null;
            // prepare() may have wrapped and started the guard during early registration; stop it so an undone early
            // registration does not leave a started processor behind (matches doStop)
            if (toolProcessor != null) {
                ServiceHelper.stopService(toolProcessor);
                toolProcessor = null;
            }
        }
    }

    private void prepare() throws Exception {
        Map<String, String> params = configuration.getParameters();
        String argSchema = configuration.getArgSchema();
        AiToolParameterHelper.validateParameterSourceExclusive(params, argSchema);

        Map<String, String> outputParams = configuration.getOutputParameters();
        String outputSchema = configuration.getOutputSchema();
        AiToolParameterHelper.validateOutputSourceExclusive(outputParams, outputSchema);

        Map<String, AiToolParameterHelper.ParameterDef> parameterDefs = Map.of();
        String jsonSchema = null;
        Map<String, AiToolParameterHelper.ParameterDef> outputParameterDefs = Map.of();
        String outputJsonSchema = null;

        if (params != null && !params.isEmpty()) {
            parameterDefs = AiToolParameterHelper.parseParameterMetadata(params);
            jsonSchema = AiToolParameterHelper.buildJsonSchemaFromDefs(parameterDefs);
        } else if (argSchema != null && !argSchema.isBlank()) {
            jsonSchema = AiToolParameterHelper.resolveArgSchema(getEndpoint().getCamelContext(), argSchema);
        }

        if (outputParams != null && !outputParams.isEmpty()) {
            outputParameterDefs = AiToolParameterHelper.parseParameterMetadata(outputParams);
            outputJsonSchema = AiToolParameterHelper.buildJsonSchemaFromDefs(outputParameterDefs);
        } else if (outputSchema != null && !outputSchema.isBlank()) {
            outputJsonSchema = AiToolParameterHelper.resolveOutputSchema(getEndpoint().getCamelContext(), outputSchema);
        }

        String desc = configuration.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = toolName;
        }

        AiToolAnnotations annotations = AiToolAnnotations.fromConfiguration(configuration);
        registeredSpec = new AiToolSpec(
                toolName, desc, parameterDefs, jsonSchema, outputParameterDefs, outputJsonSchema, annotations, this);

        String tags = configuration.getTags();
        String[] parsedTags = (tags != null && !tags.isBlank())
                ? AiToolParameterHelper.splitTags(tags)
                : null;
        if (parsedTags != null && parsedTags.length > 0) {
            registeredTags = parsedTags;
            registeredInDefaultPool = false;
        } else {
            registeredTags = null;
            registeredInDefaultPool = true;
        }

        // Apply the authorization guard BEFORE the tool is registered (register() follows in both doStart() and
        // registerEarly()), so the tool is never discoverable in an unguarded state during route warm-up.
        applyAuthorizationPolicy();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (registeredSpec != null) {
            deregister();
        }
        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();
        if (registeredSpec != null) {
            register();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (registeredSpec != null) {
            deregister();
            registeredSpec = null;
            registeredTags = null;
            registeredInDefaultPool = false;
        }
        if (toolProcessor != null) {
            ServiceHelper.stopService(toolProcessor);
            toolProcessor = null;
        }
        super.doStop();
    }

    private void register() {
        AiToolRegistry registry = AiToolRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Registering tool '{}' with tag '{}'", toolName, tag);
                registry.put(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            LOG.debug("Registering tool '{}' in default pool (no tags)", toolName);
            registry.putDefault(registeredSpec);
        }
    }

    private void deregister() {
        AiToolRegistry registry = AiToolRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Removing tool '{}' from tag '{}'", registeredSpec.getName(), tag);
                registry.remove(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            LOG.debug("Removing tool '{}' from default pool", registeredSpec.getName());
            registry.removeDefault(registeredSpec);
        }
    }
}
