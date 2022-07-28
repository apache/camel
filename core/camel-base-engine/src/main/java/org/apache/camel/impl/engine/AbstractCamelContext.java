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
package org.apache.camel.impl.engine;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExchangeConstantProvider;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToStartComponentException;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.GlobalEndpointConfiguration;
import org.apache.camel.IsSingleton;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupListener;
import org.apache.camel.StartupStep;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.TypeConverter;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.AnnotationScanTypeConverters;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.CamelContextTracker;
import org.apache.camel.spi.CamelDependencyInjectionAnnotationFactory;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptEndpointFactory;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.InternalProcessorFactory;
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
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RestBindingJaxbDataFormatFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RestRegistryFactory;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError.Phase;
import org.apache.camel.spi.RouteFactory;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.startup.DefaultStartupStepRecorder;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.apache.camel.spi.UnitOfWork.MDC_CAMEL_CONTEXT_ID;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public abstract class AbstractCamelContext extends BaseService
        implements ExtendedCamelContext, CatalogCamelContext, Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCamelContext.class);

    // start auto assigning route ids using numbering 1000 and upwards
    int defaultRouteStartupOrder = 1000;

    private final AtomicInteger endpointKeyCounter = new AtomicInteger();
    private final List<EndpointStrategy> endpointStrategies = new ArrayList<>();
    private final GlobalEndpointConfiguration globalEndpointConfiguration = new DefaultGlobalEndpointConfiguration();
    private final Map<String, Component> components = new ConcurrentHashMap<>();
    private final Set<Route> routes = new LinkedHashSet<>();
    private final List<Service> servicesToStop = new CopyOnWriteArrayList<>();
    private final List<BootstrapCloseable> bootstraps = new CopyOnWriteArrayList<>();
    private final List<StartupListener> startupListeners = new CopyOnWriteArrayList<>();
    private final DeferServiceStartupListener deferStartupListener = new DeferServiceStartupListener();
    private final Map<String, Language> languages = new ConcurrentHashMap<>();
    private final Map<String, DataFormat> dataformats = new ConcurrentHashMap<>();
    private final List<LifecycleStrategy> lifecycleStrategies = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Boolean> isStartingRoutes = new ThreadLocal<>();
    private final ThreadLocal<Boolean> isSetupRoutes = new ThreadLocal<>();
    private final Map<String, FactoryFinder> factories = new ConcurrentHashMap<>();
    private final Map<String, FactoryFinder> bootstrapFactories = new ConcurrentHashMap<>();
    private volatile FactoryFinder bootstrapFactoryFinder;
    private volatile ConfigurerResolver bootstrapConfigurerResolver;
    private final Map<String, RouteService> routeServices = new LinkedHashMap<>();
    private final Map<String, RouteService> suspendedRouteServices = new LinkedHashMap<>();
    private final Object lock = new Object();
    private final RouteController internalRouteController = new InternalRouteController(this);
    private final InternalRouteStartupManager internalRouteStartupManager = new InternalRouteStartupManager(this);
    private volatile DeferServiceFactory deferServiceFactory;
    private volatile AnnotationBasedProcessorFactory annotationBasedProcessorFactory;
    private final List<RouteStartupOrder> routeStartupOrder = new ArrayList<>();
    private final StopWatch stopWatch = new StopWatch(false);
    private final Map<Class<?>, Object> extensions = new ConcurrentHashMap<>();
    private Set<LogListener> logListeners;
    private final ThreadLocal<Set<String>> componentsInCreation = new ThreadLocal<Set<String>>() {
        @Override
        public Set<String> initialValue() {
            return new HashSet<>();
        }
    };
    private VetoCamelContextStartException vetoed;
    private String managementName;
    private ClassLoader applicationContextClassLoader;
    private boolean autoCreateComponents = true;
    private volatile RestConfiguration restConfiguration;
    private volatile VaultConfiguration vaultConfiguration = new VaultConfiguration();
    private List<InterceptStrategy> interceptStrategies = new ArrayList<>();
    private List<RoutePolicyFactory> routePolicyFactories = new ArrayList<>();
    // special flags to control the first startup which can are special
    private volatile boolean firstStartDone;
    private volatile boolean doNotStartRoutesOnFirstStart;
    private Initialization initialization = Initialization.Default;
    private Boolean autoStartup = Boolean.TRUE;
    private Boolean backlogTrace = Boolean.FALSE;
    private Boolean trace = Boolean.FALSE;
    private Boolean traceStandby = Boolean.FALSE;
    private String tracePattern;
    private String tracingLoggingFormat;
    private Boolean modeline = Boolean.FALSE;
    private Boolean debug = Boolean.FALSE;
    private Boolean messageHistory = Boolean.FALSE;
    private Boolean logMask = Boolean.FALSE;
    private Boolean logExhaustedMessageBody = Boolean.FALSE;
    private Boolean streamCache = Boolean.TRUE;
    private Boolean disableJMX = Boolean.FALSE;
    private Boolean loadTypeConverters = Boolean.FALSE;
    private Boolean loadHealthChecks = Boolean.FALSE;
    private Boolean devConsole = Boolean.FALSE;
    private Boolean sourceLocationEnabled = Boolean.FALSE;
    private Boolean typeConverterStatisticsEnabled = Boolean.FALSE;
    private Boolean dumpRoutes = Boolean.FALSE;
    private Boolean useMDCLogging = Boolean.FALSE;
    private String mdcLoggingKeysPattern;
    private Boolean useDataType = Boolean.FALSE;
    private Boolean useBreadcrumb = Boolean.FALSE;
    private Boolean allowUseOriginalMessage = Boolean.FALSE;
    private Boolean caseInsensitiveHeaders = Boolean.TRUE;
    private Boolean autowiredEnabled = Boolean.TRUE;
    private String basePackageScan;
    private boolean lightweight;
    private Long delay;
    @Deprecated
    private ErrorHandlerFactory errorHandlerFactory;
    private Map<String, String> globalOptions = new HashMap<>();
    private volatile String version;
    private volatile PropertiesComponent propertiesComponent;
    private volatile CamelContextNameStrategy nameStrategy;
    private volatile ExchangeFactoryManager exchangeFactoryManager;
    private volatile ExchangeFactory exchangeFactory;
    private volatile ProcessorExchangeFactory processorExchangeFactory;
    private volatile ReactiveExecutor reactiveExecutor;
    private volatile ManagementNameStrategy managementNameStrategy;
    private volatile Registry registry;
    private volatile TypeConverter typeConverter;
    private volatile TypeConverterRegistry typeConverterRegistry;
    private volatile Injector injector;
    private volatile CamelBeanPostProcessor beanPostProcessor;
    private volatile CamelDependencyInjectionAnnotationFactory dependencyInjectionAnnotationFactory;
    private volatile ComponentResolver componentResolver;
    private volatile ComponentNameResolver componentNameResolver;
    private volatile LanguageResolver languageResolver;
    private volatile ConfigurerResolver configurerResolver;
    private volatile UriFactoryResolver uriFactoryResolver;
    private volatile DataFormatResolver dataFormatResolver;
    private volatile HealthCheckResolver healthCheckResolver;
    private volatile DevConsoleResolver devConsoleResolver;
    private volatile ManagementStrategy managementStrategy;
    private volatile ManagementMBeanAssembler managementMBeanAssembler;
    private volatile RestRegistryFactory restRegistryFactory;
    private volatile RestRegistry restRegistry;
    private volatile HeadersMapFactory headersMapFactory;
    private volatile BeanProxyFactory beanProxyFactory;
    private volatile BeanProcessorFactory beanProcessorFactory;
    private volatile XMLRoutesDefinitionLoader xmlRoutesDefinitionLoader;
    private volatile RoutesLoader routesLoader;
    private volatile ResourceLoader resourceLoader;
    private volatile ModelToXMLDumper modelToXMLDumper;
    private volatile RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory;
    private volatile RuntimeCamelCatalog runtimeCamelCatalog;
    private volatile ClassResolver classResolver;
    private volatile PackageScanClassResolver packageScanClassResolver;
    private volatile PackageScanResourceResolver packageScanResourceResolver;
    private volatile NodeIdFactory nodeIdFactory;
    private volatile ModelineFactory modelineFactory;
    private volatile ProcessorFactory processorFactory;
    private volatile InternalProcessorFactory internalProcessorFactory;
    private volatile InterceptEndpointFactory interceptEndpointFactory;
    private volatile RouteFactory routeFactory;
    private volatile MessageHistoryFactory messageHistoryFactory;
    private volatile FactoryFinderResolver factoryFinderResolver;
    private volatile StreamCachingStrategy streamCachingStrategy;
    private volatile InflightRepository inflightRepository;
    private volatile AsyncProcessorAwaitManager asyncProcessorAwaitManager;
    private volatile ShutdownStrategy shutdownStrategy;
    private volatile ModelJAXBContextFactory modelJAXBContextFactory;
    private volatile ExecutorServiceManager executorServiceManager;
    private volatile UuidGenerator uuidGenerator;
    private volatile UnitOfWorkFactory unitOfWorkFactory;
    private volatile RouteController routeController;
    private volatile ScheduledExecutorService errorHandlerExecutorService;
    private volatile BeanIntrospection beanIntrospection;
    private volatile Tracer tracer;
    private volatile boolean eventNotificationApplicable;
    private volatile TransformerRegistry<TransformerKey> transformerRegistry;
    private volatile ValidatorRegistry<ValidatorKey> validatorRegistry;
    private volatile StartupStepRecorder startupStepRecorder = new DefaultStartupStepRecorder();
    private EndpointRegistry<NormalizedUri> endpoints;
    private RuntimeEndpointRegistry runtimeEndpointRegistry;
    private ShutdownRoute shutdownRoute = ShutdownRoute.Default;
    private ShutdownRunningTask shutdownRunningTask = ShutdownRunningTask.CompleteCurrentTaskOnly;
    private Debugger debugger;
    private long buildTaken;
    private long initTaken;
    private long startDate;
    private SSLContextParameters sslContextParameters;
    private StartupSummaryLevel startupSummaryLevel = StartupSummaryLevel.Default;
    private boolean logJvmUptime;

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

    public AbstractCamelContext(boolean build) {
        // create a provisional (temporary) endpoint registry at first since end
        // users may access endpoints before CamelContext is started
        // we will later transfer the endpoints to the actual
        // DefaultEndpointRegistry later, but we do this to startup Camel faster.
        this.endpoints = new ProvisionalEndpointRegistry();

        // add the defer service startup listener
        this.startupListeners.add(deferStartupListener);

        // add a default LifecycleStrategy that discover strategies on the registry and invoke them
        this.lifecycleStrategies.add(new OnCamelContextLifecycleStrategy());

        // add a default LifecycleStrategy to customize services using customizers from registry
        this.lifecycleStrategies.add(new CustomizersLifecycleStrategy(this));

        // add a default autowired strategy
        this.lifecycleStrategies.add(new DefaultAutowiredLifecycleStrategy(this));

        // add the default bootstrap closer
        this.bootstraps.add(new DefaultServiceBootstrapCloseable(this));

        // add a cleaner for FactoryFinder used only when bootstrapping the context
        this.bootstraps.add(new BootstrapCloseable() {
            @Override
            public void close() throws IOException {
                bootstrapFactories.clear();
            }
        });

        if (build) {
            try {
                build();
            } catch (Exception e) {
                throw new RuntimeException("Error initializing CamelContext", e);
            }
        }
    }

    protected static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookupByNameAndType(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
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

    public void close() throws IOException {
        try {
            stop();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public CamelContext getCamelContextReference() {
        return this;
    }

    /**
     * Whether to eager create {@link TypeConverter} during initialization of CamelContext. This is enabled by default
     * to optimize camel-core.
     */
    protected boolean eagerCreateTypeConverter() {
        return true;
    }

    @Override
    public <T extends CamelContext> T adapt(Class<T> type) {
        return type.cast(this);
    }

    @Override
    public <T> T getExtension(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        Object extension = extensions.get(type);
        if (extension instanceof Supplier) {
            extension = ((Supplier) extension).get();
            setExtension(type, (T) extension);
        }
        return (T) extension;
    }

    @Override
    public <T> void setExtension(Class<T> type, T module) {
        if (module != null) {
            try {
                extensions.put(type, doAddService(module));
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    public <T> void setDefaultExtension(Class<T> type, Supplier<T> module) {
        if (module != null) {
            extensions.putIfAbsent(type, module);
        }
    }

    @Override
    public boolean isVetoStarted() {
        return vetoed != null;
    }

    @Deprecated
    public Initialization getInitialization() {
        return initialization;
    }

    @Deprecated
    public void setInitialization(Initialization initialization) {
        this.initialization = initialization;
    }

    @Override
    public String getName() {
        return getNameStrategy().getName();
    }

    @Override
    public void setName(String name) {
        // use an explicit name strategy since an explicit name was provided to be used
        setNameStrategy(new ExplicitCamelContextNameStrategy(name));
    }

    @Override
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

    @Override
    public void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        this.nameStrategy = doAddService(nameStrategy);
    }

    @Override
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

    @Override
    public void setManagementNameStrategy(ManagementNameStrategy managementNameStrategy) {
        this.managementNameStrategy = doAddService(managementNameStrategy);
    }

    @Override
    public String getManagementName() {
        return managementName;
    }

    @Override
    public void setManagementName(String managementName) {
        this.managementName = managementName;
    }

    @Override
    public Component hasComponent(String componentName) {
        if (components.isEmpty()) {
            return null;
        }
        return components.get(componentName);
    }

    @Override
    public void addComponent(String componentName, final Component component) {
        ObjectHelper.notNull(component, "component");
        component.setCamelContext(getCamelContextReference());
        if (isStarted()) {
            // start component if context is already started (camel will start components when it starts)
            ServiceHelper.startService(component);
        } else {
            // otherwise init the component
            ServiceHelper.initService(component);
        }
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
    }

    @Override
    public Component getComponent(String name) {
        return getComponent(name, autoCreateComponents, true);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents) {
        return getComponent(name, autoCreateComponents, true);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents, boolean autoStart) {
        // ensure CamelContext are initialized before we can get a component
        build();

        // Check if the named component is already being created, that would mean
        // that the initComponent has triggered a new getComponent
        if (componentsInCreation.get().contains(name)) {
            throw new IllegalStateException(
                    "Circular dependency detected, the component " + name + " is already being created");
        }

        try {
            // Flag used to mark a component of being created.
            final AtomicBoolean created = new AtomicBoolean();

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
                StartupStep step = startupStepRecorder.beginStep(Component.class, name, "Start Component");
                startService(component);
                startupStepRecorder.endStep(step);
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
            StartupStep step = startupStepRecorder.beginStep(Component.class, name, "Resolve Component");
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using ComponentResolver: {} to resolve component with name: {}", getComponentResolver(), name);
                }

                // Mark the component as being created so we can detect circular
                // requests.
                //
                // In spring apps, the component resolver may trigger a new
                // getComponent because of the underlying bean factory and as
                // the endpoints are registered as singleton, the spring factory
                // creates the bean and then check the type so the getComponent
                // is always triggered.
                //
                // Simple circular dependency:
                //
                // <camelContext id="camel"
                // xmlns="http://camel.apache.org/schema/spring">
                // <route>
                // <from id="twitter"
                // uri="twitter://timeline/home?type=polling"/>
                // <log message="Got ${body}"/>
                // </route>
                // </camelContext>
                //
                // Complex circular dependency:
                //
                // <camelContext id="camel"
                // xmlns="http://camel.apache.org/schema/spring">
                // <route>
                // <from id="log" uri="seda:test"/>
                // <to id="seda" uri="log:test"/>
                // </route>
                // </camelContext>
                //
                // This would freeze the app (lock or infinite loop).
                //
                // See https://issues.apache.org/jira/browse/CAMEL-11225
                componentsInCreation.get().add(name);

                component = ResolverHelper.lookupComponentInRegistryWithFallback(getCamelContextReference(), name);
                if (component == null) {
                    component = getComponentResolver().resolveComponent(name, getCamelContextReference());
                }

                if (component != null) {
                    component.setCamelContext(getCamelContextReference());
                    ServiceHelper.buildService(component);
                    postInitComponent(name, component);
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot auto create component: " + name, e);
            }
            startupStepRecorder.endStep(step);
        }
        return component;
    }

    @Override
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

    // Endpoint Management Methods
    // -----------------------------------------------------------------------

    @Override
    public Component removeComponent(String componentName) {
        Component oldComponent = components.remove(componentName);
        if (oldComponent != null) {
            try {
                stopServices(oldComponent);
            } catch (Exception e) {
                LOG.warn("Error stopping component " + oldComponent + ". This exception will be ignored.", e);
            }
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onComponentRemove(componentName, oldComponent);
            }
        }
        return oldComponent;
    }

    @Override
    public EndpointRegistry<NormalizedUri> getEndpointRegistry() {
        return endpoints;
    }

    @Override
    public Collection<Endpoint> getEndpoints() {
        return endpoints.getReadOnlyValues();
    }

    @Override
    public Map<String, Endpoint> getEndpointMap() {
        Map<String, Endpoint> answer = new TreeMap<>();
        for (Map.Entry<NormalizedUri, Endpoint> entry : endpoints.entrySet()) {
            answer.put(entry.getKey().get(), entry.getValue());
        }
        return answer;
    }

    @Override
    public Endpoint hasEndpoint(String uri) {
        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.get(getEndpointKey(uri));
    }

    @Override
    public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.get(uri);
    }

    @Override
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

    @Override
    public void removeEndpoint(Endpoint endpoint) throws Exception {
        removeEndpoints(endpoint.getEndpointUri());
    }

    @Override
    public Collection<Endpoint> removeEndpoints(String uri) throws Exception {
        Collection<Endpoint> answer = new ArrayList<>();
        Endpoint oldEndpoint = endpoints.remove(getEndpointKey(uri));
        if (oldEndpoint != null) {
            answer.add(oldEndpoint);
            stopServices(oldEndpoint);
        } else {
            List<NormalizedUri> toRemove = new ArrayList<>();
            for (Map.Entry<NormalizedUri, Endpoint> entry : endpoints.entrySet()) {
                oldEndpoint = entry.getValue();
                if (EndpointHelper.matchEndpoint(this, oldEndpoint.getEndpointUri(), uri)) {
                    try {
                        stopServices(oldEndpoint);
                    } catch (Exception e) {
                        LOG.warn("Error stopping endpoint " + oldEndpoint + ". This exception will be ignored.", e);
                    }
                    answer.add(oldEndpoint);
                    toRemove.add(entry.getKey());
                }
            }
            for (NormalizedUri key : toRemove) {
                endpoints.remove(key);
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

    @Override
    public NormalizedEndpointUri normalizeUri(String uri) {
        try {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(this, uri);
            return NormalizedUri.newNormalizedUri(uri, false);
        } catch (Exception e) {
            if (e instanceof ResolveEndpointFailedException) {
                throw e;
            }
            throw new ResolveEndpointFailedException(uri, e);
        }
    }

    @Override
    public Endpoint getEndpoint(String uri) {
        StartupStep step = null;
        // only record startup step during startup (not started)
        if (!isStarted() && startupStepRecorder.isEnabled()) {
            String u = URISupport.sanitizeUri(uri);
            step = startupStepRecorder.beginStep(Endpoint.class, u, "Get Endpoint");
        }
        Endpoint answer = doGetEndpoint(uri, null, false, false);
        if (step != null) {
            startupStepRecorder.endStep(step);
        }
        return answer;
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri) {
        return doGetEndpoint(uri.getUri(), null, true, false);
    }

    @Override
    public Endpoint getPrototypeEndpoint(String uri) {
        return doGetEndpoint(uri, null, false, true);
    }

    @Override
    public Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri) {
        return doGetEndpoint(uri.getUri(), null, true, true);
    }

    @Override
    public Endpoint getEndpoint(String uri, Map<String, Object> parameters) {
        return doGetEndpoint(uri, parameters, false, false);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters) {
        return doGetEndpoint(uri.getUri(), parameters, true, false);
    }

    protected Endpoint doGetEndpoint(String uri, Map<String, Object> parameters, boolean normalized, boolean prototype) {
        // ensure CamelContext are initialized before we can get an endpoint
        build();

        StringHelper.notEmpty(uri, "uri");

        LOG.trace("Getting endpoint with uri: {} and parameters: {}", uri, parameters);

        // in case path has property placeholders then try to let property component resolve those
        if (!normalized) {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(this, uri);
        }

        final String rawUri = uri;

        // normalize uri so we can do endpoint hits with minor mistakes and
        // parameters is not in the same order
        if (!normalized) {
            uri = EndpointHelper.normalizeEndpointUri(uri);
        }

        LOG.trace("Getting endpoint with raw uri: {}, normalized uri: {}", rawUri, uri);

        String scheme;
        Endpoint answer = null;
        if (!prototype) {
            // use optimized method to get the endpoint uri
            NormalizedUri key = NormalizedUri.newNormalizedUri(uri, true);
            // only lookup and reuse existing endpoints if not prototype scoped
            answer = endpoints.get(key);
        }
        if (answer == null) {
            try {
                scheme = StringHelper.before(uri, ":");
                if (scheme == null) {
                    // it may refer to a logical endpoint
                    answer = getRegistry().lookupByNameAndType(uri, Endpoint.class);
                    if (answer != null) {
                        return answer;
                    } else {
                        throw new NoSuchEndpointException(uri);
                    }
                }
                LOG.trace("Endpoint uri: {} is from component with name: {}", uri, scheme);
                Component component = getComponent(scheme);
                ServiceHelper.initService(component);

                // Ask the component to resolve the endpoint.
                if (component != null) {
                    LOG.trace("Creating endpoint from uri: {} using component: {}", uri, component);

                    // Have the component create the endpoint if it can.
                    answer = component.createEndpoint(
                            component.useRawUri() ? rawUri : uri,
                            parameters);

                    if (answer != null && LOG.isDebugEnabled()) {
                        LOG.debug("{} converted to endpoint: {} by component: {}", URISupport.sanitizeUri(uri), answer,
                                component);
                    }
                }

                if (answer == null) {
                    // no component then try in registry and elsewhere
                    answer = createEndpoint(uri);
                    LOG.trace("No component to create endpoint from uri: {} fallback lookup in registry -> {}", uri, answer);
                }

                if (answer != null) {
                    if (!prototype) {
                        addService(answer);
                        // register in registry
                        answer = addEndpointToRegistry(uri, answer);
                    } else {
                        addPrototypeService(answer);
                        // if there is endpoint strategies, then use the endpoints they return
                        // as this allows to intercept endpoints etc.
                        for (EndpointStrategy strategy : endpointStrategies) {
                            answer = strategy.registerEndpoint(uri, answer);
                        }
                    }
                }
            } catch (NoSuchEndpointException e) {
                // throw as-is
                throw e;
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }

        // unknown scheme
        if (answer == null) {
            throw new NoSuchEndpointException(uri);
        }

        return answer;
    }

    @Override
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
            throw new IllegalArgumentException(
                    "The endpoint is not of type: " + endpointType + " but is: " + endpoint.getClass().getCanonicalName());
        }
    }

    @Override
    public void registerEndpointCallback(EndpointStrategy strategy) {
        if (!endpointStrategies.contains(strategy)) {
            // let it be invoked for already registered endpoints so it can
            // catch-up.
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
     * @param  uri      uri of the endpoint
     * @param  endpoint the endpoint to add
     * @return          the added endpoint
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
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link DefaultEndpointRegistry}
     *
     * @param  uri the endpoint uri
     * @return     the key
     */
    protected NormalizedUri getEndpointKey(String uri) {
        return NormalizedUri.newNormalizedUri(uri, false);
    }

    /**
     * Gets the endpoint key to use for lookup or whe adding endpoints to the {@link DefaultEndpointRegistry}
     *
     * @param  uri      the endpoint uri
     * @param  endpoint the endpoint
     * @return          the key
     */
    protected NormalizedUri getEndpointKey(String uri, Endpoint endpoint) {
        if (endpoint != null && !endpoint.isSingleton()) {
            int counter = endpointKeyCounter.incrementAndGet();
            return NormalizedUri.newNormalizedUri(uri + ":" + counter, false);
        } else {
            return NormalizedUri.newNormalizedUri(uri, false);
        }
    }

    // Route Management Methods
    // -----------------------------------------------------------------------

    @Override
    public GlobalEndpointConfiguration getGlobalEndpointConfiguration() {
        return globalEndpointConfiguration;
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

    @Override
    public void setRouteController(RouteController routeController) {
        this.routeController = doAddService(routeController);
    }

    @Override
    public List<RouteStartupOrder> getRouteStartupOrder() {
        return routeStartupOrder;
    }

    @Override
    public List<Route> getRoutes() {
        // lets return a copy of the collection as objects are removed later
        // when services are stopped
        if (routes.isEmpty()) {
            return Collections.emptyList();
        } else {
            synchronized (routes) {
                return new ArrayList<>(routes);
            }
        }
    }

    @Override
    public int getRoutesSize() {
        return routes.size();
    }

    @Override
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

    @Override
    public Processor getProcessor(String id) {
        for (Route route : getRoutes()) {
            List<Processor> list = route.filter(id);
            if (list.size() == 1) {
                return list.get(0);
            }
        }
        return null;
    }

    @Override
    public <T extends Processor> T getProcessor(String id, Class<T> type) {
        Processor answer = getProcessor(id);
        if (answer != null) {
            return type.cast(answer);
        }
        return null;
    }

    public void removeRoute(Route route) {
        synchronized (this.routes) {
            this.routes.remove(route);
        }
    }

    public void addRoute(Route route) {
        synchronized (this.routes) {
            this.routes.add(route);
        }
    }

    @Override
    public void addRoutes(RoutesBuilder builder) throws Exception {
        // in case the builder is also a route configuration builder
        // then we need to add the configuration first
        if (builder instanceof RouteConfigurationsBuilder) {
            addRoutesConfigurations((RouteConfigurationsBuilder) builder);
        }
        try (LifecycleHelper helper = new LifecycleHelper()) {
            build();
            LOG.debug("Adding routes from builder: {}", builder);
            builder.addRoutesToCamelContext(AbstractCamelContext.this);
        }
    }

    @Override
    public void addRoutesConfigurations(RouteConfigurationsBuilder builder) throws Exception {
        try (LifecycleHelper helper = new LifecycleHelper()) {
            build();
            LOG.debug("Adding route configurations from builder: {}", builder);
            builder.addRouteConfigurationsToCamelContext(AbstractCamelContext.this);
        }
    }

    public ServiceStatus getRouteStatus(String key) {
        RouteService routeService = routeServices.get(key);
        if (routeService != null) {
            return routeService.getStatus();
        }
        return null;
    }

    public boolean isStartingRoutes() {
        Boolean answer = isStartingRoutes.get();
        return answer != null && answer;
    }

    public void setStartingRoutes(boolean starting) {
        if (starting) {
            isStartingRoutes.set(true);
        } else {
            isStartingRoutes.remove();
        }
    }

    @Override
    public boolean isSetupRoutes() {
        Boolean answer = isSetupRoutes.get();
        return answer != null && answer;
    }

    public void startAllRoutes() throws Exception {
        internalRouteStartupManager.doStartOrResumeRoutes(routeServices, true, true, false, false);
    }

    public void stopAllRoutes() throws Exception {
        RouteController controller = getRouteController();
        if (controller == null) {
            // in case we are called during shutdown and controller is null
            return;
        }

        // stop all routes in reverse order that they were started
        Comparator<RouteStartupOrder> comparator = Comparator.comparingInt(RouteStartupOrder::getStartupOrder);
        if (shutdownStrategy == null || shutdownStrategy.isShutdownRoutesInReverseOrder()) {
            comparator = comparator.reversed();
        }
        List<RouteStartupOrder> routesOrdered = new ArrayList<>(getRouteStartupOrder());
        routesOrdered.sort(comparator);
        for (RouteStartupOrder order : routesOrdered) {
            Route route = order.getRoute();
            boolean stopped = controller.getRouteStatus(route.getRouteId()).isStopped();
            if (!stopped) {
                stopRoute(route.getRouteId(), LoggingLevel.DEBUG);
            }
        }
        // stop any remainder routes
        for (Route route : getRoutes()) {
            boolean stopped = controller.getRouteStatus(route.getRouteId()).isStopped();
            if (!stopped) {
                stopRoute(route.getRouteId(), LoggingLevel.DEBUG);
            }
        }

        if (startupSummaryLevel != StartupSummaryLevel.Oneline
                && startupSummaryLevel != StartupSummaryLevel.Off) {
            logRouteStopSummary(LoggingLevel.INFO);
        }
    }

    public void removeAllRoutes() throws Exception {
        // stop all routes in reverse order that they were started
        Comparator<RouteStartupOrder> comparator = Comparator.comparingInt(RouteStartupOrder::getStartupOrder);
        if (shutdownStrategy == null || shutdownStrategy.isShutdownRoutesInReverseOrder()) {
            comparator = comparator.reversed();
        }
        List<RouteStartupOrder> routesOrdered = new ArrayList<>(getRouteStartupOrder());
        routesOrdered.sort(comparator);
        for (RouteStartupOrder order : routesOrdered) {
            Route route = order.getRoute();
            boolean stopped = getRouteController().getRouteStatus(route.getRouteId()).isStopped();
            if (!stopped) {
                stopRoute(route.getRouteId(), LoggingLevel.DEBUG);
            }
        }
        // stop any remainder routes
        for (Route route : getRoutes()) {
            boolean stopped = getRouteController().getRouteStatus(route.getRouteId()).isStopped();
            if (!stopped) {
                stopRoute(route.getRouteId(), LoggingLevel.DEBUG);
            }
        }

        // do not be noisy when removing routes
        // as this is used by route-reload functionality, so lets be brief
        logRouteStopSummary(LoggingLevel.DEBUG);

        // remove all routes
        for (Route route : getRoutes()) {
            removeRoute(route.getRouteId(), LoggingLevel.DEBUG);
        }
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

    public synchronized boolean stopRoute(
            String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout, LoggingLevel loggingLevel)
            throws Exception {
        DefaultRouteError.reset(this, routeId);

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            try {
                RouteStartupOrder route = new DefaultRouteStartupOrder(1, routeService.getRoute(), routeService);

                boolean completed = getShutdownStrategy().shutdown(this, route, timeout, timeUnit, abortAfterTimeout);
                if (completed) {
                    // must stop route service as well
                    stopRouteService(routeService, false, loggingLevel);
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
        stopRoute(routeId, LoggingLevel.INFO);
    }

    public void stopRoute(String routeId, LoggingLevel loggingLevel) throws Exception {
        doShutdownRoute(routeId, getShutdownStrategy().getTimeout(), getShutdownStrategy().getTimeUnit(), false, loggingLevel);
    }

    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        doShutdownRoute(routeId, timeout, timeUnit, false, LoggingLevel.INFO);
    }

    protected synchronized void doShutdownRoute(
            String routeId, long timeout, TimeUnit timeUnit, boolean removingRoutes, LoggingLevel loggingLevel)
            throws Exception {
        DefaultRouteError.reset(this, routeId);

        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            try {
                List<RouteStartupOrder> routes = new ArrayList<>(1);
                RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoute(), routeService);
                routes.add(order);

                getShutdownStrategy().shutdown(this, routes, timeout, timeUnit);
                // must stop route service as well (and remove the routes from
                // management)
                stopRouteService(routeService, removingRoutes, loggingLevel);
            } catch (Exception e) {
                DefaultRouteError.set(this, routeId, removingRoutes ? Phase.SHUTDOWN : Phase.STOP, e);
                throw e;
            }
        }
    }

    @Override
    public synchronized boolean removeRoute(String routeId) throws Exception {
        return removeRoute(routeId, LoggingLevel.INFO);
    }

    protected synchronized boolean removeRoute(String routeId, LoggingLevel loggingLevel) throws Exception {
        DefaultRouteError.reset(this, routeId);

        // gather a map of all the endpoints in use by the routes, so we can
        // known if a given endpoints is in use
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
                    shutdownRouteService(routeService, loggingLevel);
                    routeServices.remove(routeId);
                    // remove route from startup order as well, as it was
                    // removed
                    routeStartupOrder.removeIf(order -> order.getRoute().getId().equals(routeId));

                    // from the route which we have removed, then remove all its
                    // private endpoints
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
                        // notice we will count ourselves so if there is only 1
                        // then its safe to remove
                        if (count <= 1) {
                            toRemove.add(endpoint);
                        }
                    }
                    for (Endpoint endpoint : toRemove) {
                        LOG.debug("Removing: {} which was only in use by route: {}", endpoint, routeId);
                        removeEndpoint(endpoint);
                    }
                } catch (Exception e) {
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
                Route route = routeService.getRoute();
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

    @Override
    public void addService(Object object) throws Exception {
        addService(object, true);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown) throws Exception {
        addService(object, stopOnShutdown, false);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception {
        internalAddService(object, stopOnShutdown, forceStart, true);
    }

    @Override
    public void addPrototypeService(Object object) throws Exception {
        doAddService(object, false, true, false);
    }

    protected <T> T doAddService(T object) {
        return doAddService(object, true);
    }

    protected <T> T doAddService(T object, boolean stopOnShutdown) {
        return doAddService(object, stopOnShutdown, true, true);
    }

    protected <T> T doAddService(T object, boolean stopOnShutdown, boolean forceStart, boolean useLifecycleStrategies) {
        try {
            internalAddService(object, stopOnShutdown, forceStart, useLifecycleStrategies);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return object;
    }

    private void internalAddService(
            Object object, boolean stopOnShutdown,
            boolean forceStart, boolean useLifecycleStrategies)
            throws Exception {

        // inject CamelContext
        CamelContextAware.trySetCamelContext(object, getCamelContextReference());

        if (object instanceof Service) {
            Service service = (Service) object;

            if (useLifecycleStrategies) {
                for (LifecycleStrategy strategy : lifecycleStrategies) {
                    if (service instanceof Endpoint) {
                        // use specialized endpoint add
                        strategy.onEndpointAdd((Endpoint) service);
                    } else {
                        Route route;
                        if (service instanceof RouteAware) {
                            route = ((RouteAware) service).getRoute();
                        } else {
                            // if the service is added while creating a new route then grab the route from the startup manager
                            route = internalRouteStartupManager.getSetupRoute();
                        }
                        strategy.onServiceAdd(getCamelContextReference(), service, route);
                    }
                }
            }

            if (!forceStart) {
                ServiceHelper.initService(service);
                // now start the service (and defer starting if CamelContext is
                // starting up itself)
                deferStartService(object, stopOnShutdown);
            } else {
                // only add to services to close if its a singleton
                // otherwise we could for example end up with a lot of prototype
                // scope endpoints
                boolean singleton = true; // assume singleton by default
                if (object instanceof IsSingleton) {
                    singleton = ((IsSingleton) service).isSingleton();
                }
                // do not add endpoints as they have their own list
                if (singleton && !(service instanceof Endpoint)) {
                    // only add to list of services to stop if its not already there
                    if (stopOnShutdown && !hasService(service)) {
                        // special for type converter / type converter registry which is stopped manual later
                        boolean tc = service instanceof TypeConverter || service instanceof TypeConverterRegistry;
                        if (!tc) {
                            servicesToStop.add(service);
                        }
                    }
                }
                if (isStartingOrStarted()) {
                    ServiceHelper.startService(service);
                } else {
                    ServiceHelper.initService(service);
                    deferStartService(object, stopOnShutdown, true);
                }
            }
        }
    }

    @Override
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

    @Override
    public void addBootstrap(BootstrapCloseable bootstrap) {
        bootstraps.add(bootstrap);
    }

    @Override
    public List<Service> getServices() {
        return Collections.unmodifiableList(servicesToStop);
    }

    @Override
    public boolean hasService(Object object) {
        if (servicesToStop.isEmpty()) {
            return false;
        }
        if (object instanceof Service) {
            Service service = (Service) object;
            return servicesToStop.contains(service);
        }
        return false;
    }

    @Override
    public <T> T hasService(Class<T> type) {
        if (servicesToStop.isEmpty()) {
            return null;
        }
        for (Service service : servicesToStop) {
            if (type.isInstance(service)) {
                return type.cast(service);
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Set<T> hasServices(Class<T> type) {
        if (servicesToStop.isEmpty()) {
            return Collections.EMPTY_SET;
        }
        Set<T> set = new HashSet<>();
        for (Service service : servicesToStop) {
            if (type.isInstance(service)) {
                set.add((T) service);
            }
        }
        return set;
    }

    @Override
    public void deferStartService(Object object, boolean stopOnShutdown) throws Exception {
        deferStartService(object, stopOnShutdown, false);
    }

    public void deferStartService(Object object, boolean stopOnShutdown, boolean startEarly) throws Exception {
        if (object instanceof Service) {
            Service service = (Service) object;

            // only add to services to close if its a singleton
            // otherwise we could for example end up with a lot of prototype
            // scope endpoints
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
                deferStartupListener.addService(service, startEarly);
            }
        }
    }

    protected List<StartupListener> getStartupListeners() {
        return startupListeners;
    }

    @Override
    public void addStartupListener(StartupListener listener) throws Exception {
        // either add to listener so we can invoke then later when CamelContext
        // has been started
        // or invoke the callback right now
        if (isStarted()) {
            listener.onCamelContextStarted(this, true);
        } else {
            startupListeners.add(listener);
        }
    }

    public String getComponentParameterJsonSchema(String componentName) throws IOException {
        // use the component factory finder to find the package name of the
        // component class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = getFactoryFinder(DefaultComponentResolver.RESOURCE_PATH);
        Class<?> clazz = finder.findClass(componentName).orElse(null);
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
        LOG.debug("Loading component JSON Schema for: {} using class resolver: {} -> {}", componentName, resolver, inputStream);
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
    }

    public String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException {
        // use the dataformat factory finder to find the package name of the
        // dataformat class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = getFactoryFinder(DefaultDataFormatResolver.DATAFORMAT_RESOURCE_PATH);
        Class<?> clazz = finder.findClass(dataFormatName).orElse(null);
        if (clazz == null) {
            return null;
        }

        String packageName = clazz.getPackage().getName();
        packageName = packageName.replace('.', '/');
        String path = packageName + "/" + dataFormatName + ".json";

        ClassResolver resolver = getClassResolver();
        InputStream inputStream = resolver.loadResourceAsStream(path);
        LOG.debug("Loading dataformat JSON Schema for: {} using class resolver: {} -> {}", dataFormatName, resolver,
                inputStream);
        if (inputStream != null) {
            try {
                return IOHelper.loadText(inputStream);
            } finally {
                IOHelper.close(inputStream);
            }
        }
        return null;
    }

    public String getLanguageParameterJsonSchema(String languageName) throws IOException {
        // use the language factory finder to find the package name of the
        // language class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = getFactoryFinder(DefaultLanguageResolver.LANGUAGE_RESOURCE_PATH);
        Class<?> clazz = finder.findClass(languageName).orElse(null);
        if (clazz == null) {
            return null;
        }

        String packageName = clazz.getPackage().getName();
        packageName = packageName.replace('.', '/');
        String path = packageName + "/" + languageName + ".json";

        ClassResolver resolver = getClassResolver();
        InputStream inputStream = resolver.loadResourceAsStream(path);
        LOG.debug("Loading language JSON Schema for: {} using class resolver: {} -> {}", languageName, resolver, inputStream);
        if (inputStream != null) {
            try {
                return IOHelper.loadText(inputStream);
            } finally {
                IOHelper.close(inputStream);
            }
        }
        return null;
    }

    // Helper methods
    // -----------------------------------------------------------------------

    public String getEipParameterJsonSchema(String eipName) throws IOException {
        // the eip json schema may be in some of the sub-packages so look until
        // we find it
        String[] subPackages = new String[] { "", "/config", "/dataformat", "/language", "/loadbalancer", "/rest" };
        for (String sub : subPackages) {
            String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + "/" + eipName + ".json";
            ClassResolver resolver = getClassResolver();
            InputStream inputStream = resolver.loadResourceAsStream(path);
            if (inputStream != null) {
                LOG.debug("Loading eip JSON Schema for: {} using class resolver: {} -> {}", eipName, resolver, inputStream);
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
        }
        return null;
    }

    @Override
    public Language resolveLanguage(String name) {
        LOG.debug("Resolving language: {}", name);

        return languages.computeIfAbsent(name, new Function<String, Language>() {
            @Override
            public Language apply(String s) {
                StartupStep step = null;
                // only record startup step during startup (not started)
                if (!isStarted() && startupStepRecorder.isEnabled()) {
                    step = startupStepRecorder.beginStep(Language.class, name, "Resolve Language");
                }

                final CamelContext camelContext = getCamelContextReference();

                // as first iteration, check if there is a language instance for the given name
                // bound to the registry
                Language language = ResolverHelper.lookupLanguageInRegistryWithFallback(camelContext, name);

                if (language == null) {
                    // language not known, then use resolver
                    language = getLanguageResolver().resolveLanguage(name, camelContext);
                }

                if (language != null) {
                    if (language instanceof Service) {
                        try {
                            Service service = (Service) language;
                            // init service first
                            CamelContextAware.trySetCamelContext(service, camelContext);
                            ServiceHelper.initService(service);
                            startService(service);
                        } catch (Exception e) {
                            throw RuntimeCamelException.wrapRuntimeCamelException(e);
                        }
                    }

                    // inject CamelContext if aware
                    CamelContextAware.trySetCamelContext(language, camelContext);

                    for (LifecycleStrategy strategy : lifecycleStrategies) {
                        strategy.onLanguageCreated(name, language);
                    }
                }

                if (step != null) {
                    startupStepRecorder.endStep(step);
                }
                return language;
            }
        });
    }

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public String resolvePropertyPlaceholders(String text) {
        return resolvePropertyPlaceholders(text, false);
    }

    @Override
    public String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional) {
        if (text != null && text.contains(PropertiesComponent.PREFIX_TOKEN)) {
            // the parser will throw exception if property key was not found
            String answer = getPropertiesComponent().parseUri(text, keepUnresolvedOptional);
            LOG.debug("Resolved text: {} -> {}", text, answer);
            return answer;
        }
        // is the value a known field (currently we only support
        // constants from Exchange.class)
        if (text != null && text.startsWith("Exchange.")) {
            String field = StringHelper.after(text, "Exchange.");
            String constant = ExchangeConstantProvider.lookup(field);
            if (constant != null) {
                LOG.debug("Resolved constant: {} -> {}", text, constant);
                return constant;
            } else {
                throw new IllegalArgumentException("Constant field with name: " + field + " not found on Exchange.class");
            }
        }

        // return original text as is
        return text;
    }

    @Override
    public TypeConverter getTypeConverter() {
        return typeConverter;
    }

    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = doAddService(typeConverter);
    }

    protected TypeConverter getOrCreateTypeConverter() {
        if (typeConverter == null) {
            synchronized (lock) {
                if (typeConverter == null) {
                    setTypeConverter(createTypeConverter());
                }
            }
        }
        return typeConverter;
    }

    @Override
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

    @Override
    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        this.typeConverterRegistry = doAddService(typeConverterRegistry);
        // some registries are also a type converter implementation
        if (typeConverterRegistry instanceof TypeConverter) {
            this.typeConverter = (TypeConverter) typeConverterRegistry;
        }
    }

    @Override
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

    @Override
    public void setInjector(Injector injector) {
        this.injector = doAddService(injector);
    }

    @Override
    public PropertiesComponent getPropertiesComponent() {
        if (propertiesComponent == null) {
            synchronized (lock) {
                if (propertiesComponent == null) {
                    setPropertiesComponent(createPropertiesComponent());
                }
            }
        }
        return propertiesComponent;
    }

    @Override
    public void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = doAddService(propertiesComponent);
    }

    @Override
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

    @Override
    public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {
        this.beanPostProcessor = doAddService(beanPostProcessor);
    }

    @Override
    public CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory() {
        if (dependencyInjectionAnnotationFactory == null) {
            synchronized (lock) {
                if (dependencyInjectionAnnotationFactory == null) {
                    setDependencyInjectionAnnotationFactory(createDependencyInjectionAnnotationFactory());
                }
            }
        }
        return dependencyInjectionAnnotationFactory;
    }

    @Override
    public void setDependencyInjectionAnnotationFactory(
            CamelDependencyInjectionAnnotationFactory dependencyInjectionAnnotationFactory) {
        this.dependencyInjectionAnnotationFactory = dependencyInjectionAnnotationFactory;
    }

    @Override
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

    public ComponentNameResolver getComponentNameResolver() {
        if (componentNameResolver == null) {
            synchronized (lock) {
                if (componentNameResolver == null) {
                    setComponentNameResolver(createComponentNameResolver());
                }
            }
        }
        return componentNameResolver;
    }

    public void setComponentNameResolver(ComponentNameResolver componentNameResolver) {
        this.componentNameResolver = doAddService(componentNameResolver);
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

    public ConfigurerResolver getConfigurerResolver() {
        if (configurerResolver == null) {
            synchronized (lock) {
                if (configurerResolver == null) {
                    setConfigurerResolver(createConfigurerResolver());
                }
            }
        }
        return configurerResolver;
    }

    public void setConfigurerResolver(ConfigurerResolver configurerResolver) {
        this.configurerResolver = doAddService(configurerResolver);
    }

    public UriFactoryResolver getUriFactoryResolver() {
        if (uriFactoryResolver == null) {
            synchronized (lock) {
                if (uriFactoryResolver == null) {
                    setUriFactoryResolver(createUriFactoryResolver());
                }
            }
        }
        return uriFactoryResolver;
    }

    public void setUriFactoryResolver(UriFactoryResolver uriFactoryResolver) {
        this.uriFactoryResolver = doAddService(uriFactoryResolver);
    }

    public boolean isAutoCreateComponents() {
        return autoCreateComponents;
    }

    public void setAutoCreateComponents(boolean autoCreateComponents) {
        this.autoCreateComponents = autoCreateComponents;
    }

    @Override
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

    @Override
    public void setRegistry(Registry registry) {
        CamelContextAware.trySetCamelContext(registry, getCamelContextReference());
        this.registry = registry;
    }

    @Override
    public <T> T getRegistry(Class<T> type) {
        Registry reg = getRegistry();

        if (type.isAssignableFrom(reg.getClass())) {
            return type.cast(reg);
        }
        return null;
    }

    @Override
    public List<LifecycleStrategy> getLifecycleStrategies() {
        return lifecycleStrategies;
    }

    @Override
    public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        // avoid adding double which can happen with spring xml on spring boot
        if (!getLifecycleStrategies().contains(lifecycleStrategy)) {
            getLifecycleStrategies().add(lifecycleStrategy);
        }
    }

    @Override
    public void setupRoutes(boolean done) {
        if (done) {
            isSetupRoutes.remove();
        } else {
            isSetupRoutes.set(true);
        }
    }

    @Override
    public RestConfiguration getRestConfiguration() {
        if (restConfiguration == null) {
            synchronized (lock) {
                if (restConfiguration == null) {
                    setRestConfiguration(createRestConfiguration());
                }
            }
        }
        return restConfiguration;
    }

    @Override
    public void setRestConfiguration(RestConfiguration restConfiguration) {
        this.restConfiguration = restConfiguration;
    }

    @Override
    public VaultConfiguration getVaultConfiguration() {
        return vaultConfiguration;
    }

    @Override
    public void setVaultConfiguration(VaultConfiguration vaultConfiguration) {
        this.vaultConfiguration = vaultConfiguration;
    }

    @Override
    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    public void setInterceptStrategies(List<InterceptStrategy> interceptStrategies) {
        this.interceptStrategies = interceptStrategies;
    }

    @Override
    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        // avoid adding double which can happen with spring xml on spring boot
        if (!getInterceptStrategies().contains(interceptStrategy)) {
            getInterceptStrategies().add(interceptStrategy);
        }
    }

    @Override
    public List<RoutePolicyFactory> getRoutePolicyFactories() {
        return routePolicyFactories;
    }

    public void setRoutePolicyFactories(List<RoutePolicyFactory> routePolicyFactories) {
        this.routePolicyFactories = routePolicyFactories;
    }

    @Override
    public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {
        // avoid adding double which can happen with spring xml on spring boot
        if (!getRoutePolicyFactories().contains(routePolicyFactory)) {
            getRoutePolicyFactories().add(routePolicyFactory);
        }
    }

    @Override
    public Set<LogListener> getLogListeners() {
        return logListeners;
    }

    @Override
    public void addLogListener(LogListener listener) {
        if (logListeners == null) {
            logListeners = new LinkedHashSet<>();
        }
        // avoid adding double which can happen with spring xml on spring boot
        logListeners.add(listener);
    }

    @Override
    public void setStreamCaching(Boolean cache) {
        this.streamCache = cache;
    }

    @Override
    public Boolean isStreamCaching() {
        return streamCache;
    }

    @Override
    public void setTracing(Boolean tracing) {
        this.trace = tracing;
    }

    @Override
    public Boolean isTracing() {
        return trace;
    }

    @Override
    public String getTracingPattern() {
        return tracePattern;
    }

    @Override
    public void setTracingPattern(String tracePattern) {
        this.tracePattern = tracePattern;
    }

    @Override
    public String getTracingLoggingFormat() {
        return tracingLoggingFormat;
    }

    @Override
    public void setTracingLoggingFormat(String format) {
        this.tracingLoggingFormat = format;
    }

    @Override
    public Boolean isBacklogTracing() {
        return backlogTrace;
    }

    @Override
    public void setBacklogTracing(Boolean backlogTrace) {
        this.backlogTrace = backlogTrace;
    }

    @Override
    public void setDebugging(Boolean debug) {
        this.debug = debug;
    }

    @Override
    public Boolean isDebugging() {
        return debug;
    }

    @Override
    public void setMessageHistory(Boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    @Override
    public Boolean isMessageHistory() {
        return messageHistory;
    }

    @Override
    public void setLogMask(Boolean logMask) {
        this.logMask = logMask;
    }

    @Override
    public Boolean isLogMask() {
        return logMask != null && logMask;
    }

    @Override
    public Boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    @Override
    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    @Override
    public Long getDelayer() {
        return delay;
    }

    @Override
    public void setDelayer(Long delay) {
        this.delay = delay;
    }

    @Override
    public ProducerTemplate createProducerTemplate() {
        return createProducerTemplate(0);
    }

    @Override
    public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
        DefaultProducerTemplate answer = new DefaultProducerTemplate(getCamelContextReference());
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
    public FluentProducerTemplate createFluentProducerTemplate() {
        return createFluentProducerTemplate(0);
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize) {
        DefaultFluentProducerTemplate answer = new DefaultFluentProducerTemplate(getCamelContextReference());
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
    public ConsumerTemplate createConsumerTemplate() {
        return createConsumerTemplate(0);
    }

    @Override
    public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
        DefaultConsumerTemplate answer = new DefaultConsumerTemplate(getCamelContextReference());
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
    public ErrorHandlerFactory getErrorHandlerFactory() {
        return errorHandlerFactory;
    }

    @Override
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerFactory = errorHandlerFactory;
    }

    @Override
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

    public void setErrorHandlerExecutorService(ScheduledExecutorService errorHandlerExecutorService) {
        this.errorHandlerExecutorService = errorHandlerExecutorService;
    }

    protected ScheduledExecutorService createErrorHandlerExecutorService() {
        return getExecutorServiceManager().newDefaultScheduledThreadPool("ErrorHandlerRedeliveryThreadPool",
                "ErrorHandlerRedeliveryTask");
    }

    @Override
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

    @Override
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        this.unitOfWorkFactory = doAddService(unitOfWorkFactory);
    }

    @Override
    public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
        return runtimeEndpointRegistry;
    }

    @Override
    public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {
        this.runtimeEndpointRegistry = doAddService(runtimeEndpointRegistry);
    }

    @Override
    public String getUptime() {
        long delta = getUptimeMillis();
        if (delta == 0) {
            return "";
        }
        return TimeUtils.printDuration(delta);
    }

    @Override
    public long getUptimeMillis() {
        if (startDate == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startDate;
    }

    @Override
    public Date getStartDate() {
        if (startDate == 0) {
            return null;
        }
        return new Date(startDate);
    }

    @Override
    public boolean isEventNotificationApplicable() {
        return eventNotificationApplicable;
    }

    @Override
    public void setEventNotificationApplicable(boolean eventNotificationApplicable) {
        this.eventNotificationApplicable = eventNotificationApplicable;
    }

    @Override
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
            is = AbstractCamelContext.class
                    .getResourceAsStream("/META-INF/maven/org.apache.camel/camel-base-engine/pom.properties");
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

        LOG.info("Apache Camel {} ({}) is suspending", getVersion(), getName());
        StopWatch watch = new StopWatch();

        // update list of started routes to be suspended
        // because we only want to suspend started routes
        // (so when we resume we only resume the routes which actually was
        // suspended)
        for (Map.Entry<String, RouteService> entry : getRouteServices().entrySet()) {
            if (entry.getValue().getStatus().isStarted()) {
                suspendedRouteServices.put(entry.getKey(), entry.getValue());
            }
        }

        // assemble list of startup ordering so routes can be shutdown
        // accordingly
        List<RouteStartupOrder> orders = new ArrayList<>();
        for (Map.Entry<String, RouteService> entry : suspendedRouteServices.entrySet()) {
            Route route = entry.getValue().getRoute();
            Integer order = route.getStartupOrder();
            if (order == null) {
                order = defaultRouteStartupOrder++;
            }
            orders.add(new DefaultRouteStartupOrder(order, route, entry.getValue()));
        }

        // suspend routes using the shutdown strategy so it can shutdown in
        // correct order
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
        if (LOG.isInfoEnabled()) {
            LOG.info("Apache Camel {} ({}) is suspended in {}", getVersion(), getName(),
                    TimeUtils.printDuration(watch.taken(), true));
        }

        EventHelper.notifyCamelContextSuspended(this);
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    @Override
    protected void doResume() throws Exception {
        try {
            EventHelper.notifyCamelContextResuming(this);

            LOG.info("Apache Camel {} ({}) is resuming", getVersion(), getName());
            StopWatch watch = new StopWatch();

            // start the suspended routes (do not check for route clashes, and
            // indicate)
            internalRouteStartupManager.doStartOrResumeRoutes(suspendedRouteServices, false, true, true, false);

            // mark the route services as resumed (will be marked as started) as
            // well
            for (RouteService service : suspendedRouteServices.values()) {
                if (routeSupportsSuspension(service.getId())) {
                    service.resume();
                } else {
                    service.start();
                }
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("Resumed {} routes", suspendedRouteServices.size());
                LOG.info("Apache Camel {} ({}) resumed in {}", getVersion(), getName(),
                        TimeUtils.printDuration(watch.taken(), true));
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
    protected AutoCloseable doLifecycleChange() {
        return new LifecycleHelper();
    }

    @Override
    public void init() {
        try {
            super.init();
        } catch (RuntimeCamelException e) {
            if (e.getCause() != null && e.getCause() instanceof VetoCamelContextStartException) {
                vetoed = (VetoCamelContextStartException) e.getCause();
            } else {
                throw e;
            }
        }

        // was the initialization vetoed?
        if (vetoed != null) {
            LOG.info("CamelContext ({}) vetoed to not initialize due to: {}", getName(), vetoed.getMessage());
            failOnStartup(vetoed);
            return;
        }
    }

    @Override
    public void start() {
        super.start();

        //
        // We need to perform the following actions after the {@link #start()} method
        // is called, so that the state of the {@link CamelContext} is <code>Started<code>.
        //

        // did the start veto?
        if (vetoed != null) {
            LOG.info("CamelContext ({}) vetoed to not start due to: {}", getName(), vetoed.getMessage());
            failOnStartup(vetoed);
            stop();
            return;
        }

        for (LifecycleStrategy strategy : lifecycleStrategies) {
            try {
                strategy.onContextStarted(this);
            } catch (Throwable e) {
                LOG.warn("Lifecycle strategy {} failed on CamelContext ({}) due to: {}. This exception will be ignored",
                        strategy,
                        getName(),
                        e.getMessage());
            }
        }

        // okay the routes has been started so emit event that CamelContext
        // has started (here at the end)
        EventHelper.notifyCamelContextStarted(this);

        // now call the startup listeners where the routes has been started
        for (StartupListener startup : startupListeners) {
            try {
                startup.onCamelContextFullyStarted(this, isStarted());
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    @Override
    public void stop() {
        for (LifecycleStrategy strategy : lifecycleStrategies) {
            try {
                strategy.onContextStopping(this);
                strategy.onContextStop(this);
            } catch (Throwable e) {
                LOG.warn("Lifecycle strategy {} failed on CamelContext ({}) due to: {}. This exception will be ignored",
                        strategy,
                        getName(),
                        e.getMessage());
            }
        }

        super.stop();
    }

    @Override
    public void doBuild() throws Exception {
        StopWatch watch = new StopWatch();

        // auto-detect step recorder from classpath if none has been explicit configured
        if (startupStepRecorder.getClass().getSimpleName().equals("DefaultStartupStepRecorder")) {
            StartupStepRecorder fr = getBootstrapFactoryFinder()
                    .newInstance(StartupStepRecorder.FACTORY, StartupStepRecorder.class).orElse(null);
            if (fr != null) {
                LOG.debug("Discovered startup recorder: {} from classpath", fr);
                startupStepRecorder = fr;
            }
        }

        startupStepRecorder.start();
        StartupStep step = startupStepRecorder.beginStep(CamelContext.class, null, "Build CamelContext");

        // Initialize LRUCacheFactory as eager as possible,
        // to let it warm up concurrently while Camel is startup up
        if (initialization != Initialization.Lazy) {
            StartupStep subStep = startupStepRecorder.beginStep(CamelContext.class, null, "Setup LRUCacheFactory");
            LRUCacheFactory.init();
            startupStepRecorder.endStep(subStep);
        }

        // Setup management first since end users may use it to add event
        // notifiers using the management strategy before the CamelContext has been started
        StartupStep step3 = startupStepRecorder.beginStep(CamelContext.class, null, "Setup Management");
        setupManagement(null);
        startupStepRecorder.endStep(step3);

        // setup health-check registry as its needed this early phase for 3rd party to register custom repositories
        HealthCheckRegistry hcr = getExtension(HealthCheckRegistry.class);
        if (hcr == null) {
            StartupStep step4 = startupStepRecorder.beginStep(CamelContext.class, null, "Setup HealthCheckRegistry");
            hcr = createHealthCheckRegistry();
            if (hcr != null) {
                // install health-check registry if it was discovered from classpath (camel-health)
                hcr.setCamelContext(this);
                setExtension(HealthCheckRegistry.class, hcr);
            }
            startupStepRecorder.endStep(step4);
        }

        // setup dev-console registry as its needed this early phase for 3rd party to register custom consoles
        DevConsoleRegistry dcr = getExtension(DevConsoleRegistry.class);
        if (dcr == null) {
            StartupStep step5 = startupStepRecorder.beginStep(CamelContext.class, null, "Setup DevConsoleRegistry");
            dcr = createDevConsoleRegistry();
            if (dcr != null) {
                // install dev-console registry if it was discovered from classpath (camel-console)
                dcr.setCamelContext(this);
                setExtension(DevConsoleRegistry.class, dcr);
            }
            startupStepRecorder.endStep(step5);
        }

        // Call all registered trackers with this context
        // Note, this may use a partially constructed object
        CamelContextTracker.notifyContextCreated(this);

        // Setup type converter eager as its highly in use and should not be lazy initialized
        if (eagerCreateTypeConverter()) {
            StartupStep step5 = startupStepRecorder.beginStep(CamelContext.class, null, "Setting up TypeConverter");
            getOrCreateTypeConverter();
            startupStepRecorder.endStep(step5);
        }

        startupStepRecorder.endStep(step);

        buildTaken = watch.taken();
        LOG.debug("Apache Camel {} ({}) built in {}", getVersion(), getName(), TimeUtils.printDuration(buildTaken, true));
    }

    protected void resetBuildTime() {
        // needed by camel-quarkus
        buildTaken = 0;
    }

    @Override
    public void doInit() throws Exception {
        StopWatch watch = new StopWatch();

        vetoed = null;

        StartupStep step = startupStepRecorder.beginStep(CamelContext.class, null, "Init CamelContext");

        // init the route controller
        this.routeController = getRouteController();
        if (startupSummaryLevel == StartupSummaryLevel.Verbose) {
            // verbose startup should let route controller do the route startup logging
            if (routeController.getLoggingLevel().ordinal() < LoggingLevel.INFO.ordinal()) {
                routeController.setLoggingLevel(LoggingLevel.INFO);
            }
        }

        // init the shutdown strategy
        this.shutdownStrategy = getShutdownStrategy();
        if (startupSummaryLevel == StartupSummaryLevel.Verbose) {
            // verbose startup should let route controller do the route shutdown logging
            if (shutdownStrategy != null && shutdownStrategy.getLoggingLevel().ordinal() < LoggingLevel.INFO.ordinal()) {
                shutdownStrategy.setLoggingLevel(LoggingLevel.INFO);
            }
        }

        // optimize - before starting routes lets check if event notifications is possible
        eventNotificationApplicable = EventHelper.eventsApplicable(this);

        // ensure additional type converters is loaded (either if enabled or we should use package scanning from the base)
        boolean load = loadTypeConverters || getBasePackageScan() != null;
        if (load && typeConverter instanceof AnnotationScanTypeConverters) {
            StartupStep step2 = startupStepRecorder.beginStep(CamelContext.class, null, "Scan TypeConverters");
            ((AnnotationScanTypeConverters) typeConverter).scanTypeConverters();
            startupStepRecorder.endStep(step2);
        }

        // ensure additional health checks is loaded
        if (loadHealthChecks) {
            StartupStep step3 = startupStepRecorder.beginStep(CamelContext.class, null, "Scan HealthChecks");
            HealthCheckRegistry hcr = getExtension(HealthCheckRegistry.class);
            if (hcr != null) {
                hcr.loadHealthChecks();
            }
            startupStepRecorder.endStep(step3);
        }
        // ensure additional dev consoles is loaded
        if (devConsole) {
            StartupStep step4 = startupStepRecorder.beginStep(CamelContext.class, null, "Scan DevConsoles");
            DevConsoleRegistry dcr = getExtension(DevConsoleRegistry.class);
            if (dcr != null) {
                dcr.loadDevConsoles();
            }
            startupStepRecorder.endStep(step4);
        }

        // custom properties may use property placeholders so resolve those
        // early on
        if (globalOptions != null && !globalOptions.isEmpty()) {
            for (Map.Entry<String, String> entry : globalOptions.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null) {
                    String replaced = resolvePropertyPlaceholders(value);
                    if (!value.equals(replaced)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Camel property with key {} replaced value from {} -> {}", key, value, replaced);
                        }
                        entry.setValue(replaced);
                    }
                }
            }
        }

        forceLazyInitialization();

        addService(getManagementStrategy(), false);
        lifecycleStrategies.sort(OrderedComparator.get());
        ServiceHelper.initService(lifecycleStrategies);
        for (LifecycleStrategy strategy : lifecycleStrategies) {
            try {
                strategy.onContextInitializing(this);
            } catch (VetoCamelContextStartException e) {
                // okay we should not start Camel since it was vetoed
                LOG.warn("Lifecycle strategy {} vetoed initializing CamelContext ({}) due to: {}", strategy, getName(),
                        e.getMessage());
                throw e;
            } catch (Exception e) {
                LOG.warn("Lifecycle strategy {} failed initializing CamelContext ({}) due to: {}", strategy, getName(),
                        e.getMessage());
                throw e;
            }
        }

        // optimize - before starting routes lets check if event notifications is possible
        eventNotificationApplicable = EventHelper.eventsApplicable(this);

        // start notifiers as services
        for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
            if (notifier instanceof Service) {
                Service service = (Service) notifier;
                for (LifecycleStrategy strategy : lifecycleStrategies) {
                    strategy.onServiceAdd(getCamelContextReference(), service, null);
                }
            }
            ServiceHelper.initService(notifier);
        }

        // the event notifiers must be initialized before we can emit this event
        EventHelper.notifyCamelContextInitializing(this);

        // re-create endpoint registry as the cache size limit may be set after the constructor of this instance was called.
        // and we needed to create endpoints up-front as it may be accessed before this context is started
        endpoints = doAddService(createEndpointRegistry(endpoints));

        // optimised to not include runtimeEndpointRegistry unless startServices
        // is enabled or JMX statistics is in extended mode
        if (runtimeEndpointRegistry == null && getManagementStrategy() != null
                && getManagementStrategy().getManagementAgent() != null) {
            Boolean isEnabled = getManagementStrategy().getManagementAgent().getEndpointRuntimeStatisticsEnabled();
            boolean isExtended = getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
            // extended mode is either if we use Extended statistics level or
            // the option is explicit enabled
            boolean extended = isExtended || isEnabled != null && isEnabled;
            if (extended) {
                runtimeEndpointRegistry = new DefaultRuntimeEndpointRegistry();
            }
        }
        if (runtimeEndpointRegistry != null) {
            if (runtimeEndpointRegistry instanceof EventNotifier && getManagementStrategy() != null) {
                getManagementStrategy().addEventNotifier((EventNotifier) runtimeEndpointRegistry);
            }
            addService(runtimeEndpointRegistry, true, true);
        }

        bindDataFormats();

        // init components
        ServiceHelper.initService(components.values());

        // create route definitions from route templates if we have any sources
        for (RouteTemplateParameterSource source : getRegistry().findByType(RouteTemplateParameterSource.class)) {
            for (String routeId : source.routeIds()) {
                // do a defensive copy of the parameters
                Map<String, Object> map = new HashMap<>(source.parameters(routeId));
                Object templateId = map.remove(RouteTemplateParameterSource.TEMPLATE_ID);
                if (templateId == null) {
                    // use alternative style as well
                    templateId = map.remove("template-id");
                }
                final String id = templateId != null ? templateId.toString() : null;
                if (id == null) {
                    throw new IllegalArgumentException(
                            "RouteTemplateParameterSource with routeId: " + routeId + " has no templateId defined");
                }
                addRouteFromTemplate(routeId, id, map);
            }
        }

        // init the route definitions before the routes is started
        StartupStep subStep = startupStepRecorder.beginStep(CamelContext.class, getName(), "Init Routes");
        // the method is called start but at this point it will only initialize (as context is starting up)
        startRouteDefinitions();
        // this will init route definitions and populate as route services which we can then initialize now
        internalRouteStartupManager.doInitRoutes(routeServices);
        startupStepRecorder.endStep(subStep);

        if (!lifecycleStrategies.isEmpty()) {
            subStep = startupStepRecorder.beginStep(CamelContext.class, getName(), "LifecycleStrategy onContextInitialized");
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                try {
                    strategy.onContextInitialized(this);
                } catch (VetoCamelContextStartException e) {
                    // okay we should not start Camel since it was vetoed
                    LOG.warn("Lifecycle strategy {} vetoed initializing CamelContext ({}) due to: {}", strategy, getName(),
                            e.getMessage());
                    throw e;
                } catch (Exception e) {
                    LOG.warn("Lifecycle strategy {} failed initializing CamelContext ({}) due to: {}", strategy, getName(),
                            e.getMessage());
                    throw e;
                }
            }
            startupStepRecorder.endStep(subStep);
        }

        EventHelper.notifyCamelContextInitialized(this);

        startupStepRecorder.endStep(step);

        initTaken = watch.taken();
        LOG.debug("Apache Camel {} ({}) initialized in {}", getVersion(), getName(), TimeUtils.printDuration(initTaken, true));
    }

    @Override
    protected void doStart() throws Exception {
        if (firstStartDone) {
            // its not good practice resetting a camel context
            LOG.warn("Starting CamelContext: {} after the context has been stopped is not recommended", getName());
        }
        StartupStep step = startupStepRecorder.beginStep(CamelContext.class, getName(), "Start CamelContext");

        try {
            doStartContext();
        } catch (Exception e) {
            // fire event that we failed to start
            EventHelper.notifyCamelContextStartupFailed(AbstractCamelContext.this, e);
            // rethrow cause
            throw e;
        }

        startupStepRecorder.endStep(step);

        // if we should only record the startup process then stop it right after started
        if (startupStepRecorder.getStartupRecorderDuration() < 0) {
            startupStepRecorder.stop();
        }
    }

    protected void doStartContext() throws Exception {
        LOG.info("Apache Camel {} ({}) is starting", getVersion(), getName());

        vetoed = null;
        startDate = System.currentTimeMillis();
        stopWatch.restart();

        // Start the route controller
        startService(this.routeController);

        doNotStartRoutesOnFirstStart = !firstStartDone && !isAutoStartup();

        // if the context was configured with auto startup = false, and we
        // are already started,
        // then we may need to start the routes on the 2nd start call
        if (firstStartDone && !isAutoStartup() && isStarted()) {
            // invoke this logic to warm up the routes and if possible also
            // start the routes
            try {
                internalRouteStartupManager.doStartOrResumeRoutes(routeServices, true, true, false, true);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        // super will invoke doStart which will prepare internal services
        // and start routes etc.
        try {
            firstStartDone = true;
            doStartCamel();
        } catch (Exception e) {
            VetoCamelContextStartException veto = ObjectHelper.getException(VetoCamelContextStartException.class, e);
            if (veto != null) {
                // mark we veto against starting Camel
                vetoed = veto;
                return;
            } else {
                LOG.error("Error starting CamelContext (" + getName() + ") due to exception thrown: " + e.getMessage(), e);
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        // duplicate components in use?
        logDuplicateComponents();

        // log startup summary
        logStartSummary();

        // now Camel has been started/bootstrap is complete, then run cleanup to help free up memory etc
        for (BootstrapCloseable bootstrap : bootstraps) {
            try {
                bootstrap.close();
            } catch (Exception e) {
                LOG.warn("Error during closing bootstrap. This exception is ignored.", e);
            }
        }
        bootstraps.clear();

        if (adapt(ExtendedCamelContext.class).getExchangeFactory().isPooled()) {
            LOG.info(
                    "Pooled mode enabled. Camel pools and reuses objects to reduce JVM object allocations. The pool capacity is: {} elements.",
                    adapt(ExtendedCamelContext.class).getExchangeFactory().getCapacity());
        }
        if (isLightweight()) {
            LOG.info("Lightweight mode enabled. Performing optimizations and memory reduction.");
            ReifierStrategy.clearReifiers();
            adapt(ExtendedCamelContext.class).disposeModel();
        }
    }

    protected void logDuplicateComponents() {
        // output how many instances of the same component class are in use, as multiple instances is potential a mistake
        if (LOG.isInfoEnabled()) {
            Map<Class<?>, Set<String>> counters = new LinkedHashMap<>();
            // use TreeSet to sort the names
            Set<String> cnames = new TreeSet<>(getComponentNames());
            for (String sourceName : cnames) {
                Class<?> source = getComponent(sourceName).getClass();
                if (!counters.containsKey(source)) {
                    for (String targetName : cnames) {
                        Class<?> target = getComponent(targetName).getClass();
                        if (source == target) {
                            Set<String> names = counters.computeIfAbsent(source, k -> new TreeSet<>());
                            names.add(targetName);
                        }
                    }
                }
            }
            for (Map.Entry<Class<?>, Set<String>> entry : counters.entrySet()) {
                int count = entry.getValue().size();
                if (count > 1) {
                    String fqn = entry.getKey().getName();
                    String names = String.join(", ", entry.getValue());
                    LOG.info("Using {} instances of same component class: {} with names: {}", count,
                            fqn, names);
                }
            }
        }

    }

    protected void logStartSummary() {
        // supervising route controller should do their own startup log summary
        boolean supervised = getRouteController().isSupervising();
        if (!supervised && startupSummaryLevel != StartupSummaryLevel.Oneline && startupSummaryLevel != StartupSummaryLevel.Off
                && LOG.isInfoEnabled()) {
            int started = 0;
            int total = 0;
            int disabled = 0;
            List<String> lines = new ArrayList<>();
            List<String> configs = new ArrayList<>();
            routeStartupOrder.sort(Comparator.comparingInt(RouteStartupOrder::getStartupOrder));
            for (RouteStartupOrder order : routeStartupOrder) {
                total++;
                String id = order.getRoute().getRouteId();
                String status = getRouteStatus(id).name();
                if (ServiceStatus.Started.name().equals(status)) {
                    started++;
                }
                // use basic endpoint uri to not log verbose details or potential sensitive data
                String uri = order.getRoute().getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);
                String loc = order.getRoute().getSourceLocationShort();
                if (startupSummaryLevel == StartupSummaryLevel.Verbose && loc != null) {
                    lines.add(String.format("    %s %s (%s) (source: %s)", status, id, uri, loc));
                } else {
                    lines.add(String.format("    %s %s (%s)", status, id, uri));
                }
                String cid = order.getRoute().getConfigurationId();
                if (cid != null) {
                    configs.add(String.format("    %s (%s)", id, cid));
                }
            }
            for (Route route : routes) {
                if (!route.isAutoStartup()) {
                    total++;
                    disabled++;
                    String id = route.getRouteId();
                    String status = getRouteStatus(id).name();
                    if (ServiceStatus.Stopped.name().equals(status)) {
                        status = "Disabled";
                    }
                    // use basic endpoint uri to not log verbose details or potential sensitive data
                    String uri = route.getEndpoint().getEndpointBaseUri();
                    uri = URISupport.sanitizeUri(uri);
                    String loc = route.getSourceLocationShort();
                    if (startupSummaryLevel == StartupSummaryLevel.Verbose && loc != null) {
                        lines.add(String.format("    %s %s (%s) (source: %s)", status, id, uri, loc));
                    } else {
                        lines.add(String.format("    %s %s (%s)", status, id, uri));
                    }

                    String cid = route.getConfigurationId();
                    if (cid != null) {
                        configs.add(String.format("    %s (%s)", id, cid));
                    }
                }
            }
            if (disabled > 0) {
                LOG.info("Routes startup (total:{} started:{} disabled:{})", total, started, disabled);
            } else if (total != started) {
                LOG.info("Routes startup (total:{} started:{})", total, started);
            } else {
                LOG.info("Routes startup (started:{})", started);
            }
            // if we are default/verbose then log each route line
            if (startupSummaryLevel == StartupSummaryLevel.Default || startupSummaryLevel == StartupSummaryLevel.Verbose) {
                for (String line : lines) {
                    LOG.info(line);
                }
                if (startupSummaryLevel == StartupSummaryLevel.Verbose) {
                    LOG.info("Routes configuration:");
                    for (String line : configs) {
                        LOG.info(line);
                    }
                }
            }
        }

        if (startupSummaryLevel != StartupSummaryLevel.Off && LOG.isInfoEnabled()) {
            long taken = stopWatch.taken();
            long max = buildTaken + initTaken + taken;
            String total = TimeUtils.printDuration(max, true);
            String start = TimeUtils.printDuration(taken, true);
            String init = TimeUtils.printDuration(initTaken, true);
            String built = TimeUtils.printDuration(buildTaken, true);
            String jvm = logJvmUptime ? getJvmUptime() : null;
            if (jvm != null) {
                LOG.info("Apache Camel {} ({}) started in {} (build:{} init:{} start:{} JVM-uptime:{})", getVersion(),
                        getName(), total, built,
                        init, start, jvm);
            } else {
                LOG.info("Apache Camel {} ({}) started in {} (build:{} init:{} start:{})", getVersion(), getName(), total,
                        built,
                        init,
                        start);
            }
        }
    }

    protected void doStartCamel() throws Exception {
        if (!adapt(ExtendedCamelContext.class).getBeanPostProcessor().isEnabled()) {
            LOG.info("BeanPostProcessor is disabled. Dependency injection of Camel annotations in beans is not supported.");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Using ClassResolver={}, PackageScanClassResolver={}, ApplicationContextClassLoader={}, RouteController={}",
                    getClassResolver(),
                    getPackageScanClassResolver(), getApplicationContextClassLoader(), getRouteController());
        }
        if (isStreamCaching()) {
            // stream caching is default enabled so lets report if it has been disabled
            LOG.debug("StreamCaching is disabled on CamelContext: {}", getName());
        }
        if (isBacklogTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            LOG.debug("Backlog Tracing is enabled on CamelContext: {}", getName());
        }
        if (isTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            LOG.info("Tracing is enabled on CamelContext: {}", getName());
        }
        if (isUseMDCLogging()) {
            // log if MDC has been enabled
            String pattern = getMDCLoggingKeysPattern();
            if (pattern != null) {
                LOG.info("MDC logging (keys-pattern: {}) is enabled on CamelContext: {}", pattern, getName());
            } else {
                LOG.info("MDC logging is enabled on CamelContext: {}", getName());
            }
        }
        if (getDelayer() != null && getDelayer() > 0) {
            LOG.info("Delayer is enabled with: {} ms. on CamelContext: {}", getDelayer(), getName());
        }

        // start management strategy before lifecycles are started
        ManagementStrategy managementStrategy = getManagementStrategy();
        startService(managementStrategy);

        // start lifecycle strategies
        if (!lifecycleStrategies.isEmpty()) {
            StartupStep subStep
                    = startupStepRecorder.beginStep(CamelContext.class, getName(), "LifecycleStrategy onContextStarting");
            startServices(lifecycleStrategies);
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                try {
                    strategy.onContextStarting(this);
                    strategy.onContextStart(this);
                } catch (VetoCamelContextStartException e) {
                    // okay we should not start Camel since it was vetoed
                    LOG.warn("Lifecycle strategy {} vetoed starting CamelContext ({}) due to: {}", strategy, getName(),
                            e.getMessage());
                    throw e;
                } catch (Exception e) {
                    LOG.warn("Lifecycle strategy {} failed starting CamelContext ({}) due to: {}", strategy, getName(),
                            e.getMessage());
                    throw e;
                }
            }
            startupStepRecorder.endStep(subStep);
        }

        // ensure components are started
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            StartupStep step = startupStepRecorder.beginStep(Component.class, entry.getKey(), "Start Component");
            try {
                startService(entry.getValue());
            } catch (Exception e) {
                throw new FailedToStartComponentException(entry.getKey(), e.getMessage(), e);
            } finally {
                startupStepRecorder.endStep(step);
            }
        }

        if (!startupListeners.isEmpty()) {
            StartupStep subStep
                    = startupStepRecorder.beginStep(CamelContext.class, getName(), "StartupListener onCamelContextStarting");
            // sort the startup listeners so they are started in the right order
            startupListeners.sort(OrderedComparator.get());
            // now call the startup listeners where the routes has been warmed up
            // (only the actual route consumer has not yet been started)
            for (StartupListener startup : startupListeners) {
                startup.onCamelContextStarting(getCamelContextReference(), isStarted());
            }
            startupStepRecorder.endStep(subStep);
        }

        // start notifiers as services
        for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
            if (notifier instanceof Service) {
                startService((Service) notifier);
            }
        }

        // must let some bootstrap service be started before we can notify the starting event
        EventHelper.notifyCamelContextStarting(this);

        if (isUseDataType()) {
            // log if DataType has been enabled
            LOG.debug("Message DataType is enabled on CamelContext: {}", getName());
        }

        // is there any stream caching enabled then log an info about this and
        // its limit of spooling to disk, so people is aware of this
        if (isStreamCachingInUse()) {
            // stream caching is in use so enable the strategy
            getStreamCachingStrategy().setEnabled(true);
        } else {
            // log if stream caching is not in use as this can help people to
            // enable it if they use streams
            LOG.debug("StreamCaching is not in use. If using streams then it's recommended to enable stream caching."
                      + " See more details at http://camel.apache.org/stream-caching.html");
        }

        if (isAllowUseOriginalMessage()) {
            LOG.debug("AllowUseOriginalMessage enabled because UseOriginalMessage is in use");
        }

        LOG.debug("Using HeadersMapFactory: {}", getHeadersMapFactory());
        if (isCaseInsensitiveHeaders() && !getHeadersMapFactory().isCaseInsensitive()) {
            LOG.info(
                    "HeadersMapFactory: {} is case-sensitive which can cause problems for protocols such as HTTP based, which rely on case-insensitive headers.",
                    getHeadersMapFactory());
        } else if (!isCaseInsensitiveHeaders()) {
            // notify user that the headers are sensitive which can be a problem
            LOG.info(
                    "Case-insensitive headers is not in use. This can cause problems for protocols such as HTTP based, which rely on case-insensitive headers.");
        }

        // lets log at INFO level if we are not using the default reactive executor
        if (!getReactiveExecutor().getClass().getSimpleName().equals("DefaultReactiveExecutor")) {
            LOG.info("Using ReactiveExecutor: {}", getReactiveExecutor());
        } else {
            LOG.debug("Using ReactiveExecutor: {}", getReactiveExecutor());
        }

        // lets log at INFO level if we are not using the default thread pool factory
        if (!getExecutorServiceManager().getThreadPoolFactory().getClass().getSimpleName().equals("DefaultThreadPoolFactory")) {
            LOG.info("Using ThreadPoolFactory: {}", getExecutorServiceManager().getThreadPoolFactory());
        } else {
            LOG.debug("Using ThreadPoolFactory: {}", getExecutorServiceManager().getThreadPoolFactory());
        }

        HealthCheckRegistry hcr = getExtension(HealthCheckRegistry.class);
        if (hcr != null && hcr.isEnabled()) {
            LOG.debug("Using HealthCheck: {}", hcr.getId());
        }

        // start routes
        if (doNotStartRoutesOnFirstStart) {
            LOG.debug("Skip starting routes as CamelContext has been configured with autoStartup=false");
        }

        if (isDumpRoutes() != null && isDumpRoutes()) {
            doDumpRoutes();
        }

        if (!getRouteController().isSupervising()) {
            // invoke this logic to warmup the routes and if possible also start the routes (using default route controller)
            StartupStep subStep = startupStepRecorder.beginStep(CamelContext.class, getName(), "Start Routes");
            EventHelper.notifyCamelContextRoutesStarting(this);
            internalRouteStartupManager.doStartOrResumeRoutes(routeServices, true, !doNotStartRoutesOnFirstStart, false, true);
            EventHelper.notifyCamelContextRoutesStarted(this);
            startupStepRecorder.endStep(subStep);
        }

        long cacheCounter = beanIntrospection != null ? beanIntrospection.getCachedClassesCounter() : 0;
        if (cacheCounter > 0) {
            LOG.debug("Clearing BeanIntrospection cache with {} objects using during starting Camel", cacheCounter);
            beanIntrospection.clearCache();
        }
        long invokedCounter = beanIntrospection != null ? beanIntrospection.getInvokedCounter() : 0;
        if (invokedCounter > 0) {
            LOG.debug("BeanIntrospection invoked {} times during starting Camel", invokedCounter);
        }
        // starting will continue in the start method
    }

    @Override
    protected void doStop() throws Exception {
        stopWatch.restart();

        if (startupSummaryLevel != StartupSummaryLevel.Oneline && startupSummaryLevel != StartupSummaryLevel.Off) {
            if (shutdownStrategy != null && shutdownStrategy.getTimeUnit() != null) {
                long timeout = shutdownStrategy.getTimeUnit().toMillis(shutdownStrategy.getTimeout());
                // only use precise print duration if timeout is shorter than 10 seconds
                String to = TimeUtils.printDuration(timeout, timeout < 10000);
                LOG.info("Apache Camel {} ({}) is shutting down (timeout:{})", getVersion(), getName(), to);
            } else {
                LOG.info("Apache Camel {} ({}) is shutting down", getVersion(), getName());
            }
        }

        EventHelper.notifyCamelContextStopping(this);
        EventHelper.notifyCamelContextRoutesStopping(this);

        // Stop the route controller
        ServiceHelper.stopAndShutdownService(this.routeController);

        // stop route inputs in the same order as they was started so we stop
        // the very first inputs first
        try {
            // force shutting down routes as they may otherwise cause shutdown to hang
            if (shutdownStrategy != null) {
                shutdownStrategy.shutdownForced(this, getRouteStartupOrder());
            }
        } catch (Throwable e) {
            LOG.warn("Error occurred while shutting down routes. This exception will be ignored.", e);
        }

        // shutdown await manager to trigger interrupt of blocked threads to
        // attempt to free these threads graceful
        shutdownServices(asyncProcessorAwaitManager);

        // we need also to include routes which failed to start to ensure all resources get stopped when stopping Camel
        for (RouteService routeService : routeServices.values()) {
            boolean found = routeStartupOrder.stream().anyMatch(o -> o.getRoute().getId().equals(routeService.getId()));
            if (!found) {
                LOG.debug("Route: {} which failed to startup will be stopped", routeService.getId());
                routeStartupOrder.add(internalRouteStartupManager.doPrepareRouteToBeStarted(routeService));
            }
        }

        routeStartupOrder.sort(Comparator.comparingInt(RouteStartupOrder::getStartupOrder).reversed());
        List<RouteService> list = new ArrayList<>();
        for (RouteStartupOrder startupOrder : routeStartupOrder) {
            DefaultRouteStartupOrder order = (DefaultRouteStartupOrder) startupOrder;
            RouteService routeService = order.getRouteService();
            list.add(routeService);
        }
        shutdownServices(list, false);

        if (startupSummaryLevel != StartupSummaryLevel.Oneline
                && startupSummaryLevel != StartupSummaryLevel.Off) {
            logRouteStopSummary(LoggingLevel.INFO);
        }

        // do not clear route services or startup listeners as we can start
        // Camel again and get the route back as before
        routeStartupOrder.clear();

        EventHelper.notifyCamelContextRoutesStopped(this);

        // but clear any suspend routes
        suspendedRouteServices.clear();

        // stop consumers from the services to close first, such as POJO
        // consumer (eg @Consumer)
        // which we need to stop after the routes, as a POJO consumer is
        // essentially a route also
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

        // shutdown services as late as possible (except type converters as they may be needed during the remainder of the stopping)
        shutdownServices(servicesToStop);
        servicesToStop.clear();

        try {
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onContextStopped(this);
            }
        } catch (Throwable e) {
            LOG.warn("Error occurred while stopping lifecycle strategies. This exception will be ignored.", e);
        }

        // must notify that we are stopped before stopping the management strategy
        EventHelper.notifyCamelContextStopped(this);

        // stop the notifier service
        if (getManagementStrategy() != null) {
            for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
                shutdownServices(notifier);
            }
        }

        // shutdown management and lifecycle after all other services
        shutdownServices(managementStrategy);
        shutdownServices(managementMBeanAssembler);
        shutdownServices(lifecycleStrategies);
        // do not clear lifecycleStrategies as we can start Camel again and get
        // the route back as before

        // shutdown executor service, reactive executor last
        shutdownServices(executorServiceManager);
        shutdownServices(reactiveExecutor);

        // shutdown type converter and registry as late as possible
        ServiceHelper.stopService(typeConverter);
        ServiceHelper.stopService(typeConverterRegistry);
        ServiceHelper.stopService(registry);

        // stop the lazy created so they can be re-created on restart
        forceStopLazyInitialization();

        if (startupSummaryLevel != StartupSummaryLevel.Off) {
            if (LOG.isInfoEnabled()) {
                String taken = TimeUtils.printDuration(stopWatch.taken(), true);
                String jvm = logJvmUptime ? getJvmUptime() : null;
                if (jvm != null) {
                    LOG.info("Apache Camel {} ({}) shutdown in {} (uptime:{} JVM-uptime:{})", getVersion(), getName(), taken,
                            getUptime(), jvm);
                } else {
                    LOG.info("Apache Camel {} ({}) shutdown in {} (uptime:{})", getVersion(), getName(), taken, getUptime());
                }
            }
        }

        // ensure any recorder is stopped in case it was kept running
        startupStepRecorder.stop();

        // and clear start date
        startDate = 0;

        // Call all registered trackers with this context
        // Note, this may use a partially constructed object
        CamelContextTracker.notifyContextDestroyed(this);

        firstStartDone = true;
    }

    @Override
    protected void doFail(Exception e) {
        super.doFail(e);
        // reset flag in case of startup fail as we want to be able to allow to start again
        firstStartDone = false;
    }

    protected void doDumpRoutes() {
        // noop
    }

    protected void logRouteStopSummary(LoggingLevel loggingLevel) {
        CamelLogger logger = new CamelLogger(LOG, loggingLevel);
        if (logger.shouldLog()) {
            int total = 0;
            int stopped = 0;
            int forced = 0;
            List<String> lines = new ArrayList<>();

            if (shutdownStrategy != null && shutdownStrategy.isShutdownRoutesInReverseOrder()) {
                routeStartupOrder.sort(Comparator.comparingInt(RouteStartupOrder::getStartupOrder).reversed());
            } else {
                routeStartupOrder.sort(Comparator.comparingInt(RouteStartupOrder::getStartupOrder));
            }
            for (RouteStartupOrder order : routeStartupOrder) {
                total++;
                String id = order.getRoute().getRouteId();
                String status = getRouteStatus(id).name();
                if (ServiceStatus.Stopped.name().equals(status)) {
                    stopped++;
                }
                if (order.getRoute().getProperties().containsKey("forcedShutdown")) {
                    forced++;
                    status = "Forced stopped";
                }
                // use basic endpoint uri to not log verbose details or potential sensitive data
                String uri = order.getRoute().getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);
                lines.add(String.format("    %s %s (%s)", status, id, uri));
            }
            if (forced > 0) {
                logger.log(String.format("Routes stopped (total:%s stopped:%s forced:%s)", total, stopped, forced));
            } else if (total != stopped) {
                logger.log(String.format("Routes stopped (total:%s stopped:%s)", total, stopped));
            } else {
                logger.log(String.format("Routes stopped (stopped:%s)", stopped));
            }
            // if we are default/verbose then log each route line
            if (startupSummaryLevel == StartupSummaryLevel.Default || startupSummaryLevel == StartupSummaryLevel.Verbose) {
                for (String line : lines) {
                    logger.log(line);
                }
            }
        }
    }

    public void startRouteDefinitions() throws Exception {
    }

    protected boolean isStreamCachingInUse() throws Exception {
        return isStreamCaching();
    }

    protected void bindDataFormats() throws Exception {
    }

    protected boolean routeSupportsSuspension(String routeId) {
        RouteService routeService = routeServices.get(routeId);
        if (routeService != null) {
            return routeService.getRoute().supportsSuspension();
        }
        return false;
    }

    private void shutdownServices(Object service) {
        // do not rethrow exception as we want to keep shutting down in case of
        // problems

        // allow us to do custom work before delegating to service helper
        try {
            if (service instanceof Service) {
                ServiceHelper.stopAndShutdownService(service);
            } else if (service instanceof Collection) {
                ServiceHelper.stopAndShutdownServices((Collection<?>) service);
            }
        } catch (Throwable e) {
            LOG.warn("Error occurred while shutting down service: " + service + ". This exception will be ignored.", e);
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

    void startService(Service service) throws Exception {
        // and register startup aware so they can be notified when
        // camel context has been started
        if (service instanceof StartupListener) {
            StartupListener listener = (StartupListener) service;
            addStartupListener(listener);
        }

        CamelContextAware.trySetCamelContext(service, getCamelContextReference());
        ServiceHelper.startService(service);
    }

    private void startServices(Collection<?> services) throws Exception {
        for (Object element : services) {
            if (element instanceof Service) {
                startService((Service) element);
            }
        }
    }

    private void stopServices(Object service) throws Exception {
        // allow us to do custom work before delegating to service helper
        try {
            ServiceHelper.stopService(service);
        } catch (Exception e) {
            // fire event
            EventHelper.notifyServiceStopFailure(getCamelContextReference(), service, e);
            // rethrow to signal error with stopping
            throw e;
        }
    }

    /**
     * Starts the given route service
     */
    public synchronized void startRouteService(RouteService routeService, boolean addingRoutes) throws Exception {
        // we may already be starting routes so remember this, so we can unset
        // accordingly in finally block
        boolean alreadyStartingRoutes = isStartingRoutes();
        if (!alreadyStartingRoutes) {
            setStartingRoutes(true);
        }

        try {
            // the route service could have been suspended, and if so then
            // resume it instead
            if (routeService.getStatus().isSuspended()) {
                resumeRouteService(routeService);
            } else {
                // start the route service
                routeServices.put(routeService.getId(), routeService);
                if (shouldStartRoutes()) {
                    StartupStep step
                            = startupStepRecorder.beginStep(Route.class, routeService.getId(), "Start Route Services");
                    // this method will log the routes being started
                    internalRouteStartupManager.safelyStartRouteServices(true, true, true, false, addingRoutes, routeService);
                    // start route services if it was configured to auto startup
                    // and we are not adding routes
                    boolean autoStartup = routeService.isAutoStartup();
                    if (!addingRoutes || autoStartup) {
                        // start the route since auto start is enabled or we are
                        // starting a route (not adding new routes)
                        routeService.start();
                    }
                    startupStepRecorder.endStep(step);
                }
            }
        } finally {
            if (!alreadyStartingRoutes) {
                setStartingRoutes(false);
            }
        }
    }

    /**
     * Resumes the given route service
     */
    protected synchronized void resumeRouteService(RouteService routeService) throws Exception {
        // the route service could have been stopped, and if so then start it
        // instead
        if (!routeService.getStatus().isSuspended()) {
            startRouteService(routeService, false);
        } else {
            // resume the route service
            if (shouldStartRoutes()) {
                // this method will log the routes being started
                internalRouteStartupManager.safelyStartRouteServices(true, false, true, true, false, routeService);
                // must resume route service as well
                routeService.resume();
            }
        }
    }

    protected synchronized void stopRouteService(RouteService routeService, boolean removingRoutes, LoggingLevel loggingLevel)
            throws Exception {
        routeService.setRemovingRoutes(removingRoutes);
        stopRouteService(routeService, loggingLevel);
    }

    protected void logRouteState(Route route, String state, LoggingLevel loggingLevel) {
        CamelLogger logger = new CamelLogger(LOG, loggingLevel);
        if (logger.shouldLog()) {
            if (route.getConsumer() != null) {
                String id = route.getId();
                String uri = route.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);
                String line = String.format("%s %s (%s)", state, id, uri);
                logger.log(line);
            } else {
                String id = route.getId();
                String line = String.format("%s %s", state, id);
                logger.log(line);
            }
        }
    }

    protected synchronized void stopRouteService(RouteService routeService, LoggingLevel loggingLevel) throws Exception {
        routeService.stop();
        logRouteState(routeService.getRoute(), "Stopped", loggingLevel);
    }

    protected synchronized void shutdownRouteService(RouteService routeService) throws Exception {
        shutdownRouteService(routeService, LoggingLevel.INFO);
    }

    protected synchronized void shutdownRouteService(RouteService routeService, LoggingLevel loggingLevel) throws Exception {
        routeService.shutdown();
        logRouteState(routeService.getRoute(), "Shutdown", loggingLevel);
    }

    protected synchronized void suspendRouteService(RouteService routeService) throws Exception {
        routeService.setRemovingRoutes(false);
        routeService.suspend();
        logRouteState(routeService.getRoute(), "Suspended", LoggingLevel.INFO);
    }

    /**
     * Force some lazy initialization to occur upfront before we start any components and create routes
     */
    protected void forceLazyInitialization() {
        StartupStep step = startupStepRecorder.beginStep(CamelContext.class, getName(), "Start Mandatory Services");
        initEagerMandatoryServices();
        startupStepRecorder.endStep(step);

        if (initialization != Initialization.Lazy) {
            step = startupStepRecorder.beginStep(CamelContext.class, getName(), "Start Standard Services");
            doStartStandardServices();
            startupStepRecorder.endStep(step);

            if (initialization == Initialization.Eager) {
                step = startupStepRecorder.beginStep(CamelContext.class, getName(), "Start Eager Services");
                doStartEagerServices();
                startupStepRecorder.endStep(step);
            }
        }
    }

    /**
     * Initializes eager some mandatory services which needs to warmup and be ready as this helps optimize Camel at
     * runtime.
     */
    protected void initEagerMandatoryServices() {
        if (headersMapFactory == null) {
            // we want headers map to be created as then JVM can optimize using it as we use it per exchange/message
            synchronized (lock) {
                if (headersMapFactory == null) {
                    if (isCaseInsensitiveHeaders()) {
                        // use factory to find the map factory to use
                        setHeadersMapFactory(createHeadersMapFactory());
                    } else {
                        // case sensitive so we can use hash map
                        setHeadersMapFactory(new HashMapHeadersMapFactory());
                    }
                }
            }
        }
    }

    protected void doStartStandardServices() {
        getVersion();
        getClassResolver();
        getRegistry();
        getBootstrapFactoryFinder();
        getFactoryFinderResolver();
        getTypeConverterRegistry();
        getInjector();
        getDefaultFactoryFinder();
        getBootstrapConfigurerResolver();
        getConfigurerResolver();
        getPropertiesComponent();

        getLanguageResolver();
        getComponentResolver();
        getComponentNameResolver();
        getDataFormatResolver();
        getHealthCheckResolver();

        getExecutorServiceManager();
        getExchangeFactoryManager();
        getExchangeFactory();
        getShutdownStrategy();
        getUuidGenerator();

        if (isTypeConverterStatisticsEnabled()) {
            getTypeConverterRegistry().getStatistics().setStatisticsEnabled(isTypeConverterStatisticsEnabled());
        }

        // resolve simple language to initialize it
        resolveLanguage("simple");
    }

    protected void doStartEagerServices() {
        getPackageScanClassResolver();
        getInflightRepository();
        getAsyncProcessorAwaitManager();
        getReactiveExecutor();
        getBeanIntrospection();
        getUriFactoryResolver();
        getXMLRoutesDefinitionLoader();
        getModelToXMLDumper();
        getNodeIdFactory();
        getModelJAXBContextFactory();
        getUnitOfWorkFactory();
        getRouteController();
        getRoutesLoader();
        getResourceLoader();

        try {
            getRestRegistryFactory();
        } catch (IllegalArgumentException e) {
            // ignore in case camel-rest is not on the classpath
        }
        try {
            getProcessorFactory();
            getInternalProcessorFactory();
        } catch (IllegalArgumentException e) {
            // ignore in case camel-core-processor is not on the classpath
        }
        try {
            getBeanProxyFactory();
            getBeanProcessorFactory();
        } catch (IllegalArgumentException e) {
            // ignore in case camel-bean is not on the classpath
        }
        getBeanPostProcessor();
    }

    /**
     * Force clear lazy initialization so they can be re-created on restart
     */
    protected void forceStopLazyInitialization() {
        injector = null;
        languageResolver = null;
        dataFormatResolver = null;
        componentResolver = null;
        typeConverterRegistry = null;
        typeConverter = null;
        reactiveExecutor = null;
        asyncProcessorAwaitManager = null;
        exchangeFactory = null;
        exchangeFactoryManager = null;
        processorExchangeFactory = null;
        registry = null;
    }

    /**
     * A pluggable strategy to allow an endpoint to be created without requiring a component to be its factory, such as
     * for looking up the URI inside some {@link Registry}
     *
     * @param  uri the uri for the endpoint to be created
     * @return     the newly created endpoint or null if it could not be resolved
     */
    protected Endpoint createEndpoint(String uri) {
        Object value = getRegistry().lookupByName(uri);
        if (value instanceof Endpoint) {
            return (Endpoint) value;
        } else if (value instanceof Processor) {
            return new ProcessorEndpoint(uri, getCamelContextReference(), (Processor) value);
        } else if (value != null) {
            return convertBeanToEndpoint(uri, value);
        }
        return null;
    }

    /**
     * Strategy method for attempting to convert the bean from a {@link Registry} to an endpoint using some kind of
     * transformation or wrapper
     *
     * @param  uri  the uri for the endpoint (and name in the registry)
     * @param  bean the bean to be converted to an endpoint, which will be not null
     * @return      a new endpoint
     */
    protected Endpoint convertBeanToEndpoint(String uri, Object bean) {
        throw new IllegalArgumentException("uri: " + uri + " bean: " + bean + " could not be converted to an Endpoint");
    }

    /**
     * Should we start newly added routes?
     */
    protected boolean shouldStartRoutes() {
        return isStarted() && !isStarting();
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        return globalOptions;
    }

    @Override
    public void setGlobalOptions(Map<String, String> globalOptions) {
        this.globalOptions = globalOptions;
    }

    @Override
    public FactoryFinder getDefaultFactoryFinder() {
        return getFactoryFinder(FactoryFinder.DEFAULT_PATH);
    }

    @Override
    public ConfigurerResolver getBootstrapConfigurerResolver() {
        if (bootstrapConfigurerResolver == null) {
            synchronized (lock) {
                if (bootstrapConfigurerResolver == null) {
                    bootstrapConfigurerResolver = new BootstrapConfigurerResolver(
                            getFactoryFinderResolver().resolveBootstrapFactoryFinder(getClassResolver(),
                                    ConfigurerResolver.RESOURCE_PATH));
                }
            }
        }
        return bootstrapConfigurerResolver;
    }

    @Override
    public void setBootstrapConfigurerResolver(ConfigurerResolver configurerResolver) {
        this.bootstrapConfigurerResolver = configurerResolver;
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder() {
        if (bootstrapFactoryFinder == null) {
            synchronized (lock) {
                if (bootstrapFactoryFinder == null) {
                    bootstrapFactoryFinder = getFactoryFinderResolver().resolveBootstrapFactoryFinder(getClassResolver());
                }
            }
        }
        return bootstrapFactoryFinder;
    }

    @Override
    public void setBootstrapFactoryFinder(FactoryFinder factoryFinder) {
        this.bootstrapFactoryFinder = factoryFinder;
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder(String path) {
        return bootstrapFactories.computeIfAbsent(path, this::createBootstrapFactoryFinder);
    }

    protected FactoryFinder createBootstrapFactoryFinder(String path) {
        return getFactoryFinderResolver().resolveBootstrapFactoryFinder(getClassResolver(), path);
    }

    @Override
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

    @Override
    public void setFactoryFinderResolver(FactoryFinderResolver factoryFinderResolver) {
        this.factoryFinderResolver = doAddService(factoryFinderResolver);
    }

    @Override
    public FactoryFinder getFactoryFinder(String path) {
        return factories.computeIfAbsent(path, this::createFactoryFinder);
    }

    protected FactoryFinder createFactoryFinder(String path) {
        return getFactoryFinderResolver().resolveFactoryFinder(getClassResolver(), path);
    }

    @Override
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

    @Override
    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = doAddService(classResolver);
    }

    @Override
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

    @Override
    public void setPackageScanClassResolver(PackageScanClassResolver packageScanClassResolver) {
        this.packageScanClassResolver = doAddService(packageScanClassResolver);
    }

    @Override
    public PackageScanResourceResolver getPackageScanResourceResolver() {
        if (packageScanResourceResolver == null) {
            synchronized (lock) {
                if (packageScanResourceResolver == null) {
                    setPackageScanResourceResolver(createPackageScanResourceResolver());
                }
            }
        }
        return packageScanResourceResolver;
    }

    @Override
    public void setPackageScanResourceResolver(PackageScanResourceResolver packageScanResourceResolver) {
        this.packageScanResourceResolver = doAddService(packageScanResourceResolver);
    }

    @Override
    public Set<String> getComponentNames() {
        return Collections.unmodifiableSet(components.keySet());
    }

    @Override
    public Set<String> getLanguageNames() {
        return Collections.unmodifiableSet(languages.keySet());
    }

    @Override
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

    @Override
    public void setModelJAXBContextFactory(final ModelJAXBContextFactory modelJAXBContextFactory) {
        this.modelJAXBContextFactory = doAddService(modelJAXBContextFactory);
    }

    @Override
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

    @Override
    public void setNodeIdFactory(NodeIdFactory idFactory) {
        this.nodeIdFactory = doAddService(idFactory);
    }

    @Override
    public ModelineFactory getModelineFactory() {
        if (modelineFactory == null) {
            synchronized (lock) {
                if (modelineFactory == null) {
                    setModelineFactory(createModelineFactory());
                }
            }
        }
        return modelineFactory;
    }

    @Override
    public void setModelineFactory(ModelineFactory modelineFactory) {
        this.modelineFactory = doAddService(modelineFactory);
    }

    @Override
    public ManagementStrategy getManagementStrategy() {
        return managementStrategy;
    }

    @Override
    public void setManagementStrategy(ManagementStrategy managementStrategy) {
        this.managementStrategy = managementStrategy;
    }

    @Override
    public void disableJMX() {
        if (isNew()) {
            disableJMX = true;
        } else if (isInit() || isBuild()) {
            disableJMX = true;
            // we are still in initializing mode, so we can disable JMX, by
            // setting up management again
            setupManagement(null);
        } else {
            throw new IllegalStateException("Disabling JMX can only be done when CamelContext has not been started");
        }
    }

    public boolean isJMXDisabled() {
        String override = System.getProperty(JmxSystemPropertyKeys.DISABLED);
        if (override != null) {
            return "true".equals(override);
        } else {
            return disableJMX;
        }
    }

    @Override
    public void setupManagement(Map<String, Object> options) {
        LOG.trace("Setting up management");

        ManagementStrategyFactory factory = null;
        if (!isJMXDisabled()) {
            try {
                // create a one time factory as we dont need this anymore
                FactoryFinder finder = createFactoryFinder("META-INF/services/org/apache/camel/management/");
                if (finder != null) {
                    Object object = finder.newInstance("ManagementStrategyFactory").orElse(null);
                    if (object instanceof ManagementStrategyFactory) {
                        factory = (ManagementStrategyFactory) object;
                    }
                }
                // detect if camel-debug is on classpath that enables debugging
                DebuggerFactory df
                        = getBootstrapFactoryFinder().newInstance(Debugger.FACTORY, DebuggerFactory.class).orElse(null);
                if (df != null) {
                    LOG.info("Detected: {} JAR (Enabling Camel Debugging)", df);
                    setDebugging(true);
                    Debugger debugger = df.createDebugger(this);
                    if (debugger != null) {
                        setDebugger(debugger);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Cannot create JmxManagementStrategyFactory. Will fallback and disable JMX.", e);
            }
        }
        if (factory == null) {
            factory = new DefaultManagementStrategyFactory();
        }
        LOG.debug("Setting up management with factory: {}", factory);

        // preserve any existing event notifiers that may have been already added
        List<EventNotifier> notifiers = null;
        if (managementStrategy != null) {
            notifiers = managementStrategy.getEventNotifiers();
        }

        try {
            ManagementStrategy strategy = factory.create(getCamelContextReference(), options);
            if (notifiers != null) {
                notifiers.forEach(strategy::addEventNotifier);
            }
            LifecycleStrategy lifecycle = factory.createLifecycle(this);
            factory.setupManagement(this, strategy, lifecycle);
        } catch (Exception e) {
            LOG.warn("Error setting up management due {}", e.getMessage());
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private static String getJvmUptime() {
        try {
            return TimeUtils.printDuration(ManagementFactory.getRuntimeMXBean().getUptime());
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
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

    @Override
    public void setInflightRepository(InflightRepository repository) {
        this.inflightRepository = doAddService(repository);
    }

    @Override
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

    @Override
    public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager asyncProcessorAwaitManager) {
        this.asyncProcessorAwaitManager = doAddService(asyncProcessorAwaitManager);
    }

    @Override
    public BeanIntrospection getBeanIntrospection() {
        if (beanIntrospection == null) {
            synchronized (lock) {
                if (beanIntrospection == null) {
                    setBeanIntrospection(createBeanIntrospection());
                }
            }
        }
        return beanIntrospection;
    }

    @Override
    public void setBeanIntrospection(BeanIntrospection beanIntrospection) {
        this.beanIntrospection = doAddService(beanIntrospection);
    }

    @Override
    public void setAutoStartup(Boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    @Override
    public Boolean isAutoStartup() {
        return autoStartup != null && autoStartup;
    }

    @Override
    public Boolean isLoadTypeConverters() {
        return loadTypeConverters != null && loadTypeConverters;
    }

    @Override
    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    @Override
    public Boolean isLoadHealthChecks() {
        return loadHealthChecks != null && loadHealthChecks;
    }

    @Override
    public void setLoadHealthChecks(Boolean loadHealthChecks) {
        this.loadHealthChecks = loadHealthChecks;
    }

    @Override
    public Boolean isModeline() {
        return modeline != null && modeline;
    }

    @Override
    public void setModeline(Boolean modeline) {
        this.modeline = modeline;
    }

    public Boolean isDevConsole() {
        return devConsole != null && devConsole;
    }

    @Override
    public void setDevConsole(Boolean loadDevConsoles) {
        this.devConsole = loadDevConsoles;
    }

    @Override
    public Boolean isTypeConverterStatisticsEnabled() {
        return typeConverterStatisticsEnabled != null && typeConverterStatisticsEnabled;
    }

    @Override
    public Boolean isSourceLocationEnabled() {
        return sourceLocationEnabled;
    }

    @Override
    public void setSourceLocationEnabled(Boolean sourceLocationEnabled) {
        this.sourceLocationEnabled = sourceLocationEnabled;
    }

    @Override
    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        this.typeConverterStatisticsEnabled = typeConverterStatisticsEnabled;
    }

    @Override
    public String getBasePackageScan() {
        return basePackageScan;
    }

    @Override
    public void setBasePackageScan(String basePackageScan) {
        this.basePackageScan = basePackageScan;
    }

    @Override
    public Boolean isDumpRoutes() {
        return dumpRoutes;
    }

    @Override
    public void setDumpRoutes(Boolean dumpRoutes) {
        this.dumpRoutes = dumpRoutes;
    }

    @Override
    public Boolean isUseMDCLogging() {
        return useMDCLogging != null && useMDCLogging;
    }

    @Override
    public void setUseMDCLogging(Boolean useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    @Override
    public String getMDCLoggingKeysPattern() {
        return mdcLoggingKeysPattern;
    }

    @Override
    public void setMDCLoggingKeysPattern(String pattern) {
        this.mdcLoggingKeysPattern = pattern;
    }

    @Override
    public Boolean isUseDataType() {
        return useDataType;
    }

    @Override
    public void setUseDataType(Boolean useDataType) {
        this.useDataType = useDataType;
    }

    @Override
    public Boolean isUseBreadcrumb() {
        return useBreadcrumb != null && useBreadcrumb;
    }

    @Override
    public void setUseBreadcrumb(Boolean useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    @Override
    public ClassLoader getApplicationContextClassLoader() {
        return applicationContextClassLoader;
    }

    @Override
    public void setApplicationContextClassLoader(ClassLoader classLoader) {
        applicationContextClassLoader = classLoader;
    }

    @Override
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

    @Override
    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        this.dataFormatResolver = doAddService(dataFormatResolver);
    }

    @Override
    public DataFormat resolveDataFormat(String name) {
        final DataFormat answer = dataformats.computeIfAbsent(name, s -> {
            StartupStep step = null;
            // only record startup step during startup (not started)
            if (!isStarted() && startupStepRecorder.isEnabled()) {
                step = startupStepRecorder.beginStep(DataFormat.class, name, "Resolve DataFormat");
            }

            DataFormat df = Optional
                    .ofNullable(ResolverHelper.lookupDataFormatInRegistryWithFallback(getCamelContextReference(), name))
                    .orElseGet(() -> getDataFormatResolver().createDataFormat(name, getCamelContextReference()));

            if (df != null) {
                // inject CamelContext if aware
                CamelContextAware.trySetCamelContext(df, getCamelContextReference());

                for (LifecycleStrategy strategy : lifecycleStrategies) {
                    strategy.onDataFormatCreated(name, df);
                }
            }

            if (step != null) {
                startupStepRecorder.endStep(step);
            }

            return df;
        });

        return answer;
    }

    @Override
    public DataFormat createDataFormat(String name) {
        StartupStep step = null;
        // only record startup step during startup (not started)
        if (!isStarted() && startupStepRecorder.isEnabled()) {
            step = startupStepRecorder.beginStep(DataFormat.class, name, "Create DataFormat");
        }

        DataFormat answer = getDataFormatResolver().createDataFormat(name, getCamelContextReference());

        // inject CamelContext if aware
        CamelContextAware.trySetCamelContext(answer, getCamelContextReference());

        for (LifecycleStrategy strategy : lifecycleStrategies) {
            strategy.onDataFormatCreated(name, answer);
        }

        if (step != null) {
            startupStepRecorder.endStep(step);
        }
        return answer;
    }

    @Override
    public Set<String> getDataFormatNames() {
        return Collections.unmodifiableSet(dataformats.keySet());
    }

    @Override
    public HealthCheckResolver getHealthCheckResolver() {
        if (healthCheckResolver == null) {
            synchronized (lock) {
                if (healthCheckResolver == null) {
                    setHealthCheckResolver(createHealthCheckResolver());
                }
            }
        }
        return healthCheckResolver;
    }

    @Override
    public void setHealthCheckResolver(HealthCheckResolver healthCheckResolver) {
        this.healthCheckResolver = doAddService(healthCheckResolver);
    }

    public DevConsoleResolver getDevConsoleResolver() {
        if (devConsoleResolver == null) {
            synchronized (lock) {
                if (devConsoleResolver == null) {
                    setDevConsoleResolver(createDevConsoleResolver());
                }
            }
        }
        return devConsoleResolver;
    }

    public void setDevConsoleResolver(DevConsoleResolver devConsoleResolver) {
        this.devConsoleResolver = doAddService(devConsoleResolver);
    }

    @Override
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

    @Override
    public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        this.shutdownStrategy = doAddService(shutdownStrategy);
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    @Override
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    @Override
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    @Override
    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    @Override
    public Boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage != null && allowUseOriginalMessage;
    }

    @Override
    public Boolean isCaseInsensitiveHeaders() {
        return caseInsensitiveHeaders != null && caseInsensitiveHeaders;
    }

    @Override
    public void setCaseInsensitiveHeaders(Boolean caseInsensitiveHeaders) {
        this.caseInsensitiveHeaders = caseInsensitiveHeaders;
    }

    @Override
    public Boolean isAutowiredEnabled() {
        return autowiredEnabled != null && autowiredEnabled;
    }

    @Override
    public void setAutowiredEnabled(Boolean autowiredEnabled) {
        this.autowiredEnabled = autowiredEnabled;
    }

    @Override
    public boolean isLightweight() {
        return lightweight;
    }

    @Override
    public void setLightweight(boolean lightweight) {
        this.lightweight = lightweight;
    }

    @Override
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

    @Override
    public void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {
        // special for executorServiceManager as want to stop it manually so
        // false in stopOnShutdown
        this.executorServiceManager = doAddService(executorServiceManager, false);
    }

    @Override
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

    @Override
    public void setProcessorFactory(ProcessorFactory processorFactory) {
        this.processorFactory = doAddService(processorFactory);
    }

    @Override
    public InternalProcessorFactory getInternalProcessorFactory() {
        if (internalProcessorFactory == null) {
            synchronized (lock) {
                if (internalProcessorFactory == null) {
                    setInternalProcessorFactory(createInternalProcessorFactory());
                }
            }
        }
        return internalProcessorFactory;
    }

    @Override
    public void setInternalProcessorFactory(InternalProcessorFactory internalProcessorFactory) {
        this.internalProcessorFactory = doAddService(internalProcessorFactory);
    }

    @Override
    public InterceptEndpointFactory getInterceptEndpointFactory() {
        if (interceptEndpointFactory == null) {
            synchronized (lock) {
                if (interceptEndpointFactory == null) {
                    setInterceptEndpointFactory(createInterceptEndpointFactory());
                }
            }
        }
        return interceptEndpointFactory;
    }

    @Override
    public void setInterceptEndpointFactory(InterceptEndpointFactory interceptEndpointFactory) {
        this.interceptEndpointFactory = doAddService(interceptEndpointFactory);
    }

    @Override
    public RouteFactory getRouteFactory() {
        if (routeFactory == null) {
            synchronized (lock) {
                if (routeFactory == null) {
                    setRouteFactory(createRouteFactory());
                }
            }
        }
        return routeFactory;
    }

    @Override
    public void setRouteFactory(RouteFactory routeFactory) {
        this.routeFactory = routeFactory;
    }

    @Override
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

    @Override
    public void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {
        this.messageHistoryFactory = doAddService(messageHistoryFactory);
        // enable message history if we set a custom factory
        setMessageHistory(true);
    }

    @Override
    public Debugger getDebugger() {
        return debugger;
        // do not lazy create debugger as the DefaultDebugger is mostly only useable for testing
        // and if debugging is enabled then Camel will use BacklogDebugger that can be remotely controlled via JMX management
    }

    @Override
    public void setDebugger(Debugger debugger) {
        if (isStartingOrStarted()) {
            throw new IllegalStateException("Cannot set debugger on a started CamelContext");
        }
        this.debugger = doAddService(debugger, true, false, true);
    }

    @Override
    public Tracer getTracer() {
        if (tracer == null) {
            synchronized (lock) {
                if (tracer == null) {
                    setTracer(createTracer());
                }
            }
        }
        return tracer;
    }

    @Override
    public void setTracer(Tracer tracer) {
        // if tracing is in standby mode, then we can use it after camel is started
        if (!isTracingStandby() && isStartingOrStarted()) {
            throw new IllegalStateException("Cannot set tracer on a started CamelContext");
        }
        this.tracer = doAddService(tracer, true, false, true);
    }

    @Override
    public void setTracingStandby(boolean tracingStandby) {
        this.traceStandby = tracingStandby;
    }

    @Override
    public boolean isTracingStandby() {
        return traceStandby != null && traceStandby;
    }

    @Override
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

    @Override
    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = doAddService(uuidGenerator);
    }

    @Override
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

    @Override
    public void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {
        this.streamCachingStrategy = doAddService(streamCachingStrategy, true, false, true);
    }

    @Override
    public RestRegistry getRestRegistry() {
        if (restRegistry == null) {
            synchronized (lock) {
                if (restRegistry == null) {
                    setRestRegistry(createRestRegistry());
                }
            }
        }
        return restRegistry;
    }

    @Override
    public void setRestRegistry(RestRegistry restRegistry) {
        this.restRegistry = doAddService(restRegistry);
    }

    protected RestRegistry createRestRegistry() {
        RestRegistryFactory factory = getRestRegistryFactory();
        return factory.createRegistry();
    }

    public RestRegistryFactory getRestRegistryFactory() {
        if (restRegistryFactory == null) {
            synchronized (lock) {
                if (restRegistryFactory == null) {
                    setRestRegistryFactory(createRestRegistryFactory());
                }
            }
        }
        return restRegistryFactory;
    }

    public void setRestRegistryFactory(RestRegistryFactory restRegistryFactory) {
        this.restRegistryFactory = doAddService(restRegistryFactory);
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
    public Transformer resolveTransformer(String scheme) {
        return getTransformerRegistry().resolveTransformer(new TransformerKey(scheme));
    }

    @Override
    public Transformer resolveTransformer(DataType from, DataType to) {
        return getTransformerRegistry().resolveTransformer(new TransformerKey(from, to));
    }

    @Override
    public TransformerRegistry getTransformerRegistry() {
        if (transformerRegistry == null) {
            synchronized (lock) {
                if (transformerRegistry == null) {
                    setTransformerRegistry(createTransformerRegistry());
                }
            }
        }
        return transformerRegistry;
    }

    public void setTransformerRegistry(TransformerRegistry transformerRegistry) {
        this.transformerRegistry = doAddService(transformerRegistry);
    }

    @Override
    public Validator resolveValidator(DataType type) {
        return getValidatorRegistry().resolveValidator(new ValidatorKey(type));
    }

    @Override
    public ValidatorRegistry getValidatorRegistry() {
        if (validatorRegistry == null) {
            synchronized (lock) {
                if (validatorRegistry == null) {
                    setValidatorRegistry(createValidatorRegistry());
                }
            }
        }
        return validatorRegistry;
    }

    public void setValidatorRegistry(ValidatorRegistry validatorRegistry) {
        this.validatorRegistry = doAddService(validatorRegistry);
    }

    @Override
    public SSLContextParameters getSSLContextParameters() {
        return this.sslContextParameters;
    }

    @Override
    public void setSSLContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public StartupSummaryLevel getStartupSummaryLevel() {
        return startupSummaryLevel;
    }

    @Override
    public void setStartupSummaryLevel(StartupSummaryLevel startupSummaryLevel) {
        this.startupSummaryLevel = startupSummaryLevel;
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        return headersMapFactory;
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory headersMapFactory) {
        this.headersMapFactory = doAddService(headersMapFactory);
    }

    @Override
    public XMLRoutesDefinitionLoader getXMLRoutesDefinitionLoader() {
        if (xmlRoutesDefinitionLoader == null) {
            synchronized (lock) {
                if (xmlRoutesDefinitionLoader == null) {
                    setXMLRoutesDefinitionLoader(createXMLRoutesDefinitionLoader());
                }
            }
        }
        return xmlRoutesDefinitionLoader;
    }

    @Override
    public void setXMLRoutesDefinitionLoader(XMLRoutesDefinitionLoader xmlRoutesDefinitionLoader) {
        this.xmlRoutesDefinitionLoader = doAddService(xmlRoutesDefinitionLoader);
    }

    @Override
    public RoutesLoader getRoutesLoader() {
        if (routesLoader == null) {
            synchronized (lock) {
                if (routesLoader == null) {
                    setRoutesLoader(createRoutesLoader());
                }
            }
        }
        return routesLoader;
    }

    @Override
    public void setRoutesLoader(RoutesLoader routesLoader) {
        this.routesLoader = doAddService(routesLoader);
    }

    @Override
    public ResourceLoader getResourceLoader() {
        if (resourceLoader == null) {
            synchronized (lock) {
                if (resourceLoader == null) {
                    setResourceLoader(createResourceLoader());
                }
            }
        }
        return resourceLoader;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = doAddService(resourceLoader);
    }

    public ModelToXMLDumper getModelToXMLDumper() {
        if (modelToXMLDumper == null) {
            synchronized (lock) {
                if (modelToXMLDumper == null) {
                    setModelToXMLDumper(createModelToXMLDumper());
                }
            }
        }
        return modelToXMLDumper;
    }

    public void setModelToXMLDumper(ModelToXMLDumper modelToXMLDumper) {
        this.modelToXMLDumper = doAddService(modelToXMLDumper);
    }

    public RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory() {
        if (restBindingJaxbDataFormatFactory == null) {
            synchronized (lock) {
                if (restBindingJaxbDataFormatFactory == null) {
                    setRestBindingJaxbDataFormatFactory(createRestBindingJaxbDataFormatFactory());
                }
            }
        }
        return restBindingJaxbDataFormatFactory;
    }

    public void setRestBindingJaxbDataFormatFactory(RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory) {
        this.restBindingJaxbDataFormatFactory = restBindingJaxbDataFormatFactory;
    }

    @Override
    public RuntimeCamelCatalog getRuntimeCamelCatalog() {
        if (runtimeCamelCatalog == null) {
            synchronized (lock) {
                if (runtimeCamelCatalog == null) {
                    setRuntimeCamelCatalog(createRuntimeCamelCatalog());
                }
            }
        }
        return runtimeCamelCatalog;
    }

    @Override
    public void setRuntimeCamelCatalog(RuntimeCamelCatalog runtimeCamelCatalog) {
        this.runtimeCamelCatalog = doAddService(runtimeCamelCatalog);
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        if (exchangeFactory == null) {
            synchronized (lock) {
                if (exchangeFactory == null) {
                    setExchangeFactory(createExchangeFactory());
                }
            }
        }
        return exchangeFactory;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        // automatic inject camel context
        exchangeFactory.setCamelContext(this);
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public ExchangeFactoryManager getExchangeFactoryManager() {
        if (exchangeFactoryManager == null) {
            synchronized (lock) {
                if (exchangeFactoryManager == null) {
                    setExchangeFactoryManager(createExchangeFactoryManager());
                }
            }
        }
        return exchangeFactoryManager;
    }

    @Override
    public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {
        this.exchangeFactoryManager = doAddService(exchangeFactoryManager);
    }

    @Override
    public ProcessorExchangeFactory getProcessorExchangeFactory() {
        if (processorExchangeFactory == null) {
            synchronized (lock) {
                if (processorExchangeFactory == null) {
                    setProcessorExchangeFactory(createProcessorExchangeFactory());
                }
            }
        }
        return processorExchangeFactory;
    }

    @Override
    public void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory) {
        // automatic inject camel context
        processorExchangeFactory.setCamelContext(this);
        this.processorExchangeFactory = processorExchangeFactory;
    }

    @Override
    public ReactiveExecutor getReactiveExecutor() {
        if (reactiveExecutor == null) {
            synchronized (lock) {
                if (reactiveExecutor == null) {
                    setReactiveExecutor(createReactiveExecutor());
                }
            }
        }
        return reactiveExecutor;
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        // special for executorServiceManager as want to stop it manually so
        // false in stopOnShutdown
        this.reactiveExecutor = doAddService(reactiveExecutor, false);
    }

    @Override
    public DeferServiceFactory getDeferServiceFactory() {
        if (deferServiceFactory == null) {
            synchronized (lock) {
                if (deferServiceFactory == null) {
                    setDeferServiceFactory(createDeferServiceFactory());
                }
            }
        }
        return deferServiceFactory;
    }

    public void setDeferServiceFactory(DeferServiceFactory deferServiceFactory) {
        this.deferServiceFactory = deferServiceFactory;
    }

    @Override
    public AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory() {
        if (annotationBasedProcessorFactory == null) {
            synchronized (lock) {
                if (annotationBasedProcessorFactory == null) {
                    setAnnotationBasedProcessorFactory(createAnnotationBasedProcessorFactory());
                }
            }
        }
        return annotationBasedProcessorFactory;
    }

    public void setAnnotationBasedProcessorFactory(AnnotationBasedProcessorFactory annotationBasedProcessorFactory) {
        this.annotationBasedProcessorFactory = annotationBasedProcessorFactory;
    }

    @Override
    public BeanProxyFactory getBeanProxyFactory() {
        if (beanProxyFactory == null) {
            synchronized (lock) {
                if (beanProxyFactory == null) {
                    setBeanProxyFactory(createBeanProxyFactory());
                }
            }
        }
        return beanProxyFactory;
    }

    public void setBeanProxyFactory(BeanProxyFactory beanProxyFactory) {
        this.beanProxyFactory = doAddService(beanProxyFactory);
    }

    @Override
    public BeanProcessorFactory getBeanProcessorFactory() {
        if (beanProcessorFactory == null) {
            synchronized (lock) {
                if (beanProcessorFactory == null) {
                    setBeanProcessorFactory(createBeanProcessorFactory());
                }
            }
        }
        return beanProcessorFactory;
    }

    public void setBeanProcessorFactory(BeanProcessorFactory beanProcessorFactory) {
        this.beanProcessorFactory = doAddService(beanProcessorFactory);
    }

    public boolean isLogJvmUptime() {
        return logJvmUptime;
    }

    /**
     * Whether to log the JVM uptime on startup and shutdown
     */
    public void setLogJvmUptime(boolean logJvmUptime) {
        this.logJvmUptime = logJvmUptime;
    }

    protected Map<String, RouteService> getRouteServices() {
        return routeServices;
    }

    @Override
    public String toString() {
        return "CamelContext(" + getName() + ")";
    }

    protected void failOnStartup(Exception e) {
        if (e instanceof VetoCamelContextStartException) {
            VetoCamelContextStartException vetoException = (VetoCamelContextStartException) e;
            if (vetoException.isRethrowException()) {
                fail(e);
            } else {
                // swallow exception and change state of this camel context to stopped
                status = FAILED;
            }
        } else {
            fail(e);
        }
    }

    protected abstract ExchangeFactory createExchangeFactory();

    protected abstract ExchangeFactoryManager createExchangeFactoryManager();

    protected abstract ProcessorExchangeFactory createProcessorExchangeFactory();

    protected abstract HealthCheckRegistry createHealthCheckRegistry();

    protected abstract DevConsoleRegistry createDevConsoleRegistry();

    protected abstract ReactiveExecutor createReactiveExecutor();

    protected abstract StreamCachingStrategy createStreamCachingStrategy();

    protected abstract TypeConverter createTypeConverter();

    protected abstract TypeConverterRegistry createTypeConverterRegistry();

    protected abstract Injector createInjector();

    protected abstract PropertiesComponent createPropertiesComponent();

    protected abstract CamelBeanPostProcessor createBeanPostProcessor();

    protected abstract CamelDependencyInjectionAnnotationFactory createDependencyInjectionAnnotationFactory();

    protected abstract ComponentResolver createComponentResolver();

    protected abstract ComponentNameResolver createComponentNameResolver();

    protected abstract Registry createRegistry();

    protected abstract UuidGenerator createUuidGenerator();

    protected abstract ModelJAXBContextFactory createModelJAXBContextFactory();

    protected abstract NodeIdFactory createNodeIdFactory();

    protected abstract ModelineFactory createModelineFactory();

    protected abstract FactoryFinderResolver createFactoryFinderResolver();

    protected abstract ClassResolver createClassResolver();

    protected abstract ProcessorFactory createProcessorFactory();

    protected abstract InternalProcessorFactory createInternalProcessorFactory();

    protected abstract InterceptEndpointFactory createInterceptEndpointFactory();

    protected abstract RouteFactory createRouteFactory();

    protected abstract DataFormatResolver createDataFormatResolver();

    protected abstract HealthCheckResolver createHealthCheckResolver();

    protected abstract DevConsoleResolver createDevConsoleResolver();

    protected abstract MessageHistoryFactory createMessageHistoryFactory();

    protected abstract InflightRepository createInflightRepository();

    protected abstract AsyncProcessorAwaitManager createAsyncProcessorAwaitManager();

    protected abstract RouteController createRouteController();

    protected abstract ShutdownStrategy createShutdownStrategy();

    protected abstract PackageScanClassResolver createPackageScanClassResolver();

    protected abstract PackageScanResourceResolver createPackageScanResourceResolver();

    protected abstract ExecutorServiceManager createExecutorServiceManager();

    protected abstract UnitOfWorkFactory createUnitOfWorkFactory();

    protected abstract CamelContextNameStrategy createCamelContextNameStrategy();

    protected abstract ManagementNameStrategy createManagementNameStrategy();

    protected abstract HeadersMapFactory createHeadersMapFactory();

    protected abstract BeanProxyFactory createBeanProxyFactory();

    protected abstract AnnotationBasedProcessorFactory createAnnotationBasedProcessorFactory();

    protected abstract DeferServiceFactory createDeferServiceFactory();

    protected abstract BeanProcessorFactory createBeanProcessorFactory();

    protected abstract BeanIntrospection createBeanIntrospection();

    protected abstract XMLRoutesDefinitionLoader createXMLRoutesDefinitionLoader();

    protected abstract RoutesLoader createRoutesLoader();

    protected abstract ResourceLoader createResourceLoader();

    protected abstract ModelToXMLDumper createModelToXMLDumper();

    protected abstract RestBindingJaxbDataFormatFactory createRestBindingJaxbDataFormatFactory();

    protected abstract RuntimeCamelCatalog createRuntimeCamelCatalog();

    protected abstract Tracer createTracer();

    protected abstract LanguageResolver createLanguageResolver();

    protected abstract ConfigurerResolver createConfigurerResolver();

    protected abstract UriFactoryResolver createUriFactoryResolver();

    protected abstract RestRegistryFactory createRestRegistryFactory();

    protected abstract EndpointRegistry<NormalizedUri> createEndpointRegistry(Map<NormalizedUri, Endpoint> endpoints);

    protected abstract TransformerRegistry<TransformerKey> createTransformerRegistry();

    protected abstract ValidatorRegistry<ValidatorKey> createValidatorRegistry();

    protected RestConfiguration createRestConfiguration() {
        // lookup a global which may have been on a container such spring-boot / CDI / etc.
        RestConfiguration conf
                = CamelContextHelper.lookup(this, RestConfiguration.DEFAULT_REST_CONFIGURATION_ID, RestConfiguration.class);
        if (conf == null) {
            conf = CamelContextHelper.findSingleByType(this, RestConfiguration.class);
        }
        if (conf == null) {
            conf = new RestConfiguration();
        }

        return conf;
    }

    @Override
    public RouteController getInternalRouteController() {
        return internalRouteController;
    }

    @Override
    public EndpointUriFactory getEndpointUriFactory(String scheme) {
        return getUriFactoryResolver().resolveFactory(scheme, this);
    }

    @Override
    public StartupStepRecorder getStartupStepRecorder() {
        return startupStepRecorder;
    }

    @Override
    public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {
        this.startupStepRecorder = startupStepRecorder;
    }

    @Deprecated
    public enum Initialization {
        Eager,
        Default,
        Lazy
    }

    class LifecycleHelper implements AutoCloseable {
        final Map<String, String> originalContextMap;
        final ClassLoader tccl;

        LifecycleHelper() {
            // Using the ApplicationClassLoader as the default for TCCL
            tccl = Thread.currentThread().getContextClassLoader();
            if (applicationContextClassLoader != null) {
                Thread.currentThread().setContextClassLoader(applicationContextClassLoader);
            }
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
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }
}
