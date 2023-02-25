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

package org.apache.camel.impl.lw;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExchangeConstantProvider;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
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
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InterceptEndpointFactory;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.InternalProcessorFactory;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
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
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

class LightweightCamelContextExtension implements ExtendedCamelContext {
    private final CamelContext camelContext;

    public LightweightCamelContextExtension(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public byte getStatusPhase() {
        return camelContext.getCamelContextExtension().getStatusPhase();
    }

    @Override
    public void disposeModel() {
        // noop
    }

    @Override
    public Registry getRegistry() {
        return camelContext.getCamelContextExtension().getRegistry();
    }

    @Override
    public void setRegistry(Registry registry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        return camelContext.getCamelContextExtension().getModelJAXBContextFactory();
    }

    @Override
    public void setModelJAXBContextFactory(ModelJAXBContextFactory modelJAXBContextFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ComponentResolver getComponentResolver() {
        return camelContext.getCamelContextExtension().getComponentResolver();
    }

    @Override
    public void setComponentResolver(ComponentResolver componentResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ComponentNameResolver getComponentNameResolver() {
        return camelContext.getCamelContextExtension().getComponentNameResolver();
    }

    @Override
    public void setComponentNameResolver(ComponentNameResolver componentResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LanguageResolver getLanguageResolver() {
        return camelContext.getCamelContextExtension().getLanguageResolver();
    }

    @Override
    public void setLanguageResolver(LanguageResolver languageResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataFormatResolver getDataFormatResolver() {
        return camelContext.getCamelContextExtension().getDataFormatResolver();
    }

    @Override
    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HealthCheckResolver getHealthCheckResolver() {
        return camelContext.getCamelContextExtension().getHealthCheckResolver();
    }

    @Override
    public void setHealthCheckResolver(HealthCheckResolver healthCheckResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DevConsoleResolver getDevConsoleResolver() {
        return camelContext.getCamelContextExtension().getDevConsoleResolver();
    }

    @Override
    public void setDevConsoleResolver(DevConsoleResolver devConsoleResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return camelContext.getCamelContextExtension().getName();
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription() {
        return camelContext.getCamelContextExtension().getDescription();
    }

    @Override
    public void setDescription(String description) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addBootstrap(BootstrapCloseable bootstrap) {
    }

    @Override
    public List<Service> getServices() {
        return null;
    }

    @Override
    public String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional) {
        if (text != null && text.contains(PropertiesComponent.PREFIX_TOKEN)) {
            // the parser will throw exception if property key was not found
            return camelContext.getPropertiesComponent().parseUri(text, keepUnresolvedOptional);
        }
        // is the value a known field (currently we only support
        // constants from Exchange.class)
        if (text != null && text.startsWith("Exchange.")) {
            String field = StringHelper.after(text, "Exchange.");
            String constant = ExchangeConstantProvider.lookup(field);
            if (constant != null) {
                return constant;
            } else {
                throw new IllegalArgumentException("Constant field with name: " + field + " not found on Exchange.class");
            }
        }
        // return original text as is
        return text;
    }

    @Override
    public String getBasePackageScan() {
        return camelContext.getCamelContextExtension().getBasePackageScan();
    }

    @Override
    public void setBasePackageScan(String basePackageScan) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Endpoint getPrototypeEndpoint(String uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
        return camelContext.getEndpointRegistry().get(uri);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri) {
        return doGetEndpoint(uri.getUri(), true, false);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters) {
        return doGetEndpoint(uri.getUri(), parameters, true);
    }

    @Override
    public NormalizedEndpointUri normalizeUri(String uri) {
        try {
            uri = resolvePropertyPlaceholders(uri, false);
            return NormalizedUri.newNormalizedUri(uri, false);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
    }

    @Override
    public List<RouteStartupOrder> getRouteStartupOrder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CamelBeanPostProcessor getBeanPostProcessor() {
        return camelContext.getCamelContextExtension().getBeanPostProcessor();
    }

    @Override
    public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory() {
        return camelContext.getCamelContextExtension().getDependencyInjectionAnnotationFactory();
    }

    @Override
    public void setDependencyInjectionAnnotationFactory(CamelDependencyInjectionAnnotationFactory factory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ErrorHandlerFactory getErrorHandlerFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageScanClassResolver getPackageScanClassResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPackageScanClassResolver(PackageScanClassResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageScanResourceResolver getPackageScanResourceResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPackageScanResourceResolver(PackageScanResourceResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactoryFinder getDefaultFactoryFinder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConfigurerResolver getBootstrapConfigurerResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBootstrapConfigurerResolver(ConfigurerResolver configurerResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBootstrapFactoryFinder(FactoryFinder factoryFinder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactoryFinder getFactoryFinder(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactoryFinderResolver getFactoryFinderResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFactoryFinderResolver(FactoryFinderResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessorFactory getProcessorFactory() {
        return camelContext.getCamelContextExtension().getProcessorFactory();
    }

    @Override
    public void setProcessorFactory(ProcessorFactory processorFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelineFactory getModelineFactory() {
        return camelContext.getCamelContextExtension().getModelineFactory();
    }

    @Override
    public void setModelineFactory(ModelineFactory modelineFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalProcessorFactory getInternalProcessorFactory() {
        return camelContext.getCamelContextExtension().getInternalProcessorFactory();
    }

    @Override
    public void setInternalProcessorFactory(InternalProcessorFactory internalProcessorFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InterceptEndpointFactory getInterceptEndpointFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInterceptEndpointFactory(InterceptEndpointFactory interceptEndpointFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RouteFactory getRouteFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRouteFactory(RouteFactory routeFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeferServiceFactory getDeferServiceFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDeferServiceFactory(DeferServiceFactory deferServiceFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnitOfWorkFactory getUnitOfWorkFactory() {
        return camelContext.getCamelContextExtension().getUnitOfWorkFactory();
    }

    @Override
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAnnotationBasedProcessorFactory(AnnotationBasedProcessorFactory annotationBasedProcessorFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanProxyFactory getBeanProxyFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanProcessorFactory getBeanProcessorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledExecutorService getErrorHandlerExecutorService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InterceptStrategy> getInterceptStrategies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<LogListener> getLogListeners() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
        return camelContext.getCamelContextExtension().getAsyncProcessorAwaitManager();
    }

    @Override
    public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager manager) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BeanIntrospection getBeanIntrospection() {
        return camelContext.getCamelContextExtension().getBeanIntrospection();
    }

    @Override
    public void setBeanIntrospection(BeanIntrospection beanIntrospection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        return camelContext.getCamelContextExtension().getHeadersMapFactory();
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory factory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CliConnectorFactory getCliConnectorFactory() {
        return camelContext.getCamelContextExtension().getCliConnectorFactory();
    }

    @Override
    public void setCliConnectorFactory(CliConnectorFactory cliConnectorFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        return camelContext.getCamelContextExtension().getExchangeFactory();
    }

    @Override
    public ExchangeFactoryManager getExchangeFactoryManager() {
        return camelContext.getCamelContextExtension().getExchangeFactoryManager();
    }

    @Override
    public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessorExchangeFactory getProcessorExchangeFactory() {
        return camelContext.getCamelContextExtension().getProcessorExchangeFactory();
    }

    @Override
    public void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReactiveExecutor getReactiveExecutor() {
        return camelContext.getCamelContextExtension().getReactiveExecutor();
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEventNotificationApplicable() {
        return camelContext.getCamelContextExtension().isEventNotificationApplicable();
    }

    @Override
    public void setEventNotificationApplicable(boolean eventNotificationApplicable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelToXMLDumper getModelToXMLDumper() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModelToXMLDumper(ModelToXMLDumper modelToXMLDumper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeCamelCatalog getRuntimeCamelCatalog() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRuntimeCamelCatalog(RuntimeCamelCatalog runtimeCamelCatalog) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConfigurerResolver getConfigurerResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConfigurerResolver(ConfigurerResolver configurerResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRoute(Route route) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeRoute(Route route) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        // TODO: need to revisit this in order to support dynamic endpoints uri
        throw new UnsupportedOperationException();
    }

    @Override
    public void setupRoutes(boolean done) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSetupRoutes() {
        return false;
    }

    @Override
    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setupManagement(Map<String, Object> options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLogListener(LogListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRestBindingJaxbDataFormatFactory(RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRoutesLoader(RoutesLoader routesLoader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoutesLoader getRoutesLoader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResourceLoader getResourceLoader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerEndpointCallback(EndpointStrategy strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeIdFactory getNodeIdFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodeIdFactory(NodeIdFactory factory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLightweight(boolean lightweight) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLightweight() {
        return true;
    }

    @Override
    public String getTestExcludeRoutes() {
        return null;
    }

    @Override
    public RouteController getInternalRouteController() {
        return new RouteController() {
            @Override
            public LoggingLevel getLoggingLevel() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setLoggingLevel(LoggingLevel loggingLevel) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isSupervising() {
                return false;
            }

            @Override
            public SupervisingRouteController supervising() {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends RouteController> T adapt(Class<T> type) {
                return type.cast(this);
            }

            @Override
            public Collection<Route> getControlledRoutes() {
                return camelContext.getRoutes();
            }

            @Override
            public void startAllRoutes() throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopAllRoutes() throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeAllRoutes() throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isStartingRoutes() {
                return false;
            }

            @Override
            public void reloadAllRoutes() throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isReloadingRoutes() {
                return false;
            }

            @Override
            public ServiceStatus getRouteStatus(String routeId) {
                return ServiceStatus.Started;
            }

            @Override
            public void startRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopRoute(String routeId, Throwable cause) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout)
                    throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void suspendRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public void resumeRoute(String routeId) throws Exception {
                throw new UnsupportedOperationException();
            }

            @Override
            public CamelContext getCamelContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setCamelContext(CamelContext camelContext) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void start() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void stop() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public UriFactoryResolver getUriFactoryResolver() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setUriFactoryResolver(UriFactoryResolver uriFactoryResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EndpointUriFactory getEndpointUriFactory(String scheme) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StartupStepRecorder getStartupStepRecorder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PeriodTaskResolver getPeriodTaskResolver() {
        return camelContext.getCamelContextExtension().getPeriodTaskResolver();
    }

    @Override
    public void setPeriodTaskScheduler(PeriodTaskScheduler periodTaskScheduler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PeriodTaskScheduler getPeriodTaskScheduler() {
        return camelContext.getCamelContextExtension().getPeriodTaskScheduler();
    }

    @Override
    public void setPeriodTaskResolver(PeriodTaskResolver periodTaskResolver) {
        throw new UnsupportedOperationException();
    }

    Endpoint doGetEndpoint(String uri, boolean normalized, boolean prototype) {
        StringHelper.notEmpty(uri, "uri");
        // in case path has property placeholders then try to let property
        // component resolve those
        if (!normalized) {
            try {
                uri = camelContext.getCamelContextExtension().resolvePropertyPlaceholders(uri, false);
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }
        // normalize uri so we can do endpoint hits with minor mistakes and
        // parameters is not in the same order
        if (!normalized) {
            uri = normalizeEndpointUri(uri);
        }
        Endpoint answer = null;
        if (!prototype) {
            // use optimized method to get the endpoint uri
            NormalizedUri key = NormalizedUri.newNormalizedUri(uri, true);
            // only lookup and reuse existing endpoints if not prototype scoped
            answer = camelContext.getEndpointRegistry().get(key);
        }
        // unknown scheme
        if (answer == null) {
            throw new NoSuchEndpointException(uri);
        }
        return answer;
    }

    protected Endpoint doGetEndpoint(String uri, Map<String, Object> parameters, boolean normalized) {
        StringHelper.notEmpty(uri, "uri");
        // in case path has property placeholders then try to let property
        // component resolve those
        if (!normalized) {
            try {
                uri = resolvePropertyPlaceholders(uri, false);
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        }
        // normalize uri so we can do endpoint hits with minor mistakes and
        // parameters is not in the same order
        if (!normalized) {
            uri = normalizeEndpointUri(uri);
        }
        Endpoint answer;
        String scheme = null;
        // use optimized method to get the endpoint uri
        NormalizedUri key = NormalizedUri.newNormalizedUri(uri, true);
        answer = camelContext.getEndpointRegistry().get(key);
        // unknown scheme
        if (answer == null) {
            throw new ResolveEndpointFailedException(uri, "No component found with scheme: " + scheme);
        }
        return answer;
    }

    /**
     * Normalize uri so we can do endpoint hits with minor mistakes and parameters is not in the same order.
     *
     * @param  uri                            the uri
     * @return                                normalized uri
     * @throws ResolveEndpointFailedException if uri cannot be normalized
     */
    static String normalizeEndpointUri(String uri) {
        try {
            uri = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }
        return uri;
    }
}
