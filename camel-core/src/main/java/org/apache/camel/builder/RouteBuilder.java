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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * A <a href="http://activemq.apache.org/camel/dsl.html">Java DSL</a>
 * which is used to build {@link Route} instances in a @{link CamelContext} for smart routing.
 *
 * @version $Revision$
 */
public abstract class RouteBuilder extends BuilderSupport {
    private List<FromBuilder> fromBuilders = new ArrayList<FromBuilder>();
    private AtomicBoolean initalized = new AtomicBoolean(false);
    private List<Route> routes = new ArrayList<Route>();

    protected RouteBuilder() {
        this(null);
    }

    protected RouteBuilder(CamelContext context) {
        super(context);
    }

    /**
     * Called on initialization to to build the required destinationBuilders
     */
    public abstract void configure() throws Exception;

    @Fluent
    public FromBuilder from( @FluentArg("uri") String uri) {
    	if( uri == null ) {
    		throw new IllegalArgumentException("uri parameter cannot be null");
    	}
    	Endpoint endpoint = endpoint(uri);
    	if( endpoint == null ) {
    		throw new IllegalArgumentException("uri '"+uri+"' could not be resolved.");
    	}
        return from(endpoint);
    }

    @Fluent
    public FromBuilder from( @FluentArg("ref") Endpoint endpoint) {
        FromBuilder answer = new FromBuilder(this, endpoint);
        addFromBuilder(answer);
        return answer;
    }

    /**
     * Installs the given error handler builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public RouteBuilder errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures whether or not the error handler is inherited by every processing node (or just the top most one)
     *
     * @param value the flag as to whether error handlers should be inherited or not
     * @return the current builder
     */
    public RouteBuilder inheritErrorHandler(boolean value) {
        setInheritErrorHandler(value);
        return this;
    }

    // Properties
    //-----------------------------------------------------------------------
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

    /**
     * Returns the builders which have been created
     */
    public List<FromBuilder> getFromBuilders() throws Exception {
        checkInitialized();
        return fromBuilders;
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    public void addFromBuilder(FromBuilder answer) {
        fromBuilders.add(answer);
    }

    protected void checkInitialized() throws Exception {
        if (initalized.compareAndSet(false, true)) {
            configure();
            populateRoutes(routes);
        }
    }

    protected void populateRoutes(List<Route> routes) throws Exception {
        for (FromBuilder builder : fromBuilders) {
            Route route = builder.createRoute();
            routes.add(route);
        }
    }

    /**
     * Factory method
     */
    protected CamelContext createContainer() {
        return new DefaultCamelContext();
    }
}
