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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.AdviceWithTask;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.ContractAdvice;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.reifier.rest.RestBindingReifier;
import org.apache.camel.spi.Contract;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.util.ObjectHelper;

public class RouteReifier extends ProcessorReifier<RouteDefinition> {

    private static final String[] RESERVED_PROPERTIES = new String[] {
    Route.ID_PROPERTY, Route.CUSTOM_ID_PROPERTY, Route.PARENT_PROPERTY,
    Route.DESCRIPTION_PROPERTY, Route.GROUP_PROPERTY,
    Route.REST_PROPERTY};

    public RouteReifier(CamelContext camelContext, ProcessorDefinition<?> definition) {
        super(camelContext, (RouteDefinition) definition);
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once
     * (you can of course advice multiple routes). If you do it multiple times,
     * then it may not work as expected, especially when any kind of error
     * handling is involved. The Camel team plan for Camel 3.0 to support this
     * as internal refactorings in the routing engine is needed to support this
     * properly.
     * <p/>
     * You can use a regular {@link RouteBuilder} but the specialized
     * {@link AdviceWithRouteBuilder} has additional features when using the
     * <a href="http://camel.apache.org/advicewith.html">advice with</a>
     * feature. We therefore suggest you to use the
     * {@link AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on
     * completions etc. configured from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes
     * to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start
     * this new advised route.
     *
     * @param definition the model definition
     * @param camelContext the camel context
     * @param builder the route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     * @see AdviceWithRouteBuilder
     */
    public static RouteDefinition adviceWith(RouteDefinition definition, CamelContext camelContext, RouteBuilder builder) throws Exception {
        ObjectHelper.notNull(definition, "RouteDefinition");
        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notNull(builder, "RouteBuilder");

        if (definition.getInput() == null) {
            throw new IllegalArgumentException("RouteDefinition has no input");
        }
        return new RouteReifier(camelContext, definition).adviceWith(builder);
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

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once
     * (you can of course advice multiple routes). If you do it multiple times,
     * then it may not work as expected, especially when any kind of error
     * handling is involved. The Camel team plan for Camel 3.0 to support this
     * as internal refactorings in the routing engine is needed to support this
     * properly.
     * <p/>
     * You can use a regular {@link RouteBuilder} but the specialized
     * {@link org.apache.camel.builder.AdviceWithRouteBuilder} has additional
     * features when using the
     * <a href="http://camel.apache.org/advicewith.html">advice with</a>
     * feature. We therefore suggest you to use the
     * {@link org.apache.camel.builder.AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on
     * completions etc. configured from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes
     * to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start
     * this new advised route.
     *
     * @param builder the route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     * @see AdviceWithRouteBuilder
     */
    @SuppressWarnings("deprecation")
    public RouteDefinition adviceWith(RouteBuilder builder) throws Exception {
        ObjectHelper.notNull(builder, "RouteBuilder");

        log.debug("AdviceWith route before: {}", this);
        ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
        Model model = camelContext.getExtension(Model.class);

        // inject this route into the advice route builder so it can access this route
        // and offer features to manipulate the route directly
        if (builder instanceof AdviceWithRouteBuilder) {
            AdviceWithRouteBuilder arb = (AdviceWithRouteBuilder)builder;
            arb.setOriginalRoute(definition);
        }

        // configure and prepare the routes from the builder
        RoutesDefinition routes = builder.configureRoutes(camelContext);

        // was logging enabled or disabled
        boolean logRoutesAsXml = true;
        if (builder instanceof AdviceWithRouteBuilder) {
            AdviceWithRouteBuilder arb = (AdviceWithRouteBuilder)builder;
            logRoutesAsXml = arb.isLogRouteAsXml();
        }

        log.debug("AdviceWith routes: {}", routes);

        // we can only advice with a route builder without any routes
        if (!builder.getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("You can only advice from a RouteBuilder which has no existing routes. Remove all routes from the route builder.");
        }
        // we can not advice with error handlers (if you added a new error
        // handler in the route builder)
        // we must check the error handler on builder is not the same as on
        // camel context, as that would be the default
        // context scoped error handler, in case no error handlers was
        // configured
        if (builder.getRouteCollection().getErrorHandlerFactory() != null
            && ecc.getErrorHandlerFactory() != builder.getRouteCollection().getErrorHandlerFactory()) {
            throw new IllegalArgumentException("You can not advice with error handlers. Remove the error handlers from the route builder.");
        }

        String beforeAsXml = null;
        if (logRoutesAsXml && log.isInfoEnabled()) {
            try {
                beforeAsXml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, definition);
            } catch (Throwable e) {
                // ignore, it may be due jaxb is not on classpath etc
            }
        }

        // stop and remove this existing route
        model.removeRouteDefinition(definition);

        // any advice with tasks we should execute first?
        if (builder instanceof AdviceWithRouteBuilder) {
            List<AdviceWithTask> tasks = ((AdviceWithRouteBuilder) builder).getAdviceWithTasks();
            for (AdviceWithTask task : tasks) {
                task.task();
            }
        }

        // now merge which also ensures that interceptors and the likes get
        // mixed in correctly as well
        RouteDefinition merged = routes.route(definition);

        // add the new merged route
        model.getRouteDefinitions().add(0, merged);

        // log the merged route at info level to make it easier to end users to
        // spot any mistakes they may have made
        if (log.isInfoEnabled()) {
            log.info("AdviceWith route after: {}", merged);
        }

        if (beforeAsXml != null && logRoutesAsXml && log.isInfoEnabled()) {
            try {
                String afterAsXml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, merged);
                log.info("Adviced route before/after as XML:\n{}\n{}", beforeAsXml, afterAsXml);
            } catch (Throwable e) {
                // ignore, it may be due jaxb is not on classpath etc
            }
        }

        // If the camel context is started then we start the route
        if (camelContext.isStarted()) {
            model.addRouteDefinition(merged);
        }
        return merged;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected Route doCreateRoute() throws Exception {
        // resolve endpoint
        Endpoint endpoint = definition.getInput().getEndpoint();
        if (endpoint == null) {
            EndpointConsumerBuilder def = definition.getInput().getEndpointConsumerBuilder();
            if (def != null) {
                endpoint = def.resolve(camelContext);
            } else {
                endpoint = resolveEndpoint(definition.getInput().getEndpointUri());
            }
        }

        // create route
        String id = definition.idOrCreate(camelContext.adapt(ExtendedCamelContext.class).getNodeIdFactory());
        String desc = RouteDefinitionHelper.getRouteMessage(definition.toString());
        DefaultRoute route = new DefaultRoute(camelContext, definition, id, desc, endpoint);

        // configure error handler
        route.setErrorHandlerFactory(definition.getErrorHandlerFactory());

        // configure tracing
        if (definition.getTrace() != null) {
            Boolean isTrace = parseBoolean(definition.getTrace());
            if (isTrace != null) {
                route.setTracing(isTrace);
                if (isTrace) {
                    log.debug("Tracing is enabled on route: {}", definition.getId());
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
                    log.debug("Message history is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure Log EIP mask
        if (definition.getLogMask() != null) {
            Boolean isLogMask = parseBoolean(definition.getLogMask());
            if (isLogMask != null) {
                route.setLogMask(isLogMask);
                if (isLogMask) {
                    log.debug("Security mask for Logging is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure stream caching
        if (definition.getStreamCache() != null) {
            Boolean isStreamCache = parseBoolean(definition.getStreamCache());
            if (isStreamCache != null) {
                route.setStreamCaching(isStreamCache);
                if (isStreamCache) {
                    log.debug("StreamCaching is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure delayer
        if (definition.getDelayer() != null) {
            Long delayer = parseLong(definition.getDelayer());
            if (delayer != null) {
                route.setDelayer(delayer);
                if (delayer > 0) {
                    log.debug("Delayer is enabled with: {} ms. on route: {}", delayer, definition.getId());
                } else {
                    log.debug("Delayer is disabled on route: {}", definition.getId());
                }
            }
        }

        // configure route policy
        if (definition.getRoutePolicies() != null && !definition.getRoutePolicies().isEmpty()) {
            for (RoutePolicy policy : definition.getRoutePolicies()) {
                log.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                route.getRoutePolicyList().add(policy);
            }
        }
        if (definition.getRoutePolicyRef() != null) {
            StringTokenizer policyTokens = new StringTokenizer(definition.getRoutePolicyRef(), ",");
            while (policyTokens.hasMoreTokens()) {
                String ref = policyTokens.nextToken().trim();
                RoutePolicy policy = mandatoryLookup(ref, RoutePolicy.class);
                log.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                route.getRoutePolicyList().add(policy);
            }
        }
        if (camelContext.getRoutePolicyFactories() != null) {
            for (RoutePolicyFactory factory : camelContext.getRoutePolicyFactories()) {
                RoutePolicy policy = factory.createRoutePolicy(camelContext, definition.getId(), definition);
                if (policy != null) {
                    log.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                    route.getRoutePolicyList().add(policy);
                }
            }
        }

        // configure auto startup
        Boolean isAutoStartup = parseBoolean(definition.getAutoStartup());

        // configure startup order
        Integer startupOrder = definition.getStartupOrder();

        // configure shutdown
        if (definition.getShutdownRoute() != null) {
            log.debug("Using ShutdownRoute {} on route: {}", definition.getShutdownRoute(), definition.getId());
            route.setShutdownRoute(parse(ShutdownRoute.class, definition.getShutdownRoute()));
        }
        if (definition.getShutdownRunningTask() != null) {
            log.debug("Using ShutdownRunningTask {} on route: {}", definition.getShutdownRunningTask(), definition.getId());
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
            Exception cause = new IllegalArgumentException("Route " + definition.getId() + " has no output processors."
                                                           + " You need to add outputs to the route such as to(\"log:foo\").");
            throw new FailedToCreateRouteException(definition.getId(), definition.toString(), at, cause);
        }

        List<ProcessorDefinition<?>> list = new ArrayList<>(definition.getOutputs());
        for (ProcessorDefinition<?> output : list) {
            try {
                ProcessorReifier.reifier(route, output).addRoutes();
            } catch (Exception e) {
                throw new FailedToCreateRouteException(definition.getId(), definition.toString(), output.toString(), e);
            }
        }

        // now lets turn all of the event driven consumer processors into a single route
        List<Processor> eventDrivenProcessors = route.getEventDrivenProcessors();
        if (eventDrivenProcessors.isEmpty()) {
            return null;
        }

        // Set route properties
        Map<String, Object> routeProperties = computeRouteProperties();

        // always use an pipeline even if there are only 1 processor as the pipeline
        // handles preparing the response from the exchange in regard to IN vs OUT messages etc
        Processor target = new Pipeline(camelContext, eventDrivenProcessors);

        // and wrap it in a unit of work so the UoW is on the top, so the entire route will be in the same UoW
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, target);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));

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

            internal.addAdvice(new CamelInternalProcessor.RoutePolicyAdvice(routePolicyList));
        }

        // wrap in route inflight processor to track number of inflight exchanges for the route
        internal.addAdvice(new CamelInternalProcessor.RouteInflightRepositoryAdvice(camelContext.getInflightRepository(), route.getRouteId()));

        // wrap in JMX instrumentation processor that is used for performance stats
        ManagementInterceptStrategy managementInterceptStrategy = route.getManagementInterceptStrategy();
        if (managementInterceptStrategy != null) {
            internal.addAdvice(CamelInternalProcessor.wrap(managementInterceptStrategy.createProcessor("route")));
        }

        // wrap in route lifecycle
        internal.addAdvice(new CamelInternalProcessor.RouteLifecycleAdvice());

        // add advices
        if (definition.getRestBindingDefinition() != null) {
            try {
                internal.addAdvice(new RestBindingReifier(route, definition.getRestBindingDefinition()).createRestBindingAdvice());
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

        // and create the route that wraps all of this
        route.setProcessor(internal);
        route.getProperties().putAll(routeProperties);
        route.setStartupOrder(startupOrder);
        if (isAutoStartup != null) {
            log.debug("Using AutoStartup {} on route: {}", isAutoStartup, definition.getId());
            route.setAutoStartup(isAutoStartup);
        }

        // after the route is created then set the route on the policy processor so we get hold of it
        CamelInternalProcessor.RoutePolicyAdvice task = internal.getAdvice(CamelInternalProcessor.RoutePolicyAdvice.class);
        if (task != null) {
            task.setRoute(route);
        }
        CamelInternalProcessor.RouteLifecycleAdvice task2 = internal.getAdvice(CamelInternalProcessor.RouteLifecycleAdvice.class);
        if (task2 != null) {
            task2.setRoute(route);
        }

        // invoke init on route policy
        if (routePolicyList != null && !routePolicyList.isEmpty()) {
            for (RoutePolicy policy : routePolicyList) {
                policy.onInit(route);
            }
        }

        return route;
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
        String rest = Boolean.toString(definition.isRest() != null && definition.isRest());
        routeProperties.put(Route.REST_PROPERTY, rest);

        List<PropertyDefinition> properties = definition.getRouteProperties();
        if (properties != null) {

            for (PropertyDefinition prop : properties) {
                try {
                    final String key = parseString(prop.getKey());
                    final String val = parseString(prop.getValue());
                    for (String property : RESERVED_PROPERTIES) {
                        if (property.equalsIgnoreCase(key)) {
                            throw new IllegalArgumentException("Cannot set route property " + property + " as it is a reserved property");
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

}
