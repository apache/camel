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
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
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
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupListener;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.TypeConverter;
import org.apache.camel.ValueHolder;
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
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.vault.VaultConfiguration;

@Experimental
public class LightweightCamelContext implements CamelContext, CatalogCamelContext, ModelCamelContext {

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

        getCamelContextExtension().setRegistry(registry);
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
    public void addTemplatedRoutes(RoutesBuilder builder) throws Exception {
        delegate.addTemplatedRoutes(builder);
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
    public void setBacklogTracingStandby(boolean backlogTracingStandby) {
        delegate.setBacklogTracingStandby(backlogTracingStandby);
    }

    @Override
    public boolean isBacklogTracingStandby() {
        return delegate.isBacklogTracingStandby();
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

    @Override
    public ExtendedCamelContext getCamelContextExtension() {
        return delegate.getCamelContextExtension();
    }

    //
    // CatalogCamelContext
    //

    protected CatalogCamelContext getCatalogCamelContext() {
        return (CatalogCamelContext) delegate;
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
        return (ModelCamelContext) delegate;
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
    public void removeRouteConfiguration(RouteConfigurationDefinition routeConfigurationDefinition) throws Exception {
        getModelCamelContext().removeRouteConfiguration(routeConfigurationDefinition);
    }

    @Override
    public RouteConfigurationDefinition getRouteConfigurationDefinition(String id) {
        return getModelCamelContext().getRouteConfigurationDefinition(id);
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
    public String addRouteFromTemplate(String routeId, String routeTemplateId, String prefixId, Map<String, Object> parameters)
            throws Exception {
        return getModelCamelContext().addRouteFromTemplate(routeId, routeTemplateId, prefixId, parameters);
    }

    @Override
    public String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId, RouteTemplateContext routeTemplateContext)
            throws Exception {
        return getModelCamelContext().addRouteFromTemplate(routeId, routeTemplateId, prefixId, routeTemplateContext);
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
