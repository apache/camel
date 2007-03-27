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
public abstract class RouteBuilder<E extends Exchange> extends BuilderSupport<E> {
    private List<FromBuilder<E>> fromBuilders = new ArrayList<FromBuilder<E>>();
    private AtomicBoolean initalized = new AtomicBoolean(false);
    private List<Route<E>> routes = new ArrayList<Route<E>>();

    protected RouteBuilder() {
        this(null);
    }

    protected RouteBuilder(CamelContext context) {
        super(context);
    }

    /**
     * Called on initialization to to build the required destinationBuilders
     */
    public abstract void configure();

    @Fluent
    public FromBuilder<E> from( @FluentArg("uri") String uri) {
        return from(endpoint(uri));
    }

    @Fluent
    public FromBuilder<E> from( @FluentArg("endpoint") Endpoint<E> endpoint) {
        FromBuilder<E> answer = new FromBuilder<E>(this, endpoint);
        fromBuilders.add(answer);
        return answer;
    }

    /**
     * Installs the given error handler builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public RouteBuilder<E> errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures whether or not the error handler is inherited by every processing node (or just the top most one)
     *
     * @param value the falg as to whether error handlers should be inherited or not
     * @return the current builder
     */
    public RouteBuilder<E> inheritErrorHandler(boolean value) {
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
    public List<Route<E>> getRouteList() throws Exception {
        checkInitialized();
        return routes;
    }

    /**
     * Returns the builders which have been created
     */
    public List<FromBuilder<E>> getFromBuilders() throws Exception {
        checkInitialized();
        return fromBuilders;
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected void checkInitialized() throws Exception {
        if (initalized.compareAndSet(false, true)) {
            configure();
            populateRoutes(routes);
        }
    }

    protected void populateRoutes(List<Route<E>> routes) throws Exception {
        for (FromBuilder<E> builder : fromBuilders) {
            Endpoint<E> from = builder.getFrom();
            Processor<E> processor = makeProcessor(from, builder);
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for DestinationBuilder: " + builder);
            }
            routes.add(new Route<E>(from, processor));
        }
    }

    /**
     * Factory method to create the underlying {@link Processor} for the given builder applying any
     * necessary interceptors.
     *
     * @param from    the endpoint which starts the route
     * @param builder the builder which is the factory of the processor
     * @return
     */
    protected Processor<E> makeProcessor(Endpoint<E> from, FromBuilder<E> builder) throws Exception {
        return builder.createProcessor();
    }

    protected CamelContext createContainer() {
        return new DefaultCamelContext();
    }
}
