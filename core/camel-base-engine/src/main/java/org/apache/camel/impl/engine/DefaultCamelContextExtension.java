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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

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
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelDependencyInjectionAnnotationFactory;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InterceptEndpointFactory;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.InternalProcessorFactory;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.NormalizedEndpointUri;
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
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteFactory;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultCamelContextExtension implements ExtendedCamelContext {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContextExtension.class);

    private final AbstractCamelContext camelContext;
    private final ThreadLocal<Boolean> isSetupRoutes = new ThreadLocal<>();
    private List<InterceptStrategy> interceptStrategies = new ArrayList<>();
    private final Map<String, FactoryFinder> factories = new ConcurrentHashMap<>();
    private Set<LogListener> logListeners;
    private volatile String description;
    @Deprecated
    private ErrorHandlerFactory errorHandlerFactory;
    private String basePackageScan;
    private boolean lightweight;

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
        return Collections.unmodifiableList(camelContext.servicesToStop);
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
    public CamelBeanPostProcessor getBeanPostProcessor() {
        if (camelContext.beanPostProcessor == null) {
            synchronized (camelContext.lock) {
                if (camelContext.beanPostProcessor == null) {
                    setBeanPostProcessor(camelContext.createBeanPostProcessor());
                }
            }
        }
        return camelContext.beanPostProcessor;
    }

    @Override
    public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {
        camelContext.beanPostProcessor = camelContext.doAddService(beanPostProcessor);
    }

    @Override
    public CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory() {
        if (camelContext.dependencyInjectionAnnotationFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.dependencyInjectionAnnotationFactory == null) {
                    setDependencyInjectionAnnotationFactory(camelContext.createDependencyInjectionAnnotationFactory());
                }
            }
        }
        return camelContext.dependencyInjectionAnnotationFactory;
    }

    @Override
    public void setDependencyInjectionAnnotationFactory(
            CamelDependencyInjectionAnnotationFactory dependencyInjectionAnnotationFactory) {
        camelContext.dependencyInjectionAnnotationFactory = dependencyInjectionAnnotationFactory;
    }

    @Override
    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        return camelContext.managementMBeanAssembler;
    }

    public ComponentResolver getComponentResolver() {
        if (camelContext.componentResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.componentResolver == null) {
                    setComponentResolver(camelContext.createComponentResolver());
                }
            }
        }
        return camelContext.componentResolver;
    }

    public void setComponentResolver(ComponentResolver componentResolver) {
        camelContext.componentResolver = camelContext.doAddService(componentResolver);
    }

    public ComponentNameResolver getComponentNameResolver() {
        if (camelContext.componentNameResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.componentNameResolver == null) {
                    setComponentNameResolver(camelContext.createComponentNameResolver());
                }
            }
        }
        return camelContext.componentNameResolver;
    }

    public void setComponentNameResolver(ComponentNameResolver componentNameResolver) {
        camelContext.componentNameResolver = camelContext.doAddService(componentNameResolver);
    }

    public LanguageResolver getLanguageResolver() {
        if (camelContext.languageResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.languageResolver == null) {
                    setLanguageResolver(camelContext.createLanguageResolver());
                }
            }
        }
        return camelContext.languageResolver;
    }

    public void setLanguageResolver(LanguageResolver languageResolver) {
        camelContext.languageResolver = camelContext.doAddService(languageResolver);
    }

    public ConfigurerResolver getConfigurerResolver() {
        if (camelContext.configurerResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.configurerResolver == null) {
                    setConfigurerResolver(camelContext.createConfigurerResolver());
                }
            }
        }
        return camelContext.configurerResolver;
    }

    public void setConfigurerResolver(ConfigurerResolver configurerResolver) {
        camelContext.configurerResolver = camelContext.doAddService(configurerResolver);
    }

    public UriFactoryResolver getUriFactoryResolver() {
        if (camelContext.uriFactoryResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.uriFactoryResolver == null) {
                    setUriFactoryResolver(camelContext.createUriFactoryResolver());
                }
            }
        }
        return camelContext.uriFactoryResolver;
    }

    public void setUriFactoryResolver(UriFactoryResolver uriFactoryResolver) {
        camelContext.uriFactoryResolver = camelContext.doAddService(uriFactoryResolver);
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
        if (logListeners == null) {
            logListeners = new LinkedHashSet<>();
        }

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
    public ScheduledExecutorService getErrorHandlerExecutorService() {
        if (camelContext.errorHandlerExecutorService == null) {
            synchronized (camelContext.lock) {
                if (camelContext.errorHandlerExecutorService == null) {
                    // setup default thread pool for error handler
                    camelContext.errorHandlerExecutorService = camelContext.createErrorHandlerExecutorService();
                }
            }
        }
        return camelContext.errorHandlerExecutorService;
    }

    @Override
    public UnitOfWorkFactory getUnitOfWorkFactory() {
        if (camelContext.unitOfWorkFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.unitOfWorkFactory == null) {
                    setUnitOfWorkFactory(camelContext.createUnitOfWorkFactory());
                }
            }
        }
        return camelContext.unitOfWorkFactory;
    }

    @Override
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        camelContext.unitOfWorkFactory = camelContext.doAddService(unitOfWorkFactory);
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
    public ConfigurerResolver getBootstrapConfigurerResolver() {
        if (camelContext.bootstrapConfigurerResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.bootstrapConfigurerResolver == null) {
                    camelContext.bootstrapConfigurerResolver = new BootstrapConfigurerResolver(
                            getFactoryFinderResolver().resolveBootstrapFactoryFinder(camelContext.getClassResolver(),
                                    ConfigurerResolver.RESOURCE_PATH));
                }
            }
        }
        return camelContext.bootstrapConfigurerResolver;
    }

    @Override
    public void setBootstrapConfigurerResolver(ConfigurerResolver configurerResolver) {
        camelContext.bootstrapConfigurerResolver = configurerResolver;
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder() {
        if (camelContext.bootstrapFactoryFinder == null) {
            synchronized (camelContext.lock) {
                if (camelContext.bootstrapFactoryFinder == null) {
                    camelContext.bootstrapFactoryFinder
                            = getFactoryFinderResolver().resolveBootstrapFactoryFinder(camelContext.getClassResolver());
                }
            }
        }
        return camelContext.bootstrapFactoryFinder;
    }

    @Override
    public void setBootstrapFactoryFinder(FactoryFinder factoryFinder) {
        camelContext.bootstrapFactoryFinder = factoryFinder;
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder(String path) {
        return camelContext.bootstrapFactories.computeIfAbsent(path, camelContext::createBootstrapFactoryFinder);
    }

    @Override
    public FactoryFinderResolver getFactoryFinderResolver() {
        if (camelContext.factoryFinderResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.factoryFinderResolver == null) {
                    camelContext.factoryFinderResolver = camelContext.createFactoryFinderResolver();
                }
            }
        }
        return camelContext.factoryFinderResolver;
    }

    @Override
    public void setFactoryFinderResolver(FactoryFinderResolver factoryFinderResolver) {
        camelContext.factoryFinderResolver = camelContext.doAddService(factoryFinderResolver);
    }

    @Override
    public FactoryFinder getFactoryFinder(String path) {
        return factories.computeIfAbsent(path, camelContext::createFactoryFinder);
    }

    @Override
    public PackageScanClassResolver getPackageScanClassResolver() {
        if (camelContext.packageScanClassResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.packageScanClassResolver == null) {
                    setPackageScanClassResolver(camelContext.createPackageScanClassResolver());
                }
            }
        }
        return camelContext.packageScanClassResolver;
    }

    @Override
    public void setPackageScanClassResolver(PackageScanClassResolver packageScanClassResolver) {
        camelContext.packageScanClassResolver = camelContext.doAddService(packageScanClassResolver);
    }

    @Override
    public PackageScanResourceResolver getPackageScanResourceResolver() {
        if (camelContext.packageScanResourceResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.packageScanResourceResolver == null) {
                    setPackageScanResourceResolver(camelContext.createPackageScanResourceResolver());
                }
            }
        }
        return camelContext.packageScanResourceResolver;
    }

    @Override
    public void setPackageScanResourceResolver(PackageScanResourceResolver packageScanResourceResolver) {
        camelContext.packageScanResourceResolver = camelContext.doAddService(packageScanResourceResolver);
    }

    @Override
    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        if (camelContext.modelJAXBContextFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.modelJAXBContextFactory == null) {
                    setModelJAXBContextFactory(camelContext.createModelJAXBContextFactory());
                }
            }
        }
        return camelContext.modelJAXBContextFactory;
    }

    @Override
    public void setModelJAXBContextFactory(final ModelJAXBContextFactory modelJAXBContextFactory) {
        camelContext.modelJAXBContextFactory = camelContext.doAddService(modelJAXBContextFactory);
    }

    @Override
    public NodeIdFactory getNodeIdFactory() {
        if (camelContext.nodeIdFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.nodeIdFactory == null) {
                    setNodeIdFactory(camelContext.createNodeIdFactory());
                }
            }
        }
        return camelContext.nodeIdFactory;
    }

    @Override
    public void setNodeIdFactory(NodeIdFactory idFactory) {
        camelContext.nodeIdFactory = camelContext.doAddService(idFactory);
    }

    @Override
    public ModelineFactory getModelineFactory() {
        if (camelContext.modelineFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.modelineFactory == null) {
                    setModelineFactory(camelContext.createModelineFactory());
                }
            }
        }
        return camelContext.modelineFactory;
    }

    @Override
    public void setModelineFactory(ModelineFactory modelineFactory) {
        camelContext.modelineFactory = camelContext.doAddService(modelineFactory);
    }

    @Override
    public PeriodTaskResolver getPeriodTaskResolver() {
        if (camelContext.periodTaskResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.periodTaskResolver == null) {
                    setPeriodTaskResolver(camelContext.createPeriodTaskResolver());
                }
            }
        }
        return camelContext.periodTaskResolver;
    }

    @Override
    public void setPeriodTaskResolver(PeriodTaskResolver periodTaskResolver) {
        camelContext.periodTaskResolver = camelContext.doAddService(periodTaskResolver);
    }

    public PeriodTaskScheduler getPeriodTaskScheduler() {
        if (camelContext.periodTaskScheduler == null) {
            synchronized (camelContext.lock) {
                if (camelContext.periodTaskScheduler == null) {
                    setPeriodTaskScheduler(camelContext.createPeriodTaskScheduler());
                }
            }
        }
        return camelContext.periodTaskScheduler;
    }

    public void setPeriodTaskScheduler(PeriodTaskScheduler periodTaskScheduler) {
        camelContext.periodTaskScheduler = camelContext.doAddService(periodTaskScheduler);
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
    public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
        if (camelContext.asyncProcessorAwaitManager == null) {
            synchronized (camelContext.lock) {
                if (camelContext.asyncProcessorAwaitManager == null) {
                    setAsyncProcessorAwaitManager(camelContext.createAsyncProcessorAwaitManager());
                }
            }
        }
        return camelContext.asyncProcessorAwaitManager;
    }

    @Override
    public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager asyncProcessorAwaitManager) {
        camelContext.asyncProcessorAwaitManager = camelContext.doAddService(asyncProcessorAwaitManager);
    }

    @Override
    public BeanIntrospection getBeanIntrospection() {
        if (camelContext.beanIntrospection == null) {
            synchronized (camelContext.lock) {
                if (camelContext.beanIntrospection == null) {
                    setBeanIntrospection(camelContext.createBeanIntrospection());
                }
            }
        }
        return camelContext.beanIntrospection;
    }

    @Override
    public void setBeanIntrospection(BeanIntrospection beanIntrospection) {
        camelContext.beanIntrospection = camelContext.doAddService(beanIntrospection);
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
    public DataFormatResolver getDataFormatResolver() {
        if (camelContext.dataFormatResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.dataFormatResolver == null) {
                    setDataFormatResolver(camelContext.createDataFormatResolver());
                }
            }
        }
        return camelContext.dataFormatResolver;
    }

    @Override
    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        camelContext.dataFormatResolver = camelContext.doAddService(dataFormatResolver);
    }

    @Override
    public HealthCheckResolver getHealthCheckResolver() {
        if (camelContext.healthCheckResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.healthCheckResolver == null) {
                    setHealthCheckResolver(camelContext.createHealthCheckResolver());
                }
            }
        }
        return camelContext.healthCheckResolver;
    }

    @Override
    public void setHealthCheckResolver(HealthCheckResolver healthCheckResolver) {
        camelContext.healthCheckResolver = camelContext.doAddService(healthCheckResolver);
    }

    public DevConsoleResolver getDevConsoleResolver() {
        if (camelContext.devConsoleResolver == null) {
            synchronized (camelContext.lock) {
                if (camelContext.devConsoleResolver == null) {
                    setDevConsoleResolver(camelContext.createDevConsoleResolver());
                }
            }
        }
        return camelContext.devConsoleResolver;
    }

    public void setDevConsoleResolver(DevConsoleResolver devConsoleResolver) {
        camelContext.devConsoleResolver = camelContext.doAddService(devConsoleResolver);
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
    public ProcessorFactory getProcessorFactory() {
        if (camelContext.processorFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.processorFactory == null) {
                    setProcessorFactory(camelContext.createProcessorFactory());
                }
            }
        }
        return camelContext.processorFactory;
    }

    @Override
    public void setProcessorFactory(ProcessorFactory processorFactory) {
        camelContext.processorFactory = camelContext.doAddService(processorFactory);
    }

    @Override
    public InternalProcessorFactory getInternalProcessorFactory() {
        if (camelContext.internalProcessorFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.internalProcessorFactory == null) {
                    setInternalProcessorFactory(camelContext.createInternalProcessorFactory());
                }
            }
        }
        return camelContext.internalProcessorFactory;
    }

    @Override
    public void setInternalProcessorFactory(InternalProcessorFactory internalProcessorFactory) {
        camelContext.internalProcessorFactory = camelContext.doAddService(internalProcessorFactory);
    }

    @Override
    public InterceptEndpointFactory getInterceptEndpointFactory() {
        if (camelContext.interceptEndpointFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.interceptEndpointFactory == null) {
                    setInterceptEndpointFactory(camelContext.createInterceptEndpointFactory());
                }
            }
        }
        return camelContext.interceptEndpointFactory;
    }

    @Override
    public void setInterceptEndpointFactory(InterceptEndpointFactory interceptEndpointFactory) {
        camelContext.interceptEndpointFactory = camelContext.doAddService(interceptEndpointFactory);
    }

    @Override
    public RouteFactory getRouteFactory() {
        if (camelContext.routeFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.routeFactory == null) {
                    setRouteFactory(camelContext.createRouteFactory());
                }
            }
        }
        return camelContext.routeFactory;
    }

    @Override
    public void setRouteFactory(RouteFactory routeFactory) {
        camelContext.routeFactory = routeFactory;
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        return camelContext.headersMapFactory;
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory headersMapFactory) {
        camelContext.headersMapFactory = camelContext.doAddService(headersMapFactory);
    }

    @Override
    public RoutesLoader getRoutesLoader() {
        if (camelContext.routesLoader == null) {
            synchronized (camelContext.lock) {
                if (camelContext.routesLoader == null) {
                    setRoutesLoader(camelContext.createRoutesLoader());
                }
            }
        }
        return camelContext.routesLoader;
    }

    @Override
    public void setRoutesLoader(RoutesLoader routesLoader) {
        camelContext.routesLoader = camelContext.doAddService(routesLoader);
    }

    @Override
    public ResourceLoader getResourceLoader() {
        if (camelContext.resourceLoader == null) {
            synchronized (camelContext.lock) {
                if (camelContext.resourceLoader == null) {
                    setResourceLoader(camelContext.createResourceLoader());
                }
            }
        }
        return camelContext.resourceLoader;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        camelContext.resourceLoader = camelContext.doAddService(resourceLoader);
    }

    public ModelToXMLDumper getModelToXMLDumper() {
        if (camelContext.modelToXMLDumper == null) {
            synchronized (camelContext.lock) {
                if (camelContext.modelToXMLDumper == null) {
                    setModelToXMLDumper(camelContext.createModelToXMLDumper());
                }
            }
        }
        return camelContext.modelToXMLDumper;
    }

    public void setModelToXMLDumper(ModelToXMLDumper modelToXMLDumper) {
        camelContext.modelToXMLDumper = camelContext.doAddService(modelToXMLDumper);
    }

    public RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory() {
        if (camelContext.restBindingJaxbDataFormatFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.restBindingJaxbDataFormatFactory == null) {
                    setRestBindingJaxbDataFormatFactory(camelContext.createRestBindingJaxbDataFormatFactory());
                }
            }
        }
        return camelContext.restBindingJaxbDataFormatFactory;
    }

    public void setRestBindingJaxbDataFormatFactory(RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory) {
        camelContext.restBindingJaxbDataFormatFactory = restBindingJaxbDataFormatFactory;
    }

    @Override
    public RuntimeCamelCatalog getRuntimeCamelCatalog() {
        if (camelContext.runtimeCamelCatalog == null) {
            synchronized (camelContext.lock) {
                if (camelContext.runtimeCamelCatalog == null) {
                    setRuntimeCamelCatalog(camelContext.createRuntimeCamelCatalog());
                }
            }
        }
        return camelContext.runtimeCamelCatalog;
    }

    @Override
    public void setRuntimeCamelCatalog(RuntimeCamelCatalog runtimeCamelCatalog) {
        camelContext.runtimeCamelCatalog = camelContext.doAddService(runtimeCamelCatalog);
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
        camelContext.exchangeFactoryManager = camelContext.doAddService(exchangeFactoryManager);
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
        camelContext.reactiveExecutor = camelContext.doAddService(reactiveExecutor, false);
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
    public BeanProxyFactory getBeanProxyFactory() {
        if (camelContext.beanProxyFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.beanProxyFactory == null) {
                    camelContext.setBeanProxyFactory(camelContext.createBeanProxyFactory());
                }
            }
        }
        return camelContext.beanProxyFactory;
    }

    @Override
    public BeanProcessorFactory getBeanProcessorFactory() {
        if (camelContext.beanProcessorFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.beanProcessorFactory == null) {
                    camelContext.setBeanProcessorFactory(camelContext.createBeanProcessorFactory());
                }
            }
        }
        return camelContext.beanProcessorFactory;
    }

    @Override
    public RouteController getInternalRouteController() {
        return camelContext.internalRouteController;
    }

    @Override
    public EndpointUriFactory getEndpointUriFactory(String scheme) {
        return getUriFactoryResolver().resolveFactory(scheme, camelContext);
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
    public CliConnectorFactory getCliConnectorFactory() {
        if (camelContext.cliConnectorFactory == null) {
            synchronized (camelContext.lock) {
                if (camelContext.cliConnectorFactory == null) {
                    setCliConnectorFactory(camelContext.createCliConnectorFactory());
                }
            }
        }
        return camelContext.cliConnectorFactory;
    }

    @Override
    public void setCliConnectorFactory(CliConnectorFactory cliConnectorFactory) {
        camelContext.cliConnectorFactory = cliConnectorFactory;
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
}
