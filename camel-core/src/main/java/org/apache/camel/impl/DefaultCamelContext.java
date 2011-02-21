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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.Context;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.IsSingleton;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupListener;
import org.apache.camel.SuspendableService;
import org.apache.camel.TypeConverter;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.converter.BaseTypeConverterRegistry;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.converter.LazyLoadingTypeConverter;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.management.DefaultManagementLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.Constants;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.processor.interceptor.Debug;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.StreamCaching;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionInjector;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the context used to configure routes and the policies to use.
 *
 * @version 
 */
public class DefaultCamelContext extends ServiceSupport implements CamelContext, SuspendableService {
    private final transient Logger log = LoggerFactory.getLogger(getClass());
    private JAXBContext jaxbContext;
    private CamelContextNameStrategy nameStrategy = new DefaultCamelContextNameStrategy();
    private String managementName;
    private ClassLoader applicationContextClassLoader;
    private final Map<EndpointKey, Endpoint> endpoints = new EndpointRegistry();
    private final AtomicInteger endpointKeyCounter = new AtomicInteger();
    private final List<EndpointStrategy> endpointStrategies = new ArrayList<EndpointStrategy>();
    private final Map<String, Component> components = new HashMap<String, Component>();
    private Set<Route> routes;
    private final List<Service> servicesToClose = new ArrayList<Service>();
    private final Set<StartupListener> startupListeners = new LinkedHashSet<StartupListener>();
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
    private AtomicBoolean managementStrategyInitialized = new AtomicBoolean(false);
    private final List<RouteDefinition> routeDefinitions = new ArrayList<RouteDefinition>();
    private List<InterceptStrategy> interceptStrategies = new ArrayList<InterceptStrategy>();

    // special flags to control the first startup which can are special
    private volatile boolean firstStartDone;
    private volatile boolean doNotStartRoutesOnFirstStart;
    private final ThreadLocal<Boolean> isStartingRoutes = new ThreadLocal<Boolean>();
    private Boolean autoStartup = Boolean.TRUE;
    private Boolean trace = Boolean.FALSE;
    private Boolean streamCache = Boolean.FALSE;
    private Boolean handleFault = Boolean.FALSE;
    private Boolean disableJMX = Boolean.FALSE;
    private Boolean lazyLoadTypeConverters = Boolean.FALSE;
    private Boolean useMDCLogging = Boolean.FALSE;
    private Long delay;
    private ErrorHandlerBuilder errorHandlerBuilder;
    private Map<String, DataFormatDefinition> dataFormats = new HashMap<String, DataFormatDefinition>();
    private DataFormatResolver dataFormatResolver = new DefaultDataFormatResolver();
    private Map<String, String> properties = new HashMap<String, String>();
    private FactoryFinderResolver factoryFinderResolver = new DefaultFactoryFinderResolver();
    private FactoryFinder defaultFactoryFinder;
    private final Map<String, FactoryFinder> factories = new HashMap<String, FactoryFinder>();
    private final Map<String, RouteService> routeServices = new LinkedHashMap<String, RouteService>();
    private final Map<String, RouteService> suspendedRouteServices = new LinkedHashMap<String, RouteService>();
    private ClassResolver classResolver = new DefaultClassResolver();
    private PackageScanClassResolver packageScanClassResolver;
    // we use a capacity of 100 per endpoint, so for the same endpoint we have at most 100 producers in the pool
    // so if we have 6 endpoints in the pool, we can have 6 x 100 producers in total
    private ServicePool<Endpoint, Producer> producerServicePool = new SharedProducerServicePool(100);
    private NodeIdFactory nodeIdFactory = new DefaultNodeIdFactory();
    private ProcessorFactory processorFactory;
    private InterceptStrategy defaultTracer;
    private InflightRepository inflightRepository = new DefaultInflightRepository();
    private final List<RouteStartupOrder> routeStartupOrder = new ArrayList<RouteStartupOrder>();
    // start auto assigning route ids using numbering 1000 and upwards
    private int defaultRouteStartupOrder = 1000;
    private ShutdownStrategy shutdownStrategy = new DefaultShutdownStrategy(this);
    private ShutdownRoute shutdownRoute = ShutdownRoute.Default;
    private ShutdownRunningTask shutdownRunningTask = ShutdownRunningTask.CompleteCurrentTaskOnly;
    private ExecutorServiceStrategy executorServiceStrategy = new DefaultExecutorServiceStrategy(this);
    private Debugger debugger;
    private UuidGenerator uuidGenerator = createDefaultUuidGenerator();
    private final StopWatch stopWatch = new StopWatch(false);
    private Date startDate;

    public DefaultCamelContext() {
        super();

        // use WebSphere specific resolver if running on WebSphere
        if (WebSpherePackageScanClassResolver.isWebSphereClassLoader(this.getClass().getClassLoader())) {
            log.info("Using WebSphere specific PackageScanClassResolver");
            packageScanClassResolver = new WebSpherePackageScanClassResolver("META-INF/services/org/apache/camel/TypeConverter");
        } else {
            packageScanClassResolver = new DefaultPackageScanClassResolver();
        }
    }

    /**
     * Creates the {@link CamelContext} using the given JNDI context as the registry
     *
     * @param jndiContext the JNDI context
     */
    public DefaultCamelContext(Context jndiContext) {
        this();
        setJndiContext(jndiContext);
    }

    /**
     * Creates the {@link CamelContext} using the given registry
     *
     * @param registry the registry
     */
    public DefaultCamelContext(Registry registry) {
        this();
        setRegistry(registry);
    }

    public String getName() {
        return getNameStrategy().getName();
    }

    /**
     * Sets the name of the this context.
     *
     * @param name the name
     */
    public void setName(String name) {
        // use an explicit name strategy since an explicit name was provided to be used
        this.nameStrategy = new ExplicitCamelContextNameStrategy(name);
    }

    public CamelContextNameStrategy getNameStrategy() {
        return nameStrategy;
    }

    public void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        this.nameStrategy = nameStrategy;
    }

    public String getManagementName() {
        return managementName;
    }

    public void setManagementName(String managementName) {
        this.managementName = managementName;
    }

    public Component hasComponent(String componentName) {
        return components.get(componentName);
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
                    throw new RuntimeCamelException("Cannot auto create component: " + name, e);
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
            throw new IllegalArgumentException("Found component of type: " 
                + component.getClass() + " instead of expected: " + componentType);
        }
    }

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

    // Endpoint Management Methods
    // -----------------------------------------------------------------------

    public Collection<Endpoint> getEndpoints() {
        synchronized (endpoints) {
            return new ArrayList<Endpoint>(endpoints.values());
        }
    }

    public Map<String, Endpoint> getEndpointMap() {
        synchronized (endpoints) {
            TreeMap<String, Endpoint> answer = new TreeMap<String, Endpoint>();
            for (Map.Entry<EndpointKey, Endpoint> entry : endpoints.entrySet()) {
                answer.put(entry.getKey().get(), entry.getValue());
            }
            return answer;
        }
    }

    public Endpoint hasEndpoint(String uri) {
        synchronized (endpoints) {
            return endpoints.get(getEndpointKey(uri));
        }
    }

    public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
        Endpoint oldEndpoint;
        synchronized (endpoints) {
            startServices(endpoint);
            oldEndpoint = endpoints.remove(getEndpointKey(uri));
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

    public Collection<Endpoint> removeEndpoints(String uri) throws Exception {
        Collection<Endpoint> answer = new ArrayList<Endpoint>();
        Endpoint oldEndpoint = endpoints.remove(getEndpointKey(uri));
        if (oldEndpoint != null) {
            answer.add(oldEndpoint);
            stopServices(oldEndpoint);
        } else {
            for (Map.Entry entry : endpoints.entrySet()) {
                oldEndpoint = (Endpoint)entry.getValue();
                if (EndpointHelper.matchEndpoint(oldEndpoint.getEndpointUri(), uri)) {
                    answer.add(oldEndpoint);
                    stopServices(oldEndpoint);
                }
            }
            for (Endpoint endpoint : answer) {
                endpoints.remove(getEndpointKey(endpoint.getEndpointUri()));
            }
        }

        // notify lifecycle its being removed
        for (Endpoint endpoint : answer) {
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onEndpointRemove(endpoint);
            }
        }

        return answer;
    }

    public Endpoint getEndpoint(String uri) {
        ObjectHelper.notEmpty(uri, "uri");

        if (log.isTraceEnabled()) {
            log.trace("Getting endpoint with uri: " + uri);
        }

        // in case path has property placeholders then try to let property component resolve those
        try {
            uri = resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }

        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        uri = normalizeEndpointUri(uri);

        if (log.isTraceEnabled()) {
            log.trace("Getting endpoint with normalized uri: " + uri);
        }

        Endpoint answer;
        String scheme = null;
        synchronized (endpoints) {
            answer = endpoints.get(getEndpointKey(uri));
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

                            if (answer != null && log.isDebugEnabled()) {
                                log.debug(uri + " converted to endpoint: " + answer + " by component: " + component);
                            }
                        }
                    }

                    if (answer == null) {
                        // no component then try in registry and elsewhere
                        answer = createEndpoint(uri);
                    }

                    if (answer != null) {
                        addService(answer);
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
        if (endpoint == null) {
            throw new NoSuchEndpointException(name);
        }
        if (endpoint instanceof InterceptSendToEndpoint) {
            endpoint = ((InterceptSendToEndpoint) endpoint).getDelegate();
        }
        if (endpointType.isInstance(endpoint)) {
            return endpointType.cast(endpoint);
        } else {
            throw new IllegalArgumentException("The endpoint is not of type: " + endpointType 
                + " but is: " + endpoint.getClass().getCanonicalName());
        }
    }

    public void addRegisterEndpointCallback(EndpointStrategy strategy) {
        if (!endpointStrategies.contains(strategy)) {
            // let it be invoked for already registered endpoints so it can catch-up.
            endpointStrategies.add(strategy);
            for (Endpoint endpoint : getEndpoints()) {
                Endpoint newEndpoint = strategy.registerEndpoint(endpoint.getEndpointUri(), endpoint);
                if (newEndpoint != null) {
                    // put will replace existing endpoint with the new endpoint
                    endpoints.put(getEndpointKey(endpoint.getEndpointUri()), newEndpoint);
                }
            }
        }
    }

    /**
     * Strategy to add the given endpoint to the internal endpoint registry
     *
     * @param uri      uri of the endpoint
     * @param endpoint the endpoint to add
     * @return the added endpoint
     */
    protected Endpoint addEndpointToRegistry(String uri, Endpoint endpoint) {
        ObjectHelper.notEmpty(uri, "uri");
        ObjectHelper.notNull(endpoint, "endpoint");

        for (EndpointStrategy strategy : endpointStrategies) {
            endpoint = strategy.registerEndpoint(uri, endpoint);
        }
        endpoints.put(getEndpointKey(uri, endpoint), endpoint);
        return endpoint;
    }

    /**
     * Normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order.
     *
     * @param uri the uri
     * @return normalized uri
     * @throws ResolveEndpointFailedException if uri cannot be normalized
     */
    protected static String normalizeEndpointUri(String uri) {
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
        return uri;
    }

    /**
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link EndpointRegistry}
     *
     * @param uri the endpoint uri
     * @return the key
     */
    protected EndpointKey getEndpointKey(String uri) {
        return new EndpointKey(uri);
    }

    /**
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link EndpointRegistry}
     *
     * @param uri      the endpoint uri
     * @param endpoint the endpoint
     * @return the key
     */
    protected EndpointKey getEndpointKey(String uri, Endpoint endpoint) {
        if (endpoint != null && !endpoint.isSingleton()) {
            int counter = endpointKeyCounter.incrementAndGet();
            return new EndpointKey(uri + ":" + counter);
        } else {
            return new EndpointKey(uri);
        }
    }

    // Route Management Methods
    // -----------------------------------------------------------------------

    /**
     * Returns the order in which the route inputs was started.
     * <p/>
     * The order may not be according to the startupOrder defined on the route.
     * For example a route could be started manually later, or new routes added at runtime.
     *
     * @return a list in the order how routes was started
     */
    public List<RouteStartupOrder> getRouteStartupOrder() {
        return routeStartupOrder;
    }

    public synchronized List<Route> getRoutes() {
        if (routes == null) {
            routes = new LinkedHashSet<Route>();
        }

        // lets return a copy of the collection as objects are removed later
        // when services are stopped
        return new ArrayList<Route>(routes);
    }

    public Route getRoute(String id) {
        for (Route route : getRoutes()) {
            if (route.getId().equals(id)) {
                return route;
            }
        }
        return null;
    }

    @Deprecated
    public void setRoutes(List<Route> routes) {
        throw new UnsupportedOperationException("Overriding existing routes is not supported yet, use addRouteCollection instead");
    }

    synchronized void removeRouteCollection(Collection<Route> routes) {
        if (this.routes != null) {
            this.routes.removeAll(routes);
        }
    }

    synchronized void addRouteCollection(Collection<Route> routes) throws Exception {
        if (this.routes == null) {
            this.routes = new LinkedHashSet<Route>();
        }

        if (routes != null) {
            this.routes.addAll(routes);
        }
    }

    public void addRoutes(RoutesBuilder builder) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Adding routes from builder: " + builder);
        }
        // lets now add the routes from the builder
        builder.addRoutesToCamelContext(this);
    }

    public synchronized RoutesDefinition loadRoutesDefinition(InputStream is) throws Exception {
        // load routes using JAXB
        if (jaxbContext == null) {
            jaxbContext = JAXBContext.newInstance(Constants.JAXB_CONTEXT_PACKAGES);
        }

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Object result = unmarshaller.unmarshal(is);

        if (result == null) {
            throw new IOException("Cannot unmarshal to routes using JAXB from input stream: " + is);
        }

        // can either be routes or a single route
        RoutesDefinition answer = null;
        if (result instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) result;
            answer = new RoutesDefinition();
            answer.getRoutes().add(route);
        } else if (result instanceof RoutesDefinition) {
            answer = (RoutesDefinition) result;
        } else {
            throw new IllegalArgumentException("Unmarshalled object is an unsupported type: " + ObjectHelper.className(result) + " -> " + result);
        }

        return answer;
    }

    public synchronized void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
        this.routeDefinitions.addAll(routeDefinitions);
        if (shouldStartRoutes()) {
            startRouteDefinitions(routeDefinitions);
        }
    }

    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        addRouteDefinitions(Arrays.asList(routeDefinition));
    }

    /**
     * Removes the route definition with the given key.
     *
     * @return true if one or more routes was removed
     */
    protected boolean removeRouteDefinition(String key) {
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

    public synchronized void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        this.routeDefinitions.removeAll(routeDefinitions);
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
    }

    public synchronized void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        String id = routeDefinition.idOrCreate(nodeIdFactory);
        stopRoute(id);
        removeRoute(id);
    }

    public ServiceStatus getRouteStatus(String key) {
        RouteService routeService = routeServices.get(key);
        if (routeService != null) {
            return routeService.getStatus();
        }
        return null;
    }

    public void startRoute(RouteDefinition route) throws Exception {
        // indicate we are staring the route using this thread so
        // we are able to query this if needed
        isStartingRoutes.set(true);
        try {
            // must ensure route is prepared, before we can start it
            route.prepare();

            List<Route> routes = new ArrayList<Route>();
            List<RouteContext> routeContexts = route.addRoutes(this, routes);
            RouteService routeService = new RouteService(this, route, routeContexts, routes);
            startRouteService(routeService, true);
        } finally {
            // we are done staring routes
            isStartingRoutes.remove();
        }
    }

    public boolean isStartingRoutes() {
        Boolean answer = isStartingRoutes.get();
        return answer != null && answer;
    }

    public void stopRoute(RouteDefinition route) throws Exception {
        stopRoute(route.idOrCreate(nodeIdFactory));
    }

    public synchronized void startRoute(String routeId) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            startRouteService(routeService, false);
        }
    }

    public synchronized void resumeRoute(String routeId) throws Exception {
        if (!routeSupportsSuspension(routeId)) {
            // start route if suspension is not supported
            startRoute(routeId);
            return;
        }

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            resumeRouteService(routeService);
        }
    }

    public synchronized boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            RouteStartupOrder route = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);

            boolean completed = getShutdownStrategy().shutdown(this, route, timeout, timeUnit, abortAfterTimeout);
            if (completed) {
                // must stop route service as well
                routeService.setRemovingRoutes(false);
                stopRouteService(routeService);
            } else {
                // shutdown was aborted, make sure route is re-started properly
                startRouteService(routeService, false);
            }
            return completed;
        } 
        return false;
    }
    
    public synchronized void stopRoute(String routeId) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
            RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
            routes.add(order);

            getShutdownStrategy().shutdown(this, routes);
            // must stop route service as well
            routeService.setRemovingRoutes(false);
            stopRouteService(routeService);
        }
    }

    public synchronized void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
            RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
            routes.add(order);

            getShutdownStrategy().shutdown(this, routes, timeout, timeUnit);
            // must stop route service as well
            routeService.setRemovingRoutes(false);
            stopRouteService(routeService);
        }
    }

    public synchronized void shutdownRoute(String routeId) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
            RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
            routes.add(order);

            getShutdownStrategy().shutdown(this, routes);
            // must stop route service as well (and remove the routes from management)
            routeService.setRemovingRoutes(true);
            stopRouteService(routeService);
        }
    }

    public synchronized void shutdownRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
            RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
            routes.add(order);

            getShutdownStrategy().shutdown(this, routes, timeout, timeUnit);
            // must stop route service as well (and remove the routes from management)
            routeService.setRemovingRoutes(true);
            stopRouteService(routeService);
        }
    }

    public synchronized boolean removeRoute(String routeId) throws Exception {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            if (getRouteStatus(routeId).isStopped()) {
                routeService.setRemovingRoutes(true);
                shutdownRouteService(routeService);
                removeRouteDefinition(routeId);
                ServiceHelper.stopAndShutdownServices(routeService);
                routeServices.remove(routeId);
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    public synchronized void suspendRoute(String routeId) throws Exception {
        if (!routeSupportsSuspension(routeId)) {
            // stop if we suspend is not supported
            stopRoute(routeId);
            return;
        }

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
            RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
            routes.add(order);

            getShutdownStrategy().suspend(this, routes);
            // must suspend route service as well
            routeService.setRemovingRoutes(false);
            suspendRouteService(routeService);
        }
    }

    public synchronized void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        if (!routeSupportsSuspension(routeId)) {
            stopRoute(routeId, timeout, timeUnit);
            return;
        }

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            List<RouteStartupOrder> routes = new ArrayList<RouteStartupOrder>(1);
            RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
            routes.add(order);

            getShutdownStrategy().suspend(this, routes, timeout, timeUnit);
            // must suspend route service as well
            routeService.setRemovingRoutes(false);
            suspendRouteService(routeService);
        }
    }

    public void addService(Object object) throws Exception {
        if (object instanceof Service) {
            Service service = (Service) object;

            for (LifecycleStrategy strategy : lifecycleStrategies) {
                if (service instanceof Endpoint) {
                    // use specialized endpoint add
                    strategy.onEndpointAdd((Endpoint) service);
                } else {
                    strategy.onServiceAdd(this, service, null);
                }
            }

            // only add to services to close if its a singleton
            // otherwise we could for example end up with a lot of prototype scope endpoints
            boolean singleton = true; // assume singleton by default
            if (service instanceof IsSingleton) {
                singleton = ((IsSingleton) service).isSingleton();
            }
            // do not add endpoints as they have their own list
            if (singleton && !(service instanceof Endpoint)) {
                servicesToClose.add(service);
            }
        }
        startServices(object);
    }

    public boolean hasService(Object object) {
        if (object instanceof Service) {
            Service service = (Service) object;
            return servicesToClose.contains(service);
        }
        return false;
    }

    public void addStartupListener(StartupListener listener) throws Exception {
        // either add to listener so we can invoke then later when CamelContext has been started
        // or invoke the callback right now
        if (isStarted()) {
            listener.onCamelContextStarted(this, true);
        } else {
            startupListeners.add(listener);
        }
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

    public String resolvePropertyPlaceholders(String text) throws Exception {
        // do not parse uris that are designated for the properties component as it will handle that itself
        if (text != null && !text.startsWith("properties:") && text.contains(PropertiesComponent.PREFIX_TOKEN)) {
            // the uri contains property placeholders so lookup mandatory properties component and let it parse it
            Component component = hasComponent("properties");
            if (component == null) {
                // then fallback to lookup the component
                component = getRegistry().lookup("properties", Component.class);
            }
            if (component == null) {
                throw new IllegalArgumentException("PropertiesComponent with name properties must be defined"
                        + " in CamelContext to support property placeholders.");
            }
            // force component to be created and registered as a component
            PropertiesComponent pc = getComponent("properties", PropertiesComponent.class);
            // the parser will throw exception if property key was not found
            String answer = pc.parseUri(text);
            if (log.isDebugEnabled()) {
                log.debug("Resolved text: " + text + " -> " + answer);
            }
            return answer;
        }

        // return original text as is
        return text;
    }

    // Properties
    // -----------------------------------------------------------------------

    public TypeConverter getTypeConverter() {
        if (typeConverter == null) {
            synchronized (this) {
                // we can synchronize on this as there is only one instance
                // of the camel context (its the container)
                typeConverter = createTypeConverter();
                try {
                    addService(typeConverter);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }
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
            if (typeConverter instanceof TypeConverterRegistry) {
                typeConverterRegistry = (TypeConverterRegistry) typeConverter;
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
        if (languageResolver == null) {
            languageResolver = new DefaultLanguageResolver();
        }
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
            setRegistry(registry);
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
        // wrap the registry so we always do propery placeholder lookups
        if (!(registry instanceof PropertyPlaceholderDelegateRegistry)) {
            registry = new PropertyPlaceholderDelegateRegistry(this, registry);
        }
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

    public RouteDefinition getRouteDefinition(String id) {
        for (RouteDefinition route : routeDefinitions) {
            if (route.getId().equals(id)) {
                return route;
            }
        }
        return null;
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

    public Boolean isStreamCaching() {
        return streamCache;
    }

    public void setTracing(Boolean tracing) {
        this.trace = tracing;
    }

    public Boolean isTracing() {
        return trace;
    }

    public Boolean isHandleFault() {
        return handleFault;
    }

    public void setHandleFault(Boolean handleFault) {
        this.handleFault = handleFault;
    }

    public Long getDelayer() {
        return delay;
    }

    public void setDelayer(Long delay) {
        this.delay = delay;
    }

    public ProducerTemplate createProducerTemplate() {
        int size = CamelContextHelper.getMaximumCachePoolSize(this);
        return createProducerTemplate(size);
    }

    public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
        DefaultProducerTemplate answer = new DefaultProducerTemplate(this);
        answer.setMaximumCacheSize(maximumCacheSize);
        // start it so its ready to use
        try {
            startServices(answer);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    public ConsumerTemplate createConsumerTemplate() {
        int size = CamelContextHelper.getMaximumCachePoolSize(this);
        return createConsumerTemplate(size);
    }

    public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
        DefaultConsumerTemplate answer = new DefaultConsumerTemplate(this);
        answer.setMaximumCacheSize(maximumCacheSize);
        // start it so its ready to use
        try {
            startServices(answer);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    public void setProducerServicePool(ServicePool<Endpoint, Producer> producerServicePool) {
        this.producerServicePool = producerServicePool;
    }

    public ServicePool<Endpoint, Producer> getProducerServicePool() {
        return producerServicePool;
    }

    public String getUptime() {
        // compute and log uptime
        if (startDate == null) {
            return "not started";
        }
        long delta = new Date().getTime() - startDate.getTime();
        return TimeUtils.printDuration(delta);
    }

    @Override
    protected void doSuspend() throws Exception {
        EventHelper.notifyCamelContextSuspending(this);

        log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") is suspending");
        StopWatch watch = new StopWatch();

        // update list of started routes to be suspended
        // because we only want to suspend started routes
        // (so when we resume we only resume the routes which actually was suspended)
        for (Map.Entry<String, RouteService> entry : getRouteServices().entrySet()) {
            if (entry.getValue().getStatus().isStarted()) {
                suspendedRouteServices.put(entry.getKey(), entry.getValue());
            }
        }

        // assemble list of startup ordering so routes can be shutdown accordingly
        List<RouteStartupOrder> orders = new ArrayList<RouteStartupOrder>();
        for (Map.Entry<String, RouteService> entry : suspendedRouteServices.entrySet()) {
            Route route = entry.getValue().getRoutes().iterator().next();
            Integer order = entry.getValue().getRouteDefinition().getStartupOrder();
            if (order == null) {
                order = defaultRouteStartupOrder++;
            }
            orders.add(new DefaultRouteStartupOrder(order, route, entry.getValue()));
        }

        // suspend routes using the shutdown strategy so it can shutdown in correct order
        // routes which doesn't support suspension will be stopped instead
        getShutdownStrategy().suspend(this, orders);

        // mark the route services as suspended or stopped
        for (RouteService service : suspendedRouteServices.values()) {
            if (routeSupportsSuspension(service.getId())) {
                service.suspend();
            } else {
                service.stop();
            }
        }

        watch.stop();
        if (log.isInfoEnabled()) {
            log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") is suspended in " + TimeUtils.printDuration(watch.taken()));
        }

        EventHelper.notifyCamelContextSuspended(this);
    }

    @Override
    protected void doResume() throws Exception {
        try {
            EventHelper.notifyCamelContextResuming(this);

            log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") is resuming");
            StopWatch watch = new StopWatch();

            // start the suspended routes (do not check for route clashes, and indicate)
            doStartOrResumeRoutes(suspendedRouteServices, false, true, true, false);

            // mark the route services as resumed (will be marked as started) as well
            for (RouteService service : suspendedRouteServices.values()) {
                if (routeSupportsSuspension(service.getId())) {
                    service.resume();
                } else {
                    service.start();
                }
            }

            watch.stop();
            if (log.isInfoEnabled()) {
                log.info("Resumed " + suspendedRouteServices.size() + " routes");
                log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") resumed in " + TimeUtils.printDuration(watch.taken()));
            }

            // and clear the list as they have been resumed
            suspendedRouteServices.clear();

            EventHelper.notifyCamelContextResumed(this);
        } catch (Exception e) {
            EventHelper.notifyCamelContextResumeFailed(this, e);
            throw e;
        }
    }

    public void start() throws Exception {
        startDate = new Date();
        stopWatch.restart();
        log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") is starting");

        doNotStartRoutesOnFirstStart = !firstStartDone && !isAutoStartup();
        firstStartDone = true;

        // super will invoke doStart which will prepare internal services and start routes etc.
        super.start();

        stopWatch.stop();
        if (log.isInfoEnabled()) {
            // count how many routes are actually started
            int started = 0;
            for (Route route : getRoutes()) {
                if (getRouteStatus(route.getId()).isStarted()) {
                    started++;
                }
            }
            log.info("Total " + getRoutes().size() + " routes, of which " + started + " is started.");
            log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") started in " + TimeUtils.printDuration(stopWatch.taken()));
        }
        EventHelper.notifyCamelContextStarted(this);
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected synchronized void doStart() throws Exception {
        try {
            doStartCamel();
        } catch (Exception e) {
            // fire event that we failed to start
            EventHelper.notifyCamelContextStartupFailed(this, e);
            // rethrow cause
            throw e;
        }
    }

    private void doStartCamel() throws Exception {
        if (isStreamCaching()) {
            // only add a new stream cache if not already configured
            if (StreamCaching.getStreamCaching(this) == null) {
                log.info("StreamCaching is enabled on CamelContext: " + getName());
                addInterceptStrategy(new StreamCaching());
            }
        }

        if (isTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            log.info("Tracing is enabled on CamelContext: " + getName());
        }

        if (isUseMDCLogging()) {
            // log if MDC has been enabled
            log.info("MDC logging is enabled on CamelContext: " + getName());
        }

        if (isHandleFault()) {
            // only add a new handle fault if not already configured
            if (HandleFault.getHandleFault(this) == null) {
                log.info("HandleFault is enabled on CamelContext: " + getName());
                addInterceptStrategy(new HandleFault());
            }
        }

        if (getDelayer() != null && getDelayer() > 0) {
            // only add a new delayer if not already configured
            if (Delayer.getDelayer(this) == null) {
                long millis = getDelayer();
                log.info("Delayer is enabled with: " + millis + " ms. on CamelContext: " + getName());
                addInterceptStrategy(new Delayer(millis));
            }
        }
        
        // register debugger
        if (getDebugger() != null) {
            log.info("Debugger: " + getDebugger() + " is enabled on CamelContext: " + getName());
            // register this camel context on the debugger
            getDebugger().setCamelContext(this);
            startServices(getDebugger());
            addInterceptStrategy(new Debug(getDebugger()));
        }

        // start management strategy before lifecycles are started
        getManagementStrategy().start();

        // start lifecycle strategies
        Iterator<LifecycleStrategy> it = lifecycleStrategies.iterator();
        while (it.hasNext()) {
            LifecycleStrategy strategy = it.next();
            try {
                strategy.onContextStart(this);
            } catch (VetoCamelContextStartException e) {
                // okay we should not start Camel since it was vetoed
                log.warn("Lifecycle strategy vetoed starting CamelContext (" + getName() + ")", e);
                throw e;
            } catch (Exception e) {
                log.warn("Lifecycle strategy " + strategy + " failed starting CamelContext (" + getName() + ")", e);
                throw e;
            }
        }

        // start notifiers as services
        for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
            if (notifier instanceof Service) {
                Service service = (Service) notifier;
                for (LifecycleStrategy strategy : lifecycleStrategies) {
                    strategy.onServiceAdd(this, service, null);
                }
            }
            startServices(notifier);
        }

        // must let some bootstrap service be started before we can notify the starting event
        EventHelper.notifyCamelContextStarting(this);

        forceLazyInitialization();
        addService(executorServiceStrategy);
        addService(producerServicePool);
        addService(inflightRepository);
        addService(shutdownStrategy);

        startServices(components.values());

        // start the route definitions before the routes is started
        startRouteDefinitions(routeDefinitions);

        // start routes
        if (doNotStartRoutesOnFirstStart) {
            log.info("Cannot start routes as CamelContext has been configured with autoStartup=false");
        }

        // invoke this logic to warmup the routes and if possible also start the routes
        doStartOrResumeRoutes(routeServices, true, !doNotStartRoutesOnFirstStart, false, true);

        // starting will continue in the start method
    }

    protected synchronized void doStop() throws Exception {
        stopWatch.restart();
        log.info("Apache Camel " + getVersion() + " (CamelContext:" + getName() + ") is shutting down");
        EventHelper.notifyCamelContextStopping(this);

        // stop route inputs in the same order as they was started so we stop the very first inputs first
        try {
            shutdownStrategy.shutdown(this, getRouteStartupOrder());
        } catch (Throwable e) {
            log.warn("Error occurred while shutting down routes. This exception will be ignored.", e);
        }
        getRouteStartupOrder().clear();

        shutdownServices(routeServices.values());
        // do not clear route services or startup listeners as we can start Camel again and get the route back as before

        // but clear any suspend routes
        suspendedRouteServices.clear();

        // the stop order is important

        // shutdown debugger
        ServiceHelper.stopAndShutdownService(getDebugger());

        shutdownServices(endpoints.values());
        endpoints.clear();

        shutdownServices(components.values());
        components.clear();

        try {
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onContextStop(this);
            }
        } catch (Throwable e) {
            log.warn("Error occurred while stopping lifecycle strategies. This exception will be ignored.", e);
        }

        // shutdown services as late as possible
        shutdownServices(servicesToClose);
        servicesToClose.clear();

        // must notify that we are stopped before stopping the management strategy
        EventHelper.notifyCamelContextStopped(this);

        // stop the notifier service
        for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
            shutdownServices(notifier);
        }

        // shutdown management as the last one
        shutdownServices(managementStrategy);

        // stop the lazy created so they can be re-created on restart
        forceStopLazyInitialization();

        stopWatch.stop();
        if (log.isInfoEnabled()) {
            log.info("Uptime: " + getUptime());
            log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") is shutdown in " + TimeUtils.printDuration(stopWatch.taken()));
        }

        // and clear start date
        startDate = null;
    }

    /**
     * Starts or resumes the routes
     *
     * @param routeServices  the routes to start (will only start a route if its not already started)
     * @param checkClash     whether to check for startup ordering clash
     * @param startConsumer  whether the route consumer should be started. Can be used to warmup the route without starting the consumer.
     * @param resumeConsumer whether the route consumer should be resumed.
     * @param addingRoutes   whether we are adding new routes
     * @throws Exception is thrown if error starting routes
     */
    protected void doStartOrResumeRoutes(Map<String, RouteService> routeServices, boolean checkClash,
                                         boolean startConsumer, boolean resumeConsumer, boolean addingRoutes) throws Exception {
        // filter out already started routes
        Map<String, RouteService> filtered = new LinkedHashMap<String, RouteService>();
        for (Map.Entry<String, RouteService> entry : routeServices.entrySet()) {
            boolean startable;

            Consumer consumer = entry.getValue().getRoutes().iterator().next().getConsumer();
            if (consumer instanceof SuspendableService) {
                // consumer could be suspended, which is not reflected in the RouteService status
                startable = ((SuspendableService) consumer).isSuspended();
            } else if (consumer instanceof ServiceSupport) {
                // consumer could be stopped, which is not reflected in the RouteService status
                startable = ((ServiceSupport) consumer).getStatus().isStartable();
            } else {
                // no consumer so use state from route service
                startable = entry.getValue().getStatus().isStartable();
            }

            if (startable) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        if (!filtered.isEmpty()) {
            // the context is now considered started (i.e. isStarted() == true))
            // starting routes is done after, not during context startup
            safelyStartRouteServices(checkClash, startConsumer, resumeConsumer, addingRoutes, filtered.values());
        }

        // now notify any startup aware listeners as all the routes etc has been started,
        // allowing the listeners to do custom work after routes has been started
        for (StartupListener startup : startupListeners) {
            startup.onCamelContextStarted(this, isStarted());
        }
    }

    protected boolean routeSupportsSuspension(String routeId) {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            return routeService.getRoutes().iterator().next().supportsSuspension();
        }
        return false;
    }

    private void shutdownServices(Object service) {
        // do not rethrow exception as we want to keep shutting down in case of problems

        // allow us to do custom work before delegating to service helper
        try {
            ServiceHelper.stopAndShutdownService(service);
        } catch (Throwable e) {
            log.warn("Error occurred while shutting down service: " + service + ". This exception will be ignored.", e);
            // fire event
            EventHelper.notifyServiceStopFailure(this, service, e);
        }
    }

    private void shutdownServices(Collection<?> services) {
        // reverse stopping by default
        shutdownServices(services, true);
    }

    private void shutdownServices(Collection<?> services, boolean reverse) {
        Collection<Object> list = CastUtils.cast(services);
        if (reverse) {
            ArrayList<Object> reverseList = new ArrayList<Object>(services);
            Collections.reverse(reverseList);
            list = reverseList;
        }

        for (Object service : list) {
            shutdownServices(service);
        }
    }

    private void startServices(Object service) throws Exception {
        // it can be a collection so ensure we look inside it
        if (service instanceof Collection<?>) {
            for (Object element : (Collection<?>)service) {
                startServices(element);
            }
        }

        // and register startup aware so they can be notified when
        // camel context has been started
        if (service instanceof StartupListener) {
            StartupListener listener = (StartupListener) service;
            addStartupListener(listener);
        }

        // and then start the service
        ServiceHelper.startService(service);
    }

    private void resumeServices(Object service) throws Exception {
        // it can be a collection so ensure we look inside it
        if (service instanceof Collection<?>) {
            for (Object element : (Collection<?>)service) {
                resumeServices(element);
            }
        }

        // and then start the service
        ServiceHelper.resumeService(service);
    }

    private void stopServices(Object service) throws Exception {
        // allow us to do custom work before delegating to service helper
        try {
            ServiceHelper.stopService(service);
        } catch (Exception e) {
            // fire event
            EventHelper.notifyServiceStopFailure(this, service, e);
            // rethrow to signal error with stopping
            throw e;
        }
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
    protected synchronized void startRouteService(RouteService routeService, boolean addingRoutes) throws Exception {
        // we may already be starting routes so remember this, so we can unset accordingly in finally block
        boolean alreadyStartingRoutes = isStartingRoutes();
        if (!alreadyStartingRoutes) {
            isStartingRoutes.set(true);
        }

        try {
            // the route service could have been suspended, and if so then resume it instead
            if (routeService.getStatus().isSuspended()) {
                resumeRouteService(routeService);
            } else {
                // start the route service
                routeServices.put(routeService.getId(), routeService);
                if (shouldStartRoutes()) {
                    // this method will log the routes being started
                    safelyStartRouteServices(true, true, true, false, addingRoutes, routeService);
                    // start route services if it was configured to auto startup and we are not adding routes
                    boolean autoStartup = routeService.getRouteDefinition().isAutoStartup(this);
                    if (!addingRoutes || autoStartup) {
                        // start the route since auto start is enabled or we are starting a route (not adding new routes)
                        routeService.start();
                    }
                }
            }
        } finally {
            if (!alreadyStartingRoutes) {
                isStartingRoutes.remove();
            }
        }
    }

    /**
     * Resumes the given route service
     */
    protected synchronized void resumeRouteService(RouteService routeService) throws Exception {
        // the route service could have been stopped, and if so then start it instead
        if (!routeService.getStatus().isSuspended()) {
            startRouteService(routeService, false);
        } else {
            // resume the route service
            if (shouldStartRoutes()) {
                // this method will log the routes being started
                safelyStartRouteServices(true, false, true, true, false, routeService);
                // must resume route service as well
                routeService.resume();
            }
        }
    }

    protected synchronized void stopRouteService(RouteService routeService) throws Exception {
        routeService.stop();
        for (Route route : routeService.getRoutes()) {
            if (log.isInfoEnabled()) {
                log.info("Route: " + route.getId() + " stopped, was consuming from: " + route.getConsumer().getEndpoint());
            }
        }
    }

    protected synchronized void shutdownRouteService(RouteService routeService) throws Exception {
        routeService.shutdown();
        for (Route route : routeService.getRoutes()) {
            if (log.isInfoEnabled()) {
                log.info("Route: " + route.getId() + " shutdown and removed, was consuming from: " + route.getConsumer().getEndpoint());
            }
        }
    }

    protected synchronized void suspendRouteService(RouteService routeService) throws Exception {
        routeService.suspend();
        for (Route route : routeService.getRoutes()) {
            if (log.isInfoEnabled()) {
                log.info("Route: " + route.getId() + " suspended, was consuming from: " + route.getConsumer().getEndpoint());
            }
        }
    }

    /**
     * Starts the routes services in a proper manner which ensures the routes will be started in correct order,
     * check for clash and that the routes will also be shutdown in correct order as well.
     * <p/>
     * This method <b>must</b> be used to start routes in a safe manner.
     *
     * @param checkClash     whether to check for startup order clash
     * @param startConsumer  whether the route consumer should be started. Can be used to warmup the route without starting the consumer.
     * @param resumeConsumer whether the route consumer should be resumed.
     * @param addingRoutes   whether we are adding new routes
     * @param routeServices  the routes
     * @throws Exception is thrown if error starting the routes
     */
    protected synchronized void safelyStartRouteServices(boolean checkClash, boolean startConsumer, boolean resumeConsumer,
                                                         boolean addingRoutes, Collection<RouteService> routeServices) throws Exception {
        // list of inputs to start when all the routes have been prepared for starting
        // we use a tree map so the routes will be ordered according to startup order defined on the route
        Map<Integer, DefaultRouteStartupOrder> inputs = new TreeMap<Integer, DefaultRouteStartupOrder>();

        // figure out the order in which the routes should be started
        for (RouteService routeService : routeServices) {
            DefaultRouteStartupOrder order = doPrepareRouteToBeStarted(routeService);
            // check for clash before we add it as input
            if (checkClash) {
                doCheckStartupOrderClash(order, inputs);
            }
            inputs.put(order.getStartupOrder(), order);
        }

        // warm up routes before we start them
        doWarmUpRoutes(inputs, startConsumer);

        if (startConsumer) {
            if (resumeConsumer) {
                // and now resume the routes
                doResumeRouteConsumers(inputs, addingRoutes);
            } else {
                // and now start the routes
                // and check for clash with multiple consumers of the same endpoints which is not allowed
                doStartRouteConsumers(inputs, addingRoutes);
            }
        }

        // inputs no longer needed
        inputs.clear();
    }

    /**
     * @see #safelyStartRouteServices(boolean,boolean,boolean,boolean,java.util.Collection)
     */
    protected synchronized void safelyStartRouteServices(boolean forceAutoStart, boolean checkClash, boolean startConsumer,
                                                         boolean resumeConsumer, boolean addingRoutes, RouteService... routeServices) throws Exception {
        safelyStartRouteServices(checkClash, startConsumer, resumeConsumer, addingRoutes, Arrays.asList(routeServices));
    }

    private DefaultRouteStartupOrder doPrepareRouteToBeStarted(RouteService routeService) {
        // add the inputs from this route service to the list to start afterwards
        // should be ordered according to the startup number
        Integer startupOrder = routeService.getRouteDefinition().getStartupOrder();
        if (startupOrder == null) {
            // auto assign a default startup order
            startupOrder = defaultRouteStartupOrder++;
        }

        // create holder object that contains information about this route to be started
        Route route = routeService.getRoutes().iterator().next();
        return new DefaultRouteStartupOrder(startupOrder, route, routeService);
    }

    private boolean doCheckStartupOrderClash(DefaultRouteStartupOrder answer, Map<Integer, DefaultRouteStartupOrder> inputs) throws FailedToStartRouteException {
        // TODO: There could potential be routeId clash as well, so we should check for that as well

        // check for clash by startupOrder id
        DefaultRouteStartupOrder other = inputs.get(answer.getStartupOrder());
        if (other != null && answer != other) {
            String otherId = other.getRoute().getId();
            throw new FailedToStartRouteException(answer.getRoute().getId(), "startupOrder clash. Route " + otherId + " already has startupOrder "
                + answer.getStartupOrder() + " configured which this route have as well. Please correct startupOrder to be unique among all your routes.");
        }
        // check in existing already started as well
        for (RouteStartupOrder order : routeStartupOrder) {
            String otherId = order.getRoute().getId();
            if (answer.getRoute().getId().equals(otherId)) {
                // its the same route id so skip clash check as its the same route (can happen when using suspend/resume)
            } else if (answer.getStartupOrder() == order.getStartupOrder()) {
                throw new FailedToStartRouteException(answer.getRoute().getId(), "startupOrder clash. Route " + otherId + " already has startupOrder "
                    + answer.getStartupOrder() + " configured which this route have as well. Please correct startupOrder to be unique among all your routes.");
            }
        }
        return true;
    }

    private void doWarmUpRoutes(Map<Integer, DefaultRouteStartupOrder> inputs, boolean autoStartup) throws Exception {
        // now prepare the routes by starting its services before we start the input
        for (Map.Entry<Integer, DefaultRouteStartupOrder> entry : inputs.entrySet()) {
            // defer starting inputs till later as we want to prepare the routes by starting
            // all their processors and child services etc.
            // then later we open the floods to Camel by starting the inputs
            // what this does is to ensure Camel is more robust on starting routes as all routes
            // will then be prepared in time before we start inputs which will consume messages to be routed
            RouteService routeService = entry.getValue().getRouteService();
            if (log.isDebugEnabled()) {
                log.debug("Warming up route id: " + routeService.getId() + " having autoStartup=" + autoStartup);
            }
            routeService.warmUp();
        }
    }

    private void doResumeRouteConsumers(Map<Integer, DefaultRouteStartupOrder> inputs, boolean addingRoutes) throws Exception {
        doStartOrResumeRouteConsumers(inputs, true, addingRoutes);
    }

    private void doStartRouteConsumers(Map<Integer, DefaultRouteStartupOrder> inputs, boolean addingRoutes) throws Exception {
        doStartOrResumeRouteConsumers(inputs, false, addingRoutes);
    }

    private void doStartOrResumeRouteConsumers(Map<Integer, DefaultRouteStartupOrder> inputs, boolean resumeOnly, boolean addingRoute) throws Exception {
        List<Endpoint> routeInputs = new ArrayList<Endpoint>();

        for (Map.Entry<Integer, DefaultRouteStartupOrder> entry : inputs.entrySet()) {
            Integer order = entry.getKey();
            Route route = entry.getValue().getRoute();
            RouteService routeService = entry.getValue().getRouteService();

            // if we are starting camel, then skip routes which are configured to not be auto started
            boolean autoStartup = routeService.getRouteDefinition().isAutoStartup(this);
            if (addingRoute && !autoStartup) {
                log.info("Cannot start route " + routeService.getId() + " as its configured with autoStartup=false");
                continue;
            }

            // start the service
            for (Consumer consumer : routeService.getInputs().values()) {
                Endpoint endpoint = consumer.getEndpoint();

                // check multiple consumer violation
                if (!doCheckMultipleConsumerSupportClash(endpoint, routeInputs)) {
                    throw new FailedToStartRouteException(routeService.getId(),
                        "Multiple consumers for the same endpoint is not allowed: " + endpoint);
                }

                // start the consumer on the route
                if (log.isDebugEnabled()) {
                    log.debug("Route: " + route.getId() + " >>> " + route);
                    if (resumeOnly) {
                        log.debug("Resuming consumer (order: " + order + ") on route: " + route.getId());
                    } else {
                        log.debug("Starting consumer (order: " + order + ") on route: " + route.getId());
                    }
                }

                if (resumeOnly && route.supportsSuspension()) {
                    // if we are resuming and the route can be resumed
                    resumeServices(consumer);
                    log.info("Route: " + route.getId() + " resumed and consuming from: " + endpoint);
                } else {
                    // when starting we should invoke the lifecycle strategies
                    for (LifecycleStrategy strategy : lifecycleStrategies) {
                        strategy.onServiceAdd(this, consumer, route);
                    }
                    startServices(consumer);
                    log.info("Route: " + route.getId() + " started and consuming from: " + endpoint);
                }

                routeInputs.add(endpoint);

                // add to the order which they was started, so we know how to stop them in reverse order
                // but only add if we haven't already registered it before (we dont want to double add when restarting)
                boolean found = false;
                for (RouteStartupOrder other : routeStartupOrder) {
                    if (other.getRoute().getId() == route.getId()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    routeStartupOrder.add(entry.getValue());
                }
            }

            // and start the route service (no need to start children as they are already warmed up)
            routeService.start(false);
        }
    }

    private boolean doCheckMultipleConsumerSupportClash(Endpoint endpoint, List<Endpoint> routeInputs) {
        // is multiple consumers supported
        boolean multipleConsumersSupported = false;
        if (endpoint instanceof MultipleConsumersSupport) {
            multipleConsumersSupported = ((MultipleConsumersSupport) endpoint).isMultipleConsumersSupported();
        }

        if (multipleConsumersSupported) {
            // multiple consumer allowed, so return true
            return true;
        }

        // check in progress list
        if (routeInputs.contains(endpoint)) {
            return false;
        }

        return true;
    }

    /**
     * Lets force some lazy initialization to occur upfront before we start any
     * components and create routes
     */
    protected void forceLazyInitialization() {
        getInjector();
        getLanguageResolver();
        getTypeConverterRegistry();
        getTypeConverter();
    }

    /**
     * Lets force clear lazy initialization so they can be re-created on restart
     */
    protected void forceStopLazyInitialization() {
        injector = null;
        languageResolver = null;
        typeConverterRegistry = null;
        typeConverter = null;
    }

    /**
     * Lazily create a default implementation
     */
    protected TypeConverter createTypeConverter() {
        BaseTypeConverterRegistry answer;
        if (isLazyLoadTypeConverters()) {
            answer = new LazyLoadingTypeConverter(packageScanClassResolver, getInjector(), getDefaultFactoryFinder());
        } else {
            answer = new DefaultTypeConverter(packageScanClassResolver, getInjector(), getDefaultFactoryFinder());
        }
        setTypeConverterRegistry(answer);
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
        synchronized (managementStrategyInitialized) {
            if (managementStrategyInitialized.compareAndSet(false, true)) {
                managementStrategy = createManagementStrategy();
            }
            return managementStrategy;
        }
    }

    public void setManagementStrategy(ManagementStrategy managementStrategy) {
        synchronized (managementStrategyInitialized) {
            if (managementStrategyInitialized.get()) {
                log.warn("Resetting ManagementStrategy for context " + getName());
            }

            this.managementStrategy = managementStrategy;
            managementStrategyInitialized.set(true);
        }
    }

    public InterceptStrategy getDefaultTracer() {
        if (defaultTracer == null) {
            defaultTracer = new Tracer();
        }
        return defaultTracer;
    }

    public void setDefaultTracer(InterceptStrategy defaultTracer) {
        this.defaultTracer = defaultTracer;
    }

    public void disableJMX() {
        disableJMX = true;
    }

    public InflightRepository getInflightRepository() {
        return inflightRepository;
    }

    public void setInflightRepository(InflightRepository repository) {
        this.inflightRepository = repository;
    }

    public void setAutoStartup(Boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public Boolean isAutoStartup() {
        return autoStartup != null && autoStartup;
    }
    
    public Boolean isLazyLoadTypeConverters() {
        return lazyLoadTypeConverters != null && lazyLoadTypeConverters;
    }
    
    public void setLazyLoadTypeConverters(Boolean lazyLoadTypeConverters) {
        this.lazyLoadTypeConverters = lazyLoadTypeConverters;
    }

    public Boolean isUseMDCLogging() {
        return useMDCLogging != null && useMDCLogging;
    }

    public void setUseMDCLogging(Boolean useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    public ClassLoader getApplicationContextClassLoader() {
        return applicationContextClassLoader;
    }

    public void setApplicationContextClassLoader(ClassLoader classLoader) {
        applicationContextClassLoader = classLoader;
    }

    public DataFormatResolver getDataFormatResolver() {
        return dataFormatResolver;
    }

    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        this.dataFormatResolver = dataFormatResolver;
    }

    public DataFormat resolveDataFormat(String name) {
        return dataFormatResolver.resolveDataFormat(name, this);
    }

    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        return dataFormatResolver.resolveDataFormatDefinition(name, this);
    }

    public ShutdownStrategy getShutdownStrategy() {
        return shutdownStrategy;
    }

    public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        this.shutdownStrategy = shutdownStrategy;
    }

    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    public ExecutorServiceStrategy getExecutorServiceStrategy() {
        return executorServiceStrategy;
    }

    public void setExecutorServiceStrategy(ExecutorServiceStrategy executorServiceStrategy) {
        this.executorServiceStrategy = executorServiceStrategy;
    }

    public ProcessorFactory getProcessorFactory() {
        return processorFactory;
    }

    public void setProcessorFactory(ProcessorFactory processorFactory) {
        this.processorFactory = processorFactory;
    }

    public Debugger getDebugger() {
        return debugger;
    }

    public void setDebugger(Debugger debugger) {
        this.debugger = debugger;
    }
    
    public UuidGenerator getUuidGenerator() {
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    protected Map<String, RouteService> getRouteServices() {
        return routeServices;
    }

    protected ManagementStrategy createManagementStrategy() {
        ManagementStrategy answer;

        if (disableJMX || Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
            log.info("JMX is disabled. Using DefaultManagementStrategy.");
            answer = new DefaultManagementStrategy();
        } else {
            try {
                log.info("JMX enabled. Using ManagedManagementStrategy.");
                answer = new ManagedManagementStrategy(new DefaultManagementAgent(this));
                // must start it to ensure JMX works and can load needed Spring JARs
                startServices(answer);
                // prefer to have it at first strategy
                lifecycleStrategies.add(0, new DefaultManagementLifecycleStrategy(this));
            } catch (NoClassDefFoundError e) {
                answer = null;

                // if we can't instantiate the JMX enabled strategy then fallback to default
                // could be because of missing .jars on the classpath
                log.warn("Cannot find needed classes for JMX lifecycle strategy."
                        + " Needed class is in spring-context.jar using Spring 2.5 or newer"
                        + " (spring-jmx.jar using Spring 2.0.x)."
                        + " NoClassDefFoundError: " + e.getMessage());
            } catch (Exception e) {
                answer = null;
                log.warn("Cannot create JMX lifecycle strategy. Fallback to using DefaultManagementStrategy (non JMX).", e);
            }
        }

        if (answer == null) {
            log.warn("Cannot use JMX. Fallback to using DefaultManagementStrategy (non JMX).");
            answer = new DefaultManagementStrategy();
        }

        // inject CamelContext
        if (answer instanceof CamelContextAware) {
            CamelContextAware aware = (CamelContextAware) answer;
            aware.setCamelContext(this);
        }

        return answer;
    }

    @Override
    public String toString() {
        return "CamelContext(" + getName() + ")";
    }

    /**
     * Reset context counter to a preset value. Mostly used for tests to ensure a predictable getName()
     *
     * @param value new value for the context counter
     */
    public static void setContextCounter(int value) {
        DefaultCamelContextNameStrategy.setCounter(value);
    }

    private static UuidGenerator createDefaultUuidGenerator() {
        if (System.getProperty("com.google.appengine.runtime.environment") != null) {
            // either "Production" or "Development"
            return new JavaUuidGenerator();
        } else {
            return new ActiveMQUuidGenerator();
        }
    }
}
