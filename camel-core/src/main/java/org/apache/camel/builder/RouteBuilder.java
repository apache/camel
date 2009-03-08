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
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.Routes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.interceptor.StreamCachingInterceptor;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is
 * used to build {@link Route} instances in a {@link CamelContext} for smart routing.
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
        RouteDefinition answer = routeCollection.from(uri);
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
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures whether or not the <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * is inherited by every processing node (or just the top most one)
     *
     * @param inherit  whether error handlers should be inherited or not
     * @return the current builder
     */
    public RouteBuilder inheritErrorHandler(boolean inherit) {
        routeCollection.setInheritErrorHandlerFlag(inherit);
        return this;
    }

    /**
     * Adds the given interceptor to this route
     *
     * @param interceptor  the interceptor
     * @return the builder
     */
    public RouteBuilder intercept(DelegateProcessor interceptor) {
        routeCollection.intercept(interceptor);
        return this;
    }

    /**
     * Adds a route for an interceptor; use the {@link ProcessorDefinition#proceed()} method
     * to continue processing the underlying route being intercepted.
     * @return the builder
     */
    public InterceptDefinition intercept() {
        return routeCollection.intercept();
    }

    /**
     * Applies a route for an interceptor if the given predicate is true
     * otherwise the interceptor route is not applied
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public ChoiceDefinition intercept(Predicate predicate) {
        return routeCollection.intercept(predicate);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
     *
     * @param exception exception to catch
     * @return the builder
     */
    public OnExceptionDefinition onException(Class exception) {
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
    public void setInheritErrorHandler(boolean inheritErrorHandler) {
        super.setInheritErrorHandler(inheritErrorHandler);
        routeCollection.setInheritErrorHandlerFlag(inheritErrorHandler);

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
     * Completely disable stream caching for all routes being defined in the same RouteBuilder after this.
     */
    public void noStreamCaching() {
        StreamCachingInterceptor.noStreamCaching(routeCollection.getInterceptors());
    }

    /**
     * Enable stream caching for all routes being defined in the same RouteBuilder after this call.
     */
    public void streamCaching() {
        routeCollection.intercept(new StreamCachingInterceptor());
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
