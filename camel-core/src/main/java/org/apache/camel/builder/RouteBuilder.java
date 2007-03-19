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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpointResolver;
import org.apache.camel.util.ObjectHelper;

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
public abstract class RouteBuilder<E extends Exchange> {
    private EndpointResolver<E> endpointResolver;
    private List<DestinationBuilder<E>> destinationBuilders = new ArrayList<DestinationBuilder<E>>();
    private AtomicBoolean initalized = new AtomicBoolean(false);
    private Map<Endpoint<E>, List<Processor<E>>> routeMap = new HashMap<Endpoint<E>, List<Processor<E>>>();

    /**
     * Called on initialisation to to build the required destinationBuilders
     */
    public abstract void configure();

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> endpoint(String uri) {
        return getEndpointResolver().resolve(uri);
    }

    public DestinationBuilder<E> from(String uri) {
        return from(endpoint(uri));
    }

    public DestinationBuilder<E> from(Endpoint<E> endpoint) {
        DestinationBuilder<E> answer = new DestinationBuilder<E>(this, endpoint);
        destinationBuilders.add(answer);
        return answer;
    }

    // Helper methods
    //-----------------------------------------------------------------------
    public Predicate<E> headerEquals(final String header, final Object value) {
        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                return ObjectHelper.equals(value, exchange.getHeader(header));
            }

            @Override
            public String toString() {
                return "header[" + header + "] == " + value;
            }
        };
    }

    // Properties
    //-----------------------------------------------------------------------

    /**
     * Returns the routing map from inbound endpoints to processors
     */
    public Map<Endpoint<E>, List<Processor<E>>> getRouteMap() {
        checkInitialized();
        return routeMap;
    }

    /**
     * Returns the destinationBuilders which have been created
     */
    public List<DestinationBuilder<E>> getDestinationBuilders() {
        checkInitialized();
        return destinationBuilders;
    }

    public EndpointResolver<E> getEndpointResolver() {
        if (endpointResolver == null) {
            endpointResolver = createEndpointResolver();
        }
        return endpointResolver;
    }

    public void setEndpointResolver(EndpointResolver<E> endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected EndpointResolver<E> createEndpointResolver() {
        return new DefaultEndpointResolver<E>();
    }

    protected void checkInitialized() {
        if (initalized.compareAndSet(false, true)) {
            configure();
            populateRouteMap(routeMap);
        }
    }

    protected void populateRouteMap(Map<Endpoint<E>, List<Processor<E>>> routeMap) {
        for (DestinationBuilder<E> destinationBuilder : destinationBuilders) {
            Endpoint<E> from = destinationBuilder.getFrom();
            destinationBuilder.createProcessors();
            List<Processor<E>> processors = destinationBuilder.getProcessors();
            routeMap.put(from, processors);
        }
    }
}
