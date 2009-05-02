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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.Routes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is
 * used to build {@link org.apache.camel.impl.DefaultRoute} instances in a {@link CamelContext} for smart routing.
 *
 * @version $Revision$
 */
public abstract class RouteBuilder extends BuilderSupport implements Routes {
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private RoutesDefinition routeCollection = new RoutesDefinition();
    private List<Route> routes = new ArrayList<Route>();

    public RouteBuilder() {
        this(null);
    }

    public RouteBuilder(CamelContext context) {
        super(context);
    }

    @Override
    public String toString() {
        return routeCollection.toString();
    }

    /**
     * <b>Called on initialization to build the routes using the fluent builder syntax.</b>
     * <p/>
     * This is a central method for RouteBuilder implementations to implement
     * the routes using the Java fluent builder syntax.
     *
     * @throws Exception can be thrown during configuration
     */
    public abstract void configure() throws Exception;

    /**
     * Creates a new route from the given URI input
     *
     * @param uri  the from uri
     * @return the builder
     */
    public RouteDefinition from(String uri) {
        routeCollection.setCamelContext(getContext());
        RouteDefinition answer = routeCollection.from(uri);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given URI input
     *
     * @param uri  the String formatted from uri
     * @param args arguments for the string formatting of the uri
     * @return the builder
     */
    public RouteDefinition fromF(String uri, Object... args) {
        routeCollection.setCamelContext(getContext());
        RouteDefinition answer = routeCollection.from(String.format(uri, args));
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param endpoint  the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        routeCollection.setCamelContext(getContext());
        RouteDefinition answer = routeCollection.from(endpoint);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given URIs input
     *
     * @param uris  the from uris
     * @return the builder
     */
    public RouteDefinition from(String... uris) {
        routeCollection.setCamelContext(getContext());
        RouteDefinition answer = routeCollection.from(uris);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param endpoints  the from endpoints
     * @return the builder
     */
    public RouteDefinition from(Endpoint... endpoints) {
        routeCollection.setCamelContext(getContext());
        RouteDefinition answer = routeCollection.from(endpoints);
        configureRoute(answer);
        return answer;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder
     *
     * @param errorHandlerBuilder  the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public RouteBuilder errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        routeCollection.setCamelContext(getContext());
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Adds a route for an interceptor; use the {@link org.apache.camel.model.ProcessorDefinition#proceed()} method
     * to continue processing the underlying route being intercepted.
     *
     * @return the builder
     */
    public InterceptDefinition intercept() {
        routeCollection.setCamelContext(getContext());
        return routeCollection.intercept();
    }

    /**
     * Adds a route for an interceptor; use the {@link org.apache.camel.model.ProcessorDefinition#proceed()} method
     * to continue processing the underlying route being intercepted.
     *
     * @return the builder
     */
    public InterceptFromDefinition interceptFrom() {
        routeCollection.setCamelContext(getContext());
        return routeCollection.interceptFrom();
    }

    /**
     * Adds a route for an interceptor; use the {@link org.apache.camel.model.ProcessorDefinition#proceed()} method
     * to continue processing the underlying route being intercepted.
     *
     * @param uri  endpoint uri
     * @return the builder
     */
    public InterceptFromDefinition interceptFrom(String uri) {
        routeCollection.setCamelContext(getContext());
        return routeCollection.interceptFrom(uri);
    }

    /**
     * Applies a route for an interceptor if an exchange is send to the given endpoint
     *
     * @param uri  endpoint uri
     * @return the builder
     */
    public InterceptSendToEndpointDefinition interceptSendToEndpoint(String uri) {
        routeCollection.setCamelContext(getContext());
        return routeCollection.interceptSendToEndpoint(uri);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
     *
     * @param exception exception to catch
     * @return the builder
     */
    public OnExceptionDefinition onException(Class exception) {
        routeCollection.setCamelContext(getContext());
        return routeCollection.onException(exception);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
     *
     * @param exceptions list of exceptions to catch
     * @return the builder
     */
    public OnExceptionDefinition onException(Class... exceptions) {
        OnExceptionDefinition last = null;
        for (Class ex : exceptions) {
            last = last == null ? onException(ex) : last.onException(ex);
        }
        return last != null ? last : onException(Exception.class);
    }
    
    // Properties
    // -----------------------------------------------------------------------
    public CamelContext getContext() {
        CamelContext context = super.getContext();
        if (context == null) {
            context = createContainer();
            setContext(context);
        }
        return context;
    }

    /**
     * Uses {@link org.apache.camel.CamelContext#getRoutes()} to return the routes in the context.
     */
    public List<Route> getRouteList() throws Exception {
        checkInitialized();
        return routes;
    }

    @Override
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        super.setErrorHandlerBuilder(errorHandlerBuilder);
        routeCollection.setErrorHandlerBuilder(getErrorHandlerBuilder());
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected void checkInitialized() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            // Set the CamelContext ErrorHandler here
            CamelContext camelContext = getContext();
            if (camelContext.getErrorHandlerBuilder() != null) {
                setErrorHandlerBuilder(camelContext.getErrorHandlerBuilder());
            }
            configure();
            populateRoutes(routes);
        }
    }

    protected void populateRoutes(List<Route> routes) throws Exception {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        routeCollection.setCamelContext(camelContext);
        camelContext.addRouteDefinitions(routeCollection.getRoutes());
    }

    public void setRouteCollection(RoutesDefinition routeCollection) {
        this.routeCollection = routeCollection;
    }

    public RoutesDefinition getRouteCollection() {
        return this.routeCollection;
    }

    /**
     * Factory method
     */
    protected CamelContext createContainer() {
        return new DefaultCamelContext();
    }

    protected void configureRoute(RouteDefinition route) {
        route.setGroup(getClass().getName());
    }

    /**
     * Adds a collection of routes to this context
     *
     * @throws Exception if the routes could not be created for whatever reason
     */
    protected void addRoutes(Routes routes) throws Exception {
        getContext().addRoutes(routes);
    }
}
