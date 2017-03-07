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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;

/**
 * A series of Camel routes
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routes")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutesDefinition extends OptionalIdentifiedDefinition<RoutesDefinition> implements RouteContainer {
    @XmlElementRef
    private List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
    @XmlTransient
    private List<InterceptDefinition> intercepts = new ArrayList<InterceptDefinition>();
    @XmlTransient
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<InterceptFromDefinition>();
    @XmlTransient
    private List<InterceptSendToEndpointDefinition> interceptSendTos = new ArrayList<InterceptSendToEndpointDefinition>();
    @XmlTransient
    private List<OnExceptionDefinition> onExceptions = new ArrayList<OnExceptionDefinition>();
    @XmlTransient
    private List<OnCompletionDefinition> onCompletions = new ArrayList<OnCompletionDefinition>();
    @XmlTransient
    private ModelCamelContext camelContext;
    @XmlTransient
    private ErrorHandlerFactory errorHandlerBuilder;

    public RoutesDefinition() {
    }

    @Override
    public String toString() {
        return "Routes: " + routes;
    }

    public String getLabel() {
        return "Route " + getId();
    }

    // Properties
    //-----------------------------------------------------------------------
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

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

    public ModelCamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(ModelCamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ErrorHandlerFactory getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    public void setErrorHandlerBuilder(ErrorHandlerFactory errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Creates a new route
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
     * @param uri  the from uri
     * @return the builder
     */
    public RouteDefinition from(@AsEndpointUri String uri) {
        RouteDefinition route = createRoute();
        route.from(uri);
        return route(route);
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param endpoint  the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        RouteDefinition route = createRoute();
        route.from(endpoint);
        return route(route);
    }

    /**
     * Creates a new route from the given URI inputs
     *
     * @param uris  the from uri
     * @return the builder
     */
    public RouteDefinition from(@AsEndpointUri String... uris) {
        RouteDefinition route = createRoute();
        route.from(uris);
        return route(route);
    }

    /**
     * Creates a new route from the given endpoints
     *
     * @param endpoints  the from endpoints
     * @return the builder
     */
    public RouteDefinition from(Endpoint... endpoints) {
        RouteDefinition route = createRoute();
        route.from(endpoints);
        return route(route);
    }

    /**
     * Creates a new route using the given route
     *
     * @param route the route
     * @return the builder
     */
    public RouteDefinition route(RouteDefinition route) {
        // must prepare the route before we can add it to the routes list
        RouteDefinitionHelper.prepareRoute(getCamelContext(), route, getOnExceptions(), getIntercepts(), getInterceptFroms(),
                getInterceptSendTos(), getOnCompletions());
        getRoutes().add(route);
        // mark this route as prepared
        route.markPrepared();
        return route;
    }

    /**
     * Creates and adds an interceptor that is triggered on every step in the route
     * processing.
     *
     * @return the interceptor builder to configure
     */
    public InterceptDefinition intercept() {
        InterceptDefinition answer = new InterceptDefinition();
        getIntercepts().add(0, answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is triggered when an exchange
     * is received as input to any routes (eg from all the <tt>from</tt>)
     *
     * @return the interceptor builder to configure
     */
    public InterceptFromDefinition interceptFrom() {
        InterceptFromDefinition answer = new InterceptFromDefinition();
        getInterceptFroms().add(answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is triggered when an exchange is received
     * as input to the route defined with the given endpoint (eg from the <tt>from</tt>)
     *
     * @param uri uri of the endpoint
     * @return the interceptor builder to configure
     */
    public InterceptFromDefinition interceptFrom(@AsEndpointUri final String uri) {
        InterceptFromDefinition answer = new InterceptFromDefinition(uri);
        getInterceptFroms().add(answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is triggered when an exchange is
     * send to the given endpoint
     *
     * @param uri uri of the endpoint
     * @return  the builder
     */
    public InterceptSendToEndpointDefinition interceptSendToEndpoint(@AsEndpointUri final String uri) {
        InterceptSendToEndpointDefinition answer = new InterceptSendToEndpointDefinition(uri);
        getInterceptSendTos().add(answer);
        return answer;
    }

    /**
     * Adds an on exception
     * 
     * @param exception  the exception
     * @return the builder
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
        getOnCompletions().add(answer);
        return answer;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected RouteDefinition createRoute() {
        RouteDefinition route = new RouteDefinition();
        ErrorHandlerFactory handler = getErrorHandlerBuilder();
        if (handler != null) {
            route.setErrorHandlerBuilderIfNull(handler);
        }
        return route;
    }
}
