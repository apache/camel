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
package org.apache.camel.core.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.TypeConverters;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesFunction;
import org.apache.camel.component.properties.PropertiesLocation;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.component.properties.PropertiesResolver;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckService;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.management.DefaultManagementLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.GlobalOptionsDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.PropertiesDefinition;
import org.apache.camel.model.RestContextRefDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestContainer;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformersDefinition;
import org.apache.camel.model.validator.ValidatorsDefinition;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory to create and initialize a
 * {@link CamelContext} and install routes either explicitly configured
 * or found by searching the classpath for Java classes which extend
 * {@link org.apache.camel.builder.RouteBuilder}.
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelContextFactoryBean<T extends ModelCamelContext> extends IdentifiedType implements RouteContainer, RestContainer {
    
    /**
     * JVM system property to control lazy loading of type converters.
     */
    public static final String LAZY_LOAD_TYPE_CONVERTERS = "CamelLazyLoadTypeConverters";
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCamelContextFactoryBean.class);

    @XmlTransient
    private List<RoutesBuilder> builders = new ArrayList<RoutesBuilder>();
    @XmlTransient
    private ClassLoader contextClassLoaderOnStart;
    @XmlTransient
    private final AtomicBoolean routesSetupDone = new AtomicBoolean();

    public AbstractCamelContextFactoryBean() {
        // Keep track of the class loader for when we actually do start things up
        contextClassLoaderOnStart = Thread.currentThread().getContextClassLoader();
    }

    public T getObject() throws Exception {
        return getContext();
    }

    public abstract Class<T> getObjectType();

    public boolean isSingleton() {
        return true;
    }

    public ClassLoader getContextClassLoaderOnStart() {
        return contextClassLoaderOnStart;
    }

    //CHECKSTYLE:OFF
    public void afterPropertiesSet() throws Exception {
        if (ObjectHelper.isEmpty(getId())) {
            throw new IllegalArgumentException("Id must be set");
        }

        // set the package scan resolver as soon as possible
        PackageScanClassResolver packageResolver = getBeanForType(PackageScanClassResolver.class);
        if (packageResolver != null) {
            LOG.info("Using custom PackageScanClassResolver: {}", packageResolver);
            getContext().setPackageScanClassResolver(packageResolver);
        }

        // then set custom properties
        Map<String, String> mergedOptions = new HashMap<>();
        if (getProperties() != null) {
            mergedOptions.putAll(getProperties().asMap());
        }
        if (getGlobalOptions() != null) {
            mergedOptions.putAll(getGlobalOptions().asMap());
        }

        getContext().setGlobalOptions(mergedOptions);

        // and enable lazy loading of type converters if applicable
        initLazyLoadTypeConverters();

        setupCustomServices();

        // set the custom registry if defined
        initCustomRegistry(getContext());

        // setup property placeholder so we got it as early as possible
        initPropertyPlaceholder();

        // setup JMX agent at first
        initJMXAgent();

        Tracer tracer = getBeanForType(Tracer.class);
        if (tracer != null) {
            // use formatter if there is a TraceFormatter bean defined
            TraceFormatter formatter = getBeanForType(TraceFormatter.class);
            if (formatter != null) {
                tracer.setFormatter(formatter);
            }
            LOG.info("Using custom Tracer: {}", tracer);
            getContext().addInterceptStrategy(tracer);
        }
        BacklogTracer backlogTracer = getBeanForType(BacklogTracer.class);
        if (backlogTracer != null) {
            LOG.info("Using custom BacklogTracer: {}", backlogTracer);
            getContext().addInterceptStrategy(backlogTracer);
        }
        HandleFault handleFault = getBeanForType(HandleFault.class);
        if (handleFault != null) {
            LOG.info("Using custom HandleFault: {}", handleFault);
            getContext().addInterceptStrategy(handleFault);
        }
        @SuppressWarnings("deprecation")
        org.apache.camel.processor.interceptor.Delayer delayer 
            = getBeanForType(org.apache.camel.processor.interceptor.Delayer.class);
        if (delayer != null) {
            LOG.info("Using custom Delayer: {}", delayer);
            getContext().addInterceptStrategy(delayer);
        }
        InflightRepository inflightRepository = getBeanForType(InflightRepository.class);
        if (inflightRepository != null) {
            LOG.info("Using custom InflightRepository: {}", inflightRepository);
            getContext().setInflightRepository(inflightRepository);
        }
        AsyncProcessorAwaitManager asyncProcessorAwaitManager = getBeanForType(AsyncProcessorAwaitManager.class);
        if (asyncProcessorAwaitManager != null) {
            LOG.info("Using custom AsyncProcessorAwaitManager: {}", asyncProcessorAwaitManager);
            getContext().setAsyncProcessorAwaitManager(asyncProcessorAwaitManager);
        }
        ManagementStrategy managementStrategy = getBeanForType(ManagementStrategy.class);
        if (managementStrategy != null) {
            LOG.info("Using custom ManagementStrategy: {}", managementStrategy);
            getContext().setManagementStrategy(managementStrategy);
        }
        ManagementNamingStrategy managementNamingStrategy = getBeanForType(ManagementNamingStrategy.class);
        if (managementNamingStrategy != null) {
            LOG.info("Using custom ManagementNamingStrategy: {}", managementNamingStrategy);
            getContext().getManagementStrategy().setManagementNamingStrategy(managementNamingStrategy);
        }
        EventFactory eventFactory = getBeanForType(EventFactory.class);
        if (eventFactory != null) {
            LOG.info("Using custom EventFactory: {}", eventFactory);
            getContext().getManagementStrategy().setEventFactory(eventFactory);
        }
        UnitOfWorkFactory unitOfWorkFactory = getBeanForType(UnitOfWorkFactory.class);
        if (unitOfWorkFactory != null) {
            LOG.info("Using custom UnitOfWorkFactory: {}", unitOfWorkFactory);
            getContext().setUnitOfWorkFactory(unitOfWorkFactory);
        }
        RuntimeEndpointRegistry runtimeEndpointRegistry = getBeanForType(RuntimeEndpointRegistry.class);
        if (runtimeEndpointRegistry != null) {
            LOG.info("Using custom RuntimeEndpointRegistry: {}", runtimeEndpointRegistry);
            getContext().setRuntimeEndpointRegistry(runtimeEndpointRegistry);
        }
        HeadersMapFactory headersMapFactory = getBeanForType(HeadersMapFactory.class);
        if (headersMapFactory != null) {
            LOG.info("Using custom HeadersMapFactory: {}", headersMapFactory);
            getContext().setHeadersMapFactory(headersMapFactory);
        }
        // custom type converters defined as <bean>s
        Map<String, TypeConverters> typeConverters = getContext().getRegistry().findByTypeWithName(TypeConverters.class);
        if (typeConverters != null && !typeConverters.isEmpty()) {
            for (Entry<String, TypeConverters> entry : typeConverters.entrySet()) {
                TypeConverters converter = entry.getValue();
                LOG.info("Adding custom TypeConverters with id: {} and implementation: {}", entry.getKey(), converter);
                getContext().getTypeConverterRegistry().addTypeConverters(converter);
            }
        }
        // set the event notifier strategies if defined
        Map<String, EventNotifier> eventNotifiers = getContext().getRegistry().findByTypeWithName(EventNotifier.class);
        if (eventNotifiers != null && !eventNotifiers.isEmpty()) {
            for (Entry<String, EventNotifier> entry : eventNotifiers.entrySet()) {
                EventNotifier notifier = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getManagementStrategy().getEventNotifiers().contains(notifier)) {
                    LOG.info("Using custom EventNotifier with id: {} and implementation: {}", entry.getKey(), notifier);
                    getContext().getManagementStrategy().addEventNotifier(notifier);
                }
            }
        }
        // set endpoint strategies if defined
        Map<String, EndpointStrategy> endpointStrategies = getContext().getRegistry().findByTypeWithName(EndpointStrategy.class);
        if (endpointStrategies != null && !endpointStrategies.isEmpty()) {
            for (Entry<String, EndpointStrategy> entry : endpointStrategies.entrySet()) {
                EndpointStrategy strategy = entry.getValue();
                LOG.info("Using custom EndpointStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                getContext().addRegisterEndpointCallback(strategy);
            }
        }
        // shutdown
        ShutdownStrategy shutdownStrategy = getBeanForType(ShutdownStrategy.class);
        if (shutdownStrategy != null) {
            LOG.info("Using custom ShutdownStrategy: " + shutdownStrategy);
            getContext().setShutdownStrategy(shutdownStrategy);
        }
        // add global interceptors
        Map<String, InterceptStrategy> interceptStrategies = getContext().getRegistry().findByTypeWithName(InterceptStrategy.class);
        if (interceptStrategies != null && !interceptStrategies.isEmpty()) {
            for (Entry<String, InterceptStrategy> entry : interceptStrategies.entrySet()) {
                InterceptStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getInterceptStrategies().contains(strategy)) {
                    LOG.info("Using custom InterceptStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                    getContext().addInterceptStrategy(strategy);
                }
            }
        }
        // set the lifecycle strategy if defined
        Map<String, LifecycleStrategy> lifecycleStrategies = getContext().getRegistry().findByTypeWithName(LifecycleStrategy.class);
        if (lifecycleStrategies != null && !lifecycleStrategies.isEmpty()) {
            for (Entry<String, LifecycleStrategy> entry : lifecycleStrategies.entrySet()) {
                LifecycleStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getLifecycleStrategies().contains(strategy)) {
                    LOG.info("Using custom LifecycleStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                    getContext().addLifecycleStrategy(strategy);
                }
            }
        }
        // cluster service
        Map<String, CamelClusterService> clusterServices = getContext().getRegistry().findByTypeWithName(CamelClusterService.class);
        if (clusterServices != null && !clusterServices.isEmpty()) {
            for (Entry<String, CamelClusterService> entry : clusterServices.entrySet()) {
                CamelClusterService service = entry.getValue();
                LOG.info("Using CamelClusterService with id: {} and implementation: {}", service.getId(), service);
                getContext().addService(service);
            }
        }
        // add route policy factories
        Map<String, RoutePolicyFactory> routePolicyFactories = getContext().getRegistry().findByTypeWithName(RoutePolicyFactory.class);
        if (routePolicyFactories != null && !routePolicyFactories.isEmpty()) {
            for (Entry<String, RoutePolicyFactory> entry : routePolicyFactories.entrySet()) {
                RoutePolicyFactory factory = entry.getValue();
                LOG.info("Using custom RoutePolicyFactory with id: {} and implementation: {}", entry.getKey(), factory);
                getContext().addRoutePolicyFactory(factory);
            }
        }
        // Health check registry
        HealthCheckRegistry healthCheckRegistry = getBeanForType(HealthCheckRegistry.class);
        if (healthCheckRegistry != null) {
            healthCheckRegistry.setCamelContext(getContext());
            LOG.info("Using HealthCheckRegistry: {}", healthCheckRegistry);
            getContext().setHealthCheckRegistry(healthCheckRegistry);
        } else {
            healthCheckRegistry = getContext().getHealthCheckRegistry();
            healthCheckRegistry.setCamelContext(getContext());
        }
        // Health check repository
        Set<HealthCheckRepository> repositories = getContext().getRegistry().findByType(HealthCheckRepository.class);
        if (ObjectHelper.isNotEmpty(repositories)) {
            for (HealthCheckRepository repository: repositories) {
                healthCheckRegistry.addRepository(repository);
            }
        }
        // Health check service
        HealthCheckService healthCheckService = getBeanForType(HealthCheckService.class);
        if (healthCheckService != null) {
            LOG.info("Using HealthCheckService: {}", healthCheckService);
            getContext().addService(healthCheckService);
        }
        // Route controller
        RouteController routeController = getBeanForType(RouteController.class);
        if (routeController != null) {
            LOG.info("Using RouteController: " + routeController);
            getContext().setRouteController(routeController);
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(getContext());

        // Set the application context and camelContext for the beanPostProcessor
        initBeanPostProcessor(getContext());

        // init camel context
        initCamelContext(getContext());

        // init stream caching strategy
        initStreamCachingStrategy();
    }
    //CHECKSTYLE:ON

    /**
     * Setup all the routes which must be done prior starting {@link CamelContext}.
     */
    protected void setupRoutes() throws Exception {
        if (routesSetupDone.compareAndSet(false, true)) {
            LOG.debug("Setting up routes");

            // mark that we are setting up routes
            getContext().setupRoutes(false);

            // must init route refs before we prepare the routes below
            initRouteRefs();

            // must init rest refs before we add the rests
            initRestRefs();

            // and add the rests
            getContext().addRestDefinitions(getRests());

            // convert rests into routes so we reuse routes for runtime
            for (RestDefinition rest : getRests()) {
                List<RouteDefinition> routes = rest.asRouteDefinition(getContext());
                for (RouteDefinition route : routes) {
                    getRoutes().add(route);
                }
            }
            // convert rests api-doc into routes so they are routes for runtime
            for (RestConfiguration config : getContext().getRestConfigurations()) {
                if (config.getApiContextPath() != null) {
                    // avoid adding rest-api multiple times, in case multiple RouteBuilder classes is added
                    // to the CamelContext, as we only want to setup rest-api once
                    // so we check all existing routes if they have rest-api route already added
                    boolean hasRestApi = false;
                    for (RouteDefinition route : getContext().getRouteDefinitions()) {
                        FromDefinition from = route.getInputs().get(0);
                        if (from.getUri() != null && from.getUri().startsWith("rest-api:")) {
                            hasRestApi = true;
                        }
                    }
                    if (!hasRestApi) {
                        RouteDefinition route = RestDefinition.asRouteApiDefinition(getContext(), config);
                        LOG.debug("Adding routeId: {} as rest-api route", route.getId());
                        getRoutes().add(route);
                    }
                }
            }

            // do special preparation for some concepts such as interceptors and policies
            // this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
            // using route builders. So we have here a little custom code to fix the JAXB gaps
            prepareRoutes();

            // and add the routes
            getContext().addRouteDefinitions(getRoutes());

            LOG.debug("Found JAXB created routes: {}", getRoutes());

            findRouteBuilders();
            installRoutes();

            // and we are now finished setting up the routes
            getContext().setupRoutes(true);
        }
    }

    /**
     * Do special preparation for some concepts such as interceptors and policies
     * this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
     * using route builders. So we have here a little custom code to fix the JAXB gaps
     */
    private void prepareRoutes() {
        for (RouteDefinition route : getRoutes()) {
            // sanity check first as the route is created using XML
            RouteDefinitionHelper.sanityCheckRoute(route);

            // leverage logic from route definition helper to prepare the route
            RouteDefinitionHelper.prepareRoute(getContext(), route, getOnExceptions(), getIntercepts(), getInterceptFroms(),
                    getInterceptSendToEndpoints(), getOnCompletions());

            // mark the route as prepared now
            route.markPrepared();
        }
    }

    protected abstract void initCustomRegistry(T context);
    
    @SuppressWarnings("deprecation")
    protected void initLazyLoadTypeConverters() {
        if (getLoadTypeConverters() != null) {
            getContext().setLoadTypeConverters(getLoadTypeConverters());
        }
        if (getLazyLoadTypeConverters() != null) {
            getContext().setLazyLoadTypeConverters(getLazyLoadTypeConverters());
        } else if (System.getProperty(LAZY_LOAD_TYPE_CONVERTERS) != null) {
            // suppose a JVM property to control it so we can use that for example for unit testing
            // to speedup testing by enabling lazy loading of type converters
            String lazy = System.getProperty(LAZY_LOAD_TYPE_CONVERTERS);
            if ("true".equalsIgnoreCase(lazy)) {
                getContext().setLazyLoadTypeConverters(true);
            } else if ("false".equalsIgnoreCase(lazy)) {
                getContext().setLazyLoadTypeConverters(false);
            } else {
                throw new IllegalArgumentException("System property with key " + LAZY_LOAD_TYPE_CONVERTERS + " has unknown value: " + lazy);
            }
        }
    }

    protected void initJMXAgent() throws Exception {
        CamelJMXAgentDefinition camelJMXAgent = getCamelJMXAgent();

        boolean disabled = false;
        if (camelJMXAgent != null) {
            disabled = camelJMXAgent.getDisabled() != null && CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getDisabled());
        }

        if (disabled) {
            LOG.info("JMXAgent disabled");
            // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
            getContext().getLifecycleStrategies().clear();
            // no need to add a lifecycle strategy as we do not need one as JMX is disabled
            getContext().setManagementStrategy(new DefaultManagementStrategy());
        } else if (camelJMXAgent != null) {
            LOG.info("JMXAgent enabled: {}", camelJMXAgent);
            DefaultManagementAgent agent = new DefaultManagementAgent(getContext());

            if (camelJMXAgent.getConnectorPort() != null) {
                agent.setConnectorPort(CamelContextHelper.parseInteger(getContext(), camelJMXAgent.getConnectorPort()));
            }
            if (camelJMXAgent.getCreateConnector() != null) {
                agent.setCreateConnector(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getCreateConnector()));
            }
            if (camelJMXAgent.getMbeanObjectDomainName() != null) {
                agent.setMBeanObjectDomainName(CamelContextHelper.parseText(getContext(), camelJMXAgent.getMbeanObjectDomainName()));
            }
            if (camelJMXAgent.getMbeanServerDefaultDomain() != null) {
                agent.setMBeanServerDefaultDomain(CamelContextHelper.parseText(getContext(), camelJMXAgent.getMbeanServerDefaultDomain()));
            }
            if (camelJMXAgent.getRegistryPort() != null) {
                agent.setRegistryPort(CamelContextHelper.parseInteger(getContext(), camelJMXAgent.getRegistryPort()));
            }
            if (camelJMXAgent.getServiceUrlPath() != null) {
                agent.setServiceUrlPath(CamelContextHelper.parseText(getContext(), camelJMXAgent.getServiceUrlPath()));
            }
            if (camelJMXAgent.getUsePlatformMBeanServer() != null) {
                agent.setUsePlatformMBeanServer(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getUsePlatformMBeanServer()));
            }
            if (camelJMXAgent.getOnlyRegisterProcessorWithCustomId() != null) {
                agent.setOnlyRegisterProcessorWithCustomId(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getOnlyRegisterProcessorWithCustomId()));
            }
            if (camelJMXAgent.getRegisterAlways() != null) {
                agent.setRegisterAlways(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getRegisterAlways()));
            }
            if (camelJMXAgent.getRegisterNewRoutes() != null) {
                agent.setRegisterNewRoutes(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getRegisterNewRoutes()));
            }
            if (camelJMXAgent.getIncludeHostName() != null) {
                agent.setIncludeHostName(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getIncludeHostName()));
            }
            if (camelJMXAgent.getUseHostIPAddress() != null) {
                agent.setUseHostIPAddress(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getUseHostIPAddress()));
            }
            if (camelJMXAgent.getMask() != null) {
                agent.setMask(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getMask()));
            }
            if (camelJMXAgent.getLoadStatisticsEnabled() != null) {
                agent.setLoadStatisticsEnabled(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getLoadStatisticsEnabled()));
            }
            if (camelJMXAgent.getEndpointRuntimeStatisticsEnabled() != null) {
                agent.setEndpointRuntimeStatisticsEnabled(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getEndpointRuntimeStatisticsEnabled()));
            }
            if (camelJMXAgent.getStatisticsLevel() != null) {
                String level = CamelContextHelper.parseText(getContext(), camelJMXAgent.getStatisticsLevel());
                ManagementStatisticsLevel msLevel = getContext().getTypeConverter().mandatoryConvertTo(ManagementStatisticsLevel.class, level);
                agent.setStatisticsLevel(msLevel);
            }

            ManagementStrategy managementStrategy = new ManagedManagementStrategy(getContext(), agent);
            getContext().setManagementStrategy(managementStrategy);

            // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
            getContext().getLifecycleStrategies().clear();
            getContext().addLifecycleStrategy(new DefaultManagementLifecycleStrategy(getContext()));
        }
    }

    protected void initStreamCachingStrategy() throws Exception {
        CamelStreamCachingStrategyDefinition streamCaching = getCamelStreamCachingStrategy();
        if (streamCaching == null) {
            return;
        }

        Boolean enabled = CamelContextHelper.parseBoolean(getContext(), streamCaching.getEnabled());
        if (enabled != null) {
            getContext().getStreamCachingStrategy().setEnabled(enabled);
        }
        String spoolDirectory = CamelContextHelper.parseText(getContext(), streamCaching.getSpoolDirectory());
        if (spoolDirectory != null) {
            getContext().getStreamCachingStrategy().setSpoolDirectory(spoolDirectory);
        }
        Long spoolThreshold = CamelContextHelper.parseLong(getContext(), streamCaching.getSpoolThreshold());
        if (spoolThreshold != null) {
            getContext().getStreamCachingStrategy().setSpoolThreshold(spoolThreshold);
        }
        Integer spoolUsedHeap = CamelContextHelper.parseInteger(getContext(), streamCaching.getSpoolUsedHeapMemoryThreshold());
        if (spoolUsedHeap != null) {
            getContext().getStreamCachingStrategy().setSpoolUsedHeapMemoryThreshold(spoolUsedHeap);
        }
        String limit = CamelContextHelper.parseText(getContext(), streamCaching.getSpoolUsedHeapMemoryLimit());
        if (limit != null) {
            StreamCachingStrategy.SpoolUsedHeapMemoryLimit ul = CamelContextHelper.mandatoryConvertTo(getContext(), StreamCachingStrategy.SpoolUsedHeapMemoryLimit.class, limit);
            getContext().getStreamCachingStrategy().setSpoolUsedHeapMemoryLimit(ul);
        }
        String spoolChiper = CamelContextHelper.parseText(getContext(), streamCaching.getSpoolChiper());
        if (spoolChiper != null) {
            getContext().getStreamCachingStrategy().setSpoolChiper(spoolChiper);
        }
        Boolean remove = CamelContextHelper.parseBoolean(getContext(), streamCaching.getRemoveSpoolDirectoryWhenStopping());
        if (remove != null) {
            getContext().getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(remove);
        }
        Boolean statisticsEnabled = CamelContextHelper.parseBoolean(getContext(), streamCaching.getStatisticsEnabled());
        if (statisticsEnabled != null) {
            getContext().getStreamCachingStrategy().getStatistics().setStatisticsEnabled(statisticsEnabled);
        }
        Boolean anySpoolRules = CamelContextHelper.parseBoolean(getContext(), streamCaching.getAnySpoolRules());
        if (anySpoolRules != null) {
            getContext().getStreamCachingStrategy().setAnySpoolRules(anySpoolRules);
        }
        String spoolRules = CamelContextHelper.parseText(getContext(), streamCaching.getSpoolRules());
        if (spoolRules != null) {
            Iterator<Object> it = ObjectHelper.createIterator(spoolRules);
            while (it.hasNext()) {
                String name = it.next().toString();
                StreamCachingStrategy.SpoolRule rule = getContext().getRegistry().lookupByNameAndType(name, StreamCachingStrategy.SpoolRule.class);
                if (rule != null) {
                    getContext().getStreamCachingStrategy().addSpoolRule(rule);
                }
            }
        }
    }

    protected void initPropertyPlaceholder() throws Exception {
        if (getCamelPropertyPlaceholder() != null) {
            CamelPropertyPlaceholderDefinition def = getCamelPropertyPlaceholder();

            List<PropertiesLocation> locations = new ArrayList<>();

            if (def.getLocation() != null) {
                ObjectHelper.createIterable(def.getLocation()).forEach(
                    location -> locations.add(new PropertiesLocation((String) location))
                );
            }
            if (def.getLocations() != null) {
                def.getLocations().forEach(
                    definition -> locations.add(definition.toLocation())
                );
            }

            PropertiesComponent pc = new PropertiesComponent();
            pc.setLocations(locations);
            pc.setEncoding(def.getEncoding());

            if (def.isCache() != null) {
                pc.setCache(def.isCache());
            }

            if (def.isIgnoreMissingLocation() != null) {
                pc.setIgnoreMissingLocation(def.isIgnoreMissingLocation());
            }

            // if using a custom resolver
            if (ObjectHelper.isNotEmpty(def.getPropertiesResolverRef())) {
                PropertiesResolver resolver = CamelContextHelper.mandatoryLookup(getContext(), def.getPropertiesResolverRef(),
                                                                                 PropertiesResolver.class);
                pc.setPropertiesResolver(resolver);
            }

            // if using a custom parser
            if (ObjectHelper.isNotEmpty(def.getPropertiesParserRef())) {
                PropertiesParser parser = CamelContextHelper.mandatoryLookup(getContext(), def.getPropertiesParserRef(),
                                                                             PropertiesParser.class);
                pc.setPropertiesParser(parser);
            }
            
            pc.setPropertyPrefix(def.getPropertyPrefix());
            pc.setPropertySuffix(def.getPropertySuffix());
            
            if (def.isFallbackToUnaugmentedProperty() != null) {
                pc.setFallbackToUnaugmentedProperty(def.isFallbackToUnaugmentedProperty());
            }
            if (def.getDefaultFallbackEnabled() != null) {
                pc.setDefaultFallbackEnabled(def.getDefaultFallbackEnabled());
            }
            
            pc.setPrefixToken(def.getPrefixToken());
            pc.setSuffixToken(def.getSuffixToken());

            if (def.getFunctions() != null && !def.getFunctions().isEmpty()) {
                for (CamelPropertyPlaceholderFunctionDefinition function : def.getFunctions()) {
                    String ref = function.getRef();
                    PropertiesFunction pf = CamelContextHelper.mandatoryLookup(getContext(), ref, PropertiesFunction.class);
                    pc.addFunction(pf);
                }
            }

            // register the properties component
            getContext().addComponent("properties", pc);
        }
    }

    protected void initRouteRefs() throws Exception {
        // add route refs to existing routes
        if (getRouteRefs() != null) {
            for (RouteContextRefDefinition ref : getRouteRefs()) {
                List<RouteDefinition> defs = ref.lookupRoutes(getContext());
                for (RouteDefinition def : defs) {
                    LOG.debug("Adding route from {} -> {}", ref, def);
                    // add in top as they are most likely to be common/shared
                    // which you may want to start first
                    getRoutes().add(0, def);
                }
            }
        }
    }

    protected void initRestRefs() throws Exception {
        // add rest refs to existing rests
        if (getRestRefs() != null) {
            for (RestContextRefDefinition ref : getRestRefs()) {
                List<RestDefinition> defs = ref.lookupRests(getContext());
                for (RestDefinition def : defs) {
                    LOG.debug("Adding rest from {} -> {}", ref, def);
                    // add in top as they are most likely to be common/shared
                    // which you may want to start first
                    getRests().add(0, def);
                }
            }
        }
    }

    protected abstract <S> S getBeanForType(Class<S> clazz);

    public void destroy() throws Exception {
        routesSetupDone.set(false);
        getContext().stop();
    }

    // Properties
    // -------------------------------------------------------------------------
    public T getContext() {
        return getContext(true);
    }

    public abstract T getContext(boolean create);

    public abstract List<RouteDefinition> getRoutes();

    public abstract List<RestDefinition> getRests();

    public abstract RestConfigurationDefinition getRestConfiguration();

    public abstract List<? extends AbstractCamelEndpointFactoryBean> getEndpoints();

    public abstract List<? extends AbstractCamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies();

    public abstract List<InterceptDefinition> getIntercepts();

    public abstract List<InterceptFromDefinition> getInterceptFroms();

    public abstract List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints();

    @Deprecated
    public abstract PropertiesDefinition getProperties();

    public abstract GlobalOptionsDefinition getGlobalOptions();

    public abstract String[] getPackages();

    public abstract PackageScanDefinition getPackageScan();

    public abstract void setPackageScan(PackageScanDefinition packageScan);

    public abstract ContextScanDefinition getContextScan();

    public abstract void setContextScan(ContextScanDefinition contextScan);

    public abstract CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder();

    public abstract String getTrace();

    public abstract String getMessageHistory();

    public abstract String getLogMask();

    public abstract String getLogExhaustedMessageBody();

    public abstract String getStreamCache();

    public abstract String getDelayer();

    public abstract String getHandleFault();

    public abstract String getAutoStartup();

    public abstract String getUseMDCLogging();

    public abstract String getUseDataType();

    public abstract String getUseBreadcrumb();

    public abstract String getAllowUseOriginalMessage();

    public abstract String getRuntimeEndpointRegistryEnabled();

    public abstract String getManagementNamePattern();

    public abstract String getThreadNamePattern();

    /**
     * @deprecated this option is no longer supported, will be removed in a future Camel release.
     */
    @Deprecated
    public abstract Boolean getLazyLoadTypeConverters();

    public abstract Boolean getLoadTypeConverters();

    public abstract Boolean getTypeConverterStatisticsEnabled();

    public abstract LoggingLevel getTypeConverterExistsLoggingLevel();

    public abstract TypeConverterExists getTypeConverterExists();

    public abstract CamelJMXAgentDefinition getCamelJMXAgent();

    public abstract CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy();

    public abstract List<RouteBuilderDefinition> getBuilderRefs();

    public abstract List<RouteContextRefDefinition> getRouteRefs();

    public abstract List<RestContextRefDefinition> getRestRefs();

    public abstract String getErrorHandlerRef();

    public abstract DataFormatsDefinition getDataFormats();

    public abstract TransformersDefinition getTransformers();

    public abstract ValidatorsDefinition getValidators();

    public abstract List<OnExceptionDefinition> getOnExceptions();

    public abstract List<OnCompletionDefinition> getOnCompletions();

    public abstract ShutdownRoute getShutdownRoute();

    public abstract ShutdownRunningTask getShutdownRunningTask();

    public abstract List<ThreadPoolProfileDefinition> getThreadPoolProfiles();

    public abstract String getDependsOn();

    public abstract List<AbstractCamelFactoryBean<?>> getBeansFactory();

    public abstract List<?> getBeans();

    public abstract ServiceCallConfigurationDefinition getDefaultServiceCallConfiguration();

    public abstract List<ServiceCallConfigurationDefinition> getServiceCallConfigurations();

    public abstract HystrixConfigurationDefinition getDefaultHystrixConfiguration();

    public abstract List<HystrixConfigurationDefinition> getHystrixConfigurations();

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Initializes the context
     *
     * @param ctx the context
     * @throws Exception is thrown if error occurred
     */
    protected void initCamelContext(T ctx) throws Exception {
        final T context = getContext();

        if (getStreamCache() != null) {
            ctx.setStreamCaching(CamelContextHelper.parseBoolean(context, getStreamCache()));
        }
        if (getTrace() != null) {
            ctx.setTracing(CamelContextHelper.parseBoolean(context, getTrace()));
        }
        if (getMessageHistory() != null) {
            ctx.setMessageHistory(CamelContextHelper.parseBoolean(context, getMessageHistory()));
        }
        if (getLogMask() != null) {
            ctx.setLogMask(CamelContextHelper.parseBoolean(context, getLogMask()));
        }
        if (getLogExhaustedMessageBody() != null) {
            ctx.setLogExhaustedMessageBody(CamelContextHelper.parseBoolean(context, getLogExhaustedMessageBody()));
        }
        if (getDelayer() != null) {
            ctx.setDelayer(CamelContextHelper.parseLong(context, getDelayer()));
        }
        if (getHandleFault() != null) {
            ctx.setHandleFault(CamelContextHelper.parseBoolean(context, getHandleFault()));
        }
        if (getErrorHandlerRef() != null) {
            ctx.setErrorHandlerBuilder(new ErrorHandlerBuilderRef(getErrorHandlerRef()));
        }
        if (getAutoStartup() != null) {
            ctx.setAutoStartup(CamelContextHelper.parseBoolean(context, getAutoStartup()));
        }
        if (getUseMDCLogging() != null) {
            ctx.setUseMDCLogging(CamelContextHelper.parseBoolean(context, getUseMDCLogging()));
        }
        if (getUseDataType() != null) {
            ctx.setUseDataType(CamelContextHelper.parseBoolean(context, getUseDataType()));
        }
        if (getUseBreadcrumb() != null) {
            ctx.setUseBreadcrumb(CamelContextHelper.parseBoolean(context, getUseBreadcrumb()));
        }
        if (getAllowUseOriginalMessage() != null) {
            ctx.setAllowUseOriginalMessage(CamelContextHelper.parseBoolean(context, getAllowUseOriginalMessage()));
        }
        if (getRuntimeEndpointRegistryEnabled() != null) {
            ctx.getRuntimeEndpointRegistry().setEnabled(CamelContextHelper.parseBoolean(context, getRuntimeEndpointRegistryEnabled()));
        }
        if (getManagementNamePattern() != null) {
            ctx.getManagementNameStrategy().setNamePattern(CamelContextHelper.parseText(context, getManagementNamePattern()));
        }
        if (getThreadNamePattern() != null) {
            ctx.getExecutorServiceManager().setThreadNamePattern(CamelContextHelper.parseText(context, getThreadNamePattern()));
        }
        if (getShutdownRoute() != null) {
            ctx.setShutdownRoute(getShutdownRoute());
        }
        if (getShutdownRunningTask() != null) {
            ctx.setShutdownRunningTask(getShutdownRunningTask());
        }
        if (getDataFormats() != null) {
            ctx.setDataFormats(getDataFormats().asMap());
        }
        if (getTransformers() != null) {
            ctx.setTransformers(getTransformers().getTransformers());
        }
        if (getValidators() != null) {
            ctx.setValidators(getValidators().getValidators());
        }
        if (getTypeConverterStatisticsEnabled() != null) {
            ctx.setTypeConverterStatisticsEnabled(getTypeConverterStatisticsEnabled());
        }
        if (getTypeConverterExists() != null) {
            ctx.getTypeConverterRegistry().setTypeConverterExists(getTypeConverterExists());
        }
        if (getTypeConverterExistsLoggingLevel() != null) {
            ctx.getTypeConverterRegistry().setTypeConverterExistsLoggingLevel(getTypeConverterExistsLoggingLevel());
        }
        if (getRestConfiguration() != null) {
            ctx.setRestConfiguration(getRestConfiguration().asRestConfiguration(ctx));
        }
        if (getDefaultServiceCallConfiguration() != null) {
            ctx.setServiceCallConfiguration(getDefaultServiceCallConfiguration());
        }
        if (getServiceCallConfigurations() != null) {
            for (ServiceCallConfigurationDefinition bean : getServiceCallConfigurations()) {
                ctx.addServiceCallConfiguration(bean.getId(), bean);
            }
        }
        if (getDefaultHystrixConfiguration() != null) {
            ctx.setHystrixConfiguration(getDefaultHystrixConfiguration());
        }
        if (getHystrixConfigurations() != null) {
            for (HystrixConfigurationDefinition bean : getHystrixConfigurations()) {
                ctx.addHystrixConfiguration(bean.getId(), bean);
            }
        }
    }

    protected void initThreadPoolProfiles(T context) throws Exception {
        Set<String> defaultIds = new HashSet<String>();

        // lookup and use custom profiles from the registry
        Map<String, ThreadPoolProfile> profiles = context.getRegistry().findByTypeWithName(ThreadPoolProfile.class);
        if (profiles != null && !profiles.isEmpty()) {
            for (Entry<String, ThreadPoolProfile> entry : profiles.entrySet()) {
                ThreadPoolProfile profile = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (profile.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: {} and implementation: {}", entry.getKey(), profile);
                    context.getExecutorServiceManager().setDefaultThreadPoolProfile(profile);
                    defaultIds.add(entry.getKey());
                } else {
                    context.getExecutorServiceManager().registerThreadPoolProfile(profile);
                }
            }
        }

        // use custom profiles defined in the CamelContext
        if (getThreadPoolProfiles() != null && !getThreadPoolProfiles().isEmpty()) {
            for (ThreadPoolProfileDefinition definition : getThreadPoolProfiles()) {
                if (definition.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: {} and implementation: {}", definition.getId(), definition);
                    context.getExecutorServiceManager().setDefaultThreadPoolProfile(asThreadPoolProfile(context, definition));
                    defaultIds.add(definition.getId());
                } else {
                    context.getExecutorServiceManager().registerThreadPoolProfile(asThreadPoolProfile(context, definition));
                }
            }
        }

        // validate at most one is defined
        if (defaultIds.size() > 1) {
            throw new IllegalArgumentException("Only exactly one default ThreadPoolProfile is allowed, was " + defaultIds.size() + " ids: " + defaultIds);
        }
    }

    /**
     * Creates a {@link ThreadPoolProfile} instance based on the definition.
     *
     * @param context    the camel context
     * @return           the profile
     * @throws Exception is thrown if error creating the profile
     */
    private ThreadPoolProfile asThreadPoolProfile(CamelContext context, ThreadPoolProfileDefinition definition) throws Exception {
        ThreadPoolProfile answer = new ThreadPoolProfile();
        answer.setId(definition.getId());
        answer.setDefaultProfile(definition.getDefaultProfile());
        answer.setPoolSize(CamelContextHelper.parseInteger(context, definition.getPoolSize()));
        answer.setMaxPoolSize(CamelContextHelper.parseInteger(context, definition.getMaxPoolSize()));
        answer.setKeepAliveTime(CamelContextHelper.parseLong(context, definition.getKeepAliveTime()));
        answer.setMaxQueueSize(CamelContextHelper.parseInteger(context, definition.getMaxQueueSize()));
        answer.setAllowCoreThreadTimeOut(CamelContextHelper.parseBoolean(context, definition.getAllowCoreThreadTimeOut()));
        answer.setRejectedPolicy(definition.getRejectedPolicy());
        answer.setTimeUnit(definition.getTimeUnit());
        return answer;
    }

    protected abstract void initBeanPostProcessor(T context);

    /**
     * Strategy to install all available routes into the context
     */
    protected void installRoutes() throws Exception {
        List<RouteBuilder> builders = new ArrayList<RouteBuilder>();

        // lets add RoutesBuilder's added from references
        if (getBuilderRefs() != null) {
            for (RouteBuilderDefinition builderRef : getBuilderRefs()) {
                RoutesBuilder routes = builderRef.createRoutes(getContext());
                if (routes != null) {
                    this.builders.add(routes);
                } else {
                    throw new CamelException("Cannot find any routes with this RouteBuilder reference: " + builderRef);
                }
            }
        }

        // install already configured routes
        for (RoutesBuilder routeBuilder : this.builders) {
            getContext().addRoutes(routeBuilder);
        }

        // install builders
        for (RouteBuilder builder : builders) {
            // Inject the annotated resource
            postProcessBeforeInit(builder);
            getContext().addRoutes(builder);
        }
    }

    protected abstract void postProcessBeforeInit(RouteBuilder builder);

    /**
     * Strategy method to try find {@link org.apache.camel.builder.RouteBuilder} instances on the classpath
     */
    protected void findRouteBuilders() throws Exception {
        // package scan
        addPackageElementContentsToScanDefinition();
        PackageScanDefinition packageScanDef = getPackageScan();
        if (packageScanDef != null && packageScanDef.getPackages().size() > 0) {
            // use package scan filter
            PatternBasedPackageScanFilter filter = new PatternBasedPackageScanFilter();
            // support property placeholders in include and exclude
            for (String include : packageScanDef.getIncludes()) {
                include = getContext().resolvePropertyPlaceholders(include);
                filter.addIncludePattern(include);
            }
            for (String exclude : packageScanDef.getExcludes()) {
                exclude = getContext().resolvePropertyPlaceholders(exclude);
                filter.addExcludePattern(exclude);
            }

            String[] normalized = normalizePackages(getContext(), packageScanDef.getPackages());
            findRouteBuildersByPackageScan(normalized, filter, builders);
        }

        // context scan
        ContextScanDefinition contextScanDef = getContextScan();
        if (contextScanDef != null) {
            // use package scan filter
            PatternBasedPackageScanFilter filter = new PatternBasedPackageScanFilter();
            // support property placeholders in include and exclude
            for (String include : contextScanDef.getIncludes()) {
                include = getContext().resolvePropertyPlaceholders(include);
                filter.addIncludePattern(include);
            }
            for (String exclude : contextScanDef.getExcludes()) {
                exclude = getContext().resolvePropertyPlaceholders(exclude);
                filter.addExcludePattern(exclude);
            }
            // lets be false by default, to skip prototype beans
            boolean includeNonSingletons = contextScanDef.getIncludeNonSingletons() != null ? contextScanDef.getIncludeNonSingletons() : false;
            findRouteBuildersByContextScan(filter, includeNonSingletons, builders);
        }
    }

    protected abstract void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception;

    protected abstract void findRouteBuildersByContextScan(PackageScanFilter filter, boolean includeNonSingletons, List<RoutesBuilder> builders) throws Exception;

    private void addPackageElementContentsToScanDefinition() {
        PackageScanDefinition packageScanDef = getPackageScan();

        if (getPackages() != null && getPackages().length > 0) {
            if (packageScanDef == null) {
                packageScanDef = new PackageScanDefinition();
                setPackageScan(packageScanDef);
            }

            for (String pkg : getPackages()) {
                packageScanDef.getPackages().add(pkg);
            }
        }
    }

    private String[] normalizePackages(T context, List<String> unnormalized) throws Exception {
        List<String> packages = new ArrayList<String>();
        for (String name : unnormalized) {
            // it may use property placeholders
            name = context.resolvePropertyPlaceholders(name);
            name = ObjectHelper.normalizeClassName(name);
            if (ObjectHelper.isNotEmpty(name)) {
                LOG.trace("Using package: {} to scan for RouteBuilder classes", name);
                packages.add(name);
            }
        }
        return packages.toArray(new String[packages.size()]);
    }

    private void setupCustomServices() {
        ModelJAXBContextFactory modelJAXBContextFactory = getBeanForType(ModelJAXBContextFactory.class);
        if (modelJAXBContextFactory != null) {
            LOG.info("Using custom ModelJAXBContextFactory: {}", modelJAXBContextFactory);
            getContext().setModelJAXBContextFactory(modelJAXBContextFactory);
        }
        ClassResolver classResolver = getBeanForType(ClassResolver.class);
        if (classResolver != null) {
            LOG.info("Using custom ClassResolver: {}", classResolver);
            getContext().setClassResolver(classResolver);
        }
        FactoryFinderResolver factoryFinderResolver = getBeanForType(FactoryFinderResolver.class);
        if (factoryFinderResolver != null) {
            LOG.info("Using custom FactoryFinderResolver: {}", factoryFinderResolver);
            getContext().setFactoryFinderResolver(factoryFinderResolver);
        }
        ExecutorServiceManager executorServiceStrategy = getBeanForType(ExecutorServiceManager.class);
        if (executorServiceStrategy != null) {
            LOG.info("Using custom ExecutorServiceStrategy: {}", executorServiceStrategy);
            getContext().setExecutorServiceManager(executorServiceStrategy);
        }
        ThreadPoolFactory threadPoolFactory = getBeanForType(ThreadPoolFactory.class);
        if (threadPoolFactory != null) {
            LOG.info("Using custom ThreadPoolFactory: {}", threadPoolFactory);
            getContext().getExecutorServiceManager().setThreadPoolFactory(threadPoolFactory);
        }
        ProcessorFactory processorFactory = getBeanForType(ProcessorFactory.class);
        if (processorFactory != null) {
            LOG.info("Using custom ProcessorFactory: {}", processorFactory);
            getContext().setProcessorFactory(processorFactory);
        }
        Debugger debugger = getBeanForType(Debugger.class);
        if (debugger != null) {
            LOG.info("Using custom Debugger: {}", debugger);
            getContext().setDebugger(debugger);
        }
        UuidGenerator uuidGenerator = getBeanForType(UuidGenerator.class);
        if (uuidGenerator != null) {
            LOG.info("Using custom UuidGenerator: {}", uuidGenerator);
            getContext().setUuidGenerator(uuidGenerator);
        }
        NodeIdFactory nodeIdFactory = getBeanForType(NodeIdFactory.class);
        if (nodeIdFactory != null) {
            LOG.info("Using custom NodeIdFactory: {}", nodeIdFactory);
            getContext().setNodeIdFactory(nodeIdFactory);
        }
        StreamCachingStrategy streamCachingStrategy = getBeanForType(StreamCachingStrategy.class);
        if (streamCachingStrategy != null) {
            LOG.info("Using custom StreamCachingStrategy: {}", streamCachingStrategy);
            getContext().setStreamCachingStrategy(streamCachingStrategy);
        }
        MessageHistoryFactory messageHistoryFactory = getBeanForType(MessageHistoryFactory.class);
        if (messageHistoryFactory != null) {
            LOG.info("Using custom MessageHistoryFactory: {}", messageHistoryFactory);
            getContext().setMessageHistoryFactory(messageHistoryFactory);
        }
    }
}
