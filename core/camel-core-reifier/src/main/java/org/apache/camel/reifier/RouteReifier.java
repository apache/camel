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
package org.apache.camel.reifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConsumerResolver;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupStep;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.ContractAdvice;
import org.apache.camel.processor.RoutePipeline;
import org.apache.camel.reifier.rest.RestBindingReifier;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.Contract;
import org.apache.camel.spi.ErrorHandlerAware;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PluginHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteReifier extends ProcessorReifier<RouteDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(RouteReifier.class);

    private static final String[] RESERVED_PROPERTIES = new String[] {
            Route.ID_PROPERTY, Route.CUSTOM_ID_PROPERTY, Route.PARENT_PROPERTY,
            Route.DESCRIPTION_PROPERTY, Route.GROUP_PROPERTY, Route.NODE_PREFIX_ID_PROPERTY,
            Route.REST_PROPERTY, Route.CONFIGURATION_ID_PROPERTY };

    public RouteReifier(CamelContext camelContext, ProcessorDefinition<?> definition) {
        super(camelContext, (RouteDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        throw new UnsupportedOperationException("Not implemented for RouteDefinition");
    }

    public Route createRoute() {
        try {
            return doCreateRoute();
        } catch (FailedToCreateRouteException e) {
            throw e;
        } catch (Exception e) {
            // wrap in exception which provide more details about which route
            // was failing
            throw new FailedToCreateRouteException(definition.getId(), definition.toString(), e);
        }
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected Route doCreateRoute() throws Exception {
        // resolve endpoint
        Endpoint endpoint = definition.getInput().getEndpoint();
        if (endpoint == null) {
            EndpointConsumerResolver def = definition.getInput().getEndpointConsumerBuilder();
            if (def != null) {
                endpoint = def.resolve(camelContext);
            } else {
                endpoint = resolveEndpoint(definition.getInput().getEndpointUri());
            }
        }

        // create route
        String id = definition.idOrCreate(camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class));
        String desc = definition.getDescriptionText();

        Route route = PluginHelper.getRouteFactory(camelContext).createRoute(camelContext, definition, id,
                desc, endpoint, definition.getResource());

        // configure error handler
        route.setErrorHandlerFactory(definition.getErrorHandlerFactory());

        // configure variable
        String variable = parseString(definition.getInput().getVariableReceive());
        if (variable != null) {
            // when using variable we need to turn on original message
            route.setAllowUseOriginalMessage(true);
        }

        // configure tracing
        if (definition.getTrace() != null) {
            Boolean isTrace = parseBoolean(definition.getTrace());
            if (isTrace != null) {
                route.setTracing(isTrace);
                if (isTrace) {
                    LOG.debug("Tracing is enabled on route: {}", definition.getId());
                    // tracing is added in the DefaultChannel so we can enable
                    // it on the fly
                }
            }
        }

        // configure message history
        if (definition.getMessageHistory() != null) {
            Boolean isMessageHistory = parseBoolean(definition.getMessageHistory());
            if (isMessageHistory != null) {
                route.setMessageHistory(isMessageHistory);
                if (isMessageHistory) {
                    LOG.debug("Message history is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure Log EIP mask
        if (definition.getLogMask() != null) {
            Boolean isLogMask = parseBoolean(definition.getLogMask());
            if (isLogMask != null) {
                route.setLogMask(isLogMask);
                if (isLogMask) {
                    LOG.debug("Security mask for Logging is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure stream caching
        if (definition.getStreamCache() != null) {
            Boolean isStreamCache = parseBoolean(definition.getStreamCache());
            if (isStreamCache != null) {
                route.setStreamCaching(isStreamCache);
                if (isStreamCache) {
                    LOG.debug("StreamCaching is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure delayer
        if (definition.getDelayer() != null) {
            Long delayer = parseDuration(definition.getDelayer());
            if (delayer != null) {
                route.setDelayer(delayer);
                if (delayer > 0) {
                    LOG.debug("Delayer is enabled with: {} ms. on route: {}", delayer, definition.getId());
                } else {
                    LOG.debug("Delayer is disabled on route: {}", definition.getId());
                }
            }
        }

        // configure auto startup
        Boolean isAutoStartup = parseBoolean(definition.getAutoStartup());

        // configure startup order
        Integer startupOrder = definition.getStartupOrder();

        // configure shutdown
        if (definition.getShutdownRoute() != null) {
            LOG.debug("Using ShutdownRoute {} on route: {}", definition.getShutdownRoute(), definition.getId());
            route.setShutdownRoute(parse(ShutdownRoute.class, definition.getShutdownRoute()));
        }
        if (definition.getShutdownRunningTask() != null) {
            LOG.debug("Using ShutdownRunningTask {} on route: {}", definition.getShutdownRunningTask(), definition.getId());
            route.setShutdownRunningTask(parse(ShutdownRunningTask.class, definition.getShutdownRunningTask()));
        }

        // should inherit the intercept strategies we have defined
        route.getInterceptStrategies().addAll(definition.getInterceptStrategies());

        // notify route context created
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRouteContextCreate(route);
        }

        // validate route has output processors
        if (!hasOutputs(definition.getOutputs(), true)) {
            String at = definition.getInput().toString();
            Exception cause = new IllegalArgumentException(
                    "Route " + definition.getId() + " has no output processors."
                                                           + " You need to add outputs to the route such as to(\"log:foo\").");
            throw new FailedToCreateRouteException(definition.getId(), definition.toString(), at, cause);
        }

        List<ProcessorDefinition<?>> list = new ArrayList<>(definition.getOutputs());
        for (ProcessorDefinition<?> output : list) {
            try {
                ProcessorReifier<?> reifier = ProcessorReifier.reifier(route, output);

                // ensure node has id assigned
                String outputId
                        = output.idOrCreate(camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class));
                String eip = reifier.getClass().getSimpleName().replace("Reifier", "");
                StartupStep step = camelContext.getCamelContextExtension().getStartupStepRecorder()
                        .beginStep(ProcessorReifier.class, outputId, "Create " + eip + " Processor");

                reifier.addRoutes();

                camelContext.getCamelContextExtension().getStartupStepRecorder().endStep(step);
            } catch (Exception e) {
                throw new FailedToCreateRouteException(definition.getId(), definition.toString(), output.toString(), e);
            }
        }

        // now lets turn all the event driven consumer processors into a single route
        List<Processor> eventDrivenProcessors = route.getEventDrivenProcessors();
        if (eventDrivenProcessors.isEmpty()) {
            return null;
        }

        // Set route properties
        Map<String, Object> routeProperties = computeRouteProperties();

        // always use a pipeline even if there are only 1 processor as the pipeline
        // handles preparing the response from the exchange in regard to IN vs OUT messages etc
        RoutePipeline target = new RoutePipeline(camelContext, eventDrivenProcessors);
        target.setRouteId(id);

        // and wrap it in a unit of work so the UoW is on the top, so the entire route will be in the same UoW
        InternalProcessor internal = PluginHelper.getInternalProcessorFactory(camelContext)
                .addUnitOfWorkProcessorAdvice(camelContext, target, route);

        // configure route policy
        if (definition.getRoutePolicies() != null && !definition.getRoutePolicies().isEmpty()) {
            for (RoutePolicy policy : definition.getRoutePolicies()) {
                LOG.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                route.getRoutePolicyList().add(policy);
            }
        }
        if (definition.getRoutePolicyRef() != null) {
            StringTokenizer policyTokens = new StringTokenizer(definition.getRoutePolicyRef(), ",");
            while (policyTokens.hasMoreTokens()) {
                String ref = policyTokens.nextToken().trim();
                RoutePolicy policy = mandatoryLookup(ref, RoutePolicy.class);
                LOG.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                route.getRoutePolicyList().add(policy);
            }
        }
        if (camelContext.getRoutePolicyFactories() != null) {
            for (RoutePolicyFactory factory : camelContext.getRoutePolicyFactories()) {
                RoutePolicy policy = factory.createRoutePolicy(camelContext, definition.getId(), definition);
                if (policy != null) {
                    LOG.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                    route.getRoutePolicyList().add(policy);
                }
            }
        }
        // and then optionally add route policy processor if a custom policy is set
        List<RoutePolicy> routePolicyList = route.getRoutePolicyList();
        if (routePolicyList != null && !routePolicyList.isEmpty()) {
            for (RoutePolicy policy : routePolicyList) {
                // add policy as service if we have not already done that (eg possible if two routes have the same service)
                // this ensures Camel can control the lifecycle of the policy
                if (!camelContext.hasService(policy)) {
                    try {
                        camelContext.addService(policy);
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                }
            }

            internal.addRoutePolicyAdvice(routePolicyList);
        }

        // wrap in route inflight processor to track number of inflight exchanges for the route
        internal.addRouteInflightRepositoryAdvice(camelContext.getInflightRepository(), route.getRouteId());

        // wrap in JMX instrumentation processor that is used for performance stats
        ManagementInterceptStrategy managementInterceptStrategy = route.getManagementInterceptStrategy();
        if (managementInterceptStrategy != null) {
            internal.addManagementInterceptStrategy(managementInterceptStrategy.createProcessor("route"));
        }

        // wrap in route lifecycle
        internal.addRouteLifecycleAdvice();

        // add advices
        if (definition.getRestBindingDefinition() != null) {
            try {
                internal.addAdvice(
                        new RestBindingReifier(route, definition.getRestBindingDefinition()).createRestBindingAdvice());
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        // wrap in contract
        if (definition.getInputType() != null || definition.getOutputType() != null) {
            Contract contract = new Contract();
            if (definition.getInputType() != null) {
                contract.setInputType(parseString(definition.getInputType().getUrn()));
                contract.setValidateInput(parseBoolean(definition.getInputType().getValidate(), false));
            }
            if (definition.getOutputType() != null) {
                contract.setOutputType(parseString(definition.getOutputType().getUrn()));
                contract.setValidateOutput(parseBoolean(definition.getOutputType().getValidate(), false));
            }
            internal.addAdvice(new ContractAdvice(contract));
            // make sure to enable data type as its in use when using
            // input/output types on routes
            camelContext.setUseDataType(true);
        }

        // wrap with variable
        if (variable != null) {
            internal.addAdvice(new VariableAdvice(variable));
        }

        // and create the route that wraps all of this
        route.setProcessor(internal);
        route.getProperties().putAll(routeProperties);
        route.setStartupOrder(startupOrder);
        if (isAutoStartup != null) {
            LOG.debug("Using AutoStartup {} on route: {}", isAutoStartup, definition.getId());
            route.setAutoStartup(isAutoStartup);
        }

        // after the route is created then set the route on the policy processor(s) so we get hold of it
        internal.setRouteOnAdvices(route);

        // invoke init on route policy
        if (routePolicyList != null && !routePolicyList.isEmpty()) {
            for (RoutePolicy policy : routePolicyList) {
                policy.onInit(route);
            }
        }

        // inject the route error handler for processors that are error handler aware
        // this needs to be done here at the end because the route may be transactional and have a transaction error handler
        // automatic be configured which some EIPs like Multicast/RecipientList needs to be using for special fine-grained error handling
        ErrorHandlerFactory builder = route.getErrorHandlerFactory();
        Processor errorHandler = ((ModelCamelContext) camelContext).getModelReifierFactory().createErrorHandler(route,
                builder, null);
        prepareErrorHandlerAware(route, errorHandler);

        // only during startup phase
        if (camelContext.getStatus().ordinal() < ServiceStatus.Started.ordinal()) {
            // okay route has been created from the model, then the model is no longer needed, and we can de-reference
            camelContext.getCamelContextExtension().addBootstrap(route::clearRouteModel);
        }

        return route;
    }

    private void prepareErrorHandlerAware(Route route, Processor errorHandler) {
        List<Processor> processors = route.filter("*");
        for (Processor p : processors) {
            if (p instanceof ErrorHandlerAware) {
                ((ErrorHandlerAware) p).setErrorHandler(errorHandler);
            }
        }
    }

    protected Map<String, Object> computeRouteProperties() {
        Map<String, Object> routeProperties = new HashMap<>();
        routeProperties.put(Route.ID_PROPERTY, definition.getId());
        routeProperties.put(Route.CUSTOM_ID_PROPERTY, Boolean.toString(definition.hasCustomIdAssigned()));
        routeProperties.put(Route.PARENT_PROPERTY, Integer.toHexString(definition.hashCode()));
        routeProperties.put(Route.DESCRIPTION_PROPERTY, definition.getDescriptionText());
        if (definition.getGroup() != null) {
            routeProperties.put(Route.GROUP_PROPERTY, definition.getGroup());
        }
        if (definition.getNodePrefixId() != null) {
            routeProperties.put(Route.NODE_PREFIX_ID_PROPERTY, definition.getNodePrefixId());
        }
        String rest = Boolean.toString(definition.isRest() != null && definition.isRest());
        routeProperties.put(Route.REST_PROPERTY, rest);
        String template = Boolean.toString(definition.isTemplate() != null && definition.isTemplate());
        routeProperties.put(Route.TEMPLATE_PROPERTY, template);
        String kamelet = Boolean.toString(definition.isKamelet() != null && definition.isKamelet());
        routeProperties.put(Route.KAMELET_PROPERTY, kamelet);
        if (definition.getAppliedRouteConfigurationIds() != null) {
            routeProperties.put(Route.CONFIGURATION_ID_PROPERTY,
                    String.join(",", definition.getAppliedRouteConfigurationIds()));
        }

        List<PropertyDefinition> properties = definition.getRouteProperties();
        if (properties != null) {
            for (PropertyDefinition prop : properties) {
                try {
                    final String key = parseString(prop.getKey());
                    final String val = parseString(prop.getValue());
                    for (String property : RESERVED_PROPERTIES) {
                        if (property.equalsIgnoreCase(key)) {
                            throw new IllegalArgumentException(
                                    "Cannot set route property " + property + " as it is a reserved property");
                        }
                    }
                    routeProperties.put(key, val);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }
        }
        return routeProperties;
    }

    /**
     * Advice for moving message body into a variable when using variableReceive mode
     */
    private static class VariableAdvice implements CamelInternalProcessorAdvice<Object> {

        private final String name;

        public VariableAdvice(String name) {
            this.name = name;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            // move body to variable
            ExchangeHelper.setVariableFromMessageBodyAndHeaders(exchange, name, exchange.getMessage());
            exchange.getMessage().setBody(null);
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            // noop
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

}
