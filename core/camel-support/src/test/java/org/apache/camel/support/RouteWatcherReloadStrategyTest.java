package org.apache.camel.support;

import org.apache.camel.*;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.health.HealthCheckResolver;
import org.apache.camel.spi.*;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouteWatcherReloadStrategy}
 */
public class RouteWatcherReloadStrategyTest {

    /**
     * This used to fail on Windows because we hardcoded '/' as a file part separator
     *
     * @throws Exception if doStart fails
     */
    @Test
    public void testBasePath() throws Exception {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern("*");
        strategy.setCamelContext(new MyCamelContext());
        strategy.doStart();
        assertNotNull(strategy.getFileFilter());
        File folder = new File("./src/test/resources");
        assertTrue(folder.isDirectory());
        File[] fs = folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertEquals(1, fs.length);
        assertEquals("log4j2.properties", fs[0].getName());
    }

    /**
     * Forgetting to set the pattern caused it to be set to null, which caused a NullPointerException in the filter
     *
     * @throws Exception if doStart fails
     */
    @Test
    public void testNullPattern() throws Exception {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy("./src/test/resources");
        strategy.setPattern(null);
        strategy.setCamelContext(new MyCamelContext());
        strategy.doStart();
        assertNotNull(strategy.getFileFilter());
        File folder = new File("./src/test/resources");
        assertTrue(folder.isDirectory());
        File[] fs = folder.listFiles(strategy.getFileFilter());
        assertNotNull(fs);
        assertEquals(0, fs.length);
        // null goes back to default
        assertEquals("*.yaml,*.xml", strategy.getPattern());
    }
    static class MyExecutorServiceManager implements ExecutorServiceManager {

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void shutdown() {

        }

        @Override
        public ThreadPoolFactory getThreadPoolFactory() {
            return null;
        }

        @Override
        public void setThreadPoolFactory(ThreadPoolFactory threadPoolFactory) {

        }

        @Override
        public String resolveThreadName(String name) {
            return null;
        }

        @Override
        public ThreadPoolProfile getThreadPoolProfile(String id) {
            return null;
        }

        @Override
        public void registerThreadPoolProfile(ThreadPoolProfile profile) {

        }

        @Override
        public void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile) {

        }

        @Override
        public ThreadPoolProfile getDefaultThreadPoolProfile() {
            return null;
        }

        @Override
        public void setThreadNamePattern(String pattern) throws IllegalArgumentException {

        }

        @Override
        public String getThreadNamePattern() {
            return null;
        }

        @Override
        public void setShutdownAwaitTermination(long timeInMillis) {

        }

        @Override
        public long getShutdownAwaitTermination() {
            return 0;
        }

        @Override
        public Thread newThread(String name, Runnable runnable) {
            return null;
        }

        @Override
        public ExecutorService newDefaultThreadPool(Object source, String name) {
            return null;
        }

        @Override
        public ScheduledExecutorService newDefaultScheduledThreadPool(Object source, String name) {
            return null;
        }

        @Override
        public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
            return null;
        }

        @Override
        public ExecutorService newThreadPool(Object source, String name, String profileId) {
            return null;
        }

        @Override
        public ExecutorService newThreadPool(Object source, String name, int poolSize, int maxPoolSize) {
            return null;
        }

        @Override
        public ExecutorService newSingleThreadExecutor(Object source, String name) {
            return Executors.newSingleThreadExecutor();
        }

        @Override
        public ExecutorService newCachedThreadPool(Object source, String name) {
            return null;
        }

        @Override
        public ExecutorService newFixedThreadPool(Object source, String name, int poolSize) {
            return null;
        }

        @Override
        public ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize) {
            return null;
        }

        @Override
        public ScheduledExecutorService newSingleThreadScheduledExecutor(Object source, String name) {
            return null;
        }

        @Override
        public ScheduledExecutorService newScheduledThreadPool(Object source, String name, ThreadPoolProfile profile) {
            return null;
        }

        @Override
        public ScheduledExecutorService newScheduledThreadPool(Object source, String name, String profileId) {
            return null;
        }

        @Override
        public void shutdown(ExecutorService executorService) {

        }

        @Override
        public void shutdownGraceful(ExecutorService executorService) {

        }

        @Override
        public void shutdownGraceful(ExecutorService executorService, long shutdownAwaitTermination) {

        }

        @Override
        public List<Runnable> shutdownNow(ExecutorService executorService) {
            return null;
        }

        @Override
        public boolean awaitTermination(ExecutorService executorService, long shutdownAwaitTermination) {
            return false;
        }
    }

    static class MyCamelContext implements ExtendedCamelContext {

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
        public void addPrototypeService(Object object) {

        }

        @Override
        public boolean removeService(Object object) {
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
        public void deferStartService(Object object, boolean stopOnShutdown) {

        }

        @Override
        public void addStartupListener(StartupListener listener) {

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
        public Endpoint addEndpoint(String uri, Endpoint endpoint) {
            return null;
        }

        @Override
        public void removeEndpoint(Endpoint endpoint) {

        }

        @Override
        public Collection<Endpoint> removeEndpoints(String pattern) {
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
        public void addRoutesConfigurations(RouteConfigurationsBuilder builder) {

        }

        @Override
        public boolean removeRoute(String routeId) throws Exception {
            return false;
        }

        @Override
        public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters){
            return null;
        }

        @Override
        public String addRouteFromTemplate(String routeId, String routeTemplateId, RouteTemplateContext routeTemplateContext)
                {
            return null;
        }

        @Override
        public void removeRouteTemplates(String pattern) {

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
        public RestRegistry getRestRegistry() {
            return null;
        }

        @Override
        public void setRestRegistry(RestRegistry restRegistry) {

        }

        @Override
        public TypeConverter getTypeConverter() {
            return null;
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
            return null;
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
            return new MyExecutorServiceManager();
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
        public Boolean isModeLine() {
            return null;
        }

        @Override
        public void setModeLine(Boolean modeLine) {

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
            return Boolean.TRUE;
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
            return Boolean.FALSE;
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

        @Override
        public void setName(String name) {

        }

        @Override
        public void setRegistry(Registry registry) {

        }

        @Override
        public void setupRoutes(boolean done) {

        }

        @Override
        public boolean isSetupRoutes() {
            return false;
        }

        @Override
        public void registerEndpointCallback(EndpointStrategy strategy) {

        }

        @Override
        public Endpoint getPrototypeEndpoint(String uri) {
            return null;
        }

        @Override
        public Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri) {
            return null;
        }

        @Override
        public Endpoint hasEndpoint(NormalizedEndpointUri uri) {
            return null;
        }

        @Override
        public Endpoint getEndpoint(NormalizedEndpointUri uri) {
            return null;
        }

        @Override
        public Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters) {
            return null;
        }

        @Override
        public NormalizedEndpointUri normalizeUri(String uri) {
            return null;
        }

        @Override
        public List<RouteStartupOrder> getRouteStartupOrder() {
            return null;
        }

        @Override
        public void addBootstrap(BootstrapCloseable bootstrap) {

        }

        @Override
        public List<Service> getServices() {
            return null;
        }

        @Override
        public ExchangeFactory getExchangeFactory() {
            return null;
        }

        @Override
        public void setExchangeFactory(ExchangeFactory exchangeFactory) {

        }

        @Override
        public ExchangeFactoryManager getExchangeFactoryManager() {
            return null;
        }

        @Override
        public void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager) {

        }

        @Override
        public ProcessorExchangeFactory getProcessorExchangeFactory() {
            return null;
        }

        @Override
        public void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory) {

        }

        @Override
        public CamelBeanPostProcessor getBeanPostProcessor() {
            return null;
        }

        @Override
        public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {

        }

        @Override
        public CamelDependencyInjectionAnnotationFactory getDependencyInjectionAnnotationFactory() {
            return null;
        }

        @Override
        public void setDependencyInjectionAnnotationFactory(CamelDependencyInjectionAnnotationFactory factory) {

        }

        @Override
        public ManagementMBeanAssembler getManagementMBeanAssembler() {
            return null;
        }

        @Override
        public ErrorHandlerFactory getErrorHandlerFactory() {
            return null;
        }

        @Override
        public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {

        }

        @Override
        public NodeIdFactory getNodeIdFactory() {
            return null;
        }

        @Override
        public void setNodeIdFactory(NodeIdFactory factory) {

        }

        @Override
        public ComponentResolver getComponentResolver() {
            return null;
        }

        @Override
        public void setComponentResolver(ComponentResolver componentResolver) {

        }

        @Override
        public ComponentNameResolver getComponentNameResolver() {
            return null;
        }

        @Override
        public void setComponentNameResolver(ComponentNameResolver componentNameResolver) {

        }

        @Override
        public LanguageResolver getLanguageResolver() {
            return null;
        }

        @Override
        public void setLanguageResolver(LanguageResolver languageResolver) {

        }

        @Override
        public DataFormatResolver getDataFormatResolver() {
            return null;
        }

        @Override
        public void setDataFormatResolver(DataFormatResolver dataFormatResolver) {

        }

        @Override
        public HealthCheckResolver getHealthCheckResolver() {
            return null;
        }

        @Override
        public void setHealthCheckResolver(HealthCheckResolver healthCheckResolver) {

        }

        @Override
        public DevConsoleResolver getDevConsoleResolver() {
            return null;
        }

        @Override
        public void setDevConsoleResolver(DevConsoleResolver devConsoleResolver) {

        }

        @Override
        public PackageScanClassResolver getPackageScanClassResolver() {
            return null;
        }

        @Override
        public void setPackageScanClassResolver(PackageScanClassResolver resolver) {

        }

        @Override
        public PackageScanResourceResolver getPackageScanResourceResolver() {
            return null;
        }

        @Override
        public void setPackageScanResourceResolver(PackageScanResourceResolver resolver) {

        }

        @Override
        public FactoryFinder getDefaultFactoryFinder() {
            return null;
        }

        @Override
        public FactoryFinder getBootstrapFactoryFinder() {
            return null;
        }

        @Override
        public void setBootstrapFactoryFinder(FactoryFinder factoryFinder) {

        }

        @Override
        public FactoryFinder getBootstrapFactoryFinder(String path) {
            return null;
        }

        @Override
        public ConfigurerResolver getBootstrapConfigurerResolver() {
            return null;
        }

        @Override
        public void setBootstrapConfigurerResolver(ConfigurerResolver configurerResolver) {

        }

        @Override
        public FactoryFinder getFactoryFinder(String path) {
            return null;
        }

        @Override
        public FactoryFinderResolver getFactoryFinderResolver() {
            return null;
        }

        @Override
        public void setFactoryFinderResolver(FactoryFinderResolver resolver) {

        }

        @Override
        public ProcessorFactory getProcessorFactory() {
            return null;
        }

        @Override
        public void setProcessorFactory(ProcessorFactory processorFactory) {

        }

        @Override
        public InternalProcessorFactory getInternalProcessorFactory() {
            return null;
        }

        @Override
        public void setInternalProcessorFactory(InternalProcessorFactory internalProcessorFactory) {

        }

        @Override
        public InterceptEndpointFactory getInterceptEndpointFactory() {
            return null;
        }

        @Override
        public void setInterceptEndpointFactory(InterceptEndpointFactory interceptEndpointFactory) {

        }

        @Override
        public RouteFactory getRouteFactory() {
            return null;
        }

        @Override
        public void setRouteFactory(RouteFactory routeFactory) {

        }

        @Override
        public ModelJAXBContextFactory getModelJAXBContextFactory() {
            return null;
        }

        @Override
        public void setModelJAXBContextFactory(ModelJAXBContextFactory modelJAXBContextFactory) {

        }

        @Override
        public DeferServiceFactory getDeferServiceFactory() {
            return null;
        }

        @Override
        public void setDeferServiceFactory(DeferServiceFactory deferServiceFactory) {

        }

        @Override
        public UnitOfWorkFactory getUnitOfWorkFactory() {
            return null;
        }

        @Override
        public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {

        }

        @Override
        public AnnotationBasedProcessorFactory getAnnotationBasedProcessorFactory() {
            return null;
        }

        @Override
        public void setAnnotationBasedProcessorFactory(AnnotationBasedProcessorFactory annotationBasedProcessorFactory) {

        }

        @Override
        public BeanProxyFactory getBeanProxyFactory() {
            return null;
        }

        @Override
        public BeanProcessorFactory getBeanProcessorFactory() {
            return null;
        }

        @Override
        public ScheduledExecutorService getErrorHandlerExecutorService() {
            return null;
        }

        @Override
        public void addInterceptStrategy(InterceptStrategy interceptStrategy) {

        }

        @Override
        public List<InterceptStrategy> getInterceptStrategies() {
            return null;
        }

        @Override
        public void setupManagement(Map<String, Object> options) {

        }

        @Override
        public Set<LogListener> getLogListeners() {
            return null;
        }

        @Override
        public void addLogListener(LogListener listener) {

        }

        @Override
        public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
            return null;
        }

        @Override
        public void setAsyncProcessorAwaitManager(AsyncProcessorAwaitManager manager) {

        }

        @Override
        public BeanIntrospection getBeanIntrospection() {
            return null;
        }

        @Override
        public void setBeanIntrospection(BeanIntrospection beanIntrospection) {

        }

        @Override
        public HeadersMapFactory getHeadersMapFactory() {
            return null;
        }

        @Override
        public void setHeadersMapFactory(HeadersMapFactory factory) {

        }

        @Override
        public ReactiveExecutor getReactiveExecutor() {
            return null;
        }

        @Override
        public void setReactiveExecutor(ReactiveExecutor reactiveExecutor) {

        }

        @Override
        public boolean isEventNotificationApplicable() {
            return false;
        }

        @Override
        public void setEventNotificationApplicable(boolean eventNotificationApplicable) {

        }

        @Override
        public XMLRoutesDefinitionLoader getXMLRoutesDefinitionLoader() {
            return null;
        }

        @Override
        public void setXMLRoutesDefinitionLoader(XMLRoutesDefinitionLoader xmlRoutesDefinitionLoader) {

        }

        @Override
        public RoutesLoader getRoutesLoader() {
            return null;
        }

        @Override
        public void setRoutesLoader(RoutesLoader routesLoader) {

        }

        @Override
        public ResourceLoader getResourceLoader() {
            return null;
        }

        @Override
        public void setResourceLoader(ResourceLoader resourceLoader) {

        }

        @Override
        public ModelToXMLDumper getModelToXMLDumper() {
            return null;
        }

        @Override
        public void setModelToXMLDumper(ModelToXMLDumper modelToXMLDumper) {

        }

        @Override
        public RestBindingJaxbDataFormatFactory getRestBindingJaxbDataFormatFactory() {
            return null;
        }

        @Override
        public void setRestBindingJaxbDataFormatFactory(RestBindingJaxbDataFormatFactory restBindingJaxbDataFormatFactory) {

        }

        @Override
        public RuntimeCamelCatalog getRuntimeCamelCatalog() {
            return null;
        }

        @Override
        public void setRuntimeCamelCatalog(RuntimeCamelCatalog runtimeCamelCatalog) {

        }

        @Override
        public ConfigurerResolver getConfigurerResolver() {
            return null;
        }

        @Override
        public void setConfigurerResolver(ConfigurerResolver configurerResolver) {

        }

        @Override
        public UriFactoryResolver getUriFactoryResolver() {
            return null;
        }

        @Override
        public void setUriFactoryResolver(UriFactoryResolver uriFactoryResolver) {

        }

        @Override
        public RouteController getInternalRouteController() {
            return null;
        }

        @Override
        public EndpointUriFactory getEndpointUriFactory(String scheme) {
            return null;
        }

        @Override
        public StartupStepRecorder getStartupStepRecorder() {
            return null;
        }

        @Override
        public void setStartupStepRecorder(StartupStepRecorder startupStepRecorder) {

        }

        @Override
        public void addRoute(Route route) {

        }

        @Override
        public void removeRoute(Route route) {

        }

        @Override
        public Processor createErrorHandler(Route route, Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isLightweight() {
            return false;
        }

        @Override
        public void setLightweight(boolean lightweight) {

        }

        @Override
        public void disposeModel() {

        }

        @Override
        public String getTestExcludeRoutes() {
            return null;
        }

        @Override
        public String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional) {
            return null;
        }

        @Override
        public String getBasePackageScan() {
            return null;
        }

        @Override
        public void setBasePackageScan(String basePackageScan) {

        }

        @Override
        public ModeLineFactory getModeLineFactory() {
            return null;
        }

        @Override
        public void setModeLineFactory(ModeLineFactory modeLineFactory) {

        }
    }
}
