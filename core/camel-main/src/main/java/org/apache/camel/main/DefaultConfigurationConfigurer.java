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
package org.apache.camel.main;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.TypeConverters;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.impl.engine.PrototypeExchangeFactory;
import org.apache.camel.impl.engine.PrototypeProcessorExchangeFactory;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.ClassicUuidGenerator;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.support.OffUuidGenerator;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.apache.camel.support.ShortUuidGenerator;
import org.apache.camel.support.SimpleUuidGenerator;
import org.apache.camel.support.jsse.GlobalSSLContextParametersSupplier;
import org.apache.camel.support.startup.LoggingStartupStepRecorder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.vault.AwsVaultConfiguration;
import org.apache.camel.vault.AzureVaultConfiguration;
import org.apache.camel.vault.GcpVaultConfiguration;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.apache.camel.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To configure the {@link DefaultConfigurationProperties} on {@link org.apache.camel.CamelContext} used by Camel Main,
 * Camel Spring Boot and other runtimes.
 */
public final class DefaultConfigurationConfigurer {

    public static final Logger LOG = LoggerFactory.getLogger(DefaultConfigurationConfigurer.class);

    private DefaultConfigurationConfigurer() {
    }

    /**
     * Configures the {@link CamelContext} with the configuration.
     *
     * @param camelContext the camel context
     * @param config       the configuration
     */
    public static void configure(CamelContext camelContext, DefaultConfigurationProperties<?> config) throws Exception {
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        if (config.getStartupRecorder() != null) {
            if ("false".equals(config.getStartupRecorder())) {
                ecc.getStartupStepRecorder().setEnabled(false);
            } else if ("logging".equals(config.getStartupRecorder())) {
                if (!(ecc.getStartupStepRecorder() instanceof LoggingStartupStepRecorder)) {
                    ecc.setStartupStepRecorder(new LoggingStartupStepRecorder());
                }
            } else if ("java-flight-recorder".equals(config.getStartupRecorder())) {
                if (!ecc.getStartupStepRecorder().getClass().getName().startsWith("org.apache.camel.startup.jfr")) {
                    throw new IllegalArgumentException(
                            "Cannot find Camel Java Flight Recorder on classpath. Add camel-jfr to classpath.");
                }
            }
        }
        ecc.getStartupStepRecorder().setMaxDepth(config.getStartupRecorderMaxDepth());
        ecc.getStartupStepRecorder().setRecording(config.isStartupRecorderRecording());
        ecc.getStartupStepRecorder().setStartupRecorderDuration(config.getStartupRecorderDuration());
        ecc.getStartupStepRecorder().setRecordingDir(config.getStartupRecorderDir());
        ecc.getStartupStepRecorder().setRecordingProfile(config.getStartupRecorderProfile());

        PluginHelper.getBeanPostProcessor(ecc).setEnabled(config.isBeanPostProcessorEnabled());
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(ecc);
        beanIntrospection.setExtendedStatistics(config.isBeanIntrospectionExtendedStatistics());
        if (config.getBeanIntrospectionLoggingLevel() != null) {
            beanIntrospection.setLoggingLevel(config.getBeanIntrospectionLoggingLevel());
        }
        beanIntrospection.afterPropertiesConfigured(camelContext);

        if ("pooled".equals(config.getExchangeFactory())) {
            ecc.setExchangeFactory(new PooledExchangeFactory());
            ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        } else if ("prototype".equals(config.getExchangeFactory())) {
            ecc.setExchangeFactory(new PrototypeExchangeFactory());
            ecc.setProcessorExchangeFactory(new PrototypeProcessorExchangeFactory());
        }
        ecc.getExchangeFactory().setCapacity(config.getExchangeFactoryCapacity());
        ecc.getProcessorExchangeFactory().setCapacity(config.getExchangeFactoryCapacity());
        ecc.getExchangeFactory().setStatisticsEnabled(config.isExchangeFactoryStatisticsEnabled());
        ecc.getProcessorExchangeFactory().setStatisticsEnabled(config.isExchangeFactoryStatisticsEnabled());

        if (!config.isJmxEnabled()) {
            camelContext.disableJMX();
        }

        if (config.getName() != null) {
            ecc.setName(config.getName());
        }
        if (config.getDescription() != null) {
            ecc.setDescription(config.getDescription());
        }
        if (config.getStartupSummaryLevel() != null) {
            camelContext.setStartupSummaryLevel(config.getStartupSummaryLevel());
        }

        if (config.getShutdownTimeout() > 0) {
            camelContext.getShutdownStrategy().setTimeout(config.getShutdownTimeout());
        }
        camelContext.getShutdownStrategy().setSuppressLoggingOnTimeout(config.isShutdownSuppressLoggingOnTimeout());
        camelContext.getShutdownStrategy().setShutdownNowOnTimeout(config.isShutdownNowOnTimeout());
        camelContext.getShutdownStrategy().setShutdownRoutesInReverseOrder(config.isShutdownRoutesInReverseOrder());
        camelContext.getShutdownStrategy().setLogInflightExchangesOnTimeout(config.isShutdownLogInflightExchangesOnTimeout());

        camelContext.getInflightRepository().setInflightBrowseEnabled(config.isInflightRepositoryBrowseEnabled());

        if (config.getLogDebugMaxChars() != 0) {
            camelContext.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS,
                    Integer.toString(config.getLogDebugMaxChars()));
        }

        // stream caching
        camelContext.setStreamCaching(config.isStreamCachingEnabled());
        camelContext.getStreamCachingStrategy().setAllowClasses(config.getStreamCachingAllowClasses());
        camelContext.getStreamCachingStrategy().setDenyClasses(config.getStreamCachingDenyClasses());
        camelContext.getStreamCachingStrategy().setSpoolEnabled(config.isStreamCachingSpoolEnabled());
        camelContext.getStreamCachingStrategy().setAnySpoolRules(config.isStreamCachingAnySpoolRules());
        camelContext.getStreamCachingStrategy().setBufferSize(config.getStreamCachingBufferSize());
        camelContext.getStreamCachingStrategy()
                .setRemoveSpoolDirectoryWhenStopping(config.isStreamCachingRemoveSpoolDirectoryWhenStopping());
        camelContext.getStreamCachingStrategy().setSpoolCipher(config.getStreamCachingSpoolCipher());
        if (config.getStreamCachingSpoolDirectory() != null) {
            camelContext.getStreamCachingStrategy().setSpoolDirectory(config.getStreamCachingSpoolDirectory());
        }
        if (config.getStreamCachingSpoolThreshold() != 0) {
            camelContext.getStreamCachingStrategy().setSpoolThreshold(config.getStreamCachingSpoolThreshold());
        }
        if (config.getStreamCachingSpoolUsedHeapMemoryLimit() != null) {
            StreamCachingStrategy.SpoolUsedHeapMemoryLimit limit;
            if ("Committed".equalsIgnoreCase(config.getStreamCachingSpoolUsedHeapMemoryLimit())) {
                limit = StreamCachingStrategy.SpoolUsedHeapMemoryLimit.Committed;
            } else if ("Max".equalsIgnoreCase(config.getStreamCachingSpoolUsedHeapMemoryLimit())) {
                limit = StreamCachingStrategy.SpoolUsedHeapMemoryLimit.Max;
            } else {
                throw new IllegalArgumentException(
                        "Invalid option " + config.getStreamCachingSpoolUsedHeapMemoryLimit()
                                                   + " must either be Committed or Max");
            }
            camelContext.getStreamCachingStrategy().setSpoolUsedHeapMemoryLimit(limit);
        }
        if (config.getStreamCachingSpoolUsedHeapMemoryThreshold() != 0) {
            camelContext.getStreamCachingStrategy()
                    .setSpoolUsedHeapMemoryThreshold(config.getStreamCachingSpoolUsedHeapMemoryThreshold());
        }

        if ("default".equals(config.getUuidGenerator())) {
            camelContext.setUuidGenerator(new DefaultUuidGenerator());
        } else if ("short".equals(config.getUuidGenerator())) {
            camelContext.setUuidGenerator(new ShortUuidGenerator());
        } else if ("classic".equals(config.getUuidGenerator())) {
            camelContext.setUuidGenerator(new ClassicUuidGenerator());
        } else if ("simple".equals(config.getUuidGenerator())) {
            camelContext.setUuidGenerator(new SimpleUuidGenerator());
        } else if ("off".equals(config.getUuidGenerator())) {
            camelContext.setUuidGenerator(new OffUuidGenerator());
            LOG.warn("Using OffUuidGenerator (Only intended for development purposes)");
        }

        camelContext.setLogMask(config.isLogMask());
        camelContext.setLogExhaustedMessageBody(config.isLogExhaustedMessageBody());
        camelContext.setAutoStartup(config.isAutoStartup());
        camelContext.setAllowUseOriginalMessage(config.isAllowUseOriginalMessage());
        camelContext.setCaseInsensitiveHeaders(config.isCaseInsensitiveHeaders());
        camelContext.setAutowiredEnabled(config.isAutowiredEnabled());
        camelContext.setUseBreadcrumb(config.isUseBreadcrumb());
        camelContext.setUseDataType(config.isUseDataType());
        camelContext.setDumpRoutes(config.getDumpRoutes());
        camelContext.setUseMDCLogging(config.isUseMdcLogging());
        camelContext.setMDCLoggingKeysPattern(config.getMdcLoggingKeysPattern());
        camelContext.setLoadTypeConverters(config.isLoadTypeConverters());
        camelContext.setTypeConverterStatisticsEnabled(config.isTypeConverterStatisticsEnabled());
        camelContext.setLoadHealthChecks(config.isLoadHealthChecks());
        camelContext.setDevConsole(config.isDevConsoleEnabled());
        camelContext.setModeline(config.isModeline());
        if (config.isRoutesReloadEnabled()) {
            RouteWatcherReloadStrategy reloader = new RouteWatcherReloadStrategy(
                    config.getRoutesReloadDirectory(), config.isRoutesReloadDirectoryRecursive());
            reloader.setPattern(config.getRoutesReloadPattern());
            reloader.setRemoveAllRoutes(config.isRoutesReloadRemoveAllRoutes());
            camelContext.addService(reloader);
        }
        if (config.getDumpRoutes() != null) {
            DumpRoutesStrategy drs = camelContext.getCamelContextExtension().getContextPlugin(DumpRoutesStrategy.class);
            drs.setInclude(config.getDumpRoutesInclude());
            drs.setLog(config.isDumpRoutesLog());
            drs.setUriAsParameters(config.isDumpRoutesUriAsParameters());
            drs.setGeneratedIds(config.isDumpRoutesGeneratedIds());
            drs.setResolvePlaceholders(config.isDumpRoutesResolvePlaceholders());
            drs.setOutput(config.getDumpRoutesOutput());
        }
        if (config.isContextReloadEnabled() && camelContext.hasService(ContextReloadStrategy.class) == null) {
            ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
            camelContext.addService(reloader);
        }

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            camelContext.getManagementStrategy().getManagementAgent()
                    .setEndpointRuntimeStatisticsEnabled(config.isEndpointRuntimeStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent()
                    .setLoadStatisticsEnabled(config.isLoadStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent()
                    .setStatisticsLevel(config.getJmxManagementStatisticsLevel());
            camelContext.getManagementStrategy().getManagementAgent()
                    .setMBeansLevel(config.getJmxManagementMBeansLevel());
            camelContext.getManagementStrategy().getManagementAgent()
                    .setManagementNamePattern(config.getJmxManagementNamePattern());
        }
        if (config.isCamelEventsTimestampEnabled()) {
            camelContext.getManagementStrategy().getEventFactory().setTimestampEnabled(true);
        }

        // global options
        if (config.getGlobalOptions() != null) {
            Map<String, String> map = camelContext.getGlobalOptions();
            if (map == null) {
                map = new HashMap<>();
            }
            map.putAll(config.getGlobalOptions());
            camelContext.setGlobalOptions(map);
        }

        // global endpoint configurations
        camelContext.getGlobalEndpointConfiguration().setAutowiredEnabled(config.isAutowiredEnabled());
        camelContext.getGlobalEndpointConfiguration().setBridgeErrorHandler(config.isEndpointBridgeErrorHandler());
        camelContext.getGlobalEndpointConfiguration().setLazyStartProducer(config.isEndpointLazyStartProducer());

        // debug may be enabled via camel-debug JAR on classpath so if config is false (default)
        // then do not change setting on camel-context
        if (config.isDebugging()) {
            camelContext.setDebugging(true);
        }
        if (config.isMessageHistory()) {
            camelContext.setMessageHistory(true);
        }
        if (config.isSourceLocationEnabled()) {
            camelContext.setSourceLocationEnabled(true);
        }

        camelContext.setBacklogTracing(config.isBacklogTracing());
        camelContext.setBacklogTracingStandby(config.isBacklogTracingStandby());
        camelContext.setBacklogTracingTemplates(config.isBacklogTracingTemplates());
        camelContext.setTracing(config.isTracing());
        camelContext.setTracingStandby(config.isTracingStandby());
        camelContext.setTracingPattern(config.getTracingPattern());
        camelContext.setTracingLoggingFormat(config.getTracingLoggingFormat());
        camelContext.setTracingTemplates(config.isTracingTemplates());

        if (config.getThreadNamePattern() != null) {
            camelContext.getExecutorServiceManager().setThreadNamePattern(config.getThreadNamePattern());
        }

        if (config.getRouteFilterIncludePattern() != null || config.getRouteFilterExcludePattern() != null) {
            camelContext.getCamelContextExtension().getContextPlugin(Model.class).setRouteFilterPattern(
                    config.getRouteFilterIncludePattern(),
                    config.getRouteFilterExcludePattern());
        }

        // supervising route controller
        if (config.isRouteControllerSuperviseEnabled()) {
            SupervisingRouteController src = camelContext.getRouteController().supervising();
            if (config.getRouteControllerIncludeRoutes() != null) {
                src.setIncludeRoutes(config.getRouteControllerIncludeRoutes());
            }
            if (config.getRouteControllerExcludeRoutes() != null) {
                src.setExcludeRoutes(config.getRouteControllerExcludeRoutes());
            }
            if (config.getRouteControllerThreadPoolSize() > 0) {
                src.setThreadPoolSize(config.getRouteControllerThreadPoolSize());
            }
            if (config.getRouteControllerBackOffDelay() > 0) {
                src.setBackOffDelay(config.getRouteControllerBackOffDelay());
            }
            if (config.getRouteControllerInitialDelay() > 0) {
                src.setInitialDelay(config.getRouteControllerInitialDelay());
            }
            if (config.getRouteControllerBackOffMaxAttempts() > 0) {
                src.setBackOffMaxAttempts(config.getRouteControllerBackOffMaxAttempts());
            }
            if (config.getRouteControllerBackOffMaxDelay() > 0) {
                src.setBackOffMaxDelay(config.getRouteControllerBackOffDelay());
            }
            if (config.getRouteControllerBackOffMaxElapsedTime() > 0) {
                src.setBackOffMaxElapsedTime(config.getRouteControllerBackOffMaxElapsedTime());
            }
            if (config.getRouteControllerBackOffMultiplier() > 0) {
                src.setBackOffMultiplier(config.getRouteControllerBackOffMultiplier());
            }
            src.setUnhealthyOnExhausted(config.isRouteControllerUnhealthyOnExhausted());
        }
    }

    /**
     * Performs additional configuration to lookup beans of Camel types to configure additional configurations on the
     * Camel context.
     * <p/>
     * Similar code in camel-core-xml module in class org.apache.camel.core.xml.AbstractCamelContextFactoryBean.
     */
    public static void afterConfigure(final CamelContext camelContext) throws Exception {
        final Registry registry = camelContext.getRegistry();
        final ManagementStrategy managementStrategy = camelContext.getManagementStrategy();

        StartupStepRecorder ssr = getSingleBeanOfType(registry, StartupStepRecorder.class);
        if (ssr != null) {
            camelContext.getCamelContextExtension().setStartupStepRecorder(ssr);
        }
        CliConnectorFactory ccf = getSingleBeanOfType(registry, CliConnectorFactory.class);
        if (ccf != null) {
            camelContext.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, ccf);
        }
        PropertiesComponent pc = getSingleBeanOfType(registry, PropertiesComponent.class);
        if (pc != null) {
            camelContext.setPropertiesComponent(pc);
        }
        BacklogTracer bt = getSingleBeanOfType(registry, BacklogTracer.class);
        if (bt != null) {
            camelContext.getCamelContextExtension().addContextPlugin(BacklogTracer.class, bt);
        }
        InflightRepository ir = getSingleBeanOfType(registry, InflightRepository.class);
        if (ir != null) {
            camelContext.setInflightRepository(ir);
        }
        AsyncProcessorAwaitManager apam = getSingleBeanOfType(registry, AsyncProcessorAwaitManager.class);
        if (apam != null) {
            camelContext.getCamelContextExtension().addContextPlugin(AsyncProcessorAwaitManager.class, apam);
        }
        ManagementStrategy ms = getSingleBeanOfType(registry, ManagementStrategy.class);
        if (ms != null) {
            camelContext.setManagementStrategy(ms);
        }
        ManagementObjectNameStrategy mons = getSingleBeanOfType(registry, ManagementObjectNameStrategy.class);
        if (mons != null) {
            managementStrategy.setManagementObjectNameStrategy(mons);
        }
        EventFactory ef = getSingleBeanOfType(registry, EventFactory.class);
        if (ef != null) {
            managementStrategy.setEventFactory(ef);
        }
        UnitOfWorkFactory uowf = getSingleBeanOfType(registry, UnitOfWorkFactory.class);
        if (uowf != null) {
            camelContext.getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, uowf);
        }
        RuntimeEndpointRegistry rer = getSingleBeanOfType(registry, RuntimeEndpointRegistry.class);
        if (rer != null) {
            camelContext.setRuntimeEndpointRegistry(rer);
        }
        ModelJAXBContextFactory mjcf = getSingleBeanOfType(registry, ModelJAXBContextFactory.class);
        if (mjcf != null) {
            camelContext.getCamelContextExtension().addContextPlugin(ModelJAXBContextFactory.class, mjcf);
        }
        ClassResolver cr = getSingleBeanOfType(registry, ClassResolver.class);
        if (cr != null) {
            camelContext.setClassResolver(cr);
        }
        FactoryFinderResolver ffr = getSingleBeanOfType(registry, FactoryFinderResolver.class);
        if (ffr != null) {
            camelContext.getCamelContextExtension().addContextPlugin(FactoryFinderResolver.class, ffr);
        }
        RouteController rc = getSingleBeanOfType(registry, RouteController.class);
        if (rc != null) {
            camelContext.setRouteController(rc);
        }
        UuidGenerator ug = getSingleBeanOfType(registry, UuidGenerator.class);
        if (ug != null) {
            camelContext.setUuidGenerator(ug);
        }
        ExecutorServiceManager esm = getSingleBeanOfType(registry, ExecutorServiceManager.class);
        if (esm != null) {
            camelContext.setExecutorServiceManager(esm);
        }
        ThreadPoolFactory tpf = getSingleBeanOfType(registry, ThreadPoolFactory.class);
        if (tpf != null) {
            camelContext.getExecutorServiceManager().setThreadPoolFactory(tpf);
        }
        ProcessorFactory pf = getSingleBeanOfType(registry, ProcessorFactory.class);
        if (pf != null) {
            camelContext.getCamelContextExtension().addContextPlugin(ProcessorFactory.class, pf);
        }
        Debugger debugger = getSingleBeanOfType(registry, Debugger.class);
        if (debugger != null) {
            camelContext.setDebugger(debugger);
        }
        NodeIdFactory nif = getSingleBeanOfType(registry, NodeIdFactory.class);
        if (nif != null) {
            camelContext.getCamelContextExtension().addContextPlugin(NodeIdFactory.class, nif);
        }
        MessageHistoryFactory mhf = getSingleBeanOfType(registry, MessageHistoryFactory.class);
        if (mhf != null) {
            camelContext.setMessageHistoryFactory(mhf);
        }
        ReactiveExecutor re = getSingleBeanOfType(registry, ReactiveExecutor.class);
        if (re != null) {
            camelContext.getCamelContextExtension().setReactiveExecutor(re);
        }
        ShutdownStrategy ss = getSingleBeanOfType(registry, ShutdownStrategy.class);
        if (ss != null) {
            camelContext.setShutdownStrategy(ss);
        }
        ExchangeFactory exf = getSingleBeanOfType(registry, ExchangeFactory.class);
        if (exf != null) {
            camelContext.getCamelContextExtension().setExchangeFactory(exf);
        }
        Set<TypeConverters> tcs = registry.findByType(TypeConverters.class);
        if (!tcs.isEmpty()) {
            tcs.forEach(t -> camelContext.getTypeConverterRegistry().addTypeConverters(t));
        }
        Set<EventNotifier> ens = registry.findByType(EventNotifier.class);
        if (!ens.isEmpty()) {
            ens.forEach(n -> camelContext.getManagementStrategy().addEventNotifier(n));
        }
        Set<EndpointStrategy> ess = registry.findByType(EndpointStrategy.class);
        if (!ess.isEmpty()) {
            ess.forEach(camelContext.getCamelContextExtension()::registerEndpointCallback);
        }
        Set<CamelClusterService> csss = registry.findByType(CamelClusterService.class);
        if (!csss.isEmpty()) {
            for (CamelClusterService css : csss) {
                camelContext.addService(css);
            }
        }
        Set<RoutePolicyFactory> rpfs = registry.findByType(RoutePolicyFactory.class);
        if (!rpfs.isEmpty()) {
            rpfs.forEach(camelContext::addRoutePolicyFactory);
        }

        final Predicate<EventNotifier> containsEventNotifier = managementStrategy.getEventNotifiers()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, EventNotifier.class, containsEventNotifier.negate(),
                managementStrategy::addEventNotifier);
        final Predicate<InterceptStrategy> containsInterceptStrategy
                = camelContext.getCamelContextExtension().getInterceptStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, InterceptStrategy.class, containsInterceptStrategy.negate(),
                camelContext.getCamelContextExtension()::addInterceptStrategy);
        final Predicate<LifecycleStrategy> containsLifecycleStrategy = camelContext.getLifecycleStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, LifecycleStrategy.class, containsLifecycleStrategy.negate(),
                camelContext::addLifecycleStrategy);
        ModelCamelContext mcc = (ModelCamelContext) camelContext;
        final Predicate<ModelLifecycleStrategy> containsModelLifecycleStrategy = mcc.getModelLifecycleStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, ModelLifecycleStrategy.class,
                containsModelLifecycleStrategy.negate(), mcc::addModelLifecycleStrategy);

        // log listeners
        Map<String, LogListener> logListeners = registry.findByTypeWithName(LogListener.class);
        if (logListeners != null && !logListeners.isEmpty()) {
            for (LogListener logListener : logListeners.values()) {
                boolean contains = camelContext.getCamelContextExtension().getLogListeners() != null
                        && camelContext.getCamelContextExtension().getLogListeners().contains(logListener);
                if (!contains) {
                    camelContext.getCamelContextExtension().addLogListener(logListener);
                }
            }
        }

        // service registry
        Map<String, ServiceRegistry> serviceRegistries = registry.findByTypeWithName(ServiceRegistry.class);
        if (serviceRegistries != null && !serviceRegistries.isEmpty()) {
            for (Map.Entry<String, ServiceRegistry> entry : serviceRegistries.entrySet()) {
                ServiceRegistry service = entry.getValue();
                if (service.getId() == null) {
                    service.setGeneratedId(camelContext.getUuidGenerator().generateUuid());
                }
                LOG.info("Adding Camel Cloud ServiceRegistry with id: {} and implementation: {}", service.getId(), service);
                camelContext.addService(service);
            }
        }

        // SSL context parameters
        GlobalSSLContextParametersSupplier sslContextParametersSupplier
                = getSingleBeanOfType(registry, GlobalSSLContextParametersSupplier.class);
        if (sslContextParametersSupplier != null) {
            camelContext.setSSLContextParameters(sslContextParametersSupplier.get());
        }

        // health check
        HealthCheckRegistry healthCheckRegistry = getSingleBeanOfType(registry, HealthCheckRegistry.class);
        if (healthCheckRegistry != null) {
            healthCheckRegistry.setCamelContext(camelContext);
            LOG.debug("Using HealthCheckRegistry: {}", healthCheckRegistry);
            camelContext.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, healthCheckRegistry);
        } else {
            // okay attempt to inject this camel context into existing health check (if any)
            healthCheckRegistry = HealthCheckRegistry.get(camelContext);
            if (healthCheckRegistry != null) {
                healthCheckRegistry.setCamelContext(camelContext);
            }
        }
        if (healthCheckRegistry != null) {
            // Health check repository
            Set<HealthCheckRepository> repositories = registry.findByType(HealthCheckRepository.class);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(repositories)) {
                for (HealthCheckRepository repository : repositories) {
                    healthCheckRegistry.register(repository);
                }
            }
        }
        // dev console
        DevConsoleRegistry devConsoleRegistry = getSingleBeanOfType(registry, DevConsoleRegistry.class);
        if (devConsoleRegistry != null) {
            devConsoleRegistry.setCamelContext(camelContext);
            LOG.debug("Using DevConsoleRegistry: {}", devConsoleRegistry);
            camelContext.getCamelContextExtension().addContextPlugin(DevConsoleRegistry.class, devConsoleRegistry);
        } else {
            // okay attempt to inject this camel context into existing dev console (if any)
            devConsoleRegistry = DevConsoleRegistry.get(camelContext);
            if (devConsoleRegistry != null) {
                devConsoleRegistry.setCamelContext(camelContext);
            }
        }
        if (devConsoleRegistry != null) {
            Set<DevConsole> consoles = registry.findByType(DevConsole.class);
            for (DevConsole console : consoles) {
                devConsoleRegistry.register(console);
            }
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(registry, camelContext);

        // vaults
        AwsVaultConfiguration aws = getSingleBeanOfType(registry, AwsVaultConfiguration.class);
        if (aws != null) {
            VaultConfiguration vault = camelContext.getVaultConfiguration();
            vault.setAwsVaultConfiguration(aws);
        }
        GcpVaultConfiguration gcp = getSingleBeanOfType(registry, GcpVaultConfiguration.class);
        if (gcp != null) {
            VaultConfiguration vault = camelContext.getVaultConfiguration();
            vault.setGcpVaultConfiguration(gcp);
        }
        AzureVaultConfiguration azure = getSingleBeanOfType(registry, AzureVaultConfiguration.class);
        if (azure != null) {
            VaultConfiguration vault = camelContext.getVaultConfiguration();
            vault.setAzureVaultConfiguration(azure);
        }
        HashicorpVaultConfiguration hashicorp = getSingleBeanOfType(registry, HashicorpVaultConfiguration.class);
        if (hashicorp != null) {
            VaultConfiguration vault = camelContext.getVaultConfiguration();
            vault.setHashicorpVaultConfiguration(hashicorp);
        }
        configureVault(camelContext);
    }

    /**
     * Configures security vaults such as AWS, Azure, Google and Hashicorp.
     */
    protected static void configureVault(CamelContext camelContext) throws Exception {
        VaultConfiguration vc = camelContext.getVaultConfiguration();
        if (vc == null) {
            return;
        }

        if (vc.aws().isRefreshEnabled()) {
            Optional<Runnable> task = PluginHelper.getPeriodTaskResolver(camelContext)
                    .newInstance("aws-secret-refresh", Runnable.class);
            if (task.isPresent()) {
                long period = vc.aws().getRefreshPeriod();
                Runnable r = task.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
                }
                if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                    // refresh is enabled then we need to automatically enable context-reload as well
                    ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                    camelContext.addService(reloader);
                }
                PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(camelContext);
                scheduler.schedulePeriodTask(r, period);
            }
        }

        if (vc.gcp().isRefreshEnabled()) {
            Optional<Runnable> task = PluginHelper.getPeriodTaskResolver(camelContext)
                    .newInstance("gcp-secret-refresh", Runnable.class);
            if (task.isPresent()) {
                long period = vc.gcp().getRefreshPeriod();
                Runnable r = task.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
                }
                if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                    // refresh is enabled then we need to automatically enable context-reload as well
                    ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                    camelContext.addService(reloader);
                }
                PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(camelContext);
                scheduler.schedulePeriodTask(r, period);
            }
        }

        if (vc.azure().isRefreshEnabled()) {
            Optional<Runnable> task = PluginHelper.getPeriodTaskResolver(camelContext)
                    .newInstance("azure-secret-refresh", Runnable.class);
            if (task.isPresent()) {
                long period = vc.azure().getRefreshPeriod();
                Runnable r = task.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
                }
                if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                    // refresh is enabled then we need to automatically enable context-reload as well
                    ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                    camelContext.addService(reloader);
                }
                PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(camelContext);
                scheduler.schedulePeriodTask(r, period);
            }
        }
    }

    public static void afterPropertiesSet(final CamelContext camelContext) throws Exception {
        // additional configuration
    }

    private static <T> T getSingleBeanOfType(Registry registry, Class<T> type) {
        Map<String, T> beans = registry.findByTypeWithName(type);
        if (beans.size() == 1) {
            return beans.values().iterator().next();
        } else {
            return null;
        }
    }

    private static <T> void registerPropertiesForBeanTypesWithCondition(
            final Registry registry, final Class<T> beanType, final Predicate<T> condition,
            final Consumer<T> propertySetter) {
        final Map<String, T> beans = registry.findByTypeWithName(beanType);
        if (!ObjectHelper.isNotEmpty(beans)) {
            return;
        }

        final String simpleName = beanType.getSimpleName();
        beans.forEach((name, bean) -> {
            if (condition.test(bean)) {
                LOG.info("Adding custom {} with id: {} and implementation: {}", simpleName, name, bean);
                propertySetter.accept(bean);
            }
        });
    }

    private static void initThreadPoolProfiles(Registry registry, CamelContext camelContext) {
        Set<String> defaultIds = new HashSet<>();

        // lookup and use custom profiles from the registry
        Map<String, ThreadPoolProfile> profiles = registry.findByTypeWithName(ThreadPoolProfile.class);
        if (profiles != null && !profiles.isEmpty()) {
            for (Map.Entry<String, ThreadPoolProfile> entry : profiles.entrySet()) {
                ThreadPoolProfile profile = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (profile.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: {} and implementation: {}", entry.getKey(),
                            profile);
                    camelContext.getExecutorServiceManager().setDefaultThreadPoolProfile(profile);
                    defaultIds.add(entry.getKey());
                } else {
                    camelContext.getExecutorServiceManager().registerThreadPoolProfile(profile);
                }
            }
        }

        // validate at most one is defined
        if (defaultIds.size() > 1) {
            throw new IllegalArgumentException(
                    "Only exactly one default ThreadPoolProfile is allowed, was " + defaultIds.size() + " ids: " + defaultIds);
        }
    }

}
