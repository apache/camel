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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import javax.naming.Context;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.IsSingleton;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.management.DefaultInstrumentationAgent;
import org.apache.camel.management.DefaultManagedLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.StreamCaching;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionInjector;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents the context used to configure routes and the policies to use.
 *
 * @version $Revision$
 */
public class DefaultCamelContext extends ServiceSupport implements CamelContext {
    private static final transient Log LOG = LogFactory.getLog(DefaultCamelContext.class);
    private static final String NAME_PREFIX = "camel-";
    private static int nameSuffix;
    private boolean routeDefinitionInitiated;
    private String name;  
    private final Map<String, Endpoint> endpoints = new LRUCache<String, Endpoint>(1000);
    private final List<EndpointStrategy> endpointStrategies = new ArrayList<EndpointStrategy>();
    private final Map<String, Component> components = new HashMap<String, Component>();
    private List<Route> routes;
    private final List<Service> servicesToClose = new ArrayList<Service>();
    private TypeConverter typeConverter;
    private TypeConverterRegistry typeConverterRegistry;
    private Injector injector;
    private ComponentResolver componentResolver;
    private boolean autoCreateComponents = true;
    private LanguageResolver languageResolver = new DefaultLanguageResolver();
    private final Map<String, Language> languages = new HashMap<String, Language>();
    private Registry registry;
    private List<LifecycleStrategy> lifecycleStrategies = new ArrayList<LifecycleStrategy>();
    private ManagementStrategy managementStrategy;
    private final List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
    private List<InterceptStrategy> interceptStrategies = new ArrayList<InterceptStrategy>();
    private Boolean trace = Boolean.FALSE;
    private Boolean streamCache = Boolean.FALSE;
    private Boolean handleFault = Boolean.FALSE;
    private Long delay;
    private ErrorHandlerBuilder errorHandlerBuilder;
    private Map<String, DataFormatDefinition> dataFormats = new HashMap<String, DataFormatDefinition>();
    private Map<String, String> properties = new HashMap<String, String>();
    private FactoryFinderResolver factoryFinderResolver = new DefaultFactoryFinderResolver();
    private FactoryFinder defaultFactoryFinder;
    private final Map<String, FactoryFinder> factories = new HashMap<String, FactoryFinder>();
    private final Map<String, RouteService> routeServices = new HashMap<String, RouteService>();
    private ClassResolver classResolver = new DefaultClassResolver();
    private PackageScanClassResolver packageScanClassResolver;
    // we use a capacity of 100 per endpoint, so for the same endpoint we have at most 100 producers in the pool
    // so if we have 6 endpoints in the pool, we have 6 x 100 producers in total
    private ServicePool<Endpoint, Producer> producerServicePool = new DefaultProducerServicePool(100);
    private NodeIdFactory nodeIdFactory = new DefaultNodeIdFactory();
    private Tracer defaultTracer;

    public DefaultCamelContext() {
        super();
        name = NAME_PREFIX + ++nameSuffix;

        if (Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
            LOG.info("JMX is disabled. Using DefaultManagementStrategy.");
            managementStrategy = new DefaultManagementStrategy();
        } else {
            boolean registered = false;
            try {
                LOG.info("JMX enabled. Using DefaultManagedLifecycleStrategy.");
                managementStrategy = new ManagedManagementStrategy(new DefaultInstrumentationAgent());
                lifecycleStrategies.add(new DefaultManagedLifecycleStrategy(this));
                registered = true;
            } catch (NoClassDefFoundError e) {
                // if we can't instantiate the JMX enabled strategy then fallback to default
                // could be because of missing .jars on the classpath
                LOG.warn("Could not find needed classes for JMX lifecycle strategy."
                        + " Needed class is in spring-context.jar using Spring 2.5 or newer ("
                        + " spring-jmx.jar using Spring 2.0.x)."
                        + " NoClassDefFoundError: " + e.getMessage());
            } catch (Exception e) {
                LOG.warn("Could not create JMX lifecycle strategy, caused by: " + e.getMessage());
            }
            if (!registered) {
                LOG.warn("Cannot use JMX. Fallback to using DefaultManagementStrategy.");
                managementStrategy = new DefaultManagementStrategy();
            }
        }

        // use WebSphere specific resolver if running on WebSphere
        if (WebSpherePackageScanClassResolver.isWebSphereClassLoader(this.getClass().getClassLoader())) {
            LOG.info("Using WebSphere specific PackageScanClassResolver");
            packageScanClassResolver = new WebSpherePackageScanClassResolver("META-INF/services/org/apache/camel/TypeConverter");
        } else {
            packageScanClassResolver = new DefaultPackageScanClassResolver();
        }
    }

    /**
     * Creates the {@link CamelContext} using the given JNDI context as the
     * registry
     */
    public DefaultCamelContext(Context jndiContext) {
        this();
        setJndiContext(jndiContext);
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
        ObjectHelper.notNull(component, "component");
        synchronized (components) {
            if (components.containsKey(componentName)) {
                throw new IllegalArgumentException("Cannot add component as its already previously added: " + componentName);
            }
            component.setCamelContext(this);
            components.put(componentName, component);
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onComponentAdd(componentName, component);
            }
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
                        if (isStarted() || isStarting()) {
                            // If the component is looked up after the context is started, lets start it up.
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

    @Deprecated
    public Component removeComponent(String componentName) {
        synchronized (components) {
            Component answer = components.remove(componentName);
            if (answer != null) {
                for (LifecycleStrategy strategy : lifecycleStrategies) {
                    strategy.onComponentRemove(componentName, answer);
                }
            }
            return answer;
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
                    for (LifecycleStrategy strategy : lifecycleStrategies) {
                        strategy.onComponentAdd(componentName, component);
                    }
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

    public Collection<Endpoint> getEndpoints() {
        synchronized (endpoints) {
            return new ArrayList<Endpoint>(endpoints.values());
        }
    }

    public Map<String, Endpoint> getEndpointMap() {
        synchronized (endpoints) {
            return new TreeMap<String, Endpoint>(endpoints);
        }
    }

    public Endpoint hasEndpoint(String uri) {
        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
        synchronized (endpoints) {
            return endpoints.get(uri);
        }
    }

    public Collection<Endpoint> getEndpoints(String uri) {
        Collection<Endpoint> answer = new ArrayList<Endpoint>();
        Collection<Endpoint> coll;
        synchronized (endpoints) {
            Endpoint ep = endpoints.get(uri);
            if (ep != null) {
                answer.add(ep);
                return answer;
            }
            coll = new ArrayList<Endpoint>(endpoints.values());
        }
        for (Endpoint ep : coll) {
            if (!ep.isSingleton() && uri.equals(ep.getEndpointUri())) {
                answer.add(ep);
            }
        }
        return answer;
    }

    public Collection<Endpoint> getSingletonEndpoints() {
        Collection<Endpoint> answer = new ArrayList<Endpoint>();
        Collection<Endpoint> coll = getEndpoints();
        for (Endpoint ep : coll) {
            if (ep.isSingleton()) {
                answer.add(ep);
            }
        }
        return answer;
    }

    public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
        Endpoint oldEndpoint;
        synchronized (endpoints) {
            startServices(endpoint);
            oldEndpoint = endpoints.remove(uri);
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onEndpointAdd(endpoint);
            }
            addEndpointToRegistry(uri, endpoint);
            if (oldEndpoint != null) {
                stopServices(oldEndpoint);
            }
        }
        return oldEndpoint;
    }

    @Deprecated
    public Collection<Endpoint> removeEndpoints(String uri) throws Exception {
        Collection<Endpoint> answer = new ArrayList<Endpoint>();
        synchronized (endpoints) {
            Endpoint oldEndpoint = endpoints.remove(uri);
            if (oldEndpoint != null) {
                answer.add(oldEndpoint);
                stopServices(oldEndpoint);
                for (LifecycleStrategy strategy : lifecycleStrategies) {
                    strategy.onEndpointRemove(oldEndpoint);
                }
            } else {
                for (Map.Entry entry : endpoints.entrySet()) {
                    oldEndpoint = (Endpoint) entry.getValue();
                    if (!oldEndpoint.isSingleton() && uri.equals(oldEndpoint.getEndpointUri())) {
                        answer.add(oldEndpoint);
                        stopServices(oldEndpoint);
                        endpoints.remove(entry.getKey());
                        for (LifecycleStrategy strategy : lifecycleStrategies) {
                            strategy.onEndpointRemove(oldEndpoint);
                        }
                    }
                }
            }
        }
        return answer;
    }

    public Endpoint addSingletonEndpoint(String uri, Endpoint endpoint) throws Exception {
        return addEndpoint(uri, endpoint);
    }

    @Deprecated
    public Endpoint removeSingletonEndpoint(String uri) throws Exception {
        Collection<Endpoint> answer = removeEndpoints(uri);
        return (Endpoint) (answer.size() > 0 ? answer.toArray()[0] : null);
    }

    public Endpoint getEndpoint(String uri) {
        ObjectHelper.notEmpty(uri, "uri");

        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting endpoint with uri: " + uri);
        }

        Endpoint answer;
        String scheme = null;
        synchronized (endpoints) {
            answer = endpoints.get(uri);
            if (answer == null) {
                try {
                    // Use the URI prefix to find the component.
                    String splitURI[] = ObjectHelper.splitOnCharacter(uri, ":", 2);
                    if (splitURI[1] != null) {
                        scheme = splitURI[0];
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
                        // no component then try in registry and elsewhere
                        answer = createEndpoint(uri);
                    }

                    if (answer != null) {
                        addService(answer);
                        for (LifecycleStrategy strategy : lifecycleStrategies) {
                            strategy.onEndpointAdd(answer);
                        }
                        answer = addEndpointToRegistry(uri, answer);
                    }
                } catch (Exception e) {
                    throw new ResolveEndpointFailedException(uri, e);
                }
            }
        }

        // unknown scheme
        if (answer == null && scheme != null) {
            throw new ResolveEndpointFailedException(uri, "No component found with scheme: " + scheme);
        }

        return answer;
    }
    
    public <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType) {
        Endpoint endpoint = getEndpoint(name);

        if (endpoint instanceof InterceptSendToEndpoint) {
            endpoint = ((InterceptSendToEndpoint) endpoint).getDelegate();
        }
        if (endpointType.isInstance(endpoint)) {
            return endpointType.cast(endpoint);
        } else {
            throw new IllegalArgumentException("The endpoint is not of type: " + endpointType + " but is: "
                    + endpoint.getClass().getCanonicalName());
        }
    }

    public void addRegisterEndpointCallback(EndpointStrategy strategy) {
        if (!endpointStrategies.contains(strategy)) {
            // let it be invoked for already registered endpoints so it can catch-up.
            endpointStrategies.add(strategy);
            for (Endpoint endpoint : getEndpoints()) {
                Endpoint newEndpoint = strategy.registerEndpoint(endpoint.getEndpointUri(), endpoint);
                if (newEndpoint != endpoint) {
                    endpoints.put(getEndpointKey(newEndpoint.getEndpointUri(), newEndpoint), newEndpoint);
                }
            }
        }
    }

    /**
     * Strategy to add the given endpoint to the internal endpoint registry
     *
     * @param uri  uri of endpoint
     * @param endpoint the endpoint to add
     * @return the added endpoint
     */
    protected Endpoint addEndpointToRegistry(String uri, Endpoint endpoint) {
        for (EndpointStrategy strategy : endpointStrategies) {
            endpoint = strategy.registerEndpoint(uri, endpoint);
        }
        endpoints.put(getEndpointKey(uri, endpoint), endpoint);
        return endpoint;
    }

    // Route Management Methods
    // -----------------------------------------------------------------------
    public synchronized List<Route> getRoutes() {
        if (routes == null) {
            routes = new ArrayList<Route>();
        }

        // lets return a copy of the collection as objects are removed later
        // when services are stopped
        return new ArrayList<Route>(routes);
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
        throw new UnsupportedOperationException("Overriding existing routes is not supported yet, use addRoutes instead");
    }

    synchronized void removeRouteCollection(Collection<Route> routes) {
        if (this.routes != null) {
            this.routes.removeAll(routes);
        }
    }

    synchronized void addRouteCollection(Collection<Route> routes) throws Exception {
        if (this.routes == null) {
            this.routes = new ArrayList<Route>();
        }

        if (routes != null) {
            this.routes.addAll(routes);
        }
    }

    public void addRoutes(RoutesBuilder builder) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding routes from builder: " + builder);
        }
        // lets now add the routes from the builder
        builder.addRoutesToCamelContext(this);
    }

    public void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        for (RouteDefinition routeDefinition : routeDefinitions) {
            routeDefinition.setCamelContext(this);
            removeRouteDefinition(routeDefinition);
        }
        this.routeDefinitions.addAll(routeDefinitions);
        if (shouldStartRoutes()) {
            startRouteDefinitions(routeDefinitions);
        }
    }

    /**
     * Removes the route definition with the given key.
     *
     * @return true if one or more routes was removed
     */
    public boolean removeRouteDefinition(String key) {
        boolean answer = false;
        Iterator<RouteDefinition> iter = routeDefinitions.iterator();
        while (iter.hasNext()) {
            RouteDefinition route = iter.next();
            if (route.idOrCreate(nodeIdFactory).equals(key)) {
                iter.remove();
                answer = true;
            }
        }
        return answer;
    }

    public void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        this.routeDefinitions.removeAll(routeDefinitions);
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
    }

    public void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        String key = routeDefinition.idOrCreate(nodeIdFactory);
        stopRoute(key);
        removeRouteDefinition(key);
    }

    public ServiceStatus getRouteStatus(RouteDefinition route) {
        return getRouteStatus(route.idOrCreate(nodeIdFactory));
    }

    public ServiceStatus getRouteStatus(String key) {
        RouteService routeService = routeServices.get(key);
        if (routeService != null) {
            return routeService.getStatus();
        }
        return null;
    }

    public void startRoute(RouteDefinition route) throws Exception {
        List<Route> routes = new ArrayList<Route>();
        List<RouteContext> routeContexts = route.addRoutes(this, routes);
        RouteService routeService = new RouteService(this, route, routeContexts, routes);
        startRouteService(routeService);
    }

    public synchronized void startRoute(String routeId) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            routeService.start();
        }
    }

    public void stopRoute(RouteDefinition route) throws Exception {
        stopRoute(route.idOrCreate(nodeIdFactory));
    }

    /**
     * Stops the route denoted by the given RouteType id
     */
    public synchronized void stopRoute(String key) throws Exception {
        RouteService routeService = routeServices.get(key);
        if (routeService != null) {
            routeService.stop();
        }
    }

    public void addService(Object object) throws Exception {
        if (object instanceof Service) {
            Service service = (Service) object;
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onServiceAdd(this, service);
            }
            servicesToClose.add(service);
        }
        startServices(object);
    }

    // Helper methods
    // -----------------------------------------------------------------------

    public Language resolveLanguage(String language) {
        Language answer;
        synchronized (languages) {
            answer = languages.get(language);

            // check if the language is singleton, if so return the shared instance
            if (answer instanceof IsSingleton) {
                boolean singleton = ((IsSingleton) answer).isSingleton();
                if (singleton) {
                    return answer;
                }
            }

            // language not known or not singleton, then use resolver
            answer = getLanguageResolver().resolveLanguage(language, this);
            if (answer != null) {
                languages.put(language, answer);
            }
        }

        // no language resolved
        return answer;
    }

    // Properties
    // -----------------------------------------------------------------------

    public TypeConverter getTypeConverter() {
        if (typeConverter == null) {
            typeConverter = createTypeConverter();
        }
        return typeConverter;
    }

    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    public TypeConverterRegistry getTypeConverterRegistry() {
        if (typeConverterRegistry == null) {
            // init type converter as its lazy
            if (typeConverter == null) {
                getTypeConverter();
            }
            // type converter is usually the default one that also is the registry
            if (typeConverter instanceof DefaultTypeConverter) {
                typeConverterRegistry = (DefaultTypeConverter) typeConverter;
            }
        }
        return typeConverterRegistry;
    }

    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        this.typeConverterRegistry = typeConverterRegistry;
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

    /**
     * Sets the registry to the given JNDI context
     *
     * @param jndiContext is the JNDI context to use as the registry
     * @see #setRegistry(org.apache.camel.spi.Registry)
     */
    public void setJndiContext(Context jndiContext) {
        setRegistry(new JndiRegistry(jndiContext));
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public List<LifecycleStrategy> getLifecycleStrategies() {
        return lifecycleStrategies;
    }

    public void setLifecycleStrategies(List<LifecycleStrategy> lifecycleStrategies) {
        this.lifecycleStrategies = lifecycleStrategies;
    }

    public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        this.lifecycleStrategies.add(lifecycleStrategy);
    }

    public List<RouteDefinition> getRouteDefinitions() {
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

        // for backwards compatible or if user add them here instead of the setXXX methods

        if (interceptStrategy instanceof Tracer) {
            setTracing(true);
        } else if (interceptStrategy instanceof HandleFault) {
            setHandleFault(true);
        } else if (interceptStrategy instanceof StreamCaching) {
            setStreamCaching(true);
        } else if (interceptStrategy instanceof Delayer) {
            setDelayer(((Delayer)interceptStrategy).getDelay());
        }
    }

    public void setStreamCaching(Boolean cache) {
        this.streamCache = cache;
    }

    public boolean isStreamCaching() {
        return streamCache;
    }

    public void setTracing(Boolean tracing) {
        this.trace = tracing;
    }

    public boolean isTracing() {
        return trace;
    }

    public boolean isHandleFault() {
        return handleFault;
    }

    public void setHandleFault(Boolean handleFault) {
        this.handleFault = handleFault;
    }

    public Long getDelayer() {
        return delay;
    }

    public void setDelayer(long delay) {
        this.delay = delay;
    }

    public ProducerTemplate createProducerTemplate() {
        return new DefaultProducerTemplate(this);
    }

    public ConsumerTemplate createConsumerTemplate() {
        return new DefaultConsumerTemplate(this);
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

    public void setProducerServicePool(ServicePool<Endpoint, Producer> producerServicePool) {
        this.producerServicePool = producerServicePool;
    }

    public ServicePool<Endpoint, Producer> getProducerServicePool() {
        return producerServicePool;
    }

    public void start() throws Exception {
        super.start();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting routes");
        }
        // the context is now considered started (i.e. isStarted() == true))
        // starting routes is done after, not during context startup
        synchronized (this) {
            for (RouteService routeService : routeServices.values()) {
                routeService.start();
            }
        }
        if (LOG.isDebugEnabled()) {
            for (int i = 0; i < getRoutes().size(); i++) {
                LOG.debug("Route " + i + ": " + getRoutes().get(i));
            }
            LOG.debug("Started routes");
        }

        LOG.info("Apache Camel " + getVersion() + " (CamelContext:" + getName() + ") started");
        EventHelper.notifyCamelContextStarted(this);
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected synchronized void doStart() throws Exception {
        LOG.info("Apache Camel " + getVersion() + " (CamelContext:" + getName() + ") is starting");
        EventHelper.notifyCamelContextStarting(this);

        startServices(producerServicePool);

        if (isStreamCaching()) {
            // only add a new stream cache if not already configured
            if (StreamCaching.getStreamCaching(this) == null) {
                LOG.debug("StreamCaching is enabled");
                addInterceptStrategy(new StreamCaching());
            }
        }

        if (isTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            LOG.debug("Tracing is enabled");
        }

        if (isHandleFault()) {
            // only add a new handle fault if not already configured
            if (HandleFault.getHandleFault(this) == null) {
                LOG.debug("HandleFault is enabled");
                addInterceptStrategy(new HandleFault());
            }
        }

        if (getDelayer() != null && getDelayer() > 0) {
            // only add a new delayer if not already configured
            if (Delayer.getDelayer(this) == null) {
                long millis = getDelayer();
                LOG.debug("Delayer is enabled with: " + millis + " ms.");
                addInterceptStrategy(new Delayer(millis));
            }
        }


        Iterator<LifecycleStrategy> it = lifecycleStrategies.iterator();
        while (it.hasNext()) {
            LifecycleStrategy strategy = it.next();
            try {
                strategy.onContextStart(this);
            } catch (Exception e) {
                // not all containers allow access to its MBeanServer (such as OC4j)
                // so here we remove the troublesome strategy to be able to continue
                LOG.warn("Cannot start lifecycle strategy: " + strategy + ". This strategy will be removed. Cause " + e.getMessage(), e);
                it.remove();
            }
        }

        forceLazyInitialization();
        startServices(components.values());

         // To avoid initiating the routeDefinitions after stopping the camel context
        if (!routeDefinitionInitiated) {            
            startRouteDefinitions(routeDefinitions);
            routeDefinitionInitiated = true;
        }

        // starting will continue in the start method
    }

    protected synchronized void doStop() throws Exception {
        LOG.info("Apache Camel " + getVersion() + " (CamelContext:" + getName() + ") is stopping");
        EventHelper.notifyCamelContextStopping(this);

        // the stop order is important
        stopServices(endpoints.values());
        endpoints.clear();

        stopServices(routeServices.values());
        // do not clear route services as we can start Camel again and get the route back as before
        stopServices(producerServicePool);

        stopServices(components.values());
        components.clear();

        stopServices(servicesToClose);
        servicesToClose.clear();

        try {
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onContextStop(this);
            }
        } catch (Exception e) {
            LOG.warn("Cannot stop lifecycle strategies: " + e.getMessage());
        }

        LOG.info("Apache Camel " + getVersion() + " (CamelContext:" + getName() + ") stopped");
        EventHelper.notifyCamelContextStopped(this);
    }

    private void stopServices(Object service) throws Exception {
        // allow us to do custom work before delegating to service helper
        ServiceHelper.stopService(service);
    }

    @SuppressWarnings("unchecked")
    private void stopServices(Collection services) throws Exception {
        // close them in reverse order as they where added
        List reverse = new ArrayList(services);
        Collections.reverse(reverse);
        for (Object service : reverse) {
            stopServices(service);
        }
    }

    private void startServices(Object service) throws Exception {
        ServiceHelper.startService(service);
    }

    protected void startRouteDefinitions(Collection<RouteDefinition> list) throws Exception {
        if (list != null) {
            for (RouteDefinition route : list) {
                startRoute(route);
            }
        }
    }

    /**
     * Starts the given route service
     */
    protected synchronized void startRouteService(RouteService routeService) throws Exception {
        String key = routeService.getId();
        ServiceStatus status = getRouteStatus(key);

        if (status != null && status.isStarted()) {
            // already started, then stop it
            LOG.debug("Route " + key + " is already started");
            return;
        } else {
            routeServices.put(key, routeService);
            if (shouldStartRoutes()) {
                routeService.start();
            }
        }
    }

    /**
     * Lets force some lazy initialization to occur upfront before we start any
     * components and create routes
     */
    protected void forceLazyInitialization() {
        getInjector();
        getLanguageResolver();
        getTypeConverter();
    }

    /**
     * Lazily create a default implementation
     */
    protected TypeConverter createTypeConverter() {
        DefaultTypeConverter answer = new DefaultTypeConverter(packageScanClassResolver, getInjector(), getDefaultFactoryFinder());
        typeConverterRegistry = answer;
        return answer;
    }

    /**
     * Lazily create a default implementation
     */
    protected Injector createInjector() {
        FactoryFinder finder = getDefaultFactoryFinder();
        try {
            return (Injector) finder.newInstance("Injector");
        } catch (NoFactoryAvailableException e) {
            // lets use the default
            return new ReflectionInjector();
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
     * Strategy method for attempting to convert the bean from a {@link Registry} to an endpoint using
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

    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        this.dataFormats = dataFormats;
    }

    public Map<String, DataFormatDefinition> getDataFormats() {
        return dataFormats;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public FactoryFinder getDefaultFactoryFinder() {
        if (defaultFactoryFinder == null) {
            defaultFactoryFinder = factoryFinderResolver.resolveDefaultFactoryFinder(getClassResolver());
        }
        return defaultFactoryFinder;
    }

    public void setFactoryFinderResolver(FactoryFinderResolver resolver) {
        this.factoryFinderResolver = resolver;
    }

    public FactoryFinder getFactoryFinder(String path) throws NoFactoryAvailableException {
        synchronized (factories) {
            FactoryFinder answer = factories.get(path);
            if (answer == null) {
                answer = factoryFinderResolver.resolveFactoryFinder(getClassResolver(), path);
                factories.put(path, answer);
            }
            return answer;
        }
    }

    public ClassResolver getClassResolver() {
        return classResolver;
    }

    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    public PackageScanClassResolver getPackageScanClassResolver() {
        return packageScanClassResolver;
    }

    public void setPackageScanClassResolver(PackageScanClassResolver packageScanClassResolver) {
        this.packageScanClassResolver = packageScanClassResolver;
    }

    public List<String> getComponentNames() {
        synchronized (components) {
            List<String> answer = new ArrayList<String>();
            for (String name : components.keySet()) {
                answer.add(name);
            }
            return answer;
        }
    }

    public List<String> getLanguageNames() {
        synchronized (languages) {
            List<String> answer = new ArrayList<String>();
            for (String name : languages.keySet()) {
                answer.add(name);
            }
            return answer;
        }
    }

    public NodeIdFactory getNodeIdFactory() {
        return nodeIdFactory;
    }

    public void setNodeIdFactory(NodeIdFactory idFactory) {
        this.nodeIdFactory = idFactory;
    }

    public ManagementStrategy getManagementStrategy() {
        return managementStrategy;
    }

    public void setManagementStrategy(ManagementStrategy managementStrategy) {
        this.managementStrategy = managementStrategy;
    }

    public InterceptStrategy getDefaultTracer() {
        if (defaultTracer == null) {
            defaultTracer = new Tracer();
        }
        return defaultTracer;
    }

    protected synchronized String getEndpointKey(String uri, Endpoint endpoint) {
        if (endpoint.isSingleton()) {
            return uri;
        } else {
            // lets try find the first endpoint key which is free
            for (int counter = 0; true; counter++) {
                String key = (counter > 0) ? uri + ":" + counter : uri;
                if (!endpoints.containsKey(key)) {
                    return key;
                }
            }
        }
    }
    
    protected Map<String, RouteService> getRouteServices() {
        return routeServices;
    }

    @Override
    public String toString() {
        return "CamelContext(" + getName() + ")";
    }

}
