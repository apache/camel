/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultEndpointResolver;
import org.apache.camel.impl.DefaultExchangeConverter;

/**
 * Represents the context used to configure routes and the policies to use.
 *
 * @version $Revision$
 * @org.apache.xbean.XBean element="container" rootElement="true"
 */
public class CamelContext<E extends Exchange> {

    private EndpointResolver<E> endpointResolver;
    private ExchangeConverter exchangeConverter;
    private Map<String, Component> components = new HashMap<String, Component>();
	private Map<Endpoint<E>, Processor<E>> routes;
    
    /**
     * Activates all the starting endpoints in that were added as routes.
     */
    public void activateEndpoints() {
        for (Map.Entry<Endpoint<E>, Processor<E>> entry : routes.entrySet()) {
            Endpoint<E> endpoint = entry.getKey();
            Processor<E> processor = entry.getValue();
            endpoint.activate(processor);
        }
    }
    
    /**
     * Deactivates all the starting endpoints in that were added as routes.
     */
    public void deactivateEndpoints() {
        for (Endpoint<E> endpoint : routes.keySet()) {
            endpoint.deactivate();
        }
    }

    // Builder APIs
    //-----------------------------------------------------------------------
    public void routes(RouteBuilder<E> builder) {
        // lets now add the routes from the builder
        builder.setContainer(this);
        routes = builder.getRouteMap();
    }

    public void routes(final RouteFactory factory) {
        RouteBuilder<E> builder = new RouteBuilder<E>(this) {
            public void configure() {
                factory.build(this);
            }
        };
        routes(builder);
    }


    /**
     * Adds a component to the container if there is not currently a component already registered.
     */
    public void addComponent(String componentName, final Component<E> component) {
        // TODO provide a version of this which barfs if the component is registered multiple times

        getOrCreateComponent(componentName, new Callable<Component<E>>() {
            public Component<E> call() throws Exception {
                return component;
            }
        });
    }


    /**O
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> endpoint(String uri) {
         EndpointResolver<E> er = getEndpointResolver();
         return er.resolveEndpoint(this, uri);
    }


    // Properties
    //-----------------------------------------------------------------------
    public EndpointResolver<E> getEndpointResolver() {
        if (endpointResolver == null) {
            endpointResolver = createEndpointResolver();
        }
        return endpointResolver;
    }

    public void setEndpointResolver(EndpointResolver<E> endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    public ExchangeConverter getExchangeConverter() {
        if (exchangeConverter == null) {
            exchangeConverter = createExchangeConverter();
        }
        return exchangeConverter;
    }

    public void setExchangeConverter(ExchangeConverter exchangeConverter) {
        this.exchangeConverter = exchangeConverter;
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected EndpointResolver<E> createEndpointResolver() {
        return new DefaultEndpointResolver<E>();
    }

    /**
     * Lazily create a default exchange converter implementation
     */
    protected ExchangeConverter createExchangeConverter() {
        return new DefaultExchangeConverter();
    }

    public Component getOrCreateComponent(String componentName, Callable<Component<E>> factory) {
        synchronized (components) {
            Component component = components.get(componentName);
            if (component == null) {
                try {
                    component = factory.call();
                    if (component == null) {
                        throw new IllegalArgumentException("Factory failed to create the " + componentName + " component, it returned null.");
                    }
                    components.put(componentName, component);
                    component.setContainer(this);
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("Factory failed to create the " + componentName + " component", e);
                }
            }
            return component;
        }
    }

    public Component getComponent(String componentName) {
        synchronized (components) {
            Component component = components.get(componentName);
            return component;
        }
    }
}
