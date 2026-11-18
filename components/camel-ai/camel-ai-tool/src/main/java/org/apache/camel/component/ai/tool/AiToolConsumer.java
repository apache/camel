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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that registers a Camel route as an AI tool in the {@link AiToolRegistry} on start and deregisters on stop.
 * <p>
 * It is {@link Suspendable}, so a route stop or graceful shutdown suspends it first, which deregisters the tool, and
 * stops it once the tool calls in flight have completed or the shutdown timeout has elapsed. Suspending the route
 * deregisters the tool without stopping the route, and resuming it registers the same tool spec again. A suspended tool
 * refuses new calls.
 *
 * @since 4.22
 */
public class AiToolConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolConsumer.class);

    private final String toolName;
    private final AiToolConfiguration configuration;
    private AiToolSpec registeredSpec;
    private String[] registeredTags;
    private boolean registeredInDefaultPool;
    private volatile Processor toolProcessor;
    // the processor authorizationPolicy.wrap returned, which this consumer starts and stops; null without a policy
    private Processor policyProcessor;

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
     * Puts the authorization guard in front of the tool route's processor, so the call is authorized before the route
     * runs. The guard is the {@code allowedRoles} check and the configured {@link AuthorizationPolicy}, whichever are
     * set. The roles check is the outer one: with both set the roles are checked first, and a call that fails them does
     * not reach the policy. An {@code allowedRoles} that is set but names no role is still a guard, which refuses every
     * call. The route starts, so that one misconfigured tool does not stop the other routes, and a {@code WARN} says
     * that the tool refuses its calls.
     * <p>
     * The policy is applied via {@code beforeWrap} then {@code wrap} (as the {@code .policy()} DSL does through
     * {@code PolicyReifier}); the wrapped processor is started and stopped with this consumer. The roles check has no
     * lifecycle on the processor it guards, so with {@code allowedRoles} alone this consumer starts and stops nothing
     * more than without it. Because {@code getProcessor()} is the route's <em>outer</em> processor, the guard runs in
     * front of the route (before its unit of work, tracing and error handling): a denied call is logged and returned to
     * the model as {@link AiToolResult.AuthorizationDenied}, but it does not produce a route span or metric. There is
     * no {@code ProcessorDefinition} here (the component owns the guard, not the DSL), so {@code beforeWrap} gets a
     * {@code null} definition, which Camel's {@link AuthorizationPolicy} implementations ignore (they use only the
     * route). Called from {@link #prepare()} before the tool is registered, so the tool is never discoverable
     * unguarded; idempotent (a guard already applied is not wrapped twice).
     */
    private void applyAuthorizationGuard() throws Exception {
        if (toolProcessor != null) {
            return;
        }
        AuthorizationPolicy policy = configuration.getAuthorizationPolicy();
        String allowedRoles = configuration.getAllowedRoles();
        if (policy == null && allowedRoles == null) {
            return;
        }
        Processor target = getProcessor();
        if (target == null) {
            return;
        }
        Processor guarded = target;
        if (policy != null) {
            policy.beforeWrap(getRoute(), null);
            guarded = policy.wrap(getRoute(), guarded);
            ServiceHelper.startService(guarded);
            policyProcessor = guarded;
            LOG.debug("Tool '{}' is guarded by authorization policy {}", toolName, policy.getClass().getName());
        }
        if (allowedRoles != null) {
            List<String> roles = List.of(AiToolParameterHelper.splitTags(allowedRoles));
            if (roles.isEmpty()) {
                LOG.warn("Tool '{}' sets an allowedRoles that names no role, so every call to it is refused", toolName);
            } else {
                LOG.debug("Tool '{}' is guarded by allowedRoles {} (allRolesRequired={})", toolName, roles,
                        configuration.isAllRolesRequired());
            }
            guarded = new AiToolAllowedRolesProcessor(guarded, toolName, roles, configuration.isAllRolesRequired());
        }
        toolProcessor = guarded;
    }

    /**
     * The processor {@link AiToolExecutor} invokes for this tool: the authorization guard when {@code allowedRoles} or
     * an {@link AuthorizationPolicy} is set, otherwise the plain route processor. Fails <em>closed</em>: if either is
     * set but the guard is not in place, it returns a processor that denies the call rather than exposing the unguarded
     * route processor.
     */
    Processor getToolProcessor() {
        Processor guarded = toolProcessor;
        if (guarded != null) {
            return guarded;
        }
        if (configuration.getAuthorizationPolicy() != null || configuration.getAllowedRoles() != null) {
            // fail closed: never fall back to the unguarded route processor while a guard is configured
            return exchange -> {
                throw new CamelAuthorizationException(
                        "Authorization for tool '" + toolName + "' is not ready", exchange);
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
            // prepare() may have wrapped and started the policy during early registration; stop it so an undone early
            // registration does not leave a started processor behind (matches doStop)
            if (toolProcessor != null) {
                ServiceHelper.stopService(policyProcessor);
                policyProcessor = null;
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
                toolName, desc, parameterDefs, jsonSchema, outputParameterDefs, outputJsonSchema, annotations, this,
                configuration.isSearchable());

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
        applyAuthorizationGuard();
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
            ServiceHelper.stopService(policyProcessor);
            policyProcessor = null;
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
