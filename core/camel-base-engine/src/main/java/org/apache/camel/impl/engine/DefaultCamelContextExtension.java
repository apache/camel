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
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.DeferServiceFactory;
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
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContextExtension.class);

    private final AbstractCamelContext camelContext;
    private final ThreadLocal<Boolean> isSetupRoutes = new ThreadLocal<>();
    private final List<InterceptStrategy> interceptStrategies = new ArrayList<>();
    private final Map<String, FactoryFinder> factories = new ConcurrentHashMap<>();
    private final Map<String, FactoryFinder> bootstrapFactories = new ConcurrentHashMap<>();
    private final Set<LogListener> logListeners = new LinkedHashSet<>();
    private final PluginManager pluginManager = new DefaultContextPluginManager();
    private volatile String description;
    @Deprecated
    private ErrorHandlerFactory errorHandlerFactory;
    private String basePackageScan;
    private boolean lightweight;

    private final Object lock = new Object();

    private volatile FactoryFinder bootstrapFactoryFinder;

    public DefaultCamelContextExtension(AbstractCamelContext camelContext) {
        this.camelContext = camelContext;
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
        camelContext.bootstraps.add(bootstrap);
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
    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        return camelContext.managementMBeanAssembler;
    }

    @Override
    public Registry getRegistry() {
        if (camelContext.registry == null) {
            synchronized (camelContext.lock) {
                if (camelContext.registry == null) {
                    setRegistry(camelContext.createRegistry());
                }
            }
        }
        return camelContext.registry;
    }

    @Override
    public void setRegistry(Registry registry) {
        CamelContextAware.trySetCamelContext(registry, camelContext);
        camelContext.registry = registry;
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
        return camelContext.eventNotificationApplicable;
    }

    @Override
    public void setEventNotificationApplicable(boolean eventNotificationApplicable) {
        camelContext.eventNotificationApplicable = eventNotificationApplicable;
    }

    @Override
    public FactoryFinder getDefaultFactoryFinder() {
        return getFactoryFinder(FactoryFinder.DEFAULT_PATH);
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
        LOG.trace("Setting up management");

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
                    LOG.info("Detected: {} JAR (Enabling Camel Debugging)", df);
                    camelContext.enableDebugging(df);
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
        if (camelContext.managementStrategy != null) {
            notifiers = camelContext.managementStrategy.getEventNotifiers();
        }

        try {
            ManagementStrategy strategy = factory.create(camelContext.getCamelContextReference(), options);
            if (notifiers != null) {
                notifiers.forEach(strategy::addEventNotifier);
            }
            LifecycleStrategy lifecycle = factory.createLifecycle(camelContext);
            factory.setupManagement(camelContext, strategy, lifecycle);
        } catch (Exception e) {
            LOG.warn("Error setting up management due {}", e.getMessage());
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
    public boolean isLightweight() {
        return lightweight;
    }

    @Override
    public void setLightweight(boolean lightweight) {
        this.lightweight = lightweight;
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        return camelContext.headersMapFactory;
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory headersMapFactory) {
        camelContext.headersMapFactory = camelContext.getInternalServiceManager().addService(headersMapFactory);
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        if (camelContext.exchangeFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.exchangeFactory == null) {
                    setExchangeFactory(camelContext.createExchangeFactory());
                }
            }
        }
        return camelContext.exchangeFactory;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        // automatic inject camel context
        exchangeFactory.setCamelContext(camelContext);
        camelContext.exchangeFactory = exchangeFactory;
    }

    @Override
    public ExchangeFactoryManager getExchangeFactoryManager() {
        if (camelContext.exchangeFactoryManager == null) {
            synchronized (camelContext.lock) {
                if (camelContext.exchangeFactoryManager == null) {
                    setExchangeFactoryManager(camelContext.createExchangeFactoryManager());
                }
            }
        }
        return camelContext.exchangeFactoryManager;
    }

    @Override
    public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {
        camelContext.exchangeFactoryManager = camelContext.getInternalServiceManager().addService(exchangeFactoryManager);
    }

    @Override
    public ProcessorExchangeFactory getProcessorExchangeFactory() {
        if (camelContext.processorExchangeFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.processorExchangeFactory == null) {
                    setProcessorExchangeFactory(camelContext.createProcessorExchangeFactory());
                }
            }
        }
        return camelContext.processorExchangeFactory;
    }

    @Override
    public void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory) {
        // automatic inject camel context
        processorExchangeFactory.setCamelContext(camelContext);
        camelContext.processorExchangeFactory = processorExchangeFactory;
    }

    @Override
    public ReactiveExecutor getReactiveExecutor() {
        if (camelContext.reactiveExecutor == null) {
            synchronized (camelContext.lock) {
                if (camelContext.reactiveExecutor == null) {
                    setReactiveExecutor(camelContext.createReactiveExecutor());
                }
            }
        }
        return camelContext.reactiveExecutor;
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        // special for executorServiceManager as want to stop it manually so
        // false in stopOnShutdown
        camelContext.reactiveExecutor = camelContext.getInternalServiceManager().addService(reactiveExecutor, false);
    }

    @Override
    public DeferServiceFactory getDeferServiceFactory() {
        if (camelContext.deferServiceFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.deferServiceFactory == null) {
                    setDeferServiceFactory(camelContext.createDeferServiceFactory());
                }
            }
        }
        return camelContext.deferServiceFactory;
    }

    public void setDeferServiceFactory(DeferServiceFactory deferServiceFactory) {
        camelContext.deferServiceFactory = deferServiceFactory;
    }

    @Override
    public AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory() {
        if (camelContext.annotationBasedProcessorFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.annotationBasedProcessorFactory == null) {
                    setAnnotationBasedProcessorFactory(camelContext.createAnnotationBasedProcessorFactory());
                }
            }
        }
        return camelContext.annotationBasedProcessorFactory;
    }

    public void setAnnotationBasedProcessorFactory(AnnotationBasedProcessorFactory annotationBasedProcessorFactory) {
        camelContext.annotationBasedProcessorFactory = annotationBasedProcessorFactory;
    }

    @Override
    public RouteController getInternalRouteController() {
        return camelContext.internalRouteController;
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

    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        return camelContext.createErrorHandler(route, processor);
    }

    public void disposeModel() {
        camelContext.disposeModel();
    }

    public String getTestExcludeRoutes() {
        return camelContext.getTestExcludeRoutes();
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
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
}
