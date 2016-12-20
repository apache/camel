/**
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
package org.apache.camel.spring.boot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverters;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.impl.FileWatcherReloadStrategy;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.processor.interceptor.DefaultTraceFormatter;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

@Configuration
@EnableConfigurationProperties(CamelConfigurationProperties.class)
@Import(TypeConversionConfiguration.class)
public class CamelAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CamelAutoConfiguration.class);

    /**
     * Spring-aware Camel context for the application. Auto-detects and loads all routes available in the Spring context.
     */
    @Bean
    @ConditionalOnMissingBean(CamelContext.class)
    CamelContext camelContext(ApplicationContext applicationContext,
                              CamelConfigurationProperties config) {

        if (ObjectHelper.isNotEmpty(config.getFileConfigurations())) {
            Environment env = applicationContext.getEnvironment();
            if (env instanceof ConfigurableEnvironment) {
                MutablePropertySources sources = ((ConfigurableEnvironment) env).getPropertySources();
                if (sources != null) {
                    if (!sources.contains("camel-file-configuration")) {
                        sources.addFirst(new FilePropertySource("camel-file-configuration", applicationContext, config.getFileConfigurations()));
                    }
                }
            }
        }

        CamelContext camelContext = new SpringCamelContext(applicationContext);
        SpringCamelContext.setNoStart(true);

        if (!config.isJmxEnabled()) {
            camelContext.disableJMX();
        }

        if (config.getName() != null) {
            ((SpringCamelContext) camelContext).setName(config.getName());
        }

        if (config.getShutdownTimeout() > 0) {
            camelContext.getShutdownStrategy().setTimeout(config.getShutdownTimeout());
        }
        camelContext.getShutdownStrategy().setSuppressLoggingOnTimeout(config.isShutdownSuppressLoggingOnTimeout());
        camelContext.getShutdownStrategy().setShutdownNowOnTimeout(config.isShutdownNowOnTimeout());
        camelContext.getShutdownStrategy().setShutdownRoutesInReverseOrder(config.isShutdownRoutesInReverseOrder());
        camelContext.getShutdownStrategy().setLogInflightExchangesOnTimeout(config.isShutdownLogInflightExchangesOnTimeout());

        if (config.getLogDebugMaxChars() > 0) {
            camelContext.getProperties().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "" + config.getLogDebugMaxChars());
        }

        // stream caching
        camelContext.setStreamCaching(config.isStreamCachingEnabled());
        camelContext.getStreamCachingStrategy().setAnySpoolRules(config.isStreamCachingAnySpoolRules());
        camelContext.getStreamCachingStrategy().setBufferSize(config.getStreamCachingBufferSize());
        camelContext.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(config.isStreamCachingRemoveSpoolDirectoryWhenStopping());
        camelContext.getStreamCachingStrategy().setSpoolChiper(config.getStreamCachingSpoolChiper());
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
        camelContext.setLogExhaustedMessageBody(config.isLogExhaustedMessageBody());
        camelContext.setHandleFault(config.isHandleFault());
        camelContext.setAutoStartup(config.isAutoStartup());
        camelContext.setAllowUseOriginalMessage(config.isAllowUseOriginalMessage());

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            camelContext.getManagementStrategy().getManagementAgent().setEndpointRuntimeStatisticsEnabled(config.isEndpointRuntimeStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(config.getJmxManagementStatisticsLevel());
            camelContext.getManagementStrategy().getManagementAgent().setManagementNamePattern(config.getJmxManagementNamePattern());
            camelContext.getManagementStrategy().getManagementAgent().setCreateConnector(config.isJmxCreateConnector());
        }

        camelContext.setPackageScanClassResolver(new FatJarPackageScanClassResolver());

        // tracing
        camelContext.setTracing(config.isTracing());
        if (camelContext.getDefaultTracer() instanceof Tracer) {
            Tracer tracer = (Tracer) camelContext.getDefaultTracer();
            if (tracer.getDefaultTraceFormatter() != null) {
                DefaultTraceFormatter formatter = tracer.getDefaultTraceFormatter();
                if (config.getTracerFormatterBreadCrumbLength() != null) {
                    formatter.setBreadCrumbLength(config.getTracerFormatterBreadCrumbLength());
                }
                if (config.getTracerFormatterMaxChars() != null) {
                    formatter.setMaxChars(config.getTracerFormatterMaxChars());
                }
                if (config.getTracerFormatterNodeLength() != null) {
                    formatter.setNodeLength(config.getTracerFormatterNodeLength());
                }
                formatter.setShowBody(config.isTraceFormatterShowBody());
                formatter.setShowBodyType(config.isTracerFormatterShowBodyType());
                formatter.setShowBreadCrumb(config.isTraceFormatterShowBreadCrumb());
                formatter.setShowException(config.isTraceFormatterShowException());
                formatter.setShowExchangeId(config.isTraceFormatterShowExchangeId());
                formatter.setShowExchangePattern(config.isTraceFormatterShowExchangePattern());
                formatter.setShowHeaders(config.isTraceFormatterShowHeaders());
                formatter.setShowNode(config.isTraceFormatterShowNode());
                formatter.setShowProperties(config.isTraceFormatterShowProperties());
                formatter.setShowRouteId(config.isTraceFormatterShowRouteId());
                formatter.setShowShortExchangeId(config.isTraceFormatterShowShortExchangeId());
            }
        }

        if (config.getXmlRoutesReloadDirectory() != null) {
            ReloadStrategy reload = new FileWatcherReloadStrategy(config.getXmlRoutesReloadDirectory());
            camelContext.setReloadStrategy(reload);
        }

        // additional advanced configuration which is not configured using CamelConfigurationProperties
        afterPropertiesSet(applicationContext, camelContext);

        return camelContext;
    }

    @Bean
    CamelSpringBootApplicationController applicationController(ApplicationContext applicationContext, CamelContext camelContext) {
        // CAMEL-10279: We have to call setNoStart(true) here so that if a <camelContext> is imported via
        // @ImportResource then it does not get started before the RoutesCollector gets a chance to add any
        // routes found in RouteBuilders.  Even if no RouteBuilders are found, the RoutesCollector will handle
        // starting the the Camel Context.
        SpringCamelContext.setNoStart(true);
        return new CamelSpringBootApplicationController(applicationContext, camelContext);
    }

    @Bean
    @ConditionalOnMissingBean(RoutesCollector.class)
    RoutesCollector routesCollector(ApplicationContext applicationContext, CamelConfigurationProperties config) {
        Collection<CamelContextConfiguration> configurations = applicationContext.getBeansOfType(CamelContextConfiguration.class).values();
        return new RoutesCollector(applicationContext, new ArrayList<CamelContextConfiguration>(configurations), config);
    }

    /**
     * Default producer template for the bootstrapped Camel context.
     */
    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    @ConditionalOnMissingBean(ProducerTemplate.class)
    ProducerTemplate producerTemplate(CamelContext camelContext,
                                      CamelConfigurationProperties config) {
        return camelContext.createProducerTemplate(config.getProducerTemplateCacheSize());
    }

    /**
     * Default consumer template for the bootstrapped Camel context.
     */
    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    @ConditionalOnMissingBean(ConsumerTemplate.class)
    ConsumerTemplate consumerTemplate(CamelContext camelContext,
                                      CamelConfigurationProperties config) {
        return camelContext.createConsumerTemplate(config.getConsumerTemplateCacheSize());
    }

    // SpringCamelContext integration

    @Bean
    PropertiesParser propertiesParser() {
        return new SpringPropertiesParser();
    }

    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    PropertiesComponent properties(CamelContext camelContext, PropertiesParser parser) {
        if (camelContext.hasComponent("properties") != null) {
            return camelContext.getComponent("properties", PropertiesComponent.class);
        } else {
            PropertiesComponent pc = new PropertiesComponent();
            pc.setPropertiesParser(parser);
            return pc;
        }
    }

    /**
     * Camel post processor - required to support Camel annotations.
     */
    @Bean
    CamelBeanPostProcessor camelBeanPostProcessor(ApplicationContext applicationContext) {
        CamelBeanPostProcessor processor = new CamelBeanPostProcessor();
        processor.setApplicationContext(applicationContext);
        return processor;
    }

    /**
     * Performs additional configuration to lookup beans of Camel types to configure
     * advanced configurations.
     * <p/>
     * Similar code in camel-core-xml module in class org.apache.camel.core.xml.AbstractCamelContextFactoryBean.
     */
    void afterPropertiesSet(ApplicationContext applicationContext, CamelContext camelContext) {
        Tracer tracer = getSingleBeanOfType(applicationContext, Tracer.class);
        if (tracer != null) {
            // use formatter if there is a TraceFormatter bean defined
            TraceFormatter formatter = getSingleBeanOfType(applicationContext, TraceFormatter.class);
            if (formatter != null) {
                tracer.setFormatter(formatter);
            }
            LOG.info("Using custom Tracer: {}", tracer);
            camelContext.addInterceptStrategy(tracer);
        }
        BacklogTracer backlogTracer = getSingleBeanOfType(applicationContext, BacklogTracer.class);
        if (backlogTracer != null) {
            LOG.info("Using custom BacklogTracer: {}", backlogTracer);
            camelContext.addInterceptStrategy(backlogTracer);
        }
        HandleFault handleFault = getSingleBeanOfType(applicationContext, HandleFault.class);
        if (handleFault != null) {
            LOG.info("Using custom HandleFault: {}", handleFault);
            camelContext.addInterceptStrategy(handleFault);
        }
        InflightRepository inflightRepository = getSingleBeanOfType(applicationContext, InflightRepository.class);
        if (inflightRepository != null) {
            LOG.info("Using custom InflightRepository: {}", inflightRepository);
            camelContext.setInflightRepository(inflightRepository);
        }
        AsyncProcessorAwaitManager asyncProcessorAwaitManager = getSingleBeanOfType(applicationContext, AsyncProcessorAwaitManager.class);
        if (asyncProcessorAwaitManager != null) {
            LOG.info("Using custom AsyncProcessorAwaitManager: {}", asyncProcessorAwaitManager);
            camelContext.setAsyncProcessorAwaitManager(asyncProcessorAwaitManager);
        }
        ManagementStrategy managementStrategy = getSingleBeanOfType(applicationContext, ManagementStrategy.class);
        if (managementStrategy != null) {
            LOG.info("Using custom ManagementStrategy: {}", managementStrategy);
            camelContext.setManagementStrategy(managementStrategy);
        }
        ManagementNamingStrategy managementNamingStrategy = getSingleBeanOfType(applicationContext, ManagementNamingStrategy.class);
        if (managementNamingStrategy != null) {
            LOG.info("Using custom ManagementNamingStrategy: {}", managementNamingStrategy);
            camelContext.getManagementStrategy().setManagementNamingStrategy(managementNamingStrategy);
        }
        EventFactory eventFactory = getSingleBeanOfType(applicationContext, EventFactory.class);
        if (eventFactory != null) {
            LOG.info("Using custom EventFactory: {}", eventFactory);
            camelContext.getManagementStrategy().setEventFactory(eventFactory);
        }
        UnitOfWorkFactory unitOfWorkFactory = getSingleBeanOfType(applicationContext, UnitOfWorkFactory.class);
        if (unitOfWorkFactory != null) {
            LOG.info("Using custom UnitOfWorkFactory: {}", unitOfWorkFactory);
            camelContext.setUnitOfWorkFactory(unitOfWorkFactory);
        }
        RuntimeEndpointRegistry runtimeEndpointRegistry = getSingleBeanOfType(applicationContext, RuntimeEndpointRegistry.class);
        if (runtimeEndpointRegistry != null) {
            LOG.info("Using custom RuntimeEndpointRegistry: {}", runtimeEndpointRegistry);
            camelContext.setRuntimeEndpointRegistry(runtimeEndpointRegistry);
        }
        // custom type converters defined as <bean>s
        Map<String, TypeConverters> typeConverters = applicationContext.getBeansOfType(TypeConverters.class);
        if (typeConverters != null && !typeConverters.isEmpty()) {
            for (Map.Entry<String, TypeConverters> entry : typeConverters.entrySet()) {
                TypeConverters converter = entry.getValue();
                LOG.info("Adding custom TypeConverters with id: {} and implementation: {}", entry.getKey(), converter);
                camelContext.getTypeConverterRegistry().addTypeConverters(converter);
            }
        }
        // set the event notifier strategies if defined
        Map<String, EventNotifier> eventNotifiers = applicationContext.getBeansOfType(EventNotifier.class);
        if (eventNotifiers != null && !eventNotifiers.isEmpty()) {
            for (Map.Entry<String, EventNotifier> entry : eventNotifiers.entrySet()) {
                EventNotifier notifier = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!camelContext.getManagementStrategy().getEventNotifiers().contains(notifier)) {
                    LOG.info("Using custom EventNotifier with id: {} and implementation: {}", entry.getKey(), notifier);
                    camelContext.getManagementStrategy().addEventNotifier(notifier);
                }
            }
        }
        // set endpoint strategies if defined
        Map<String, EndpointStrategy> endpointStrategies = applicationContext.getBeansOfType(EndpointStrategy.class);
        if (endpointStrategies != null && !endpointStrategies.isEmpty()) {
            for (Map.Entry<String, EndpointStrategy> entry : endpointStrategies.entrySet()) {
                EndpointStrategy strategy = entry.getValue();
                LOG.info("Using custom EndpointStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                camelContext.addRegisterEndpointCallback(strategy);
            }
        }
        // shutdown
        ShutdownStrategy shutdownStrategy = getSingleBeanOfType(applicationContext, ShutdownStrategy.class);
        if (shutdownStrategy != null) {
            LOG.info("Using custom ShutdownStrategy: " + shutdownStrategy);
            camelContext.setShutdownStrategy(shutdownStrategy);
        }
        // add global interceptors
        Map<String, InterceptStrategy> interceptStrategies = applicationContext.getBeansOfType(InterceptStrategy.class);
        if (interceptStrategies != null && !interceptStrategies.isEmpty()) {
            for (Map.Entry<String, InterceptStrategy> entry : interceptStrategies.entrySet()) {
                InterceptStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!camelContext.getInterceptStrategies().contains(strategy)) {
                    LOG.info("Using custom InterceptStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                    camelContext.addInterceptStrategy(strategy);
                }
            }
        }
        // set the lifecycle strategy if defined
        Map<String, LifecycleStrategy> lifecycleStrategies = applicationContext.getBeansOfType(LifecycleStrategy.class);
        if (lifecycleStrategies != null && !lifecycleStrategies.isEmpty()) {
            for (Map.Entry<String, LifecycleStrategy> entry : lifecycleStrategies.entrySet()) {
                LifecycleStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!camelContext.getLifecycleStrategies().contains(strategy)) {
                    LOG.info("Using custom LifecycleStrategy with id: {} and implementation: {}", entry.getKey(), strategy);
                    camelContext.addLifecycleStrategy(strategy);
                }
            }
        }
        // add route policy factories
        Map<String, RoutePolicyFactory> routePolicyFactories = applicationContext.getBeansOfType(RoutePolicyFactory.class);
        if (routePolicyFactories != null && !routePolicyFactories.isEmpty()) {
            for (Map.Entry<String, RoutePolicyFactory> entry : routePolicyFactories.entrySet()) {
                RoutePolicyFactory factory = entry.getValue();
                LOG.info("Using custom RoutePolicyFactory with id: {} and implementation: {}", entry.getKey(), factory);
                camelContext.addRoutePolicyFactory(factory);
            }
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(applicationContext, camelContext);
    }

    private void initThreadPoolProfiles(ApplicationContext applicationContext, CamelContext camelContext) {
        Set<String> defaultIds = new HashSet<String>();

        // lookup and use custom profiles from the registry
        Map<String, ThreadPoolProfile> profiles = applicationContext.getBeansOfType(ThreadPoolProfile.class);
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

    private <T> T getSingleBeanOfType(ApplicationContext applicationContext, Class<T> type) {
        Map<String, T> beans = applicationContext.getBeansOfType(type);
        if (beans.size() == 1) {
            return beans.values().iterator().next();
        } else {
            return null;
        }
    }

}
