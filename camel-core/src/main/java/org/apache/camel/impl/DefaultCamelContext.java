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

import org.apache.camel.*;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.ExchangeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.ReflectionInjector;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;
import org.apache.camel.util.ServiceHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Represents the context used to configure routes and the policies to use.
 *
 * @version $Revision: 520517 $
 * @org.apache.xbean.XBean element="container" rootElement="true"
 */
public class DefaultCamelContext extends ServiceSupport implements CamelContext, Service {
    private Map<String, Endpoint> endpoints = new HashMap<String, Endpoint>();
    private Map<String, Component> components = new HashMap<String, Component>();
    private List<Route> routes;
    private List<Service> servicesToClose = new ArrayList<Service>();
    private TypeConverter typeConverter;
    private ExchangeConverter exchangeConverter;
    private Injector injector;
    private ComponentResolver componentResolver = new DefaultComponentResolver();

    /**
     * Adds a component to the container.
     */
    public void addComponent(String componentName, final Component component) {
        synchronized (components) {
            if (components.containsKey(componentName)) {
                throw new IllegalArgumentException("Component previously added: " + componentName);
            }
            component.setCamelContext(this);
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
     *
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
     * @param factory       used to create a new component instance if the component was not previously added.
     * @return
     */
    public Component getOrCreateComponent(String componentName, Callable<Component> factory) {
        synchronized (components) {
            Component component = components.get(componentName);
            if (component == null) {
                try {
                    component = factory.call();
                    if (component == null) {
                        throw new IllegalArgumentException("Factory failed to create the " + componentName + " component, it returned null.");
                    }
                    components.put(componentName, component);
                    component.setCamelContext(this);
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

    public Collection<Endpoint> getEndpoints() {
        synchronized (endpoints) {
            return new ArrayList<Endpoint>(endpoints.values());
        }
    }

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint resolveEndpoint(String uri) {
        Endpoint answer;
        synchronized (endpoints) {
            answer = endpoints.get(uri);
            if (answer == null) {
                try {
                    synchronized (components) {
                        Collection<Component> componentSet = components.values();
                        for (Component component : componentSet) {
                            answer = component.resolveEndpoint(uri);
                            if (answer != null) {
                                break;
                            }
                        }
                    }
                    if (answer == null) {
                        Component component = componentResolver.resolveComponent(uri, this);
                        if (component != null) {
                            ServiceHelper.startServices(component);
                            answer = component.resolveEndpoint(uri);
                        }
                    }
                    if (answer != null) {
                        ServiceHelper.startServices(answer);
                        endpoints.put(uri, answer);
                    }
                }
                catch (Exception e) {
                    throw new ResolveEndpointFailedException(uri, e);
                }
            }
        }
        return answer;
    }

    /**
     * Looks up the current active endpoint by URI without auto-creating it.
     */
    public Endpoint getEndpoint(String uri) {
        Endpoint answer;
        synchronized (endpoints) {
            answer = endpoints.get(uri);
        }
        return answer;
    }

    /**
     * Activates all the starting endpoints in that were added as routes.
     */
    public void activateEndpoints() throws Exception {
        if (routes != null) {
            for (Route<Exchange> route : routes) {
                Processor<Exchange> processor = route.getProcessor();
                Consumer<Exchange> consumer = route.getEndpoint().createConsumer(processor);
                if (consumer != null) {
                    consumer.start();
                    servicesToClose.add(consumer);
                }
                if (processor instanceof Service) {
                    Service service = (Service) processor;
                    service.start();
                    servicesToClose.add(service);
                }
            }
        }
    }

    /**
     * Deactivates all the starting endpoints in that were added as routes.
     */
    public void deactivateEndpoints() throws Exception {
        ServiceHelper.stopServices(servicesToClose);
    }

    // Route Management Methods
    //-----------------------------------------------------------------------
    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public void addRoutes(List<Route> routes) {
        if (this.routes == null) {
            this.routes = new ArrayList<Route>(routes);
        }
        else {
            this.routes.addAll(routes);
        }
    }

    public void addRoutes(RouteBuilder builder) throws Exception {
        // lets now add the routes from the builder
        builder.setContext(this);
        addRoutes(builder.getRouteList());
    }

    public void addRoutes(final RouteFactory factory) throws Exception {
        RouteBuilder builder = new RouteBuilder(this) {
            public void configure() {
                factory.build(this);
            }
        };
        addRoutes(builder);
    }

    // Properties
    //-----------------------------------------------------------------------
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

    public Injector getInjector() {
        if (injector == null) {
            injector = createInjector();
        }
        return injector;
    }

    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    // Implementation methods
    //-----------------------------------------------------------------------

    protected void doStart() throws Exception {
        activateEndpoints();
    }

    protected void doStop() throws Exception {
        deactivateEndpoints();
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
    protected TypeConverter createTypeConverter() {
        return new DefaultTypeConverter();
    }

    /**
     * Lazily create a default implementation
     */
    protected Injector createInjector() {
        FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/camel/");
        try {
            return (Injector) finder.newInstance("Injector");
        }
        catch (NoFactoryAvailableException e) {
            // lets use the default
            return new ReflectionInjector();
        }
        catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
        catch (InstantiationException e) {
            throw new RuntimeCamelException(e);
        }
        catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
