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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExchangeConstantProvider;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.EndpointServiceRegistry;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.PluginManager;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RestRegistryFactory;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.startup.DefaultStartupStepRecorder;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultCamelContextExtension implements ExtendedCamelContext {

    private final AbstractCamelContext camelContext;
    private final ThreadLocal<String> isCreateRoute = new ThreadLocal<>();
    private final ThreadLocal<String> isCreateProcessor = new ThreadLocal<>();
    private final ThreadLocal<Boolean> isSetupRoutes = new ThreadLocal<>();
    private final List<InterceptStrategy> interceptStrategies = new ArrayList<>();
    private final Map<String, FactoryFinder> factories = new ConcurrentHashMap<>();
    private final Map<String, FactoryFinder> bootstrapFactories = new ConcurrentHashMap<>();
    private final Set<LogListener> logListeners = new LinkedHashSet<>();
    private final PluginManager pluginManager = new DefaultContextPluginManager();
    private final RouteController internalRouteController;

    // start auto assigning route ids using numbering 1000 and upwards
    private final List<BootstrapCloseable> bootstraps = new CopyOnWriteArrayList<>();

    private volatile String description;
    private volatile String profile;
    private volatile ExchangeFactory exchangeFactory;
    private volatile ExchangeFactoryManager exchangeFactoryManager;
    private volatile ProcessorExchangeFactory processorExchangeFactory;
    private volatile ReactiveExecutor reactiveExecutor;
    private volatile Registry registry;
    private volatile ManagementStrategy managementStrategy;
    private volatile ManagementMBeanAssembler managementMBeanAssembler;
    private volatile HeadersMapFactory headersMapFactory;
    private volatile boolean eventNotificationApplicable;
    private volatile CamelContextNameStrategy nameStrategy;
    private volatile ManagementNameStrategy managementNameStrategy;
    private volatile PropertiesComponent propertiesComponent;
    private volatile RestRegistryFactory restRegistryFactory;
    private volatile RestConfiguration restConfiguration;
    private volatile RestRegistry restRegistry;
    private volatile ClassResolver classResolver;
    private volatile MessageHistoryFactory messageHistoryFactory;
    private volatile StreamCachingStrategy streamCachingStrategy;
    private volatile InflightRepository inflightRepository;
    private volatile UuidGenerator uuidGenerator;
    private volatile Tracer tracer;
    private volatile TransformerRegistry transformerRegistry;
    private volatile ValidatorRegistry validatorRegistry;
    private volatile TypeConverterRegistry typeConverterRegistry;
    private volatile EndpointServiceRegistry endpointServiceRegistry;
    private volatile TypeConverter typeConverter;
    private volatile RouteController routeController;
    private volatile ShutdownStrategy shutdownStrategy;
    private volatile ExecutorServiceManager executorServiceManager;

    private volatile Injector injector;

    private volatile StartupStepRecorder startupStepRecorder = new DefaultStartupStepRecorder();

    @Deprecated(since = "3.17.0")
    private ErrorHandlerFactory errorHandlerFactory;
    private String basePackageScan;

    private final Lock lock = new ReentrantLock();

    private volatile FactoryFinder bootstrapFactoryFinder;

    public DefaultCamelContextExtension(AbstractCamelContext camelContext) {
        this.camelContext = camelContext;
        this.internalRouteController = new InternalRouteController(camelContext);
    }

    @Override
    public byte getStatusPhase() {
        return camelContext.getStatusPhase();
    }

    @Override
    public String getName() {
        return camelContext.getNameStrategy().getName();
    }

    CamelContextNameStrategy getNameStrategy() {
        if (nameStrategy == null) {
            lock.lock();
            try {
                if (nameStrategy == null) {
                    setNameStrategy(camelContext.createCamelContextNameStrategy());
                }
            } finally {
                lock.unlock();
            }
        }
        return nameStrategy;
    }

    void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        this.nameStrategy = camelContext.getInternalServiceManager().addService(camelContext, nameStrategy);
    }

    ManagementNameStrategy getManagementNameStrategy() {
        if (managementNameStrategy == null) {
            lock.lock();
            try {
                if (managementNameStrategy == null) {
                    setManagementNameStrategy(camelContext.createManagementNameStrategy());
                }
            } finally {
                lock.unlock();
            }
        }
        return managementNameStrategy;
    }

    void setManagementNameStrategy(ManagementNameStrategy managementNameStrategy) {
        this.managementNameStrategy = camelContext.getInternalServiceManager().addService(camelContext, managementNameStrategy);
    }

    PropertiesComponent getPropertiesComponent() {
        if (propertiesComponent == null) {
            lock.lock();
            try {
                if (propertiesComponent == null) {
                    setPropertiesComponent(camelContext.createPropertiesComponent());
                }
            } finally {
                lock.unlock();
            }
        }
        return propertiesComponent;
    }

    void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = camelContext.getInternalServiceManager().addService(camelContext, propertiesComponent);
    }

    @Override
    public void setName(String name) {
        // use an explicit name strategy since an explicit name was provided to be used
        camelContext.setNameStrategy(new ExplicitCamelContextNameStrategy(name));
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getProfile() {
        return profile;
    }

    @Override
    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
        return camelContext.getEndpointRegistry().get(uri);
    }

    @Override
    public NormalizedEndpointUri normalizeUri(String uri) {
        try {
            uri = EndpointHelper.resolveEndpointUriPropertyPlaceholders(camelContext, uri);
            return NormalizedUri.newNormalizedUri(uri, false);
        } catch (ResolveEndpointFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri) {
        return camelContext.doGetEndpoint(uri.getUri(), null, true, false);
    }

    @Override
    public Endpoint getPrototypeEndpoint(String uri) {
        return camelContext.doGetEndpoint(uri, null, false, true);
    }

    @Override
    public Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri) {
        return camelContext.doGetEndpoint(uri.getUri(), null, true, true);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters) {
        return camelContext.doGetEndpoint(uri.getUri(), parameters, true, false);
    }

    @Override
    public void registerEndpointCallback(EndpointStrategy strategy) {
        // let it be invoked for already registered endpoints so it can
        // catch-up.
        if (camelContext.getEndpointStrategies().add(strategy)) {
            for (Endpoint endpoint : camelContext.getEndpoints()) {
                Endpoint newEndpoint = strategy.registerEndpoint(endpoint.getEndpointUri(),
                        endpoint);
                if (newEndpoint != null) {
                    // put will replace existing endpoint with the new endpoint
                    camelContext.getEndpointRegistry()
                            .put(camelContext.getEndpointKey(endpoint.getEndpointUri()),
                                    newEndpoint);
                }
            }
        }
    }

    @Override
    public List<RouteStartupOrder> getRouteStartupOrder() {
        return camelContext.getRouteStartupOrder();
    }

    @Override
    public boolean isSetupRoutes() {
        Boolean answer = isSetupRoutes.get();
        return answer != null && answer;
    }

    @Override
    public String getCreateRoute() {
        return isCreateRoute.get();
    }

    @Override
    public String getCreateProcessor() {
        return isCreateProcessor.get();
    }

    @Override
    public void addBootstrap(BootstrapCloseable bootstrap) {
        bootstraps.add(bootstrap);
    }

    void closeBootstraps() {
        for (BootstrapCloseable bootstrap : bootstraps) {
            try {
                bootstrap.close();
            } catch (Exception e) {
                logger().warn("Error during closing bootstrap. This exception is ignored.", e);
            }
        }
        bootstraps.clear();
    }

    List<BootstrapCloseable> getBootstraps() {
        return bootstraps;
    }

    @Override
    public List<Service> getServices() {
        return camelContext.getInternalServiceManager().getServices();
    }

    @Override
    public String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional) {
        if (text != null && text.contains(PropertiesComponent.PREFIX_TOKEN)) {
            // the parser will throw exception if property key was not found
            String answer = camelContext.getPropertiesComponent().parseUri(text, keepUnresolvedOptional);
            logger().debug("Resolved text: {} -> {}", text, answer);
            return answer;
        }
        // is the value a known field (currently we only support
        // constants from Exchange.class)
        if (text != null && text.startsWith("Exchange.")) {
            String field = StringHelper.after(text, "Exchange.");
            String constant = ExchangeConstantProvider.lookup(field);
            if (constant != null) {
                logger().debug("Resolved constant: {} -> {}", text, constant);
                return constant;
            } else {
                throw new IllegalArgumentException("Constant field with name: " + field + " not found on Exchange.class");
            }
        }

        // return original text as is
        return text;
    }

    @Override
    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        return managementMBeanAssembler;
    }

    @Override
    public void setManagementMBeanAssembler(ManagementMBeanAssembler managementMBeanAssembler) {
        this.managementMBeanAssembler
                = camelContext.getInternalServiceManager().addService(camelContext, managementMBeanAssembler, false);
    }

    void stopRegistry() {
        ServiceHelper.stopService(registry);
    }

    @Override
    public Registry getRegistry() {
        if (registry == null) {
            lock.lock();
            try {
                if (registry == null) {
                    setRegistry(camelContext.createRegistry());
                }
            } finally {
                lock.unlock();
            }
        }
        return registry;
    }

    @Override
    public void setRegistry(Registry registry) {
        CamelContextAware.trySetCamelContext(registry, camelContext);
        this.registry = registry;
    }

    @Override
    public void createRoute(String routeId) {
        if (routeId != null) {
            isCreateRoute.set(routeId);
        } else {
            isSetupRoutes.remove();
        }
    }

    @Override
    public void createProcessor(String processorId) {
        if (processorId != null) {
            isCreateProcessor.set(processorId);
        } else {
            isCreateProcessor.remove();
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
    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    @Override
    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        // avoid adding double which can happen with spring xml on spring boot
        if (!interceptStrategies.contains(interceptStrategy)) {
            interceptStrategies.add(interceptStrategy);
        }
    }

    @Override
    public Set<LogListener> getLogListeners() {
        return logListeners;
    }

    @Override
    public void addLogListener(LogListener listener) {
        // avoid adding double which can happen with spring xml on spring boot
        CamelContextAware.trySetCamelContext(listener, camelContext);
        logListeners.add(listener);
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
    public boolean isEventNotificationApplicable() {
        return eventNotificationApplicable;
    }

    @Override
    public void setEventNotificationApplicable(boolean eventNotificationApplicable) {
        this.eventNotificationApplicable = eventNotificationApplicable;
    }

    @Override
    public FactoryFinder getDefaultFactoryFinder() {
        return getFactoryFinder(FactoryFinder.DEFAULT_PATH);
    }

    @Override
    public void setDefaultFactoryFinder(FactoryFinder factoryFinder) {
        factories.put(FactoryFinder.DEFAULT_PATH, factoryFinder);
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder() {
        if (bootstrapFactoryFinder == null) {
            lock.lock();
            try {
                if (bootstrapFactoryFinder == null) {
                    bootstrapFactoryFinder
                            = PluginHelper.getFactoryFinderResolver(this)
                                    .resolveBootstrapFactoryFinder(camelContext.getClassResolver());
                }
            } finally {
                lock.unlock();
            }
        }
        return bootstrapFactoryFinder;
    }

    @Override
    public void setBootstrapFactoryFinder(FactoryFinder factoryFinder) {
        bootstrapFactoryFinder = factoryFinder;
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder(String path) {
        return bootstrapFactories.computeIfAbsent(path, camelContext::createBootstrapFactoryFinder);
    }

    @Override
    public FactoryFinder getFactoryFinder(String path) {
        return factories.computeIfAbsent(path, camelContext::createFactoryFinder);
    }

    @Override
    public void setupManagement(Map<String, Object> options) {
        logger().trace("Setting up management");

        ManagementStrategyFactory factory = null;
        if (!camelContext.isJMXDisabled()) {
            try {
                // create a one time factory as we dont need this anymore
                FactoryFinder finder = camelContext.createFactoryFinder("META-INF/services/org/apache/camel/management/");
                if (finder != null) {
                    Object object = finder.newInstance("ManagementStrategyFactory").orElse(null);
                    if (object instanceof ManagementStrategyFactory managementStrategyFactory) {
                        factory = managementStrategyFactory;
                    }
                }
            } catch (Exception e) {
                logger().warn("Cannot create JmxManagementStrategyFactory. Will fallback and disable JMX.", e);
            }
        }
        if (factory == null) {
            factory = new DefaultManagementStrategyFactory();
        }
        logger().debug("Setting up management with factory: {}", factory);

        // preserve any existing event notifiers that may have been already added
        List<EventNotifier> notifiers = null;
        if (managementStrategy != null) {
            notifiers = managementStrategy.getEventNotifiers();
        }

        try {
            ManagementStrategy strategy = factory.create(camelContext.getCamelContextReference(), options);
            if (notifiers != null) {
                notifiers.forEach(strategy::addEventNotifier);
            }
            LifecycleStrategy lifecycle = factory.createLifecycle(camelContext);
            factory.setupManagement(camelContext, strategy, lifecycle);
        } catch (Exception e) {
            logger().warn("Error setting up management due {}", e.getMessage());
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
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
    public HeadersMapFactory getHeadersMapFactory() {
        return headersMapFactory;
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory headersMapFactory) {
        this.headersMapFactory = camelContext.getInternalServiceManager().addService(camelContext, headersMapFactory);
    }

    void initEagerMandatoryServices(boolean caseInsensitive, Supplier<HeadersMapFactory> headersMapFactorySupplier) {
        if (this.headersMapFactory == null) {
            // we want headers map to be created as then JVM can optimize using it as we use it per exchange/message
            lock.lock();
            try {
                if (this.headersMapFactory == null) {
                    if (caseInsensitive) {
                        // use factory to find the map factory to use
                        setHeadersMapFactory(headersMapFactorySupplier.get());
                    } else {
                        // case sensitive so we can use hash map
                        setHeadersMapFactory(new HashMapHeadersMapFactory());
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        if (exchangeFactory == null) {
            lock.lock();
            try {
                if (exchangeFactory == null) {
                    setExchangeFactory(camelContext.createExchangeFactory());
                }
            } finally {
                lock.unlock();
            }
        }
        return exchangeFactory;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        // automatic inject camel context
        exchangeFactory.setCamelContext(camelContext);
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public ExchangeFactoryManager getExchangeFactoryManager() {
        if (exchangeFactoryManager == null) {
            lock.lock();
            try {
                if (exchangeFactoryManager == null) {
                    setExchangeFactoryManager(camelContext.createExchangeFactoryManager());
                }
            } finally {
                lock.unlock();
            }
        }
        return exchangeFactoryManager;
    }

    @Override
    public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {
        this.exchangeFactoryManager = camelContext.getInternalServiceManager().addService(camelContext, exchangeFactoryManager);
    }

    @Override
    public ProcessorExchangeFactory getProcessorExchangeFactory() {
        if (processorExchangeFactory == null) {
            lock.lock();
            try {
                if (processorExchangeFactory == null) {
                    setProcessorExchangeFactory(camelContext.createProcessorExchangeFactory());
                }
            } finally {
                lock.unlock();
            }
        }
        return processorExchangeFactory;
    }

    @Override
    public void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory) {
        // automatic inject camel context
        processorExchangeFactory.setCamelContext(camelContext);
        this.processorExchangeFactory = processorExchangeFactory;
    }

    @Override
    public ReactiveExecutor getReactiveExecutor() {
        if (reactiveExecutor == null) {
            lock.lock();
            try {
                if (reactiveExecutor == null) {
                    setReactiveExecutor(camelContext.createReactiveExecutor());
                }
            } finally {
                lock.unlock();
            }
        }
        return reactiveExecutor;
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        // special for executorServiceManager as want to stop it manually so
        // false in stopOnShutdown
        this.reactiveExecutor = camelContext.getInternalServiceManager().addService(camelContext, reactiveExecutor, false);
    }

    RestRegistryFactory getRestRegistryFactory() {
        if (restRegistryFactory == null) {
            lock.lock();
            try {
                if (restRegistryFactory == null) {
                    setRestRegistryFactory(camelContext.createRestRegistryFactory());
                }
            } finally {
                lock.unlock();
            }
        }
        return restRegistryFactory;
    }

    void setRestRegistryFactory(RestRegistryFactory restRegistryFactory) {
        this.restRegistryFactory = camelContext.getInternalServiceManager().addService(camelContext, restRegistryFactory);
    }

    RestRegistry getRestRegistry() {
        if (restRegistry == null) {
            lock.lock();
            try {
                if (restRegistry == null) {
                    setRestRegistry(camelContext.createRestRegistry());
                }
            } finally {
                lock.unlock();
            }
        }
        return restRegistry;
    }

    void setRestRegistry(RestRegistry restRegistry) {
        this.restRegistry = camelContext.getInternalServiceManager().addService(camelContext, restRegistry);
    }

    RestConfiguration getRestConfiguration() {
        if (restConfiguration == null) {
            lock.lock();
            try {
                if (restConfiguration == null) {
                    setRestConfiguration(camelContext.createRestConfiguration());
                }
            } finally {
                lock.unlock();
            }
        }
        return restConfiguration;
    }

    void setRestConfiguration(RestConfiguration restConfiguration) {
        this.restConfiguration = restConfiguration;
    }

    ClassResolver getClassResolver() {
        if (classResolver == null) {
            lock.lock();
            try {
                if (classResolver == null) {
                    setClassResolver(camelContext.createClassResolver());
                }
            } finally {
                lock.unlock();
            }
        }
        return classResolver;
    }

    void setClassResolver(ClassResolver classResolver) {
        this.classResolver = camelContext.getInternalServiceManager().addService(camelContext, classResolver);
    }

    MessageHistoryFactory getMessageHistoryFactory() {
        if (messageHistoryFactory == null) {
            lock.lock();
            try {
                if (messageHistoryFactory == null) {
                    setMessageHistoryFactory(camelContext.createMessageHistoryFactory());
                }
            } finally {
                lock.unlock();
            }
        }
        return messageHistoryFactory;
    }

    void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {
        this.messageHistoryFactory = camelContext.getInternalServiceManager().addService(camelContext, messageHistoryFactory);
    }

    StreamCachingStrategy getStreamCachingStrategy() {
        if (streamCachingStrategy == null) {
            lock.lock();
            try {
                if (streamCachingStrategy == null) {
                    setStreamCachingStrategy(camelContext.createStreamCachingStrategy());
                }
            } finally {
                lock.unlock();
            }
        }
        return streamCachingStrategy;
    }

    void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {
        this.streamCachingStrategy
                = camelContext.getInternalServiceManager().addService(camelContext, streamCachingStrategy, true, false, true);
    }

    InflightRepository getInflightRepository() {
        if (inflightRepository == null) {
            lock.lock();
            try {
                if (inflightRepository == null) {
                    setInflightRepository(camelContext.createInflightRepository());
                }
            } finally {
                lock.unlock();
            }
        }
        return inflightRepository;
    }

    void setInflightRepository(InflightRepository repository) {
        this.inflightRepository = camelContext.getInternalServiceManager().addService(camelContext, repository);
    }

    UuidGenerator getUuidGenerator() {
        if (uuidGenerator == null) {
            lock.lock();
            try {
                if (uuidGenerator == null) {
                    setUuidGenerator(camelContext.createUuidGenerator());
                }
            } finally {
                lock.unlock();
            }
        }
        return uuidGenerator;
    }

    void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = camelContext.getInternalServiceManager().addService(camelContext, uuidGenerator);
    }

    Tracer getTracer() {
        if (tracer == null) {
            lock.lock();
            try {
                if (tracer == null) {
                    setTracer(camelContext.createTracer());
                }
            } finally {
                lock.unlock();
            }
        }
        return tracer;
    }

    void setTracer(Tracer tracer) {
        this.tracer = camelContext.getInternalServiceManager().addService(camelContext, tracer, true, false, true);
    }

    TransformerRegistry getTransformerRegistry() {
        if (transformerRegistry == null) {
            lock.lock();
            try {
                if (transformerRegistry == null) {
                    setTransformerRegistry(camelContext.createTransformerRegistry());
                }
            } finally {
                lock.unlock();
            }
        }
        return transformerRegistry;
    }

    void setTransformerRegistry(TransformerRegistry transformerRegistry) {
        this.transformerRegistry = camelContext.getInternalServiceManager().addService(camelContext, transformerRegistry);
    }

    @Override
    public EndpointServiceRegistry getEndpointServiceRegistry() {
        if (endpointServiceRegistry == null) {
            lock.lock();
            try {
                if (endpointServiceRegistry == null) {
                    setEndpointServiceRegistry(camelContext.createEndpointServiceRegistry());
                }
            } finally {
                lock.unlock();
            }
        }
        return endpointServiceRegistry;
    }

    @Override
    public void setEndpointServiceRegistry(EndpointServiceRegistry endpointServiceRegistry) {
        this.endpointServiceRegistry
                = camelContext.getInternalServiceManager().addService(camelContext, endpointServiceRegistry);
    }

    ValidatorRegistry getValidatorRegistry() {
        if (validatorRegistry == null) {
            lock.lock();
            try {
                if (validatorRegistry == null) {
                    setValidatorRegistry(camelContext.createValidatorRegistry());
                }
            } finally {
                lock.unlock();
            }
        }
        return validatorRegistry;
    }

    public void setValidatorRegistry(ValidatorRegistry validatorRegistry) {
        this.validatorRegistry = camelContext.getInternalServiceManager().addService(camelContext, validatorRegistry);
    }

    void stopTypeConverterRegistry() {
        ServiceHelper.stopService(typeConverterRegistry);
    }

    void resetTypeConverterRegistry() {
        typeConverterRegistry = null;
    }

    TypeConverterRegistry getTypeConverterRegistry() {
        if (typeConverterRegistry == null) {
            lock.lock();
            try {
                if (typeConverterRegistry == null) {
                    setTypeConverterRegistry(camelContext.createTypeConverterRegistry());
                }
            } finally {
                lock.unlock();
            }
        }
        return typeConverterRegistry;
    }

    void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        this.typeConverterRegistry = camelContext.getInternalServiceManager().addService(camelContext, typeConverterRegistry);
        // some registries are also a type converter implementation
        if (typeConverterRegistry instanceof TypeConverter newTypeConverter) {
            setTypeConverter(newTypeConverter);
        }
    }

    void stopTypeConverter() {
        ServiceHelper.stopService(typeConverter);
    }

    void resetTypeConverter() {
        typeConverter = null;
    }

    TypeConverter getTypeConverter() {
        return typeConverter;
    }

    void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = camelContext.getInternalServiceManager().addService(camelContext, typeConverter);
    }

    TypeConverter getOrCreateTypeConverter() {
        if (typeConverter == null) {
            lock.lock();
            try {
                if (typeConverter == null) {
                    setTypeConverter(camelContext.createTypeConverter());
                }
            } finally {
                lock.unlock();
            }
        }
        return typeConverter;
    }

    void resetInjector() {
        injector = null;
    }

    Injector getInjector() {
        if (injector == null) {
            lock.lock();
            try {
                if (injector == null) {
                    setInjector(camelContext.createInjector());
                }
            } finally {
                lock.unlock();
            }
        }
        return injector;
    }

    void setInjector(Injector injector) {
        this.injector = camelContext.getInternalServiceManager().addService(camelContext, injector);
    }

    void stopAndShutdownRouteController() {
        ServiceHelper.stopAndShutdownService(this.routeController);
    }

    RouteController getRouteController() {
        if (routeController == null) {
            lock.lock();
            try {
                if (routeController == null) {
                    setRouteController(camelContext.createRouteController());
                }
            } finally {
                lock.unlock();
            }
        }
        return routeController;
    }

    void setRouteController(RouteController routeController) {
        this.routeController = camelContext.getInternalServiceManager().addService(camelContext, routeController);
    }

    ShutdownStrategy getShutdownStrategy() {
        if (shutdownStrategy == null) {
            lock.lock();
            try {
                if (shutdownStrategy == null) {
                    setShutdownStrategy(camelContext.createShutdownStrategy());
                }
            } finally {
                lock.unlock();
            }
        }
        return shutdownStrategy;
    }

    void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        this.shutdownStrategy = camelContext.getInternalServiceManager().addService(camelContext, shutdownStrategy);
    }

    ExecutorServiceManager getExecutorServiceManager() {
        if (executorServiceManager == null) {
            lock.lock();
            try {
                if (executorServiceManager == null) {
                    setExecutorServiceManager(camelContext.createExecutorServiceManager());
                }
            } finally {
                lock.unlock();
            }
        }
        return this.executorServiceManager;
    }

    void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {
        // special for executorServiceManager as want to stop it manually so
        // false in stopOnShutdown
        this.executorServiceManager
                = camelContext.getInternalServiceManager().addService(camelContext, executorServiceManager, false);
    }

    @Override
    public RouteController getInternalRouteController() {
        return internalRouteController;
    }

    @Override
    public EndpointUriFactory getEndpointUriFactory(String scheme) {
        return PluginHelper.getUriFactoryResolver(this).resolveFactory(scheme, camelContext);
    }

    @Override
    public StartupStepRecorder getStartupStepRecorder() {
        return startupStepRecorder;
    }

    @Override
    public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {
        this.startupStepRecorder = startupStepRecorder;
    }

    @Override
    public void addRoute(Route route) {
        camelContext.addRoute(route);
    }

    @Override
    public void removeRoute(Route route) {
        camelContext.removeRoute(route);
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        return camelContext.createErrorHandler(route, processor);
    }

    @Override
    public String getTestExcludeRoutes() {
        return camelContext.getTestExcludeRoutes();
    }

    ManagementStrategy getManagementStrategy() {
        return managementStrategy;
    }

    void setManagementStrategy(ManagementStrategy managementStrategy) {
        this.managementStrategy = managementStrategy;
    }

    @Override
    public <T> T getContextPlugin(Class<T> type) {
        T ret = pluginManager.getContextPlugin(type);

        // Note: this is because of interfaces like Model which are still tightly coupled with the context
        if (ret == null) {
            if (type.isInstance(camelContext)) {
                return type.cast(camelContext);
            }
        }

        return ret;
    }

    @Override
    public boolean isContextPluginInUse(Class<?> type) {
        return pluginManager.isContextPluginInUse(type);
    }

    @Override
    public <T> void addContextPlugin(Class<T> type, T module) {
        final T addedModule = camelContext.getInternalServiceManager().addService(camelContext, module);
        pluginManager.addContextPlugin(type, addedModule);
    }

    @Override
    public <T> void lazyAddContextPlugin(Class<T> type, Supplier<T> module) {
        pluginManager.lazyAddContextPlugin(type, () -> lazyInitAndAdd(module));
    }

    private <T> T lazyInitAndAdd(Supplier<T> supplier) {
        T module = supplier.get();

        return camelContext.getInternalServiceManager().addService(camelContext, module);
    }

    /*
     * NOTE: see CAMEL-19724. We log like this instead of using a statically declared logger in order to
     * reduce the risk of dropping log messages due to slf4j log substitution behavior during its own
     * initialization.
     */
    private static final class Holder {
        static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContextExtension.class);
    }

    private static Logger logger() {
        return Holder.LOG;
    }
}
