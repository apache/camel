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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ExchangeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ServiceHelper.startServices;
import static org.apache.camel.util.ServiceHelper.stopServices;

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
    private ComponentResolver componentResolver;
    private boolean autoCreateComponents = true;

    /**
     * Adds a component to the container.
     */
    public void addComponent(String componentName, final Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        synchronized (components) {
            if (components.containsKey(componentName)) {
                throw new IllegalArgumentException("Component previously added: " + componentName);
            }
            component.setCamelContext(this);
            components.put(componentName, component);
        }
    }

    public Component getComponent(String name) {
        // synchronize the look up and auto create so that 2 threads can't
        // concurrently auto create the same component.
        synchronized (components) {
            Component component = components.get(name);
            if (component == null && autoCreateComponents) {
                try {
                    component = getComponentResolver().resolveComponent(name, this);
                    if (component != null) {
                        addComponent(name, component);
                        if (isStarted()) {
                            // If the component is looked up after the context is started,
                            // lets start it up.
                            startServices(component);
                        }
                    }
                }
                catch (Exception e) {
                    throw new RuntimeCamelException("Could not auto create component: " + name, e);
                }
            }
            return component;
        }
    }

    public <T extends Component> T getComponent(String name, Class<T> componentType) {
        Component component = getComponent(name);
        if (componentType.isInstance(component)) {
            return componentType.cast(component);
        }
        else {
            throw new IllegalArgumentException("The component is not of type: " + componentType + " but is: " + component);
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
                        throw new RuntimeCamelException("Factory failed to create the " + componentName + " component, it returned null.");
                    }
                    components.put(componentName, component);
                    component.setCamelContext(this);
                }
                catch (Exception e) {
                    throw new RuntimeCamelException("Factory failed to create the " + componentName + " component", e);
                }
            }
            return component;
        }
    }

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    public Collection<Endpoint> getSingletonEndpoints() {
        synchronized (endpoints) {
            return new ArrayList<Endpoint>(endpoints.values());
        }
    }

    public Endpoint addSingletonEndpoint(String uri, Endpoint endpoint) throws Exception {
        Endpoint oldEndpoint;
        synchronized (endpoints) {
            startServices(endpoint);
            oldEndpoint = endpoints.remove(uri);
            endpoints.put(uri, endpoint);
            stopServices(oldEndpoint);
        }
        return oldEndpoint;
    }

    public Endpoint removeSingletonEndpoint(String uri) throws Exception {
        Endpoint oldEndpoint;
        synchronized (endpoints) {
            oldEndpoint = endpoints.remove(uri);
            stopServices(oldEndpoint);
        }
        return oldEndpoint;
    }

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint getEndpoint(String uri) {
        Endpoint answer;
        synchronized (endpoints) {
            answer = endpoints.get(uri);
            if (answer == null) {
                try {

                    // Use the URI prefix to find the component.
                    String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
                    if (splitURI[1] == null) {
                        throw new IllegalArgumentException("Invalid URI, it did not contain a scheme: " + uri);
                    }
                    String scheme = splitURI[0];
                    Component component = getComponent(scheme);

                    // Ask the component to resolve the endpoint.
                    if (component != null) {

                        // Have the component create the endpoint if it can.
                        answer = component.createEndpoint(uri);

                        // If it's a singleton then auto register it.
                        if (answer != null && answer.isSingleton()) {
                            if (answer != null) {
                                startServices(answer);
                                endpoints.put(uri, answer);
                            }
                        }
                    }
                }
                catch (Exception e) {
                    throw new ResolveEndpointFailedException(uri, e);
                }
            }
        }
        return answer;
    }

    public <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType) {
        Endpoint endpoint = getEndpoint(name);
        if (endpointType.isInstance(endpoint)) {
            return endpointType.cast(endpoint);
        }
        else {
            throw new IllegalArgumentException("The endpoint is not of type: " + endpointType + " but is: " + endpoint);
        }
    }

    // Route Management Methods
    //-----------------------------------------------------------------------
    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public void addRoutes(Collection<Route> routes) throws Exception {
        if (this.routes == null) {
            this.routes = new ArrayList<Route>(routes);
        }
        else {
            this.routes.addAll(routes);
        }
        if (isStarted()) {
            startRoutes(routes);
        }
    }

    public void addRoutes(RouteBuilder builder) throws Exception {
        // lets now add the routes from the builder
        builder.setContext(this);
        addRoutes(builder.getRouteList());
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

    public ComponentResolver getComponentResolver() {
        if (componentResolver == null) {
            componentResolver = createComponentResolver();
        }
        return componentResolver;
    }

    public void setComponentResolver(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

    // Implementation methods
    //-----------------------------------------------------------------------

    protected void doStart() throws Exception {
        if (components != null) {
            for (Component component : components.values()) {
                startServices(component);
            }
        }
        startRoutes(routes);
    }

    protected void doStop() throws Exception {
        stopServices(servicesToClose);
        if (components != null) {
            for (Component component : components.values()) {
                stopServices(component);
            }
        }
    }

    protected void startRoutes(Collection<Route> routeList) throws Exception {
        if (routeList != null) {
            for (Route<Exchange> route : routeList) {
                List<Service> services = route.getServicesForRoute();
                servicesToClose.addAll(services);
                startServices(services);
            }
        }
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
        FactoryFinder finder = new FactoryFinder();
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

    /**
     * Lazily create a default implementation
     */
    protected ComponentResolver createComponentResolver() {
        return new DefaultComponentResolver();
    }

    public boolean isAutoCreateComponents() {
        return autoCreateComponents;
    }

    public void setAutoCreateComponents(boolean autoCreateComponents) {
        this.autoCreateComponents = autoCreateComponents;
    }
}
