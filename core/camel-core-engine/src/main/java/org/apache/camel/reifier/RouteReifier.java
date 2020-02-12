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
import java.util.List;
import java.util.StringTokenizer;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.AdviceWithTask;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.processor.ContractAdvice;
import org.apache.camel.reifier.rest.RestBindingReifier;
import org.apache.camel.spi.Contract;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class RouteReifier extends ProcessorReifier<RouteDefinition> {

    public RouteReifier(RouteContext routeContext, ProcessorDefinition<?> definition) {
        super(routeContext, (RouteDefinition) definition);
    }

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
            return doCreateRoute(camelContext, routeContext);
        } catch (FailedToCreateRouteException e) {
            throw e;
        } catch (Exception e) {
            // wrap in exception which provide more details about which route
            // was failing
            throw new FailedToCreateRouteException(definition.getId(), definition.toString(), e);
        }
    }

    public Endpoint resolveEndpoint(String uri) throws NoSuchEndpointException {
        ObjectHelper.notNull(camelContext, "CamelContext");
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri);
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

        // inject this route into the advice route builder so it can access this route
        // and offer features to manipulate the route directly
        boolean logRoutesAsXml = true;
        if (builder instanceof AdviceWithRouteBuilder) {
            AdviceWithRouteBuilder arb = (AdviceWithRouteBuilder)builder;
            arb.setOriginalRoute(definition);
            logRoutesAsXml = arb.isLogRouteAsXml();
        }

        // configure and prepare the routes from the builder
        RoutesDefinition routes = builder.configureRoutes(camelContext);

        log.debug("AdviceWith routes: {}", routes);

        // we can only advice with a route builder without any routes
        if (!builder.getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("You can only advice from a RouteBuilder which has no existing routes." + " Remove all routes from the route builder.");
        }
        // we can not advice with error handlers (if you added a new error
        // handler in the route builder)
        // we must check the error handler on builder is not the same as on
        // camel context, as that would be the default
        // context scoped error handler, in case no error handlers was
        // configured
        if (builder.getRouteCollection().getErrorHandlerFactory() != null
            && camelContext.adapt(ExtendedCamelContext.class).getErrorHandlerFactory() != builder.getRouteCollection().getErrorHandlerFactory()) {
            throw new IllegalArgumentException("You can not advice with error handlers. Remove the error handlers from the route builder.");
        }

        String beforeAsXml = null;
        if (logRoutesAsXml && log.isInfoEnabled()) {
            try {
                ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                beforeAsXml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, definition);
            } catch (Throwable e) {
                // ignore, it may be due jaxb is not on classpath etc
            }
        }

        // stop and remove this existing route
        camelContext.getExtension(Model.class).removeRouteDefinition(definition);

        // any advice with tasks we should execute first?
        if (builder instanceof AdviceWithRouteBuilder) {
            List<AdviceWithTask> tasks = ((AdviceWithRouteBuilder)builder).getAdviceWithTasks();
            for (AdviceWithTask task : tasks) {
                task.task();
            }
        }

        // now merge which also ensures that interceptors and the likes get
        // mixed in correctly as well
        RouteDefinition merged = routes.route(definition);

        // add the new merged route
        camelContext.getExtension(Model.class).getRouteDefinitions().add(0, merged);

        // log the merged route at info level to make it easier to end users to
        // spot any mistakes they may have made
        if (log.isInfoEnabled()) {
            log.info("AdviceWith route after: {}", merged);
        }

        if (beforeAsXml != null && logRoutesAsXml && log.isInfoEnabled()) {
            try {
                ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                String afterAsXml = ecc.getModelToXMLDumper().dumpModelAsXml(camelContext, merged);
                log.info("Adviced route before/after as XML:\n{}\n{}", beforeAsXml, afterAsXml);
            } catch (Throwable e) {
                // ignore, it may be due jaxb is not on classpath etc
            }
        }

        // If the camel context is started then we start the route
        if (camelContext.isStarted()) {
            camelContext.getExtension(Model.class).addRouteDefinition(merged);
        }
        return merged;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected Route doCreateRoute(CamelContext camelContext, RouteContext routeContext) throws Exception {
        // configure error handler
        routeContext.setErrorHandlerFactory(definition.getErrorHandlerFactory());

        // configure tracing
        if (definition.getTrace() != null) {
            Boolean isTrace = CamelContextHelper.parseBoolean(camelContext, definition.getTrace());
            if (isTrace != null) {
                routeContext.setTracing(isTrace);
                if (isTrace) {
                    log.debug("Tracing is enabled on route: {}", definition.getId());
                    // tracing is added in the DefaultChannel so we can enable
                    // it on the fly
                }
            }
        }

        // configure message history
        if (definition.getMessageHistory() != null) {
            Boolean isMessageHistory = CamelContextHelper.parseBoolean(camelContext, definition.getMessageHistory());
            if (isMessageHistory != null) {
                routeContext.setMessageHistory(isMessageHistory);
                if (isMessageHistory) {
                    log.debug("Message history is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure Log EIP mask
        if (definition.getLogMask() != null) {
            Boolean isLogMask = CamelContextHelper.parseBoolean(camelContext, definition.getLogMask());
            if (isLogMask != null) {
                routeContext.setLogMask(isLogMask);
                if (isLogMask) {
                    log.debug("Security mask for Logging is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure stream caching
        if (definition.getStreamCache() != null) {
            Boolean isStreamCache = CamelContextHelper.parseBoolean(camelContext, definition.getStreamCache());
            if (isStreamCache != null) {
                routeContext.setStreamCaching(isStreamCache);
                if (isStreamCache) {
                    log.debug("StreamCaching is enabled on route: {}", definition.getId());
                }
            }
        }

        // configure delayer
        if (definition.getDelayer() != null) {
            Long delayer = CamelContextHelper.parseLong(camelContext, definition.getDelayer());
            if (delayer != null) {
                routeContext.setDelayer(delayer);
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
                routeContext.getRoutePolicyList().add(policy);
            }
        }
        if (definition.getRoutePolicyRef() != null) {
            StringTokenizer policyTokens = new StringTokenizer(definition.getRoutePolicyRef(), ",");
            while (policyTokens.hasMoreTokens()) {
                String ref = policyTokens.nextToken().trim();
                RoutePolicy policy = CamelContextHelper.mandatoryLookup(camelContext, ref, RoutePolicy.class);
                log.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                routeContext.getRoutePolicyList().add(policy);
            }
        }
        if (camelContext.getRoutePolicyFactories() != null) {
            for (RoutePolicyFactory factory : camelContext.getRoutePolicyFactories()) {
                RoutePolicy policy = factory.createRoutePolicy(camelContext, definition.getId(), definition);
                if (policy != null) {
                    log.debug("RoutePolicy is enabled: {} on route: {}", policy, definition.getId());
                    routeContext.getRoutePolicyList().add(policy);
                }
            }
        }

        // configure auto startup
        Boolean isAutoStartup = CamelContextHelper.parseBoolean(camelContext, definition.getAutoStartup());
        if (isAutoStartup != null) {
            log.debug("Using AutoStartup {} on route: {}", isAutoStartup, definition.getId());
            routeContext.setAutoStartup(isAutoStartup);
        }

        // configure startup order
        if (definition.getStartupOrder() != null) {
            routeContext.setStartupOrder(definition.getStartupOrder());
        }

        // configure shutdown
        if (definition.getShutdownRoute() != null) {
            log.debug("Using ShutdownRoute {} on route: {}", definition.getShutdownRoute(), definition.getId());
            routeContext.setShutdownRoute(parse(ShutdownRoute.class, definition.getShutdownRoute()));
        }
        if (definition.getShutdownRunningTask() != null) {
            log.debug("Using ShutdownRunningTask {} on route: {}", definition.getShutdownRunningTask(), definition.getId());
            routeContext.setShutdownRunningTask(parse(ShutdownRunningTask.class, definition.getShutdownRunningTask()));
        }

        // should inherit the intercept strategies we have defined
        routeContext.setInterceptStrategies(definition.getInterceptStrategies());

        // resolve endpoint
        Endpoint endpoint = definition.getInput().getEndpoint();
        if (endpoint == null) {
            EndpointConsumerBuilder def = definition.getInput().getEndpointConsumerBuilder();
            if (def != null) {
                endpoint = def.resolve(camelContext);
            } else {
                endpoint = routeContext.resolveEndpoint(definition.getInput().getEndpointUri());
            }
        }
        routeContext.setEndpoint(endpoint);

        // notify route context created
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRouteContextCreate(routeContext);
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
                ProcessorReifier.reifier(routeContext, output).addRoutes();
            } catch (Exception e) {
                throw new FailedToCreateRouteException(definition.getId(), definition.toString(), output.toString(), e);
            }
        }

        if (definition.getRestBindingDefinition() != null) {
            try {
                routeContext.addAdvice(new RestBindingReifier(routeContext, definition.getRestBindingDefinition()).createRestBindingAdvice());
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
            routeContext.addAdvice(new ContractAdvice(contract));
            // make sure to enable data type as its in use when using
            // input/output types on routes
            camelContext.setUseDataType(true);
        }

        // Set route properties
        routeContext.addProperty(Route.ID_PROPERTY, definition.getId());
        routeContext.addProperty(Route.CUSTOM_ID_PROPERTY, Boolean.toString(definition.hasCustomIdAssigned()));
        routeContext.addProperty(Route.PARENT_PROPERTY, Integer.toHexString(definition.hashCode()));
        routeContext.addProperty(Route.DESCRIPTION_PROPERTY, definition.getDescriptionText());
        if (definition.getGroup() != null) {
            routeContext.addProperty(Route.GROUP_PROPERTY, definition.getGroup());
        }
        String rest = Boolean.toString(definition.isRest() != null && definition.isRest());
        routeContext.addProperty(Route.REST_PROPERTY, rest);

        List<PropertyDefinition> properties = definition.getRouteProperties();
        if (properties != null) {
            final String[] reservedProperties = new String[] {Route.ID_PROPERTY, Route.CUSTOM_ID_PROPERTY, Route.PARENT_PROPERTY, Route.DESCRIPTION_PROPERTY, Route.GROUP_PROPERTY,
                                                              Route.REST_PROPERTY};

            for (PropertyDefinition prop : properties) {
                try {
                    final String key = CamelContextHelper.parseText(camelContext, prop.getKey());
                    final String val = CamelContextHelper.parseText(camelContext, prop.getValue());

                    for (String property : reservedProperties) {
                        if (property.equalsIgnoreCase(key)) {
                            throw new IllegalArgumentException("Cannot set route property " + property + " as it is a reserved property");
                        }
                    }

                    routeContext.addProperty(key, val);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }
        }

        return routeContext.commit();
    }

}
