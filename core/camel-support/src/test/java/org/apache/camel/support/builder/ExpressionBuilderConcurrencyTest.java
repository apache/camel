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
package org.apache.camel.support.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
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
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.SimpleTypeConverter;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.task.TaskTestSupport;
import org.apache.camel.vault.VaultConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ExpressionBuilderConcurrencyTest extends TaskTestSupport {

    static StringBuilder expectedResult = new StringBuilder();
    static Expression concatExpression;
    static CamelContext context;
    static List<Expression> expressions;

    @BeforeAll
    public static void init() {
        expressions = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            expressions.add(ExpressionBuilder.simpleExpression("This is a test a with startLabel: "));
            expressions.add(ExpressionBuilder.simpleExpression("`${body.get('startLabel')}` endLabel: "));
            expressions.add(ExpressionBuilder.simpleExpression("`${body.get('endLabel')}` and label: `${body.get('label')}`"));

            //Mount the expected result after call the concatExpression
            expectedResult.append("This is a test a with startLabel: ");
            expectedResult.append("`${body.get('startLabel')}` endLabel: ");
            expectedResult.append("`${body.get('endLabel')}` and label: `${body.get('label')}`");
        }

        concatExpression = ExpressionBuilder.concatExpression(expressions);
        context = new MockCamelContext();
    }

    @DisplayName("Test if concatExpression generate correct results in Parallel processing")
    @Test
    public void testConcatExpressionConcurrency() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicBoolean error = new AtomicBoolean(false);
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                Exchange exchange = new DefaultExchange(context);
                concatExpression.init(context);
                String r = concatExpression.evaluate(exchange, String.class);

                if (!error.get() && !expectedResult.toString().equals(r)) {
                    error.set(true);
                }

            });
        }

        // Waiting for all threads finish the task to check the result
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assertions.assertFalse(error.get());

    }

    static class MockCamelContext implements CamelContext {

        @Override
        public <T extends CamelContext> T adapt(Class<T> type) {
            return null;
        }

        @Override
        public <T> T getExtension(Class<T> type) {
            return null;
        }

        @Override
        public <T> void setExtension(Class<T> type, T module) {

        }

        @Override
        public boolean isVetoStarted() {
            return false;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public CamelContextNameStrategy getNameStrategy() {
            return null;
        }

        @Override
        public void setNameStrategy(CamelContextNameStrategy nameStrategy) {

        }

        @Override
        public ManagementNameStrategy getManagementNameStrategy() {
            return null;
        }

        @Override
        public void setManagementNameStrategy(ManagementNameStrategy nameStrategy) {

        }

        @Override
        public String getManagementName() {
            return null;
        }

        @Override
        public void setManagementName(String name) {

        }

        @Override
        public String getVersion() {
            return null;
        }

        @Override
        public String getUptime() {
            return null;
        }

        @Override
        public long getUptimeMillis() {
            return 0;
        }

        @Override
        public Date getStartDate() {
            return null;
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
        public void addComponent(String componentName, Component component) {

        }

        @Override
        public Component hasComponent(String componentName) {
            return null;
        }

        @Override
        public Component getComponent(String componentName) {
            return null;
        }

        @Override
        public Component getComponent(String name, boolean autoCreateComponents) {
            return null;
        }

        @Override
        public Component getComponent(String name, boolean autoCreateComponents, boolean autoStart) {
            return null;
        }

        @Override
        public <T extends Component> T getComponent(String name, Class<T> componentType) {
            return null;
        }

        @Override
        public Set<String> getComponentNames() {
            return null;
        }

        @Override
        public Component removeComponent(String componentName) {
            return null;
        }

        @Override
        public EndpointRegistry<? extends ValueHolder<String>> getEndpointRegistry() {
            return null;
        }

        @Override
        public Endpoint getEndpoint(String uri) {
            return null;
        }

        @Override
        public Endpoint getEndpoint(String uri, Map<String, Object> parameters) {
            return null;
        }

        @Override
        public <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType) {
            return null;
        }

        @Override
        public Collection<Endpoint> getEndpoints() {
            return null;
        }

        @Override
        public Map<String, Endpoint> getEndpointMap() {
            return null;
        }

        @Override
        public Endpoint hasEndpoint(String uri) {
            return null;
        }

        @Override
        public Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception {
            return null;
        }

        @Override
        public void removeEndpoint(Endpoint endpoint) throws Exception {

        }

        @Override
        public Collection<Endpoint> removeEndpoints(String pattern) throws Exception {
            return null;
        }

        @Override
        public GlobalEndpointConfiguration getGlobalEndpointConfiguration() {
            return null;
        }

        @Override
        public void setRouteController(RouteController routeController) {

        }

        @Override
        public RouteController getRouteController() {
            return null;
        }

        @Override
        public List<Route> getRoutes() {
            return null;
        }

        @Override
        public int getRoutesSize() {
            return 0;
        }

        @Override
        public Route getRoute(String id) {
            return null;
        }

        @Override
        public Processor getProcessor(String id) {
            return null;
        }

        @Override
        public <T extends Processor> T getProcessor(String id, Class<T> type) {
            return null;
        }

        @Override
        public void addRoutes(RoutesBuilder builder) throws Exception {

        }

        @Override
        public void addTemplatedRoutes(RoutesBuilder builder) throws Exception {

        }

        @Override
        public void addRoutesConfigurations(RouteConfigurationsBuilder builder) throws Exception {

        }

        @Override
        public boolean removeRoute(String routeId) throws Exception {
            return false;
        }

        @Override
        public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters)
                throws Exception {
            return null;
        }

        @Override
        public String addRouteFromTemplate(
                String routeId, String routeTemplateId, String prefixId, Map<String, Object> parameters)
                throws Exception {
            return null;
        }

        @Override
        public String addRouteFromTemplate(String routeId, String routeTemplateId, RouteTemplateContext routeTemplateContext)
                throws Exception {
            return null;
        }

        @Override
        public String addRouteFromTemplate(
                String routeId, String routeTemplateId, String prefixId, RouteTemplateContext routeTemplateContext)
                throws Exception {
            return null;
        }

        @Override
        public void removeRouteTemplates(String pattern) throws Exception {

        }

        @Override
        public void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory) {

        }

        @Override
        public List<RoutePolicyFactory> getRoutePolicyFactories() {
            return null;
        }

        @Override
        public void setRestConfiguration(RestConfiguration restConfiguration) {

        }

        @Override
        public RestConfiguration getRestConfiguration() {
            return null;
        }

        @Override
        public void setVaultConfiguration(VaultConfiguration vaultConfiguration) {

        }

        @Override
        public VaultConfiguration getVaultConfiguration() {
            return null;
        }

        @Override
        public RestRegistry getRestRegistry() {
            return null;
        }

        @Override
        public void setRestRegistry(RestRegistry restRegistry) {

        }

        @Override
        public TypeConverter getTypeConverter() {
            TypeConverter converter = new SimpleTypeConverter(true, (type, exchange, value) -> {
                return value;
            });
            return converter;
        }

        @Override
        public TypeConverterRegistry getTypeConverterRegistry() {
            return null;
        }

        @Override
        public void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry) {

        }

        @Override
        public Registry getRegistry() {
            return null;
        }

        @Override
        public <T> T getRegistry(Class<T> type) {
            return null;
        }

        @Override
        public Injector getInjector() {
            return null;
        }

        @Override
        public void setInjector(Injector injector) {

        }

        @Override
        public List<LifecycleStrategy> getLifecycleStrategies() {
            return null;
        }

        @Override
        public void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy) {

        }

        @Override
        public Language resolveLanguage(String language) throws NoSuchLanguageException {
            return new LanguageSupport() {
                @Override
                public Predicate createPredicate(String expression) {
                    return null;
                }

                @Override
                public Expression createExpression(String expression) {
                    return new Expression() {
                        @Override
                        public <T> T evaluate(Exchange exchange, Class<T> type) {
                            return (T) expression;
                        }
                    };
                }
            };
        }

        @Override
        public String resolvePropertyPlaceholders(String text) {
            return null;
        }

        @Override
        public PropertiesComponent getPropertiesComponent() {
            return null;
        }

        @Override
        public void setPropertiesComponent(PropertiesComponent propertiesComponent) {

        }

        @Override
        public Set<String> getLanguageNames() {
            return null;
        }

        @Override
        public ProducerTemplate createProducerTemplate() {
            return null;
        }

        @Override
        public ProducerTemplate createProducerTemplate(int maximumCacheSize) {
            return null;
        }

        @Override
        public FluentProducerTemplate createFluentProducerTemplate() {
            return null;
        }

        @Override
        public FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize) {
            return null;
        }

        @Override
        public ConsumerTemplate createConsumerTemplate() {
            return null;
        }

        @Override
        public ConsumerTemplate createConsumerTemplate(int maximumCacheSize) {
            return null;
        }

        @Override
        public DataFormat resolveDataFormat(String name) {
            return null;
        }

        @Override
        public DataFormat createDataFormat(String name) {
            return null;
        }

        @Override
        public Set<String> getDataFormatNames() {
            return null;
        }

        @Override
        public Transformer resolveTransformer(String model) {
            return null;
        }

        @Override
        public Transformer resolveTransformer(DataType from, DataType to) {
            return null;
        }

        @Override
        public TransformerRegistry getTransformerRegistry() {
            return null;
        }

        @Override
        public Validator resolveValidator(DataType type) {
            return null;
        }

        @Override
        public ValidatorRegistry getValidatorRegistry() {
            return null;
        }

        @Override
        public void setGlobalOptions(Map<String, String> globalOptions) {

        }

        @Override
        public Map<String, String> getGlobalOptions() {
            return null;
        }

        @Override
        public String getGlobalOption(String key) {
            return null;
        }

        @Override
        public ClassResolver getClassResolver() {
            return null;
        }

        @Override
        public void setClassResolver(ClassResolver resolver) {

        }

        @Override
        public ManagementStrategy getManagementStrategy() {
            return null;
        }

        @Override
        public void setManagementStrategy(ManagementStrategy strategy) {

        }

        @Override
        public void disableJMX() throws IllegalStateException {

        }

        @Override
        public InflightRepository getInflightRepository() {
            return null;
        }

        @Override
        public void setInflightRepository(InflightRepository repository) {

        }

        @Override
        public ClassLoader getApplicationContextClassLoader() {
            return null;
        }

        @Override
        public void setApplicationContextClassLoader(ClassLoader classLoader) {

        }

        @Override
        public ShutdownStrategy getShutdownStrategy() {
            return null;
        }

        @Override
        public void setShutdownStrategy(ShutdownStrategy shutdownStrategy) {

        }

        @Override
        public ExecutorServiceManager getExecutorServiceManager() {
            return null;
        }

        @Override
        public void setExecutorServiceManager(ExecutorServiceManager executorServiceManager) {

        }

        @Override
        public MessageHistoryFactory getMessageHistoryFactory() {
            return null;
        }

        @Override
        public void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory) {

        }

        @Override
        public Debugger getDebugger() {
            return null;
        }

        @Override
        public void setDebugger(Debugger debugger) {

        }

        @Override
        public Tracer getTracer() {
            return null;
        }

        @Override
        public void setTracer(Tracer tracer) {

        }

        @Override
        public void setTracingStandby(boolean tracingStandby) {

        }

        @Override
        public boolean isTracingStandby() {
            return false;
        }

        @Override
        public void setBacklogTracingStandby(boolean backlogTracingStandby) {

        }

        @Override
        public boolean isBacklogTracingStandby() {
            return false;
        }

        @Override
        public UuidGenerator getUuidGenerator() {
            return null;
        }

        @Override
        public void setUuidGenerator(UuidGenerator uuidGenerator) {

        }

        @Override
        public Boolean isLoadTypeConverters() {
            return null;
        }

        @Override
        public void setLoadTypeConverters(Boolean loadTypeConverters) {

        }

        @Override
        public Boolean isLoadHealthChecks() {
            return null;
        }

        @Override
        public void setLoadHealthChecks(Boolean loadHealthChecks) {

        }

        @Override
        public Boolean isSourceLocationEnabled() {
            return null;
        }

        @Override
        public void setSourceLocationEnabled(Boolean sourceLocationEnabled) {

        }

        @Override
        public Boolean isModeline() {
            return null;
        }

        @Override
        public void setModeline(Boolean modeline) {

        }

        @Override
        public Boolean isDevConsole() {
            return null;
        }

        @Override
        public void setDevConsole(Boolean loadDevConsoles) {

        }

        @Override
        public Boolean isTypeConverterStatisticsEnabled() {
            return null;
        }

        @Override
        public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {

        }

        @Override
        public Boolean isUseMDCLogging() {
            return null;
        }

        @Override
        public void setUseMDCLogging(Boolean useMDCLogging) {

        }

        @Override
        public String getMDCLoggingKeysPattern() {
            return null;
        }

        @Override
        public void setMDCLoggingKeysPattern(String pattern) {

        }

        @Override
        public String getTracingLoggingFormat() {
            return null;
        }

        @Override
        public void setTracingLoggingFormat(String format) {

        }

        @Override
        public Boolean isDumpRoutes() {
            return null;
        }

        @Override
        public void setDumpRoutes(Boolean dumpRoutes) {

        }

        @Override
        public Boolean isUseDataType() {
            return null;
        }

        @Override
        public void setUseDataType(Boolean useDataType) {

        }

        @Override
        public Boolean isUseBreadcrumb() {
            return null;
        }

        @Override
        public void setUseBreadcrumb(Boolean useBreadcrumb) {

        }

        @Override
        public StreamCachingStrategy getStreamCachingStrategy() {
            return null;
        }

        @Override
        public void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy) {

        }

        @Override
        public RuntimeEndpointRegistry getRuntimeEndpointRegistry() {
            return null;
        }

        @Override
        public void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry) {

        }

        @Override
        public void setSSLContextParameters(SSLContextParameters sslContextParameters) {

        }

        @Override
        public SSLContextParameters getSSLContextParameters() {
            return null;
        }

        @Override
        public void setStartupSummaryLevel(StartupSummaryLevel startupSummaryLevel) {

        }

        @Override
        public StartupSummaryLevel getStartupSummaryLevel() {
            return null;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public boolean isStarted() {
            return false;
        }

        @Override
        public boolean isStarting() {
            return false;
        }

        @Override
        public boolean isStopping() {
            return false;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public boolean isSuspending() {
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
        public void build() {

        }

        @Override
        public void init() {

        }

        @Override
        public void suspend() {

        }

        @Override
        public void resume() {

        }

        @Override
        public void shutdown() {

        }

        @Override
        public void close() throws Exception {

        }

        @Override
        public ServiceStatus getStatus() {
            return null;
        }

        @Override
        public void setStreamCaching(Boolean cache) {

        }

        @Override
        public Boolean isStreamCaching() {
            return null;
        }

        @Override
        public void setTracing(Boolean tracing) {

        }

        @Override
        public Boolean isTracing() {
            return null;
        }

        @Override
        public String getTracingPattern() {
            return null;
        }

        @Override
        public void setTracingPattern(String tracePattern) {

        }

        @Override
        public void setBacklogTracing(Boolean backlogTrace) {

        }

        @Override
        public Boolean isBacklogTracing() {
            return null;
        }

        @Override
        public void setDebugging(Boolean debugging) {

        }

        @Override
        public Boolean isDebugging() {
            return null;
        }

        @Override
        public void setMessageHistory(Boolean messageHistory) {

        }

        @Override
        public Boolean isMessageHistory() {
            return null;
        }

        @Override
        public void setLogMask(Boolean logMask) {

        }

        @Override
        public Boolean isLogMask() {
            return null;
        }

        @Override
        public void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody) {

        }

        @Override
        public Boolean isLogExhaustedMessageBody() {
            return null;
        }

        @Override
        public void setDelayer(Long delay) {

        }

        @Override
        public Long getDelayer() {
            return null;
        }

        @Override
        public void setAutoStartup(Boolean autoStartup) {

        }

        @Override
        public Boolean isAutoStartup() {
            return null;
        }

        @Override
        public void setShutdownRoute(ShutdownRoute shutdownRoute) {

        }

        @Override
        public ShutdownRoute getShutdownRoute() {
            return null;
        }

        @Override
        public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {

        }

        @Override
        public ShutdownRunningTask getShutdownRunningTask() {
            return null;
        }

        @Override
        public void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage) {

        }

        @Override
        public Boolean isAllowUseOriginalMessage() {
            return null;
        }

        @Override
        public Boolean isCaseInsensitiveHeaders() {
            return null;
        }

        @Override
        public void setCaseInsensitiveHeaders(Boolean caseInsensitiveHeaders) {

        }

        @Override
        public Boolean isAutowiredEnabled() {
            return null;
        }

        @Override
        public void setAutowiredEnabled(Boolean autowiredEnabled) {

        }
    }

}
