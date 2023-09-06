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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A series of Camel routes
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routes")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutesDefinition extends OptionalIdentifiedDefinition<RoutesDefinition>
        implements RouteContainer, CamelContextAware, ResourceAware {

    private static final Logger LOG = LoggerFactory.getLogger(RoutesDefinition.class);

    @XmlTransient
    private List<InterceptDefinition> intercepts = new ArrayList<>();
    @XmlTransient
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<>();
    @XmlTransient
    private List<InterceptSendToEndpointDefinition> interceptSendTos = new ArrayList<>();
    @XmlTransient
    private List<OnExceptionDefinition> onExceptions = new ArrayList<>();
    @XmlTransient
    private List<OnCompletionDefinition> onCompletions = new ArrayList<>();
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ErrorHandlerFactory errorHandlerFactory;
    @XmlTransient
    private Resource resource;

    @XmlElementRef
    private List<RouteDefinition> routes = new ArrayList<>();

    public RoutesDefinition() {
    }

    @Override
    public String toString() {
        return "Routes: " + routes;
    }

    @Override
    public String getShortName() {
        return "routes";
    }

    @Override
    public String getLabel() {
        return "Routes " + getId();
    }

    // Properties
    // -----------------------------------------------------------------------
    @Override
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    @Override
    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    public List<InterceptFromDefinition> getInterceptFroms() {
        return interceptFroms;
    }

    public void setInterceptFroms(List<InterceptFromDefinition> interceptFroms) {
        this.interceptFroms = interceptFroms;
    }

    public List<InterceptSendToEndpointDefinition> getInterceptSendTos() {
        return interceptSendTos;
    }

    public void setInterceptSendTos(List<InterceptSendToEndpointDefinition> interceptSendTos) {
        this.interceptSendTos = interceptSendTos;
    }

    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
    }

    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    public List<OnCompletionDefinition> getOnCompletions() {
        return onCompletions;
    }

    public void setOnCompletions(List<OnCompletionDefinition> onCompletions) {
        this.onCompletions = onCompletions;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ErrorHandlerFactory getErrorHandlerFactory() {
        return errorHandlerFactory;
    }

    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerFactory = errorHandlerFactory;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Creates a new route
     *
     * Prefer to use the from methods when creating a new route.
     *
     * @return the builder
     */
    public RouteDefinition route() {
        RouteDefinition route = createRoute();
        return route(route);
    }

    /**
     * Creates a new route from the given URI input
     *
     * @param  uri the from uri
     * @return     the builder
     */
    public RouteDefinition from(@AsEndpointUri String uri) {
        RouteDefinition route = createRoute();
        route.from(uri);
        return route(route);
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param  endpoint the from endpoint
     * @return          the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        RouteDefinition route = createRoute();
        route.from(endpoint);
        return route(route);
    }

    public RouteDefinition from(EndpointConsumerBuilder endpoint) {
        RouteDefinition route = createRoute();
        route.from(endpoint);
        return route(route);
    }

    /**
     * Creates a new route using the given route.
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     *
     * @param  route the route
     * @return       the builder
     */
    public RouteDefinition route(RouteDefinition route) {
        // must set the error handler if not already set on the route
        ErrorHandlerFactory handler = getErrorHandlerFactory();
        if (handler != null) {
            route.setErrorHandlerFactoryIfNull(handler);
        }
        getRoutes().add(route);
        return route;
    }

    public void prepareRoute(RouteDefinition route) {
        if (route.isPrepared()) {
            return;
        }

        // reset before preparing route
        route.resetPrepare();

        // remember the source resource
        route.setResource(resource);

        // merge global and route scoped together
        final AtomicReference<ErrorHandlerDefinition> gcErrorHandler = new AtomicReference<>();
        List<OnExceptionDefinition> oe = new ArrayList<>(onExceptions);
        List<InterceptDefinition> icp = new ArrayList<>(intercepts);
        List<InterceptFromDefinition> ifrom = new ArrayList<>(interceptFroms);
        List<InterceptSendToEndpointDefinition> ito = new ArrayList<>(interceptSendTos);
        List<OnCompletionDefinition> oc = new ArrayList<>(onCompletions);
        if (getCamelContext() != null) {
            List<RouteConfigurationDefinition> globalConfigurations
                    = ((ModelCamelContext) getCamelContext()).getRouteConfigurationDefinitions();
            if (globalConfigurations != null) {
                String[] ids;
                if (route.getRouteConfigurationId() != null) {
                    // if the RouteConfigurationId was configured with property placeholder it should be resolved first
                    // and include properties sources from the template parameters
                    if (route.getTemplateParameters() != null && route.getRouteConfigurationId().startsWith("{{")) {
                        OrderedLocationProperties props = new OrderedLocationProperties();
                        props.putAll("TemplateProperties", new HashMap<>(route.getTemplateParameters()));
                        camelContext.getPropertiesComponent().setLocalProperties(props);
                        try {
                            ids = camelContext.getCamelContextExtension()
                                    .resolvePropertyPlaceholders(route.getRouteConfigurationId(), true)
                                    .split(",");
                        } finally {
                            camelContext.getPropertiesComponent().setLocalProperties(null);
                        }
                    } else {
                        ids = route.getRouteConfigurationId().split(",");
                    }
                } else {
                    ids = new String[] { "*" };
                }

                // if there are multiple ids configured then we should apply in that same order
                for (String id : ids) {
                    // sort according to ordered
                    globalConfigurations.stream().sorted(OrderedComparator.get())
                            .filter(g -> {
                                if (route.getRouteConfigurationId() != null) {
                                    // if the route has a route configuration assigned then use pattern matching
                                    return PatternHelper.matchPattern(g.getId(), id);
                                } else {
                                    // global configurations have no id assigned or is a wildcard
                                    return g.getId() == null || g.getId().equals(id);
                                }
                            })
                            .forEach(g -> {
                                // there can only be one global error handler, so override previous, meaning
                                // that we will pick the last in the sort (take precedence)
                                if (g.getErrorHandler() != null) {
                                    gcErrorHandler.set(g.getErrorHandler());
                                }

                                String aid = g.getId() == null ? "<default>" : g.getId();
                                // remember the id that was used on the route
                                route.addAppliedRouteConfigurationId(aid);
                                oe.addAll(g.getOnExceptions());
                                icp.addAll(g.getIntercepts());
                                ifrom.addAll(g.getInterceptFroms());
                                ito.addAll(g.getInterceptSendTos());
                                oc.addAll(g.getOnCompletions());
                            });
                }

                // set error handler before prepare
                if (errorHandlerFactory == null && gcErrorHandler.get() != null) {
                    ErrorHandlerDefinition ehd = gcErrorHandler.get();
                    route.setErrorHandlerFactoryIfNull(ehd.getErrorHandlerType());
                }
            }
        }

        // if the route does not already have an error handler set then use route configured error handler
        // if one was configured
        ErrorHandlerDefinition ehd = null;
        if (errorHandlerFactory == null && gcErrorHandler.get() != null) {
            ehd = gcErrorHandler.get();
        }

        // must prepare the route before we can add it to the routes list
        RouteDefinitionHelper.prepareRoute(getCamelContext(), route, ehd, oe, icp, ifrom, ito, oc);

        if (LOG.isDebugEnabled() && route.getAppliedRouteConfigurationIds() != null) {
            LOG.debug("Route: {} is using route configurations ids: {}", route.getId(),
                    route.getAppliedRouteConfigurationIds());
        }

        // mark this route as prepared
        route.markPrepared();
    }

    /**
     * Creates and adds an interceptor that is triggered on every step in the route processing.
     *
     * @return the interceptor builder to configure
     */
    public InterceptDefinition intercept() {
        InterceptDefinition answer = new InterceptDefinition();
        getIntercepts().add(0, answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is triggered when an exchange is received as input to any routes (eg from
     * all the <tt>from</tt>)
     *
     * @return the interceptor builder to configure
     */
    public InterceptFromDefinition interceptFrom() {
        InterceptFromDefinition answer = new InterceptFromDefinition();
        getInterceptFroms().add(answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is triggered when an exchange is received as input to the route defined with
     * the given endpoint (eg from the <tt>from</tt>)
     *
     * @param  uri uri of the endpoint
     * @return     the interceptor builder to configure
     */
    public InterceptFromDefinition interceptFrom(@AsEndpointUri final String uri) {
        InterceptFromDefinition answer = new InterceptFromDefinition(uri);
        getInterceptFroms().add(answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is triggered when an exchange is send to the given endpoint
     *
     * @param  uri uri of the endpoint
     * @return     the builder
     */
    public InterceptSendToEndpointDefinition interceptSendToEndpoint(@AsEndpointUri final String uri) {
        InterceptSendToEndpointDefinition answer = new InterceptSendToEndpointDefinition(uri);
        getInterceptSendTos().add(answer);
        return answer;
    }

    /**
     * Adds an on exception
     *
     * @param  exception the exception
     * @return           the builder
     */
    public OnExceptionDefinition onException(Class<? extends Throwable> exception) {
        OnExceptionDefinition answer = new OnExceptionDefinition(exception);
        answer.setRouteScoped(false);
        getOnExceptions().add(answer);
        return answer;
    }

    /**
     * Adds an on completion
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompletion() {
        OnCompletionDefinition answer = new OnCompletionDefinition();
        answer.setRouteScoped(false);
        getOnCompletions().add(answer);
        return answer;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected RouteDefinition createRoute() {
        RouteDefinition route = new RouteDefinition();
        route.setCamelContext(getCamelContext());
        ErrorHandlerFactory handler = getErrorHandlerFactory();
        if (handler != null) {
            route.setErrorHandlerFactoryIfNull(handler);
        }
        if (resource != null) {
            route.setResource(resource);
        }
        return route;
    }
}
