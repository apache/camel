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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Component;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Experimental;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.GlobalEndpointConfiguration;
import org.apache.camel.IsSingleton;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupListener;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.TypeConverter;
import org.apache.camel.ValueHolder;
import org.apache.camel.impl.converter.CoreTypeConverterRegistry;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.impl.engine.DefaultDataFormatResolver;
import org.apache.camel.impl.engine.DefaultLanguageResolver;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
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
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.NormalizedUri;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Experimental
public class LightweightRuntimeCamelContext implements CamelContext, CatalogCamelContext {

    private static final Logger LOG = LoggerFactory.getLogger(LightweightRuntimeCamelContext.class);

    private final CamelContext reference;
    private final Registry registry;
    private final CoreTypeConverterRegistry typeConverter;
    private final UuidGenerator uuidGenerator;
    private final EndpointRegistry<? extends ValueHolder<String>> endpoints;
    private final Map<String, Component> components;
    private final Map<String, Language> languages;
    private final PropertiesComponent propertiesComponent;
    private final ExecutorServiceManager executorServiceManager;
    private final ShutdownStrategy shutdownStrategy;
    private final ClassLoader applicationContextClassLoader;
    private final RouteController routeController;
    private final InflightRepository inflightRepository;
    private final Injector injector;
    private final ClassResolver classResolver;
    private final Map<String, String> globalOptions;
    private final String name;
    private final boolean useDataType;
    private final boolean useBreadcrumb;
    private final boolean dumpRoutes;
    private final String mdcLoggingKeysPattern;
    private final boolean useMDCLogging;
    private final List<Route> routes;
    private final boolean messageHistory;
    private final boolean allowUseOriginalMessage;
    private final boolean logExhaustedMessageBody;
    private final String version;
    private final LightweightCamelContextExtension lwCamelContextExtension = new LightweightCamelContextExtension(this);
    private Date startDate;
    private StartupSummaryLevel startupSummaryLevel;

    LightweightRuntimeCamelContext(CamelContext reference, CamelContext context) {
        this.reference = reference;
        registry = context.getRegistry();
        typeConverter = new CoreTypeConverterRegistry(context.getTypeConverterRegistry());
        routes = Collections.unmodifiableList(context.getRoutes());
        uuidGenerator = context.getUuidGenerator();
        endpoints = context.getEndpointRegistry();
        components = context.getComponentNames().stream().collect(Collectors.toMap(s -> s, context::hasComponent));
        languages = context.getLanguageNames().stream().collect(Collectors.toMap(s -> s, context::resolveLanguage));
        propertiesComponent = context.getPropertiesComponent();
        executorServiceManager = context.getExecutorServiceManager();
        shutdownStrategy = context.getShutdownStrategy();
        applicationContextClassLoader = context.getApplicationContextClassLoader();
        routeController = context.getRouteController();
        inflightRepository = context.getInflightRepository();
        globalOptions = context.getGlobalOptions();
        injector = context.getInjector();
        classResolver = context.getClassResolver();
        name = context.getName();
        useDataType = context.isUseDataType();
        useBreadcrumb = context.isUseBreadcrumb();
        dumpRoutes = context.isDumpRoutes();
        mdcLoggingKeysPattern = context.getMDCLoggingKeysPattern();
        useMDCLogging = context.isUseMDCLogging();
        messageHistory = context.isMessageHistory();
        allowUseOriginalMessage = context.isAllowUseOriginalMessage();
        logExhaustedMessageBody = context.isLogExhaustedMessageBody();
        version = context.getVersion();
        startupSummaryLevel = context.getStartupSummaryLevel();
    }

    //
    // Lifecycle
    //
    @Override
    public boolean isStarted() {
        return false;
    }

    @Override
    public boolean isStarting() {
        return false;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public boolean isStopping() {
        return false;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isRunAllowed() {
        return false;
    }

    @Override
    public boolean isSuspending() {
        return false;
    }

    @Override
    public void build() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void init() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void suspend() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resume() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        startDate = new Date();
        LOG.info("Apache Camel {} ({}) is starting", getVersion(), lwCamelContextExtension.getName());
        for (Route route : routes) {
            route.getConsumer().start();
        }
        if (LOG.isInfoEnabled()) {
            long l = System.currentTimeMillis() - startDate.getTime();
            LOG.info("Apache Camel {} ({}) {} routes started in {}",
                    getVersion(), lwCamelContextExtension.getName(), routes.size(), TimeUtils.printDuration(l, true));
        }
    }

    @Override
    public void stop() {
        for (Route route : routes) {
            route.getConsumer().stop();
        }
    }

    @Override
    public void shutdown() {
    }

    //
    // RuntimeConfig
    //

    @Override
    public void close() throws IOException {
        stop();
    }

    @Override
    public void setStreamCaching(Boolean cache) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isStreamCaching() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isTracing() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTracingPattern() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracingPattern(String tracePattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTracingLoggingFormat() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracingLoggingFormat(String format) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isBacklogTracing() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isDebugging() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isMessageHistory() {
        return messageHistory;
    }

    @Override
    public Boolean isLogMask() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    @Override
    public Long getDelayer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDelayer(Long delay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isAutoStartup() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        throw new UnsupportedOperationException();
    }

    //
    // Model
    //

    @Override
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    @Override
    public Boolean isCaseInsensitiveHeaders() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCaseInsensitiveHeaders(Boolean caseInsensitiveHeaders) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isAutowiredEnabled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAutowiredEnabled(Boolean autowiredEnabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeConverterRegistry getTypeConverterRegistry() {
        return typeConverter;
    }

    @Override
    public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Registry getRegistry() {
        return null;
    }

    @Override
    public UuidGenerator getUuidGenerator() {
        return uuidGenerator;
    }

    @Override
    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExtendedCamelContext getCamelContextExtension() {
        return lwCamelContextExtension;
    }

    @Override
    public boolean isVetoStarted() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return lwCamelContextExtension.getDescription();
    }

    @Override
    public CamelContextNameStrategy getNameStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNameStrategy(CamelContextNameStrategy nameStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagementNameStrategy getManagementNameStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setManagementNameStrategy(ManagementNameStrategy nameStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getManagementName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setManagementName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public ServiceStatus getStatus() {
        return ServiceStatus.Started;
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
        if (startDate == null) {
            return 0;
        }
        return new Date().getTime() - startDate.getTime();
    }

    @Override
    public Date getStartDate() {
        return startDate;
    }

    @Override
    public void addService(Object object) throws Exception {
    }

    @Override
    public void addService(Object object, boolean stopOnShutdown) throws Exception {

    }

    @Override
    public void addService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception {

    }

    @Override
    public void addPrototypeService(Object object) throws Exception {

    }

    @Override
    public boolean removeService(Object object) throws Exception {
        return false;
    }

    @Override
    public boolean hasService(Object object) {
        return false;
    }

    @Override
    public <T> T hasService(Class<T> type) {
        return null;
    }

    @Override
    public <T> Set<T> hasServices(Class<T> type) {
        return null;
    }

    @Override
    public void deferStartService(Object object, boolean stopOnShutdown) throws Exception {
    }

    @Override
    public void addStartupListener(StartupListener listener) throws Exception {
    }

    @Override
    public Component hasComponent(String componentName) {
        return components.get(componentName);
    }

    @Override
    public Component getComponent(String componentName) {
        return getComponent(name, true, true);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents) {
        return getComponent(name, autoCreateComponents, true);
    }

    @Override
    public Component getComponent(String name, boolean autoCreateComponents, boolean autoStart) {
        return components.get(name);
    }

    @Override
    public <T extends Component> T getComponent(String name, Class<T> componentType) {
        return componentType.cast(hasComponent(name));
    }

    @Override
    public Set<String> getComponentNames() {
        return Collections.unmodifiableSet(components.keySet());
    }

    @Override
    public EndpointRegistry<? extends ValueHolder<String>> getEndpointRegistry() {
        return endpoints;
    }

    @Override
    public Endpoint getEndpoint(String uri) {
        return lwCamelContextExtension.doGetEndpoint(uri, false, false);
    }

    @Override
    public Endpoint getEndpoint(String uri, Map<String, Object> parameters) {
        return lwCamelContextExtension.doGetEndpoint(uri, parameters, false);
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

    @Override
    public Collection<Endpoint> getEndpoints() {
        return new ArrayList<>(endpoints.values());
    }

    @Override
    public Endpoint hasEndpoint(String uri) {
        return endpoints.get(NormalizedUri.newNormalizedUri(uri, false));
    }

    @Override
    public GlobalEndpointConfiguration getGlobalEndpointConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RouteController getRouteController() {
        return routeController;
    }

    @Override
    public void setRouteController(RouteController routeController) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Route> getRoutes() {
        return routes;
    }

    @Override
    public int getRoutesSize() {
        return routes.size();
    }

    @Override
    public Route getRoute(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Processor getProcessor(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Processor> T getProcessor(String id, Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RoutePolicyFactory> getRoutePolicyFactories() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RestConfiguration getRestConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRestConfiguration(RestConfiguration restConfiguration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setVaultConfiguration(VaultConfiguration vaultConfiguration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VaultConfiguration getVaultConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RestRegistry getRestRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRestRegistry(RestRegistry restRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeConverter getTypeConverter() {
        return typeConverter;
    }

    @Override
    public <T> T getRegistry(Class<T> type) {
        return type.cast(registry);
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Override
    public void setInjector(Injector injector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<LifecycleStrategy> getLifecycleStrategies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Language resolveLanguage(String language) throws NoSuchLanguageException {
        Language answer;
        synchronized (languages) {
            answer = languages.get(language);
            // check if the language is singleton, if so return the shared
            // instance
            if (answer instanceof IsSingleton) {
                boolean singleton = ((IsSingleton) answer).isSingleton();
                if (singleton) {
                    return answer;
                }
            }
            // language not known or not singleton, then use resolver
            answer = PluginHelper.getLanguageResolver(lwCamelContextExtension).resolveLanguage(language, reference);
            // inject CamelContext if aware
            if (answer != null) {
                CamelContextAware.trySetCamelContext(answer, reference);
                if (answer instanceof Service) {
                    try {
                        startService((Service) answer);
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                }
                languages.put(language, answer);
            }
        }
        return answer;
    }

    @Override
    public String resolvePropertyPlaceholders(String text) {
        return lwCamelContextExtension.resolvePropertyPlaceholders(text, false);
    }

    @Override
    public PropertiesComponent getPropertiesComponent() {
        return propertiesComponent;
    }

    @Override
    public void setPropertiesComponent(PropertiesComponent propertiesComponent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getLanguageNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProducerTemplate createProducerTemplate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConsumerTemplate createConsumerTemplate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataFormat resolveDataFormat(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataFormat createDataFormat(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getDataFormatNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Transformer resolveTransformer(String model) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Transformer resolveTransformer(DataType from, DataType to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TransformerRegistry getTransformerRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Validator resolveValidator(DataType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ValidatorRegistry getValidatorRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getGlobalOptions() {
        return globalOptions;
    }

    @Override
    public void setGlobalOptions(Map<String, String> globalOptions) {
        throw new UnsupportedOperationException();
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
    public ClassResolver getClassResolver() {
        return classResolver;
    }

    @Override
    public void setClassResolver(ClassResolver resolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ManagementStrategy getManagementStrategy() {
        return null;
    }

    @Override
    public void setManagementStrategy(ManagementStrategy strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableJMX() throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InflightRepository getInflightRepository() {
        return inflightRepository;
    }

    @Override
    public void setInflightRepository(InflightRepository repository) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracingStandby(boolean tracingStandby) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTracingStandby() {
        return false;
    }

    @Override
    public void setBacklogTracingStandby(boolean backlogTracingStandby) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBacklogTracingStandby() {
        return false;
    }

    //
    // ExtendedCamelContext
    //

    @Override
    public ClassLoader getApplicationContextClassLoader() {
        return applicationContextClassLoader;
    }

    @Override
    public void setApplicationContextClassLoader(ClassLoader classLoader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShutdownStrategy getShutdownStrategy() {
        return shutdownStrategy;
    }

    @Override
    public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExecutorServiceManager getExecutorServiceManager() {
        return executorServiceManager;
    }

    @Override
    public void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MessageHistoryFactory getMessageHistoryFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Debugger getDebugger() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDebugger(Debugger debugger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tracer getTracer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracer(Tracer tracer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isLoadTypeConverters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isLoadHealthChecks() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isDevConsole() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDevConsole(Boolean loadDevConsoles) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isModeline() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModeline(Boolean modeline) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLoadHealthChecks(Boolean loadHealthChecks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isSourceLocationEnabled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSourceLocationEnabled(Boolean sourceLocationEnabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isTypeConverterStatisticsEnabled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isDumpRoutes() {
        return dumpRoutes;
    }

    @Override
    public void setDumpRoutes(Boolean dumpRoutes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isUseMDCLogging() {
        return useMDCLogging;
    }

    @Override
    public void setUseMDCLogging(Boolean useMDCLogging) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMDCLoggingKeysPattern() {
        return mdcLoggingKeysPattern;
    }

    @Override
    public void setMDCLoggingKeysPattern(String pattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isUseDataType() {
        return useDataType;
    }

    @Override
    public void setUseDataType(Boolean useDataType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isUseBreadcrumb() {
        return useBreadcrumb;
    }

    @Override
    public void setUseBreadcrumb(Boolean useBreadcrumb) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamCachingStrategy getStreamCachingStrategy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLContextParameters getSSLContextParameters() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSSLContextParameters(SSLContextParameters sslContextParameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStartupSummaryLevel(StartupSummaryLevel startupSummaryLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StartupSummaryLevel getStartupSummaryLevel() {
        return startupSummaryLevel;
    }

    //
    // Unsupported mutable methods
    //

    @Override
    public String getComponentParameterJsonSchema(String componentName) throws IOException {
        Class<?> clazz;
        Object instance = lwCamelContextExtension.getRegistry().lookupByNameAndType(componentName, Component.class);
        if (instance != null) {
            clazz = instance.getClass();
        } else {
            clazz = lwCamelContextExtension.getFactoryFinder(DefaultComponentResolver.RESOURCE_PATH).findClass(componentName)
                    .orElse(null);
            if (clazz == null) {
                instance = hasComponent(componentName);
                if (instance != null) {
                    clazz = instance.getClass();
                } else {
                    return null;
                }
            }
        }

        return getJsonSchema(clazz.getPackage().getName(), componentName);
    }

    @Override
    public String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException {
        Class<?> clazz;
        Object instance = lwCamelContextExtension.getRegistry().lookupByNameAndType(dataFormatName, DataFormat.class);
        if (instance != null) {
            clazz = instance.getClass();
        } else {
            clazz = lwCamelContextExtension.getFactoryFinder(DefaultDataFormatResolver.DATAFORMAT_RESOURCE_PATH)
                    .findClass(dataFormatName).orElse(null);
            if (clazz == null) {
                return null;
            }
        }
        return getJsonSchema(clazz.getPackage().getName(), dataFormatName);
    }

    @Override
    public String getLanguageParameterJsonSchema(String languageName) throws IOException {
        Class<?> clazz;
        Object instance = lwCamelContextExtension.getRegistry().lookupByNameAndType(languageName, Language.class);
        if (instance != null) {
            clazz = instance.getClass();
        } else {
            clazz = lwCamelContextExtension.getFactoryFinder(DefaultLanguageResolver.LANGUAGE_RESOURCE_PATH)
                    .findClass(languageName).orElse(null);
            if (clazz == null) {
                return null;
            }
        }
        return getJsonSchema(clazz.getPackage().getName(), languageName);
    }

    @Override
    public String getEipParameterJsonSchema(String eipName) throws IOException {
        // the eip json schema may be in some of the sub-packages so look until
        // we find it
        String[] subPackages = new String[] { "", "/config", "/dataformat", "/language", "/loadbalancer", "/rest" };
        for (String sub : subPackages) {
            String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + "/" + eipName + ".json";
            InputStream inputStream = getClassResolver().loadResourceAsStream(path);
            if (inputStream != null) {
                try {
                    return IOHelper.loadText(inputStream);
                } finally {
                    IOHelper.close(inputStream);
                }
            }
        }
        return null;
    }

    private String getJsonSchema(String packageName, String name) throws IOException {
        String path = packageName.replace('.', '/') + "/" + name + ".json";
        InputStream inputStream = getClassResolver().loadResourceAsStream(path);
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
    public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEndpoint(Endpoint endpoint) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Endpoint> removeEndpoints(String pattern) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addComponent(String componentName, Component component) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Component removeComponent(String componentName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRoutes(RoutesBuilder builder) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addTemplatedRoutes(RoutesBuilder builder) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRoutesConfigurations(RouteConfigurationsBuilder builder) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeRoute(String routeId) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTracing(Boolean tracing) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBacklogTracing(Boolean backlogTrace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDebugging(Boolean debugging) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMessageHistory(Boolean messageHistory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogMask(Boolean logMask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAutoStartup(Boolean autoStartup) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, String prefixId, Map<String, Object> parameters)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId, RouteTemplateContext routeTemplateContext)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeRouteTemplates(String pattern) throws Exception {
        throw new UnsupportedOperationException();
    }

    private void startService(Service service) throws Exception {
        // and register startup aware so they can be notified when
        // camel context has been started
        if (service instanceof StartupListener) {
            StartupListener listener = (StartupListener) service;
            addStartupListener(listener);
        }
        CamelContextAware.trySetCamelContext(service, reference);
        service.start();
    }

    public ExtendedCamelContext getLwCamelContextExtension() {
        return lwCamelContextExtension;
    }

}
