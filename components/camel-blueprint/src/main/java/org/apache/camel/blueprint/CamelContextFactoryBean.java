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
package org.apache.camel.blueprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.aries.blueprint.ExtendedBeanMetadata;
import org.apache.aries.blueprint.ext.PropertyPlaceholderExt;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.core.osgi.OsgiCamelContextPublisher;
import org.apache.camel.core.osgi.OsgiEventAdminNotifier;
import org.apache.camel.core.osgi.utils.BundleDelegatingClassLoader;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
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
import org.apache.camel.model.Resilience4jConfigurationDefinition;
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
import org.apache.camel.util.StringHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bean to create and initialize a {@link BlueprintCamelContext} and install
 * routes either explicitly configured in Blueprint XML or found by searching
 * the classpath for Java classes which extend {@link RouteBuilder} using the
 * nested {@link #setPackages(String[])}.
 */
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends AbstractCamelContextFactoryBean<BlueprintCamelContext> {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextFactoryBean.class);

    @XmlAttribute(name = "depends-on")
    private String dependsOn;
    @XmlAttribute
    private String trace;
    @XmlAttribute
    private String backlogTrace;
    @XmlAttribute
    private String tracePattern;
    @XmlAttribute
    private String debug;
    @XmlAttribute
    private String messageHistory;
    @XmlAttribute
    private String logMask;
    @XmlAttribute
    private String logExhaustedMessageBody;
    @XmlAttribute
    private String streamCache = "false";
    @XmlAttribute
    private String delayer;
    @XmlAttribute
    private String errorHandlerRef;
    @XmlAttribute
    private String autoStartup = "true";
    @XmlAttribute
    private String useMDCLogging;
    @XmlAttribute
    private String mdcLoggingKeysPattern;
    @XmlAttribute
    private String useDataType;
    @XmlAttribute
    private String useBreadcrumb;
    @XmlAttribute
    private String allowUseOriginalMessage;
    @XmlAttribute
    private String caseInsensitiveHeaders;
    @XmlAttribute
    private String runtimeEndpointRegistryEnabled;
    @XmlAttribute
    private String managementNamePattern;
    @XmlAttribute
    private String threadNamePattern;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private Boolean useBlueprintPropertyResolver;
    @XmlAttribute
    private ShutdownRoute shutdownRoute;
    @XmlAttribute
    private ShutdownRunningTask shutdownRunningTask;
    @XmlAttribute
    private Boolean loadTypeConverters;
    @XmlAttribute
    private Boolean typeConverterStatisticsEnabled;
    @XmlAttribute
    private Boolean inflightRepositoryBrowseEnabled;
    @XmlAttribute
    private TypeConverterExists typeConverterExists;
    @XmlAttribute
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
    @XmlElement(name = "jmxAgent", type = CamelJMXAgentDefinition.class)
    private CamelJMXAgentDefinition camelJMXAgent;
    @XmlElement(name = "streamCaching", type = CamelStreamCachingStrategyDefinition.class)
    private CamelStreamCachingStrategyDefinition camelStreamCachingStrategy;
    @XmlElements({@XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class),
                  @XmlElement(name = "fluentTemplate", type = CamelFluentProducerTemplateFactoryBean.class),
                  @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class), @XmlElement(name = "proxy", type = CamelProxyFactoryBean.class),
                  @XmlElement(name = "errorHandler", type = CamelErrorHandlerFactoryBean.class)})
    private List<AbstractCamelFactoryBean<?>> beansFactory;
    @XmlElements({@XmlElement(name = "export", type = CamelServiceExporterDefinition.class)})
    private List<?> beans;
    @XmlElement(name = "defaultServiceCallConfiguration")
    private ServiceCallConfigurationDefinition defaultServiceCallConfiguration;
    @XmlElement(name = "serviceCallConfiguration", type = ServiceCallConfigurationDefinition.class)
    private List<ServiceCallConfigurationDefinition> serviceCallConfigurations;
    @XmlElement(name = "defaultHystrixConfiguration")
    private HystrixConfigurationDefinition defaultHystrixConfiguration;
    @XmlElement(name = "hystrixConfiguration", type = HystrixConfigurationDefinition.class)
    private List<HystrixConfigurationDefinition> hystrixConfigurations;
    @XmlElement(name = "defaultResilience4jConfiguration")
    private Resilience4jConfigurationDefinition defaultResilience4jConfiguration;
    @XmlElement(name = "resilience4jConfiguration", type = Resilience4jConfigurationDefinition.class)
    private List<Resilience4jConfigurationDefinition> resilience4jConfigurations;
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
    @XmlElement(name = "route")
    private List<RouteDefinition> routes = new ArrayList<>();
    @XmlTransient
    private BlueprintCamelContext context;
    @XmlTransient
    private BlueprintContainer blueprintContainer;
    @XmlTransient
    private BundleContext bundleContext;
    @XmlTransient
    private boolean implicitId;
    @XmlTransient
    private OsgiCamelContextPublisher osgiCamelContextPublisher;

    @Override
    public Class<BlueprintCamelContext> getObjectType() {
        return BlueprintCamelContext.class;
    }

    @Override
    public BlueprintCamelContext getContext(boolean create) {
        if (context == null && create) {
            context = createContext();
            if (!isImplicitId()) {
                context.setName(getId());
            }
        }
        return context;
    }

    public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
        this.blueprintContainer = blueprintContainer;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    protected BlueprintCamelContext createContext() {
        return new BlueprintCamelContext(bundleContext, blueprintContainer);
    }

    @Override
    protected void initCustomRegistry(BlueprintCamelContext context) {
        Registry registry = getBeanForType(Registry.class);
        if (registry != null) {
            LOG.info("Using custom Registry: {}", registry);
            context.setRegistry(registry);
        }
    }

    @Override
    protected <S> S getBeanForType(Class<S> clazz) {
        Collection<S> objects = BlueprintContainerBeanRepository.lookupByType(blueprintContainer, clazz).values();
        if (objects.size() == 1) {
            return objects.iterator().next();
        }
        return null;
    }

    @Override
    protected void initPropertyPlaceholder() throws Exception {
        super.initPropertyPlaceholder();

        // if blueprint property resolver is enabled on CamelContext then bridge
        // PropertiesComponent to blueprint
        if (isUseBlueprintPropertyResolver()) {
            // lookup existing configured properties component
            PropertiesComponent pc = (PropertiesComponent)getContext().getPropertiesComponent();

            // any extra properties
            ServiceReference<?> ref = bundleContext.getServiceReference(PropertiesComponent.OVERRIDE_PROPERTIES);
            if (ref != null) {
                Properties extra = (Properties)bundleContext.getService(ref);
                if (extra != null) {
                    pc.setOverrideProperties(extra);
                }
            }

            List<String> ids = new ArrayList<>();
            for (String bp : pc.getLocations()) {
                String resolver = StringHelper.before(bp, ":");
                String path = StringHelper.after(bp, ":");
                if ("blueprint".equals(resolver)) {
                    ids.add(path);
                }
            }
            if (ids.isEmpty()) {
                // no blueprint locations has been set, so auto-detect the
                // blueprint property placeholders to use (convention over
                // configuration)
                ids = lookupPropertyPlaceholderIds();
            }
            pc.addPropertiesSource(new BlueprintPropertiesSource(blueprintContainer, ids));
        }
    }

    /**
     * Lookup the ids of the Blueprint property placeholder services in the
     * Blueprint container.
     *
     * @return the ids, will be an empty if none found.
     */
    private List<String> lookupPropertyPlaceholderIds() {
        List<String> ids = new ArrayList<>();

        for (Object componentId : blueprintContainer.getComponentIds()) {
            String id = (String)componentId;
            ComponentMetadata meta = blueprintContainer.getComponentMetadata(id);
            if (meta instanceof ExtendedBeanMetadata) {
                Class<?> clazz = ((ExtendedBeanMetadata)meta).getRuntimeClass();
                if (clazz != null && PropertyPlaceholderExt.class.isAssignableFrom(clazz)) {
                    ids.add(id);
                }
            }
        }

        return ids;
    }

    @Override
    protected void initBeanPostProcessor(BlueprintCamelContext context) {
    }

    @Override
    protected void postProcessBeforeInit(RouteBuilder builder) {
    }

    @Override
    protected void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception {
        // add filter to class resolver which then will filter
        getContext().getPackageScanClassResolver().addFilter(filter);
        ClassLoader classLoader = new BundleDelegatingClassLoader(bundleContext.getBundle());
        PackageScanRouteBuilderFinder finder = new PackageScanRouteBuilderFinder(getContext(), packages, classLoader, getContext().getPackageScanClassResolver());
        finder.appendBuilders(builders);

        // and remove the filter
        getContext().getPackageScanClassResolver().removeFilter(filter);
    }

    @Override
    protected void findRouteBuildersByContextScan(PackageScanFilter filter, boolean includeNonSingletons, List<RoutesBuilder> builders) throws Exception {
        ContextScanRouteBuilderFinder finder = new ContextScanRouteBuilderFinder(getContext(), filter, includeNonSingletons);
        finder.appendBuilders(builders);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        // setup the application context classloader with the bundle delegating
        // classloader
        ClassLoader cl = new BundleDelegatingClassLoader(bundleContext.getBundle());
        LOG.debug("Set the application context classloader to: {}", cl);
        getContext().setApplicationContextClassLoader(cl);
        osgiCamelContextPublisher = new OsgiCamelContextPublisher(bundleContext);
        osgiCamelContextPublisher.start();
        getContext().getManagementStrategy().addEventNotifier(osgiCamelContextPublisher);
        try {
            getClass().getClassLoader().loadClass("org.osgi.service.event.EventAdmin");
            getContext().getManagementStrategy().addEventNotifier(new OsgiEventAdminNotifier(bundleContext));
        } catch (Throwable t) {
            // Ignore, if the EventAdmin package is not available, just don't
            // use it
            LOG.debug("EventAdmin package is not available, just don't use it");
        }
        // ensure routes is setup
        setupRoutes();
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        if (osgiCamelContextPublisher != null) {
            osgiCamelContextPublisher.shutdown();
        }
    }

    @Override
    public String getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    @Override
    public String getAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    @Override
    public String getUseMDCLogging() {
        return useMDCLogging;
    }

    public void setUseMDCLogging(String useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    @Override
    public String getMDCLoggingKeysPattern() {
        return mdcLoggingKeysPattern;
    }

    public void setMDCLoggingKeysPattern(String mdcLoggingKeysPattern) {
        this.mdcLoggingKeysPattern = mdcLoggingKeysPattern;
    }

    @Override
    public String getUseDataType() {
        return useDataType;
    }

    public void setUseDataType(String useDataType) {
        this.useDataType = useDataType;
    }

    @Override
    public String getUseBreadcrumb() {
        return useBreadcrumb;
    }

    public void setUseBreadcrumb(String useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    @Override
    public String getAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    public void setAllowUseOriginalMessage(String allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    @Override
    public String getCaseInsensitiveHeaders() {
        return caseInsensitiveHeaders;
    }

    public void setCaseInsensitiveHeaders(String caseInsensitiveHeaders) {
        this.caseInsensitiveHeaders = caseInsensitiveHeaders;
    }

    @Override
    public String getRuntimeEndpointRegistryEnabled() {
        return runtimeEndpointRegistryEnabled;
    }

    public void setRuntimeEndpointRegistryEnabled(String runtimeEndpointRegistryEnabled) {
        this.runtimeEndpointRegistryEnabled = runtimeEndpointRegistryEnabled;
    }

    @Override
    public String getManagementNamePattern() {
        return managementNamePattern;
    }

    public void setManagementNamePattern(String managementNamePattern) {
        this.managementNamePattern = managementNamePattern;
    }

    @Override
    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
    }

    @Override
    public Boolean getLoadTypeConverters() {
        return loadTypeConverters;
    }

    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    @Override
    public Boolean getTypeConverterStatisticsEnabled() {
        return typeConverterStatisticsEnabled;
    }

    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        this.typeConverterStatisticsEnabled = typeConverterStatisticsEnabled;
    }

    @Override
    public TypeConverterExists getTypeConverterExists() {
        return typeConverterExists;
    }

    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        this.typeConverterExists = typeConverterExists;
    }

    @Override
    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        return typeConverterExistsLoggingLevel;
    }

    public void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel) {
        this.typeConverterExistsLoggingLevel = typeConverterExistsLoggingLevel;
    }

    @Override
    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    @Override
    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    @Override
    public CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder() {
        return camelPropertyPlaceholder;
    }

    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
    }

    @Override
    public List<RouteContextRefDefinition> getRouteRefs() {
        return routeRefs;
    }

    public void setRouteRefs(List<RouteContextRefDefinition> routeRefs) {
        this.routeRefs = routeRefs;
    }

    @Override
    public List<RestContextRefDefinition> getRestRefs() {
        return restRefs;
    }

    public void setRestRefs(List<RestContextRefDefinition> restRefs) {
        this.restRefs = restRefs;
    }

    @Override
    public List<CamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies() {
        return redeliveryPolicies;
    }

    public void setRedeliveryPolicies(List<CamelRedeliveryPolicyFactoryBean> redeliveryPolicies) {
        this.redeliveryPolicies = redeliveryPolicies;
    }

    @Override
    public List<ThreadPoolProfileDefinition> getThreadPoolProfiles() {
        return threadPoolProfiles;
    }

    public void setThreadPoolProfiles(List<ThreadPoolProfileDefinition> threadPoolProfiles) {
        this.threadPoolProfiles = threadPoolProfiles;
    }

    public List<CamelThreadPoolFactoryBean> getThreadPools() {
        return threadPools;
    }

    public void setThreadPools(List<CamelThreadPoolFactoryBean> threadPools) {
        this.threadPools = threadPools;
    }

    @Override
    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    @Override
    public String getBacklogTrace() {
        return backlogTrace;
    }

    /**
     * Sets whether backlog tracing is enabled or not.
     */
    public void setBacklogTrace(String backlogTrace) {
        this.backlogTrace = backlogTrace;
    }

    @Override
    public String getTracePattern() {
        return tracePattern;
    }

    public void setTracePattern(String tracePattern) {
        this.tracePattern = tracePattern;
    }

    @Override
    public String getDebug() {
        return debug;
    }

    /**
     * Sets whether debugging is enabled or not.
     */
    public void setDebug(String debug) {
        this.debug = debug;
    }

    @Override
    public String getMessageHistory() {
        return messageHistory;
    }

    public void setMessageHistory(String messageHistory) {
        this.messageHistory = messageHistory;
    }

    @Override
    public String getLogMask() {
        return logMask;
    }

    public void setLogMask(String logMask) {
        this.logMask = logMask;
    }

    @Override
    public String getLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    public void setLogExhaustedMessageBody(String logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    @Override
    public String getStreamCache() {
        return streamCache;
    }

    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    @Override
    public String getDelayer() {
        return delayer;
    }

    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    @Override
    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
    }

    @Override
    public GlobalOptionsDefinition getGlobalOptions() {
        return globalOptions;
    }

    public void setGlobalOptions(GlobalOptionsDefinition globalOptions) {
        this.globalOptions = globalOptions;
    }

    @Override
    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    @Override
    public PackageScanDefinition getPackageScan() {
        return packageScan;
    }

    @Override
    public void setPackageScan(PackageScanDefinition packageScan) {
        this.packageScan = packageScan;
    }

    @Override
    public ContextScanDefinition getContextScan() {
        return contextScan;
    }

    @Override
    public void setContextScan(ContextScanDefinition contextScan) {
        this.contextScan = contextScan;
    }

    @Override
    public CamelJMXAgentDefinition getCamelJMXAgent() {
        return camelJMXAgent;
    }

    public void setCamelJMXAgent(CamelJMXAgentDefinition camelJMXAgent) {
        this.camelJMXAgent = camelJMXAgent;
    }

    @Override
    public CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy() {
        return camelStreamCachingStrategy;
    }

    public void setCamelStreamCachingStrategy(CamelStreamCachingStrategyDefinition camelStreamCachingStrategy) {
        this.camelStreamCachingStrategy = camelStreamCachingStrategy;
    }

    @Override
    public List<AbstractCamelFactoryBean<?>> getBeansFactory() {
        return beansFactory;
    }

    public void setBeansFactory(List<AbstractCamelFactoryBean<?>> beansFactory) {
        this.beansFactory = beansFactory;
    }

    @Override
    public List<?> getBeans() {
        return beans;
    }

    public void setBeans(List<?> beans) {
        this.beans = beans;
    }

    @Override
    public ServiceCallConfigurationDefinition getDefaultServiceCallConfiguration() {
        return defaultServiceCallConfiguration;
    }

    public void setDefaultServiceCallConfiguration(ServiceCallConfigurationDefinition defaultServiceCallConfiguration) {
        this.defaultServiceCallConfiguration = defaultServiceCallConfiguration;
    }

    @Override
    public List<ServiceCallConfigurationDefinition> getServiceCallConfigurations() {
        return serviceCallConfigurations;
    }

    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> serviceCallConfigurations) {
        this.serviceCallConfigurations = serviceCallConfigurations;
    }

    @Override
    public HystrixConfigurationDefinition getDefaultHystrixConfiguration() {
        return defaultHystrixConfiguration;
    }

    public void setDefaultHystrixConfiguration(HystrixConfigurationDefinition defaultHystrixConfiguration) {
        this.defaultHystrixConfiguration = defaultHystrixConfiguration;
    }

    @Override
    public List<HystrixConfigurationDefinition> getHystrixConfigurations() {
        return hystrixConfigurations;
    }

    public void setHystrixConfigurations(List<HystrixConfigurationDefinition> hystrixConfigurations) {
        this.hystrixConfigurations = hystrixConfigurations;
    }

    @Override
    public Resilience4jConfigurationDefinition getDefaultResilience4jConfiguration() {
        return defaultResilience4jConfiguration;
    }

    public void setDefaultResilience4jConfiguration(Resilience4jConfigurationDefinition defaultResilience4jConfiguration) {
        this.defaultResilience4jConfiguration = defaultResilience4jConfiguration;
    }

    @Override
    public List<Resilience4jConfigurationDefinition> getResilience4jConfigurations() {
        return resilience4jConfigurations;
    }

    public void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> resilience4jConfigurations) {
        this.resilience4jConfigurations = resilience4jConfigurations;
    }

    @Override
    public List<RouteBuilderDefinition> getBuilderRefs() {
        return builderRefs;
    }

    public void setBuilderRefs(List<RouteBuilderDefinition> builderRefs) {
        this.builderRefs = builderRefs;
    }

    @Override
    public List<CamelEndpointFactoryBean> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<CamelEndpointFactoryBean> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public DataFormatsDefinition getDataFormats() {
        return dataFormats;
    }

    public void setDataFormats(DataFormatsDefinition dataFormats) {
        this.dataFormats = dataFormats;
    }

    public void setTransformers(TransformersDefinition transformers) {
        this.transformers = transformers;
    }

    @Override
    public TransformersDefinition getTransformers() {
        return transformers;
    }

    public void setValidators(ValidatorsDefinition validators) {
        this.validators = validators;
    }

    @Override
    public ValidatorsDefinition getValidators() {
        return validators;
    }

    @Override
    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
    }

    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    @Override
    public List<OnCompletionDefinition> getOnCompletions() {
        return onCompletions;
    }

    public void setOnCompletions(List<OnCompletionDefinition> onCompletions) {
        this.onCompletions = onCompletions;
    }

    @Override
    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    @Override
    public List<InterceptFromDefinition> getInterceptFroms() {
        return interceptFroms;
    }

    public void setInterceptFroms(List<InterceptFromDefinition> interceptFroms) {
        this.interceptFroms = interceptFroms;
    }

    @Override
    public List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints() {
        return interceptSendToEndpoints;
    }

    public void setInterceptSendToEndpoints(List<InterceptSendToEndpointDefinition> interceptSendToEndpoints) {
        this.interceptSendToEndpoints = interceptSendToEndpoints;
    }

    @Override
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    @Override
    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    @Override
    public List<RestDefinition> getRests() {
        return rests;
    }

    @Override
    public void setRests(List<RestDefinition> rests) {
        this.rests = rests;
    }

    @Override
    public RestConfigurationDefinition getRestConfiguration() {
        return restConfiguration;
    }

    public void setRestConfiguration(RestConfigurationDefinition restConfiguration) {
        this.restConfiguration = restConfiguration;
    }

    public boolean isImplicitId() {
        return implicitId;
    }

    public void setImplicitId(boolean flag) {
        implicitId = flag;
    }

    public Boolean getUseBlueprintPropertyResolver() {
        return useBlueprintPropertyResolver;
    }

    /**
     * Whether to automatic detect OSGi Blueprint property placeholder service in use,
     * and bridge with Camel property placeholder.
     * When enabled this allows you to only setup OSGi Blueprint property placeholder
     * and Camel can use the properties in the <camelContext>.
     */
    public void setUseBlueprintPropertyResolver(Boolean useBlueprintPropertyResolver) {
        this.useBlueprintPropertyResolver = useBlueprintPropertyResolver;
    }

    public boolean isUseBlueprintPropertyResolver() {
        // enable by default
        return useBlueprintPropertyResolver == null || useBlueprintPropertyResolver.booleanValue();
    }

    @Override
    public Boolean getInflightRepositoryBrowseEnabled() {
        return inflightRepositoryBrowseEnabled;
    }

    public void setInflightRepositoryBrowseEnabled(Boolean inflightRepositoryBrowseEnabled) {
        this.inflightRepositoryBrowseEnabled = inflightRepositoryBrowseEnabled;
    }
}
