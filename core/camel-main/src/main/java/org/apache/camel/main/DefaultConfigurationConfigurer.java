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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.TypeConverters;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckService;
import org.apache.camel.model.Model;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
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
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.jsse.GlobalSSLContextParametersSupplier;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To configure the {@link DefaultConfigurationProperties} on {@link org.apache.camel.CamelContext}
 * used by Camel Main, Camel Spring Boot and other runtimes.
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
    public static void configure(CamelContext camelContext, DefaultConfigurationProperties config) throws Exception {
        ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
        ecc.getBeanIntrospection().setExtendedStatistics(config.isBeanIntrospectionExtendedStatistics());
        if (config.getBeanIntrospectionLoggingLevel() != null) {
            ecc.getBeanIntrospection().setLoggingLevel(config.getBeanIntrospectionLoggingLevel());
        }
        ecc.getBeanIntrospection().afterPropertiesConfigured(camelContext);

        if (!config.isJmxEnabled()) {
            camelContext.disableJMX();
        }

        if (config.getName() != null) {
            ecc.setName(config.getName());
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
            camelContext.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "" + config.getLogDebugMaxChars());
        }

        // stream caching
        camelContext.setStreamCaching(config.isStreamCachingEnabled());
        camelContext.getStreamCachingStrategy().setAnySpoolRules(config.isStreamCachingAnySpoolRules());
        camelContext.getStreamCachingStrategy().setBufferSize(config.getStreamCachingBufferSize());
        camelContext.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(config.isStreamCachingRemoveSpoolDirectoryWhenStopping());
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
                throw new IllegalArgumentException("Invalid option " + config.getStreamCachingSpoolUsedHeapMemoryLimit() + " must either be Committed or Max");
            }
            camelContext.getStreamCachingStrategy().setSpoolUsedHeapMemoryLimit(limit);
        }
        if (config.getStreamCachingSpoolUsedHeapMemoryThreshold() != 0) {
            camelContext.getStreamCachingStrategy().setSpoolUsedHeapMemoryThreshold(config.getStreamCachingSpoolUsedHeapMemoryThreshold());
        }

        camelContext.setMessageHistory(config.isMessageHistory());
        camelContext.setLogMask(config.isLogMask());
        camelContext.setLogExhaustedMessageBody(config.isLogExhaustedMessageBody());
        camelContext.setAutoStartup(config.isAutoStartup());
        camelContext.setAllowUseOriginalMessage(config.isAllowUseOriginalMessage());
        camelContext.setCaseInsensitiveHeaders(config.isCaseInsensitiveHeaders());
        camelContext.setUseBreadcrumb(config.isUseBreadcrumb());
        camelContext.setUseDataType(config.isUseDataType());
        camelContext.setUseMDCLogging(config.isUseMdcLogging());
        camelContext.setMDCLoggingKeysPattern(config.getMdcLoggingKeysPattern());
        camelContext.setLoadTypeConverters(config.isLoadTypeConverters());

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            camelContext.getManagementStrategy().getManagementAgent().setEndpointRuntimeStatisticsEnabled(config.isEndpointRuntimeStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(config.getJmxManagementStatisticsLevel());
            camelContext.getManagementStrategy().getManagementAgent().setManagementNamePattern(config.getJmxManagementNamePattern());
        }

        // global endpoint configurations
        camelContext.getGlobalEndpointConfiguration().setBasicPropertyBinding(config.isEndpointBasicPropertyBinding());
        camelContext.getGlobalEndpointConfiguration().setBridgeErrorHandler(config.isEndpointBridgeErrorHandler());
        camelContext.getGlobalEndpointConfiguration().setLazyStartProducer(config.isEndpointLazyStartProducer());

        camelContext.setBacklogTracing(config.isBacklogTracing());
        camelContext.setTracing(config.isTracing());
        camelContext.setTracingPattern(config.getTracingPattern());

        if (config.getThreadNamePattern() != null) {
            camelContext.getExecutorServiceManager().setThreadNamePattern(config.getThreadNamePattern());
        }

        if (config.getRouteFilterIncludePattern() != null || config.getRouteFilterExcludePattern() != null) {
            camelContext.getExtension(Model.class).setRouteFilterPattern(config.getRouteFilterIncludePattern(), config.getRouteFilterExcludePattern());
        }
    }

    /**
     * Performs additional configuration to lookup beans of Camel types to configure
     * additional configurations on the Camel context.
     * <p/>
     * Similar code in camel-core-xml module in class org.apache.camel.core.xml.AbstractCamelContextFactoryBean
     * or in camel-spring-boot module in class org.apache.camel.spring.boot.CamelAutoConfiguration.
     */
    public static void afterPropertiesSet(final CamelContext camelContext) throws Exception {
        final Registry registry = camelContext.getRegistry();
        final ManagementStrategy managementStrategy = camelContext.getManagementStrategy();
        final ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);

        PropertiesComponent pc = getSingleBeanOfType(registry, PropertiesComponent.class);
        if (pc != null) {
            ecc.setPropertiesComponent(pc);
        }
        BacklogTracer bt = getSingleBeanOfType(registry, BacklogTracer.class);
        if (bt != null) {
            ecc.setExtension(BacklogTracer.class, bt);
        }
        InflightRepository ir = getSingleBeanOfType(registry, InflightRepository.class);
        if (ir != null) {
            ecc.setInflightRepository(ir);
        }
        AsyncProcessorAwaitManager apam = getSingleBeanOfType(registry, AsyncProcessorAwaitManager.class);
        if (apam != null) {
            ecc.setAsyncProcessorAwaitManager(apam);
        }
        ManagementStrategy ms = getSingleBeanOfType(registry, ManagementStrategy.class);
        if (ms != null) {
            ecc.setManagementStrategy(ms);
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
            ecc.setUnitOfWorkFactory(uowf);
        }
        RuntimeEndpointRegistry rer = getSingleBeanOfType(registry, RuntimeEndpointRegistry.class);
        if (rer != null) {
            ecc.setRuntimeEndpointRegistry(rer);
        }
        ModelJAXBContextFactory mjcf = getSingleBeanOfType(registry, ModelJAXBContextFactory.class);
        if (mjcf != null) {
            ecc.setModelJAXBContextFactory(mjcf);
        }
        ClassResolver cr = getSingleBeanOfType(registry, ClassResolver.class);
        if (cr != null) {
            ecc.setClassResolver(cr);
        }
        FactoryFinderResolver ffr = getSingleBeanOfType(registry, FactoryFinderResolver.class);
        if (ffr != null) {
            ecc.setFactoryFinderResolver(ffr);
        }
        RouteController rc = getSingleBeanOfType(registry, RouteController.class);
        if (rc != null) {
            ecc.setRouteController(rc);
        }
        UuidGenerator ug = getSingleBeanOfType(registry, UuidGenerator.class);
        if (ug != null) {
            ecc.setUuidGenerator(ug);
        }
        ExecutorServiceManager esm = getSingleBeanOfType(registry, ExecutorServiceManager.class);
        if (esm != null) {
            ecc.setExecutorServiceManager(esm);
        }
        ThreadPoolFactory tpf = getSingleBeanOfType(registry, ThreadPoolFactory.class);
        if (tpf != null) {
            ecc.getExecutorServiceManager().setThreadPoolFactory(tpf);
        }
        ProcessorFactory pf = getSingleBeanOfType(registry, ProcessorFactory.class);
        if (pf != null) {
            ecc.setProcessorFactory(pf);
        }
        Debugger debugger = getSingleBeanOfType(registry, Debugger.class);
        if (debugger != null) {
            ecc.setDebugger(debugger);
        }
        NodeIdFactory nif = getSingleBeanOfType(registry, NodeIdFactory.class);
        if (nif != null) {
            ecc.setNodeIdFactory(nif);
        }
        MessageHistoryFactory mhf = getSingleBeanOfType(registry, MessageHistoryFactory.class);
        if (mhf != null) {
            ecc.setMessageHistoryFactory(mhf);
        }
        ReactiveExecutor re = getSingleBeanOfType(registry, ReactiveExecutor.class);
        if (re != null) {
            ecc.setReactiveExecutor(re);
        }
        ShutdownStrategy ss = getSingleBeanOfType(registry, ShutdownStrategy.class);
        if (ss != null) {
            ecc.setShutdownStrategy(ss);
        }
        Set<TypeConverters> tcs = registry.findByType(TypeConverters.class);
        if (!tcs.isEmpty()) {
            tcs.forEach(t -> camelContext.getTypeConverterRegistry().addTypeConverters(t));
        }
        Set<EndpointStrategy> ess = registry.findByType(EndpointStrategy.class);
        if (!ess.isEmpty()) {
            ess.forEach(ecc::registerEndpointCallback);
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
        registerPropertiesForBeanTypesWithCondition(registry, EventNotifier.class, containsEventNotifier.negate(), managementStrategy::addEventNotifier);
        final Predicate<InterceptStrategy> containsInterceptStrategy = camelContext.adapt(ExtendedCamelContext.class).getInterceptStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, InterceptStrategy.class, containsInterceptStrategy.negate(), camelContext.adapt(ExtendedCamelContext.class)::addInterceptStrategy);
        final Predicate<LifecycleStrategy> containsLifecycleStrategy = camelContext.getLifecycleStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, LifecycleStrategy.class, containsLifecycleStrategy.negate(), camelContext::addLifecycleStrategy);
        final Predicate<LogListener> containsLogListener = camelContext.adapt(ExtendedCamelContext.class).getLogListeners()::contains;
        registerPropertiesForBeanTypesWithCondition(registry, LogListener.class, containsLogListener.negate(), camelContext.adapt(ExtendedCamelContext.class)::addLogListener);

        // service registry
        Map<String, ServiceRegistry> serviceRegistries = registry.findByTypeWithName(ServiceRegistry.class);
        if (serviceRegistries != null && !serviceRegistries.isEmpty()) {
            for (Map.Entry<String, ServiceRegistry> entry : serviceRegistries.entrySet()) {
                ServiceRegistry service = entry.getValue();

                if (service.getId() == null) {
                    service.setId(camelContext.getUuidGenerator().generateUuid());
                }

                LOG.info("Using ServiceRegistry with id: {} and implementation: {}", service.getId(), service);
                camelContext.addService(service);
            }
        }

        // SSL context parameters
        GlobalSSLContextParametersSupplier sslContextParametersSupplier = getSingleBeanOfType(registry, GlobalSSLContextParametersSupplier.class);
        if (sslContextParametersSupplier != null) {
            camelContext.setSSLContextParameters(sslContextParametersSupplier.get());
        }

        // health check
        HealthCheckRegistry healthCheckRegistry = getSingleBeanOfType(registry, HealthCheckRegistry.class);
        if (healthCheckRegistry != null) {
            healthCheckRegistry.setCamelContext(camelContext);
            LOG.info("Using HealthCheckRegistry: {}", healthCheckRegistry);
            camelContext.setExtension(HealthCheckRegistry.class, healthCheckRegistry);
        } else {
            healthCheckRegistry = HealthCheckRegistry.get(camelContext);
            healthCheckRegistry.setCamelContext(camelContext);
        }
        Set<HealthCheckRepository> hcrs = registry.findByType(HealthCheckRepository.class);
        if (!hcrs.isEmpty()) {
            hcrs.forEach(healthCheckRegistry::addRepository);
        }

        HealthCheckService hcs = getSingleBeanOfType(registry, HealthCheckService.class);
        if (hcs != null) {
            camelContext.addService(hcs);
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(registry, camelContext);
    }

    private static <T> void registerPropertyForBeanType(final Registry registry, final Class<T> beanType, final Consumer<T> propertySetter) {
        T propertyBean = getSingleBeanOfType(registry, beanType);
        if (propertyBean == null) {
            return;
        }

        LOG.info("Using custom {}: {}", beanType.getSimpleName(), propertyBean);
        propertySetter.accept(propertyBean);
    }

    private static <T> T getSingleBeanOfType(Registry registry, Class<T> type) {
        Map<String, T> beans = registry.findByTypeWithName(type);
        if (beans.size() == 1) {
            return beans.values().iterator().next();
        } else {
            return null;
        }
    }

    private static <T> void registerPropertiesForBeanTypes(final Registry registry, final Class<T> beanType, final Consumer<T> propertySetter) {
        registerPropertiesForBeanTypesWithCondition(registry, beanType, b -> true, propertySetter);
    }

    private static <T> void registerPropertiesForBeanTypesWithCondition(final Registry registry, final Class<T> beanType, final Predicate<T> condition,
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

    private static <T> Consumer<T> addServiceToContext(final CamelContext camelContext) {
        return service -> {
            try {
                camelContext.addService(service);
            } catch (Exception e) {
                throw new RuntimeException("Unable to add service to Camel context", e);
            }
        };
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
                    LOG.info("Using custom default ThreadPoolProfile with id: {} and implementation: {}", entry.getKey(), profile);
                    camelContext.getExecutorServiceManager().setDefaultThreadPoolProfile(profile);
                    defaultIds.add(entry.getKey());
                } else {
                    camelContext.getExecutorServiceManager().registerThreadPoolProfile(profile);
                }
            }
        }

        // validate at most one is defined
        if (defaultIds.size() > 1) {
            throw new IllegalArgumentException("Only exactly one default ThreadPoolProfile is allowed, was " + defaultIds.size() + " ids: " + defaultIds);
        }
    }

}
