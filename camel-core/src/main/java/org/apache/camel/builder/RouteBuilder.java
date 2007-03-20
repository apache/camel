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

import org.apache.camel.CamelContainer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
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
public abstract class RouteBuilder<E extends Exchange> extends BuilderSupport<E> {
    private CamelContainer<E> container;
    private List<DestinationBuilder<E>> destinationBuilders = new ArrayList<DestinationBuilder<E>>();
    private AtomicBoolean initalized = new AtomicBoolean(false);
    private Map<Endpoint<E>, Processor<E>> routeMap = new HashMap<Endpoint<E>, Processor<E>>();

    protected RouteBuilder() {
    }

    protected RouteBuilder(CamelContainer<E> container) {
        this.container = container;
    }

    /**
     * Called on initialisation to to build the required destinationBuilders
     */
    public abstract void configure();

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> endpoint(String uri) {
         CamelContainer<E> c = getContainer();
         EndpointResolver<E> er = c.getEndpointResolver();
         return er.resolveEndpoint(c, uri);
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

    public CamelContainer<E> getContainer() {
        if (container == null) {
            container = createContainer();
        }
        return container;
    }

    public void setContainer(CamelContainer<E> container) {
        this.container = container;
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
    public List<DestinationBuilder<E>> getDestinationBuilders() {
        checkInitialized();
        return destinationBuilders;
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
        for (DestinationBuilder<E> destinationBuilder : destinationBuilders) {
            Endpoint<E> from = destinationBuilder.getFrom();
            Processor<E> processor = destinationBuilder.createProcessor();
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for DestinationBuilder: " + destinationBuilder);
            }
            routeMap.put(from, processor);
        }
    }

    protected CamelContainer<E> createContainer() {
        return new CamelContainer<E>();
    }
}
