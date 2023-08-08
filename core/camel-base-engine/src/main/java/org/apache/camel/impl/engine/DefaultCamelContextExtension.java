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
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.PluginManager;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultCamelContextExtension implements ExtendedCamelContext {
    private final AbstractCamelContext camelContext;
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
    private volatile ExchangeFactory exchangeFactory;
    private volatile ExchangeFactoryManager exchangeFactoryManager;
    private volatile ProcessorExchangeFactory processorExchangeFactory;
    private volatile ReactiveExecutor reactiveExecutor;
    private volatile Registry registry;
    private volatile ManagementStrategy managementStrategy;
    private volatile ManagementMBeanAssembler managementMBeanAssembler;
    private volatile HeadersMapFactory headersMapFactory;
    private volatile boolean eventNotificationApplicable;

    @Deprecated
    private ErrorHandlerFactory errorHandlerFactory;
    private String basePackageScan;

    private final Object lock = new Object();

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
    public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
        if (camelContext.getEndpointRegistry().isEmpty()) {
            return null;
        }
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
        if (!camelContext.getEndpointStrategies().contains(strategy)) {
            // let it be invoked for already registered endpoints so it can
            // catch-up.
            camelContext.getEndpointStrategies().add(strategy);
            for (Endpoint endpoint : camelContext.getEndpoints()) {
                Endpoint newEndpoint = strategy.registerEndpoint(endpoint.getEndpointUri(), endpoint);
                if (newEndpoint != null) {
                    // put will replace existing endpoint with the new endpoint
                    camelContext.getEndpointRegistry().put(camelContext.getEndpointKey(endpoint.getEndpointUri()), newEndpoint);
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

    void setManagementMBeanAssembler(ManagementMBeanAssembler managementMBeanAssembler) {
        this.managementMBeanAssembler = camelContext.getInternalServiceManager().addService(managementMBeanAssembler, false);
    }

    @Override
    public Registry getRegistry() {
        if (registry == null) {
            synchronized (lock) {
                if (registry == null) {
                    setRegistry(camelContext.createRegistry());
                }
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
            synchronized (lock) {
                if (bootstrapFactoryFinder == null) {
                    bootstrapFactoryFinder
                            = PluginHelper.getFactoryFinderResolver(this)
                                    .resolveBootstrapFactoryFinder(camelContext.getClassResolver());
                }
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
                    if (object instanceof ManagementStrategyFactory) {
                        factory = (ManagementStrategyFactory) object;
                    }
                }
                // detect if camel-debug is on classpath that enables debugging
                DebuggerFactory df
                        = getBootstrapFactoryFinder().newInstance(Debugger.FACTORY, DebuggerFactory.class).orElse(null);
                if (df != null) {
                    logger().info("Detected: {} JAR (Enabling Camel Debugging)", df);
                    camelContext.enableDebugging(df);
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
        this.headersMapFactory = camelContext.getInternalServiceManager().addService(headersMapFactory);
    }

    void initEagerMandatoryServices(boolean caseInsensitive, Supplier<HeadersMapFactory> headersMapFactorySupplier) {
        if (this.headersMapFactory == null) {
            // we want headers map to be created as then JVM can optimize using it as we use it per exchange/message
            synchronized (lock) {
                if (this.headersMapFactory == null) {
                    if (caseInsensitive) {
                        // use factory to find the map factory to use
                        setHeadersMapFactory(headersMapFactorySupplier.get());
                    } else {
                        // case sensitive so we can use hash map
                        setHeadersMapFactory(new HashMapHeadersMapFactory());
                    }
                }
            }
        }
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        if (exchangeFactory == null) {
            synchronized (lock) {
                if (exchangeFactory == null) {
                    setExchangeFactory(camelContext.createExchangeFactory());
                }
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
            synchronized (lock) {
                if (exchangeFactoryManager == null) {
                    setExchangeFactoryManager(camelContext.createExchangeFactoryManager());
                }
            }
        }
        return exchangeFactoryManager;
    }

    @Override
    public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {
        this.exchangeFactoryManager = camelContext.getInternalServiceManager().addService(exchangeFactoryManager);
    }

    @Override
    public ProcessorExchangeFactory getProcessorExchangeFactory() {
        if (processorExchangeFactory == null) {
            synchronized (lock) {
                if (processorExchangeFactory == null) {
                    setProcessorExchangeFactory(camelContext.createProcessorExchangeFactory());
                }
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
            synchronized (lock) {
                if (reactiveExecutor == null) {
                    setReactiveExecutor(camelContext.createReactiveExecutor());
                }
            }
        }
        return reactiveExecutor;
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        // special for executorServiceManager as want to stop it manually so
        // false in stopOnShutdown
        this.reactiveExecutor = camelContext.getInternalServiceManager().addService(reactiveExecutor, false);
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
        return camelContext.startupStepRecorder;
    }

    @Override
    public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {
        camelContext.startupStepRecorder = startupStepRecorder;
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
    public void disposeModel() {
        camelContext.disposeModel();
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
    public <T> void addContextPlugin(Class<T> type, T module) {
        final T addedModule = camelContext.getInternalServiceManager().addService(module);
        pluginManager.addContextPlugin(type, addedModule);
    }

    @Override
    public <T> void lazyAddContextPlugin(Class<T> type, Supplier<T> module) {
        pluginManager.lazyAddContextPlugin(type, () -> lazyInitAndAdd(module));
    }

    private <T> T lazyInitAndAdd(Supplier<T> supplier) {
        T module = supplier.get();

        return camelContext.getInternalServiceManager().addService(module);
    }

    /*
     * NOTE: see CAMEL-19724. We log like this instead of using a statically declared logger in order to
     * reduce the risk of dropping log messages due to slf4j log substitution behavior during its own
     * initialization.
     */
    private Logger logger() {
        class Holder {
            static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContextExtension.class);
        }

        return Holder.LOG;
    }
}
