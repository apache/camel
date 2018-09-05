/**
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
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.AdviceWithTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class RouteReifier extends ProcessorReifier<RouteDefinition> {

    public RouteReifier(ProcessorDefinition<?> definition) {
        super((RouteDefinition) definition);
    }

    public static RouteDefinition adviceWith(RouteDefinition definition, CamelContext camelContext, RouteBuilder builder) throws Exception {
        return adviceWith(definition, camelContext.adapt(ModelCamelContext.class), builder);
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is involved.
     * The Camel team plan for Camel 3.0 to support this as internal refactorings in the routing engine is needed to support this properly.
     * <p/>
     * You can use a regular {@link RouteBuilder} but the specialized {@link AdviceWithRouteBuilder}
     * has additional features when using the <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
     * We therefore suggest you to use the {@link AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured
     * from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param definition
     * @param camelContext the camel context
     * @param builder      the route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     * @see AdviceWithRouteBuilder
     */
    public static RouteDefinition adviceWith(RouteDefinition definition, ModelCamelContext camelContext, RouteBuilder builder) throws Exception {
        return new RouteReifier(definition).adviceWith(camelContext, builder);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new UnsupportedOperationException("Not implemented for RouteDefinition");
    }

    public List<RouteContext> addRoutes(ModelCamelContext camelContext, Collection<Route> routes) throws Exception {
        List<RouteContext> answer = new ArrayList<>();

        @SuppressWarnings("deprecation")
        ErrorHandlerFactory handler = camelContext.getErrorHandlerFactory();
        if (handler != null) {
            definition.setErrorHandlerBuilderIfNull(handler);
        }

        for (FromDefinition fromType : definition.getInputs()) {
            RouteContext routeContext;
            try {
                routeContext = addRoutes(camelContext, routes, fromType);
            } catch (FailedToCreateRouteException e) {
                throw e;
            } catch (Exception e) {
                // wrap in exception which provide more details about which route was failing
                throw new FailedToCreateRouteException(definition.getId(), definition.toString(), e);
            }
            answer.add(routeContext);
        }
        return answer;
    }


    public Endpoint resolveEndpoint(CamelContext camelContext, String uri) throws NoSuchEndpointException {
        ObjectHelper.notNull(camelContext, "CamelContext");
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri);
    }

    public RouteDefinition adviceWith(CamelContext camelContext, RouteBuilder builder) throws Exception {
        return adviceWith((ModelCamelContext)camelContext, builder);
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is involved.
     * The Camel team plan for Camel 3.0 to support this as internal refactorings in the routing engine is needed to support this properly.
     * <p/>
     * You can use a regular {@link RouteBuilder} but the specialized {@link org.apache.camel.builder.AdviceWithRouteBuilder}
     * has additional features when using the <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
     * We therefore suggest you to use the {@link org.apache.camel.builder.AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured
     * from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param camelContext the camel context
     * @param builder      the route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     * @see AdviceWithRouteBuilder
     */
    @SuppressWarnings("deprecation")
    public RouteDefinition adviceWith(ModelCamelContext camelContext, RouteBuilder builder) throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notNull(builder, "RouteBuilder");

        log.debug("AdviceWith route before: {}", this);

        // inject this route into the advice route builder so it can access this route
        // and offer features to manipulate the route directly
        if (builder instanceof AdviceWithRouteBuilder) {
            ((AdviceWithRouteBuilder) builder).setOriginalRoute(definition);
        }

        // configure and prepare the routes from the builder
        RoutesDefinition routes = builder.configureRoutes(camelContext);

        log.debug("AdviceWith routes: {}", routes);

        // we can only advice with a route builder without any routes
        if (!builder.getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("You can only advice from a RouteBuilder which has no existing routes."
                    + " Remove all routes from the route builder.");
        }
        // we can not advice with error handlers (if you added a new error handler in the route builder)
        // we must check the error handler on builder is not the same as on camel context, as that would be the default
        // context scoped error handler, in case no error handlers was configured
        if (builder.getRouteCollection().getErrorHandlerFactory() != null
                && camelContext.getErrorHandlerFactory() != builder.getRouteCollection().getErrorHandlerFactory()) {
            throw new IllegalArgumentException("You can not advice with error handlers. Remove the error handlers from the route builder.");
        }

        String beforeAsXml = ModelHelper.dumpModelAsXml(camelContext, definition);

        // stop and remove this existing route
        camelContext.removeRouteDefinition(definition);

        // any advice with tasks we should execute first?
        if (builder instanceof AdviceWithRouteBuilder) {
            List<AdviceWithTask> tasks = ((AdviceWithRouteBuilder) builder).getAdviceWithTasks();
            for (AdviceWithTask task : tasks) {
                task.task();
            }
        }

        // now merge which also ensures that interceptors and the likes get mixed in correctly as well
        RouteDefinition merged = routes.route(definition);

        // add the new merged route
        camelContext.getRouteDefinitions().add(0, merged);

        // log the merged route at info level to make it easier to end users to spot any mistakes they may have made
        log.info("AdviceWith route after: {}", merged);

        String afterAsXml = ModelHelper.dumpModelAsXml(camelContext, merged);
        log.info("Adviced route before/after as XML:\n{}\n{}", beforeAsXml, afterAsXml);

        // If the camel context is started then we start the route
        if (camelContext instanceof StatefulService) {
            StatefulService service = (StatefulService) camelContext;
            if (service.isStarted()) {
                camelContext.adapt(ModelCamelContext.class).addRouteDefinition(merged);
            }
        }
        return merged;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected RouteContext addRoutes(CamelContext camelContext, Collection<Route> routes, FromDefinition fromType) throws Exception {
        RouteContext routeContext = new DefaultRouteContext(camelContext, definition, fromType, routes);

        // configure tracing
        if (definition.getTrace() != null) {
            Boolean isTrace = CamelContextHelper.parseBoolean(camelContext, definition.getTrace());
            if (isTrace != null) {
                routeContext.setTracing(isTrace);
                if (isTrace) {
                    log.debug("Tracing is enabled on route: {}", definition.getId());
                    // tracing is added in the DefaultChannel so we can enable it on the fly
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

        // configure handle fault
        if (definition.getHandleFault() != null) {
            Boolean isHandleFault = CamelContextHelper.parseBoolean(camelContext, definition.getHandleFault());
            if (isHandleFault != null) {
                routeContext.setHandleFault(isHandleFault);
                if (isHandleFault) {
                    log.debug("HandleFault is enabled on route: {}", definition.getId());
                    // only add a new handle fault if not already a global configured on camel context
                    if (HandleFault.getHandleFault(camelContext) == null) {
                        definition.addInterceptStrategy(new HandleFault());
                    }
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

        // configure shutdown
        if (definition.getShutdownRoute() != null) {
            log.debug("Using ShutdownRoute {} on route: {}", definition.getShutdownRoute(), definition.getId());
            routeContext.setShutdownRoute(definition.getShutdownRoute());
        }
        if (definition.getShutdownRunningTask() != null) {
            log.debug("Using ShutdownRunningTask {} on route: {}", definition.getShutdownRunningTask(), definition.getId());
            routeContext.setShutdownRunningTask(definition.getShutdownRunningTask());
        }

        // should inherit the intercept strategies we have defined
        routeContext.setInterceptStrategies(definition.getInterceptStrategies());
        // force endpoint resolution
        routeContext.getEndpoint();
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRouteContextCreate(routeContext);
        }

        // validate route has output processors
        if (!ProcessorDefinitionHelper.hasOutputs(definition.getOutputs(), true)) {
            RouteDefinition route = (RouteDefinition) routeContext.getRoute();
            String at = fromType.toString();
            Exception cause = new IllegalArgumentException("Route " + route.getId() + " has no output processors."
                    + " You need to add outputs to the route such as to(\"log:foo\").");
            throw new FailedToCreateRouteException(route.getId(), route.toString(), at, cause);
        }

        List<ProcessorDefinition<?>> list = new ArrayList<>(definition.getOutputs());
        for (ProcessorDefinition<?> output : list) {
            try {
                ProcessorReifier.reifier(output).addRoutes(routeContext, routes);
            } catch (Exception e) {
                RouteDefinition route = (RouteDefinition) routeContext.getRoute();
                throw new FailedToCreateRouteException(route.getId(), route.toString(), output.toString(), e);
            }
        }

        routeContext.commit();
        return routeContext;
    }


}
