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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.InterceptType;
import org.apache.camel.model.OtherwiseType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.RoutesType;
import org.apache.camel.model.ExceptionType;
import org.apache.camel.processor.DelegateProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A <a href="http://activemq.apache.org/camel/dsl.html">Java DSL</a> which is
 * used to build {@link Route} instances in a
 * 
 * @{link CamelContext} for smart routing.
 * 
 * @version $Revision$
 */
public abstract class RouteBuilder extends BuilderSupport {
    private AtomicBoolean initalized = new AtomicBoolean(false);
    private RoutesType routeCollection = new RoutesType();
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
     * Called on initialization to to build the required destinationBuilders
     */
    public abstract void configure() throws Exception;

    /**
     * Creates a new route from the given URI input
     */
    public RouteType from(String uri) {
        RouteType answer = routeCollection.from(uri);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given endpoint
     */
    public RouteType from(Endpoint endpoint) {
        RouteType answer = routeCollection.from(endpoint);
        configureRoute(answer);
        return answer;
    }

    /**
     * Installs the given error handler builder
     * 
     * @param errorHandlerBuilder the error handler to be used by default for
     *                all child routes
     * @return the current builder with the error handler configured
     */
    public RouteBuilder errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures whether or not the error handler is inherited by every
     * processing node (or just the top most one)
     * 
     * @param value the flag as to whether error handlers should be inherited or
     *                not
     * @return the current builder
     */
    public RouteBuilder inheritErrorHandler(boolean value) {
        routeCollection.setInheritErrorHandlerFlag(value);
        return this;
    }

    /**
     * Adds the given interceptor to this route
     */
    public RouteBuilder intercept(DelegateProcessor interceptor) {
        routeCollection.intercept(interceptor);
        return this;
    }

    /**
     * Adds a route for an interceptor; use the {@link ProcessorType#proceed()} method
     * to continue processing the underying route being intercepted.
     *
     * @return
     */
    public InterceptType intercept() {
        return routeCollection.intercept();
    }

    /**
     * Applies a route for an interceptor if the given predicate is true
     * otherwise the interceptor route is not applied
     */
    public OtherwiseType intercept(Predicate predicate) {
        return routeCollection.intercept(predicate);
    }

    /**
     * Adds an exception handler route for the given exception type
     */
    public ExceptionType exception(Class exceptionType) {
        return routeCollection.exception(exceptionType);
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
     * Returns the routing map from inbound endpoints to processors
     */
    public List<Route> getRouteList() throws Exception {
        checkInitialized();
        return routes;
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected void checkInitialized() throws Exception {
        if (initalized.compareAndSet(false, true)) {
            configure();
            populateRoutes(routes);
        }
    }

    protected void populateRoutes(List<Route> routes) throws Exception {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("No CamelContext has been injected!");
        }
        routeCollection.setCamelContext(camelContext);
        camelContext.addRouteDefinitions(routeCollection.getRoutes());
    }

    public void setRouteCollection(RoutesType routeCollection) {
        this.routeCollection = routeCollection;
    }

    /**
     * Factory method
     */
    protected CamelContext createContainer() {
        return new DefaultCamelContext();
    }

    protected void configureRoute(RouteType route) {
        route.setGroup(getClass().getName());
    }
}
