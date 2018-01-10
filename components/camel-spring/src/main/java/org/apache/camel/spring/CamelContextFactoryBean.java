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
package org.apache.camel.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesLocation;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.core.xml.CamelProxyFactoryDefinition;
import org.apache.camel.core.xml.CamelServiceExporterDefinition;
import org.apache.camel.core.xml.CamelStreamCachingStrategyDefinition;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.GlobalOptionsDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.PropertiesDefinition;
import org.apache.camel.model.RestContextRefDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
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
import org.apache.camel.spring.spi.BridgePropertyPlaceholderConfigurer;
import org.apache.camel.spring.spi.XmlCamelContextConfigurer;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
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

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

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
    private String dependsOn;
    @XmlAttribute
    private String trace;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String messageHistory;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String logMask;
    @XmlAttribute
    private String logExhaustedMessageBody;
    @XmlAttribute
    private String streamCache;
    @XmlAttribute
    private String delayer;
    @XmlAttribute
    private String handleFault;
    @XmlAttribute
    private String errorHandlerRef;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String autoStartup;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String shutdownEager;
    @XmlAttribute @Metadata(defaultValue = "false")
    @Deprecated
    private String registerEndpointIdsFromRoute;
    @XmlAttribute
    private String useMDCLogging;
    @XmlAttribute
    private String useDataType;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String useBreadcrumb;
    @XmlAttribute
    private String allowUseOriginalMessage;
    @XmlAttribute
    private String runtimeEndpointRegistryEnabled;
    @XmlAttribute @Metadata(defaultValue = "#name#")
    private String managementNamePattern;
    @XmlAttribute @Metadata(defaultValue = "Camel (#camelId#) thread ##counter# - #name#")
    private String threadNamePattern;
    @XmlAttribute @Metadata(defaultValue = "Default")
    private ShutdownRoute shutdownRoute;
    @XmlAttribute @Metadata(defaultValue = "CompleteCurrentTaskOnly")
    private ShutdownRunningTask shutdownRunningTask;
    @XmlAttribute
    @Deprecated  @Metadata(defaultValue = "false")
    private Boolean lazyLoadTypeConverters;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean loadTypeConverters;
    @XmlAttribute
    private Boolean typeConverterStatisticsEnabled;
    @XmlAttribute @Metadata(defaultValue = "Override")
    private TypeConverterExists typeConverterExists;
    @XmlAttribute @Metadata(defaultValue = "WARN")
    private LoggingLevel typeConverterExistsLoggingLevel;
    @Deprecated
    @XmlElement(name = "properties")
    private PropertiesDefinition properties;
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
    private CamelJMXAgentDefinition camelJMXAgent;
    @XmlElements({
            @XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class),
            @XmlElement(name = "fluentTemplate", type = CamelFluentProducerTemplateFactoryBean.class),
            @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class)})
    private List<AbstractCamelFactoryBean<?>> beansFactory;
    @XmlElements({
        @XmlElement(name = "proxy", type = CamelProxyFactoryDefinition.class),
        @XmlElement(name = "export", type = CamelServiceExporterDefinition.class),
        @XmlElement(name = "errorHandler", type = ErrorHandlerDefinition.class) })
    private List<?> beans;
    @XmlElement(name = "defaultServiceCallConfiguration")
    private ServiceCallConfigurationDefinition defaultServiceCallConfiguration;
    @XmlElement(name = "serviceCallConfiguration", type = ServiceCallConfigurationDefinition.class)
    private List<ServiceCallConfigurationDefinition> serviceCallConfigurations;
    @XmlElement(name = "defaultHystrixConfiguration")
    private HystrixConfigurationDefinition defaultHystrixConfiguration;
    @XmlElement(name = "hystrixConfiguration", type = HystrixConfigurationDefinition.class)
    private List<HystrixConfigurationDefinition> hystrixConfigurations;
    @XmlElement(name = "routeBuilder")
    private List<RouteBuilderDefinition> builderRefs = new ArrayList<RouteBuilderDefinition>();
    @XmlElement(name = "routeContextRef")
    private List<RouteContextRefDefinition> routeRefs = new ArrayList<RouteContextRefDefinition>();
    @XmlElement(name = "restContextRef")
    private List<RestContextRefDefinition> restRefs = new ArrayList<RestContextRefDefinition>();
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
    private List<OnExceptionDefinition> onExceptions = new ArrayList<OnExceptionDefinition>();
    @XmlElement(name = "onCompletion")
    private List<OnCompletionDefinition> onCompletions = new ArrayList<OnCompletionDefinition>();
    @XmlElement(name = "intercept")
    private List<InterceptDefinition> intercepts = new ArrayList<InterceptDefinition>();
    @XmlElement(name = "interceptFrom")
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<InterceptFromDefinition>();
    @XmlElement(name = "interceptSendToEndpoint")
    private List<InterceptSendToEndpointDefinition> interceptSendToEndpoints = new ArrayList<InterceptSendToEndpointDefinition>();
    @XmlElement(name = "restConfiguration")
    private RestConfigurationDefinition restConfiguration;
    @XmlElement(name = "rest")
    private List<RestDefinition> rests = new ArrayList<RestDefinition>();
    @XmlElement(name = "route")
    private List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
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
    protected void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception {
        // add filter to class resolver which then will filter
        getContext().getPackageScanClassResolver().addFilter(filter);

        PackageScanRouteBuilderFinder finder = new PackageScanRouteBuilderFinder(getContext(), packages, getContextClassLoaderOnStart(),
                                                                                 getBeanPostProcessor(), getContext().getPackageScanClassResolver());
        finder.appendBuilders(builders);

        // and remove the filter
        getContext().getPackageScanClassResolver().removeFilter(filter);
    }

    @Override
    protected void findRouteBuildersByContextScan(PackageScanFilter filter, boolean includeNonSingletons, List<RoutesBuilder> builders) throws Exception {
        ContextScanRouteBuilderFinder finder = new ContextScanRouteBuilderFinder(getContext(), filter, includeNonSingletons);
        finder.appendBuilders(builders);
    }

    protected void initBeanPostProcessor(SpringCamelContext context) {
        if (beanPostProcessor != null) {
            if (beanPostProcessor instanceof ApplicationContextAware) {
                ((ApplicationContextAware) beanPostProcessor).setApplicationContext(applicationContext);
            }
            if (beanPostProcessor instanceof CamelBeanPostProcessor) {
                ((CamelBeanPostProcessor) beanPostProcessor).setCamelContext(getContext());
            }
        }
    }

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
            LOG.debug("Using shutdownEager: " + shutdownEager);
            getContext().setShutdownEager(shutdownEager);
        }

        LOG.debug("afterPropertiesSet() took {} millis", watch.stop());
    }

    protected void initCustomRegistry(SpringCamelContext context) {
        Registry registry = getBeanForType(Registry.class);
        if (registry != null) {
            LOG.info("Using custom Registry: " + registry);
            context.setRegistry(registry);
        }
    }

    @Override
    protected void initPropertyPlaceholder() throws Exception {
        super.initPropertyPlaceholder();

        Map<String, BridgePropertyPlaceholderConfigurer> beans = applicationContext.getBeansOfType(BridgePropertyPlaceholderConfigurer.class);
        if (beans.size() == 1) {
            // setup properties component that uses this beans
            BridgePropertyPlaceholderConfigurer configurer = beans.values().iterator().next();
            String id = beans.keySet().iterator().next();
            LOG.info("Bridging Camel and Spring property placeholder configurer with id: " + id);

            // get properties component
            PropertiesComponent pc = (PropertiesComponent) getContext().getComponent("properties", false);
            if (pc == null) {
                // do not auto create the component as spring autowrire by constructor causes a side effect when using bridge
                pc = new PropertiesComponent();
                getContext().addComponent("properties", pc);
            }
            // use the spring system properties mode which has a different value than Camel may have
            pc.setSystemPropertiesMode(configurer.getSystemPropertiesMode());
            // replace existing resolver with us
            configurer.setResolver(pc.getPropertiesResolver());
            configurer.setParser(pc.getPropertiesParser());
            // use the bridge to handle the resolve and parsing
            pc.setPropertiesResolver(configurer);
            pc.setPropertiesParser(configurer);

            // and update locations to have our as ref first
            List<PropertiesLocation> locations = new ArrayList<>(pc.getLocations());
            locations.add(0, new PropertiesLocation("ref", id));

            pc.setLocations(locations);
        } else if (beans.size() > 1) {
            LOG.warn("Cannot bridge Camel and Spring property placeholders, as exact only 1 bean of type BridgePropertyPlaceholderConfigurer"
                    + " must be defined, was {} beans defined.", beans.size());
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
        ctx.setName(getId());

        return ctx;
    }

    /**
     * Apply additional configuration to the context
     */
    protected void configure(SpringCamelContext ctx) {
        try {
            // allow any custom configuration, such as when running in camel-spring-boot
            if (applicationContext.containsBean("xmlCamelContextConfigurer")) {
                XmlCamelContextConfigurer configurer = applicationContext.getBean("xmlCamelContextConfigurer", XmlCamelContextConfigurer.class);
                if (configurer != null) {
                    configurer.configure(applicationContext, ctx);
                }
            }
        } catch (Exception e) {
            // error during configuration
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected SpringCamelContext newCamelContext() {
        return new SpringCamelContext(getApplicationContext());
    }

    public SpringCamelContext getContext(boolean create) {
        if (context == null && create) {
            context = createContext();
            configure(context);
        }
        return context;
    }

    public void setContext(SpringCamelContext context) {
        this.context = context;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    /**
     * Contains the Camel routes
     */
    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    public List<RestDefinition> getRests() {
        return rests;
    }

    /**
     * Contains the rest services defined using the rest-dsl
     */
    public void setRests(List<RestDefinition> rests) {
        this.rests = rests;
    }

    public RestConfigurationDefinition getRestConfiguration() {
        return restConfiguration;
    }

    /**
     * Configuration for rest-dsl
     */
    public void setRestConfiguration(RestConfigurationDefinition restConfiguration) {
        this.restConfiguration = restConfiguration;
    }

    public List<CamelEndpointFactoryBean> getEndpoints() {
        return endpoints;
    }

    /**
     * Configuration of endpoints
     */
    public void setEndpoints(List<CamelEndpointFactoryBean> endpoints) {
        this.endpoints = endpoints;
    }

    public List<CamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies() {
        return redeliveryPolicies;
    }

    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    /**
     * Configuration of interceptors.
     */
    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    public List<InterceptFromDefinition> getInterceptFroms() {
        return interceptFroms;
    }

    /**
     * Configuration of interceptors that triggers from the beginning of routes.
     */
    public void setInterceptFroms(List<InterceptFromDefinition> interceptFroms) {
        this.interceptFroms = interceptFroms;
    }

    public List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints() {
        return interceptSendToEndpoints;
    }

    /**
     * Configuration of interceptors that triggers sending messages to endpoints.
     */
    public void setInterceptSendToEndpoints(List<InterceptSendToEndpointDefinition> interceptSendToEndpoints) {
        this.interceptSendToEndpoints = interceptSendToEndpoints;
    }

    @Deprecated
    public PropertiesDefinition getProperties() {
        return properties;
    }

    @Override
    public GlobalOptionsDefinition getGlobalOptions() {
        return globalOptions;
    }

    /**
     * Configuration of CamelContext properties such as limit of debug logging
     * and other general options.
     * 
     * @deprecated Use {@link GlobalOptionsDefinition} instead.
     */
    @Deprecated
    public void setProperties(PropertiesDefinition properties) {
        this.properties = properties;
    }

    /**
     * Configuration of CamelContext properties such as limit of debug logging
     * and other general options.
     */
    public void setGlobalOptions(GlobalOptionsDefinition globalOptions) {
        this.globalOptions = globalOptions;
    }

    public String[] getPackages() {
        return packages;
    }

    /**
     * Sets the package names to be recursively searched for Java classes which
     * extend {@link org.apache.camel.builder.RouteBuilder} to be auto-wired up to the
     * {@link CamelContext} as a route. Note that classes are excluded if
     * they are specifically configured in the spring.xml
     * <p/>
     * A more advanced configuration can be done using {@link #setPackageScan(org.apache.camel.model.PackageScanDefinition)}
     *
     * @param packages the package names which are recursively searched
     * @see #setPackageScan(org.apache.camel.model.PackageScanDefinition)
     */
    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public PackageScanDefinition getPackageScan() {
        return packageScan;
    }

    /**
     * Sets the package scanning information. Package scanning allows for the
     * automatic discovery of certain camel classes at runtime for inclusion
     * e.g. {@link org.apache.camel.builder.RouteBuilder} implementations
     *
     * @param packageScan the package scan
     */
    public void setPackageScan(PackageScanDefinition packageScan) {
        this.packageScan = packageScan;
    }

    public ContextScanDefinition getContextScan() {
        return contextScan;
    }

    /**
     * Sets the context scanning (eg Spring's ApplicationContext) information.
     * Context scanning allows for the automatic discovery of Camel routes runtime for inclusion
     * e.g. {@link org.apache.camel.builder.RouteBuilder} implementations
     *
     * @param contextScan the context scan
     */
    public void setContextScan(ContextScanDefinition contextScan) {
        this.contextScan = contextScan;
    }

    public CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder() {
        return camelPropertyPlaceholder;
    }

    /**
     * Configuration of property placeholder
     */
    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
    }

    public CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy() {
        return camelStreamCachingStrategy;
    }

    /**
     * Configuration of stream caching.
     */
    public void setCamelStreamCachingStrategy(CamelStreamCachingStrategyDefinition camelStreamCachingStrategy) {
        this.camelStreamCachingStrategy = camelStreamCachingStrategy;
    }

    /**
     * Configuration of JMX.
     */
    public void setCamelJMXAgent(CamelJMXAgentDefinition agent) {
        camelJMXAgent = agent;
    }

    public String getTrace() {
        return trace;
    }

    /**
     * Sets whether tracing is enabled or not.
     */
    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String getMessageHistory() {
        return messageHistory;
    }

    /**
     * Sets whether message history is enabled or not.
     */
    public void setMessageHistory(String messageHistory) {
        this.messageHistory = messageHistory;
    }

    public String getLogMask() {
        return logMask;
    }

    /**
     * Sets whether security mask for Logging is enabled or not.
     */
    public void setLogMask(String logMask) {
        this.logMask = logMask;
    }

    public String getLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    /**
     * Sets whether to log exhausted message body with message history.
     */
    public void setLogExhaustedMessageBody(String logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public String getStreamCache() {
        return streamCache;
    }

    /**
     * Sets whether stream caching is enabled or not.
     */
    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    public String getDelayer() {
        return delayer;
    }

    /**
     * Sets a delay value in millis that a message is delayed at every step it takes in the route path,
     * slowing the process down to better observe what is occurring
     */
    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    public String getHandleFault() {
        return handleFault;
    }

    /**
     * Sets whether fault handling is enabled or not (default is disabled).
     */
    public void setHandleFault(String handleFault) {
        this.handleFault = handleFault;
    }

    public String getAutoStartup() {
        return autoStartup;
    }

    /**
     * Sets whether the object should automatically start when Camel starts.
     * <p/>
     * <b>Important:</b> Currently only routes can be disabled, as {@link CamelContext}s are always started.
     * <br/>
     * <b>Note:</b> When setting auto startup <tt>false</tt> on {@link CamelContext} then that takes precedence
     * and <i>no</i> routes is started. You would need to start {@link CamelContext} explicit using
     * the {@link org.apache.camel.CamelContext#start()} method, to start the context, and then
     * you would need to start the routes manually using {@link CamelContext#startRoute(String)}.
     */
    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    public String getShutdownEager() {
        return shutdownEager;
    }

    /**
     * Whether to shutdown CamelContext eager when Spring is shutting down.
     * This ensure a cleaner shutdown of Camel, as dependent bean's are not shutdown at this moment.
     * The bean's will then be shutdown after camelContext.
     */
    public void setShutdownEager(String shutdownEager) {
        this.shutdownEager = shutdownEager;
    }

    public String getRegisterEndpointIdsFromRoute() {
        return registerEndpointIdsFromRoute;
    }

    /**
     * Sets whether to register endpoints that has id attribute assigned in the Spring registry.
     * <p/>
     * This mode is by default false, but can be turned on for backwards compatibility.
     */
    public void setRegisterEndpointIdsFromRoute(String registerEndpointIdsFromRoute) {
        this.registerEndpointIdsFromRoute = registerEndpointIdsFromRoute;
    }

    public String getUseMDCLogging() {
        return useMDCLogging;
    }

    /**
     * Set whether <a href="http://www.slf4j.org/api/org/slf4j/MDC.html">MDC</a> is enabled.
     */
    public void setUseMDCLogging(String useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    public String getUseDataType() {
        return useDataType;
    }

    /**
     * Whether to enable using data type on Camel messages.
     * <p/>
     * Data type are automatic turned on if:
     * <ul>
     *   <li>one ore more routes has been explicit configured with input and output types</li>
     *   <li>when using rest-dsl with binding turned on</li>
     * </ul>
     * Otherwise data type is default off.
     */
    public void setUseDataType(String useDataType) {
        this.useDataType = useDataType;
    }

    public String getUseBreadcrumb() {
        return useBreadcrumb;
    }

    /**
     * Set whether breadcrumb is enabled.
     */
    public void setUseBreadcrumb(String useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    public String getAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    /**
     * Sets whether to allow access to the original message from Camel's error handler,
     * or from {@link org.apache.camel.spi.UnitOfWork#getOriginalInMessage()}.
     * <p/>
     * Turning this off can optimize performance, as defensive copy of the original message is not needed.
     */
    public void setAllowUseOriginalMessage(String allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    public String getRuntimeEndpointRegistryEnabled() {
        return runtimeEndpointRegistryEnabled;
    }

    /**
     * Sets whether {@link org.apache.camel.spi.RuntimeEndpointRegistry} is enabled.
     */
    public void setRuntimeEndpointRegistryEnabled(String runtimeEndpointRegistryEnabled) {
        this.runtimeEndpointRegistryEnabled = runtimeEndpointRegistryEnabled;
    }

    public String getManagementNamePattern() {
        return managementNamePattern;
    }

    /**
     * The naming pattern for creating the CamelContext management name.
     */
    public void setManagementNamePattern(String managementNamePattern) {
        this.managementNamePattern = managementNamePattern;
    }

    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    /**
     * Sets the thread name pattern used for creating the full thread name.
     * <p/>
     * The default pattern is: <tt>Camel (#camelId#) thread ##counter# - #name#</tt>
     * <p/>
     * Where <tt>#camelId#</tt> is the name of the {@link org.apache.camel.CamelContext}
     * <br/>and <tt>#counter#</tt> is a unique incrementing counter.
     * <br/>and <tt>#name#</tt> is the regular thread name.
     * <br/>You can also use <tt>#longName#</tt> is the long thread name which can includes endpoint parameters etc.
     */
    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
    }

    @Deprecated
    public Boolean getLazyLoadTypeConverters() {
        return lazyLoadTypeConverters;
    }

    /**
     * Sets whether type converters should be loaded lazy
     */
    @Deprecated
    public void setLazyLoadTypeConverters(Boolean lazyLoadTypeConverters) {
        this.lazyLoadTypeConverters = lazyLoadTypeConverters;
    }

    @Override
    public Boolean getLoadTypeConverters() {
        return loadTypeConverters;
    }

    /**
     * Sets whether to load custom type converters by scanning classpath.
     * This can be turned off if you are only using Camel components
     * that does not provide type converters which is needed at runtime.
     * In such situations setting this option to false, can speedup starting
     * Camel.
     *
     * @param loadTypeConverters whether to load custom type converters.
     */
    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    public Boolean getTypeConverterStatisticsEnabled() {
        return typeConverterStatisticsEnabled;
    }

    /**
     * Sets whether or not type converter statistics is enabled.
     * <p/>
     * By default the type converter utilization statistics is disabled.
     * <b>Notice:</b> If enabled then there is a slight performance impact under very heavy load.
     * <p/>
     * You can enable/disable the statistics at runtime using the
     * {@link org.apache.camel.spi.TypeConverterRegistry#getStatistics()#setTypeConverterStatisticsEnabled(Boolean)} method,
     * or from JMX on the {@link org.apache.camel.api.management.mbean.ManagedTypeConverterRegistryMBean} mbean.
     */
    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        this.typeConverterStatisticsEnabled = typeConverterStatisticsEnabled;
    }

    public TypeConverterExists getTypeConverterExists() {
        return typeConverterExists;
    }

    /**
     * What should happen when attempting to add a duplicate type converter.
     * <p/>
     * The default behavior is to override the existing.
     */
    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        this.typeConverterExists = typeConverterExists;
    }

    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        return typeConverterExistsLoggingLevel;
    }

    /**
     * The logging level to use when logging that a type converter already exists when attempting to add a duplicate type converter.
     * <p/>
     * The default logging level is <tt>WARN</tt>
     */
    public void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel) {
        this.typeConverterExistsLoggingLevel = typeConverterExistsLoggingLevel;
    }

    public CamelJMXAgentDefinition getCamelJMXAgent() {
        return camelJMXAgent;
    }

    public List<RouteBuilderDefinition> getBuilderRefs() {
        return builderRefs;
    }

    /**
     * Refers to Java {@link RouteBuilder} instances to include as routes in this CamelContext.
     */
    public void setBuilderRefs(List<RouteBuilderDefinition> builderRefs) {
        this.builderRefs = builderRefs;
    }

    public List<RouteContextRefDefinition> getRouteRefs() {
        return routeRefs;
    }

    /**
     * Refers to XML routes to include as routes in this CamelContext.
     */
    public void setRouteRefs(List<RouteContextRefDefinition> routeRefs) {
        this.routeRefs = routeRefs;
    }

    public List<RestContextRefDefinition> getRestRefs() {
        return restRefs;
    }

    /**
     * Refers to XML rest-dsl to include as REST services in this CamelContext.
     */
    public void setRestRefs(List<RestContextRefDefinition> restRefs) {
        this.restRefs = restRefs;
    }

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

    public DataFormatsDefinition getDataFormats() {
        return dataFormats;
    }

    /**
     * Configuration of transformers.
     */
    public void setTransformers(TransformersDefinition transformers) {
        this.transformers = transformers;
    }

    public TransformersDefinition getTransformers() {
        return transformers;
    }

    /**
     * Configuration of validators.
     */
    public void setValidators(ValidatorsDefinition validators) {
        this.validators = validators;
    }

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
    public List<HystrixConfigurationDefinition> getHystrixConfigurations() {
        return hystrixConfigurations;
    }

    @Override
    public HystrixConfigurationDefinition getDefaultHystrixConfiguration() {
        return defaultHystrixConfiguration;
    }

    /**
     * Hystrix EIP default configuration
     */
    public void setDefaultHystrixConfiguration(HystrixConfigurationDefinition defaultHystrixConfiguration) {
        this.defaultHystrixConfiguration = defaultHystrixConfiguration;
    }

    /**
     * Hystrix EIP configurations
     */
    public void setHystrixConfigurations(List<HystrixConfigurationDefinition> hystrixConfigurations) {
        this.hystrixConfigurations = hystrixConfigurations;
    }

    /**
     * Configuration of error handlers that triggers on exceptions thrown.
     */
    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
    }

    public List<OnCompletionDefinition> getOnCompletions() {
        return onCompletions;
    }

    /**
     * Configuration of sub routes to run at the completion of routing.
     */
    public void setOnCompletions(List<OnCompletionDefinition> onCompletions) {
        this.onCompletions = onCompletions;
    }

    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    /**
     * Sets the ShutdownRoute option for routes.
     */
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    /**
     * Sets the ShutdownRunningTask option to use when shutting down a route.
     */
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

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
