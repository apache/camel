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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverters;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.health.HealthCheckService;
import org.apache.camel.impl.CompositeRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.FileWatcherReloadStrategy;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.spi.XmlCamelContextConfigurer;
import org.apache.camel.support.jsse.GlobalSSLContextParametersSupplier;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.OrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

@Configuration
@EnableConfigurationProperties(CamelConfigurationProperties.class)
@Import(TypeConversionConfiguration.class)
public class CamelAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CamelAutoConfiguration.class);

    /**
     * Allows to do custom configuration when running XML based Camel in Spring Boot
     */
    // must be named xmlCamelContextConfigurer
    @Bean(name = "xmlCamelContextConfigurer")
    XmlCamelContextConfigurer springBootCamelContextConfigurer() {
        return new SpringBootXmlCamelContextConfigurer();
    }

    /**
     * Spring-aware Camel context for the application. Auto-detects and loads all routes available in the Spring context.
     */
    // We explicitly declare the destroyMethod to be "" as the Spring @Bean
    // annotation defaults to AbstractBeanDefinition.INFER_METHOD otherwise
    // and in that case CamelContext::shutdown or CamelContext::stop would
    // be used for bean destruction. As SpringCamelContext is a lifecycle
    // bean (implements Lifecycle) additional invocations of shutdown or
    // close would be superfluous.
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(CamelContext.class)
    CamelContext camelContext(ApplicationContext applicationContext,
                              CamelConfigurationProperties config) throws Exception {
        CamelContext camelContext = new SpringCamelContext(applicationContext);
        return doConfigureCamelContext(applicationContext, camelContext, config);
    }

    static CamelContext doConfigureCamelContext(ApplicationContext applicationContext,
                                         CamelContext camelContext,
                                         CamelConfigurationProperties config) throws Exception {

        if (camelContext instanceof DefaultCamelContext) {
            final DefaultCamelContext defaultContext = (DefaultCamelContext) camelContext;
            final Map<String, Registry> registryBeans = applicationContext.getBeansOfType(Registry.class);

            if (!registryBeans.isEmpty()) {
                final List<Registry> registries = new ArrayList<>(registryBeans.values());
                OrderComparator.sort(registries);
                final Registry registry = new CompositeRegistry(registries);
                defaultContext.setRegistry(registry);
            }
        }

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

        if (config.getLogDebugMaxChars() != 0) {
            camelContext.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "" + config.getLogDebugMaxChars());
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
        camelContext.setLogMask(config.isLogMask());
        camelContext.setLogExhaustedMessageBody(config.isLogExhaustedMessageBody());
        camelContext.setHandleFault(config.isHandleFault());
        camelContext.setAutoStartup(config.isAutoStartup());
        camelContext.setAllowUseOriginalMessage(config.isAllowUseOriginalMessage());
        camelContext.setUseBreadcrumb(config.isUseBreadcrumb());
        camelContext.setUseDataType(config.isUseDataType());
        camelContext.setUseMDCLogging(config.isUseMdcLogging());
        camelContext.setLoadTypeConverters(config.isLoadTypeConverters());

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            camelContext.getManagementStrategy().getManagementAgent().setEndpointRuntimeStatisticsEnabled(config.isEndpointRuntimeStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(config.getJmxManagementStatisticsLevel());
            camelContext.getManagementStrategy().getManagementAgent().setManagementNamePattern(config.getJmxManagementNamePattern());
            camelContext.getManagementStrategy().getManagementAgent().setCreateConnector(config.isJmxCreateConnector());
        }

        camelContext.setPackageScanClassResolver(new FatJarPackageScanClassResolver());

        // tracing
        camelContext.setTracing(config.isTracing());

        if (config.getXmlRoutesReloadDirectory() != null) {
            ReloadStrategy reload = new FileWatcherReloadStrategy(config.getXmlRoutesReloadDirectory());
            camelContext.setReloadStrategy(reload);
        }

        if (config.getThreadNamePattern() != null) {
            camelContext.getExecutorServiceManager().setThreadNamePattern(config.getThreadNamePattern());
        }

        // additional advanced configuration which is not configured using CamelConfigurationProperties
        afterPropertiesSet(applicationContext, camelContext);

        return camelContext;
    }

    @Bean
    CamelSpringBootApplicationController applicationController(ApplicationContext applicationContext, CamelContext camelContext) {
        return new CamelSpringBootApplicationController(applicationContext, camelContext);
    }

    @Bean
    @ConditionalOnMissingBean(RoutesCollector.class)
    RoutesCollector routesCollector(ApplicationContext applicationContext, CamelConfigurationProperties config) {
        Collection<CamelContextConfiguration> configurations = applicationContext.getBeansOfType(CamelContextConfiguration.class).values();
        return new RoutesCollector(applicationContext, new ArrayList<>(configurations), config);
    }

    /**
     * Default fluent producer template for the bootstrapped Camel context.
     * Create the bean lazy as it should only be created if its in-use.
     */
    // We explicitly declare the destroyMethod to be "" as the Spring @Bean
    // annotation defaults to AbstractBeanDefinition.INFER_METHOD otherwise
    // and in that case Service::close (FluentProducerTemplate implements Service)
    // would be used for bean destruction. And we want Camel to handle the
    // lifecycle.
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(FluentProducerTemplate.class)
    @Lazy
    FluentProducerTemplate fluentProducerTemplate(CamelContext camelContext,
                                                 CamelConfigurationProperties config) throws Exception {
        final FluentProducerTemplate fluentProducerTemplate = camelContext.createFluentProducerTemplate(config.getProducerTemplateCacheSize());
        // we add this fluentProducerTemplate as a Service to CamelContext so that it performs proper lifecycle (start and stop)
        camelContext.addService(fluentProducerTemplate);
        return fluentProducerTemplate;
    }

    /**
     * Default producer template for the bootstrapped Camel context.
     * Create the bean lazy as it should only be created if its in-use.
     */
    // We explicitly declare the destroyMethod to be "" as the Spring @Bean
    // annotation defaults to AbstractBeanDefinition.INFER_METHOD otherwise
    // and in that case Service::close (ProducerTemplate implements Service)
    // would be used for bean destruction. And we want Camel to handle the
    // lifecycle.
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(ProducerTemplate.class)
    @Lazy
    ProducerTemplate producerTemplate(CamelContext camelContext,
                                      CamelConfigurationProperties config) throws Exception {
        final ProducerTemplate producerTemplate = camelContext.createProducerTemplate(config.getProducerTemplateCacheSize());
        // we add this producerTemplate as a Service to CamelContext so that it performs proper lifecycle (start and stop)
        camelContext.addService(producerTemplate);
        return producerTemplate;
    }

    /**
     * Default consumer template for the bootstrapped Camel context.
     * Create the bean lazy as it should only be created if its in-use.
     */
    // We explicitly declare the destroyMethod to be "" as the Spring @Bean
    // annotation defaults to AbstractBeanDefinition.INFER_METHOD otherwise
    // and in that case Service::close (ConsumerTemplate implements Service)
    // would be used for bean destruction. And we want Camel to handle the
    // lifecycle.
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(ConsumerTemplate.class)
    @Lazy
    ConsumerTemplate consumerTemplate(CamelContext camelContext,
                                      CamelConfigurationProperties config) throws Exception {
        final ConsumerTemplate consumerTemplate = camelContext.createConsumerTemplate(config.getConsumerTemplateCacheSize());
        // we add this consumerTemplate as a Service to CamelContext so that it performs proper lifecycle (start and stop)
        camelContext.addService(consumerTemplate);
        return consumerTemplate;
    }

    // SpringCamelContext integration

    @Bean
    PropertiesParser propertiesParser() {
        return new SpringPropertiesParser();
    }

    // We explicitly declare the destroyMethod to be "" as the Spring @Bean
    // annotation defaults to AbstractBeanDefinition.INFER_METHOD otherwise
    // and in that case ShutdownableService::shutdown/Service::close
    // (PropertiesComponent extends ServiceSupport) would be used for bean
    // destruction. And we want Camel to handle the lifecycle.
    @Bean(destroyMethod = "")
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
    static void afterPropertiesSet(ApplicationContext applicationContext, CamelContext camelContext) throws Exception {
        final ManagementStrategy managementStrategy = camelContext.getManagementStrategy();

        registerPropertyForBeanType(applicationContext, BacklogTracer.class, camelContext::addInterceptStrategy);
        registerPropertyForBeanType(applicationContext, HandleFault.class, camelContext::addInterceptStrategy);
        registerPropertyForBeanType(applicationContext, InflightRepository.class, camelContext::setInflightRepository);
        registerPropertyForBeanType(applicationContext, AsyncProcessorAwaitManager.class, camelContext::setAsyncProcessorAwaitManager);
        registerPropertyForBeanType(applicationContext, ManagementStrategy.class, camelContext::setManagementStrategy);
        registerPropertyForBeanType(applicationContext, ManagementObjectNameStrategy.class, managementStrategy::setManagementObjectNameStrategy);
        registerPropertyForBeanType(applicationContext, EventFactory.class, managementStrategy::setEventFactory);
        registerPropertyForBeanType(applicationContext, UnitOfWorkFactory.class, camelContext::setUnitOfWorkFactory);
        registerPropertyForBeanType(applicationContext, RuntimeEndpointRegistry.class, camelContext::setRuntimeEndpointRegistry);

        registerPropertiesForBeanTypes(applicationContext, TypeConverters.class, camelContext.getTypeConverterRegistry()::addTypeConverters);

        final Predicate<EventNotifier> containsEventNotifier = managementStrategy.getEventNotifiers()::contains;
        registerPropertiesForBeanTypesWithCondition(applicationContext, EventNotifier.class, containsEventNotifier.negate(), managementStrategy::addEventNotifier);

        registerPropertiesForBeanTypes(applicationContext, EndpointStrategy.class, camelContext::addRegisterEndpointCallback);

        registerPropertyForBeanType(applicationContext, ShutdownStrategy.class, camelContext::setShutdownStrategy);
        
        final Predicate<InterceptStrategy> containsInterceptStrategy = camelContext.getInterceptStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(applicationContext, InterceptStrategy.class, containsInterceptStrategy.negate(), camelContext::addInterceptStrategy);

        final Predicate<LifecycleStrategy> containsLifecycleStrategy = camelContext.getLifecycleStrategies()::contains;
        registerPropertiesForBeanTypesWithCondition(applicationContext, LifecycleStrategy.class, containsLifecycleStrategy.negate(), camelContext::addLifecycleStrategy);

        registerPropertiesForBeanTypes(applicationContext, CamelClusterService.class, addServiceToContext(camelContext));

        // service registry
        Map<String, ServiceRegistry> serviceRegistries = applicationContext.getBeansOfType(ServiceRegistry.class);
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

        registerPropertiesForBeanTypes(applicationContext, RoutePolicyFactory.class, camelContext::addRoutePolicyFactory);

        // add SSL context parameters
        GlobalSSLContextParametersSupplier sslContextParametersSupplier = getSingleBeanOfType(applicationContext, GlobalSSLContextParametersSupplier.class);
        if (sslContextParametersSupplier != null) {
            camelContext.setSSLContextParameters(sslContextParametersSupplier.get());
        }
        // Health check registry
        HealthCheckRegistry healthCheckRegistry = getSingleBeanOfType(applicationContext, HealthCheckRegistry.class);
        if (healthCheckRegistry != null) {
            healthCheckRegistry.setCamelContext(camelContext);
            LOG.info("Using HealthCheckRegistry: {}", healthCheckRegistry);
            camelContext.setHealthCheckRegistry(healthCheckRegistry);
        } else {
            healthCheckRegistry = camelContext.getHealthCheckRegistry();
            healthCheckRegistry.setCamelContext(camelContext);
        }

        registerPropertiesForBeanTypes(applicationContext, HealthCheckRepository.class, healthCheckRegistry::addRepository);

        registerPropertyForBeanType(applicationContext, HealthCheckService.class, addServiceToContext(camelContext));
        registerPropertyForBeanType(applicationContext, RouteController.class, camelContext::setRouteController);
        registerPropertyForBeanType(applicationContext, UuidGenerator.class, camelContext::setUuidGenerator);

        final Predicate<LogListener> containsLogListener = camelContext.getLogListeners()::contains;
        registerPropertiesForBeanTypesWithCondition(applicationContext, LogListener.class, containsLogListener.negate(), camelContext::addLogListener);

        registerPropertyForBeanType(applicationContext, ExecutorServiceManager.class, camelContext::setExecutorServiceManager);

        // set the default thread pool profile if defined
        initThreadPoolProfiles(applicationContext, camelContext);
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

    private static <T> void registerPropertyForBeanType(final ApplicationContext applicationContext, final Class<T> beanType, final Consumer<T> propertySetter) {
        T propertyBean = getSingleBeanOfType(applicationContext, beanType);
        if (propertyBean == null) {
            return;
        }

        LOG.info("Using custom {}: {}", beanType.getSimpleName(), propertyBean);
        propertySetter.accept(propertyBean);
    }

    private static <T> void registerPropertiesForBeanTypes(final ApplicationContext applicationContext, final Class<T> beanType, final Consumer<T> propertySetter) {
        registerPropertiesForBeanTypesWithCondition(applicationContext, beanType, b -> true, propertySetter);
    }

    private static <T> void registerPropertiesForBeanTypesWithCondition(final ApplicationContext applicationContext, final Class<T> beanType, final Predicate<T> condition,
        final Consumer<T> propertySetter) {
        final Map<String, T> beans = applicationContext.getBeansOfType(beanType);
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
    
    private static void initThreadPoolProfiles(ApplicationContext applicationContext, CamelContext camelContext) {
        Set<String> defaultIds = new HashSet<>();

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

    private static <T> T getSingleBeanOfType(ApplicationContext applicationContext, Class<T> type) {
        Map<String, T> beans = applicationContext.getBeansOfType(type);
        if (beans.size() == 1) {
            return beans.values().iterator().next();
        } else {
            return null;
        }
    }

}
