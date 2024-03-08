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
package org.apache.camel.spring.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.core.xml.CamelRouteControllerDefinition;
import org.apache.camel.core.xml.CamelStreamCachingStrategyDefinition;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.GlobalOptionsDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RestContextRefDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteConfigurationContextRefDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateContextRefDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformersDefinition;
import org.apache.camel.model.validator.ValidatorsDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.spi.BridgePropertyPlaceholderConfigurer;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.Lifecycle;
import org.springframework.context.Phased;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

/**
 * CamelContext using XML configuration.
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends AbstractCamelContextFactoryBean<SpringCamelContext>
        implements FactoryBean<SpringCamelContext>, InitializingBean, DisposableBean, ApplicationContextAware, Lifecycle,
        Phased, ApplicationListener<ContextRefreshedEvent>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextFactoryBean.class);

    @XmlAttribute(name = "depends-on")
    @Metadata(displayName = "Depends On")
    private String dependsOn;
    @XmlAttribute
    @Metadata(defaultValue = "Default")
    private StartupSummaryLevel startupSummaryLevel;
    @XmlAttribute
    private String trace;
    @XmlAttribute
    private String backlogTrace;
    @XmlAttribute
    private String tracePattern;
    @XmlAttribute
    private String traceLoggingFormat;
    @XmlAttribute
    private String debug;
    @XmlAttribute
    @Metadata(defaultValue = "false")
    private String messageHistory;
    @XmlAttribute
    @Metadata(defaultValue = "false")
    private String sourceLocationEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "false")
    private String logMask;
    @XmlAttribute
    private String logExhaustedMessageBody;
    @XmlAttribute
    private String streamCache;
    @XmlAttribute
    private String delayer;
    @XmlAttribute
    private String errorHandlerRef;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private String autoStartup;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private String shutdownEager;
    @XmlAttribute
    @Metadata(defaultValue = "false")
    private String dumpRoutes;
    @XmlAttribute
    @Metadata(displayName = "Use MDC Logging")
    private String useMDCLogging;
    @XmlAttribute
    @Metadata(displayName = "MDC Logging Keys Pattern")
    private String mdcLoggingKeysPattern;
    @XmlAttribute
    private String useDataType;
    @XmlAttribute
    private String useBreadcrumb;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private String beanPostProcessorEnabled;
    @XmlAttribute
    private String allowUseOriginalMessage;
    @XmlAttribute
    private String caseInsensitiveHeaders;
    @XmlAttribute
    private String autowiredEnabled;
    @XmlAttribute
    private String runtimeEndpointRegistryEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "#name#")
    private String managementNamePattern;
    @XmlAttribute
    @Metadata(defaultValue = "Camel (#camelId#) thread ##counter# - #name#")
    private String threadNamePattern;
    @XmlAttribute
    @Metadata(defaultValue = "Default")
    private ShutdownRoute shutdownRoute;
    @XmlAttribute
    @Metadata(defaultValue = "CompleteCurrentTaskOnly")
    private ShutdownRunningTask shutdownRunningTask;
    @XmlAttribute
    @Metadata(defaultValue = "false")
    private String loadTypeConverters;
    @XmlAttribute
    private String typeConverterStatisticsEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "false")
    private String loadHealthChecks;
    @XmlAttribute
    private String inflightRepositoryBrowseEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "Ignore")
    private TypeConverterExists typeConverterExists;
    @XmlAttribute
    @Metadata(defaultValue = "DEBUG")
    private LoggingLevel typeConverterExistsLoggingLevel;
    @XmlElement(name = "globalOptions")
    private GlobalOptionsDefinition globalOptions;
    @XmlElement(name = "propertyPlaceholder", type = CamelPropertyPlaceholderDefinition.class)
    private CamelPropertyPlaceholderDefinition camelPropertyPlaceholder;
    @XmlElement(name = "package")
    private String[] packages = {};
    @XmlElement(name = "packageScan", type = PackageScanDefinition.class)
    private PackageScanDefinition packageScan;
    @XmlElement(name = "contextScan", type = ContextScanDefinition.class)
    private ContextScanDefinition contextScan;
    @XmlElement(name = "streamCaching", type = CamelStreamCachingStrategyDefinition.class)
    private CamelStreamCachingStrategyDefinition camelStreamCachingStrategy;
    @XmlElement(name = "jmxAgent", type = CamelJMXAgentDefinition.class)
    @Metadata(displayName = "JMX Agent")
    private CamelJMXAgentDefinition camelJMXAgent;
    @XmlElement(name = "routeController", type = CamelRouteControllerDefinition.class)
    private CamelRouteControllerDefinition camelRouteController;
    @XmlElements({
            @XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class),
            @XmlElement(name = "fluentTemplate", type = CamelFluentProducerTemplateFactoryBean.class),
            @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class) })
    private List<AbstractCamelFactoryBean<?>> beansFactory;
    @XmlElements({
            @XmlElement(name = "errorHandler", type = SpringErrorHandlerDefinition.class) })
    private List<?> beans;
    @XmlElement(name = "defaultServiceCallConfiguration")
    private ServiceCallConfigurationDefinition defaultServiceCallConfiguration;
    @XmlElement(name = "serviceCallConfiguration", type = ServiceCallConfigurationDefinition.class)
    private List<ServiceCallConfigurationDefinition> serviceCallConfigurations;
    @XmlElement(name = "defaultResilience4jConfiguration")
    private Resilience4jConfigurationDefinition defaultResilience4jConfiguration;
    @XmlElement(name = "resilience4jConfiguration", type = Resilience4jConfigurationDefinition.class)
    private List<Resilience4jConfigurationDefinition> resilience4jConfigurations;
    @XmlElement(name = "defaultFaultToleranceConfiguration")
    private FaultToleranceConfigurationDefinition defaultFaultToleranceConfiguration;
    @XmlElement(name = "faultToleranceConfiguration", type = Resilience4jConfigurationDefinition.class)
    private List<FaultToleranceConfigurationDefinition> faultToleranceConfigurations;
    @XmlElement(name = "routeConfigurationContextRef")
    private List<RouteConfigurationContextRefDefinition> routeConfigurationRefs = new ArrayList<>();
    @XmlElement(name = "routeTemplateContextRef")
    private List<RouteTemplateContextRefDefinition> routeTemplateRefs = new ArrayList<>();
    @XmlElement(name = "routeBuilder")
    private List<RouteBuilderDefinition> builderRefs = new ArrayList<>();
    @XmlElement(name = "routeContextRef")
    private List<RouteContextRefDefinition> routeRefs = new ArrayList<>();
    @XmlElement(name = "restContextRef")
    private List<RestContextRefDefinition> restRefs = new ArrayList<>();
    @XmlElement(name = "threadPoolProfile")
    private List<ThreadPoolProfileDefinition> threadPoolProfiles;
    @XmlElement(name = "threadPool")
    private List<CamelThreadPoolFactoryBean> threadPools;
    @XmlElement(name = "endpoint")
    private List<CamelEndpointFactoryBean> endpoints;
    @XmlElement(name = "dataFormats")
    private DataFormatsDefinition dataFormats;
    @XmlElement(name = "transformers")
    private TransformersDefinition transformers;
    @XmlElement(name = "validators")
    private ValidatorsDefinition validators;
    @XmlElement(name = "redeliveryPolicyProfile")
    private List<CamelRedeliveryPolicyFactoryBean> redeliveryPolicies;
    @XmlElement(name = "onException")
    private List<OnExceptionDefinition> onExceptions = new ArrayList<>();
    @XmlElement(name = "onCompletion")
    private List<OnCompletionDefinition> onCompletions = new ArrayList<>();
    @XmlElement(name = "intercept")
    private List<InterceptDefinition> intercepts = new ArrayList<>();
    @XmlElement(name = "interceptFrom")
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<>();
    @XmlElement(name = "interceptSendToEndpoint")
    private List<InterceptSendToEndpointDefinition> interceptSendToEndpoints = new ArrayList<>();
    @XmlElement(name = "restConfiguration")
    private RestConfigurationDefinition restConfiguration;
    @XmlElement(name = "rest")
    private List<RestDefinition> rests = new ArrayList<>();
    @XmlElement(name = "routeConfiguration")
    private List<RouteConfigurationDefinition> routeConfigurations = new ArrayList<>();
    @XmlElement(name = "routeTemplate")
    private List<RouteTemplateDefinition> routeTemplates = new ArrayList<>();
    @XmlElement(name = "templatedRoute")
    private List<TemplatedRouteDefinition> templatedRoutes = new ArrayList<>();
    @XmlElement(name = "route")
    private List<RouteDefinition> routes = new ArrayList<>();
    @XmlTransient
    private SpringCamelContext context;
    @XmlTransient
    private ClassLoader contextClassLoaderOnStart;
    @XmlTransient
    private ApplicationContext applicationContext;
    @XmlTransient
    private BeanPostProcessor beanPostProcessor;
    @XmlTransient
    private boolean implicitId;

    @Override
    public Class<SpringCamelContext> getObjectType() {
        return SpringCamelContext.class;
    }

    @Override
    protected <S> S getBeanForType(Class<S> clazz) {
        S bean = null;
        String[] names = getApplicationContext().getBeanNamesForType(clazz, true, true);
        if (names.length == 1) {
            bean = getApplicationContext().getBean(names[0], clazz);
        }
        if (bean == null) {
            ApplicationContext parentContext = getApplicationContext().getParent();
            if (parentContext != null) {
                names = parentContext.getBeanNamesForType(clazz, true, true);
                if (names.length == 1) {
                    bean = parentContext.getBean(names[0], clazz);
                }
            }
        }
        return bean;
    }

    @Override
    protected void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders)
            throws Exception {
        // add filter to class resolver which then will filter
        PluginHelper.getPackageScanClassResolver(getContext()).addFilter(filter);

        PackageScanRouteBuilderFinder finder = new PackageScanRouteBuilderFinder(
                getContext(), packages, getContextClassLoaderOnStart(),
                getBeanPostProcessor(), PluginHelper.getPackageScanClassResolver(getContext()));
        finder.appendBuilders(builders);

        // and remove the filter
        PluginHelper.getPackageScanClassResolver(getContext()).removeFilter(filter);
    }

    @Override
    protected void findRouteBuildersByContextScan(
            PackageScanFilter filter, boolean includeNonSingletons, List<RoutesBuilder> builders)
            throws Exception {
        ContextScanRouteBuilderFinder finder = new ContextScanRouteBuilderFinder(getContext(), filter, includeNonSingletons);
        finder.appendBuilders(builders);
    }

    @Override
    protected void initBeanPostProcessor(SpringCamelContext context) {
        if (beanPostProcessor != null) {
            if (beanPostProcessor instanceof ApplicationContextAware) {
                ((ApplicationContextAware) beanPostProcessor).setApplicationContext(applicationContext);
            }
            if (beanPostProcessor instanceof CamelBeanPostProcessor) {
                ((CamelBeanPostProcessor) beanPostProcessor).setCamelContext(getContext());
            }
            // register the bean post processor on camel context
            if (beanPostProcessor instanceof org.apache.camel.spi.CamelBeanPostProcessor) {
                context.getCamelContextExtension().addContextPlugin(org.apache.camel.spi.CamelBeanPostProcessor.class,
                        (org.apache.camel.spi.CamelBeanPostProcessor) beanPostProcessor);
            }
        }
    }

    @Override
    protected void postProcessBeforeInit(RouteBuilder builder) {
        if (beanPostProcessor != null) {
            // Inject the annotated resource
            beanPostProcessor.postProcessBeforeInitialization(builder, builder.toString());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        StopWatch watch = new StopWatch();

        super.afterPropertiesSet();

        Boolean shutdownEager = CamelContextHelper.parseBoolean(getContext(), getShutdownEager());
        if (shutdownEager != null) {
            LOG.debug("Using shutdownEager: {}", shutdownEager);
            getContext().setShutdownEager(shutdownEager);
        }

        LOG.debug("afterPropertiesSet() took {} millis", watch.taken());
    }

    @Override
    protected void initCustomRegistry(SpringCamelContext context) {
        Registry registry = getBeanForType(Registry.class);
        if (registry != null) {
            LOG.info("Using custom Registry: {}", registry);
            context.getCamelContextExtension().setRegistry(registry);
        }
    }

    @Override
    protected void initPropertyPlaceholder() throws Exception {
        super.initPropertyPlaceholder();

        Map<String, BridgePropertyPlaceholderConfigurer> beans
                = applicationContext.getBeansOfType(BridgePropertyPlaceholderConfigurer.class);
        if (beans.size() == 1) {
            // setup properties component that uses this beans
            BridgePropertyPlaceholderConfigurer configurer = beans.values().iterator().next();
            String id = beans.keySet().iterator().next();
            LOG.info("Bridging Camel and Spring property placeholder configurer with id: {}", id);

            // get properties component
            PropertiesComponent pc = (PropertiesComponent) getContext().getPropertiesComponent();
            // use the spring system properties mode which has a different value than Camel may have
            pc.setSystemPropertiesMode(configurer.getSystemPropertiesMode());

            // replace existing resolver with us
            configurer.setParser(pc.getPropertiesParser());
            // use the bridge to handle the resolve and parsing
            pc.setPropertiesParser(configurer);
            // use the bridge as property source
            pc.addPropertiesSource(configurer);

        } else if (beans.size() > 1) {
            LOG.warn(
                    "Cannot bridge Camel and Spring property placeholders, as exact only 1 bean of type BridgePropertyPlaceholderConfigurer"
                     + " must be defined, was {} beans defined.",
                    beans.size());
        }
    }

    @Override
    public void start() {
        try {
            setupRoutes();
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
        }
        // when the routes are setup we need to start the Camel context
        context.start();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return context != null && context.isRunning();
    }

    @Override
    public int getPhase() {
        // the factory starts the context from
        // onApplicationEvent(ContextRefreshedEvent) so the phase we're
        // in only influences when the context is to be stopped, and
        // we want the CamelContext to be first in line to get stopped
        // if we wanted the phase to be considered while starting, we
        // would need to implement SmartLifecycle (see
        // DefaultLifecycleProcessor::startBeans)
        // we use LOWEST_PRECEDENCE here as this is taken into account
        // only when stopping and then in reversed order
        return LOWEST_PRECEDENCE - 1;
    }

    @Override
    public int getOrder() {
        // CamelContextFactoryBean implements Ordered so that it's the
        // second to last in ApplicationListener to receive events,
        // SpringCamelContext should be the last one, this is important
        // for startup as we want all resources to be ready and all
        // routes added to the context (see setupRoutes() and
        // org.apache.camel.spring.boot.RoutesCollector)
        return LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        // start the CamelContext when the Spring ApplicationContext is
        // done initializing, as the last step in ApplicationContext
        // being started/refreshed, there could be a race condition with
        // other ApplicationListeners that react to
        // ContextRefreshedEvent but this is the best that we can do
        if (event.getSource() instanceof ApplicationContext) {
            ApplicationContext appCtx = (ApplicationContext) event.getSource();
            if (appCtx.getId().endsWith(":management")) {
                //don't start camel context if
                //event is from the self management ApplicationContext
                return;
            }
        }
        start();
    }

    // Properties
    // -------------------------------------------------------------------------

    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalArgumentException("No applicationContext has been injected!");
        }
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setBeanPostProcessor(BeanPostProcessor postProcessor) {
        this.beanPostProcessor = postProcessor;
    }

    public BeanPostProcessor getBeanPostProcessor() {
        return beanPostProcessor;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Create the context
     */
    protected SpringCamelContext createContext() {
        SpringCamelContext ctx = newCamelContext();
        ctx.setApplicationContext(getApplicationContext());
        ctx.getCamelContextExtension().setName(getId());

        return ctx;
    }

    /**
     * Apply additional configuration to the context
     */
    protected void configure(SpringCamelContext ctx) {
        try {
            // allow any custom configuration, such as when running in camel-spring-boot
            if (applicationContext.containsBean("xmlCamelContextConfigurer")) {
                XmlCamelContextConfigurer configurer
                        = applicationContext.getBean("xmlCamelContextConfigurer", XmlCamelContextConfigurer.class);
                if (configurer != null) {
                    configurer.configure(applicationContext, ctx);
                }
            }
        } catch (Exception e) {
            // error during configuration
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    protected SpringCamelContext newCamelContext() {
        return new SpringCamelContext();
    }

    @Override
    public SpringCamelContext getContext(boolean create) {
        if (context == null && create) {
            context = createContext();
            configure(context);
            context.build();
        }
        return context;
    }

    public void setContext(SpringCamelContext context) {
        this.context = context;
    }

    @Override
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    /**
     * Contains the Camel routes
     */
    @Override
    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    @Override
    public List<RouteConfigurationDefinition> getRouteConfigurations() {
        return routeConfigurations;
    }

    /**
     * Contains the Camel route configurations
     */
    @Override
    public void setRouteConfigurations(List<RouteConfigurationDefinition> routeConfigurations) {
        this.routeConfigurations = routeConfigurations;
    }

    @Override
    public List<RouteTemplateDefinition> getRouteTemplates() {
        return routeTemplates;
    }

    /**
     * Contains the Camel route templates
     */
    @Override
    public void setRouteTemplates(List<RouteTemplateDefinition> routeTemplates) {
        this.routeTemplates = routeTemplates;
    }

    @Override
    public List<TemplatedRouteDefinition> getTemplatedRoutes() {
        return templatedRoutes;
    }

    /**
     * Contains the Camel templated routes
     */
    @Override
    public void setTemplatedRoutes(List<TemplatedRouteDefinition> templatedRoutes) {
        this.templatedRoutes = templatedRoutes;
    }

    @Override
    public List<RestDefinition> getRests() {
        return rests;
    }

    /**
     * Contains the rest services defined using the rest-dsl
     */
    @Override
    public void setRests(List<RestDefinition> rests) {
        this.rests = rests;
    }

    @Override
    public RestConfigurationDefinition getRestConfiguration() {
        return restConfiguration;
    }

    /**
     * Configuration for rest-dsl
     */
    public void setRestConfiguration(RestConfigurationDefinition restConfiguration) {
        this.restConfiguration = restConfiguration;
    }

    @Override
    public List<CamelEndpointFactoryBean> getEndpoints() {
        return endpoints;
    }

    /**
     * Configuration of endpoints
     */
    public void setEndpoints(List<CamelEndpointFactoryBean> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public List<CamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies() {
        return redeliveryPolicies;
    }

    @Override
    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    /**
     * Configuration of interceptors.
     */
    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    @Override
    public List<InterceptFromDefinition> getInterceptFroms() {
        return interceptFroms;
    }

    /**
     * Configuration of interceptors that triggers from the beginning of routes.
     */
    public void setInterceptFroms(List<InterceptFromDefinition> interceptFroms) {
        this.interceptFroms = interceptFroms;
    }

    @Override
    public List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints() {
        return interceptSendToEndpoints;
    }

    /**
     * Configuration of interceptors that triggers sending messages to endpoints.
     */
    public void setInterceptSendToEndpoints(List<InterceptSendToEndpointDefinition> interceptSendToEndpoints) {
        this.interceptSendToEndpoints = interceptSendToEndpoints;
    }

    @Override
    public GlobalOptionsDefinition getGlobalOptions() {
        return globalOptions;
    }

    /**
     * Configuration of CamelContext properties such as limit of debug logging and other general options.
     */
    public void setGlobalOptions(GlobalOptionsDefinition globalOptions) {
        this.globalOptions = globalOptions;
    }

    @Override
    public String[] getPackages() {
        return packages;
    }

    /**
     * Sets the package names to be recursively searched for Java classes which extend
     * {@link org.apache.camel.builder.RouteBuilder} to be auto-wired up to the {@link CamelContext} as a route. Note
     * that classes are excluded if they are specifically configured in the spring.xml
     * <p/>
     * A more advanced configuration can be done using
     * {@link #setPackageScan(org.apache.camel.model.PackageScanDefinition)}
     *
     * @param packages the package names which are recursively searched
     * @see            #setPackageScan(org.apache.camel.model.PackageScanDefinition)
     */
    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    @Override
    public PackageScanDefinition getPackageScan() {
        return packageScan;
    }

    /**
     * Sets the package scanning information. Package scanning allows for the automatic discovery of certain camel
     * classes at runtime for inclusion e.g. {@link org.apache.camel.builder.RouteBuilder} implementations
     *
     * @param packageScan the package scan
     */
    @Override
    public void setPackageScan(PackageScanDefinition packageScan) {
        this.packageScan = packageScan;
    }

    @Override
    public ContextScanDefinition getContextScan() {
        return contextScan;
    }

    /**
     * Sets the context scanning (eg Spring's ApplicationContext) information. Context scanning allows for the automatic
     * discovery of Camel routes runtime for inclusion e.g. {@link org.apache.camel.builder.RouteBuilder}
     * implementations
     *
     * @param contextScan the context scan
     */
    @Override
    public void setContextScan(ContextScanDefinition contextScan) {
        this.contextScan = contextScan;
    }

    @Override
    public CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder() {
        return camelPropertyPlaceholder;
    }

    /**
     * Configuration of property placeholder
     */
    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
    }

    @Override
    public CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy() {
        return camelStreamCachingStrategy;
    }

    /**
     * Configuration of stream caching.
     */
    public void setCamelStreamCachingStrategy(CamelStreamCachingStrategyDefinition camelStreamCachingStrategy) {
        this.camelStreamCachingStrategy = camelStreamCachingStrategy;
    }

    @Override
    public CamelRouteControllerDefinition getCamelRouteController() {
        return camelRouteController;
    }

    /**
     * Configuration of route controller.
     */
    public void setCamelRouteController(CamelRouteControllerDefinition camelRouteController) {
        this.camelRouteController = camelRouteController;
    }

    /**
     * Configuration of JMX Agent.
     */
    public void setCamelJMXAgent(CamelJMXAgentDefinition agent) {
        camelJMXAgent = agent;
    }

    @Override
    public String getTrace() {
        return trace;
    }

    /**
     * Sets whether tracing is enabled or not.
     *
     * To use tracing then this must be enabled on startup to be installed in the CamelContext.
     */
    public void setTrace(String trace) {
        this.trace = trace;
    }

    public StartupSummaryLevel getStartupSummaryLevel() {
        return startupSummaryLevel;
    }

    /**
     * Controls the level of information logged during startup (and shutdown) of CamelContext.
     */
    public void setStartupSummaryLevel(StartupSummaryLevel startupSummaryLevel) {
        this.startupSummaryLevel = startupSummaryLevel;
    }

    @Override
    public String getBacklogTrace() {
        return backlogTrace;
    }

    /**
     * Sets whether backlog tracing is enabled or not.
     *
     * To use backlog tracing then this must be enabled on startup to be installed in the CamelContext.
     */
    public void setBacklogTrace(String backlogTrace) {
        this.backlogTrace = backlogTrace;
    }

    @Override
    public String getDebug() {
        return debug;
    }

    /**
     * Sets whether debugging is enabled or not.
     *
     * To use debugging then this must be enabled on startup to be installed in the CamelContext.
     */
    public void setDebug(String debug) {
        this.debug = debug;
    }

    @Override
    public String getTracePattern() {
        return tracePattern;
    }

    /**
     * Tracing pattern to match which node EIPs to trace. For example to match all To EIP nodes, use to*. The pattern
     * matches by node and route id's Multiple patterns can be separated by comma.
     */
    public void setTracePattern(String tracePattern) {
        this.tracePattern = tracePattern;
    }

    @Override
    public String getTraceLoggingFormat() {
        return traceLoggingFormat;
    }

    /**
     * To use a custom tracing logging format.
     *
     * The default format (arrow, routeId, label) is: %-4.4s [%-12.12s] [%-33.33s]
     */
    public void setTraceLoggingFormat(String traceLoggingFormat) {
        this.traceLoggingFormat = traceLoggingFormat;
    }

    @Override
    public String getMessageHistory() {
        return messageHistory;
    }

    /**
     * Sets whether message history is enabled or not.
     */
    public void setMessageHistory(String messageHistory) {
        this.messageHistory = messageHistory;
    }

    @Override
    public String getSourceLocationEnabled() {
        return sourceLocationEnabled;
    }

    /**
     * Whether to capture precise source location:line-number for all EIPs in Camel routes.
     *
     * Enabling this will impact parsing Java based routes (also Groovy, Kotlin, etc.) on startup as this uses JDK
     * StackTraceElement to calculate the location from the Camel route, which comes with a performance cost. This only
     * impact startup, not the performance of the routes at runtime.
     */
    public void setSourceLocationEnabled(String sourceLocationEnabled) {
        this.sourceLocationEnabled = sourceLocationEnabled;
    }

    @Override
    public String getLogMask() {
        return logMask;
    }

    /**
     * Sets whether security mask for Logging is enabled or not.
     */
    public void setLogMask(String logMask) {
        this.logMask = logMask;
    }

    @Override
    public String getLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    /**
     * Sets whether to log exhausted message body with message history.
     */
    public void setLogExhaustedMessageBody(String logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    @Override
    public String getStreamCache() {
        return streamCache;
    }

    /**
     * Sets whether stream caching is enabled or not.
     */
    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    @Override
    public String getDelayer() {
        return delayer;
    }

    /**
     * Sets a delay value in millis that a message is delayed at every step it takes in the route path, slowing the
     * process down to better observe what is occurring
     */
    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    @Override
    public String getAutoStartup() {
        return autoStartup;
    }

    /**
     * Sets whether the object should automatically start when Camel starts.
     * <p/>
     * <b>Important:</b> Currently only routes can be disabled, as {@link CamelContext}s are always started. <br/>
     * <b>Note:</b> When setting auto startup <tt>false</tt> on {@link CamelContext} then that takes precedence and
     * <i>no</i> routes is started. You would need to start {@link CamelContext} explicit using the
     * {@link org.apache.camel.CamelContext#start()} method, to start the context, and then you would need to start the
     * routes manually using {@link org.apache.camel.spi.RouteController#startRoute(String)}.
     */
    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    public String getShutdownEager() {
        return shutdownEager;
    }

    /**
     * Whether to shutdown CamelContext eager when Spring is shutting down. This ensure a cleaner shutdown of Camel, as
     * dependent bean's are not shutdown at this moment. The bean's will then be shutdown after camelContext.
     */
    public void setShutdownEager(String shutdownEager) {
        this.shutdownEager = shutdownEager;
    }

    @Override
    public String getDumpRoutes() {
        return dumpRoutes;
    }

    /**
     * If dumping is enabled then Camel will during startup dump all loaded routes (incl rests and route templates)
     * represented as XML DSL into the log. This is intended for trouble shooting or to assist during development.
     *
     * Sensitive information that may be configured in the route endpoints could potentially be included in the dump
     * output and is therefore not recommended to be used for production usage.
     *
     * This requires to have camel-xml-jaxb on the classpath to be able to dump the routes as XML.
     */
    public void setDumpRoutes(String dumpRoutes) {
        this.dumpRoutes = dumpRoutes;
    }

    @Override
    public String getUseMDCLogging() {
        return useMDCLogging;
    }

    /**
     * Set whether <a href="http://www.slf4j.org/api/org/slf4j/MDC.html">MDC</a> is enabled.
     */
    public void setUseMDCLogging(String useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    public String getMDCLoggingKeysPattern() {
        return mdcLoggingKeysPattern;
    }

    /**
     * Sets the pattern used for determine which custom MDC keys to propagate during message routing when the routing
     * engine continues routing asynchronously for the given message. Setting this pattern to * will propagate all
     * custom keys. Or setting the pattern to foo*,bar* will propagate any keys starting with either foo or bar. Notice
     * that a set of standard Camel MDC keys are always propagated which starts with camel. as key name.
     *
     * The match rules are applied in this order (case insensitive):
     *
     * 1. exact match, returns true 2. wildcard match (pattern ends with a * and the name starts with the pattern),
     * returns true 3. regular expression match, returns true 4. otherwise returns false
     */
    public void setMDCLoggingKeysPattern(String mdcLoggingKeysPattern) {
        this.mdcLoggingKeysPattern = mdcLoggingKeysPattern;
    }

    @Override
    public String getUseDataType() {
        return useDataType;
    }

    /**
     * Whether to enable using data type on Camel messages.
     * <p/>
     * Data type are automatic turned on if:
     * <ul>
     * <li>one ore more routes has been explicit configured with input and output types</li>
     * <li>when using rest-dsl with binding turned on</li>
     * </ul>
     * Otherwise data type is default off.
     */
    public void setUseDataType(String useDataType) {
        this.useDataType = useDataType;
    }

    @Override
    public String getUseBreadcrumb() {
        return useBreadcrumb;
    }

    /**
     * Set whether breadcrumb is enabled.
     */
    public void setUseBreadcrumb(String useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    @Override
    public String getBeanPostProcessorEnabled() {
        return beanPostProcessorEnabled;
    }

    /**
     * Can be used to turn off bean post processing.
     *
     * Be careful to turn this off, as this means that beans that use Camel annotations such as
     * {@link org.apache.camel.EndpointInject}, {@link org.apache.camel.ProducerTemplate},
     * {@link org.apache.camel.Produce}, {@link org.apache.camel.Consume} etc will not be injected and in use.
     *
     * Turning this off should only be done if you are sure you do not use any of these Camel features.
     *
     * Not all runtimes allow turning this off (such as camel-blueprint or camel-cdi with XML).
     *
     * The default value is true (enabled).
     */
    public void setBeanPostProcessorEnabled(String beanPostProcessorEnabled) {
        this.beanPostProcessorEnabled = beanPostProcessorEnabled;
    }

    @Override
    public String getAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    /**
     * Sets whether to allow access to the original message from Camel's error handler, or from
     * {@link org.apache.camel.spi.UnitOfWork#getOriginalInMessage()}.
     * <p/>
     * Turning this off can optimize performance, as defensive copy of the original message is not needed.
     */
    public void setAllowUseOriginalMessage(String allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    @Override
    public String getCaseInsensitiveHeaders() {
        return caseInsensitiveHeaders;
    }

    /**
     * Whether to use case sensitive or insensitive headers.
     *
     * Important: When using case sensitive (this is set to false). Then the map is case sensitive which means headers
     * such as content-type and Content-Type are two different keys which can be a problem for some protocols such as
     * HTTP based, which rely on case insensitive headers. However case sensitive implementations can yield faster
     * performance. Therefore use case sensitive implementation with care.
     *
     * Default is true.
     */
    public void setCaseInsensitiveHeaders(String caseInsensitiveHeaders) {
        this.caseInsensitiveHeaders = caseInsensitiveHeaders;
    }

    @Override
    public String getAutowiredEnabled() {
        return autowiredEnabled;
    }

    /**
     * Whether autowiring is enabled. This is used for automatic autowiring options (the option must be marked as
     * autowired) by looking up in the registry to find if there is a single instance of matching type, which then gets
     * configured on the component. This can be used for automatic configuring JDBC data sources, JMS connection
     * factories, AWS Clients, etc.
     *
     * Default is true.
     */
    public void setAutowiredEnabled(String autowiredEnabled) {
        this.autowiredEnabled = autowiredEnabled;
    }

    @Override
    public String getRuntimeEndpointRegistryEnabled() {
        return runtimeEndpointRegistryEnabled;
    }

    /**
     * Sets whether {@link org.apache.camel.spi.RuntimeEndpointRegistry} is enabled.
     */
    public void setRuntimeEndpointRegistryEnabled(String runtimeEndpointRegistryEnabled) {
        this.runtimeEndpointRegistryEnabled = runtimeEndpointRegistryEnabled;
    }

    @Override
    public String getInflightRepositoryBrowseEnabled() {
        return inflightRepositoryBrowseEnabled;
    }

    /**
     * Sets whether the inflight repository should allow browsing each inflight exchange.
     *
     * This is by default disabled as there is a very slight performance overhead when enabled.
     */
    public void setInflightRepositoryBrowseEnabled(String inflightRepositoryBrowseEnabled) {
        this.inflightRepositoryBrowseEnabled = inflightRepositoryBrowseEnabled;
    }

    @Override
    public String getManagementNamePattern() {
        return managementNamePattern;
    }

    /**
     * The naming pattern for creating the CamelContext management name.
     */
    public void setManagementNamePattern(String managementNamePattern) {
        this.managementNamePattern = managementNamePattern;
    }

    @Override
    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    /**
     * Sets the thread name pattern used for creating the full thread name.
     * <p/>
     * The default pattern is: <tt>Camel (#camelId#) thread ##counter# - #name#</tt>
     * <p/>
     * Where <tt>#camelId#</tt> is the name of the {@link org.apache.camel.CamelContext} <br/>
     * and <tt>#counter#</tt> is a unique incrementing counter. <br/>
     * and <tt>#name#</tt> is the regular thread name. <br/>
     * You can also use <tt>#longName#</tt> is the long thread name which can includes endpoint parameters etc.
     */
    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
    }

    @Override
    public String getLoadTypeConverters() {
        return loadTypeConverters;
    }

    /**
     * Whether to load custom type converters by scanning classpath. This is used for backwards compatibility with Camel
     * 2.x. Its recommended to migrate to use fast type converter loading by setting @Converter(loader = true) on your
     * custom type converter classes.
     */
    public void setLoadTypeConverters(String loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    @Override
    public String getLoadHealthChecks() {
        return loadHealthChecks;
    }

    /**
     * Whether to load custom health checks by scanning classpath.
     */
    public void setLoadHealthChecks(String loadHealthChecks) {
        this.loadHealthChecks = loadHealthChecks;
    }

    @Override
    public String getTypeConverterStatisticsEnabled() {
        return typeConverterStatisticsEnabled;
    }

    /**
     * Sets whether or not type converter statistics is enabled.
     * <p/>
     * By default the type converter utilization statistics is disabled. <b>Notice:</b> If enabled then there is a
     * slight performance impact under very heavy load.
     * <p/>
     * You can enable/disable the statistics at runtime using the
     * {@link org.apache.camel.spi.TypeConverterRegistry#getStatistics()#setTypeConverterStatisticsEnabled(Boolean)}
     * method, or from JMX on the {@link org.apache.camel.api.management.mbean.ManagedTypeConverterRegistryMBean} mbean.
     */
    public void setTypeConverterStatisticsEnabled(String typeConverterStatisticsEnabled) {
        this.typeConverterStatisticsEnabled = typeConverterStatisticsEnabled;
    }

    @Override
    public TypeConverterExists getTypeConverterExists() {
        return typeConverterExists;
    }

    /**
     * What should happen when attempting to add a duplicate type converter.
     * <p/>
     * The default behavior is to ignore the duplicate.
     */
    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        this.typeConverterExists = typeConverterExists;
    }

    @Override
    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        return typeConverterExistsLoggingLevel;
    }

    /**
     * The logging level to use when logging that a type converter already exists when attempting to add a duplicate
     * type converter.
     * <p/>
     * The default logging level is <tt>DEBUG</tt>
     */
    public void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel) {
        this.typeConverterExistsLoggingLevel = typeConverterExistsLoggingLevel;
    }

    @Override
    public CamelJMXAgentDefinition getCamelJMXAgent() {
        return camelJMXAgent;
    }

    @Override
    public List<RouteConfigurationContextRefDefinition> getRouteConfigurationRefs() {
        return routeConfigurationRefs;
    }

    /**
     * Refers to XML route configurations to include as route configurations in this CamelContext.
     */
    public void setRouteConfigurationRefs(List<RouteConfigurationContextRefDefinition> routeConfigurationRefs) {
        this.routeConfigurationRefs = routeConfigurationRefs;
    }

    @Override
    public List<RouteTemplateContextRefDefinition> getRouteTemplateRefs() {
        return routeTemplateRefs;
    }

    /**
     * Refers to XML route templates to include as route templates in this CamelContext.
     */
    public void setRouteTemplateRefs(List<RouteTemplateContextRefDefinition> routeTemplateRefs) {
        this.routeTemplateRefs = routeTemplateRefs;
    }

    @Override
    public List<RouteBuilderDefinition> getBuilderRefs() {
        return builderRefs;
    }

    /**
     * Refers to Java {@link RouteBuilder} instances to include as routes in this CamelContext.
     */
    public void setBuilderRefs(List<RouteBuilderDefinition> builderRefs) {
        this.builderRefs = builderRefs;
    }

    @Override
    public List<RouteContextRefDefinition> getRouteRefs() {
        return routeRefs;
    }

    /**
     * Refers to XML routes to include as routes in this CamelContext.
     */
    public void setRouteRefs(List<RouteContextRefDefinition> routeRefs) {
        this.routeRefs = routeRefs;
    }

    @Override
    public List<RestContextRefDefinition> getRestRefs() {
        return restRefs;
    }

    /**
     * Refers to XML rest-dsl to include as REST services in this CamelContext.
     */
    public void setRestRefs(List<RestContextRefDefinition> restRefs) {
        this.restRefs = restRefs;
    }

    @Override
    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    /**
     * Sets the name of the error handler object used to default the error handling strategy
     */
    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
    }

    /**
     * Configuration of data formats.
     */
    public void setDataFormats(DataFormatsDefinition dataFormats) {
        this.dataFormats = dataFormats;
    }

    @Override
    public DataFormatsDefinition getDataFormats() {
        return dataFormats;
    }

    /**
     * Configuration of transformers.
     */
    public void setTransformers(TransformersDefinition transformers) {
        this.transformers = transformers;
    }

    @Override
    public TransformersDefinition getTransformers() {
        return transformers;
    }

    /**
     * Configuration of validators.
     */
    public void setValidators(ValidatorsDefinition validators) {
        this.validators = validators;
    }

    @Override
    public ValidatorsDefinition getValidators() {
        return validators;
    }

    /**
     * Configuration of redelivery settings.
     */
    public void setRedeliveryPolicies(List<CamelRedeliveryPolicyFactoryBean> redeliveryPolicies) {
        this.redeliveryPolicies = redeliveryPolicies;
    }

    @Override
    public List<AbstractCamelFactoryBean<?>> getBeansFactory() {
        return beansFactory;
    }

    /**
     * Miscellaneous configurations
     */
    public void setBeansFactory(List<AbstractCamelFactoryBean<?>> beansFactory) {
        this.beansFactory = beansFactory;
    }

    @Override
    public List<?> getBeans() {
        return beans;
    }

    /**
     * Miscellaneous configurations
     */
    public void setBeans(List<?> beans) {
        this.beans = beans;
    }

    @Override
    public ServiceCallConfigurationDefinition getDefaultServiceCallConfiguration() {
        return defaultServiceCallConfiguration;
    }

    /**
     * ServiceCall EIP default configuration
     */
    public void setDefaultServiceCallConfiguration(ServiceCallConfigurationDefinition defaultServiceCallConfiguration) {
        this.defaultServiceCallConfiguration = defaultServiceCallConfiguration;
    }

    @Override
    public List<ServiceCallConfigurationDefinition> getServiceCallConfigurations() {
        return serviceCallConfigurations;
    }

    /**
     * ServiceCall EIP configurations
     */
    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> serviceCallConfigurations) {
        this.serviceCallConfigurations = serviceCallConfigurations;
    }

    @Override
    public Resilience4jConfigurationDefinition getDefaultResilience4jConfiguration() {
        return defaultResilience4jConfiguration;
    }

    /**
     * Resilience4j EIP default configuration
     */
    public void setDefaultResilience4jConfiguration(Resilience4jConfigurationDefinition defaultResilience4jConfiguration) {
        this.defaultResilience4jConfiguration = defaultResilience4jConfiguration;
    }

    @Override
    public List<Resilience4jConfigurationDefinition> getResilience4jConfigurations() {
        return resilience4jConfigurations;
    }

    /**
     * Resilience4j Circuit Breaker EIP configurations
     */
    public void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> resilience4jConfigurations) {
        this.resilience4jConfigurations = resilience4jConfigurations;
    }

    @Override
    public FaultToleranceConfigurationDefinition getDefaultFaultToleranceConfiguration() {
        return defaultFaultToleranceConfiguration;
    }

    /**
     * MicroProfile Fault Tolerance EIP default configuration
     */
    public void setDefaultFaultToleranceConfiguration(
            FaultToleranceConfigurationDefinition defaultFaultToleranceConfiguration) {
        this.defaultFaultToleranceConfiguration = defaultFaultToleranceConfiguration;
    }

    @Override
    public List<FaultToleranceConfigurationDefinition> getFaultToleranceConfigurations() {
        return faultToleranceConfigurations;
    }

    /**
     * MicroProfile Circuit Breaker EIP configurations
     */
    public void setFaultToleranceConfigurations(List<FaultToleranceConfigurationDefinition> faultToleranceConfigurations) {
        this.faultToleranceConfigurations = faultToleranceConfigurations;
    }

    /**
     * Configuration of error handlers that triggers on exceptions thrown.
     */
    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    @Override
    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
    }

    @Override
    public List<OnCompletionDefinition> getOnCompletions() {
        return onCompletions;
    }

    /**
     * Configuration of sub routes to run at the completion of routing.
     */
    public void setOnCompletions(List<OnCompletionDefinition> onCompletions) {
        this.onCompletions = onCompletions;
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    /**
     * Sets the ShutdownRoute option for routes.
     */
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    /**
     * Sets the ShutdownRunningTask option to use when shutting down a route.
     */
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    @Override
    public List<ThreadPoolProfileDefinition> getThreadPoolProfiles() {
        return threadPoolProfiles;
    }

    /**
     * Configuration of thread pool profiles.
     */
    public void setThreadPoolProfiles(List<ThreadPoolProfileDefinition> threadPoolProfiles) {
        this.threadPoolProfiles = threadPoolProfiles;
    }

    public List<CamelThreadPoolFactoryBean> getThreadPools() {
        return threadPools;
    }

    /**
     * Configuration of thread pool
     */
    public void setThreadPools(List<CamelThreadPoolFactoryBean> threadPools) {
        this.threadPools = threadPools;
    }

    @Override
    public String getDependsOn() {
        return dependsOn;
    }

    /**
     * List of other bean id's this CamelContext depends up. Multiple bean id's can be separated by comma.
     */
    public void setDependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    public boolean isImplicitId() {
        return implicitId;
    }

    public void setImplicitId(boolean flag) {
        implicitId = flag;
    }
}
