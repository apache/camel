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
package org.apache.camel.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.naming.Context;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.Routes;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.management.InstrumentationLifecycleStrategy;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ExchangeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.NoFactoryAvailableException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionInjector;
import org.apache.camel.util.SystemHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ServiceHelper.startServices;
import static org.apache.camel.util.ServiceHelper.stopServices;


/**
 * Represents the context used to configure routes and the policies to use.
 *
 * @version $Revision$
 */
public class DefaultCamelContext extends ServiceSupport implements CamelContext, Service {
    private static final transient Log LOG = LogFactory.getLog(DefaultCamelContext.class);
    private static final String NAME_PREFIX = "camel-";
    private static int nameSuffix;

    private String name;
    private final Map<String, Endpoint> endpoints = new HashMap<String, Endpoint>();
    private final Map<String, Component> components = new HashMap<String, Component>();
    private List<Route> routes;
    private List<Service> servicesToClose = new ArrayList<Service>();
    private TypeConverter typeConverter;
    private ExchangeConverter exchangeConverter;
    private Injector injector;
    private ComponentResolver componentResolver;
    private boolean autoCreateComponents = true;
    private LanguageResolver languageResolver = new DefaultLanguageResolver();
    private Registry registry;
    private LifecycleStrategy lifecycleStrategy;
    private List<RouteType> routeDefinitions = new ArrayList<RouteType>();
    private List<InterceptStrategy> interceptStrategies = new ArrayList<InterceptStrategy>();
    private Boolean trace;
    private ErrorHandlerBuilder errorHandlerBuilder;

    public DefaultCamelContext() {
        name = NAME_PREFIX + ++nameSuffix;

        if (Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
            LOG.info("JMX is disabled. Using DefaultLifecycleStrategy.");
            lifecycleStrategy = new DefaultLifecycleStrategy();
        } else {
            try {
                LOG.info("JMX enabled. Using InstrumentationLifecycleStrategy.");
                lifecycleStrategy = new InstrumentationLifecycleStrategy();
            } catch (NoClassDefFoundError e) {
                // if we can't instantiate the JMX enabled strategy then fallback to default
                // could be because of missing .jars on the classpath
                LOG.warn("Could not find needed classes for JMX lifecycle strategy."
                    + " Are you missing spring-context.jar by any chance? NoClassDefFoundError: " + e.getMessage());
            } catch (Exception e) {
                LOG.warn("Could not create JMX lifecycle strategy, caused by: " + e.getMessage());
            }
            // if not created then fallback to default
            if (lifecycleStrategy == null) {
                LOG.warn("Not possible to use JMX lifecycle strategy. Using DefaultLifecycleStrategy instead.");
                lifecycleStrategy = new DefaultLifecycleStrategy();
            }
        }
    }

    /**
     * Creates the {@link CamelContext} using the given JNDI context as the
     * registry
     *
     * @param jndiContext
     */
    public DefaultCamelContext(Context jndiContext) {
        this(new JndiRegistry(jndiContext));
    }

    /**
     * Creates the {@link CamelContext} using the given registry
     */
    public DefaultCamelContext(Registry registry) {
        this();
        this.registry = registry;
    }

    public String getName() {
        return name;
    }

    /**
     * Sets the name of the this context.
     */
    public void setName(String name) {
        this.name = name;
    }

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
                            // If the component is looked up after the context
                            // is started,
                            // lets start it up.
                            startServices(component);
                        }
                    }
                } catch (Exception e) {
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
        } else {
            throw new IllegalArgumentException("The component is not of type: " + componentType + " but is: "
                    + component);
        }
    }

    public Component removeComponent(String componentName) {
        synchronized (components) {
            return components.remove(componentName);
        }
    }

    public Component getOrCreateComponent(String componentName, Callable<Component> factory) {
        synchronized (components) {
            Component component = components.get(componentName);
            if (component == null) {
                try {
                    component = factory.call();
                    if (component == null) {
                        throw new RuntimeCamelException("Factory failed to create the " + componentName
                                + " component, it returned null.");
                    }
                    components.put(componentName, component);
                    component.setCamelContext(this);
                } catch (Exception e) {
                    throw new RuntimeCamelException("Factory failed to create the " + componentName
                            + " component", e);
                }
            }
            return component;
        }
    }

    // Endpoint Management Methods
    // -----------------------------------------------------------------------

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

    public Endpoint getEndpoint(String uri) {
        Endpoint answer;
        synchronized (endpoints) {
            answer = endpoints.get(uri);
            if (answer == null) {
                try {

                    // Use the URI prefix to find the component.
                    String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
                    if (splitURI[1] != null) {
                        String scheme = splitURI[0];
                        Component component = getComponent(scheme);

                        // Ask the component to resolve the endpoint.
                        if (component != null) {
                            // Have the component create the endpoint if it can.
                            answer = component.createEndpoint(uri);

                            if (answer != null && LOG.isDebugEnabled()) {
                                LOG.debug(uri + " converted to endpoint: " + answer + " by component: " + component);
                            }
                        }
                    }
                    if (answer == null) {
                        answer = createEndpoint(uri);
                    }

                    // If it's a singleton then auto register it.
                    if (answer != null) {
                        addService(answer);

                        if (answer.isSingleton()) {
                            endpoints.put(uri, answer);

                            // TODO we should support non-singletons in the lifecycle
                            lifecycleStrategy.onEndpointAdd(answer);
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Failed to resolve endpoint " + uri + ". Reason: " + e, e);
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
        } else {
            throw new IllegalArgumentException("The endpoint is not of type: " + endpointType + " but is: "
                    + endpoint);
        }
    }

    // Route Management Methods
    // -----------------------------------------------------------------------
    public List<Route> getRoutes() {
        if (routes == null) {
            routes = new ArrayList<Route>();
        }
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public void addRoutes(Collection<Route> routes) throws Exception {
        if (this.routes == null) {
            this.routes = new ArrayList<Route>(routes);
        } else {
            this.routes.addAll(routes);
        }
        lifecycleStrategy.onRoutesAdd(routes);
        if (shouldStartRoutes()) {
            startRoutes(routes);
        }
    }

    public void addRoutes(Routes builder) throws Exception {
        // lets now add the routes from the builder
        builder.setContext(this);
        List<Route> routeList = builder.getRouteList();
        LOG.debug("Adding routes from: " + builder + " routes: " + routeList);
        addRoutes(routeList);
    }

    public void addRouteDefinitions(Collection<RouteType> routeDefinitions) throws Exception {
        this.routeDefinitions.addAll(routeDefinitions);
        if (shouldStartRoutes()) {
            startRouteDefinitions(routeDefinitions);
        }

    }

    /**
     * Adds a service, starting it so that it will be stopped with this context
     */
    public void addService(Object object) throws Exception {
        if (object instanceof Service) {
            Service service = (Service) object;
            service.start();
            servicesToClose.add(service);
        }
    }

    // Helper methods
    // -----------------------------------------------------------------------

    public Language resolveLanguage(String language) {
        return getLanguageResolver().resolveLanguage(language, this);
    }

    // Properties
    // -----------------------------------------------------------------------
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

    public LanguageResolver getLanguageResolver() {
        return languageResolver;
    }

    public void setLanguageResolver(LanguageResolver languageResolver) {
        this.languageResolver = languageResolver;
    }

    public boolean isAutoCreateComponents() {
        return autoCreateComponents;
    }

    public void setAutoCreateComponents(boolean autoCreateComponents) {
        this.autoCreateComponents = autoCreateComponents;
    }

    public Registry getRegistry() {
        if (registry == null) {
            registry = createRegistry();
        }
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public LifecycleStrategy getLifecycleStrategy() {
        return lifecycleStrategy;
    }

    public void setLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        this.lifecycleStrategy = lifecycleStrategy;
    }

    public List<RouteType> getRouteDefinitions() {
        return routeDefinitions;
    }

    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    public void setInterceptStrategies(List<InterceptStrategy> interceptStrategies) {
        this.interceptStrategies = interceptStrategies;
    }

    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        getInterceptStrategies().add(interceptStrategy);
    }

    /**
     * Returns true if tracing has been enabled or disabled via the {@link #setTrace(Boolean)} method
     * or it has not been specified then default to the <b>camel.trace</b> system property
     */
    public boolean getTrace() {
        final Boolean value = getTracing();
        if (value != null) {
            return value;
        } else {
            return SystemHelper.isSystemProperty("canel.trace");
        }
    }

    public Boolean getTracing() {
        return trace;
    }

    public void setTrace(Boolean trace) {
        this.trace = trace;
    }

    public <E extends Exchange> ProducerTemplate<E> createProducerTemplate() {
        return new DefaultProducerTemplate<E>(this);
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    /**
     * Sets the default error handler builder which is inherited by the routes
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected void doStart() throws Exception {
        if (getTrace()) {
            // lets check if we already have already been configured and if not add the default
            boolean found = false;
            final List<InterceptStrategy> list = getInterceptStrategies();
            for (InterceptStrategy strategy : list) {
                if (strategy instanceof Tracer) {
                    found = true;
                }
            }
            if (!found) {
                addInterceptStrategy(new Tracer());
            }
        }
        lifecycleStrategy.onContextStart(this);

        forceLazyInitialization();
        if (components != null) {
            for (Component component : components.values()) {
                startServices(component);
            }
        }
        startRouteDefinitions(routeDefinitions);
        startRoutes(routes);
    }

    protected void startRouteDefinitions(Collection<RouteType> list) throws Exception {
        if (list != null) {
            Collection<Route> routes = new ArrayList<Route>();
            for (RouteType route : list) {
                route.addRoutes(this, routes);
            }
            addRoutes(routes);
        }
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
                for (Service service : services) {
                    addService(service);
                }
            }
        }
    }

    /**
     * Lets force some lazy initialization to occur upfront before we start any
     * components and create routes
     */
    protected void forceLazyInitialization() {
        getExchangeConverter();
        getInjector();
        getLanguageResolver();
        getTypeConverter();
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
        return new DefaultTypeConverter(getInjector());
    }

    /**
     * Lazily create a default implementation
     */
    protected Injector createInjector() {
        FactoryFinder finder = new FactoryFinder();
        try {
            return (Injector) finder.newInstance("Injector");
        } catch (NoFactoryAvailableException e) {
            // lets use the default
            return new ReflectionInjector();
        } catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        } catch (InstantiationException e) {
            throw new RuntimeCamelException(e);
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Lazily create a default implementation
     */
    protected ComponentResolver createComponentResolver() {
        return new DefaultComponentResolver();
    }

    /**
     * Lazily create a default implementation
     */
    protected Registry createRegistry() {
        return new JndiRegistry();
    }

    /**
     * A pluggable strategy to allow an endpoint to be created without requiring
     * a component to be its factory, such as for looking up the URI inside some
     * {@link Registry}
     *
     * @param uri the uri for the endpoint to be created
     * @return the newly created endpoint or null if it could not be resolved
     */
    protected Endpoint createEndpoint(String uri) {
        Object value = getRegistry().lookup(uri);
        if (value instanceof Endpoint) {
            return (Endpoint) value;
        } else if (value instanceof Processor) {
            return new ProcessorEndpoint(uri, this, (Processor) value);
        } else if (value != null) {
            return convertBeanToEndpoint(uri, value);
        }
        return null;
    }

    /**
     * Attempt to convert the bean from a {@link Registry} to an endpoint using
     * some kind of transformation or wrapper
     *
     * @param uri  the uri for the endpoint (and name in the registry)
     * @param bean the bean to be converted to an endpoint, which will be not null
     * @return a new endpoint
     */
    protected Endpoint convertBeanToEndpoint(String uri, Object bean) {
        throw new IllegalArgumentException("uri: " + uri + " bean: " + bean
                + " could not be converted to an Endpoint");
    }

    /**
     * Should we start newly added routes?
     */
    protected boolean shouldStartRoutes() {
        return isStarted() && !isStarting();
    }


}
