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
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.core.xml.CamelServiceExporterDefinition;
import org.apache.camel.core.xml.CamelStreamCachingStrategyDefinition;
import org.apache.camel.model.ContextScanDefinition;
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
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.Registry;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bean to create and initialize a {@link BlueprintCamelContext}
 * and install routes either explicitly configured in
 * Blueprint XML or found by searching the classpath for Java classes which extend
 * {@link RouteBuilder} using the nested {@link #setPackages(String[])}.
 *
 * @version 
 */
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends AbstractCamelContextFactoryBean<BlueprintCamelContext> {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextFactoryBean.class);

    @XmlAttribute(name = "depends-on", required = false)
    private String dependsOn;
    @XmlAttribute(required = false)
    private String trace;
    @XmlAttribute(required = false)
    private String messageHistory;
    @XmlAttribute(required = false)
    private String logExhaustedMessageBody;
    @XmlAttribute(required = false)
    private String streamCache = "false";
    @XmlAttribute(required = false)
    private String delayer;
    @XmlAttribute(required = false)
    private String handleFault;
    @XmlAttribute(required = false)
    private String errorHandlerRef;
    @XmlAttribute(required = false)
    private String autoStartup = "true";
    @XmlAttribute(required = false)
    private String useMDCLogging;
    @XmlAttribute(required = false)
    private String useBreadcrumb;
    @XmlAttribute(required = false)
    private String allowUseOriginalMessage;
    @XmlAttribute(required = false)
    private String runtimeEndpointRegistryEnabled;
    @XmlAttribute(required = false)
    private String managementNamePattern;
    @XmlAttribute(required = false)
    private String threadNamePattern;
    @XmlAttribute(required = false)
    private Boolean useBlueprintPropertyResolver;
    @XmlAttribute(required = false)
    private ShutdownRoute shutdownRoute;
    @XmlAttribute(required = false)
    private ShutdownRunningTask shutdownRunningTask;
    @XmlAttribute(required = false)
    @Deprecated
    private Boolean lazyLoadTypeConverters;
    @XmlAttribute(required = false)
    private Boolean typeConverterStatisticsEnabled;
    @XmlAttribute(required = false)
    private TypeConverterExists typeConverterExists;
    @XmlAttribute(required = false)
    private LoggingLevel typeConverterExistsLoggingLevel;
    @XmlElement(name = "properties", required = false)
    private PropertiesDefinition properties;
    @XmlElement(name = "propertyPlaceholder", type = CamelPropertyPlaceholderDefinition.class, required = false)
    private CamelPropertyPlaceholderDefinition camelPropertyPlaceholder;
    @XmlElement(name = "package", required = false)
    private String[] packages = {};
    @XmlElement(name = "packageScan", type = PackageScanDefinition.class, required = false)
    private PackageScanDefinition packageScan;
    @XmlElement(name = "contextScan", type = ContextScanDefinition.class, required = false)
    private ContextScanDefinition contextScan;
    @XmlElement(name = "jmxAgent", type = CamelJMXAgentDefinition.class, required = false)
    private CamelJMXAgentDefinition camelJMXAgent;
    @XmlElement(name = "streamCaching", type = CamelStreamCachingStrategyDefinition.class, required = false)
    private CamelStreamCachingStrategyDefinition camelStreamCachingStrategy;
    @XmlElements({
        @XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class, required = false),
        @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class, required = false),
        @XmlElement(name = "proxy", type = CamelProxyFactoryBean.class, required = false),
        @XmlElement(name = "export", type = CamelServiceExporterDefinition.class, required = false),
        @XmlElement(name = "errorHandler", type = CamelErrorHandlerFactoryBean.class, required = false)})
    private List<?> beans;
    @XmlElement(name = "routeBuilder", required = false)
    private List<RouteBuilderDefinition> builderRefs = new ArrayList<RouteBuilderDefinition>();
    @XmlElement(name = "routeContextRef", required = false)
    private List<RouteContextRefDefinition> routeRefs = new ArrayList<RouteContextRefDefinition>();
    @XmlElement(name = "restContextRef", required = false)
    private List<RestContextRefDefinition> restRefs = new ArrayList<RestContextRefDefinition>();
    @XmlElement(name = "threadPoolProfile", required = false)
    private List<ThreadPoolProfileDefinition> threadPoolProfiles;
    @XmlElement(name = "threadPool", required = false)
    private List<CamelThreadPoolFactoryBean> threadPools;
    @XmlElement(name = "endpoint", required = false)
    private List<CamelEndpointFactoryBean> endpoints;
    @XmlElement(name = "dataFormats", required = false)
    private DataFormatsDefinition dataFormats;
    @XmlElement(name = "redeliveryPolicyProfile", required = false)
    private List<CamelRedeliveryPolicyFactoryBean> redeliveryPolicies;
    @XmlElement(name = "onException", required = false)
    private List<OnExceptionDefinition> onExceptions = new ArrayList<OnExceptionDefinition>();
    @XmlElement(name = "onCompletion", required = false)
    private List<OnCompletionDefinition> onCompletions = new ArrayList<OnCompletionDefinition>();
    @XmlElement(name = "intercept", required = false)
    private List<InterceptDefinition> intercepts = new ArrayList<InterceptDefinition>();
    @XmlElement(name = "interceptFrom", required = false)
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<InterceptFromDefinition>();
    @XmlElement(name = "interceptSendToEndpoint", required = false)
    private List<InterceptSendToEndpointDefinition> interceptSendToEndpoints = new ArrayList<InterceptSendToEndpointDefinition>();
    @XmlElement(name = "restConfiguration", required = false)
    private RestConfigurationDefinition restConfiguration;
    @XmlElement(name = "rest", required = false)
    private List<RestDefinition> rests = new ArrayList<RestDefinition>();
    @XmlElement(name = "route", required = false)
    private List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
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
            LOG.info("Using custom Registry: " + registry);
            context.setRegistry(registry);
        }
    }

    @Override
    protected <S> S getBeanForType(Class<S> clazz) {
        Collection<S> objects = BlueprintContainerRegistry.lookupByType(blueprintContainer, clazz).values();
        if (objects.size() == 1) {
            return objects.iterator().next();
        }
        return null;
    }

    @Override
    protected void initPropertyPlaceholder() throws Exception {
        super.initPropertyPlaceholder();

        // if blueprint property resolver is enabled on CamelContext then bridge PropertiesComponent to blueprint
        if (isUseBlueprintPropertyResolver()) {
            // lookup existing configured properties component
            PropertiesComponent pc = getContext().getComponent("properties", PropertiesComponent.class);

            BlueprintPropertiesParser parser = new BlueprintPropertiesParser(pc, blueprintContainer, pc.getPropertiesParser());
            BlueprintPropertiesResolver resolver = new BlueprintPropertiesResolver(pc.getPropertiesResolver(), parser);

            // any extra properties
            ServiceReference<?> ref = bundleContext.getServiceReference(PropertiesComponent.OVERRIDE_PROPERTIES);
            if (ref != null) {
                Properties extra = (Properties) bundleContext.getService(ref);
                if (extra != null) {
                    pc.setOverrideProperties(extra);
                }
            }

            // no locations has been set, so its a default component
            if (pc.getLocations() == null) {
                StringBuilder sb = new StringBuilder();
                String[] ids = parser.lookupPropertyPlaceholderIds();
                for (String id : ids) {
                    sb.append("blueprint:").append(id).append(",");
                }
                if (sb.length() > 0) {
                    // location supports multiple separated by comma
                    pc.setLocation(sb.toString());
                }
            }

            if (pc.getLocations() != null) {
                // bridge camel properties with blueprint
                pc.setPropertiesParser(parser);
                pc.setPropertiesResolver(resolver);
            }
        }
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
        PackageScanRouteBuilderFinder finder = new PackageScanRouteBuilderFinder(getContext(), packages, classLoader,
                                                                                 getContext().getPackageScanClassResolver());
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
        // setup the application context classloader with the bundle delegating classloader
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
            // Ignore, if the EventAdmin package is not available, just don't use it
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

    public String getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    public String getAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    public String getUseMDCLogging() {
        return useMDCLogging;
    }

    public void setUseMDCLogging(String useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    public String getUseBreadcrumb() {
        return useBreadcrumb;
    }

    public void setUseBreadcrumb(String useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    public String getAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    public void setAllowUseOriginalMessage(String allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    public String getRuntimeEndpointRegistryEnabled() {
        return runtimeEndpointRegistryEnabled;
    }

    public void setRuntimeEndpointRegistryEnabled(String runtimeEndpointRegistryEnabled) {
        this.runtimeEndpointRegistryEnabled = runtimeEndpointRegistryEnabled;
    }

    public String getManagementNamePattern() {
        return managementNamePattern;
    }

    public void setManagementNamePattern(String managementNamePattern) {
        this.managementNamePattern = managementNamePattern;
    }

    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
    }

    @Deprecated
    public Boolean getLazyLoadTypeConverters() {
        // use false by default
        return lazyLoadTypeConverters != null ? lazyLoadTypeConverters : Boolean.FALSE;
    }

    @Deprecated
    public void setLazyLoadTypeConverters(Boolean lazyLoadTypeConverters) {
        this.lazyLoadTypeConverters = lazyLoadTypeConverters;
    }

    public Boolean getTypeConverterStatisticsEnabled() {
        return typeConverterStatisticsEnabled;
    }

    public void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled) {
        this.typeConverterStatisticsEnabled = typeConverterStatisticsEnabled;
    }

    public TypeConverterExists getTypeConverterExists() {
        return typeConverterExists;
    }

    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        this.typeConverterExists = typeConverterExists;
    }

    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        return typeConverterExistsLoggingLevel;
    }

    public void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel) {
        this.typeConverterExistsLoggingLevel = typeConverterExistsLoggingLevel;
    }

    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    public CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder() {
        return camelPropertyPlaceholder;
    }

    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
    }

    public List<RouteContextRefDefinition> getRouteRefs() {
        return routeRefs;
    }

    public void setRouteRefs(List<RouteContextRefDefinition> routeRefs) {
        this.routeRefs = routeRefs;
    }

    public List<RestContextRefDefinition> getRestRefs() {
        return restRefs;
    }

    public void setRestRefs(List<RestContextRefDefinition> restRefs) {
        this.restRefs = restRefs;
    }

    public List<CamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies() {
        return redeliveryPolicies;
    }

    public void setRedeliveryPolicies(List<CamelRedeliveryPolicyFactoryBean> redeliveryPolicies) {
        this.redeliveryPolicies = redeliveryPolicies;
    }

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

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String getMessageHistory() {
        return messageHistory;
    }

    public void setMessageHistory(String messageHistory) {
        this.messageHistory = messageHistory;
    }

    public String getLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    public void setLogExhaustedMessageBody(String logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public String getStreamCache() {
        return streamCache;
    }

    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    public String getDelayer() {
        return delayer;
    }

    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    public String getHandleFault() {
        return handleFault;
    }

    public void setHandleFault(String handleFault) {
        this.handleFault = handleFault;
    }

    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
    }

    public PropertiesDefinition getProperties() {
        return properties;
    }

    public void setProperties(PropertiesDefinition properties) {
        this.properties = properties;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public PackageScanDefinition getPackageScan() {
        return packageScan;
    }

    public void setPackageScan(PackageScanDefinition packageScan) {
        this.packageScan = packageScan;
    }

    public ContextScanDefinition getContextScan() {
        return contextScan;
    }

    public void setContextScan(ContextScanDefinition contextScan) {
        this.contextScan = contextScan;
    }

    public CamelJMXAgentDefinition getCamelJMXAgent() {
        return camelJMXAgent;
    }

    public void setCamelJMXAgent(CamelJMXAgentDefinition camelJMXAgent) {
        this.camelJMXAgent = camelJMXAgent;
    }

    public CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy() {
        return camelStreamCachingStrategy;
    }

    public void setCamelStreamCachingStrategy(CamelStreamCachingStrategyDefinition camelStreamCachingStrategy) {
        this.camelStreamCachingStrategy = camelStreamCachingStrategy;
    }

    public List<?> getBeans() {
        return beans;
    }

    public void setBeans(List<?> beans) {
        this.beans = beans;
    }

    public List<RouteBuilderDefinition> getBuilderRefs() {
        return builderRefs;
    }

    public void setBuilderRefs(List<RouteBuilderDefinition> builderRefs) {
        this.builderRefs = builderRefs;
    }

    public List<CamelEndpointFactoryBean> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<CamelEndpointFactoryBean> endpoints) {
        this.endpoints = endpoints;
    }

    public DataFormatsDefinition getDataFormats() {
        return dataFormats;
    }

    public void setDataFormats(DataFormatsDefinition dataFormats) {
        this.dataFormats = dataFormats;
    }

    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
    }

    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    public List<OnCompletionDefinition> getOnCompletions() {
        return onCompletions;
    }

    public void setOnCompletions(List<OnCompletionDefinition> onCompletions) {
        this.onCompletions = onCompletions;
    }

    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    public List<InterceptFromDefinition> getInterceptFroms() {
        return interceptFroms;
    }

    public void setInterceptFroms(List<InterceptFromDefinition> interceptFroms) {
        this.interceptFroms = interceptFroms;
    }

    public List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints() {
        return interceptSendToEndpoints;
    }

    public void setInterceptSendToEndpoints(List<InterceptSendToEndpointDefinition> interceptSendToEndpoints) {
        this.interceptSendToEndpoints = interceptSendToEndpoints;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    public List<RestDefinition> getRests() {
        return rests;
    }

    public void setRests(List<RestDefinition> rests) {
        this.rests = rests;
    }

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

    public void setUseBlueprintPropertyResolver(Boolean useBlueprintPropertyResolver) {
        this.useBlueprintPropertyResolver = useBlueprintPropertyResolver;
    }

    public boolean isUseBlueprintPropertyResolver() {
        // enable by default
        return useBlueprintPropertyResolver == null || useBlueprintPropertyResolver.booleanValue();
    }

}
