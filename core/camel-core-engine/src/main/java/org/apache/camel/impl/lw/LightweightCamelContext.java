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

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Experimental;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.GlobalEndpointConfiguration;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupListener;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.TypeConverter;
import org.apache.camel.ValueHolder;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.AnnotationBasedProcessorFactory;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.CamelDependencyInjectionAnnotationFactory;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ComponentNameResolver;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DeferServiceFactory;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptEndpointFactory;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.InternalProcessorFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelReifierFactory;
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
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteFactory;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RouteStartupOrder;
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
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.vault.VaultConfiguration;

@Experimental
public class LightweightCamelContext implements ExtendedCamelContext, CatalogCamelContext, ModelCamelContext {

    protected volatile CamelContext delegate;

    protected LightweightCamelContext(CamelContext delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates the {@link ModelCamelContext} using {@link org.apache.camel.support.DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public LightweightCamelContext() {
        DefaultCamelContext d = new DefaultCamelContext(false) {
            @Override
            public CamelContext getCamelContextReference() {
                return LightweightCamelContext.this;
            }
        };
        delegate = d;
        d.build();
    }

    /**
     * Creates the {@link CamelContext} using the given {@link BeanRepository} as first-choice repository, and the
     * {@link org.apache.camel.support.SimpleRegistry} as fallback, via the {@link DefaultRegistry} implementation.
     *
     * @param repository the bean repository.
     */
    public LightweightCamelContext(BeanRepository repository) {
        this(new DefaultRegistry(repository));
    }

    /**
     * Creates the {@link ModelCamelContext} using the given registry
     *
     * @param registry the registry
     */
    public LightweightCamelContext(Registry registry) {
        this();
        setRegistry(registry);
    }

    public CamelContext getCamelContextReference() {
        return this;
    }

    @Override
    public byte getStatusPhase() {
        return delegate.adapt(ExtendedCamelContext.class).getStatusPhase();
    }

    @Override
    public void disposeModel() {
        delegate.adapt(ExtendedCamelContext.class).disposeModel();
    }

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean isStarting() {
        return delegate.isStarting();
    }

    @Override
    public boolean isStopped() {
        return delegate.isStopped();
    }

    @Override
    public boolean isStopping() {
        return delegate.isStopping();
    }

    @Override
    public boolean isSuspended() {
        return delegate.isSuspended();
    }

    @Override
    public boolean isRunAllowed() {
        return delegate.isRunAllowed();
    }

    @Override
    public boolean isSuspending() {
        return delegate.isSuspending();
    }

    @Override
    public void build() {
        delegate.build();
    }

    @Override
    public void suspend() {
        delegate.suspend();
    }

    @Override
    public void resume() {
        delegate.resume();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    @Override
    public <T extends CamelContext> T adapt(Class<T> type) {
        T res = delegate.adapt(type);
        if (res == delegate) {
            return type.cast(this);
        } else {
            return res;
        }
    }

    @Override
    public <T> T getExtension(Class<T> type) {
        return delegate.getExtension(type);
    }

    @Override
    public <T> void setExtension(Class<T> type, T module) {
        delegate.setExtension(type, module);
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public boolean isVetoStarted() {
        return delegate.isVetoStarted();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public CamelContextNameStrategy getNameStrategy() {
        return delegate.getNameStrategy();
    }

    @Override
    public void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        delegate.setNameStrategy(nameStrategy);
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void setDescription(String description) {
        delegate.adapt(ExtendedCamelContext.class).setDescription(description);
    }

    @Override
    public ManagementNameStrategy getManagementNameStrategy() {
        return delegate.getManagementNameStrategy();
    }

    @Override
    public void setManagementNameStrategy(ManagementNameStrategy nameStrategy) {
        delegate.setManagementNameStrategy(nameStrategy);
    }

    @Override
    public String getManagementName() {
        return delegate.getManagementName();
    }

    @Override
    public void setManagementName(String name) {
        delegate.setManagementName(name);
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public ServiceStatus getStatus() {
        return delegate.getStatus();
    }

    @Override
    public String getUptime() {
        return delegate.getUptime();
    }

    @Override
    public long getUptimeMillis() {
        return delegate.getUptimeMillis();
    }

    @Override
    public Date getStartDate() {
        return delegate.getStartDate();
    }

    @Override
    public void addBootstrap(BootstrapCloseable bootstrap) {
        getExtendedCamelContext().addBootstrap(bootstrap);
    }

    @Override
    public void addService(Object object) throws Exception {
        delegate.addService(object);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown) throws Exception {
        delegate.addService(object, stopOnShutdown);
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception {
        delegate.addService(object, stopOnShutdown, forceStart);
    }

    @Override
    public void addPrototypeService(Object object) throws Exception {
        delegate.addPrototypeService(object);
    }

    @Override
    public boolean removeService(Object object) throws Exception {
        return delegate.removeService(object);
    }

    @Override
    public List<Service> getServices() {
        return getExtendedCamelContext().getServices();
    }

    @Override
    public boolean hasService(Object object) {
        return delegate.hasService(object);
    }

    @Override
    public <T> T hasService(Class<T> type) {
        return delegate.hasService(type);
    }

    @Override
    public <T> Set<T> hasServices(Class<T> type) {
        return delegate.hasServices(type);
    }

    @Override
    public void deferStartService(Object object, boolean stopOnShutdown) throws Exception {
        delegate.deferStartService(object, stopOnShutdown);
    }

    @Override
    public void addStartupListener(StartupListener listener) throws Exception {
        delegate.addStartupListener(listener);
    }

    @Override
    public void addComponent(String componentName, Component component) {
        delegate.addComponent(componentName, component);
    }

    @Override
    public Component hasComponent(String componentName) {
        return delegate.hasComponent(componentName);
    }

    @Override
    public Component getComponent(String componentName) {
        return delegate.getComponent(componentName);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents) {
        return delegate.getComponent(name, autoCreateComponents);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents, boolean autoStart) {
        return delegate.getComponent(name, autoCreateComponents, autoStart);
    }

    @Override
    public <T extends Component> T getComponent(String name, Class<T> componentType) {
        return delegate.getComponent(name, componentType);
    }

    @Override
    public Set<String> getComponentNames() {
        return delegate.getComponentNames();
    }

    @Override
    public Component removeComponent(String componentName) {
        return delegate.removeComponent(componentName);
    }

    @Override
    public EndpointRegistry<? extends ValueHolder<String>> getEndpointRegistry() {
        return delegate.getEndpointRegistry();
    }

    @Override
    public Endpoint getEndpoint(String uri) {
        return delegate.getEndpoint(uri);
    }

    @Override
    public Endpoint getEndpoint(String uri, Map<String, Object> parameters) {
        return delegate.getEndpoint(uri, parameters);
    }

    @Override
    public <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType) {
        return delegate.getEndpoint(name, endpointType);
    }

    @Override
    public Collection<Endpoint> getEndpoints() {
        return delegate.getEndpoints();
    }

    @Override
    @Deprecated
    public Map<String, Endpoint> getEndpointMap() {
        return delegate.getEndpointMap();
    }

    @Override
    public Endpoint hasEndpoint(String uri) {
        return delegate.hasEndpoint(uri);
    }

    @Override
    public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
        return delegate.addEndpoint(uri, endpoint);
    }

    @Override
    public void removeEndpoint(Endpoint endpoint) throws Exception {
        delegate.removeEndpoint(endpoint);
    }

    @Override
    public Collection<Endpoint> removeEndpoints(String pattern) throws Exception {
        return delegate.removeEndpoints(pattern);
    }

    @Override
    public GlobalEndpointConfiguration getGlobalEndpointConfiguration() {
        return delegate.getGlobalEndpointConfiguration();
    }

    @Override
    public void setRouteController(RouteController routeController) {
        delegate.setRouteController(routeController);
    }

    @Override
    public RouteController getRouteController() {
        return delegate.getRouteController();
    }

    @Override
    public List<Route> getRoutes() {
        return delegate.getRoutes();
    }

    @Override
    public int getRoutesSize() {
        return delegate.getRoutesSize();
    }

    @Override
    public Route getRoute(String id) {
        return delegate.getRoute(id);
    }

    @Override
    public Processor getProcessor(String id) {
        return delegate.getProcessor(id);
    }

    @Override
    public <T extends Processor> T getProcessor(String id, Class<T> type) {
        return delegate.getProcessor(id, type);
    }

    @Override
    public void addRoutes(RoutesBuilder builder) throws Exception {
        delegate.addRoutes(builder);
    }

    @Override
    public void addRoutesConfigurations(RouteConfigurationsBuilder builder) throws Exception {
        delegate.addRoutesConfigurations(builder);
    }

    @Override
    public boolean removeRoute(String routeId) throws Exception {
        return delegate.removeRoute(routeId);
    }

    @Override
    public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {
        delegate.addRoutePolicyFactory(routePolicyFactory);
    }

    @Override
    public List<RoutePolicyFactory> getRoutePolicyFactories() {
        return delegate.getRoutePolicyFactories();
    }

    @Override
    public void setRestConfiguration(RestConfiguration restConfiguration) {
        delegate.setRestConfiguration(restConfiguration);
    }

    @Override
    public RestConfiguration getRestConfiguration() {
        return delegate.getRestConfiguration();
    }

    @Override
    public void setVaultConfiguration(VaultConfiguration vaultConfiguration) {
        delegate.setVaultConfiguration(vaultConfiguration);
    }

    @Override
    public VaultConfiguration getVaultConfiguration() {
        return delegate.getVaultConfiguration();
    }

    @Override
    public RestRegistry getRestRegistry() {
        return delegate.getRestRegistry();
    }

    @Override
    public void setRestRegistry(RestRegistry restRegistry) {
        delegate.setRestRegistry(restRegistry);
    }

    @Override
    public TypeConverter getTypeConverter() {
        return delegate.getTypeConverter();
    }

    @Override
    public TypeConverterRegistry getTypeConverterRegistry() {
        return delegate.getTypeConverterRegistry();
    }

    @Override
    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        delegate.setTypeConverterRegistry(typeConverterRegistry);
    }

    @Override
    public Registry getRegistry() {
        return delegate.getRegistry();
    }

    @Override
    public <T> T getRegistry(Class<T> type) {
        return delegate.getRegistry(type);
    }

    @Override
    public Injector getInjector() {
        return delegate.getInjector();
    }

    @Override
    public void setInjector(Injector injector) {
        delegate.setInjector(injector);
    }

    @Override
    public List<LifecycleStrategy> getLifecycleStrategies() {
        return delegate.getLifecycleStrategies();
    }

    @Override
    public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        delegate.addLifecycleStrategy(lifecycleStrategy);
    }

    @Override
    public Language resolveLanguage(String language) throws NoSuchLanguageException {
        return delegate.resolveLanguage(language);
    }

    @Override
    public String resolvePropertyPlaceholders(String text) {
        return delegate.resolvePropertyPlaceholders(text);
    }

    @Override
    public String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional) {
        return getExtendedCamelContext().resolvePropertyPlaceholders(text, keepUnresolvedOptional);
    }

    @Override
    public PropertiesComponent getPropertiesComponent() {
        return delegate.getPropertiesComponent();
    }

    @Override
    public void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        delegate.setPropertiesComponent(propertiesComponent);
    }

    @Override
    public Set<String> getLanguageNames() {
        return delegate.getLanguageNames();
    }

    @Override
    public ProducerTemplate createProducerTemplate() {
        return delegate.createProducerTemplate();
    }

    @Override
    public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
        return delegate.createProducerTemplate(maximumCacheSize);
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate() {
        return delegate.createFluentProducerTemplate();
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize) {
        return delegate.createFluentProducerTemplate(maximumCacheSize);
    }

    @Override
    public ConsumerTemplate createConsumerTemplate() {
        return delegate.createConsumerTemplate();
    }

    @Override
    public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
        return delegate.createConsumerTemplate(maximumCacheSize);
    }

    @Override
    public DataFormat resolveDataFormat(String name) {
        return delegate.resolveDataFormat(name);
    }

    @Override
    public DataFormat createDataFormat(String name) {
        return delegate.createDataFormat(name);
    }

    @Override
    public Set<String> getDataFormatNames() {
        return delegate.getDataFormatNames();
    }

    @Override
    public Transformer resolveTransformer(String model) {
        return delegate.resolveTransformer(model);
    }

    @Override
    public Transformer resolveTransformer(DataType from, DataType to) {
        return delegate.resolveTransformer(from, to);
    }

    @Override
    public TransformerRegistry getTransformerRegistry() {
        return delegate.getTransformerRegistry();
    }

    @Override
    public Validator resolveValidator(DataType type) {
        return delegate.resolveValidator(type);
    }

    @Override
    public ValidatorRegistry getValidatorRegistry() {
        return delegate.getValidatorRegistry();
    }

    @Override
    public void setGlobalOptions(Map<String, String> globalOptions) {
        delegate.setGlobalOptions(globalOptions);
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        return delegate.getGlobalOptions();
    }

    @Override
    public String getGlobalOption(String key) {
        return delegate.getGlobalOption(key);
    }

    @Override
    public ClassResolver getClassResolver() {
        return delegate.getClassResolver();
    }

    @Override
    public void setClassResolver(ClassResolver resolver) {
        delegate.setClassResolver(resolver);
    }

    @Override
    public ManagementStrategy getManagementStrategy() {
        return delegate.getManagementStrategy();
    }

    @Override
    public void setManagementStrategy(ManagementStrategy strategy) {
        delegate.setManagementStrategy(strategy);
    }

    @Override
    public void disableJMX() throws IllegalStateException {
        delegate.disableJMX();
    }

    @Override
    public InflightRepository getInflightRepository() {
        return delegate.getInflightRepository();
    }

    @Override
    public void setInflightRepository(InflightRepository repository) {
        delegate.setInflightRepository(repository);
    }

    @Override
    public ClassLoader getApplicationContextClassLoader() {
        return delegate.getApplicationContextClassLoader();
    }

    @Override
    public void setApplicationContextClassLoader(ClassLoader classLoader) {
        delegate.setApplicationContextClassLoader(classLoader);
    }

    @Override
    public ShutdownStrategy getShutdownStrategy() {
        return delegate.getShutdownStrategy();
    }

    @Override
    public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        delegate.setShutdownStrategy(shutdownStrategy);
    }

    @Override
    public ExecutorServiceManager getExecutorServiceManager() {
        return delegate.getExecutorServiceManager();
    }

    @Override
    public void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {
        delegate.setExecutorServiceManager(executorServiceManager);
    }

    @Override
    public MessageHistoryFactory getMessageHistoryFactory() {
        return delegate.getMessageHistoryFactory();
    }

    @Override
    public void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {
        delegate.setMessageHistoryFactory(messageHistoryFactory);
    }

    @Override
    public Debugger getDebugger() {
        return delegate.getDebugger();
    }

    @Override
    public void setDebugger(Debugger debugger) {
        delegate.setDebugger(debugger);
    }

    @Override
    public Tracer getTracer() {
        return delegate.getTracer();
    }

    @Override
    public void setTracer(Tracer tracer) {
        delegate.setTracer(tracer);
    }

    @Override
    public void setTracingStandby(boolean tracingStandby) {
        delegate.setTracingStandby(tracingStandby);
    }

    @Override
    public boolean isTracingStandby() {
        return delegate.isTracingStandby();
    }

    @Override
    public UuidGenerator getUuidGenerator() {
        return delegate.getUuidGenerator();
    }

    @Override
    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        delegate.setUuidGenerator(uuidGenerator);
    }

    @Override
    public Boolean isLoadTypeConverters() {
        return delegate.isLoadTypeConverters();
    }

    @Override
    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        delegate.setLoadTypeConverters(loadTypeConverters);
    }

    @Override
    public Boolean isTypeConverterStatisticsEnabled() {
        return delegate.isTypeConverterStatisticsEnabled();
    }

    @Override
    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        delegate.setTypeConverterStatisticsEnabled(typeConverterStatisticsEnabled);
    }

    @Override
    public Boolean isLoadHealthChecks() {
        return delegate.isLoadHealthChecks();
    }

    @Override
    public void setLoadHealthChecks(Boolean loadHealthChecks) {
        delegate.setLoadHealthChecks(loadHealthChecks);
    }

    @Override
    public Boolean isSourceLocationEnabled() {
        return delegate.isSourceLocationEnabled();
    }

    @Override
    public void setSourceLocationEnabled(Boolean sourceLocationEnabled) {
        delegate.setSourceLocationEnabled(sourceLocationEnabled);
    }

    @Override
    public Boolean isModeline() {
        return delegate.isModeline();
    }

    @Override
    public void setModeline(Boolean modeline) {
        delegate.setModeline(modeline);
    }

    @Override
    public Boolean isDevConsole() {
        return delegate.isDevConsole();
    }

    @Override
    public void setDevConsole(Boolean loadDevConsoles) {
        delegate.setDevConsole(loadDevConsoles);
    }

    @Override
    public Boolean isDumpRoutes() {
        return delegate.isDumpRoutes();
    }

    @Override
    public void setDumpRoutes(Boolean dumpRoutes) {
        delegate.setDumpRoutes(dumpRoutes);
    }

    @Override
    public String getBasePackageScan() {
        return getExtendedCamelContext().getBasePackageScan();
    }

    @Override
    public void setBasePackageScan(String basePackageScan) {
        getExtendedCamelContext().setBasePackageScan(basePackageScan);
    }

    @Override
    public Boolean isUseMDCLogging() {
        return delegate.isUseMDCLogging();
    }

    @Override
    public void setUseMDCLogging(Boolean useMDCLogging) {
        delegate.setUseMDCLogging(useMDCLogging);
    }

    @Override
    public String getMDCLoggingKeysPattern() {
        return delegate.getMDCLoggingKeysPattern();
    }

    @Override
    public void setMDCLoggingKeysPattern(String pattern) {
        delegate.setMDCLoggingKeysPattern(pattern);
    }

    @Override
    public Boolean isUseDataType() {
        return delegate.isUseDataType();
    }

    @Override
    public void setUseDataType(Boolean useDataType) {
        delegate.setUseDataType(useDataType);
    }

    @Override
    public Boolean isUseBreadcrumb() {
        return delegate.isUseBreadcrumb();
    }

    @Override
    public void setUseBreadcrumb(Boolean useBreadcrumb) {
        delegate.setUseBreadcrumb(useBreadcrumb);
    }

    @Override
    public StreamCachingStrategy getStreamCachingStrategy() {
        return delegate.getStreamCachingStrategy();
    }

    @Override
    public void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {
        delegate.setStreamCachingStrategy(streamCachingStrategy);
    }

    @Override
    public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
        return delegate.getRuntimeEndpointRegistry();
    }

    @Override
    public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {
        delegate.setRuntimeEndpointRegistry(runtimeEndpointRegistry);
    }

    @Override
    public void setSSLContextParameters(SSLContextParameters sslContextParameters) {
        delegate.setSSLContextParameters(sslContextParameters);
    }

    @Override
    public SSLContextParameters getSSLContextParameters() {
        return delegate.getSSLContextParameters();
    }

    @Override
    public void setStartupSummaryLevel(StartupSummaryLevel startupSummaryLevel) {
        delegate.setStartupSummaryLevel(startupSummaryLevel);
    }

    @Override
    public StartupSummaryLevel getStartupSummaryLevel() {
        return delegate.getStartupSummaryLevel();
    }

    @Override
    public void setStreamCaching(Boolean cache) {
        delegate.setStreamCaching(cache);
    }

    @Override
    public Boolean isStreamCaching() {
        return delegate.isStreamCaching();
    }

    @Override
    public void setTracing(Boolean tracing) {
        delegate.setTracing(tracing);
    }

    @Override
    public Boolean isTracing() {
        return delegate.isTracing();
    }

    @Override
    public String getTracingPattern() {
        return delegate.getTracingPattern();
    }

    @Override
    public void setTracingPattern(String tracePattern) {
        delegate.setTracingPattern(tracePattern);
    }

    @Override
    public String getTracingLoggingFormat() {
        return delegate.getTracingLoggingFormat();
    }

    @Override
    public void setTracingLoggingFormat(String format) {
        delegate.setTracingLoggingFormat(format);
    }

    @Override
    public void setBacklogTracing(Boolean backlogTrace) {
        delegate.setBacklogTracing(backlogTrace);
    }

    @Override
    public Boolean isBacklogTracing() {
        return delegate.isBacklogTracing();
    }

    @Override
    public void setDebugging(Boolean debugging) {
        delegate.setDebugging(debugging);
    }

    @Override
    public Boolean isDebugging() {
        return delegate.isDebugging();
    }

    @Override
    public void setMessageHistory(Boolean messageHistory) {
        delegate.setMessageHistory(messageHistory);
    }

    @Override
    public Boolean isMessageHistory() {
        return delegate.isMessageHistory();
    }

    @Override
    public void setLogMask(Boolean logMask) {
        delegate.setLogMask(logMask);
    }

    @Override
    public Boolean isLogMask() {
        return delegate.isLogMask();
    }

    @Override
    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        delegate.setLogExhaustedMessageBody(logExhaustedMessageBody);
    }

    @Override
    public Boolean isLogExhaustedMessageBody() {
        return delegate.isLogExhaustedMessageBody();
    }

    @Override
    public void setDelayer(Long delay) {
        delegate.setDelayer(delay);
    }

    @Override
    public Long getDelayer() {
        return delegate.getDelayer();
    }

    @Override
    public void setAutoStartup(Boolean autoStartup) {
        delegate.setAutoStartup(autoStartup);
    }

    @Override
    public Boolean isAutoStartup() {
        return delegate.isAutoStartup();
    }

    @Override
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        delegate.setShutdownRoute(shutdownRoute);
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        return delegate.getShutdownRoute();
    }

    @Override
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        delegate.setShutdownRunningTask(shutdownRunningTask);
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        return delegate.getShutdownRunningTask();
    }

    @Override
    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        delegate.setAllowUseOriginalMessage(allowUseOriginalMessage);
    }

    @Override
    public Boolean isAllowUseOriginalMessage() {
        return delegate.isAllowUseOriginalMessage();
    }

    @Override
    public Boolean isCaseInsensitiveHeaders() {
        return delegate.isCaseInsensitiveHeaders();
    }

    @Override
    public void setCaseInsensitiveHeaders(Boolean caseInsensitiveHeaders) {
        delegate.setCaseInsensitiveHeaders(caseInsensitiveHeaders);
    }

    @Override
    public Boolean isAutowiredEnabled() {
        return delegate.isAutowiredEnabled();
    }

    @Override
    public void setAutowiredEnabled(Boolean autowiredEnabled) {
        delegate.setAutowiredEnabled(autowiredEnabled);
    }

    @Override
    public void removeRouteTemplates(String pattern) throws Exception {
        delegate.removeRouteTemplates(pattern);
    }

    //
    // ExtendedCamelContext
    //

    protected ExtendedCamelContext getExtendedCamelContext() {
        return delegate.adapt(ExtendedCamelContext.class);
    }

    @Override
    public void setName(String name) {
        getExtendedCamelContext().setName(name);
    }

    @Override
    public void setRegistry(Registry registry) {
        getExtendedCamelContext().setRegistry(registry);
    }

    @Override
    public void setupRoutes(boolean done) {
        getExtendedCamelContext().setupRoutes(done);
    }

    @Override
    public boolean isSetupRoutes() {
        return getExtendedCamelContext().isSetupRoutes();
    }

    @Override
    public void registerEndpointCallback(EndpointStrategy strategy) {
        getExtendedCamelContext().registerEndpointCallback(strategy);
    }

    @Override
    public Endpoint getPrototypeEndpoint(String uri) {
        return getExtendedCamelContext().getPrototypeEndpoint(uri);
    }

    @Override
    public Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri) {
        return getExtendedCamelContext().getPrototypeEndpoint(uri);
    }

    @Override
    public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
        return getExtendedCamelContext().hasEndpoint(uri);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri) {
        return getExtendedCamelContext().getEndpoint(uri);
    }

    @Override
    public Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters) {
        return getExtendedCamelContext().getEndpoint(uri, parameters);
    }

    @Override
    public NormalizedEndpointUri normalizeUri(String uri) {
        return getExtendedCamelContext().normalizeUri(uri);
    }

    @Override
    public List<RouteStartupOrder> getRouteStartupOrder() {
        return getExtendedCamelContext().getRouteStartupOrder();
    }

    @Override
    public CamelBeanPostProcessor getBeanPostProcessor() {
        return getExtendedCamelContext().getBeanPostProcessor();
    }

    @Override
    public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {
        getExtendedCamelContext().setBeanPostProcessor(beanPostProcessor);
    }

    @Override
    public CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory() {
        return getExtendedCamelContext().getDependencyInjectionAnnotationFactory();
    }

    @Override
    public void setDependencyInjectionAnnotationFactory(CamelDependencyInjectionAnnotationFactory factory) {
        getExtendedCamelContext().setDependencyInjectionAnnotationFactory(factory);
    }

    @Override
    public ManagementMBeanAssembler getManagementMBeanAssembler() {
        return getExtendedCamelContext().getManagementMBeanAssembler();
    }

    @Override
    public ErrorHandlerFactory getErrorHandlerFactory() {
        return getExtendedCamelContext().getErrorHandlerFactory();
    }

    @Override
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        getExtendedCamelContext().setErrorHandlerFactory(errorHandlerFactory);
    }

    @Override
    public void setNodeIdFactory(NodeIdFactory factory) {
        getExtendedCamelContext().setNodeIdFactory(factory);
    }

    @Override
    public NodeIdFactory getNodeIdFactory() {
        return getExtendedCamelContext().getNodeIdFactory();
    }

    @Override
    public ComponentResolver getComponentResolver() {
        return getExtendedCamelContext().getComponentResolver();
    }

    @Override
    public void setComponentResolver(ComponentResolver componentResolver) {
        getExtendedCamelContext().setComponentResolver(componentResolver);
    }

    @Override
    public ComponentNameResolver getComponentNameResolver() {
        return getExtendedCamelContext().getComponentNameResolver();
    }

    @Override
    public void setComponentNameResolver(ComponentNameResolver componentNameResolver) {
        getExtendedCamelContext().setComponentNameResolver(componentNameResolver);
    }

    @Override
    public LanguageResolver getLanguageResolver() {
        return getExtendedCamelContext().getLanguageResolver();
    }

    @Override
    public void setLanguageResolver(LanguageResolver languageResolver) {
        getExtendedCamelContext().setLanguageResolver(languageResolver);
    }

    @Override
    public DataFormatResolver getDataFormatResolver() {
        return getExtendedCamelContext().getDataFormatResolver();
    }

    @Override
    public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {
        getExtendedCamelContext().setDataFormatResolver(dataFormatResolver);
    }

    @Override
    public HealthCheckResolver getHealthCheckResolver() {
        return getExtendedCamelContext().getHealthCheckResolver();
    }

    @Override
    public void setHealthCheckResolver(HealthCheckResolver healthCheckResolver) {
        getExtendedCamelContext().setHealthCheckResolver(healthCheckResolver);
    }

    @Override
    public DevConsoleResolver getDevConsoleResolver() {
        return getExtendedCamelContext().getDevConsoleResolver();
    }

    @Override
    public void setDevConsoleResolver(DevConsoleResolver devConsoleResolver) {
        getExtendedCamelContext().setDevConsoleResolver(devConsoleResolver);
    }

    @Override
    public PackageScanClassResolver getPackageScanClassResolver() {
        return getExtendedCamelContext().getPackageScanClassResolver();
    }

    @Override
    public void setPackageScanClassResolver(PackageScanClassResolver resolver) {
        getExtendedCamelContext().setPackageScanClassResolver(resolver);
    }

    @Override
    public PackageScanResourceResolver getPackageScanResourceResolver() {
        return getExtendedCamelContext().getPackageScanResourceResolver();
    }

    @Override
    public void setPackageScanResourceResolver(PackageScanResourceResolver resolver) {
        getExtendedCamelContext().setPackageScanResourceResolver(resolver);
    }

    @Override
    public FactoryFinder getDefaultFactoryFinder() {
        return getExtendedCamelContext().getDefaultFactoryFinder();
    }

    @Override
    public ConfigurerResolver getBootstrapConfigurerResolver() {
        return getExtendedCamelContext().getBootstrapConfigurerResolver();
    }

    @Override
    public void setBootstrapConfigurerResolver(ConfigurerResolver configurerResolver) {
        getExtendedCamelContext().setBootstrapConfigurerResolver(configurerResolver);
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder() {
        return getExtendedCamelContext().getBootstrapFactoryFinder();
    }

    @Override
    public void setBootstrapFactoryFinder(FactoryFinder factoryFinder) {
        getExtendedCamelContext().setBootstrapFactoryFinder(factoryFinder);
    }

    @Override
    public FactoryFinder getBootstrapFactoryFinder(String path) {
        return getExtendedCamelContext().getBootstrapFactoryFinder(path);
    }

    @Override
    public FactoryFinder getFactoryFinder(String path) {
        return getExtendedCamelContext().getFactoryFinder(path);
    }

    @Override
    public void setFactoryFinderResolver(FactoryFinderResolver resolver) {
        getExtendedCamelContext().setFactoryFinderResolver(resolver);
    }

    @Override
    public FactoryFinderResolver getFactoryFinderResolver() {
        return getExtendedCamelContext().getFactoryFinderResolver();
    }

    @Override
    public ProcessorFactory getProcessorFactory() {
        return getExtendedCamelContext().getProcessorFactory();
    }

    @Override
    public void setProcessorFactory(ProcessorFactory processorFactory) {
        getExtendedCamelContext().setProcessorFactory(processorFactory);
    }

    @Override
    public ModelineFactory getModelineFactory() {
        return getExtendedCamelContext().getModelineFactory();
    }

    @Override
    public void setModelineFactory(ModelineFactory modelineFactory) {
        getExtendedCamelContext().setModelineFactory(modelineFactory);
    }

    @Override
    public InternalProcessorFactory getInternalProcessorFactory() {
        return getExtendedCamelContext().getInternalProcessorFactory();
    }

    @Override
    public void setInternalProcessorFactory(InternalProcessorFactory internalProcessorFactory) {
        getExtendedCamelContext().setInternalProcessorFactory(internalProcessorFactory);
    }

    @Override
    public InterceptEndpointFactory getInterceptEndpointFactory() {
        return getExtendedCamelContext().getInterceptEndpointFactory();
    }

    @Override
    public void setInterceptEndpointFactory(InterceptEndpointFactory interceptEndpointFactory) {
        getExtendedCamelContext().setInterceptEndpointFactory(interceptEndpointFactory);
    }

    @Override
    public RouteFactory getRouteFactory() {
        return getExtendedCamelContext().getRouteFactory();
    }

    @Override
    public void setRouteFactory(RouteFactory routeFactory) {
        getExtendedCamelContext().setRouteFactory(routeFactory);
    }

    @Override
    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        return getExtendedCamelContext().getModelJAXBContextFactory();
    }

    @Override
    public void setModelJAXBContextFactory(ModelJAXBContextFactory modelJAXBContextFactory) {
        getExtendedCamelContext().setModelJAXBContextFactory(modelJAXBContextFactory);
    }

    @Override
    public DeferServiceFactory getDeferServiceFactory() {
        return getExtendedCamelContext().getDeferServiceFactory();
    }

    @Override
    public void setDeferServiceFactory(DeferServiceFactory deferServiceFactory) {
        getExtendedCamelContext().setDeferServiceFactory(deferServiceFactory);
    }

    @Override
    public UnitOfWorkFactory getUnitOfWorkFactory() {
        return getExtendedCamelContext().getUnitOfWorkFactory();
    }

    @Override
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        getExtendedCamelContext().setUnitOfWorkFactory(unitOfWorkFactory);
    }

    @Override
    public AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory() {
        return getExtendedCamelContext().getAnnotationBasedProcessorFactory();
    }

    @Override
    public void setAnnotationBasedProcessorFactory(AnnotationBasedProcessorFactory annotationBasedProcessorFactory) {
        getExtendedCamelContext().setAnnotationBasedProcessorFactory(annotationBasedProcessorFactory);
    }

    @Override
    public BeanProxyFactory getBeanProxyFactory() {
        return getExtendedCamelContext().getBeanProxyFactory();
    }

    @Override
    public BeanProcessorFactory getBeanProcessorFactory() {
        return getExtendedCamelContext().getBeanProcessorFactory();
    }

    @Override
    public ScheduledExecutorService getErrorHandlerExecutorService() {
        return getExtendedCamelContext().getErrorHandlerExecutorService();
    }

    @Override
    public void addInterceptStrategy(InterceptStrategy interceptStrategy) {
        getExtendedCamelContext().addInterceptStrategy(interceptStrategy);
    }

    @Override
    public List<InterceptStrategy> getInterceptStrategies() {
        return getExtendedCamelContext().getInterceptStrategies();
    }

    @Override
    public void setupManagement(Map<String, Object> options) {
        getExtendedCamelContext().setupManagement(options);
    }

    @Override
    public Set<LogListener> getLogListeners() {
        return getExtendedCamelContext().getLogListeners();
    }

    @Override
    public void addLogListener(LogListener listener) {
        getExtendedCamelContext().addLogListener(listener);
    }

    @Override
    public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
        return getExtendedCamelContext().getAsyncProcessorAwaitManager();
    }

    @Override
    public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager manager) {
        getExtendedCamelContext().setAsyncProcessorAwaitManager(manager);
    }

    @Override
    public BeanIntrospection getBeanIntrospection() {
        return getExtendedCamelContext().getBeanIntrospection();
    }

    @Override
    public void setBeanIntrospection(BeanIntrospection beanIntrospection) {
        getExtendedCamelContext().setBeanIntrospection(beanIntrospection);
    }

    @Override
    public HeadersMapFactory getHeadersMapFactory() {
        return getExtendedCamelContext().getHeadersMapFactory();
    }

    @Override
    public void setHeadersMapFactory(HeadersMapFactory factory) {
        getExtendedCamelContext().setHeadersMapFactory(factory);
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        return getExtendedCamelContext().getExchangeFactory();
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        getExtendedCamelContext().setExchangeFactory(exchangeFactory);
    }

    @Override
    public ExchangeFactoryManager getExchangeFactoryManager() {
        return getExtendedCamelContext().getExchangeFactoryManager();
    }

    @Override
    public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {
        getExtendedCamelContext().setExchangeFactoryManager(exchangeFactoryManager);
    }

    @Override
    public ProcessorExchangeFactory getProcessorExchangeFactory() {
        return getExtendedCamelContext().getProcessorExchangeFactory();
    }

    @Override
    public void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory) {
        getExtendedCamelContext().setProcessorExchangeFactory(processorExchangeFactory);
    }

    @Override
    public ReactiveExecutor getReactiveExecutor() {
        return getExtendedCamelContext().getReactiveExecutor();
    }

    @Override
    public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {
        getExtendedCamelContext().setReactiveExecutor(reactiveExecutor);
    }

    @Override
    public boolean isEventNotificationApplicable() {
        return getExtendedCamelContext().isEventNotificationApplicable();
    }

    @Override
    public void setEventNotificationApplicable(boolean eventNotificationApplicable) {
        getExtendedCamelContext().setEventNotificationApplicable(eventNotificationApplicable);
    }

    @Override
    public void setXMLRoutesDefinitionLoader(XMLRoutesDefinitionLoader xmlRoutesDefinitionLoader) {
        getExtendedCamelContext().setXMLRoutesDefinitionLoader(xmlRoutesDefinitionLoader);
    }

    @Override
    public XMLRoutesDefinitionLoader getXMLRoutesDefinitionLoader() {
        return getExtendedCamelContext().getXMLRoutesDefinitionLoader();
    }

    @Override
    public void setRoutesLoader(RoutesLoader routesLoader) {
        getExtendedCamelContext().setRoutesLoader(routesLoader);
    }

    @Override
    public RoutesLoader getRoutesLoader() {
        return getExtendedCamelContext().getRoutesLoader();
    }

    @Override
    public ResourceLoader getResourceLoader() {
        return getExtendedCamelContext().getResourceLoader();
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        getExtendedCamelContext().setResourceLoader(resourceLoader);
    }

    @Override
    public void setModelToXMLDumper(ModelToXMLDumper modelToXMLDumper) {
        getExtendedCamelContext().setModelToXMLDumper(modelToXMLDumper);
    }

    @Override
    public ModelToXMLDumper getModelToXMLDumper() {
        return getExtendedCamelContext().getModelToXMLDumper();
    }

    @Override
    public void setRestBindingJaxbDataFormatFactory(RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory) {
        getExtendedCamelContext().setRestBindingJaxbDataFormatFactory(restBindingJaxbDataFormatFactory);
    }

    @Override
    public RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory() {
        return getExtendedCamelContext().getRestBindingJaxbDataFormatFactory();
    }

    @Override
    public RuntimeCamelCatalog getRuntimeCamelCatalog() {
        return getExtendedCamelContext().getRuntimeCamelCatalog();
    }

    @Override
    public void setRuntimeCamelCatalog(RuntimeCamelCatalog runtimeCamelCatalog) {
        getExtendedCamelContext().setRuntimeCamelCatalog(runtimeCamelCatalog);
    }

    @Override
    public ConfigurerResolver getConfigurerResolver() {
        return getExtendedCamelContext().getConfigurerResolver();
    }

    @Override
    public void setConfigurerResolver(ConfigurerResolver configurerResolver) {
        getExtendedCamelContext().setConfigurerResolver(configurerResolver);
    }

    @Override
    public UriFactoryResolver getUriFactoryResolver() {
        return getExtendedCamelContext().getUriFactoryResolver();
    }

    @Override
    public void setUriFactoryResolver(UriFactoryResolver uriFactoryResolver) {
        getExtendedCamelContext().setUriFactoryResolver(uriFactoryResolver);
    }

    @Override
    public RouteController getInternalRouteController() {
        return getExtendedCamelContext().getInternalRouteController();
    }

    @Override
    public EndpointUriFactory getEndpointUriFactory(String scheme) {
        return getExtendedCamelContext().getEndpointUriFactory(scheme);
    }

    @Override
    public void addRoute(Route route) {
        getExtendedCamelContext().addRoute(route);
    }

    @Override
    public void removeRoute(Route route) {
        getExtendedCamelContext().removeRoute(route);
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        return getExtendedCamelContext().createErrorHandler(route, processor);
    }

    @Override
    public void setLightweight(boolean lightweight) {
        getExtendedCamelContext().setLightweight(lightweight);
    }

    @Override
    public boolean isLightweight() {
        return getExtendedCamelContext().isLightweight();
    }

    @Override
    public StartupStepRecorder getStartupStepRecorder() {
        return getExtendedCamelContext().getStartupStepRecorder();
    }

    @Override
    public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {
        getExtendedCamelContext().setStartupStepRecorder(startupStepRecorder);
    }

    @Override
    public CliConnectorFactory getCliConnectorFactory() {
        return getExtendedCamelContext().getCliConnectorFactory();
    }

    @Override
    public void setCliConnectorFactory(CliConnectorFactory cliConnectorFactory) {
        getExtendedCamelContext().setCliConnectorFactory(cliConnectorFactory);
    }

    @Override
    public String getTestExcludeRoutes() {
        return getExtendedCamelContext().getTestExcludeRoutes();
    }

    //
    // CatalogCamelContext
    //

    protected CatalogCamelContext getCatalogCamelContext() {
        return delegate.adapt(CatalogCamelContext.class);
    }

    @Override
    public String getComponentParameterJsonSchema(String componentName) throws IOException {
        return getCatalogCamelContext().getComponentParameterJsonSchema(componentName);
    }

    @Override
    public String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException {
        return getCatalogCamelContext().getDataFormatParameterJsonSchema(dataFormatName);
    }

    @Override
    public String getLanguageParameterJsonSchema(String languageName) throws IOException {
        return getCatalogCamelContext().getLanguageParameterJsonSchema(languageName);
    }

    @Override
    public String getEipParameterJsonSchema(String eipName) throws IOException {
        return getCatalogCamelContext().getEipParameterJsonSchema(eipName);
    }

    //
    // ModelCamelContext
    //

    protected ModelCamelContext getModelCamelContext() {
        return delegate.adapt(ModelCamelContext.class);
    }

    @Override
    public void addModelLifecycleStrategy(ModelLifecycleStrategy modelLifecycleStrategy) {
        getModelCamelContext().addModelLifecycleStrategy(modelLifecycleStrategy);
    }

    @Override
    public List<ModelLifecycleStrategy> getModelLifecycleStrategies() {
        return getModelCamelContext().getModelLifecycleStrategies();
    }

    @Override
    public void addRouteConfiguration(RouteConfigurationDefinition routesConfiguration) {
        getModelCamelContext().addRouteConfiguration(routesConfiguration);
    }

    @Override
    public void addRouteConfigurations(List<RouteConfigurationDefinition> routesConfigurations) {
        getModelCamelContext().addRouteConfigurations(routesConfigurations);
    }

    @Override
    public List<RouteConfigurationDefinition> getRouteConfigurationDefinitions() {
        return getModelCamelContext().getRouteConfigurationDefinitions();
    }

    @Override
    public List<RouteDefinition> getRouteDefinitions() {
        return getModelCamelContext().getRouteDefinitions();
    }

    @Override
    public RouteDefinition getRouteDefinition(String id) {
        return getModelCamelContext().getRouteDefinition(id);
    }

    @Override
    public void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        getModelCamelContext().addRouteDefinitions(routeDefinitions);
    }

    @Override
    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        getModelCamelContext().addRouteDefinition(routeDefinition);
    }

    @Override
    public void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        getModelCamelContext().removeRouteDefinitions(routeDefinitions);
    }

    @Override
    public void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        getModelCamelContext().removeRouteDefinition(routeDefinition);
    }

    @Override
    public List<RouteTemplateDefinition> getRouteTemplateDefinitions() {
        return getModelCamelContext().getRouteTemplateDefinitions();
    }

    @Override
    public RouteTemplateDefinition getRouteTemplateDefinition(String id) {
        return getModelCamelContext().getRouteTemplateDefinition(id);
    }

    @Override
    public void addRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        getModelCamelContext().addRouteTemplateDefinitions(routeTemplateDefinitions);
    }

    @Override
    public void addRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        getModelCamelContext().addRouteTemplateDefinition(routeTemplateDefinition);
    }

    @Override
    public void removeRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        getModelCamelContext().removeRouteTemplateDefinitions(routeTemplateDefinitions);
    }

    @Override
    public void removeRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        getModelCamelContext().removeRouteTemplateDefinition(routeTemplateDefinition);
    }

    @Override
    public void addRouteTemplateDefinitionConverter(String templateIdPattern, RouteTemplateDefinition.Converter converter) {
        getModelCamelContext().addRouteTemplateDefinitionConverter(templateIdPattern, converter);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters)
            throws Exception {
        return getModelCamelContext().addRouteFromTemplate(routeId, routeTemplateId, parameters);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, RouteTemplateContext routeTemplateContext)
            throws Exception {
        return getModelCamelContext().addRouteFromTemplate(routeId, routeTemplateId, routeTemplateContext);
    }

    @Override
    public void addRouteFromTemplatedRoute(TemplatedRouteDefinition templatedRouteDefinition)
            throws Exception {
        getModelCamelContext().addRouteFromTemplatedRoute(templatedRouteDefinition);
    }

    @Override
    public void removeRouteTemplateDefinitions(String pattern) throws Exception {
        getModelCamelContext().removeRouteTemplateDefinitions(pattern);
    }

    @Override
    public List<RestDefinition> getRestDefinitions() {
        return getModelCamelContext().getRestDefinitions();
    }

    @Override
    public void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception {
        getModelCamelContext().addRestDefinitions(restDefinitions, addToRoutes);
    }

    @Override
    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        getModelCamelContext().setDataFormats(dataFormats);
    }

    @Override
    public Map<String, DataFormatDefinition> getDataFormats() {
        return getModelCamelContext().getDataFormats();
    }

    @Override
    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        return getModelCamelContext().resolveDataFormatDefinition(name);
    }

    @Override
    public ProcessorDefinition<?> getProcessorDefinition(String id) {
        return getModelCamelContext().getProcessorDefinition(id);
    }

    @Override
    public <T extends ProcessorDefinition<T>> T getProcessorDefinition(String id, Class<T> type) {
        return getModelCamelContext().getProcessorDefinition(id, type);
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        getModelCamelContext().setValidators(validators);
    }

    @Override
    public Resilience4jConfigurationDefinition getResilience4jConfiguration(String id) {
        return getModelCamelContext().getResilience4jConfiguration(id);
    }

    @Override
    public void setResilience4jConfiguration(Resilience4jConfigurationDefinition configuration) {
        getModelCamelContext().setResilience4jConfiguration(configuration);
    }

    @Override
    public void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> configurations) {
        getModelCamelContext().setResilience4jConfigurations(configurations);
    }

    @Override
    public void addResilience4jConfiguration(String id, Resilience4jConfigurationDefinition configuration) {
        getModelCamelContext().addResilience4jConfiguration(id, configuration);
    }

    @Override
    public FaultToleranceConfigurationDefinition getFaultToleranceConfiguration(String id) {
        return getModelCamelContext().getFaultToleranceConfiguration(id);
    }

    @Override
    public void setFaultToleranceConfiguration(FaultToleranceConfigurationDefinition configuration) {
        getModelCamelContext().setFaultToleranceConfiguration(configuration);
    }

    @Override
    public void setFaultToleranceConfigurations(List<FaultToleranceConfigurationDefinition> configurations) {
        getModelCamelContext().setFaultToleranceConfigurations(configurations);
    }

    @Override
    public void addFaultToleranceConfiguration(String id, FaultToleranceConfigurationDefinition configuration) {
        getModelCamelContext().addFaultToleranceConfiguration(id, configuration);
    }

    @Override
    public List<ValidatorDefinition> getValidators() {
        return getModelCamelContext().getValidators();
    }

    @Override
    public void setTransformers(List<TransformerDefinition> transformers) {
        getModelCamelContext().setTransformers(transformers);
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return getModelCamelContext().getTransformers();
    }

    @Override
    public ServiceCallConfigurationDefinition getServiceCallConfiguration(String serviceName) {
        return getModelCamelContext().getServiceCallConfiguration(serviceName);
    }

    @Override
    public void setServiceCallConfiguration(ServiceCallConfigurationDefinition configuration) {
        getModelCamelContext().setServiceCallConfiguration(configuration);
    }

    @Override
    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> configurations) {
        getModelCamelContext().setServiceCallConfigurations(configurations);
    }

    @Override
    public void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration) {
        getModelCamelContext().addServiceCallConfiguration(serviceName, configuration);
    }

    @Override
    public void startRouteDefinitions() throws Exception {
        getModelCamelContext().startRouteDefinitions();
    }

    @Override
    public void startRouteDefinitions(List<RouteDefinition> routeDefinitions) throws Exception {
        getModelCamelContext().startRouteDefinitions(routeDefinitions);
    }

    @Override
    public void setRouteFilterPattern(String include, String exclude) {
        getModelCamelContext().setRouteFilterPattern(include, exclude);
    }

    @Override
    public void setRouteFilter(Function<RouteDefinition, Boolean> filter) {
        getModelCamelContext().setRouteFilter(filter);
    }

    @Override
    public Function<RouteDefinition, Boolean> getRouteFilter() {
        return getModelCamelContext().getRouteFilter();
    }

    @Override
    public ModelReifierFactory getModelReifierFactory() {
        return getModelCamelContext().getModelReifierFactory();
    }

    @Override
    public PeriodTaskScheduler getPeriodTaskScheduler() {
        return getExtendedCamelContext().getPeriodTaskScheduler();
    }

    @Override
    public void setPeriodTaskScheduler(PeriodTaskScheduler periodTaskScheduler) {
        getExtendedCamelContext().setPeriodTaskScheduler(periodTaskScheduler);

    }

    @Override
    public PeriodTaskResolver getPeriodTaskResolver() {
        return getExtendedCamelContext().getPeriodTaskResolver();
    }

    @Override
    public void setPeriodTaskResolver(PeriodTaskResolver periodTaskResolver) {
        getExtendedCamelContext().setPeriodTaskResolver(periodTaskResolver);
    }

    @Override
    public void setModelReifierFactory(ModelReifierFactory modelReifierFactory) {
        getModelCamelContext().setModelReifierFactory(modelReifierFactory);
    }

    @Override
    public Expression createExpression(ExpressionDefinition definition) {
        return getModelCamelContext().createExpression(definition);
    }

    @Override
    public Predicate createPredicate(ExpressionDefinition definition) {
        return getModelCamelContext().createPredicate(definition);
    }

    @Override
    public RouteDefinition adviceWith(RouteDefinition definition, AdviceWithRouteBuilder builder) throws Exception {
        return getModelCamelContext().adviceWith(definition, builder);
    }

    @Override
    public void registerValidator(ValidatorDefinition validator) {
        getModelCamelContext().registerValidator(validator);
    }

    @Override
    public void registerTransformer(TransformerDefinition transformer) {
        getModelCamelContext().registerTransformer(transformer);
    }

    //
    // Immutable
    //

    public void init() {
        if (delegate instanceof LightweightRuntimeCamelContext) {
            return;
        }
        delegate.init();
        for (Route route : delegate.getRoutes()) {
            route.clearRouteModel();
        }
        delegate = new LightweightRuntimeCamelContext(this, delegate);
    }

    public void startImmutable() {
        delegate.start();
    }

}
