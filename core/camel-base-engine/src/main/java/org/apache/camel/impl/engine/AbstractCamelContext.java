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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToStartComponentException;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.GlobalEndpointConfiguration;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
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
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
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
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.PeriodTaskResolver;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
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
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PluginHelper;
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
        implements CamelContext, CatalogCamelContext, Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCamelContext.class);

    protected final InternalServiceManager internalServiceManager;

    volatile StartupStepRecorder startupStepRecorder = new DefaultStartupStepRecorder();
    int defaultRouteStartupOrder = 1000;

    private final Object lock = new Object();
    private final DefaultCamelContextExtension camelContextExtension = new DefaultCamelContextExtension(this);
    private final AtomicInteger endpointKeyCounter = new AtomicInteger();
    private final List<EndpointStrategy> endpointStrategies = new ArrayList<>();
    private final GlobalEndpointConfiguration globalEndpointConfiguration = new DefaultGlobalEndpointConfiguration();
    private final Map<String, Component> components = new ConcurrentHashMap<>();
    private final Set<Route> routes = new LinkedHashSet<>();
    private final List<StartupListener> startupListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Language> languages = new ConcurrentHashMap<>();
    private final Map<String, DataFormat> dataformats = new ConcurrentHashMap<>();
    private final List<LifecycleStrategy> lifecycleStrategies = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Boolean> isStartingRoutes = new ThreadLocal<>();
    private final ThreadLocal<Boolean> isLockModel = new ThreadLocal<>();
    private final Map<String, RouteService> routeServices = new LinkedHashMap<>();
    private final Map<String, RouteService> suspendedRouteServices = new LinkedHashMap<>();
    private final InternalRouteStartupManager internalRouteStartupManager = new InternalRouteStartupManager(this);
    private final List<RouteStartupOrder> routeStartupOrder = new ArrayList<>();
    private final StopWatch stopWatch = new StopWatch(false);
    private final ThreadLocal<Set<String>> componentsInCreation = ThreadLocal.withInitial(() -> new HashSet<>());
    private VetoCamelContextStartException vetoed;
    private String managementName;
    private ClassLoader applicationContextClassLoader;
    private boolean autoCreateComponents = true;
    private volatile RestConfiguration restConfiguration;
    private volatile VaultConfiguration vaultConfiguration = new VaultConfiguration();

    private final List<RoutePolicyFactory> routePolicyFactories = new ArrayList<>();
    // special flags to control the first startup which can are special
    private volatile boolean firstStartDone;
    private volatile boolean doNotStartRoutesOnFirstStart;
    private Boolean autoStartup = Boolean.TRUE;
    private Boolean backlogTrace = Boolean.FALSE;
    private Boolean backlogTraceStandby = Boolean.FALSE;
    private Boolean backlogTraceTemplates = Boolean.FALSE;
    private Boolean trace = Boolean.FALSE;
    private Boolean traceStandby = Boolean.FALSE;
    private Boolean traceTemplates = Boolean.FALSE;
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
    private String dumpRoutes;
    private Boolean useMDCLogging = Boolean.FALSE;
    private String mdcLoggingKeysPattern;
    private Boolean useDataType = Boolean.FALSE;
    private Boolean useBreadcrumb = Boolean.FALSE;
    private Boolean allowUseOriginalMessage = Boolean.FALSE;
    private Boolean caseInsensitiveHeaders = Boolean.TRUE;
    private Boolean autowiredEnabled = Boolean.TRUE;
    private Long delay;
    private Map<String, String> globalOptions = new HashMap<>();
    private volatile String version;
    private volatile PropertiesComponent propertiesComponent;
    private volatile CamelContextNameStrategy nameStrategy;
    private volatile ManagementNameStrategy managementNameStrategy;
    private volatile TypeConverter typeConverter;
    private volatile TypeConverterRegistry typeConverterRegistry;
    private volatile Injector injector;
    private volatile RestRegistryFactory restRegistryFactory;
    private volatile RestRegistry restRegistry;
    private volatile ClassResolver classResolver;
    private volatile MessageHistoryFactory messageHistoryFactory;
    private volatile StreamCachingStrategy streamCachingStrategy;
    private volatile InflightRepository inflightRepository;
    private volatile ShutdownStrategy shutdownStrategy;
    private volatile ExecutorServiceManager executorServiceManager;
    private volatile UuidGenerator uuidGenerator;
    private volatile RouteController routeController;
    private volatile Tracer tracer;
    private volatile TransformerRegistry<TransformerKey> transformerRegistry;
    private volatile ValidatorRegistry<ValidatorKey> validatorRegistry;
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

    /**
     * Creates the {@link CamelContext} using {@link org.apache.camel.support.DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    protected AbstractCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link CamelContext} using the given registry
     *
     * @param registry the registry
     */
    protected AbstractCamelContext(Registry registry) {
        this();
        camelContextExtension.setRegistry(registry);
    }

    protected AbstractCamelContext(boolean build) {
        // create a provisional (temporary) endpoint registry at first since end
        // users may access endpoints before CamelContext is started
        // we will later transfer the endpoints to the actual
        // DefaultEndpointRegistry later, but we do this to startup Camel faster.
        this.endpoints = new ProvisionalEndpointRegistry();

        // add a default LifecycleStrategy that discover strategies on the registry and invoke them
        this.lifecycleStrategies.add(new OnCamelContextLifecycleStrategy());

        // add a default LifecycleStrategy to customize services using customizers from registry
        this.lifecycleStrategies.add(new CustomizersLifecycleStrategy(this));

        // add a default autowired strategy
        this.lifecycleStrategies.add(new DefaultAutowiredLifecycleStrategy(this));

        // add the default bootstrap closer
        camelContextExtension.addBootstrap(new DefaultServiceBootstrapCloseable(this));

        this.internalServiceManager = new InternalServiceManager(this, internalRouteStartupManager, startupListeners);

        initPlugins();

        if (build) {
            try {
                build();
            } catch (Exception e) {
                throw new RuntimeException("Error initializing CamelContext", e);
            }
        }
    }

    /**
     * Called during object construction to initialize context plugins
     */
    protected void initPlugins() {
        camelContextExtension.addContextPlugin(CamelBeanPostProcessor.class, createBeanPostProcessor());
        camelContextExtension.addContextPlugin(CamelDependencyInjectionAnnotationFactory.class,
                createDependencyInjectionAnnotationFactory());
        camelContextExtension.addContextPlugin(ComponentResolver.class, createComponentResolver());
        camelContextExtension.addContextPlugin(ComponentNameResolver.class, createComponentNameResolver());
        camelContextExtension.addContextPlugin(LanguageResolver.class, createLanguageResolver());
        camelContextExtension.addContextPlugin(ConfigurerResolver.class, createConfigurerResolver());
        camelContextExtension.addContextPlugin(UriFactoryResolver.class, createUriFactoryResolver());
        camelContextExtension.addContextPlugin(FactoryFinderResolver.class, createFactoryFinderResolver());
        camelContextExtension.addContextPlugin(PackageScanClassResolver.class, createPackageScanClassResolver());
        camelContextExtension.addContextPlugin(PackageScanResourceResolver.class, createPackageScanResourceResolver());
        camelContextExtension.lazyAddContextPlugin(ModelineFactory.class, this::createModelineFactory);
        camelContextExtension.lazyAddContextPlugin(ModelJAXBContextFactory.class, this::createModelJAXBContextFactory);
        camelContextExtension.addContextPlugin(DataFormatResolver.class, createDataFormatResolver());
        camelContextExtension.lazyAddContextPlugin(PeriodTaskResolver.class, this::createPeriodTaskResolver);
        camelContextExtension.lazyAddContextPlugin(PeriodTaskScheduler.class, this::createPeriodTaskScheduler);
        camelContextExtension.lazyAddContextPlugin(HealthCheckResolver.class, this::createHealthCheckResolver);
        camelContextExtension.lazyAddContextPlugin(DevConsoleResolver.class, this::createDevConsoleResolver);
        camelContextExtension.lazyAddContextPlugin(ProcessorFactory.class, this::createProcessorFactory);
        camelContextExtension.lazyAddContextPlugin(InternalProcessorFactory.class, this::createInternalProcessorFactory);
        camelContextExtension.lazyAddContextPlugin(InterceptEndpointFactory.class, this::createInterceptEndpointFactory);
        camelContextExtension.lazyAddContextPlugin(RouteFactory.class, this::createRouteFactory);
        camelContextExtension.lazyAddContextPlugin(RoutesLoader.class, this::createRoutesLoader);
        camelContextExtension.lazyAddContextPlugin(AsyncProcessorAwaitManager.class, this::createAsyncProcessorAwaitManager);
        camelContextExtension.lazyAddContextPlugin(RuntimeCamelCatalog.class, this::createRuntimeCamelCatalog);
        camelContextExtension.lazyAddContextPlugin(RestBindingJaxbDataFormatFactory.class,
                this::createRestBindingJaxbDataFormatFactory);
        camelContextExtension.lazyAddContextPlugin(BeanProxyFactory.class, this::createBeanProxyFactory);
        camelContextExtension.lazyAddContextPlugin(UnitOfWorkFactory.class, this::createUnitOfWorkFactory);
        camelContextExtension.lazyAddContextPlugin(BeanIntrospection.class, this::createBeanIntrospection);
        camelContextExtension.lazyAddContextPlugin(ResourceLoader.class, this::createResourceLoader);
        camelContextExtension.lazyAddContextPlugin(BeanProcessorFactory.class, this::createBeanProcessorFactory);
        camelContextExtension.lazyAddContextPlugin(ModelToXMLDumper.class, this::createModelToXMLDumper);
        camelContextExtension.lazyAddContextPlugin(ModelToYAMLDumper.class, this::createModelToYAMLDumper);
        camelContextExtension.lazyAddContextPlugin(DeferServiceFactory.class, this::createDeferServiceFactory);
        camelContextExtension.lazyAddContextPlugin(AnnotationBasedProcessorFactory.class,
                this::createAnnotationBasedProcessorFactory);
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
    public boolean isVetoStarted() {
        return vetoed != null;
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
        this.nameStrategy = internalServiceManager.addService(nameStrategy);
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
        this.managementNameStrategy = internalServiceManager.addService(managementNameStrategy);
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
            final Component component = components.computeIfAbsent(name, comp -> {
                created.set(true);
                return initComponent(name, autoCreateComponents);
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
                    LOG.debug("Using ComponentResolver: {} to resolve component with name: {}",
                            PluginHelper.getComponentResolver(camelContextExtension), name);
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
                    component = PluginHelper.getComponentResolver(camelContextExtension).resolveComponent(name,
                            getCamelContextReference());
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
                LOG.warn("Error stopping component {}. This exception will be ignored.", oldComponent, e);
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
    public Endpoint hasEndpoint(String uri) {
        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.get(getEndpointKey(uri));
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
            String decodeUri = URISupport.getDecodeQuery(uri);
            if (decodeUri != null) {
                oldEndpoint = endpoints.remove(getEndpointKey(decodeUri));
            }
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
                            LOG.warn("Error stopping endpoint {}. This exception will be ignored.", oldEndpoint, e);
                        }
                        answer.add(oldEndpoint);
                        toRemove.add(entry.getKey());
                    }
                }
                for (NormalizedUri key : toRemove) {
                    endpoints.remove(key);
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
    public Endpoint getEndpoint(String uri, Map<String, Object> parameters) {
        return doGetEndpoint(uri, parameters, false, false);
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
                    answer = camelContextExtension.getRegistry().lookupByNameAndType(uri, Endpoint.class);
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
        this.routeController = internalServiceManager.addService(routeController);
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
            builder.addRoutesToCamelContext(this);
        }
    }

    @Override
    public void addTemplatedRoutes(RoutesBuilder builder) throws Exception {
        try (LifecycleHelper helper = new LifecycleHelper()) {
            build();
            LOG.debug("Adding templated routes from builder: {}", builder);
            builder.addTemplatedRoutesToCamelContext(this);
        }
    }

    @Override
    public void addRoutesConfigurations(RouteConfigurationsBuilder builder) throws Exception {
        try (LifecycleHelper helper = new LifecycleHelper()) {
            build();
            LOG.debug("Adding route configurations from builder: {}", builder);
            builder.addRouteConfigurationsToCamelContext(this);
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

    public boolean isLockModel() {
        Boolean answer = isLockModel.get();
        return answer != null && answer;
    }

    public void setLockModel(boolean lockModel) {
        if (lockModel) {
            isLockModel.set(true);
        } else {
            isLockModel.remove();
        }
    }

    public void startAllRoutes() throws Exception {
        internalRouteStartupManager.doStartOrResumeRoutes(routeServices, true, true, false, false);
    }

    private void doStopRoutes(RouteController controller, Comparator<RouteStartupOrder> comparator) throws Exception {
        List<RouteStartupOrder> routesOrdered = new ArrayList<>(camelContextExtension.getRouteStartupOrder());
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
        doStopRoutes(controller, comparator);

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
        doStopRoutes(getRouteController(), comparator);

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
                List<RouteStartupOrder> routeList = new ArrayList<>(1);
                RouteStartupOrder order = new DefaultRouteStartupOrder(1, routeService.getRoute(), routeService);
                routeList.add(order);

                getShutdownStrategy().shutdown(this, routeList, timeout, timeUnit);
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
                        for (Set<Endpoint> endpointSet : endpointsInUse.values()) {
                            if (endpointSet.contains(endpoint)) {
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
                List<RouteStartupOrder> routeList = new ArrayList<>(1);
                Route route = routeService.getRoute();
                RouteStartupOrder order = new DefaultRouteStartupOrder(1, route, routeService);
                routeList.add(order);

                getShutdownStrategy().suspend(this, routeList, timeout, timeUnit);
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
        internalServiceManager.doAddService(object, stopOnShutdown, forceStart, true);
    }

    @Override
    public void addPrototypeService(Object object) throws Exception {
        internalServiceManager.addService(object, false, true, false);
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

            return internalServiceManager.removeService(service);
        }
        return false;
    }

    @Override
    public boolean hasService(Object object) {
        return internalServiceManager.hasService(object);
    }

    @Override
    public <T> T hasService(Class<T> type) {
        return internalServiceManager.hasService(type);
    }

    @Override
    public <T> Set<T> hasServices(Class<T> type) {
        return internalServiceManager.hasServices(type);
    }

    @Override
    public void deferStartService(Object object, boolean stopOnShutdown) throws Exception {
        internalServiceManager.deferStartService(object, stopOnShutdown, false);
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

    private static String toResourcePath(Package clazz, String languageName) {
        String packageName = clazz.getName();
        packageName = packageName.replace('.', '/');
        return packageName + "/" + languageName + ".json";
    }

    private String doLoadResource(String resourceName, String path, String resourceType) throws IOException {
        final ClassResolver resolver = getClassResolver();
        InputStream inputStream = resolver.loadResourceAsStream(path);
        LOG.debug("Loading {} JSON Schema for: {} using class resolver: {} -> {}", resourceType, resourceName, resolver,
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

    @Override
    public String getComponentParameterJsonSchema(String componentName) throws IOException {
        // use the component factory finder to find the package name of the
        // component class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = camelContextExtension.getFactoryFinder(DefaultComponentResolver.RESOURCE_PATH);
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

        String path = toResourcePath(clazz.getPackage(), componentName);

        String inputStream = doLoadResource(componentName, path, "component");
        if (inputStream != null) {
            return inputStream;
        }

        return null;
    }

    @Override
    public String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException {
        // use the dataformat factory finder to find the package name of the
        // dataformat class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = camelContextExtension.getFactoryFinder(DefaultDataFormatResolver.DATAFORMAT_RESOURCE_PATH);
        Class<?> clazz = finder.findClass(dataFormatName).orElse(null);
        if (clazz == null) {
            return null;
        }

        String path = toResourcePath(clazz.getPackage(), dataFormatName);

        String inputStream = doLoadResource(dataFormatName, path, "dataformat");
        if (inputStream != null) {
            return inputStream;
        }
        return null;
    }

    @Override
    public String getLanguageParameterJsonSchema(String languageName) throws IOException {
        // use the language factory finder to find the package name of the
        // language class, which is the location
        // where the documentation exists as well
        FactoryFinder finder = camelContextExtension.getFactoryFinder(DefaultLanguageResolver.LANGUAGE_RESOURCE_PATH);
        Class<?> clazz = finder.findClass(languageName).orElse(null);
        if (clazz == null) {
            return null;
        }

        String path = toResourcePath(clazz.getPackage(), languageName);

        String inputStream = doLoadResource(languageName, path, "language");
        if (inputStream != null) {
            return inputStream;
        }
        return null;
    }

    // Helper methods
    // -----------------------------------------------------------------------
    @Override
    public String getEipParameterJsonSchema(String eipName) throws IOException {
        // the eip json schema may be in some of the sub-packages so look until
        // we find it
        String[] subPackages = new String[] {
                "", "cloud/", "config/", "dataformat/", "errorhandler/", "language/", "loadbalancer/", "rest/", "transformer/",
                "validator/" };
        for (String sub : subPackages) {
            String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + eipName + ".json";
            String inputStream = doLoadResource(eipName, path, "eip");
            if (inputStream != null) {
                return inputStream;
            }
        }
        return null;
    }

    @Override
    public Language resolveLanguage(String name) {
        LOG.debug("Resolving language: {}", name);

        return languages.computeIfAbsent(name, s -> doResolveLanguage(name));
    }

    private Language doResolveLanguage(String name) {
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
            language = PluginHelper.getLanguageResolver(camelContextExtension).resolveLanguage(name, camelContext);
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

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public String resolvePropertyPlaceholders(String text) {
        return camelContextExtension.resolvePropertyPlaceholders(text, false);
    }

    @Override
    public TypeConverter getTypeConverter() {
        return typeConverter;
    }

    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = internalServiceManager.addService(typeConverter);
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
        this.typeConverterRegistry = internalServiceManager.addService(typeConverterRegistry);
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
        this.injector = internalServiceManager.addService(injector);
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
        this.propertiesComponent = internalServiceManager.addService(propertiesComponent);
    }

    protected void setManagementMBeanAssembler(ManagementMBeanAssembler managementMBeanAssembler) {
        camelContextExtension.setManagementMBeanAssembler(managementMBeanAssembler);
    }

    public void setAutoCreateComponents(boolean autoCreateComponents) {
        this.autoCreateComponents = autoCreateComponents;
    }

    @Override
    public <T> T getRegistry(Class<T> type) {
        Registry reg = camelContextExtension.getRegistry();

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
    public List<RoutePolicyFactory> getRoutePolicyFactories() {
        return routePolicyFactories;
    }

    @Override
    public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {
        // avoid adding double which can happen with spring xml on spring boot
        if (!getRoutePolicyFactories().contains(routePolicyFactory)) {
            getRoutePolicyFactories().add(routePolicyFactory);
        }
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

    protected ScheduledExecutorService createErrorHandlerExecutorService() {
        return getExecutorServiceManager().newDefaultScheduledThreadPool("ErrorHandlerRedeliveryThreadPool",
                "ErrorHandlerRedeliveryTask");
    }

    @Override
    public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
        return runtimeEndpointRegistry;
    }

    @Override
    public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {
        this.runtimeEndpointRegistry = internalServiceManager.addService(runtimeEndpointRegistry);
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
        String resolvedVersion = null;

        InputStream is = null;
        // try to load from maven properties first
        try {
            Properties p = new Properties();
            is = AbstractCamelContext.class
                    .getResourceAsStream("/META-INF/maven/org.apache.camel/camel-base-engine/pom.properties");
            if (is != null) {
                p.load(is);
                resolvedVersion = p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (is != null) {
                IOHelper.close(is);
            }
        }

        // fallback to using Java API
        if (resolvedVersion == null) {
            Package aPackage = getClass().getPackage();
            if (aPackage != null) {
                resolvedVersion = aPackage.getImplementationVersion();
                if (resolvedVersion == null) {
                    resolvedVersion = aPackage.getSpecificationVersion();
                }
            }
        }

        if (resolvedVersion == null) {
            // we could not compute the version so use a blank
            resolvedVersion = "";
        }

        return resolvedVersion;
    }

    @Override
    protected void doSuspend() throws Exception {
        EventHelper.notifyCamelContextSuspending(this);

        LOG.info("Apache Camel {} ({}) is suspending", getVersion(), camelContextExtension.getName());
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
            LOG.info("Apache Camel {} ({}) is suspended in {}", getVersion(), camelContextExtension.getName(),
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

            LOG.info("Apache Camel {} ({}) is resuming", getVersion(), camelContextExtension.getName());
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
                LOG.info("Apache Camel {} ({}) resumed in {}", getVersion(), camelContextExtension.getName(),
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
            if (e.getCause() instanceof VetoCamelContextStartException) {
                vetoed = (VetoCamelContextStartException) e.getCause();
            } else {
                throw e;
            }
        }

        // was the initialization vetoed?
        if (vetoed != null) {
            LOG.info("CamelContext ({}) vetoed to not initialize due to: {}", camelContextExtension.getName(),
                    vetoed.getMessage());
            failOnStartup(vetoed);
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
            LOG.info("CamelContext ({}) vetoed to not start due to: {}", camelContextExtension.getName(), vetoed.getMessage());
            failOnStartup(vetoed);
            stop();
            return;
        }

        for (LifecycleStrategy strategy : lifecycleStrategies) {
            try {
                strategy.onContextStarted(this);
            } catch (Exception e) {
                LOG.warn("Lifecycle strategy {} failed on CamelContext ({}) due to: {}. This exception will be ignored",
                        strategy,
                        camelContextExtension.getName(),
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
            } catch (Exception e) {
                LOG.warn("Lifecycle strategy {} failed on CamelContext ({}) due to: {}. This exception will be ignored",
                        strategy,
                        camelContextExtension.getName(),
                        e.getMessage());
            }
        }

        super.stop();
    }

    @Override
    public void doBuild() throws Exception {
        final StopWatch watch = new StopWatch();

        getCamelContextExtension().addContextPlugin(NodeIdFactory.class, createNodeIdFactory());

        // auto-detect step recorder from classpath if none has been explicit configured
        if (startupStepRecorder.getClass().getSimpleName().equals("DefaultStartupStepRecorder")) {
            StartupStepRecorder fr = camelContextExtension.getBootstrapFactoryFinder()
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
        StartupStep subStep = startupStepRecorder.beginStep(CamelContext.class, null, "Setup LRUCacheFactory");
        LRUCacheFactory.init();
        startupStepRecorder.endStep(subStep);

        // Setup management first since end users may use it to add event
        // notifiers using the management strategy before the CamelContext has been started
        StartupStep step3 = startupStepRecorder.beginStep(CamelContext.class, null, "Setup Management");
        camelContextExtension.setupManagement(null);
        startupStepRecorder.endStep(step3);

        // setup health-check registry as its needed this early phase for 3rd party to register custom repositories
        HealthCheckRegistry hcr = getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
        if (hcr == null) {
            StartupStep step4 = startupStepRecorder.beginStep(CamelContext.class, null, "Setup HealthCheckRegistry");
            hcr = createHealthCheckRegistry();
            if (hcr != null) {
                // install health-check registry if it was discovered from classpath (camel-health)
                hcr.setCamelContext(this);
                getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, hcr);
            }
            startupStepRecorder.endStep(step4);
        }

        // setup dev-console registry as its needed this early phase for 3rd party to register custom consoles
        DevConsoleRegistry dcr = getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        if (dcr == null) {
            StartupStep step5 = startupStepRecorder.beginStep(CamelContext.class, null, "Setup DevConsoleRegistry");
            dcr = createDevConsoleRegistry();
            if (dcr != null) {
                // install dev-console registry if it was discovered from classpath (camel-console)
                dcr.setCamelContext(this);
                getCamelContextExtension().addContextPlugin(DevConsoleRegistry.class, dcr);
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

        if (LOG.isDebugEnabled()) {
            buildTaken = watch.taken();
            LOG.debug("Apache Camel {} ({}) built in {}", getVersion(), camelContextExtension.getName(),
                    TimeUtils.printDuration(buildTaken, true));
        }
    }

    protected void resetBuildTime() {
        // needed by camel-quarkus
        buildTaken = 0;
    }

    @Override
    public void doInit() throws Exception {
        final StopWatch watch = new StopWatch();

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

        // optimize - before starting routes lets check if event notifications are possible
        camelContextExtension.setEventNotificationApplicable(EventHelper.eventsApplicable(this));

        // ensure additional type converters is loaded (either if enabled or we should use package scanning from the base)
        boolean load = loadTypeConverters || camelContextExtension.getBasePackageScan() != null;
        if (load && typeConverter instanceof AnnotationScanTypeConverters) {
            StartupStep step2 = startupStepRecorder.beginStep(CamelContext.class, null, "Scan TypeConverters");
            ((AnnotationScanTypeConverters) typeConverter).scanTypeConverters();
            startupStepRecorder.endStep(step2);
        }

        // ensure additional health checks is loaded
        if (loadHealthChecks) {
            StartupStep step3 = startupStepRecorder.beginStep(CamelContext.class, null, "Scan HealthChecks");
            HealthCheckRegistry hcr = getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
            if (hcr != null) {
                hcr.loadHealthChecks();
            }
            startupStepRecorder.endStep(step3);
        }
        // ensure additional dev consoles is loaded
        if (devConsole) {
            StartupStep step4 = startupStepRecorder.beginStep(CamelContext.class, null, "Scan DevConsoles (phase 1)");
            DevConsoleRegistry dcr = getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
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

        // setup cli-connector if not already done
        if (hasService(CliConnector.class) == null) {
            CliConnectorFactory ccf = getCamelContextExtension().getContextPlugin(CliConnectorFactory.class);
            if (ccf != null && ccf.isEnabled()) {
                CliConnector connector = ccf.createConnector();
                addService(connector, true);
                // force start cli connector early as otherwise it will be deferred until context is started
                // but, we want status available during startup phase
                ServiceHelper.startService(connector);
            }
        }

        addService(getManagementStrategy(), false);
        lifecycleStrategies.sort(OrderedComparator.get());
        ServiceHelper.initService(lifecycleStrategies);
        for (LifecycleStrategy strategy : lifecycleStrategies) {
            try {
                strategy.onContextInitializing(this);
            } catch (VetoCamelContextStartException e) {
                // okay we should not start Camel since it was vetoed
                LOG.warn("Lifecycle strategy {} vetoed initializing CamelContext ({}) due to: {}", strategy,
                        camelContextExtension.getName(),
                        e.getMessage());
                throw e;
            } catch (Exception e) {
                LOG.warn("Lifecycle strategy {} failed initializing CamelContext ({}) due to: {}", strategy,
                        camelContextExtension.getName(),
                        e.getMessage());
                throw e;
            }
        }

        // optimize - before starting routes lets check if event notifications are possible
        camelContextExtension.setEventNotificationApplicable(EventHelper.eventsApplicable(this));

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
        endpoints = internalServiceManager.addService(createEndpointRegistry(endpoints));

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
        for (RouteTemplateParameterSource source : camelContextExtension.getRegistry()
                .findByType(RouteTemplateParameterSource.class)) {
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
        StartupStep subStep = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(), "Init Routes");
        // the method is called start but at this point it will only initialize (as context is starting up)
        startRouteDefinitions();
        // this will init route definitions and populate as route services which we can then initialize now
        internalRouteStartupManager.doInitRoutes(routeServices);
        startupStepRecorder.endStep(subStep);

        if (!lifecycleStrategies.isEmpty()) {
            subStep = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(),
                    "LifecycleStrategy onContextInitialized");
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                try {
                    strategy.onContextInitialized(this);
                } catch (VetoCamelContextStartException e) {
                    // okay we should not start Camel since it was vetoed
                    LOG.warn("Lifecycle strategy {} vetoed initializing CamelContext ({}) due to: {}", strategy,
                            camelContextExtension.getName(),
                            e.getMessage());
                    throw e;
                } catch (Exception e) {
                    LOG.warn("Lifecycle strategy {} failed initializing CamelContext ({}) due to: {}", strategy,
                            camelContextExtension.getName(),
                            e.getMessage());
                    throw e;
                }
            }
            startupStepRecorder.endStep(subStep);
        }

        EventHelper.notifyCamelContextInitialized(this);

        startupStepRecorder.endStep(step);

        if (LOG.isDebugEnabled()) {
            initTaken = watch.taken();
            LOG.debug("Apache Camel {} ({}) initialized in {}", getVersion(), camelContextExtension.getName(),
                    TimeUtils.printDuration(initTaken, true));
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (firstStartDone) {
            // its not good practice resetting a camel context
            LOG.warn("Starting CamelContext: {} after the context has been stopped is not recommended",
                    camelContextExtension.getName());
        }
        StartupStep step
                = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(), "Start CamelContext");

        try {
            doStartContext();
        } catch (Exception e) {
            // fire event that we failed to start
            EventHelper.notifyCamelContextStartupFailed(this, e);
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
        LOG.info("Apache Camel {} ({}) is starting", getVersion(), camelContextExtension.getName());

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
                LOG.error("Error starting CamelContext ({}) due to exception thrown: {}", camelContextExtension.getName(),
                        e.getMessage(), e);
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        // duplicate components in use?
        logDuplicateComponents();

        // log startup summary
        logStartSummary();

        // now Camel has been started/bootstrap is complete, then run cleanup to help free up memory etc
        camelContextExtension.closeBootstraps();

        if (camelContextExtension.getExchangeFactory().isPooled()) {
            LOG.info(
                    "Pooled mode enabled. Camel pools and reuses objects to reduce JVM object allocations. The pool capacity is: {} elements.",
                    camelContextExtension.getExchangeFactory().getCapacity());
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
            LOG.info("Apache Camel {} ({}) started in {} (build:{} init:{} start:{})", getVersion(),
                    camelContextExtension.getName(), total, built, init, start);
        }
    }

    protected void doStartCamel() throws Exception {
        if (!camelContextExtension.getContextPlugin(CamelBeanPostProcessor.class).isEnabled()) {
            LOG.info("BeanPostProcessor is disabled. Dependency injection of Camel annotations in beans is not supported.");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Using ClassResolver={}, PackageScanClassResolver={}, ApplicationContextClassLoader={}, RouteController={}",
                    getClassResolver(),
                    PluginHelper.getPackageScanClassResolver(camelContextExtension), getApplicationContextClassLoader(),
                    getRouteController());
        }
        if (isStreamCaching()) {
            // stream caching is default enabled so lets report if it has been disabled
            LOG.debug("StreamCaching is disabled on CamelContext: {}", camelContextExtension.getName());
        }
        if (isBacklogTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            LOG.debug("Backlog Tracing is enabled on CamelContext: {}", camelContextExtension.getName());
        }
        if (isTracing()) {
            // tracing is added in the DefaultChannel so we can enable it on the fly
            LOG.info("Tracing is enabled on CamelContext: {}", camelContextExtension.getName());
        }
        if (isUseMDCLogging()) {
            // log if MDC has been enabled
            String pattern = getMDCLoggingKeysPattern();
            if (pattern != null) {
                LOG.info("MDC logging (keys-pattern: {}) is enabled on CamelContext: {}", pattern,
                        camelContextExtension.getName());
            } else {
                LOG.info("MDC logging is enabled on CamelContext: {}", camelContextExtension.getName());
            }
        }
        if (getDelayer() != null && getDelayer() > 0) {
            LOG.info("Delayer is enabled with: {} ms. on CamelContext: {}", getDelayer(), camelContextExtension.getName());
        }

        // start management strategy before lifecycles are started
        startService(getManagementStrategy());

        // start lifecycle strategies
        if (!lifecycleStrategies.isEmpty()) {
            StartupStep subStep
                    = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(),
                            "LifecycleStrategy onContextStarting");
            startServices(lifecycleStrategies);
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                try {
                    strategy.onContextStarting(this);
                } catch (VetoCamelContextStartException e) {
                    // okay we should not start Camel since it was vetoed
                    LOG.warn("Lifecycle strategy {} vetoed starting CamelContext ({}) due to: {}", strategy,
                            camelContextExtension.getName(),
                            e.getMessage());
                    throw e;
                } catch (Exception e) {
                    LOG.warn("Lifecycle strategy {} failed starting CamelContext ({}) due to: {}", strategy,
                            camelContextExtension.getName(),
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
                    = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(),
                            "StartupListener onCamelContextStarting");
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
            LOG.debug("Message DataType is enabled on CamelContext: {}", camelContextExtension.getName());
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
                      + " See more details at https://camel.apache.org/stream-caching.html");
        }

        if (isAllowUseOriginalMessage()) {
            LOG.debug("AllowUseOriginalMessage enabled because UseOriginalMessage is in use");
        }

        LOG.debug("Using HeadersMapFactory: {}", camelContextExtension.getHeadersMapFactory());
        if (isCaseInsensitiveHeaders() && !camelContextExtension.getHeadersMapFactory().isCaseInsensitive()) {
            LOG.info(
                    "HeadersMapFactory: {} is case-sensitive which can cause problems for protocols such as HTTP based, which rely on case-insensitive headers.",
                    camelContextExtension.getHeadersMapFactory());
        } else if (!isCaseInsensitiveHeaders()) {
            // notify user that the headers are sensitive which can be a problem
            LOG.info(
                    "Case-insensitive headers is not in use. This can cause problems for protocols such as HTTP based, which rely on case-insensitive headers.");
        }

        // lets log at INFO level if we are not using the default reactive executor
        final ReactiveExecutor reactiveExecutor = camelContextExtension.getReactiveExecutor();
        if (!reactiveExecutor.getClass().getSimpleName().equals("DefaultReactiveExecutor")) {
            LOG.info("Using ReactiveExecutor: {}", reactiveExecutor);
        } else {
            LOG.debug("Using ReactiveExecutor: {}", reactiveExecutor);
        }

        // lets log at INFO level if we are not using the default thread pool factory
        if (!getExecutorServiceManager().getThreadPoolFactory().getClass().getSimpleName().equals("DefaultThreadPoolFactory")) {
            LOG.info("Using ThreadPoolFactory: {}", getExecutorServiceManager().getThreadPoolFactory());
        } else {
            LOG.debug("Using ThreadPoolFactory: {}", getExecutorServiceManager().getThreadPoolFactory());
        }

        HealthCheckRegistry hcr = getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
        if (hcr != null && hcr.isEnabled()) {
            LOG.debug("Using HealthCheck: {}", hcr.getId());
        }

        // start routes
        if (doNotStartRoutesOnFirstStart) {
            LOG.debug("Skip starting routes as CamelContext has been configured with autoStartup=false");
        }

        if (getDumpRoutes() != null && !"false".equals(getDumpRoutes())) {
            doDumpRoutes();
        }

        if (!getRouteController().isSupervising()) {
            // invoke this logic to warmup the routes and if possible also start the routes (using default route controller)
            StartupStep subStep
                    = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(), "Start Routes");
            EventHelper.notifyCamelContextRoutesStarting(this);
            internalRouteStartupManager.doStartOrResumeRoutes(routeServices, true, !doNotStartRoutesOnFirstStart, false, true);
            EventHelper.notifyCamelContextRoutesStarted(this);
            startupStepRecorder.endStep(subStep);
        }

        // ensure extra dev consoles is loaded in case additional JARs has been dynamically added to the classpath
        if (devConsole) {
            StartupStep step = startupStepRecorder.beginStep(CamelContext.class, null, "Scan DevConsoles (phase 2)");
            DevConsoleRegistry dcr = getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
            if (dcr != null) {
                dcr.loadDevConsoles(true);
            }
            startupStepRecorder.endStep(step);
        }

        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(this);
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
                LOG.info("Apache Camel {} ({}) is shutting down (timeout:{})", getVersion(), camelContextExtension.getName(),
                        to);
            } else {
                LOG.info("Apache Camel {} ({}) is shutting down", getVersion(), camelContextExtension.getName());
            }
        }

        EventHelper.notifyCamelContextStopping(this);
        EventHelper.notifyCamelContextRoutesStopping(this);

        // Stop the route controller
        ServiceHelper.stopAndShutdownService(this.routeController);

        // stop route inputs in the same order as they were started, so we stop
        // the very first inputs at first
        try {
            // force shutting down routes as they may otherwise cause shutdown to hang
            if (shutdownStrategy != null) {
                shutdownStrategy.shutdownForced(this, camelContextExtension.getRouteStartupOrder());
            }
        } catch (Exception e) {
            LOG.warn("Error occurred while shutting down routes. This exception will be ignored.", e);
        }

        // shutdown await manager to trigger interrupt of blocked threads to
        // attempt to free these threads graceful
        final AsyncProcessorAwaitManager asyncProcessorAwaitManager = PluginHelper.getAsyncProcessorAwaitManager(this);
        InternalServiceManager.shutdownServices(this, asyncProcessorAwaitManager);

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
        InternalServiceManager.shutdownServices(this, list, false);

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
        internalServiceManager.stopConsumers();

        // the stop order is important

        // shutdown default error handler thread pool
        final ScheduledExecutorService errorHandlerExecutorService = PluginHelper.getErrorHandlerExecutorService(this);
        if (errorHandlerExecutorService != null) {
            // force shutting down the thread pool
            getExecutorServiceManager().shutdownNow(errorHandlerExecutorService);
        }

        // shutdown debugger
        ServiceHelper.stopAndShutdownService(getDebugger());

        InternalServiceManager.shutdownServices(this, endpoints.values());
        endpoints.clear();

        InternalServiceManager.shutdownServices(this, components.values());
        components.clear();

        InternalServiceManager.shutdownServices(this, languages.values());
        languages.clear();

        // shutdown services as late as possible (except type converters as they may be needed during the remainder of the stopping)
        internalServiceManager.shutdownServices();

        try {
            for (LifecycleStrategy strategy : lifecycleStrategies) {
                strategy.onContextStopped(this);
            }
        } catch (Exception e) {
            LOG.warn("Error occurred while stopping lifecycle strategies. This exception will be ignored.", e);
        }

        // must notify that we are stopped before stopping the management strategy
        EventHelper.notifyCamelContextStopped(this);

        // stop the notifier service
        if (getManagementStrategy() != null) {
            for (EventNotifier notifier : getManagementStrategy().getEventNotifiers()) {
                InternalServiceManager.shutdownServices(this, notifier);
            }
        }

        // shutdown management and lifecycle after all other services
        InternalServiceManager.shutdownServices(this, camelContextExtension.getManagementStrategy());
        InternalServiceManager.shutdownServices(this, camelContextExtension.getManagementMBeanAssembler());
        InternalServiceManager.shutdownServices(this, lifecycleStrategies);
        // do not clear lifecycleStrategies as we can start Camel again and get
        // the route back as before

        // shutdown executor service, reactive executor last
        InternalServiceManager.shutdownServices(this, executorServiceManager);
        InternalServiceManager.shutdownServices(this, camelContextExtension.getReactiveExecutor());

        // shutdown type converter and registry as late as possible
        ServiceHelper.stopService(typeConverter);
        ServiceHelper.stopService(typeConverterRegistry);
        ServiceHelper.stopService(camelContextExtension.getRegistry());

        // stop the lazy created so they can be re-created on restart
        forceStopLazyInitialization();

        if (startupSummaryLevel != StartupSummaryLevel.Off) {
            if (LOG.isInfoEnabled()) {
                String taken = TimeUtils.printDuration(stopWatch.taken(), true);
                LOG.info("Apache Camel {} ({}) shutdown in {} (uptime:{})", getVersion(), camelContextExtension.getName(),
                        taken, getUptime());
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

    public void removeRouteDefinitionsFromTemplate() throws Exception {
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

    private void stopServices(Object service) {
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
                    boolean isAutoStartup = routeService.isAutoStartup();
                    if (!addingRoutes || isAutoStartup) {
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
        StartupStep step = startupStepRecorder.beginStep(CamelContext.class, camelContextExtension.getName(),
                "Start Mandatory Services");
        initEagerMandatoryServices();
        startupStepRecorder.endStep(step);
        step = startupStepRecorder.beginStep(CamelContext.class, getName(), "Start Standard Services");
        doStartStandardServices();
        startupStepRecorder.endStep(step);
    }

    /**
     * Initializes eager some mandatory services which needs to warmup and be ready as this helps optimize Camel at
     * runtime.
     */
    protected void initEagerMandatoryServices() {
        camelContextExtension.initEagerMandatoryServices(isCaseInsensitiveHeaders(), this::createHeadersMapFactory);
    }

    protected void doStartStandardServices() {
        getVersion();
        getClassResolver();
        camelContextExtension.getRegistry();
        camelContextExtension.getBootstrapFactoryFinder();
        getTypeConverterRegistry();
        getInjector();
        camelContextExtension.getDefaultFactoryFinder();
        getPropertiesComponent();

        getExecutorServiceManager();
        camelContextExtension.getExchangeFactoryManager();
        camelContextExtension.getExchangeFactory();
        getShutdownStrategy();
        getUuidGenerator();

        if (isTypeConverterStatisticsEnabled()) {
            getTypeConverterRegistry().getStatistics().setStatisticsEnabled(isTypeConverterStatisticsEnabled());
        }

        // resolve simple language to initialize it
        resolveLanguage("simple");
    }

    /**
     * Force clear lazy initialization so they can be re-created on restart
     */
    protected void forceStopLazyInitialization() {
        injector = null;
        typeConverterRegistry = null;
        typeConverter = null;
    }

    /**
     * A pluggable strategy to allow an endpoint to be created without requiring a component to be its factory, such as
     * for looking up the URI inside some {@link Registry}
     *
     * @param  uri the uri for the endpoint to be created
     * @return     the newly created endpoint or null if it could not be resolved
     */
    protected Endpoint createEndpoint(String uri) {
        Object value = camelContextExtension.getRegistry().lookupByName(uri);
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

    protected FactoryFinder createBootstrapFactoryFinder(String path) {
        return PluginHelper.getFactoryFinderResolver(camelContextExtension).resolveBootstrapFactoryFinder(getClassResolver(),
                path);
    }

    protected FactoryFinder createFactoryFinder(String path) {
        return PluginHelper.getFactoryFinderResolver(camelContextExtension).resolveFactoryFinder(getClassResolver(), path);
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
        this.classResolver = internalServiceManager.addService(classResolver);
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
    public ManagementStrategy getManagementStrategy() {
        return camelContextExtension.getManagementStrategy();
    }

    @Override
    public void setManagementStrategy(ManagementStrategy managementStrategy) {
        camelContextExtension.setManagementStrategy(managementStrategy);
    }

    @Override
    public void disableJMX() {
        if (isNew()) {
            disableJMX = true;
        } else if (isInit() || isBuild()) {
            disableJMX = true;
            // we are still in initializing mode, so we can disable JMX, by
            // setting up management again
            camelContextExtension.setupManagement(null);
        } else {
            throw new IllegalStateException("Disabling JMX can only be done when CamelContext has not been started");
        }
    }

    public boolean isJMXDisabled() {
        String override = System.getProperty(JmxSystemPropertyKeys.DISABLED);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        return disableJMX;
    }

    void enableDebugging(DebuggerFactory df) throws Exception {
        setDebugging(true);
        Debugger newDebugger = df.createDebugger(this);
        if (newDebugger != null) {
            setDebugger(newDebugger);
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
        this.inflightRepository = internalServiceManager.addService(repository);
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

    @Override
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
    public String getDumpRoutes() {
        return dumpRoutes;
    }

    @Override
    public void setDumpRoutes(String dumpRoutes) {
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

    private DataFormat doResolveDataFormat(String name) {
        StartupStep step = null;
        // only record startup step during startup (not started)
        if (!isStarted() && startupStepRecorder.isEnabled()) {
            step = startupStepRecorder.beginStep(DataFormat.class, name, "Resolve DataFormat");
        }

        final DataFormat df = Optional
                .ofNullable(ResolverHelper.lookupDataFormatInRegistryWithFallback(getCamelContextReference(), name))
                .orElseGet(() -> PluginHelper.getDataFormatResolver(camelContextExtension).createDataFormat(name,
                        getCamelContextReference()));

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
    }

    @Override
    public DataFormat resolveDataFormat(String name) {
        return dataformats.computeIfAbsent(name, s -> doResolveDataFormat(name));
    }

    @Override
    public DataFormat createDataFormat(String name) {
        StartupStep step = null;
        // only record startup step during startup (not started)
        if (!isStarted() && startupStepRecorder.isEnabled()) {
            step = startupStepRecorder.beginStep(DataFormat.class, name, "Create DataFormat");
        }

        DataFormat answer
                = PluginHelper.getDataFormatResolver(camelContextExtension).createDataFormat(name, getCamelContextReference());

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
        this.shutdownStrategy = internalServiceManager.addService(shutdownStrategy);
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
        this.executorServiceManager = internalServiceManager.addService(executorServiceManager, false);
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
        this.messageHistoryFactory = internalServiceManager.addService(messageHistoryFactory);
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
        this.debugger = internalServiceManager.addService(debugger, true, false, true);
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
        this.tracer = internalServiceManager.addService(tracer, true, false, true);
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
    public void setTracingTemplates(boolean tracingTemplates) {
        this.traceTemplates = tracingTemplates;
    }

    @Override
    public boolean isTracingTemplates() {
        return traceTemplates != null && traceTemplates;
    }

    @Override
    public void setBacklogTracingTemplates(boolean backlogTracingTemplates) {
        this.backlogTraceTemplates = backlogTracingTemplates;
    }

    @Override
    public boolean isBacklogTracingTemplates() {
        return backlogTraceTemplates != null && backlogTraceTemplates;
    }

    @Override
    public void setBacklogTracingStandby(boolean backlogTracingStandby) {
        this.backlogTraceStandby = backlogTracingStandby;
    }

    @Override
    public boolean isBacklogTracingStandby() {
        return backlogTraceStandby != null && backlogTraceStandby;
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
        this.uuidGenerator = internalServiceManager.addService(uuidGenerator);
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
        this.streamCachingStrategy = internalServiceManager.addService(streamCachingStrategy, true, false, true);
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
        this.restRegistry = internalServiceManager.addService(restRegistry);
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
        this.restRegistryFactory = internalServiceManager.addService(restRegistryFactory);
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
    public Transformer resolveTransformer(String name) {
        return getTransformerRegistry().resolveTransformer(new TransformerKey(name));
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
        this.transformerRegistry = internalServiceManager.addService(transformerRegistry);
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
        this.validatorRegistry = internalServiceManager.addService(validatorRegistry);
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

    protected Map<String, RouteService> getRouteServices() {
        return routeServices;
    }

    @Override
    public String toString() {
        return "CamelContext(" + camelContextExtension.getName() + ")";
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

    protected abstract PeriodTaskResolver createPeriodTaskResolver();

    protected abstract PeriodTaskScheduler createPeriodTaskScheduler();

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

    protected abstract RoutesLoader createRoutesLoader();

    protected abstract ResourceLoader createResourceLoader();

    protected abstract ModelToXMLDumper createModelToXMLDumper();

    protected abstract ModelToYAMLDumper createModelToYAMLDumper();

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

    public abstract Processor createErrorHandler(Route route, Processor processor) throws Exception;

    public abstract void disposeModel();

    public abstract String getTestExcludeRoutes();

    @Override
    public ExtendedCamelContext getCamelContextExtension() {
        return camelContextExtension;
    }

    public void setName(String name) {
        camelContextExtension.setName(name);
    }

    @Override
    public String getName() {
        return camelContextExtension.getName();
    }

    public void setDescription(String description) {
        camelContextExtension.setDescription(description);
    }

    @Override
    public String getDescription() {
        return camelContextExtension.getDescription();
    }

    public FactoryFinder getBootstrapFactoryFinder() {
        return camelContextExtension.getBootstrapFactoryFinder();
    }

    public FactoryFinder getFactoryFinder(String path) {
        return camelContextExtension.getFactoryFinder(path);
    }

    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        camelContextExtension.addInterceptStrategy(interceptStrategy);
    }

    public StartupStepRecorder getStartupStepRecorder() {
        return camelContextExtension.getStartupStepRecorder();
    }

    public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {
        camelContextExtension.setStartupStepRecorder(startupStepRecorder);
    }

    public void addRoute(Route route) {
        synchronized (routes) {
            routes.add(route);
        }
    }

    public void removeRoute(Route route) {
        synchronized (routes) {
            routes.remove(route);
        }
    }

    public String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional) {
        return camelContextExtension.resolvePropertyPlaceholders(text, keepUnresolvedOptional);
    }

    public String getBasePackageScan() {
        return camelContextExtension.getBasePackageScan();
    }

    public void setBasePackageScan(String basePackageScan) {
        camelContextExtension.setBasePackageScan(basePackageScan);
    }

    byte getStatusPhase() {
        return status;
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
                MDC.put(MDC_CAMEL_CONTEXT_ID, camelContextExtension.getName());
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

    @Override
    public Registry getRegistry() {
        return camelContextExtension.getRegistry();
    }

    List<EndpointStrategy> getEndpointStrategies() {
        return endpointStrategies;
    }

    List<RouteStartupOrder> getRouteStartupOrder() {
        return routeStartupOrder;
    }

    InternalServiceManager getInternalServiceManager() {
        return internalServiceManager;
    }

    /*
     * This method exists for testing purposes only: we need to make sure we don't leak bootstraps.
     * This allows us to check for leaks without compromising the visibility/access on the DefaultCamelContextExtension.
     * Check the test AddRoutesAtRuntimeTest for details.
     */
    @SuppressWarnings("unused")
    private List<BootstrapCloseable> getBootstraps() {
        return camelContextExtension.getBootstraps();
    }
}
