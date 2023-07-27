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
package org.apache.camel.core.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ManagementMBeansLevel;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.TypeConverters;
import org.apache.camel.ValueHolder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesLocation;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.impl.engine.DefaultManagementStrategy;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.impl.engine.ValidatorKey;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.ErrorHandlerDefinition;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.GlobalOptionsDefinition;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RestContextRefDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteConfigurationContainer;
import org.apache.camel.model.RouteConfigurationContextRefDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RouteTemplateContainer;
import org.apache.camel.model.RouteTemplateContextRefDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteContainer;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestContainer;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.transformer.TransformersDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.model.validator.ValidatorsDefinition;
import org.apache.camel.reifier.transformer.TransformerReifier;
import org.apache.camel.reifier.validator.ValidatorReifier;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataType;
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
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.Validator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory to create and initialize a {@link CamelContext} and install routes either explicitly configured or found by
 * searching the classpath for Java classes which extend {@link org.apache.camel.builder.RouteBuilder}.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelContextFactoryBean<T extends ModelCamelContext> extends IdentifiedType
        implements RouteTemplateContainer, RouteConfigurationContainer, RouteContainer, RestContainer,
        TemplatedRouteContainer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCamelContextFactoryBean.class);

    @XmlTransient
    private final List<RoutesBuilder> builders = new ArrayList<>();
    @XmlTransient
    private final ClassLoader contextClassLoaderOnStart;
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

    public void afterPropertiesSet() throws Exception {
        if (org.apache.camel.util.ObjectHelper.isEmpty(getId())) {
            throw new IllegalArgumentException("Id must be set");
        }

        // set properties as early as possible
        PropertiesComponent pc = getBeanForType(PropertiesComponent.class);
        if (pc != null) {
            LOG.debug("Using PropertiesComponent: {}", pc);
            getContext().setPropertiesComponent(pc);
        }

        // set the package scan resolver as soon as possible
        PackageScanClassResolver packageResolver = getBeanForType(PackageScanClassResolver.class);
        if (packageResolver != null) {
            LOG.info("Using custom PackageScanClassResolver: {}", packageResolver);
            getContext().getCamelContextExtension().addContextPlugin(PackageScanClassResolver.class, packageResolver);
        }

        // also set type converter registry as early as possible
        TypeConverterRegistry tcr = getBeanForType(TypeConverterRegistry.class);
        if (tcr != null) {
            LOG.info("Using custom TypeConverterRegistry: {}", tcr);
            getContext().setTypeConverterRegistry(tcr);
        }
        if (getTypeConverterStatisticsEnabled() != null) {
            getContext().setTypeConverterStatisticsEnabled(
                    CamelContextHelper.parseBoolean(getContext(), getTypeConverterStatisticsEnabled()));
        }
        if (getTypeConverterExists() != null) {
            getContext().getTypeConverterRegistry().setTypeConverterExists(getTypeConverterExists());
        }
        if (getTypeConverterExistsLoggingLevel() != null) {
            getContext().getTypeConverterRegistry().setTypeConverterExistsLoggingLevel(getTypeConverterExistsLoggingLevel());
        }
        // setup whether to load type converters as early as possible
        if (getLoadTypeConverters() != null) {
            String s = getContext().resolvePropertyPlaceholders(getLoadTypeConverters());
            getContext().setLoadTypeConverters(Boolean.parseBoolean(s));
        }
        // setup whether to load health checks as early as possible
        if (getLoadHealthChecks() != null) {
            String s = getContext().resolvePropertyPlaceholders(getLoadHealthChecks());
            getContext().setLoadHealthChecks(Boolean.parseBoolean(s));
        }

        // then set custom properties
        Map<String, String> mergedOptions = new HashMap<>();
        if (getGlobalOptions() != null) {
            mergedOptions.putAll(getGlobalOptions().asMap());
        }

        if (!mergedOptions.isEmpty()) {
            getContext().setGlobalOptions(mergedOptions);
        }

        // set the custom registry if defined
        initCustomRegistry(getContext());

        // setup property placeholder so we got it as early as possible
        initPropertyPlaceholder();

        // then setup JMX
        initJMXAgent();

        // setup all misc services
        setupCustomServices();

        BacklogTracer backlogTracer = getBeanForType(BacklogTracer.class);
        if (backlogTracer != null) {
            LOG.info("Using custom BacklogTracer: {}", backlogTracer);
            getContext().addService(backlogTracer);
        }
        InflightRepository inflightRepository = getBeanForType(InflightRepository.class);
        if (inflightRepository != null) {
            LOG.info("Using custom InflightRepository: {}", inflightRepository);
            getContext().setInflightRepository(inflightRepository);
        }
        AsyncProcessorAwaitManager asyncProcessorAwaitManager = getBeanForType(AsyncProcessorAwaitManager.class);
        if (asyncProcessorAwaitManager != null) {
            LOG.info("Using custom AsyncProcessorAwaitManager: {}", asyncProcessorAwaitManager);
            getContext().getCamelContextExtension().addContextPlugin(AsyncProcessorAwaitManager.class,
                    asyncProcessorAwaitManager);
        }
        ManagementStrategy managementStrategy = getBeanForType(ManagementStrategy.class);
        if (managementStrategy != null) {
            LOG.info("Using custom ManagementStrategy: {}", managementStrategy);
            getContext().setManagementStrategy(managementStrategy);
        }
        ManagementObjectNameStrategy managementObjectNameStrategy = getBeanForType(ManagementObjectNameStrategy.class);
        if (managementObjectNameStrategy != null) {
            LOG.info("Using custom ManagementObjectNameStrategy: {}", managementObjectNameStrategy);
            getContext().getManagementStrategy().setManagementObjectNameStrategy(managementObjectNameStrategy);
        }
        EventFactory eventFactory = getBeanForType(EventFactory.class);
        if (eventFactory != null) {
            LOG.info("Using custom EventFactory: {}", eventFactory);
            getContext().getManagementStrategy().setEventFactory(eventFactory);
        }
        UnitOfWorkFactory unitOfWorkFactory = getBeanForType(UnitOfWorkFactory.class);
        if (unitOfWorkFactory != null) {
            LOG.info("Using custom UnitOfWorkFactory: {}", unitOfWorkFactory);
            getContext().getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, unitOfWorkFactory);
        }
        RuntimeEndpointRegistry runtimeEndpointRegistry = getBeanForType(RuntimeEndpointRegistry.class);
        if (runtimeEndpointRegistry != null) {
            LOG.info("Using custom RuntimeEndpointRegistry: {}", runtimeEndpointRegistry);
            getContext().setRuntimeEndpointRegistry(runtimeEndpointRegistry);
        }
        HeadersMapFactory headersMapFactory = getBeanForType(HeadersMapFactory.class);
        if (headersMapFactory != null) {
            LOG.info("Using custom HeadersMapFactory: {}", headersMapFactory);
            getContext().getCamelContextExtension().setHeadersMapFactory(headersMapFactory);
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
        Map<String, EndpointStrategy> endpointStrategies
                = getContext().getRegistry().findByTypeWithName(EndpointStrategy.class);
        if (endpointStrategies != null && !endpointStrategies.isEmpty()) {
            for (Entry<String, EndpointStrategy> entry : endpointStrategies.entrySet()) {
                EndpointStrategy strategy = entry.getValue();
                LOG.info("Using custom EndpointStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                getContext().getCamelContextExtension().registerEndpointCallback(strategy);
            }
        }
        // shutdown
        ShutdownStrategy shutdownStrategy = getBeanForType(ShutdownStrategy.class);
        if (shutdownStrategy != null) {
            LOG.info("Using custom ShutdownStrategy: {}", shutdownStrategy);
            getContext().setShutdownStrategy(shutdownStrategy);
        }
        // add global interceptors
        Map<String, InterceptStrategy> interceptStrategies
                = getContext().getRegistry().findByTypeWithName(InterceptStrategy.class);
        if (interceptStrategies != null && !interceptStrategies.isEmpty()) {
            for (Entry<String, InterceptStrategy> entry : interceptStrategies.entrySet()) {
                InterceptStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getCamelContextExtension().getInterceptStrategies().contains(strategy)) {
                    LOG.info("Using custom InterceptStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                    getContext().getCamelContextExtension().addInterceptStrategy(strategy);
                }
            }
        }
        // set the lifecycle strategy if defined
        Map<String, LifecycleStrategy> lifecycleStrategies
                = getContext().getRegistry().findByTypeWithName(LifecycleStrategy.class);
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
        // set the node lifecycle strategy if defined
        Map<String, ModelLifecycleStrategy> modelLifecycleStrategies
                = getContext().getRegistry().findByTypeWithName(ModelLifecycleStrategy.class);
        if (modelLifecycleStrategies != null && !modelLifecycleStrategies.isEmpty()) {
            for (Entry<String, ModelLifecycleStrategy> entry : modelLifecycleStrategies.entrySet()) {
                ModelLifecycleStrategy strategy = entry.getValue();
                ModelCamelContext mcc = getContext();
                if (!mcc.getModelLifecycleStrategies().contains(strategy)) {
                    LOG.info("Using custom ModelLifecycleStrategy with id: {} and implementation: {}", entry.getKey(),
                            strategy);
                    mcc.addModelLifecycleStrategy(strategy);
                }
            }
        }
        // cluster service
        Map<String, CamelClusterService> clusterServices
                = getContext().getRegistry().findByTypeWithName(CamelClusterService.class);
        if (clusterServices != null && !clusterServices.isEmpty()) {
            for (Entry<String, CamelClusterService> entry : clusterServices.entrySet()) {
                CamelClusterService service = entry.getValue();
                LOG.info("Using CamelClusterService with id: {} and implementation: {}", service.getId(), service);
                getContext().addService(service);
            }
        }
        // service registry
        Map<String, ServiceRegistry> serviceRegistries = getContext().getRegistry().findByTypeWithName(ServiceRegistry.class);
        if (serviceRegistries != null && !serviceRegistries.isEmpty()) {
            for (Map.Entry<String, ServiceRegistry> entry : serviceRegistries.entrySet()) {
                ServiceRegistry service = entry.getValue();

                if (service.getId() == null) {
                    service.setGeneratedId(getContext().getUuidGenerator().generateUuid());
                }

                LOG.info("Using ServiceRegistry with id: {} and implementation: {}", service.getId(), service);
                getContext().addService(service);
            }
        }
        // add route policy factories
        Map<String, RoutePolicyFactory> routePolicyFactories
                = getContext().getRegistry().findByTypeWithName(RoutePolicyFactory.class);
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
            LOG.debug("Using HealthCheckRegistry: {}", healthCheckRegistry);
            getContext().getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, healthCheckRegistry);
        } else {
            // okay attempt to inject this camel context into existing health check (if any)
            healthCheckRegistry = HealthCheckRegistry.get(getContext());
            if (healthCheckRegistry != null) {
                healthCheckRegistry.setCamelContext(getContext());
            }
        }
        if (healthCheckRegistry != null) {
            // Health check repository
            Set<HealthCheckRepository> repositories = getContext().getRegistry().findByType(HealthCheckRepository.class);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(repositories)) {
                for (HealthCheckRepository repository : repositories) {
                    healthCheckRegistry.register(repository);
                }
            }
        }
        // Dev console registry
        DevConsoleRegistry devConsoleRegistry = getBeanForType(DevConsoleRegistry.class);
        if (devConsoleRegistry != null) {
            devConsoleRegistry.setCamelContext(getContext());
            LOG.debug("Using DevConsoleRegistry: {}", devConsoleRegistry);
            getContext().getCamelContextExtension().addContextPlugin(DevConsoleRegistry.class, devConsoleRegistry);
        } else {
            // okay attempt to inject this camel context into existing dev console (if any)
            devConsoleRegistry = DevConsoleRegistry.get(getContext());
            if (devConsoleRegistry != null) {
                devConsoleRegistry.setCamelContext(getContext());
            }
        }
        if (devConsoleRegistry != null) {
            Set<DevConsole> consoles = getContext().getRegistry().findByType(DevConsole.class);
            for (DevConsole console : consoles) {
                devConsoleRegistry.register(console);
            }
        }
        // UuidGenerator
        UuidGenerator uuidGenerator = getBeanForType(UuidGenerator.class);
        if (uuidGenerator != null) {
            LOG.info("Using custom UuidGenerator: {}", uuidGenerator);
            getContext().setUuidGenerator(uuidGenerator);
        }
        // LogListener
        Map<String, LogListener> logListeners = getContext().getRegistry().findByTypeWithName(LogListener.class);
        if (logListeners != null && !logListeners.isEmpty()) {
            for (Map.Entry<String, LogListener> entry : logListeners.entrySet()) {
                LogListener logListener = entry.getValue();
                if (getContext().getCamelContextExtension().getLogListeners() == null
                        || !getContext().getCamelContextExtension().getLogListeners().contains(logListener)) {
                    LOG.info("Using custom LogListener with id: {} and implementation: {}", entry.getKey(), logListener);
                    getContext().getCamelContextExtension().addLogListener(logListener);
                }
            }
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(getContext());

        // Set the application context and camelContext for the beanPostProcessor
        initBeanPostProcessor(getContext());

        // init camel context
        initCamelContext(getContext());

        // init stream caching strategy
        initStreamCachingStrategy();

        // init route controller
        initRouteController();
    }

    /**
     * Setup all the routes which must be done prior starting {@link CamelContext}.
     */
    protected void setupRoutes() throws Exception {
        if (routesSetupDone.compareAndSet(false, true)) {
            LOG.debug("Setting up routes");

            // mark that we are setting up routes
            getContext().getCamelContextExtension().setupRoutes(false);

            // add route configurations
            initRouteConfigurationRefs();
            getContext().addRouteConfigurations(getRouteConfigurations());

            // init route templates
            initRouteTemplateRefs();

            // must init route refs before we prepare the routes below
            initRouteRefs();

            // must init rest refs before we add the rests
            initRestRefs();

            initTransformers();
            initValidators();

            // cannot add rests as routes yet as we need to initialize this specially
            getContext().addRestDefinitions(getRests(), false);

            // convert rests api-doc into routes so they are routes for runtime
            RestConfiguration config = getContext().getRestConfiguration();

            if (config.getApiContextPath() != null) {
                // avoid adding rest-api multiple times, in case multiple RouteBuilder classes is added
                // to the CamelContext, as we only want to setup rest-api once
                // so we check all existing routes if they have rest-api route already added
                boolean hasRestApi = false;
                for (RouteDefinition route : getContext().getRouteDefinitions()) {
                    FromDefinition from = route.getInput();
                    if (from.getUri() != null && from.getUri().startsWith("rest-api:")) {
                        hasRestApi = true;
                        break;
                    }
                }
                if (!hasRestApi) {
                    RouteDefinition route = RestDefinition.asRouteApiDefinition(getContext(), config);
                    LOG.debug("Adding routeId: {} as rest-api route", route.getId());
                    getRoutes().add(route);
                }
            }

            // add each rest as route
            for (RestDefinition rest : getContext().getRestDefinitions()) {
                rest.asRouteDefinition(getContext()).forEach(r -> getRoutes().add(r));
            }

            // add route templates
            getContext().addRouteTemplateDefinitions(getRouteTemplates());

            // do special preparation for some concepts such as interceptors and policies
            // this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
            // using route builders. So we have here a little custom code to fix the JAXB gaps
            prepareRoutes();

            // and add the routes
            getContext().addRouteDefinitions(getRoutes());

            // Add the templated routes
            getContext().addRouteFromTemplatedRoutes(getTemplatedRoutes());

            LOG.debug("Found JAXB created routes: {}", getRoutes());

            findRouteBuilders();
            installRoutes();

            // and we are now finished setting up the routes
            getContext().getCamelContextExtension().setupRoutes(true);
        }
    }

    private void initTransformers() {
        if (getTransformers() != null) {
            for (TransformerDefinition def : getTransformers().getTransformers()) {
                // create and register transformers on transformer registry
                Transformer transformer = TransformerReifier.reifier(getContext(), def).createTransformer();
                getContext().getTransformerRegistry().put(createTransformerKey(def), transformer);
            }
        }
    }

    private static ValueHolder<String> createTransformerKey(TransformerDefinition def) {
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(def.getScheme())) {
            return org.apache.camel.util.ObjectHelper.isNotEmpty(def.getName())
                    ? new TransformerKey(def.getScheme() + ":" + def.getName()) : new TransformerKey(def.getScheme());
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(def.getName())) {
            return new TransformerKey(def.getName());
        } else {
            return new TransformerKey(new DataType(def.getFromType()), new DataType(def.getToType()));
        }
    }

    private void initValidators() {
        if (getValidators() != null) {
            for (ValidatorDefinition def : getValidators().getValidators()) {
                // create and register validators on validator registry
                Validator validator = ValidatorReifier.reifier(getContext(), def).createValidator();
                getContext().getValidatorRegistry().put(createValidatorKey(def), validator);
            }
        }
    }

    private static ValidatorKey createValidatorKey(ValidatorDefinition def) {
        return new ValidatorKey(new DataType(def.getType()));
    }

    /**
     * Do special preparation for some concepts such as interceptors and policies this is needed as JAXB does not build
     * exactly the same model definition as Spring DSL would do using route builders. So we have here a little custom
     * code to fix the JAXB gaps
     */
    private void prepareRoutes() {
        for (RouteDefinition route : getRoutes()) {
            // sanity check first as the route is created using XML
            RouteDefinitionHelper.sanityCheckRoute(route);

            // reset before preparing route
            route.resetPrepare();

            // merge global and route scoped together
            AtomicReference<ErrorHandlerDefinition> errorHandler = new AtomicReference<>();
            List<OnExceptionDefinition> oe = new ArrayList<>(getOnExceptions());
            List<InterceptDefinition> icp = new ArrayList<>(getIntercepts());
            List<InterceptFromDefinition> ifrom = new ArrayList<>(getInterceptFroms());
            List<InterceptSendToEndpointDefinition> ito = new ArrayList<>(getInterceptSendToEndpoints());
            List<OnCompletionDefinition> oc = new ArrayList<>(getOnCompletions());
            if (getContext() != null) {
                List<RouteConfigurationDefinition> globalConfigurations
                        = getContext().getRouteConfigurationDefinitions();
                if (globalConfigurations != null) {
                    // if there are multiple ids configured then we should apply in that same order
                    String[] ids = route.getRouteConfigurationId() != null
                            ? route.getRouteConfigurationId().split(",") : new String[] { "*" };
                    for (String id : ids) {
                        // sort according to ordered
                        globalConfigurations.stream().sorted(OrderedComparator.get())
                                .filter(g -> {
                                    if (route.getRouteConfigurationId() != null) {
                                        // if the route has a route configuration assigned then use pattern matching
                                        return PatternHelper.matchPattern(g.getId(), id);
                                    } else {
                                        // global configurations have no id assigned or is a wildcard
                                        return g.getId() == null || g.getId().equals(id);
                                    }
                                })
                                .forEach(g -> {
                                    // there can only be one global error handler, so override previous, meaning
                                    // that we will pick the last in the sort (take precedence)
                                    if (g.getErrorHandler() != null) {
                                        errorHandler.set(g.getErrorHandler());
                                    }

                                    String aid = g.getId() == null ? "<default>" : g.getId();
                                    // remember the id that was used on the route
                                    route.addAppliedRouteConfigurationId(aid);
                                    oe.addAll(g.getOnExceptions());
                                    icp.addAll(g.getIntercepts());
                                    ifrom.addAll(g.getInterceptFroms());
                                    ito.addAll(g.getInterceptSendTos());
                                    oc.addAll(g.getOnCompletions());
                                });
                    }
                }
            }

            // must prepare the route before we can add it to the routes list
            RouteDefinitionHelper.prepareRoute(getContext(), route, errorHandler.get(), oe, icp, ifrom, ito, oc);

            if (LOG.isDebugEnabled() && route.getAppliedRouteConfigurationIds() != null) {
                LOG.debug("Route: {} is using route configurations ids: {}", route.getId(),
                        route.getAppliedRouteConfigurationIds());
            }

            // mark the route as prepared now
            route.markPrepared();
        }
    }

    protected abstract void initCustomRegistry(T context);

    protected void initJMXAgent() throws Exception {
        CamelJMXAgentDefinition camelJMXAgent = getCamelJMXAgent();

        boolean disabled = false;
        if (camelJMXAgent != null) {
            disabled = camelJMXAgent.getDisabled() != null
                    && CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getDisabled());
        }

        if (disabled) {
            LOG.debug("JMXAgent disabled");
            // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
            getContext().getLifecycleStrategies().clear();
            // no need to add a lifecycle strategy as we do not need one as JMX is disabled
            getContext().setManagementStrategy(new DefaultManagementStrategy());
        } else if (camelJMXAgent != null) {
            LOG.debug("JMXAgent enabled: {}", camelJMXAgent);

            Map<String, Object> properties = new HashMap<>();
            if (camelJMXAgent.getMbeanObjectDomainName() != null) {
                properties.put("mbeanObjectDomainName",
                        CamelContextHelper.parseText(getContext(), camelJMXAgent.getMbeanObjectDomainName()));
            }
            if (camelJMXAgent.getUsePlatformMBeanServer() != null) {
                properties.put("usePlatformMBeanServer",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getUsePlatformMBeanServer()));
            }
            if (camelJMXAgent.getOnlyRegisterProcessorWithCustomId() != null) {
                properties.put("onlyRegisterProcessorWithCustomId",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getOnlyRegisterProcessorWithCustomId()));
            }
            if (camelJMXAgent.getRegisterAlways() != null) {
                properties.put("registerAlways",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getRegisterAlways()));
            }
            if (camelJMXAgent.getRegisterNewRoutes() != null) {
                properties.put("registerNewRoutes",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getRegisterNewRoutes()));
            }
            if (camelJMXAgent.getIncludeHostName() != null) {
                properties.put("includeHostName",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getIncludeHostName()));
            }
            if (camelJMXAgent.getUseHostIPAddress() != null) {
                properties.put("useHostIPAddress",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getUseHostIPAddress()));
            }
            if (camelJMXAgent.getMask() != null) {
                properties.put("mask", CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getMask()));
            }
            if (camelJMXAgent.getLoadStatisticsEnabled() != null) {
                properties.put("loadStatisticsEnabled",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getLoadStatisticsEnabled()));
            }
            if (camelJMXAgent.getEndpointRuntimeStatisticsEnabled() != null) {
                properties.put("endpointRuntimeStatisticsEnabled",
                        CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getEndpointRuntimeStatisticsEnabled()));
            }
            if (camelJMXAgent.getStatisticsLevel() != null) {
                String level = CamelContextHelper.parseText(getContext(), camelJMXAgent.getStatisticsLevel());
                ManagementStatisticsLevel msLevel
                        = getContext().getTypeConverter().mandatoryConvertTo(ManagementStatisticsLevel.class, level);
                properties.put("statisticsLevel", msLevel);
            }
            if (camelJMXAgent.getMbeansLevel() != null) {
                String level = CamelContextHelper.parseText(getContext(), camelJMXAgent.getMbeansLevel());
                ManagementMBeansLevel mbLevel
                        = getContext().getTypeConverter().mandatoryConvertTo(ManagementMBeansLevel.class, level);
                properties.put("mBeansLevel", mbLevel);
            }

            getContext().getCamelContextExtension().setupManagement(properties);
        }
    }

    protected void initStreamCachingStrategy() throws Exception {
        CamelStreamCachingStrategyDefinition streamCaching = getCamelStreamCachingStrategy();
        if (streamCaching == null) {
            getContext().getStreamCachingStrategy().setEnabled(true);
            return;
        }

        Boolean enabled = CamelContextHelper.parseBoolean(getContext(), streamCaching.getEnabled());
        if (enabled != null) {
            getContext().getStreamCachingStrategy().setEnabled(enabled);
        } else {
            // stream-caching is default enabled
            getContext().getStreamCachingStrategy().setEnabled(true);
        }
        String allowClasses = CamelContextHelper.parseText(getContext(), streamCaching.getAllowClasses());
        if (allowClasses != null) {
            getContext().getStreamCachingStrategy().setAllowClasses(allowClasses);
        }
        String denyClasses = CamelContextHelper.parseText(getContext(), streamCaching.getDenyClasses());
        if (denyClasses != null) {
            getContext().getStreamCachingStrategy().setDenyClasses(denyClasses);
        }
        Boolean spoolEnabled = CamelContextHelper.parseBoolean(getContext(), streamCaching.getSpoolEnabled());
        if (spoolEnabled != null) {
            getContext().getStreamCachingStrategy().setSpoolEnabled(spoolEnabled);
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
            StreamCachingStrategy.SpoolUsedHeapMemoryLimit ul = CamelContextHelper.mandatoryConvertTo(getContext(),
                    StreamCachingStrategy.SpoolUsedHeapMemoryLimit.class, limit);
            getContext().getStreamCachingStrategy().setSpoolUsedHeapMemoryLimit(ul);
        }
        String spoolCipher = CamelContextHelper.parseText(getContext(), streamCaching.getSpoolCipher());
        if (spoolCipher != null) {
            getContext().getStreamCachingStrategy().setSpoolCipher(spoolCipher);
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
            for (String name : ObjectHelper.createIterable(spoolRules)) {
                StreamCachingStrategy.SpoolRule rule
                        = getContext().getRegistry().lookupByNameAndType(name, StreamCachingStrategy.SpoolRule.class);
                if (rule != null) {
                    getContext().getStreamCachingStrategy().addSpoolRule(rule);
                }
            }
        }
    }

    protected void initRouteController() throws Exception {
        // Route controller
        RouteController routeController = getBeanForType(RouteController.class);
        if (routeController != null) {
            LOG.info("Using RouteController: {}", routeController);
            getContext().setRouteController(routeController);
            // we are using custom so dont attempt to use the default below
            return;
        }

        CamelRouteControllerDefinition rc = getCamelRouteController();
        if (rc == null) {
            return;
        }

        SupervisingRouteController src = null;
        Boolean enabled = CamelContextHelper.parseBoolean(getContext(), rc.getSupervising());
        if (enabled != null) {
            src = getContext().getRouteController().supervising();
        }
        String includeRoutes = CamelContextHelper.parseText(getContext(), rc.getIncludeRoutes());
        if (includeRoutes != null && src != null) {
            src.setIncludeRoutes(includeRoutes);
        }
        String excludeRoutes = CamelContextHelper.parseText(getContext(), rc.getExcludeRoutes());
        if (excludeRoutes != null && src != null) {
            src.setExcludeRoutes(excludeRoutes);
        }
        Integer threadPoolSize = CamelContextHelper.parseInteger(getContext(), rc.getThreadPoolSize());
        if (threadPoolSize != null && src != null) {
            src.setThreadPoolSize(threadPoolSize);
        }
        Long initialDelay = CamelContextHelper.parseLong(getContext(), rc.getInitialDelay());
        if (initialDelay != null && src != null) {
            src.setInitialDelay(initialDelay);
        }
        Long backoffDelay = CamelContextHelper.parseLong(getContext(), rc.getBackOffDelay());
        if (backoffDelay != null && src != null) {
            src.setBackOffDelay(backoffDelay);
        }
        Long backOffMaxDelay = CamelContextHelper.parseLong(getContext(), rc.getBackOffMaxDelay());
        if (backOffMaxDelay != null && src != null) {
            src.setBackOffMaxDelay(backOffMaxDelay);
        }
        Long backOffMaxElapsedTime = CamelContextHelper.parseLong(getContext(), rc.getBackOffMaxElapsedTime());
        if (backOffMaxElapsedTime != null && src != null) {
            src.setBackOffMaxElapsedTime(backOffMaxElapsedTime);
        }
        Long backOffMaxAttempts = CamelContextHelper.parseLong(getContext(), rc.getBackOffMaxAttempts());
        if (backOffMaxAttempts != null && src != null) {
            src.setBackOffMaxAttempts(backOffMaxAttempts);
        }
        Double backOffMultiplier = CamelContextHelper.parseDouble(getContext(), rc.getBackOffMultiplier());
        if (backOffMultiplier != null && src != null) {
            src.setBackOffMultiplier(backOffMultiplier);
        }
        Boolean unhealthyOnExhausted = CamelContextHelper.parseBoolean(getContext(), rc.getUnhealthyOnExhausted());
        if (unhealthyOnExhausted != null && unhealthyOnExhausted) {
            src.setUnhealthyOnExhausted(unhealthyOnExhausted);
        }
        LoggingLevel loggingLevel = CamelContextHelper.parse(getContext(), LoggingLevel.class, rc.getLoggingLevel());
        if (loggingLevel != null) {
            src.setLoggingLevel(loggingLevel);
        }
    }

    protected void initPropertyPlaceholder() throws Exception {
        if (getCamelPropertyPlaceholder() != null) {
            CamelPropertyPlaceholderDefinition def = getCamelPropertyPlaceholder();

            List<PropertiesLocation> locations = new ArrayList<>();

            if (def.getLocation() != null) {
                ObjectHelper.createIterable(def.getLocation()).forEach(
                        location -> locations.add(new PropertiesLocation(location)));
            }
            if (def.getLocations() != null) {
                def.getLocations().forEach(
                        definition -> locations.add(definition.toLocation()));
            }

            PropertiesComponent pc = new PropertiesComponent();
            pc.setLocations(locations);
            pc.setEncoding(def.getEncoding());

            if (def.isIgnoreMissingLocation() != null) {
                pc.setIgnoreMissingLocation(def.isIgnoreMissingLocation());
            }
            if (def.isNestedPlaceholder() != null) {
                pc.setNestedPlaceholder(def.isNestedPlaceholder());
            }

            // if using a custom parser
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(def.getPropertiesParserRef())) {
                PropertiesParser parser = CamelContextHelper.mandatoryLookup(getContext(), def.getPropertiesParserRef(),
                        PropertiesParser.class);
                pc.setPropertiesParser(parser);
            }

            if (def.getDefaultFallbackEnabled() != null) {
                pc.setDefaultFallbackEnabled(def.getDefaultFallbackEnabled());
            }

            if (def.getFunctions() != null && !def.getFunctions().isEmpty()) {
                for (CamelPropertyPlaceholderFunctionDefinition function : def.getFunctions()) {
                    String ref = function.getRef();
                    PropertiesFunction pf = CamelContextHelper.mandatoryLookup(getContext(), ref, PropertiesFunction.class);
                    pc.addPropertiesFunction(pf);
                }
            }

            // register the properties component
            getContext().setPropertiesComponent(pc);
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

    protected void initRouteConfigurationRefs() throws Exception {
        // add route configuration refs to existing route configurations
        if (getRouteConfigurationRefs() != null) {
            for (RouteConfigurationContextRefDefinition ref : getRouteConfigurationRefs()) {
                List<RouteConfigurationDefinition> defs = ref.lookupRouteConfigurations(getContext());
                for (RouteConfigurationDefinition def : defs) {
                    LOG.debug("Adding route configuration from {} -> {}", ref, def);
                    getRouteConfigurations().add(def);
                }
            }
        }
    }

    protected void initRouteTemplateRefs() throws Exception {
        // add route template refs to existing route templates
        if (getRouteTemplateRefs() != null) {
            for (RouteTemplateContextRefDefinition ref : getRouteTemplateRefs()) {
                List<RouteTemplateDefinition> defs = ref.lookupRouteTemplates(getContext());
                for (RouteTemplateDefinition def : defs) {
                    LOG.debug("Adding route template from {} -> {}", ref, def);
                    getRouteTemplates().add(def);
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

    @Override
    public abstract List<RouteTemplateDefinition> getRouteTemplates();

    @Override
    public abstract List<TemplatedRouteDefinition> getTemplatedRoutes();

    @Override
    public abstract List<RouteConfigurationDefinition> getRouteConfigurations();

    @Override
    public abstract List<RouteDefinition> getRoutes();

    @Override
    public abstract List<RestDefinition> getRests();

    public abstract RestConfigurationDefinition getRestConfiguration();

    public abstract List<? extends AbstractCamelEndpointFactoryBean> getEndpoints();

    public abstract List<? extends AbstractCamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies();

    public abstract List<InterceptDefinition> getIntercepts();

    public abstract List<InterceptFromDefinition> getInterceptFroms();

    public abstract List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints();

    public abstract GlobalOptionsDefinition getGlobalOptions();

    public abstract String[] getPackages();

    public abstract PackageScanDefinition getPackageScan();

    public abstract void setPackageScan(PackageScanDefinition packageScan);

    public abstract ContextScanDefinition getContextScan();

    public abstract void setContextScan(ContextScanDefinition contextScan);

    public abstract CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder();

    public abstract StartupSummaryLevel getStartupSummaryLevel();

    public abstract String getTrace();

    public abstract String getTracePattern();

    public abstract String getTraceLoggingFormat();

    public abstract String getBacklogTrace();

    public abstract String getDebug();

    public abstract String getMessageHistory();

    public abstract String getSourceLocationEnabled();

    public abstract String getLogMask();

    public abstract String getLogExhaustedMessageBody();

    public abstract String getStreamCache();

    public abstract String getDelayer();

    public abstract String getAutoStartup();

    public abstract String getUseMDCLogging();

    public abstract String getMDCLoggingKeysPattern();

    public abstract String getDumpRoutes();

    public abstract String getUseDataType();

    public abstract String getUseBreadcrumb();

    public abstract String getBeanPostProcessorEnabled();

    public abstract String getAllowUseOriginalMessage();

    public abstract String getCaseInsensitiveHeaders();

    public abstract String getAutowiredEnabled();

    public abstract String getRuntimeEndpointRegistryEnabled();

    public abstract String getManagementNamePattern();

    public abstract String getThreadNamePattern();

    public abstract String getLoadTypeConverters();

    public abstract String getLoadHealthChecks();

    public abstract String getInflightRepositoryBrowseEnabled();

    public abstract String getTypeConverterStatisticsEnabled();

    public abstract LoggingLevel getTypeConverterExistsLoggingLevel();

    public abstract TypeConverterExists getTypeConverterExists();

    public abstract CamelJMXAgentDefinition getCamelJMXAgent();

    public abstract CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy();

    public abstract CamelRouteControllerDefinition getCamelRouteController();

    public abstract List<RouteConfigurationContextRefDefinition> getRouteConfigurationRefs();

    public abstract List<RouteBuilderDefinition> getBuilderRefs();

    public abstract List<RouteContextRefDefinition> getRouteRefs();

    public abstract List<RouteTemplateContextRefDefinition> getRouteTemplateRefs();

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

    public abstract Resilience4jConfigurationDefinition getDefaultResilience4jConfiguration();

    public abstract List<Resilience4jConfigurationDefinition> getResilience4jConfigurations();

    public abstract FaultToleranceConfigurationDefinition getDefaultFaultToleranceConfiguration();

    public abstract List<FaultToleranceConfigurationDefinition> getFaultToleranceConfigurations();

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Initializes the context
     *
     * @param  context   the context
     * @throws Exception is thrown if error occurred
     */
    protected void initCamelContext(T context) throws Exception {
        if (getStartupSummaryLevel() != null) {
            context.setStartupSummaryLevel(getStartupSummaryLevel());
        }
        if (getBeanPostProcessorEnabled() != null) {
            CamelBeanPostProcessor cbpp = PluginHelper.getBeanPostProcessor(context);
            if (cbpp != null) {
                cbpp.setEnabled(CamelContextHelper.parseBoolean(context, getBeanPostProcessorEnabled()));
            }
        }
        if (getStreamCache() != null) {
            context.setStreamCaching(CamelContextHelper.parseBoolean(context, getStreamCache()));
        }
        if (getTrace() != null) {
            String text = CamelContextHelper.parseText(context, getTrace());
            if ("standby".equalsIgnoreCase(text)) {
                context.setTracingStandby(true);
            } else {
                context.setTracing(CamelContextHelper.parseBoolean(context, getTrace()));
            }
        }
        if (getTracePattern() != null) {
            context.setTracingPattern(CamelContextHelper.parseText(context, getTracePattern()));
        }
        if (getTraceLoggingFormat() != null) {
            context.setTracingLoggingFormat(CamelContextHelper.parseText(context, getTraceLoggingFormat()));
        }
        if (getBacklogTrace() != null) {
            context.setBacklogTracing(CamelContextHelper.parseBoolean(context, getBacklogTrace()));
        }
        if (getDebug() != null) {
            context.setDebugging(CamelContextHelper.parseBoolean(context, getDebug()));
        }
        if (getMessageHistory() != null) {
            context.setMessageHistory(CamelContextHelper.parseBoolean(context, getMessageHistory()));
        }
        if (getSourceLocationEnabled() != null) {
            context.setSourceLocationEnabled(CamelContextHelper.parseBoolean(context, getSourceLocationEnabled()));
        }
        if (getLogMask() != null) {
            context.setLogMask(CamelContextHelper.parseBoolean(context, getLogMask()));
        }
        if (getLogExhaustedMessageBody() != null) {
            context.setLogExhaustedMessageBody(CamelContextHelper.parseBoolean(context, getLogExhaustedMessageBody()));
        }
        if (getDelayer() != null) {
            context.setDelayer(CamelContextHelper.parseLong(context, getDelayer()));
        }
        if (getErrorHandlerRef() != null) {
            context.getCamelContextExtension()
                    .setErrorHandlerFactory(new RefErrorHandlerDefinition(getErrorHandlerRef()));
        }
        if (getAutoStartup() != null) {
            context.setAutoStartup(CamelContextHelper.parseBoolean(context, getAutoStartup()));
        }
        if (getUseMDCLogging() != null) {
            context.setUseMDCLogging(CamelContextHelper.parseBoolean(context, getUseMDCLogging()));
        }
        if (getMDCLoggingKeysPattern() != null) {
            context.setMDCLoggingKeysPattern(CamelContextHelper.parseText(context, getMDCLoggingKeysPattern()));
        }
        if (getDumpRoutes() != null) {
            context.setDumpRoutes(CamelContextHelper.parseText(context, getDumpRoutes()));
        }
        if (getUseDataType() != null) {
            context.setUseDataType(CamelContextHelper.parseBoolean(context, getUseDataType()));
        }
        if (getUseBreadcrumb() != null) {
            context.setUseBreadcrumb(CamelContextHelper.parseBoolean(context, getUseBreadcrumb()));
        }
        if (getAllowUseOriginalMessage() != null) {
            context.setAllowUseOriginalMessage(CamelContextHelper.parseBoolean(context, getAllowUseOriginalMessage()));
        }
        if (getCaseInsensitiveHeaders() != null) {
            context.setCaseInsensitiveHeaders(CamelContextHelper.parseBoolean(context, getCaseInsensitiveHeaders()));
        }
        if (getAutowiredEnabled() != null) {
            context.setAutowiredEnabled(CamelContextHelper.parseBoolean(context, getAutowiredEnabled()));
        }
        if (getRuntimeEndpointRegistryEnabled() != null) {
            context.getRuntimeEndpointRegistry()
                    .setEnabled(CamelContextHelper.parseBoolean(context, getRuntimeEndpointRegistryEnabled()));
        }
        if (getManagementNamePattern() != null) {
            context.getManagementNameStrategy()
                    .setNamePattern(CamelContextHelper.parseText(context, getManagementNamePattern()));
        }
        if (getThreadNamePattern() != null) {
            context.getExecutorServiceManager()
                    .setThreadNamePattern(CamelContextHelper.parseText(context, getThreadNamePattern()));
        }
        if (getShutdownRoute() != null) {
            context.setShutdownRoute(getShutdownRoute());
        }
        if (getShutdownRunningTask() != null) {
            context.setShutdownRunningTask(getShutdownRunningTask());
        }
        if (getDataFormats() != null) {
            context.setDataFormats(getDataFormats().asMap());
        }
        if (getTransformers() != null) {
            context.setTransformers(getTransformers().getTransformers());
        }
        if (getValidators() != null) {
            context.setValidators(getValidators().getValidators());
        }
        if (getInflightRepositoryBrowseEnabled() != null) {
            context.getInflightRepository()
                    .setInflightBrowseEnabled(CamelContextHelper.parseBoolean(context, getInflightRepositoryBrowseEnabled()));
        }
        if (getRestConfiguration() != null) {
            getRestConfiguration().asRestConfiguration(context, context.getRestConfiguration());
        }
        if (getDefaultServiceCallConfiguration() != null) {
            context.setServiceCallConfiguration(getDefaultServiceCallConfiguration());
        }
        if (getServiceCallConfigurations() != null) {
            for (ServiceCallConfigurationDefinition bean : getServiceCallConfigurations()) {
                context.addServiceCallConfiguration(bean.getId(), bean);
            }
        }
        if (getDefaultResilience4jConfiguration() != null) {
            context.setResilience4jConfiguration(getDefaultResilience4jConfiguration());
        }
        if (getResilience4jConfigurations() != null) {
            for (Resilience4jConfigurationDefinition bean : getResilience4jConfigurations()) {
                context.addResilience4jConfiguration(bean.getId(), bean);
            }
        }
        if (getDefaultFaultToleranceConfiguration() != null) {
            context.setFaultToleranceConfiguration(getDefaultFaultToleranceConfiguration());
        }
        if (getFaultToleranceConfigurations() != null) {
            for (FaultToleranceConfigurationDefinition bean : getFaultToleranceConfigurations()) {
                context.addFaultToleranceConfiguration(bean.getId(), bean);
            }
        }
    }

    protected void initThreadPoolProfiles(T context) throws Exception {
        Set<String> defaultIds = new HashSet<>();

        // lookup and use custom profiles from the registry
        Map<String, ThreadPoolProfile> profiles = context.getRegistry().findByTypeWithName(ThreadPoolProfile.class);
        if (profiles != null && !profiles.isEmpty()) {
            for (Entry<String, ThreadPoolProfile> entry : profiles.entrySet()) {
                ThreadPoolProfile profile = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (profile.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: {} and implementation: {}", entry.getKey(),
                            profile);
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
                Boolean defaultProfile = CamelContextHelper.parseBoolean(getContext(), definition.getDefaultProfile());
                if (defaultProfile != null && defaultProfile) {
                    LOG.info("Using custom default ThreadPoolProfile with id: {} and implementation: {}", definition.getId(),
                            definition);
                    context.getExecutorServiceManager().setDefaultThreadPoolProfile(asThreadPoolProfile(context, definition));
                    defaultIds.add(definition.getId());
                } else {
                    context.getExecutorServiceManager().registerThreadPoolProfile(asThreadPoolProfile(context, definition));
                }
            }
        }

        // validate at most one is defined
        if (defaultIds.size() > 1) {
            throw new IllegalArgumentException(
                    "Only exactly one default ThreadPoolProfile is allowed, was " + defaultIds.size() + " ids: " + defaultIds);
        }
    }

    /**
     * Creates a {@link ThreadPoolProfile} instance based on the definition.
     *
     * @param  context   the camel context
     * @return           the profile
     * @throws Exception is thrown if error creating the profile
     */
    private ThreadPoolProfile asThreadPoolProfile(CamelContext context, ThreadPoolProfileDefinition definition)
            throws Exception {
        ThreadPoolProfile answer = new ThreadPoolProfile();
        answer.setId(definition.getId());
        answer.setDefaultProfile(CamelContextHelper.parseBoolean(context, definition.getDefaultProfile()));
        answer.setPoolSize(CamelContextHelper.parseInteger(context, definition.getPoolSize()));
        answer.setMaxPoolSize(CamelContextHelper.parseInteger(context, definition.getMaxPoolSize()));
        answer.setKeepAliveTime(CamelContextHelper.parseLong(context, definition.getKeepAliveTime()));
        answer.setMaxQueueSize(CamelContextHelper.parseInteger(context, definition.getMaxQueueSize()));
        answer.setAllowCoreThreadTimeOut(CamelContextHelper.parseBoolean(context, definition.getAllowCoreThreadTimeOut()));
        answer.setRejectedPolicy(
                CamelContextHelper.parse(context, ThreadPoolRejectedPolicy.class, definition.getRejectedPolicy()));
        answer.setTimeUnit(CamelContextHelper.parse(context, TimeUnit.class, definition.getTimeUnit()));
        return answer;
    }

    protected abstract void initBeanPostProcessor(T context);

    /**
     * Strategy to install all available routes into the context
     */
    protected void installRoutes() throws Exception {

        // let's add RoutesBuilder's added from references
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

    }

    protected abstract void postProcessBeforeInit(RouteBuilder builder);

    /**
     * Strategy method to try find {@link org.apache.camel.builder.RouteBuilder} instances on the classpath
     */
    protected void findRouteBuilders() throws Exception {
        // package scan
        addPackageElementContentsToScanDefinition();
        PackageScanDefinition packageScanDef = getPackageScan();
        if (packageScanDef != null && !packageScanDef.getPackages().isEmpty()) {
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
            boolean includeNonSingletons = contextScanDef.getIncludeNonSingletons() != null
                    && Boolean.parseBoolean(contextScanDef.getIncludeNonSingletons());
            findRouteBuildersByContextScan(filter, includeNonSingletons, builders);
        }
    }

    protected abstract void findRouteBuildersByPackageScan(
            String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders)
            throws Exception;

    protected abstract void findRouteBuildersByContextScan(
            PackageScanFilter filter, boolean includeNonSingletons, List<RoutesBuilder> builders)
            throws Exception;

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
        List<String> packages = new ArrayList<>();
        for (String name : unnormalized) {
            // it may use property placeholders
            name = context.resolvePropertyPlaceholders(name);
            name = StringHelper.normalizeClassName(name);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(name)) {
                LOG.trace("Using package: {} to scan for RouteBuilder classes", name);
                packages.add(name);
            }
        }
        return packages.toArray(new String[0]);
    }

    private void setupCustomServices() {
        ModelJAXBContextFactory modelJAXBContextFactory = getBeanForType(ModelJAXBContextFactory.class);
        if (modelJAXBContextFactory != null) {
            LOG.info("Using custom ModelJAXBContextFactory: {}", modelJAXBContextFactory);
            getContext().getCamelContextExtension().addContextPlugin(ModelJAXBContextFactory.class, modelJAXBContextFactory);
        }
        ClassResolver classResolver = getBeanForType(ClassResolver.class);
        if (classResolver != null) {
            LOG.info("Using custom ClassResolver: {}", classResolver);
            getContext().setClassResolver(classResolver);
        }
        FactoryFinderResolver factoryFinderResolver = getBeanForType(FactoryFinderResolver.class);
        if (factoryFinderResolver != null) {
            LOG.info("Using custom FactoryFinderResolver: {}", factoryFinderResolver);
            getContext().getCamelContextExtension().addContextPlugin(FactoryFinderResolver.class, factoryFinderResolver);
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
            getContext().getCamelContextExtension().addContextPlugin(ProcessorFactory.class, processorFactory);
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
            getContext().getCamelContextExtension().addContextPlugin(NodeIdFactory.class, nodeIdFactory);
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
        ReactiveExecutor reactiveExecutor = getBeanForType(ReactiveExecutor.class);
        if (reactiveExecutor != null) {
            // already logged in CamelContext
            getContext().getCamelContextExtension().setReactiveExecutor(reactiveExecutor);
        }
    }
}
