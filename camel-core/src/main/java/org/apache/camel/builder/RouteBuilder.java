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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A builder of destinationBuilders using a typesafe Java DLS.
 *
 * @version $Revision$
 */
public abstract class RouteBuilder<E extends Exchange> extends BuilderSupport<E> {
    private CamelContext<E> context;
    private List<FromBuilder<E>> fromBuilders = new ArrayList<FromBuilder<E>>();
    private AtomicBoolean initalized = new AtomicBoolean(false);
    private Map<Endpoint<E>, Processor<E>> routeMap = new HashMap<Endpoint<E>, Processor<E>>();

    protected RouteBuilder() {
    }

    protected RouteBuilder(CamelContext<E> context) {
        this.context = context;
    }

    /**
     * Called on initialization to to build the required destinationBuilders
     */
    public abstract void configure();

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> endpoint(String uri) {
         CamelContext<E> c = getContext();
         EndpointResolver<E> er = c.getEndpointResolver();
         return er.resolveEndpoint(c, uri);
    }

    public FromBuilder<E> from(String uri) {
        return from(endpoint(uri));
    }

    public FromBuilder<E> from(Endpoint<E> endpoint) {
        FromBuilder<E> answer = new FromBuilder<E>(this, endpoint);
        fromBuilders.add(answer);
        return answer;
    }

    /**
     * Installs the given error handler builder
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public RouteBuilder<E> errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    // Properties
    //-----------------------------------------------------------------------

    public CamelContext<E> getContext() {
        if (context == null) {
            context = createContainer();
        }
        return context;
    }

    public void setContext(CamelContext<E> context) {
        this.context = context;
    }

    /**
     * Returns the routing map from inbound endpoints to processors
     */
    public Map<Endpoint<E>, Processor<E>> getRouteMap() {
        checkInitialized();
        return routeMap;
    }

    /**
     * Returns the destinationBuilders which have been created
     */
    public List<FromBuilder<E>> getDestinationBuilders() {
        checkInitialized();
        return fromBuilders;
    }


    // Implementation methods
    //-----------------------------------------------------------------------
    protected void checkInitialized() {
        if (initalized.compareAndSet(false, true)) {
            configure();
            populateRouteMap(routeMap);
        }
    }

    protected void populateRouteMap(Map<Endpoint<E>, Processor<E>> routeMap) {
        for (FromBuilder<E> fromBuilder : fromBuilders) {
            Endpoint<E> from = fromBuilder.getFrom();
            Processor<E> processor = fromBuilder.createProcessor();
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for DestinationBuilder: " + fromBuilder);
            }
            routeMap.put(from, processor);
        }
    }

    protected CamelContext<E> createContainer() {
        return new DefaultCamelContext<E>();
    }
}
