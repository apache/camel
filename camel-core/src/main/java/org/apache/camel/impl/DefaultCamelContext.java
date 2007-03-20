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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointResolver;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeConverter;
import org.apache.camel.Processor;
import org.apache.camel.RouteFactory;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;

/**
 * Represents the context used to configure routes and the policies to use.
 *
 * @version $Revision: 520517 $
 * @org.apache.xbean.XBean element="container" rootElement="true"
 */
public class DefaultCamelContext<E extends Exchange> implements CamelContext<E> {

    private EndpointResolver<E> endpointResolver;
    private ExchangeConverter exchangeConverter;
    private Map<String, Component> components = new HashMap<String, Component>();
	private Map<Endpoint<E>, Processor<E>> routes;
    private TypeConverter typeConverter;
    
    /**
     * Adds a component to the container.
     */
    public void addComponent(String componentName, final Component<E> component) {
        synchronized (components) {
            if( components.containsKey(componentName) ) {
            	throw new IllegalArgumentException("Component previously added: "+componentName);
            }
            component.setContext(this);
            components.put(componentName, component);
        }
    }

    public Component getComponent(String componentName) {
        synchronized (components) {
            return components.get(componentName);
        }
    }
    
    /**
     * Removes a previously added component.
     * @param componentName
     * @return the previously added component or null if it had not been previously added.
     */
    public Component removeComponent(String componentName) {
        synchronized (components) {
            return components.remove(componentName);
        }
    }

    /**
     * Gets the a previously added component by name or lazily creates the component
     * using the factory Callback. 
     * 
     * @param componentName
     * @param factory used to create a new component instance if the component was not previously added.
     * @return
     */
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
                    component.setContext(this);
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("Factory failed to create the " + componentName + " component", e);
                }
            }
            return component;
        }
    }

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> resolveEndpoint(String uri) {
         EndpointResolver<E> er = getEndpointResolver();
         return er.resolveEndpoint(this, uri);
    }
    
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

    // Route Management Methods
    //-----------------------------------------------------------------------
	public Map<Endpoint<E>, Processor<E>> getRoutes() {
		return routes;
	}

	public void setRoutes(Map<Endpoint<E>, Processor<E>> routes) {
		this.routes = routes;
	}

    public void setRoutes(RouteBuilder<E> builder) {
        // lets now add the routes from the builder
        builder.setContainer(this);
        setRoutes(builder.getRouteMap());
    }

    public void setRoutes(final RouteFactory factory) {
        RouteBuilder<E> builder = new RouteBuilder<E>(this) {
            public void configure() {
                factory.build(this);
            }
        };
        setRoutes(builder);
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

    public TypeConverter getTypeConverter() {
        if (typeConverter == null) {
            typeConverter = createTypeConverter();
        }
        return typeConverter;
    }

    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    // Implementation methods
    //-----------------------------------------------------------------------

    /**
     * Lazily create a default implementation
     */
    protected EndpointResolver<E> createEndpointResolver() {
        return new DefaultEndpointResolver<E>();
    }


    /**
     * Lazily create a default implementation
     */
    protected ExchangeConverter createExchangeConverter() {
        return new DefaultExchangeConverter();
    }


    /**
     * Lazily create a default implementation
     */
    private TypeConverter createTypeConverter() {
        return new DefaultTypeConverter();
    }

}
