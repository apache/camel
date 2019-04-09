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
package org.apache.camel.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.IsSingleton;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.PollingConsumer;
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
import org.apache.camel.StatefulService;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.TypeConverter;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.builder.DefaultFluentProducerTemplate;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderSupport;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.interceptor.Debug;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.CamelContextTracker;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError.Phase;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.function.ThrowingRunnable;
import org.slf4j.MDC;
import static org.apache.camel.spi.UnitOfWork.MDC_CAMEL_CONTEXT_ID;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public abstract class AbstractCamelContext extends ServiceSupport implements ModelCamelContext, CatalogCamelContext, Suspendable {

    public enum Initialization {
        Eager, Default, Lazy
    }

    private String version;
    private final AtomicBoolean vetoStated = new AtomicBoolean();
    private String managementName;
    private ClassLoader applicationContextClassLoader;
    private final AtomicInteger endpointKeyCounter = new AtomicInteger();
    private final List<EndpointStrategy> endpointStrategies = new ArrayList<>();
    private final Map<String, Component> components = new ConcurrentHashMap<>();
    private final Set<Route> routes = new LinkedHashSet<>();
    private final List<Service> servicesToStop = new CopyOnWriteArrayList<>();
    private final List<StartupListener> startupListeners = new CopyOnWriteArrayList<>();
    private final DeferServiceStartupListener deferStartupListener = new DeferServiceStartupListener();
    private boolean autoCreateComponents = true;
    private final Map<String, Language> languages = new ConcurrentHashMap<>();
    private final List<LifecycleStrategy> lifecycleStrategies = new CopyOnWriteArrayList<>();
    private final List<RouteDefinition> routeDefinitions = new ArrayList<>();
    private final List<RestDefinition> restDefinitions = new ArrayList<>();
    private Map<String, RestConfiguration> restConfigurations = new ConcurrentHashMap<>();
    private Map<String, ServiceCallConfigurationDefinition> serviceCallConfigurations = new ConcurrentHashMap<>();
    private Map<String, HystrixConfigurationDefinition> hystrixConfigurations = new ConcurrentHashMap<>();
    private List<InterceptStrategy> interceptStrategies = new ArrayList<>();
    private List<RoutePolicyFactory> routePolicyFactories = new ArrayList<>();
    private Set<LogListener> logListeners = new LinkedHashSet<>();
    // special flags to control the first startup which can are special
    private volatile boolean firstStartDone;
    private volatile boolean doNotStartRoutesOnFirstStart;
    private final ThreadLocal<Boolean> isStartingRoutes = new ThreadLocal<>();
    private final ThreadLocal<Boolean> isSetupRoutes = new ThreadLocal<>();
    private Initialization initialization = Initialization.Default;
    private Boolean autoStartup = Boolean.TRUE;
    private Boolean trace = Boolean.FALSE;
    private Boolean messageHistory = Boolean.TRUE;
    private Boolean logMask = Boolean.FALSE;
    private Boolean logExhaustedMessageBody = Boolean.FALSE;
    private Boolean streamCache = Boolean.FALSE;
    private Boolean handleFault = Boolean.FALSE;
    private Boolean disableJMX = Boolean.FALSE;
    private Boolean loadTypeConverters = Boolean.TRUE;
    private Boolean typeConverterStatisticsEnabled = Boolean.FALSE;
    private Boolean useMDCLogging = Boolean.FALSE;
    private Boolean useDataType = Boolean.FALSE;
    private Boolean useBreadcrumb = Boolean.FALSE;
    private Boolean allowUseOriginalMessage = Boolean.FALSE;
    private Long delay;
    private ErrorHandlerFactory errorHandlerBuilder;
    private Map<String, DataFormatDefinition> dataFormats = new HashMap<>();
    private Map<String, String> globalOptions = new HashMap<>();
    private PropertiesComponent propertiesComponent;
    private final Map<String, FactoryFinder> factories = new ConcurrentHashMap<>();
    private final Map<String, RouteService> routeServices = new LinkedHashMap<>();
    private final Map<String, RouteService> suspendedRouteServices = new LinkedHashMap<>();

    private final Object lock = new Object();
    private volatile CamelContextNameStrategy nameStrategy;
    private volatile ManagementNameStrategy managementNameStrategy;
    private volatile Registry registry;
    private volatile TypeConverter typeConverter;
    private volatile TypeConverterRegistry typeConverterRegistry;
    private volatile Injector injector;
    private volatile CamelBeanPostProcessor beanPostProcessor;
    private volatile ComponentResolver componentResolver;
    private volatile LanguageResolver languageResolver;
    private volatile DataFormatResolver dataFormatResolver;
    private volatile ManagementStrategy managementStrategy;
    private volatile ManagementMBeanAssembler managementMBeanAssembler;
    private volatile RestRegistry restRegistry;
    private volatile HeadersMapFactory headersMapFactory;
    private volatile ClassResolver classResolver;
    private volatile PackageScanClassResolver packageScanClassResolver;
    private volatile ServicePool<Producer> producerServicePool;
    private volatile ServicePool<PollingConsumer> pollingConsumerServicePool;
    private volatile NodeIdFactory nodeIdFactory;
    private volatile ProcessorFactory processorFactory;
    private volatile MessageHistoryFactory messageHistoryFactory;
    private volatile FactoryFinderResolver factoryFinderResolver;
    private volatile FactoryFinder defaultFactoryFinder;
    private volatile StreamCachingStrategy streamCachingStrategy;
    private volatile InflightRepository inflightRepository;
    private volatile AsyncProcessorAwaitManager asyncProcessorAwaitManager;
    private volatile ShutdownStrategy shutdownStrategy;
    private volatile ModelJAXBContextFactory modelJAXBContextFactory;
    private volatile ExecutorServiceManager executorServiceManager;
    private volatile UuidGenerator uuidGenerator;
    private volatile UnitOfWorkFactory unitOfWorkFactory;
    private volatile ReloadStrategy reloadStrategy;
    private volatile RouteController routeController;
    private volatile ScheduledExecutorService errorHandlerExecutorService;

    private TransformerRegistry<TransformerKey> transformerRegistry;
    private ValidatorRegistry<ValidatorKey> validatorRegistry;
    private EndpointRegistry<EndpointKey> endpoints;
    private RuntimeEndpointRegistry runtimeEndpointRegistry;

    private final List<RouteStartupOrder> routeStartupOrder = new ArrayList<>();
    // start auto assigning route ids using numbering 1000 and upwards
    private int defaultRouteStartupOrder = 1000;
    private ShutdownRoute shutdownRoute = ShutdownRoute.Default;
    private ShutdownRunningTask shutdownRunningTask = ShutdownRunningTask.CompleteCurrentTaskOnly;
    private Debugger debugger;
    private final StopWatch stopWatch = new StopWatch(false);
    private Date startDate;
    private List<TransformerDefinition> transformers = new ArrayList<>();
    private List<ValidatorDefinition> validators = new ArrayList<>();

    private SSLContextParameters sslContextParameters;
    private final ThreadLocal<Set<String>> componentsInCreation = new ThreadLocal<Set<String>>() {
        @Override
        public Set<String> initialValue() {
            return new HashSet<>();
        }
    };
    private Map<Class<?>, Object> extensions = new ConcurrentHashMap<>();

    /**
     * Creates the {@link CamelContext} using {@link org.apache.camel.support.DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public AbstractCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link CamelContext} using the given registry
     *
     * @param registry the registry
     */
    public AbstractCamelContext(Registry registry) {
        this();
        setRegistry(registry);
    }

    public AbstractCamelContext(boolean init) {
        // create a provisional (temporary) endpoint registry at first since end users may access endpoints before CamelContext is started
        // we will later transfer the endpoints to the actual DefaultEndpointRegistry later, but we do this to starup Camel faster.
        this.endpoints = new ProvisionalEndpointRegistry();

        // add the defer service startup listener
        this.startupListeners.add(deferStartupListener);

        setDefaultExtension(HealthCheckRegistry.class, this::createHealthCheckRegistry);
        setDefaultExtension(RuntimeCamelCatalog.class, this::createRuntimeCamelCatalog);

        if (init) {
            init();
        }
    }

    public void doInit() {
        // setup management first since end users may use it to add event notifiers
        // using the management strategy before the CamelContext has been started
        setupManagement(null);

        // Call all registered trackers with this context
        // Note, this may use a partially constructed object
        CamelContextTracker.notifyContextCreated(this);
    }

    public <T extends CamelContext> T adapt(Class<T> type) {
        return type.cast(this);
    }

    @Override
    public <T> T getExtension(Class<T> type) {
        Object extension = extensions.get(type);
        if (extension instanceof Supplier) {
            extension = ((Supplier) extension).get();
            setExtension(type, (T) extension);
        }
        return (T) extension;
    }

    @Override
    public <T> void setExtension(Class<T> type, T module) {
        try {
            extensions.put(type, doAddService(module));
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public <T> void setDefaultExtension(Class<T> type, Supplier<T> module) {
        extensions.putIfAbsent(type, module);
    }

    public boolean isVetoStarted() {
        return vetoStated.get();
    }

    public Initialization getInitialization() {
        return initialization;
    }

    public void setInitialization(Initialization initialization) {
        this.initialization = initialization;
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
        setNameStrategy(new ExplicitCamelContextNameStrategy(name));
    }

    public CamelContextNameStrategy getNameStrategy() {
        if (nameStrategy == null) {
            synchronized (lock) {
                if (nameStrategy == null) {
                    setNameStrategy(createCamelContextNameStrategy());
                }
            }
        }
        return nameStrategy;
    }

    public void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        this.nameStrategy = doAddService(nameStrategy);
    }

    public ManagementNameStrategy getManagementNameStrategy() {
        if (managementNameStrategy == null) {
            synchronized (lock) {
                if (managementNameStrategy == null) {
                    setManagementNameStrategy(createManagementNameStrategy());
                }
            }
        }
        return managementNameStrategy;
    }

    public void setManagementNameStrategy(ManagementNameStrategy managementNameStrategy) {
        this.managementNameStrategy = doAddService(managementNameStrategy);
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
        component.setCamelContext(this);
        Component oldValue = components.putIfAbsent(componentName, component);
        if (oldValue != null) {
            throw new IllegalArgumentException("Cannot add component as its already previously added: " + componentName);
        }
        postInitComponent(componentName, component);
    }

    private void postInitComponent(String componentName, final Component component) {
        for (LifecycleStrategy strategy : lifecycleStrategies) {
            strategy.onComponentAdd(componentName, component);
        }

        // keep reference to properties component up to date
        if (component instanceof PropertiesComponent && "properties".equals(componentName)) {
            propertiesComponent = (PropertiesComponent) component;
        }
    }

    public Component getComponent(String name) {
        return getComponent(name, autoCreateComponents, true);
    }

    public Component getComponent(String name, boolean autoCreateComponents) {
        return getComponent(name, autoCreateComponents, true);
    }

    public Component getComponent(String name, boolean autoCreateComponents, boolean autoStart) {
        init();

        // Check if the named component is already being created, that would mean
        // that the initComponent has triggered a new getComponent
        if (componentsInCreation.get().contains(name)) {
            throw new IllegalStateException("Circular dependency detected, the component " + name + " is already being created");
        }

        try {
            // Flag used to mark a component of being created.
            final AtomicBoolean created = new AtomicBoolean(false);

            // atomic operation to get/create a component. Avoid global locks.
            final Component component = components.computeIfAbsent(name, new Function<String, Component>() {
                @Override
                public Component apply(String comp) {
                    created.set(true);
                    return AbstractCamelContext.this.initComponent(name, autoCreateComponents);
                }
            });

            // Start the component after its creation as if it is a component proxy
            // that creates/start a delegated component, we may end up in a deadlock
            if (component != null && created.get() && autoStart && (isStarted() || isStarting())) {
                // If the component is looked up after the context is started,
                // lets start it up.
                if (component instanceof Service) {
                    startService((Service)component);
                }
            }

            return component;
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot auto create component: " + name, e);
        } finally {
            // remove the reference to the component being created
            componentsInCreation.get().remove(name);
        }
    }

    /**
     * Function to initialize a component and auto start. Returns null if the autoCreateComponents is disabled
     */
    private Component initComponent(String name, boolean autoCreateComponents) {
        Component component = null;
        if (autoCreateComponents) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Using ComponentResolver: {} to resolve component with name: {}", getComponentResolver(), name);
                }

                // Mark the component as being created so we can detect circular
                // requests.
                //
                // In spring apps, the component resolver may trigger a new getComponent
                // because of the underlying bean factory and as the endpoints are
                // registered as singleton, the spring factory creates the bean
                // and then check the type so the getComponent is always triggered.
                //
                // Simple circular dependency:
                //
                //   <camelContext id="camel" xmlns="http://camel.apache.org/schema/spring">
                //     <route>
                //       <from id="twitter" uri="twitter://timeline/home?type=polling"/>
                //       <log message="Got ${body}"/>
                //     </route>
                //   </camelContext>
                //
                // Complex circular dependency:
                //
                //   <camelContext id="camel" xmlns="http://camel.apache.org/schema/spring">
                //     <route>
                //       <from id="log" uri="seda:test"/>
                //       <to id="seda" uri="log:test"/>
                //     </route>
                //   </camelContext>
                //
                // This would freeze the app (lock or infinite loop).
                //
                // See https://issues.apache.org/jira/browse/CAMEL-11225
                componentsInCreation.get().add(name);

                component = getComponentResolver().resolveComponent(name, this);
                if (component != null) {
                    component.setCamelContext(this);
                    postInitComponent(name, component);
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot auto create component: " + name, e);
            }
        }
        return component;
    }

    public <T extends Component> T getComponent(String name, Class<T> componentType) {
        Component component = getComponent(name);
        if (componentType.isInstance(component)) {
            return componentType.cast(component);
        } else {
            String message;
            if (component == null) {
                message = "Did not find component given by the name: " + name;
            } else {
                message = "Found component of type: " + component.getClass() + " instead of expected: " + componentType;
            }
            throw new IllegalArgumentException(message);
        }
    }

    public Component resolveComponent(String name) {
        Component answer = hasComponent(name);
        if (answer == null) {
            try {
                answer = getComponentResolver().resolveComponent(name, this);
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot resolve component: " + name, e);
            }
        }
        return answer;
    }

    public Component removeComponent(String componentName) {
        Component oldComponent = components.remove(componentName);
        if (oldComponent != null) {
            try {
                stopServices(oldComponent);
            } catch (Exception e) {
                log.warn("Error stopping component " + oldComponent + ". This exception will be ignored.", e);
            }
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onComponentRemove(componentName, oldComponent);
            }
        }
        // keep reference to properties component up to date
        if (oldComponent != null && "properties".equals(componentName)) {
            propertiesComponent = null;
        }
        return oldComponent;
    }

    // Endpoint Management Methods
    // -----------------------------------------------------------------------

    public EndpointRegistry<EndpointKey> getEndpointRegistry() {
        return endpoints;
    }

    public Collection<Endpoint> getEndpoints() {
        return new ArrayList<>(endpoints.values());
    }

    public Map<String, Endpoint> getEndpointMap() {
        Map<String, Endpoint> answer = new TreeMap<>();
        for (Map.Entry<EndpointKey, Endpoint> entry : endpoints.entrySet()) {
            answer.put(entry.getKey().get(), entry.getValue());
        }
        return answer;
    }

    public Endpoint hasEndpoint(String uri) {
        return endpoints.get(getEndpointKey(uri));
    }

    public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
        Endpoint oldEndpoint;

        startService(endpoint);
        oldEndpoint = endpoints.remove(getEndpointKey(uri));
        for (LifecycleStrategy strategy : lifecycleStrategies) {
            strategy.onEndpointAdd(endpoint);
        }
        addEndpointToRegistry(uri, endpoint);
        if (oldEndpoint != null && oldEndpoint != endpoint) {
            stopServices(oldEndpoint);
        }

        return oldEndpoint;
    }

    public void removeEndpoint(Endpoint endpoint) throws Exception {
        removeEndpoints(endpoint.getEndpointUri());
    }

    public Collection<Endpoint> removeEndpoints(String uri) throws Exception {
        Collection<Endpoint> answer = new ArrayList<>();
        Endpoint oldEndpoint = endpoints.remove(getEndpointKey(uri));
        if (oldEndpoint != null) {
            answer.add(oldEndpoint);
            stopServices(oldEndpoint);
        } else {
            for (Map.Entry<EndpointKey, Endpoint> entry : endpoints.entrySet()) {
                oldEndpoint = entry.getValue();
                if (EndpointHelper.matchEndpoint(this, oldEndpoint.getEndpointUri(), uri)) {
                    try {
                        stopServices(oldEndpoint);
                    } catch (Exception e) {
                        log.warn("Error stopping endpoint " + oldEndpoint + ". This exception will be ignored.", e);
                    }
                    answer.add(oldEndpoint);
                    endpoints.remove(entry.getKey());
                }
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
        init();

        StringHelper.notEmpty(uri, "uri");

        log.trace("Getting endpoint with uri: {}", uri);

        // in case path has property placeholders then try to let property component resolve those
        try {
            uri = resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }

        final String rawUri = uri;

        // normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order
        uri = normalizeEndpointUri(uri);

        log.trace("Getting endpoint with raw uri: {}, normalized uri: {}", rawUri, uri);

        Endpoint answer;
        String scheme = null;
        // use optimized method to get the endpoint uri
        EndpointKey key = getEndpointKeyPreNormalized(uri);
        answer = endpoints.get(key);
        if (answer == null) {
            try {
                // Use the URI prefix to find the component.
                String[] splitURI = StringHelper.splitOnCharacter(uri, ":", 2);
                if (splitURI[1] != null) {
                    scheme = splitURI[0];
                    log.trace("Endpoint uri: {} is from component with name: {}", uri, scheme);
                    Component component = getComponent(scheme);

                    // Ask the component to resolve the endpoint.
                    if (component != null) {
                        log.trace("Creating endpoint from uri: {} using component: {}", uri, component);

                        // Have the component create the endpoint if it can.
                        if (component.useRawUri()) {
                            answer = component.createEndpoint(rawUri);
                        } else {
                            answer = component.createEndpoint(uri);
                        }

                        if (answer != null && log.isDebugEnabled()) {
                            log.debug("{} converted to endpoint: {} by component: {}", URISupport.sanitizeUri(uri), answer, component);
                        }
                    }
                }

                if (answer == null) {
                    // no component then try in registry and elsewhere
                    answer = createEndpoint(uri);
                    log.trace("No component to create endpoint from uri: {} fallback lookup in registry -> {}", uri, answer);
                }

                if (answer == null && splitURI[1] == null) {
                    // the uri has no context-path which is rare and it was not referring to an endpoint in the registry
                    // so try to see if it can be created by a component

                    int pos = uri.indexOf('?');
                    String componentName = pos > 0 ? uri.substring(0, pos) : uri;

                    Component component = getComponent(componentName);

                    // Ask the component to resolve the endpoint.
                    if (component != null) {
                        log.trace("Creating endpoint from uri: {} using component: {}", uri, component);

                        // Have the component create the endpoint if it can.
                        if (component.useRawUri()) {
                            answer = component.createEndpoint(rawUri);
                        } else {
                            answer = component.createEndpoint(uri);
                        }

                        if (answer != null && log.isDebugEnabled()) {
                            log.debug("{} converted to endpoint: {} by component: {}", URISupport.sanitizeUri(uri), answer, component);
                        }
                    }

                }

                if (answer != null) {
                    addService(answer);
                    answer = addEndpointToRegistry(uri, answer);
                }
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
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
            endpoint = ((InterceptSendToEndpoint) endpoint).getOriginalEndpoint();
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
        StringHelper.notEmpty(uri, "uri");
        ObjectHelper.notNull(endpoint, "endpoint");

        // if there is endpoint strategies, then use the endpoints they return
        // as this allows to intercept endpoints etc.
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
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link DefaultEndpointRegistry}
     *
     * @param uri the endpoint uri
     * @return the key
     */
    protected EndpointKey getEndpointKey(String uri) {
        return new EndpointKey(uri);
    }

    /**
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link DefaultEndpointRegistry}
     *
     * @param uri the endpoint uri which is pre normalized
     * @return the key
     */
    protected EndpointKey getEndpointKeyPreNormalized(String uri) {
        return new EndpointKey(uri, true);
    }

    /**
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link DefaultEndpointRegistry}
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

    @Override
    public void setRouteController(RouteController routeController) {
        this.routeController = routeController;
        doAddService(routeController);
    }

    @Override
    public RouteController getRouteController() {
        if (routeController == null) {
            synchronized (lock) {
                if (routeController == null) {
                    setRouteController(createRouteController());
                }
            }
        }
        return routeController;
    }

    public List<RouteStartupOrder> getRouteStartupOrder() {
        return routeStartupOrder;
    }

    public List<Route> getRoutes() {
        // lets return a copy of the collection as objects are removed later when services are stopped
        if (routes.isEmpty()) {
            return Collections.emptyList();
        } else {
            synchronized (routes) {
                return new ArrayList<>(routes);
            }
        }
    }

    public Route getRoute(String id) {
        if (id != null) {
            for (Route route : getRoutes()) {
                if (route.getId().equals(id)) {
                    return route;
                }
            }
        }
        return null;
    }

    public Processor getProcessor(String id) {
        for (Route route : getRoutes()) {
            List<Processor> list = route.filter(id);
            if (list.size() == 1) {
                return list.get(0);
            }
        }
        return null;
    }

    public <T extends Processor> T getProcessor(String id, Class<T> type) {
        Processor answer = getProcessor(id);
        if (answer != null) {
            return type.cast(answer);
        }
        return null;
    }

    public ProcessorDefinition getProcessorDefinition(String id) {
        for (RouteDefinition route : getRouteDefinitions()) {
            Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
            while (it.hasNext()) {
                ProcessorDefinition proc = it.next();
                if (id.equals(proc.getId())) {
                    return proc;
                }
            }
        }
        return null;
    }

    public <T extends ProcessorDefinition> T getProcessorDefinition(String id, Class<T> type) {
        ProcessorDefinition answer = getProcessorDefinition(id);
        if (answer != null) {
            return type.cast(answer);
        }
        return null;
    }

    void removeRouteCollection(Collection<Route> routes) {
        synchronized (this.routes) {
            this.routes.removeAll(routes);
        }
    }

    void addRouteCollection(Collection<Route> routes) throws Exception {
        synchronized (this.routes) {
            this.routes.addAll(routes);
        }
    }

    public void addRoutes(final RoutesBuilder builder) throws Exception {
        init();
        log.debug("Adding routes from builder: {}", builder);
        doWithDefinedClassLoader(() -> builder.addRoutesToCamelContext(AbstractCamelContext.this));
    }

    public void addRouteDefinitions(InputStream is) throws Exception {
        RoutesDefinition def = ModelHelper.loadRoutesDefinition(this, is);
        if (def != null) {
            addRouteDefinitions(def.getRoutes());
        }
    }

    public synchronized void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (routeDefinitions == null || routeDefinitions.isEmpty()) {
            return;
        }
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
        this.routeDefinitions.addAll(routeDefinitions);
        if (shouldStartRoutes()) {
            startRouteDefinitions(routeDefinitions);
        }
    }

    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        addRouteDefinitions(Collections.singletonList(routeDefinition));
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
            if (route.idOrCreate(getNodeIdFactory()).equals(key)) {
                iter.remove();
                answer = true;
            }
        }
        return answer;
    }

    public synchronized void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
    }

    public synchronized void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        RouteDefinition toBeRemoved = routeDefinition;
        String id = routeDefinition.getId();
        if (id != null) {
            // remove existing route
            stopRoute(id);
            removeRoute(id);
            toBeRemoved = getRouteDefinition(id);
        }
        this.routeDefinitions.remove(toBeRemoved);
    }

    public ServiceStatus getRouteStatus(String key) {
        RouteService routeService = routeServices.get(key);
        if (routeService != null) {
            return routeService.getStatus();
        }
        return null;
    }

    public void startRoute(RouteDefinition route) throws Exception {
        // assign ids to the routes and validate that the id's is all unique
        RouteDefinitionHelper.forceAssignIds(this, routeDefinitions);
        String duplicate = RouteDefinitionHelper.validateUniqueIds(route, routeDefinitions);
        if (duplicate != null) {
            throw new FailedToStartRouteException(route.getId(), "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
        }

        // indicate we are staring the route using this thread so
        // we are able to query this if needed
        isStartingRoutes.set(true);
        try {
            // must ensure route is prepared, before we can start it
            route.prepare(this);

            List<Route> routes = new ArrayList<>();
            List<RouteContext> routeContexts = new RouteReifier(route).addRoutes(this, routes);
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

    public boolean isSetupRoutes() {
        Boolean answer = isSetupRoutes.get();
        return answer != null && answer;
    }

    public void startAllRoutes() throws Exception {
        doStartOrResumeRoutes(routeServices, true, true, false, false);
    }

    public synchronized void startRoute(String routeId) throws Exception {
        DefaultRouteError.reset(this, routeId);

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            try {
                startRouteService(routeService, false);
            } catch (Exception e) {
                DefaultRouteError.set(this, routeId, Phase.START, e);
                throw e;
            }
        }
    }

    public synchronized void resumeRoute(String routeId) throws Exception {
        DefaultRouteError.reset(this, routeId);

        try {
            if (!routeSupportsSuspension(routeId)) {
                // start route if suspension is not supported
                startRoute(routeId);
                return;
            }

            RouteService routeService = routeServices.get(routeId);
            if (routeService != null) {
                resumeRouteService(routeService);
                // must resume the route as well
                Route route = getRoute(routeId);
                ServiceHelper.resumeService(route);
            }
        } catch (Exception e) {
            DefaultRouteError.set(this, routeId, Phase.RESUME, e);
            throw e;
        }
    }

    public synchronized boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        DefaultRouteError.reset(this, routeId);

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            try {
                RouteStartupOrder route = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);

                boolean completed = getShutdownStrategy().shutdown(this, route, timeout, timeUnit, abortAfterTimeout);
                if (completed) {
                    // must stop route service as well
                    stopRouteService(routeService, false);
                } else {
                    // shutdown was aborted, make sure route is re-started properly
                    startRouteService(routeService, false);
                }
                return completed;
            } catch (Exception e) {
                DefaultRouteError.set(this, routeId, Phase.STOP, e);
                throw e;
            }
        }

        return false;
    }

    public void stopRoute(String routeId) throws Exception {
        doShutdownRoute(routeId, getShutdownStrategy().getTimeout(), getShutdownStrategy().getTimeUnit(), false);
    }

    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        doShutdownRoute(routeId, timeout, timeUnit, false);
    }

    protected synchronized void doShutdownRoute(String routeId, long timeout, TimeUnit timeUnit, boolean removingRoutes) throws Exception {
        DefaultRouteError.reset(this, routeId);

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            try {
                List<RouteStartupOrder> routes = new ArrayList<>(1);
                RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoutes().iterator().next(), routeService);
                routes.add(order);

                getShutdownStrategy().shutdown(this, routes, timeout, timeUnit);
                // must stop route service as well (and remove the routes from management)
                stopRouteService(routeService, removingRoutes);
            } catch (Exception e) {
                DefaultRouteError.set(this, routeId, removingRoutes ? Phase.SHUTDOWN : Phase.STOP, e);
                throw e;
            }
        }
    }

    public synchronized boolean removeRoute(String routeId) throws Exception {
        DefaultRouteError.reset(this, routeId);

        // remove the route from ErrorHandlerBuilder if possible
        if (getErrorHandlerFactory() instanceof ErrorHandlerBuilderSupport) {
            ErrorHandlerBuilderSupport builder = (ErrorHandlerBuilderSupport) getErrorHandlerFactory();
            builder.removeOnExceptionList(routeId);
        }

        // gather a map of all the endpoints in use by the routes, so we can known if a given endpoints is in use
        // by one or more routes, when we remove the route
        Map<String, Set<Endpoint>> endpointsInUse = new HashMap<>();
        for (Map.Entry<String, RouteService> entry : routeServices.entrySet()) {
            endpointsInUse.put(entry.getKey(), entry.getValue().gatherEndpoints());
        }

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            if (getRouteStatus(routeId).isStopped()) {
                try {
                    routeService.setRemovingRoutes(true);
                    shutdownRouteService(routeService);
                    removeRouteDefinition(routeId);
                    routeServices.remove(routeId);
                    // remove route from startup order as well, as it was removed
                    routeStartupOrder.removeIf(order -> order.getRoute().getId().equals(routeId));

                    // from the route which we have removed, then remove all its private endpoints
                    // (eg the endpoints which are not in use by other routes)
                    Set<Endpoint> toRemove = new LinkedHashSet<>();
                    for (Endpoint endpoint : endpointsInUse.get(routeId)) {
                        // how many times is the endpoint in use
                        int count = 0;
                        for (Set<Endpoint> endpoints : endpointsInUse.values()) {
                            if (endpoints.contains(endpoint)) {
                                count++;
                            }
                        }
                        // notice we will count ourselves so if there is only 1 then its safe to remove
                        if (count <= 1) {
                            toRemove.add(endpoint);
                        }
                    }
                    for (Endpoint endpoint : toRemove) {
                        log.debug("Removing: {} which was only in use by route: {}", endpoint, routeId);
                        removeEndpoint(endpoint);
                    }
                } catch  (Exception e) {
                    DefaultRouteError.set(this, routeId, Phase.REMOVE, e);
                    throw e;
                }

                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    public void suspendRoute(String routeId) throws Exception {
        suspendRoute(routeId, getShutdownStrategy().getTimeout(), getShutdownStrategy().getTimeUnit());
    }

    public synchronized void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        DefaultRouteError.reset(this, routeId);

        try {
            if (!routeSupportsSuspension(routeId)) {
                stopRoute(routeId, timeout, timeUnit);
                return;
            }

            RouteService routeService = routeServices.get(routeId);
            if (routeService != null) {
                List<RouteStartupOrder> routes = new ArrayList<>(1);
                Route route = routeService.getRoutes().iterator().next();
                RouteStartupOrder order = new DefaultRouteStartupOrder(1, route, routeService);
                routes.add(order);

                getShutdownStrategy().suspend(this, routes, timeout, timeUnit);
                // must suspend route service as well
                suspendRouteService(routeService);
                // must suspend the route as well
                if (route instanceof SuspendableService) {
                    ((SuspendableService) route).suspend();
                }
            }
        } catch (Exception e) {
            DefaultRouteError.set(this, routeId, Phase.SUSPEND, e);
            throw e;
        }
    }

    public void addService(Object object) throws Exception {
        addService(object, true);
    }

    public void addService(Object object, boolean stopOnShutdown) throws Exception {
        addService(object, stopOnShutdown, false);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception {
        internalAddService(object, stopOnShutdown, forceStart);
    }

    protected <T> T doAddService(T object) {
        return doAddService(object, true);
    }

    protected <T> T doAddService(T object, boolean stopOnShutdown) {
        return doAddService(object, stopOnShutdown, true);
    }

    protected <T> T doAddService(T object, boolean stopOnShutdown, boolean forceStart) {
        try {
            internalAddService(object, stopOnShutdown, forceStart);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return object;
    }

    private void internalAddService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception {

        // inject CamelContext
        if (object instanceof CamelContextAware) {
            CamelContextAware aware = (CamelContextAware) object;
            aware.setCamelContext(this);
        }

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

            if (!forceStart) {
                // now start the service (and defer starting if CamelContext is starting up itself)
                deferStartService(object, stopOnShutdown);
            } else {
                // only add to services to close if its a singleton
                // otherwise we could for example end up with a lot of prototype scope endpoints
                boolean singleton = true; // assume singleton by default
                if (object instanceof IsSingleton) {
                    singleton = ((IsSingleton) service).isSingleton();
                }
                // do not add endpoints as they have their own list
                if (singleton && !(service instanceof Endpoint)) {
                    // only add to list of services to stop if its not already there
                    if (stopOnShutdown && !hasService(service)) {
                        servicesToStop.add(service);
                    }
                }
                ServiceHelper.startService(service);
            }
        }
    }

    public boolean removeService(Object object) throws Exception {
        if (object instanceof Endpoint) {
            removeEndpoint((Endpoint) object);
            return true;
        }
        if (object instanceof Service) {
            Service service = (Service) object;
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onServiceRemove(this, service, null);
            }
            return servicesToStop.remove(service);
        }
        return false;
    }

    public boolean hasService(Object object) {
        if (object instanceof Service) {
            Service service = (Service) object;
            return servicesToStop.contains(service);
        }
        return false;
    }

    @Override
    public <T> T hasService(Class<T> type) {
        for (Service service : servicesToStop) {
            if (type.isInstance(service)) {
                return type.cast(service);
            }
        }
        return null;
    }

    @Override
    public <T> Set<T> hasServices(Class<T> type) {
        Set<T> set = new HashSet<>();
        for (Service service : servicesToStop) {
            if (type.isInstance(service)) {
                set.add((T) service);
            }
        }
        return set;
    }

    public void deferStartService(Object object, boolean stopOnShutdown) throws Exception {
        if (object instanceof Service) {
            Service service = (Service) object;

            // only add to services to close if its a singleton
            // otherwise we could for example end up with a lot of prototype scope endpoints
            boolean singleton = true; // assume singleton by default
            if (object instanceof IsSingleton) {
                singleton = ((IsSingleton) service).isSingleton();
            }
            // do not add endpoints as they have their own list
            if (singleton && !(service instanceof Endpoint)) {
                // only add to list of services to stop if its not already there
                if (stopOnShutdown && !hasService(service)) {
                    servicesToStop.add(service);
                }
            }
            // are we already started?
            if (isStarted()) {
                ServiceHelper.startService(service);
            } else {
                deferStartupListener.addService(service);
            }
        }
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

    public String getComponentParameterJsonSchema(String componentName) throws IOException {
        // use the component factory finder to find the package name of the component class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = getFactoryFinder(DefaultComponentResolver.RESOURCE_PATH);
        try {
            Class<?> clazz = null;
            try {
                clazz = finder.findClass(componentName);
            } catch (NoFactoryAvailableException e) {
                // ignore, i.e. if a component is an auto-configured spring-boot
                // component
            }

            if (clazz == null) {
                // fallback and find existing component
                Component existing = hasComponent(componentName);
                if (existing != null) {
                    clazz = existing.getClass();
                } else {
                    return null;
                }
            }

            String packageName = clazz.getPackage().getName();
            packageName = packageName.replace('.', '/');
            String path = packageName + "/" + componentName + ".json";

            ClassResolver resolver = getClassResolver();
            InputStream inputStream = resolver.loadResourceAsStream(path);
            log.debug("Loading component JSON Schema for: {} using class resolver: {} -> {}", componentName, resolver, inputStream);
            if (inputStream != null) {
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
            // special for ActiveMQ as it is really just JMS
            if ("ActiveMQComponent".equals(clazz.getSimpleName())) {
                return getComponentParameterJsonSchema("jms");
            } else {
                return null;
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException {
        // use the dataformat factory finder to find the package name of the dataformat class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = getFactoryFinder(DefaultDataFormatResolver.DATAFORMAT_RESOURCE_PATH);
        try {
            Class<?> clazz = null;
            try {
                clazz = finder.findClass(dataFormatName);
            } catch (NoFactoryAvailableException e) {
                // ignore, i.e. if a component is an auto-configured spring-boot
                // data-formats
            }

            if (clazz == null) {
                return null;
            }

            String packageName = clazz.getPackage().getName();
            packageName = packageName.replace('.', '/');
            String path = packageName + "/" + dataFormatName + ".json";

            ClassResolver resolver = getClassResolver();
            InputStream inputStream = resolver.loadResourceAsStream(path);
            log.debug("Loading dataformat JSON Schema for: {} using class resolver: {} -> {}", dataFormatName, resolver, inputStream);
            if (inputStream != null) {
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
            return null;

        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public String getLanguageParameterJsonSchema(String languageName) throws IOException {
        // use the language factory finder to find the package name of the language class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = getFactoryFinder(DefaultLanguageResolver.LANGUAGE_RESOURCE_PATH);
        try {
            Class<?> clazz = null;
            try {
                clazz = finder.findClass(languageName);
            } catch (NoFactoryAvailableException e) {
                // ignore, i.e. if a component is an auto-configured spring-boot
                // languages
            }

            if (clazz == null) {
                return null;
            }

            String packageName = clazz.getPackage().getName();
            packageName = packageName.replace('.', '/');
            String path = packageName + "/" + languageName + ".json";

            ClassResolver resolver = getClassResolver();
            InputStream inputStream = resolver.loadResourceAsStream(path);
            log.debug("Loading language JSON Schema for: {} using class resolver: {} -> {}", languageName, resolver, inputStream);
            if (inputStream != null) {
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
            return null;

        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public String getEipParameterJsonSchema(String eipName) throws IOException {
        // the eip json schema may be in some of the sub-packages so look until we find it
        String[] subPackages = new String[]{"", "/config", "/dataformat", "/language", "/loadbalancer", "/rest"};
        for (String sub : subPackages) {
            String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + "/" + eipName + ".json";
            ClassResolver resolver = getClassResolver();
            InputStream inputStream = resolver.loadResourceAsStream(path);
            if (inputStream != null) {
                log.debug("Loading eip JSON Schema for: {} using class resolver: {} -> {}", eipName, resolver, inputStream);
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
        }
        return null;
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

            // inject CamelContext if aware
            if (answer != null) {
                if (answer instanceof CamelContextAware) {
                    ((CamelContextAware) answer).setCamelContext(this);
                }
                if (answer instanceof Service) {
                    try {
                        startService((Service) answer);
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                }

                languages.put(language, answer);
            }
        }

        return answer;
    }

    public String getPropertyPrefixToken() {
        PropertiesComponent pc = getPropertiesComponent(false);

        if (pc != null) {
            return pc.getPrefixToken();
        } else {
            return null;
        }
    }

    public String getPropertySuffixToken() {
        PropertiesComponent pc = getPropertiesComponent(false);

        if (pc != null) {
            return pc.getSuffixToken();
        } else {
            return null;
        }
    }

    public String resolvePropertyPlaceholders(String text) throws Exception {
        // While it is more efficient to only do the lookup if we are sure we need the component,
        // with custom tokens, we cannot know if the URI contains a property or not without having
        // the component.  We also lose fail-fast behavior for the missing component with this change.
        PropertiesComponent pc = getPropertiesComponent(false);

        // Do not parse uris that are designated for the properties component as it will handle that itself
        if (text != null && !text.startsWith("properties:")) {
            // No component, assume default tokens.
            if (pc == null && text.contains(PropertiesComponent.DEFAULT_PREFIX_TOKEN)) {
                // lookup existing properties component, or force create a new default component
                pc = getPropertiesComponent(true);
            }

            if (pc != null && text.contains(pc.getPrefixToken())) {
                // the parser will throw exception if property key was not found
                String answer = pc.parseUri(text);
                log.debug("Resolved text: {} -> {}", text, answer);
                return answer;
            }
        }

        // return original text as is
        return text;
    }

    // Properties
    // -----------------------------------------------------------------------

    public TypeConverter getTypeConverter() {
        if (typeConverter == null) {
            synchronized (lock) {
                if (typeConverter == null) {
                    setTypeConverter(createTypeConverter());
                }
            }
        }
        return typeConverter;
    }

    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = doAddService(typeConverter);
    }

    public TypeConverterRegistry getTypeConverterRegistry() {
        if (typeConverterRegistry == null) {
            synchronized (lock) {
                if (typeConverterRegistry == null) {
                    setTypeConverterRegistry(createTypeConverterRegistry());
                }
            }
        }
        return typeConverterRegistry;
    }

    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        this.typeConverterRegistry = doAddService(typeConverterRegistry);
        // some registries are also a type converter implementation
        if (typeConverterRegistry instanceof TypeConverter) {
            this.typeConverter = (TypeConverter) typeConverterRegistry;
        }
    }

    public Injector getInjector() {
        if (injector == null) {
            synchronized (lock) {
                if (injector == null) {
                    setInjector(createInjector());
                }
            }
        }
        return injector;
    }

    public void setInjector(Injector injector) {
        this.injector = doAddService(injector);
    }

    public CamelBeanPostProcessor getBeanPostProcessor() {
        if (beanPostProcessor == null) {
            synchronized (lock) {
                if (beanPostProcessor == null) {
                    setBeanPostProcessor(createBeanPostProcessor());
                }
            }
        }
        return beanPostProcessor;
    }

    public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {
        this.beanPostProcessor = doAddService(beanPostProcessor);
    }

    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        return managementMBeanAssembler;
    }

    public void setManagementMBeanAssembler(ManagementMBeanAssembler managementMBeanAssembler) {
        this.managementMBeanAssembler = doAddService(managementMBeanAssembler, false);
    }

    public ComponentResolver getComponentResolver() {
        if (componentResolver == null) {
            synchronized (lock) {
                if (componentResolver == null) {
                    setComponentResolver(createComponentResolver());
                }
            }
        }
        return componentResolver;
    }

    public void setComponentResolver(ComponentResolver componentResolver) {
        this.componentResolver = doAddService(componentResolver);
    }

    public LanguageResolver getLanguageResolver() {
        if (languageResolver == null) {
            synchronized (lock) {
                if (languageResolver == null) {
                    setLanguageResolver(createLanguageResolver());
                }
            }
        }
        return languageResolver;
    }

    public void setLanguageResolver(LanguageResolver languageResolver) {
        this.languageResolver = doAddService(languageResolver);
    }

    public boolean isAutoCreateComponents() {
        return autoCreateComponents;
    }

    public void setAutoCreateComponents(boolean autoCreateComponents) {
        this.autoCreateComponents = autoCreateComponents;
    }

    public Registry getRegistry() {
        if (registry == null) {
            synchronized (lock) {
                if (registry == null) {
                    setRegistry(createRegistry());
                }
            }
        }
        return registry;
    }

    public <T> T getRegistry(Class<T> type) {
        Registry reg = getRegistry();

        if (type.isAssignableFrom(reg.getClass())) {
            return type.cast(reg);
        }
        return null;
    }

    public void setRegistry(Registry registry) {
        if (registry instanceof CamelContextAware) {
            ((CamelContextAware) registry).setCamelContext(this);
        }
        this.registry = registry;
    }

    public List<LifecycleStrategy> getLifecycleStrategies() {
        return lifecycleStrategies;
    }

    public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        getLifecycleStrategies().add(lifecycleStrategy);
    }

    public void setupRoutes(boolean done) {
        if (done) {
            isSetupRoutes.remove();
        } else {
            isSetupRoutes.set(true);
        }
    }

    public synchronized List<RouteDefinition> getRouteDefinitions() {
        return routeDefinitions;
    }

    public synchronized RouteDefinition getRouteDefinition(String id) {
        for (RouteDefinition route : routeDefinitions) {
            if (route.idOrCreate(getNodeIdFactory()).equals(id)) {
                return route;
            }
        }
        return null;
    }

    public synchronized List<RestDefinition> getRestDefinitions() {
        return restDefinitions;
    }

    public void addRestDefinitions(InputStream is, boolean addToRoutes) throws Exception {
        RestsDefinition rests = ModelHelper.loadRestsDefinition(this, is);
        if (rests != null) {
            addRestDefinitions(rests.getRests(), addToRoutes);
        }
    }

    public synchronized void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception {
        if (restDefinitions == null || restDefinitions.isEmpty()) {
            return;
        }

        this.restDefinitions.addAll(restDefinitions);
        if (addToRoutes) {
            // rests are also routes so need to add them there too
            for (final RestDefinition restDefinition : restDefinitions) {
                List<RouteDefinition> routeDefinitions = restDefinition.asRouteDefinition(this);
                addRouteDefinitions(routeDefinitions);
            }
        }
    }

    public RestConfiguration getRestConfiguration() {
        return restConfigurations.get("");
    }

    public void setRestConfiguration(RestConfiguration restConfiguration) {
        restConfigurations.put("", restConfiguration);
    }

    public Collection<RestConfiguration> getRestConfigurations() {
        return restConfigurations.values();
    }

    public void addRestConfiguration(RestConfiguration restConfiguration) {
        restConfigurations.put(restConfiguration.getComponent(), restConfiguration);
    }

    public RestConfiguration getRestConfiguration(String component, boolean defaultIfNotExist) {
        if (component == null) {
            component = "";
        }
        RestConfiguration config = restConfigurations.get(component);
        if (config == null && defaultIfNotExist) {
            // grab the default configuration
            config = getRestConfiguration();
            if (config == null || (config.getComponent() != null && !config.getComponent().equals(component))) {
                config = new RestConfiguration();
                restConfigurations.put(component, config);
            }
        }
        return config;
    }

    @Override
    public ServiceCallConfigurationDefinition getServiceCallConfiguration(String serviceName) {
        if (serviceName == null) {
            serviceName = "";
        }

        return serviceCallConfigurations.get(serviceName);
    }

    @Override
    public void setServiceCallConfiguration(ServiceCallConfigurationDefinition configuration) {
        serviceCallConfigurations.put("", configuration);
    }

    @Override
    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> configurations) {
        if (configurations != null) {
            for (ServiceCallConfigurationDefinition configuration : configurations) {
                serviceCallConfigurations.put(configuration.getId(), configuration);
            }
        }
    }

    @Override
    public void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration) {
        serviceCallConfigurations.put(serviceName, configuration);
    }

    @Override
    public HystrixConfigurationDefinition getHystrixConfiguration(String id) {
        if (id == null) {
            id = "";
        }

        return hystrixConfigurations.get(id);
    }

    @Override
    public void setHystrixConfiguration(HystrixConfigurationDefinition configuration) {
        hystrixConfigurations.put("", configuration);
    }

    @Override
    public void setHystrixConfigurations(List<HystrixConfigurationDefinition> configurations) {
        if (configurations != null) {
            for (HystrixConfigurationDefinition configuration : configurations) {
                hystrixConfigurations.put(configuration.getId(), configuration);
            }
        }
    }

    @Override
    public void addHystrixConfiguration(String id, HystrixConfigurationDefinition configuration) {
        hystrixConfigurations.put(id, configuration);
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

        if (interceptStrategy instanceof HandleFault) {
            setHandleFault(true);
        }
    }

    public List<RoutePolicyFactory> getRoutePolicyFactories() {
        return routePolicyFactories;
    }

    public void setRoutePolicyFactories(List<RoutePolicyFactory> routePolicyFactories) {
        this.routePolicyFactories = routePolicyFactories;
    }

    public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {
        getRoutePolicyFactories().add(routePolicyFactory);
    }

    public Set<LogListener> getLogListeners() {
        return logListeners;
    }

    public void addLogListener(LogListener listener) {
        logListeners.add(listener);
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

    public Boolean isMessageHistory() {
        return messageHistory;
    }

    public void setMessageHistory(Boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    public Boolean isLogMask() {
        return logMask != null && logMask;
    }

    public Boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
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
        return createProducerTemplate(0);
    }

    public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
        DefaultProducerTemplate answer = new DefaultProducerTemplate(this);
        answer.setMaximumCacheSize(maximumCacheSize);
        // start it so its ready to use
        try {
            startService(answer);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    public FluentProducerTemplate createFluentProducerTemplate() {
        return createFluentProducerTemplate(0);
    }

    public FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize) {
        DefaultFluentProducerTemplate answer = new DefaultFluentProducerTemplate(this);
        answer.setMaximumCacheSize(maximumCacheSize);
        // start it so its ready to use
        try {
            startService(answer);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    public ConsumerTemplate createConsumerTemplate() {
        return createConsumerTemplate(0);
    }

    public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
        DefaultConsumerTemplate answer = new DefaultConsumerTemplate(this);
        answer.setMaximumCacheSize(maximumCacheSize);
        // start it so its ready to use
        try {
            startService(answer);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    @Override
    public AsyncProcessor createMulticast(Collection<Processor> processors, ExecutorService executor, boolean shutdownExecutorService) {
        return new MulticastProcessor(this, processors, null, true, executor,
                shutdownExecutorService, false, false, 0,
                null, false, false);
    }

    public ErrorHandlerBuilder getErrorHandlerFactory() {
        return (ErrorHandlerBuilder)errorHandlerBuilder;
    }

    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerBuilder = errorHandlerFactory;
    }

    public ScheduledExecutorService getErrorHandlerExecutorService() {
        if (errorHandlerExecutorService == null) {
            synchronized (lock) {
                if (errorHandlerExecutorService == null) {
                    // setup default thread pool for error handler
                    errorHandlerExecutorService = createErrorHandlerExecutorService();
                }
            }
        }
        return errorHandlerExecutorService;
    }

    protected ScheduledExecutorService createErrorHandlerExecutorService() {
        return getExecutorServiceManager().newDefaultScheduledThreadPool("ErrorHandlerRedeliveryThreadPool", "ErrorHandlerRedeliveryTask");
    }

    public void setErrorHandlerExecutorService(ScheduledExecutorService errorHandlerExecutorService) {
        this.errorHandlerExecutorService = errorHandlerExecutorService;
    }

    public void setProducerServicePool(ServicePool<Producer> producerServicePool) {
        this.producerServicePool = doAddService(producerServicePool);
    }

    public ServicePool<Producer> getProducerServicePool() {
        if (producerServicePool == null) {
            synchronized (lock) {
                if (producerServicePool == null) {
                    setProducerServicePool(createProducerServicePool());
                }
            }
        }
        return producerServicePool;
    }

    public ServicePool<PollingConsumer> getPollingConsumerServicePool() {
        if (pollingConsumerServicePool == null) {
            synchronized (lock) {
                if (pollingConsumerServicePool == null) {
                    setPollingConsumerServicePool(createPollingConsumerServicePool());
                }
            }
        }
        return pollingConsumerServicePool;
    }

    public void setPollingConsumerServicePool(ServicePool<PollingConsumer> pollingConsumerServicePool) {
        this.pollingConsumerServicePool = doAddService(pollingConsumerServicePool);
    }

    public UnitOfWorkFactory getUnitOfWorkFactory() {
        if (unitOfWorkFactory == null) {
            synchronized (lock) {
                if (unitOfWorkFactory == null) {
                    setUnitOfWorkFactory(createUnitOfWorkFactory());
                }
            }
        }
        return unitOfWorkFactory;
    }

    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        this.unitOfWorkFactory = doAddService(unitOfWorkFactory);
    }

    public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
        return runtimeEndpointRegistry;
    }

    public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {
        this.runtimeEndpointRegistry = doAddService(runtimeEndpointRegistry);
    }

    public String getUptime() {
        long delta = getUptimeMillis();
        if (delta == 0) {
            return "";
        }
        return TimeUtils.printDuration(delta);
    }

    public long getUptimeMillis() {
        if (startDate == null) {
            return 0;
        }
        return new Date().getTime() - startDate.getTime();
    }

    public String getVersion() {
        if (version == null) {
            synchronized (lock) {
                if (version == null) {
                    version = doGetVersion();
                }
            }
        }
        return version;
    }

    private String doGetVersion() {
        String version = null;

        InputStream is = null;
        // try to load from maven properties first
        try {
            Properties p = new Properties();
            is = getClass().getResourceAsStream("/META-INF/maven/org.apache.camel/camel-core/pom.properties");
            if (is != null) {
                p.load(is);
                version = p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (is != null) {
                IOHelper.close(is);
            }
        }

        // fallback to using Java API
        if (version == null) {
            Package aPackage = getClass().getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }

        if (version == null) {
            // we could not compute the version so use a blank
            version = "";
        }

        return version;
    }

    @Override
    protected void doSuspend() throws Exception {
        EventHelper.notifyCamelContextSuspending(this);

        log.info("Apache Camel {} (CamelContext: {}) is suspending", getVersion(), getName());
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
        List<RouteStartupOrder> orders = new ArrayList<>();
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

        watch.taken();
        if (log.isInfoEnabled()) {
            log.info("Apache Camel {} (CamelContext: {}) is suspended in {}", getVersion(), getName(), TimeUtils.printDuration(watch.taken()));
        }

        EventHelper.notifyCamelContextSuspended(this);
    }

    @Override
    protected void doResume() throws Exception {
        try {
            EventHelper.notifyCamelContextResuming(this);

            log.info("Apache Camel {} (CamelContext: {}) is resuming", getVersion(), getName());
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

            if (log.isInfoEnabled()) {
                log.info("Resumed {} routes", suspendedRouteServices.size());
                log.info("Apache Camel {} (CamelContext: {}) resumed in {}", getVersion(), getName(), TimeUtils.printDuration(watch.taken()));
            }

            // and clear the list as they have been resumed
            suspendedRouteServices.clear();

            EventHelper.notifyCamelContextResumed(this);
        } catch (Exception e) {
            EventHelper.notifyCamelContextResumeFailed(this, e);
            throw e;
        }
    }

    @Override
    public void start() throws Exception {
        try (MDCHelper mdcHelper = new MDCHelper()) {
            init();
            vetoStated.set(false);
            startDate = new Date();
            stopWatch.restart();
            log.info("Apache Camel {} (CamelContext: {}) is starting", getVersion(), getName());

            // Start the route controller
            ServiceHelper.startService(this.routeController);

            doNotStartRoutesOnFirstStart = !firstStartDone && !isAutoStartup();

            // if the context was configured with auto startup = false, and we are already started,
            // then we may need to start the routes on the 2nd start call
            if (firstStartDone && !isAutoStartup() && isStarted()) {
                // invoke this logic to warm up the routes and if possible also start the routes
                doStartOrResumeRoutes(routeServices, true, true, false, true);
            }

            // super will invoke doStart which will prepare internal services and start routes etc.
            try {
                firstStartDone = true;
                super.start();
            } catch (VetoCamelContextStartException e) {
                // mark we veto against starting Camel
                vetoStated.set(true);
                if (e.isRethrowException()) {
                    throw e;
                } else {
                    log.info("CamelContext ({}) vetoed to not start due {}", getName(), e.getMessage());
                    // swallow exception and change state of this camel context to stopped
                    stop();
                    return;
                }
            }

            if (log.isInfoEnabled()) {
                // count how many routes are actually started
                int started = 0;
                for (Route route : getRoutes()) {
                    ServiceStatus status = getRouteStatus(route.getId());
                    if (status != null && status.isStarted()) {
                        started++;
                    }
                }

                final Collection<Route> controlledRoutes = getRouteController().getControlledRoutes();
                if (controlledRoutes.isEmpty()) {
                    log.info("Total {} routes, of which {} are started",
                        getRoutes().size(),
                        started);
                } else {
                    log.info("Total {} routes, of which {} are started, and {} are managed by RouteController: {}",
                        getRoutes().size(),
                        started,
                        controlledRoutes.size(),
                        getRouteController().getClass().getName()
                    );
                }
                log.info("Apache Camel {} (CamelContext: {}) started in {}", getVersion(), getName(), TimeUtils.printDuration(stopWatch.taken()));
            }

            // okay the routes has been started so emit event that CamelContext has started (here at the end)
            EventHelper.notifyCamelContextStarted(this);

            // now call the startup listeners where the routes has been started
            for (StartupListener startup : startupListeners) {
                if (startup instanceof ExtendedStartupListener) {
                    ((ExtendedStartupListener) startup).onCamelContextFullyStarted(this, isStarted());
                }
            }
        }
    }

    @Override
    public void stop() throws Exception {
        try (MDCHelper mdcHelper = new MDCHelper()) {
            super.stop();
        }
    }

    @Override
    public void suspend() throws Exception {
        try (MDCHelper mdcHelper = new MDCHelper()) {
            super.suspend();
        }
    }

    @Override
    public void resume() throws Exception {
        try (MDCHelper mdcHelper = new MDCHelper()) {
            super.resume();
        }
    }

    @Override
    public void shutdown() throws Exception {
        try (MDCHelper mdcHelper = new MDCHelper()) {
            super.shutdown();
        }
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected synchronized void doStart() throws Exception {
        doWithDefinedClassLoader(() -> {
            try {
                doStartCamel();
            } catch (Exception e) {
                // fire event that we failed to start
                EventHelper.notifyCamelContextStartupFailed(AbstractCamelContext.this, e);
                // rethrow cause
                throw e;
            }
        });
    }

    private <T extends Throwable> void doWithDefinedClassLoader(ThrowingRunnable<T> callable) throws T {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            // Using the ApplicationClassLoader as the default for TCCL
            if (applicationContextClassLoader != null) {
                Thread.currentThread().setContextClassLoader(applicationContextClassLoader);
            }
            callable.run();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    protected void doStartCamel() throws Exception {

        // custom properties may use property placeholders so resolve those early on
        if (globalOptions != null && !globalOptions.isEmpty()) {
            for (Map.Entry<String, String> entry : globalOptions.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null) {
                    String replaced = resolvePropertyPlaceholders(value);
                    if (!value.equals(replaced)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Camel property with key {} replaced value from {} -> {}", key, value, replaced);
                        }
                        entry.setValue(replaced);
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Using ClassResolver={}, PackageScanClassResolver={}, ApplicationContextClassLoader={}, RouteController={}",
                getClassResolver(), getPackageScanClassResolver(), getApplicationContextClassLoader(), getRouteController());
        }

        if (isStreamCaching()) {
            log.info("StreamCaching is enabled on CamelContext: {}", getName());
        }

        if (isTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            log.info("Tracing is enabled on CamelContext: {}", getName());
        }

        if (isUseMDCLogging()) {
            // log if MDC has been enabled
            log.info("MDC logging is enabled on CamelContext: {}", getName());
        }

        if (isHandleFault()) {
            // only add a new handle fault if not already configured
            if (HandleFault.getHandleFault(this) == null) {
                log.info("HandleFault is enabled on CamelContext: {}", getName());
                addInterceptStrategy(new HandleFault());
            }
        }

        if (getDelayer() != null && getDelayer() > 0) {
            log.info("Delayer is enabled with: {} ms. on CamelContext: {}", getDelayer(), getName());
        }

        // register debugger
        if (getDebugger() != null) {
            log.info("Debugger: {} is enabled on CamelContext: {}", getDebugger(), getName());
            addInterceptStrategy(new Debug(getDebugger()));
        }

        // start management strategy before lifecycles are started
        ManagementStrategy managementStrategy = getManagementStrategy();
        startService(managementStrategy);

        // start lifecycle strategies
        ServiceHelper.startService(lifecycleStrategies);
        Iterator<LifecycleStrategy> it = lifecycleStrategies.iterator();
        while (it.hasNext()) {
            LifecycleStrategy strategy = it.next();
            try {
                strategy.onContextStart(this);
            } catch (VetoCamelContextStartException e) {
                // okay we should not start Camel since it was vetoed
                log.warn("Lifecycle strategy vetoed starting CamelContext ({}) due: {}", getName(), e.getMessage());
                throw e;
            } catch (Exception e) {
                log.warn("Lifecycle strategy " + strategy + " failed starting CamelContext ({}) due: {}", getName(), e.getMessage());
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
                startService((Service) notifier);
            }
        }

        // must let some bootstrap service be started before we can notify the starting event
        EventHelper.notifyCamelContextStarting(this);

        forceLazyInitialization();

        if (reloadStrategy != null) {
            log.info("Using ReloadStrategy: {}", reloadStrategy);
            addService(reloadStrategy, true, true);
        }

        // Start runtime catalog
        getExtension(RuntimeCamelCatalog.class);

        // re-create endpoint registry as the cache size limit may be set after the constructor of this instance was called.
        // and we needed to create endpoints up-front as it may be accessed before this context is started
        endpoints = doAddService(createEndpointRegistry(endpoints));
        // Initialize declarative transformer registry
        transformerRegistry = doAddService(createTransformerRegistry(transformers));
        // Initialize declarative validator registry
        validatorRegistry = doAddService(createValidatorRegistry(validators));

        // optimised to not include runtimeEndpointRegistry unlesstartServices its enabled or JMX statistics is in extended mode
        if (runtimeEndpointRegistry == null && getManagementStrategy() != null && getManagementStrategy().getManagementAgent() != null) {
            Boolean isEnabled = getManagementStrategy().getManagementAgent().getEndpointRuntimeStatisticsEnabled();
            boolean isExtended = getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
            // extended mode is either if we use Extended statistics level or the option is explicit enabled
            boolean extended = isExtended || isEnabled != null && isEnabled;
            if (extended) {
                runtimeEndpointRegistry = new DefaultRuntimeEndpointRegistry();
            }
        }
        if (runtimeEndpointRegistry != null) {
            if (runtimeEndpointRegistry instanceof EventNotifier) {
                getManagementStrategy().addEventNotifier((EventNotifier) runtimeEndpointRegistry);
            }
            addService(runtimeEndpointRegistry, true, true);
        }

        // eager lookup any configured properties component to avoid subsequent lookup attempts which may impact performance
        // due we use properties component for property placeholder resolution at runtime
        PropertiesComponent existing = getPropertiesComponent(false);
        if (existing != null) {
            // store reference to the existing properties component
            propertiesComponent = existing;
        }

        // start components
        startServices(components.values());

        // start the route definitions before the routes is started
        startRouteDefinitions(routeDefinitions);

        if (isUseDataType()) {
            // log if DataType has been enabled
            log.info("Message DataType is enabled on CamelContext: {}", getName());
        }

        // is there any stream caching enabled then log an info about this and its limit of spooling to disk, so people is aware of this
        boolean streamCachingInUse = isStreamCaching();
        if (!streamCachingInUse) {
            for (RouteDefinition route : routeDefinitions) {
                Boolean routeCache = CamelContextHelper.parseBoolean(this, route.getStreamCache());
                if (routeCache != null && routeCache) {
                    streamCachingInUse = true;
                    break;
                }
            }
        }
        if (streamCachingInUse) {
            // stream caching is in use so enable the strategy
            getStreamCachingStrategy().setEnabled(true);
        } else {
            // log if stream caching is not in use as this can help people to enable it if they use streams
            log.info("StreamCaching is not in use. If using streams then its recommended to enable stream caching."
                    + " See more details at http://camel.apache.org/stream-caching.html");
        }

        if (isAllowUseOriginalMessage()) {
            log.debug("AllowUseOriginalMessage enabled because UseOriginalMessage is in use");
        }

        log.debug("Using HeadersMapFactory: {}", getHeadersMapFactory());
        if (!getHeadersMapFactory().isCaseInsensitive()) {
            log.info("HeadersMapFactory: {} is case-sensitive which can cause problems for protocols such as HTTP based, which rely on case-insensitive headers.", getHeadersMapFactory());
        }

        // start routes
        if (doNotStartRoutesOnFirstStart) {
            log.debug("Skip starting routes as CamelContext has been configured with autoStartup=false");
        }

        // invoke this logic to warmup the routes and if possible also start the routes
        doStartOrResumeRoutes(routeServices, true, !doNotStartRoutesOnFirstStart, false, true);

        // starting will continue in the start method
    }

    protected synchronized void doStop() throws Exception {
        stopWatch.restart();
        log.info("Apache Camel {} (CamelContext: {}) is shutting down", getVersion(), getName());
        EventHelper.notifyCamelContextStopping(this);

        // Stop the route controller
        ServiceHelper.stopAndShutdownService(this.routeController);

        // stop route inputs in the same order as they was started so we stop the very first inputs first
        try {
            // force shutting down routes as they may otherwise cause shutdown to hang
            shutdownStrategy.shutdownForced(this, getRouteStartupOrder());
        } catch (Throwable e) {
            log.warn("Error occurred while shutting down routes. This exception will be ignored.", e);
        }

        // shutdown await manager to trigger interrupt of blocked threads to attempt to free these threads graceful
        shutdownServices(asyncProcessorAwaitManager);

        routeStartupOrder.sort(Comparator.comparingInt(RouteStartupOrder::getStartupOrder).reversed());
        List<RouteService> list = new ArrayList<>();
        for (RouteStartupOrder startupOrder : routeStartupOrder) {
            DefaultRouteStartupOrder order = (DefaultRouteStartupOrder) startupOrder;
            RouteService routeService = order.getRouteService();
            list.add(routeService);
        }
        shutdownServices(list, false);
        // do not clear route services or startup listeners as we can start Camel again and get the route back as before
        routeStartupOrder.clear();

        // but clear any suspend routes
        suspendedRouteServices.clear();

        // stop consumers from the services to close first, such as POJO consumer (eg @Consumer)
        // which we need to stop after the routes, as a POJO consumer is essentially a route also
        for (Service service : servicesToStop) {
            if (service instanceof Consumer) {
                shutdownServices(service);
            }
        }

        // the stop order is important

        // shutdown default error handler thread pool
        if (errorHandlerExecutorService != null) {
            // force shutting down the thread pool
            getExecutorServiceManager().shutdownNow(errorHandlerExecutorService);
            errorHandlerExecutorService = null;
        }

        // shutdown debugger
        ServiceHelper.stopAndShutdownService(getDebugger());

        shutdownServices(endpoints.values());
        endpoints.clear();

        shutdownServices(components.values());
        components.clear();

        shutdownServices(languages.values());
        languages.clear();

        try {
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onContextStop(this);
            }
        } catch (Throwable e) {
            log.warn("Error occurred while stopping lifecycle strategies. This exception will be ignored.", e);
        }

        // shutdown services as late as possible
        shutdownServices(servicesToStop);
        servicesToStop.clear();

        // must notify that we are stopped before stopping the management strategy
        EventHelper.notifyCamelContextStopped(this);

        // stop the notifier service
        for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
            shutdownServices(notifier);
        }

        // shutdown executor service and management as the last one
        shutdownServices(executorServiceManager);
        shutdownServices(managementStrategy);
        shutdownServices(managementMBeanAssembler);
        shutdownServices(lifecycleStrategies);
        // do not clear lifecycleStrategies as we can start Camel again and get the route back as before

        // stop the lazy created so they can be re-created on restart
        forceStopLazyInitialization();

        // stop to clear introspection cache
        IntrospectionSupport.stop();

        if (log.isInfoEnabled()) {
            log.info("Apache Camel " + getVersion() + " (CamelContext: " + getName() + ") uptime {}", getUptime());
            log.info("Apache Camel {} (CamelContext: {}) is shutdown in {}", getVersion(), getName(), TimeUtils.printDuration(stopWatch.taken()));
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
        isStartingRoutes.set(true);
        try {
            // filter out already started routes
            Map<String, RouteService> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, RouteService> entry : routeServices.entrySet()) {
                boolean startable = false;

                Consumer consumer = entry.getValue().getRoutes().iterator().next().getConsumer();
                if (consumer instanceof SuspendableService) {
                    // consumer could be suspended, which is not reflected in the RouteService status
                    startable = ((SuspendableService) consumer).isSuspended();
                }

                if (!startable && consumer instanceof StatefulService) {
                    // consumer could be stopped, which is not reflected in the RouteService status
                    startable = ((StatefulService) consumer).getStatus().isStartable();
                } else if (!startable) {
                    // no consumer so use state from route service
                    startable = entry.getValue().getStatus().isStartable();
                }

                if (startable) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }

            // the context is in last phase of staring, so lets start the routes
            safelyStartRouteServices(checkClash, startConsumer, resumeConsumer, addingRoutes, filtered.values());

        } finally {
            isStartingRoutes.remove();
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
            if (service instanceof Service) {
                ServiceHelper.stopAndShutdownService(service);
            } else if (service instanceof Collection) {
                ServiceHelper.stopAndShutdownServices((Collection<?>)service);
            }
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
        Collection<?> list = services;
        if (reverse) {
            List<Object> reverseList = new ArrayList<>(services);
            Collections.reverse(reverseList);
            list = reverseList;
        }

        for (Object service : list) {
            shutdownServices(service);
        }
    }

    private void startService(Service service) throws Exception {
        // and register startup aware so they can be notified when
        // camel context has been started
        if (service instanceof StartupListener) {
            StartupListener listener = (StartupListener) service;
            addStartupListener(listener);
        }

        if (service instanceof CamelContextAware) {
            CamelContextAware aware = (CamelContextAware) service;
            aware.setCamelContext(this);
        }

        service.start();
    }

    private void startServices(Collection<?> services) throws Exception {
        for (Object element : services) {
            if (element instanceof Service) {
                startService((Service)element);
            }
        }
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
                    boolean autoStartup = routeService.getRouteDefinition().isAutoStartup(this) && this.isAutoStartup();
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

    protected synchronized void stopRouteService(RouteService routeService, boolean removingRoutes) throws Exception {
        routeService.setRemovingRoutes(removingRoutes);
        stopRouteService(routeService);
    }

    protected void logRouteState(Route route, String state) {
        if (log.isInfoEnabled()) {
            if (route.getConsumer() != null) {
                log.info("Route: {} is {}, was consuming from: {}", route.getId(), state, route.getConsumer().getEndpoint());
            } else {
                log.info("Route: {} is {}.", route.getId(), state);
            }
        }
    }

    protected synchronized void stopRouteService(RouteService routeService) throws Exception {
        routeService.stop();
        for (Route route : routeService.getRoutes()) {
            logRouteState(route, "stopped");
        }
    }

    protected synchronized void shutdownRouteService(RouteService routeService) throws Exception {
        routeService.shutdown();
        for (Route route : routeService.getRoutes()) {
            logRouteState(route, "shutdown and removed");
        }
    }

    protected synchronized void suspendRouteService(RouteService routeService) throws Exception {
        routeService.setRemovingRoutes(false);
        routeService.suspend();
        for (Route route : routeService.getRoutes()) {
            logRouteState(route, "suspended");
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
        Map<Integer, DefaultRouteStartupOrder> inputs = new TreeMap<>();

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

        // sort the startup listeners so they are started in the right order
        startupListeners.sort(OrderedComparator.get());
        // now call the startup listeners where the routes has been warmed up
        // (only the actual route consumer has not yet been started)
        for (StartupListener startup : startupListeners) {
            startup.onCamelContextStarted(this, isStarted());
        }
        // because the consumers may also register startup listeners we need to reset
        // the already started listeners
        List<StartupListener> backup = new ArrayList<>(startupListeners);
        startupListeners.clear();

        // now start the consumers
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

        // sort the startup listeners so they are started in the right order
        startupListeners.sort(OrderedComparator.get());
        // now the consumers that was just started may also add new StartupListeners (such as timer)
        // so we need to ensure they get started as well
        for (StartupListener startup : startupListeners) {
            startup.onCamelContextStarted(this, isStarted());
        }
        // and add the previous started startup listeners to the list so we have them all
        startupListeners.addAll(0, backup);

        // inputs no longer needed
        inputs.clear();
    }

    /**
     * @see #safelyStartRouteServices(boolean,boolean,boolean,boolean,Collection)
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
            log.debug("Warming up route id: {} having autoStartup={}", routeService.getId(), autoStartup);
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
        List<Endpoint> routeInputs = new ArrayList<>();

        for (Map.Entry<Integer, DefaultRouteStartupOrder> entry : inputs.entrySet()) {
            Integer order = entry.getKey();
            Route route = entry.getValue().getRoute();
            RouteService routeService = entry.getValue().getRouteService();

            // if we are starting camel, then skip routes which are configured to not be auto started
            boolean autoStartup = routeService.getRouteDefinition().isAutoStartup(this) && this.isAutoStartup();
            if (addingRoute && !autoStartup) {
                log.info("Skipping starting of route {} as its configured with autoStartup=false", routeService.getId());
                continue;
            }

            // start the service
            for (Consumer consumer : routeService.getInputs().values()) {
                Endpoint endpoint = consumer.getEndpoint();

                // check multiple consumer violation, with the other routes to be started
                if (!doCheckMultipleConsumerSupportClash(endpoint, routeInputs)) {
                    throw new FailedToStartRouteException(routeService.getId(),
                        "Multiple consumers for the same endpoint is not allowed: " + endpoint);
                }

                // check for multiple consumer violations with existing routes which
                // have already been started, or is currently starting
                List<Endpoint> existingEndpoints = new ArrayList<>();
                for (Route existingRoute : getRoutes()) {
                    if (route.getId().equals(existingRoute.getId())) {
                        // skip ourselves
                        continue;
                    }
                    Endpoint existing = existingRoute.getEndpoint();
                    ServiceStatus status = getRouteStatus(existingRoute.getId());
                    if (status != null && (status.isStarted() || status.isStarting())) {
                        existingEndpoints.add(existing);
                    }
                }
                if (!doCheckMultipleConsumerSupportClash(endpoint, existingEndpoints)) {
                    throw new FailedToStartRouteException(routeService.getId(),
                            "Multiple consumers for the same endpoint is not allowed: " + endpoint);
                }

                // start the consumer on the route
                log.debug("Route: {} >>> {}", route.getId(), route);
                if (resumeOnly) {
                    log.debug("Resuming consumer (order: {}) on route: {}", order, route.getId());
                } else {
                    log.debug("Starting consumer (order: {}) on route: {}", order, route.getId());
                }

                if (resumeOnly && route.supportsSuspension()) {
                    // if we are resuming and the route can be resumed
                    ServiceHelper.resumeService(consumer);
                    log.info("Route: {} resumed and consuming from: {}", route.getId(), endpoint);
                } else {
                    // when starting we should invoke the lifecycle strategies
                    for (LifecycleStrategy strategy : lifecycleStrategies) {
                        strategy.onServiceAdd(this, consumer, route);
                    }
                    try {
                        startService(consumer);
                        route.getProperties().remove("route.start.exception");
                    } catch (Exception e) {
                        route.getProperties().put("route.start.exception", e);
                        throw e;
                    }

                    log.info("Route: {} started and consuming from: {}", route.getId(), endpoint);
                }

                routeInputs.add(endpoint);

                // add to the order which they was started, so we know how to stop them in reverse order
                // but only add if we haven't already registered it before (we dont want to double add when restarting)
                boolean found = false;
                for (RouteStartupOrder other : routeStartupOrder) {
                    if (other.getRoute().getId().equals(route.getId())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    routeStartupOrder.add(entry.getValue());
                }
            }

            if (resumeOnly) {
                routeService.resume();
            } else {
                // and start the route service (no need to start children as they are already warmed up)
                try {
                    routeService.start();
                    route.getProperties().remove("route.start.exception");
                } catch (Exception e) {
                    route.getProperties().put("route.start.exception", e);
                    throw e;
                }
            }
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
     * Force some lazy initialization to occur upfront before we start any
     * components and create routes
     */
    protected void forceLazyInitialization() {
        if (initialization != Initialization.Lazy) {
            getVersion();
            getTypeConverter();
            getTypeConverterRegistry();
            getInjector();
            getRegistry();
            getLanguageResolver();
            getExecutorServiceManager();
            getInflightRepository();
            getAsyncProcessorAwaitManager();
            getShutdownStrategy();
            getPackageScanClassResolver();
            getProducerServicePool();
            getPollingConsumerServicePool();
            getRestRegistry();

            if (isTypeConverterStatisticsEnabled() != null) {
                getTypeConverterRegistry().getStatistics().setStatisticsEnabled(isTypeConverterStatisticsEnabled());
            }

            // resolve simple language to initialize it
            resolveLanguage("simple");

            // Start runtime catalog
            getExtension(RuntimeCamelCatalog.class);

            if (initialization == Initialization.Eager) {
                getExtension(HealthCheckRegistry.class);
                getComponentResolver();
                getDataFormatResolver();
                getManagementStrategy();
                getHeadersMapFactory();
                getClassResolver();
                getNodeIdFactory();
                getProcessorFactory();
                getMessageHistoryFactory();
                getFactoryFinderResolver();
                getDefaultFactoryFinder();
                getStreamCachingStrategy();
                getModelJAXBContextFactory();
                getUuidGenerator();
                getUnitOfWorkFactory();
                getRouteController();
            }
        }
    }

    /**
     * Force clear lazy initialization so they can be re-created on restart
     */
    protected void forceStopLazyInitialization() {
        injector = null;
        languageResolver = null;
        typeConverterRegistry = null;
        typeConverter = null;
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
        Object value = getRegistry().lookupByName(uri);
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

    /**
     * Gets the properties component in use, eventually creating it.
     */
    public PropertiesComponent getPropertiesComponent() {
        return getPropertiesComponent(true);
    }

    public PropertiesComponent getPropertiesComponent(boolean autoCreate) {
        if (propertiesComponent == null && autoCreate) {
            Object comp = ResolverHelper.lookupComponentInRegistryWithFallback(this, "properties");
            if (comp == null) {
                try {
                    Class<?> type = getFactoryFinder(DefaultComponentResolver.RESOURCE_PATH).findClass("properties");
                    if (type != null) {
                        log.info("No existing PropertiesComponent has been configured, creating a new default PropertiesComponent with name: properties");
                        comp = type.newInstance();
                        globalOptions.put(PropertiesComponent.DEFAULT_CREATED, "true");
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("No Component registered for scheme: " + "properties", e);
                }
            }
            if (comp instanceof PropertiesComponent) {
                addComponent("properties", (PropertiesComponent) comp);
            }
            if (propertiesComponent == null) {
                throw new IllegalStateException();
            }
        }
        return propertiesComponent;
    }

    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        this.dataFormats = dataFormats;
    }

    public Map<String, DataFormatDefinition> getDataFormats() {
        return dataFormats;
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        return globalOptions;
    }

    public void setGlobalOptions(Map<String, String> globalOptions) {
        this.globalOptions = globalOptions;
    }

    public FactoryFinder getDefaultFactoryFinder() {
        if (defaultFactoryFinder == null) {
            synchronized (lock) {
                if (defaultFactoryFinder == null) {
                    defaultFactoryFinder = getFactoryFinderResolver().resolveDefaultFactoryFinder(getClassResolver());
                }
            }
        }
        return defaultFactoryFinder;
    }

    public FactoryFinderResolver getFactoryFinderResolver() {
        if (factoryFinderResolver == null) {
            synchronized (lock) {
                if (factoryFinderResolver == null) {
                    factoryFinderResolver = createFactoryFinderResolver();
                }
            }
        }
        return factoryFinderResolver;
    }

    public void setFactoryFinderResolver(FactoryFinderResolver factoryFinderResolver) {
        this.factoryFinderResolver = doAddService(factoryFinderResolver);
    }

    public FactoryFinder getFactoryFinder(String path) throws NoFactoryAvailableException {
        return factories.computeIfAbsent(path, this::createFactoryFinder);
    }

    protected FactoryFinder createFactoryFinder(String path) {
        return getFactoryFinderResolver().resolveFactoryFinder(getClassResolver(), path);
    }

    public ClassResolver getClassResolver() {
        if (classResolver == null) {
            synchronized (lock) {
                if (classResolver == null) {
                    setClassResolver(createClassResolver());
                }
            }
        }
        return classResolver;
    }

    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = doAddService(classResolver);
    }

    public PackageScanClassResolver getPackageScanClassResolver() {
        if (packageScanClassResolver == null) {
            synchronized (lock) {
                if (packageScanClassResolver == null) {
                    setPackageScanClassResolver(createPackageScanClassResolver());
                }
            }
        }
        return packageScanClassResolver;
    }

    public void setPackageScanClassResolver(PackageScanClassResolver packageScanClassResolver) {
        this.packageScanClassResolver = doAddService(packageScanClassResolver);
    }

    public List<String> getComponentNames() {
        return new ArrayList<>(components.keySet());
    }

    public List<String> getLanguageNames() {
        return new ArrayList<>(languages.keySet());
    }

    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        if (modelJAXBContextFactory == null) {
            synchronized (lock) {
                if (modelJAXBContextFactory == null) {
                    setModelJAXBContextFactory(createModelJAXBContextFactory());
                }
            }
        }
        return modelJAXBContextFactory;
    }

    public void setModelJAXBContextFactory(final ModelJAXBContextFactory modelJAXBContextFactory) {
        this.modelJAXBContextFactory = doAddService(modelJAXBContextFactory);
    }

    public NodeIdFactory getNodeIdFactory() {
        if (nodeIdFactory == null) {
            synchronized (lock) {
                if (nodeIdFactory == null) {
                    setNodeIdFactory(createNodeIdFactory());
                }
            }
        }
        return nodeIdFactory;
    }

    public void setNodeIdFactory(NodeIdFactory idFactory) {
        this.nodeIdFactory = doAddService(idFactory);
    }

    public ManagementStrategy getManagementStrategy() {
        return managementStrategy;
    }

    public void setManagementStrategy(ManagementStrategy managementStrategy) {
        this.managementStrategy = managementStrategy;
    }

    public void disableJMX() {
        if (isNew()) {
            disableJMX = true;
        } else if (isInit()) {
            disableJMX = true;
            // we are still in initializing mode, so we can disable JMX, by setting up management again
            setupManagement(null);
        } else {
            throw new IllegalStateException("Disabling JMX can only be done when CamelContext has not been started");
        }
    }

    public boolean isJMXDisabled() {
        return disableJMX;
    }

    public void setupManagement(Map<String, Object> options) {
        ManagementStrategyFactory factory = new DefaultManagementStrategyFactory();
        if (!isJMXDisabled()) {
            try {
                FactoryFinder finder = getFactoryFinder("META-INF/services/org/apache/camel/management/");
                try {
                    if (finder != null) {
                        Object object = finder.newInstance("ManagementStrategyFactory");
                        if (object instanceof ManagementStrategyFactory) {
                            factory = (ManagementStrategyFactory) object;
                        }
                    }
                } catch (NoFactoryAvailableException e) {
                    // ignore there is no custom factory
                }
            } catch (Exception e) {
                log.warn("Cannot create JMX lifecycle strategy. Will fallback and disable JMX.", e);
            }
        }

        log.debug("Setting up management with factory: {}", factory);
        try {
            ManagementStrategy strategy = factory.create(this, options);
            LifecycleStrategy lifecycle = factory.createLifecycle(this);
            factory.setupManagement(this, strategy, lifecycle);
        } catch (Exception e) {
            log.warn("Error setting up management due " + e.getMessage());
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public InflightRepository getInflightRepository() {
        if (inflightRepository == null) {
            synchronized (lock) {
                if (inflightRepository == null) {
                    setInflightRepository(createInflightRepository());
                }
            }
        }
        return inflightRepository;
    }

    public void setInflightRepository(InflightRepository repository) {
        this.inflightRepository = doAddService(repository);
    }

    public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
        if (asyncProcessorAwaitManager == null) {
            synchronized (lock) {
                if (asyncProcessorAwaitManager == null) {
                    setAsyncProcessorAwaitManager(createAsyncProcessorAwaitManager());
                }
            }
        }
        return asyncProcessorAwaitManager;
    }

    public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager asyncProcessorAwaitManager) {
        this.asyncProcessorAwaitManager = doAddService(asyncProcessorAwaitManager);
    }

    public void setAutoStartup(Boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public Boolean isAutoStartup() {
        return autoStartup != null && autoStartup;
    }

    public Boolean isLoadTypeConverters() {
        return loadTypeConverters != null && loadTypeConverters;
    }

    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    public Boolean isTypeConverterStatisticsEnabled() {
        return typeConverterStatisticsEnabled != null && typeConverterStatisticsEnabled;
    }

    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        this.typeConverterStatisticsEnabled = typeConverterStatisticsEnabled;
    }

    public Boolean isUseMDCLogging() {
        return useMDCLogging != null && useMDCLogging;
    }

    public void setUseMDCLogging(Boolean useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    public Boolean isUseDataType() {
        return useDataType;
    }

    @Override
    public void setUseDataType(Boolean useDataType) {
        this.useDataType = useDataType;
    }

    public Boolean isUseBreadcrumb() {
        return useBreadcrumb != null && useBreadcrumb;
    }

    public void setUseBreadcrumb(Boolean useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    public ClassLoader getApplicationContextClassLoader() {
        return applicationContextClassLoader;
    }

    public void setApplicationContextClassLoader(ClassLoader classLoader) {
        applicationContextClassLoader = classLoader;
    }

    public DataFormatResolver getDataFormatResolver() {
        if (dataFormatResolver == null) {
            synchronized (lock) {
                if (dataFormatResolver == null) {
                    setDataFormatResolver(createDataFormatResolver());
                }
            }
        }
        return dataFormatResolver;
    }

    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        this.dataFormatResolver = doAddService(dataFormatResolver);
    }

    public DataFormat resolveDataFormat(String name) {
        DataFormat answer = getDataFormatResolver().resolveDataFormat(name, this);

        // inject CamelContext if aware
        if (answer instanceof CamelContextAware) {
            ((CamelContextAware) answer).setCamelContext(this);
        }

        return answer;
    }

    public DataFormat createDataFormat(String name) {
        DataFormat answer = getDataFormatResolver().createDataFormat(name, this);

        // inject CamelContext if aware
        if (answer instanceof CamelContextAware) {
            ((CamelContextAware) answer).setCamelContext(this);
        }

        return answer;
    }

    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        // lookup type and create the data format from it
        DataFormatDefinition type = lookup(this, name, DataFormatDefinition.class);
        if (type == null && getDataFormats() != null) {
            type = getDataFormats().get(name);
        }
        return type;
    }

    private static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookupByNameAndType(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
    }

    public ShutdownStrategy getShutdownStrategy() {
        if (shutdownStrategy == null) {
            synchronized (lock) {
                if (shutdownStrategy == null) {
                    setShutdownStrategy(createShutdownStrategy());
                }
            }
        }
        return shutdownStrategy;
    }

    public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        this.shutdownStrategy = doAddService(shutdownStrategy);
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

    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    public Boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage != null && allowUseOriginalMessage;
    }

    public ExecutorServiceManager getExecutorServiceManager() {
        if (executorServiceManager == null) {
            synchronized (lock) {
                if (executorServiceManager == null) {
                    setExecutorServiceManager(createExecutorServiceManager());
                }
            }
        }
        return this.executorServiceManager;
    }

    public void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {
        // special for executorServiceManager as want to stop it manually so false in stopOnShutdown
        this.executorServiceManager = doAddService(executorServiceManager, false);
    }

    public ProcessorFactory getProcessorFactory() {
        if (processorFactory == null) {
            synchronized (lock) {
                if (processorFactory == null) {
                    setProcessorFactory(createProcessorFactory());
                }
            }
        }
        return processorFactory;
    }

    public void setProcessorFactory(ProcessorFactory processorFactory) {
        this.processorFactory = doAddService(processorFactory);
    }

    public MessageHistoryFactory getMessageHistoryFactory() {
        if (messageHistoryFactory == null) {
            synchronized (lock) {
                if (messageHistoryFactory == null) {
                    setMessageHistoryFactory(createMessageHistoryFactory());
                }
            }
        }
        return messageHistoryFactory;
    }

    public void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {
        this.messageHistoryFactory = doAddService(messageHistoryFactory);
    }

    public Debugger getDebugger() {
        return debugger;
    }

    public void setDebugger(Debugger debugger) {
        if (isStartingOrStarted()) {
            throw new IllegalStateException("Can not set debugger on a started CamelContext");
        }
        this.debugger = doAddService(debugger);
    }

    public UuidGenerator getUuidGenerator() {
        if (uuidGenerator == null) {
            synchronized (lock) {
                if (uuidGenerator == null) {
                    setUuidGenerator(createUuidGenerator());
                }
            }
        }
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = doAddService(uuidGenerator);
    }

    public StreamCachingStrategy getStreamCachingStrategy() {
        if (streamCachingStrategy == null) {
            synchronized (lock) {
                if (streamCachingStrategy == null) {
                    setStreamCachingStrategy(createStreamCachingStrategy());
                }
            }
        }
        return streamCachingStrategy;
    }

    public void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {
        this.streamCachingStrategy = doAddService(streamCachingStrategy, true, false);
    }

    public RestRegistry getRestRegistry() {
        if (restRegistry ==  null) {
            synchronized (lock) {
                if (restRegistry == null) {
                    setRestRegistry(createRestRegistry());
                }
            }
        }
        return restRegistry;
    }

    public void setRestRegistry(RestRegistry restRegistry) {
        this.restRegistry = doAddService(restRegistry);
    }

    @Override
    public String getGlobalOption(String key) {
        String value = getGlobalOptions().get(key);
        if (ObjectHelper.isNotEmpty(value)) {
            try {
                value = resolvePropertyPlaceholders(value);
            } catch (Exception e) {
                throw new RuntimeCamelException("Error getting global option: " + key, e);
            }
        }
        return value;
    }

    @Override
    public ReloadStrategy getReloadStrategy() {
        return reloadStrategy;
    }

    @Override
    public void setReloadStrategy(ReloadStrategy reloadStrategy) {
        this.reloadStrategy = doAddService(reloadStrategy);
    }

    @Override
    public void setTransformers(List<TransformerDefinition> transformers) {
        this.transformers = transformers;
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return transformers;
    }

    @Override
    public Transformer resolveTransformer(String scheme) {
        return transformerRegistry.resolveTransformer(new TransformerKey(scheme));
    }

    @Override
    public Transformer resolveTransformer(DataType from, DataType to) {
        return transformerRegistry.resolveTransformer(new TransformerKey(from, to));
    }

    @Override
    public TransformerRegistry<TransformerKey> getTransformerRegistry() {
        return transformerRegistry;
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        this.validators = validators;
    }

    @Override
    public List<ValidatorDefinition> getValidators() {
        return validators;
    }

    @Override
    public Validator resolveValidator(DataType type) {
        return validatorRegistry.resolveValidator(new ValidatorKey(type));
    }

    @Override
    public ValidatorRegistry<ValidatorKey> getValidatorRegistry() {
        return validatorRegistry;
    }

    @Override
    public void setSSLContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public SSLContextParameters getSSLContextParameters() {
        return this.sslContextParameters;
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        if (headersMapFactory == null) {
            synchronized (lock) {
                if (headersMapFactory == null) {
                    setHeadersMapFactory(createHeadersMapFactory());
                }
            }
        }
        return headersMapFactory;
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory headersMapFactory) {
        this.headersMapFactory = doAddService(headersMapFactory);
    }

    protected Map<String, RouteService> getRouteServices() {
        return routeServices;
    }

    /**
     * Reset context counter to a preset value. Mostly used for tests to ensure a predictable getName()
     *
     * @param value new value for the context counter
     */
    public static void setContextCounter(int value) {
        DefaultCamelContextNameStrategy.setCounter(value);
        DefaultManagementNameStrategy.setCounter(value);
    }

    @Override
    public String toString() {
        return "CamelContext(" + getName() + ")";
    }

    class MDCHelper implements AutoCloseable {
        final Map<String, String> originalContextMap;

        MDCHelper() {
            if (isUseMDCLogging()) {
                originalContextMap = MDC.getCopyOfContextMap();
                MDC.put(MDC_CAMEL_CONTEXT_ID, getName());
            } else {
                originalContextMap = null;
            }
        }

        @Override
        public void close() {
            if (isUseMDCLogging()) {
                if (originalContextMap != null) {
                    MDC.setContextMap(originalContextMap);
                } else {
                    MDC.clear();
                }
            }
        }
    }

    protected abstract StreamCachingStrategy createStreamCachingStrategy();

    protected abstract TypeConverter createTypeConverter();

    protected abstract TypeConverterRegistry createTypeConverterRegistry();

    protected abstract Injector createInjector();

    protected abstract CamelBeanPostProcessor createBeanPostProcessor();

    protected abstract ComponentResolver createComponentResolver();

    protected abstract Registry createRegistry();

    protected abstract UuidGenerator createUuidGenerator();

    protected abstract ModelJAXBContextFactory createModelJAXBContextFactory();

    protected abstract NodeIdFactory createNodeIdFactory();

    protected abstract FactoryFinderResolver createFactoryFinderResolver();

    protected abstract ClassResolver createClassResolver();

    protected abstract ProcessorFactory createProcessorFactory();

    protected abstract DataFormatResolver createDataFormatResolver();

    protected abstract MessageHistoryFactory createMessageHistoryFactory();

    protected abstract InflightRepository createInflightRepository();

    protected abstract AsyncProcessorAwaitManager createAsyncProcessorAwaitManager();

    protected abstract RouteController createRouteController();

    protected abstract HealthCheckRegistry createHealthCheckRegistry();

    protected abstract ShutdownStrategy createShutdownStrategy();

    protected abstract PackageScanClassResolver createPackageScanClassResolver();

    protected abstract ExecutorServiceManager createExecutorServiceManager();

    protected abstract ServicePool<Producer> createProducerServicePool();

    protected abstract ServicePool<PollingConsumer> createPollingConsumerServicePool();

    protected abstract UnitOfWorkFactory createUnitOfWorkFactory();

    protected abstract RuntimeCamelCatalog createRuntimeCamelCatalog();

    protected abstract CamelContextNameStrategy createCamelContextNameStrategy();

    protected abstract ManagementNameStrategy createManagementNameStrategy();

    protected abstract HeadersMapFactory createHeadersMapFactory();

    protected abstract LanguageResolver createLanguageResolver();

    protected abstract RestRegistry createRestRegistry();

    protected abstract EndpointRegistry<EndpointKey> createEndpointRegistry(Map<EndpointKey, Endpoint> endpoints);

    protected abstract ValidatorRegistry<ValidatorKey> createValidatorRegistry(List<ValidatorDefinition> validators) throws Exception;

    protected abstract TransformerRegistry<TransformerKey> createTransformerRegistry(List<TransformerDefinition> transformers) throws Exception;

}
