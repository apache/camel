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
package org.apache.camel.cdi.xml;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
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
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.core.xml.CamelProxyFactoryDefinition;
import org.apache.camel.core.xml.CamelServiceExporterDefinition;
import org.apache.camel.core.xml.CamelStreamCachingStrategyDefinition;
import org.apache.camel.impl.DefaultCamelContext;
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
import org.apache.camel.spi.PackageScanFilter;

@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends AbstractCamelContextFactoryBean<DefaultCamelContext> implements BeanManagerAware {

    @XmlAttribute(name = "depends-on")
    private String dependsOn;

    @XmlAttribute
    private String trace;

    @XmlAttribute
    private String messageHistory;

    @XmlAttribute
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

    @XmlAttribute
    private String autoStartup;

    @XmlAttribute
    private String shutdownEager;

    @XmlAttribute
    private String useMDCLogging;

    @XmlAttribute
    private String useDataType;

    @XmlAttribute
    private String useBreadcrumb;

    @XmlAttribute
    private String allowUseOriginalMessage;

    @XmlAttribute
    private String runtimeEndpointRegistryEnabled;

    @XmlAttribute
    private String managementNamePattern;

    @XmlAttribute
    private String threadNamePattern;

    @XmlAttribute
    private ShutdownRoute shutdownRoute;

    @XmlAttribute
    private ShutdownRunningTask shutdownRunningTask;

    @Deprecated
    @XmlAttribute
    private Boolean lazyLoadTypeConverters;

    @XmlAttribute
    private Boolean loadTypeConverters;

    @XmlAttribute
    private Boolean typeConverterStatisticsEnabled;

    @XmlAttribute
    private TypeConverterExists typeConverterExists;

    @XmlAttribute
    private LoggingLevel typeConverterExistsLoggingLevel;

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
        @XmlElement(name = "consumerTemplate", type = ConsumerTemplateFactoryBean.class),
        @XmlElement(name = "redeliveryPolicyProfile", type = RedeliveryPolicyFactoryBean.class),
        @XmlElement(name = "template", type = ProducerTemplateFactoryBean.class),
        @XmlElement(name = "threadPool", type = ThreadPoolFactoryBean.class),
    })
    private List<AbstractCamelFactoryBean<?>> beansFactory;

    @XmlTransient
    private List<?> beans;

    @XmlElement(name = "defaultServiceCallConfiguration")
    private ServiceCallConfigurationDefinition defaultServiceCallConfiguration;

    @XmlElement(name = "serviceCallConfiguration", type = ServiceCallConfigurationDefinition.class)
    private List<ServiceCallConfigurationDefinition> serviceCallConfigurations;

    @XmlElement(name = "defaultHystrixConfiguration")
    private HystrixConfigurationDefinition defaultHystrixConfiguration;

    @XmlElement(name = "hystrixConfiguration", type = HystrixConfigurationDefinition.class)
    private List<HystrixConfigurationDefinition> hystrixConfigurations;

    @XmlElement(name = "errorHandler", type = ErrorHandlerDefinition.class)
    private List<ErrorHandlerDefinition> errorHandlers;

    @XmlElement(name = "export", type = CamelServiceExporterDefinition.class)
    private List<CamelServiceExporterDefinition> exports;

    @XmlElement(name = "proxy")
    private List<CamelProxyFactoryDefinition> proxies;

    @XmlElement(name = "routeBuilder")
    private List<RouteBuilderDefinition> builderRefs = new ArrayList<>();

    @XmlElement(name = "routeContextRef")
    private List<RouteContextRefDefinition> routeRefs = new ArrayList<>();

    @XmlElement(name = "restContextRef")
    private List<RestContextRefDefinition> restRefs = new ArrayList<>();

    @XmlElement(name = "threadPoolProfile")
    private List<ThreadPoolProfileDefinition> threadPoolProfiles;

    @XmlElement(name = "endpoint")
    private List<EndpointFactoryBean> endpoints;

    @XmlElement(name = "dataFormats")
    private DataFormatsDefinition dataFormats;

    @XmlElement(name = "transformers")
    private TransformersDefinition transformers;

    @XmlElement(name = "validators")
    private ValidatorsDefinition validators;

    @XmlElement(name = "redeliveryPolicyProfile")
    private List<RedeliveryPolicyFactoryBean> redeliveryPolicies;

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
    private DefaultCamelContext context;

    @XmlTransient
    private BeanManager manager;

    @XmlTransient
    private boolean implicitId;

    @Override
    public Class<DefaultCamelContext> getObjectType() {
        return DefaultCamelContext.class;
    }

    @Override
    public void setBeanManager(BeanManager manager) {
        this.manager = manager;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <S> S getBeanForType(Class<S> clazz) {
        Bean<?> bean = manager.resolve(manager.getBeans(clazz));
        if (bean == null) {
            return null;
        }

        return (S) manager.getReference(bean, clazz, manager.createCreationalContext(bean));
    }

    @Override
    protected void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception {
        // add filter to class resolver which then will filter
        getContext().getPackageScanClassResolver().addFilter(filter);

        PackageScanRouteBuilderFinder finder = new PackageScanRouteBuilderFinder(getContext(), packages, getContextClassLoaderOnStart(), getContext().getPackageScanClassResolver());
        finder.appendBuilders(builders);

        // and remove the filter
        getContext().getPackageScanClassResolver().removeFilter(filter);
    }

    @Override
    protected void findRouteBuildersByContextScan(PackageScanFilter filter, boolean includeNonSingletons, List<RoutesBuilder> builders) throws Exception {
        ContextScanRouteBuilderFinder finder = new ContextScanRouteBuilderFinder(manager, filter, includeNonSingletons);
        finder.appendBuilders(builders);
    }

    @Override
    protected void initBeanPostProcessor(DefaultCamelContext context) {
        // Already done by Camel CDI injection target
    }

    @Override
    protected void postProcessBeforeInit(RouteBuilder builder) {
        // Already done by Camel CDI injection target
    }

    @Override
    protected void initCustomRegistry(DefaultCamelContext context) {
        // Already done by Camel CDI injection target
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        setupRoutes();
    }

    @Override
    public DefaultCamelContext getContext() {
        return context;
    }

    public void setContext(DefaultCamelContext context) {
        this.context = context;
    }

    @Override
    public DefaultCamelContext getContext(boolean create) {
        return context;
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

    public List<EndpointFactoryBean> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointFactoryBean> endpoints) {
        this.endpoints = endpoints;
    }

    public List<RedeliveryPolicyFactoryBean> getRedeliveryPolicies() {
        return redeliveryPolicies;
    }

    public void setRedeliveryPolicies(List<RedeliveryPolicyFactoryBean> redeliveryPolicies) {
        this.redeliveryPolicies = redeliveryPolicies;
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

    public PropertiesDefinition getProperties() {
        return properties;
    }

    public void setProperties(PropertiesDefinition properties) {
        this.properties = properties;
    }

    public GlobalOptionsDefinition getGlobalOptions() {
        return globalOptions;
    }

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
     * they are specifically configured in the deployment.
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
     * Sets the context scanning information.
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

    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
    }

    public CamelStreamCachingStrategyDefinition getCamelStreamCachingStrategy() {
        return camelStreamCachingStrategy;
    }

    public void setCamelStreamCachingStrategy(CamelStreamCachingStrategyDefinition camelStreamCachingStrategy) {
        this.camelStreamCachingStrategy = camelStreamCachingStrategy;
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

    public String getAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    public String getShutdownEager() {
        return shutdownEager;
    }

    public void setShutdownEager(String shutdownEager) {
        this.shutdownEager = shutdownEager;
    }

    public String getUseMDCLogging() {
        return useMDCLogging;
    }

    public void setUseMDCLogging(String useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }

    public String getUseDataType() {
        return useDataType;
    }

    public void setUseDataType(String useDataType) {
        this.useDataType = useDataType;
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
        return lazyLoadTypeConverters;
    }

    @Deprecated
    public void setLazyLoadTypeConverters(Boolean lazyLoadTypeConverters) {
        this.lazyLoadTypeConverters = lazyLoadTypeConverters;
    }

    @Override
    public Boolean getLoadTypeConverters() {
        return loadTypeConverters;
    }

    public void setLoadTypeConverters(Boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
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

    public CamelJMXAgentDefinition getCamelJMXAgent() {
        return camelJMXAgent;
    }

    public void setCamelJMXAgent(CamelJMXAgentDefinition agent) {
        camelJMXAgent = agent;
    }

    public List<RouteBuilderDefinition> getBuilderRefs() {
        return builderRefs;
    }

    public void setBuilderRefs(List<RouteBuilderDefinition> builderRefs) {
        this.builderRefs = builderRefs;
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

    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
    }

    public DataFormatsDefinition getDataFormats() {
        return dataFormats;
    }

    public void setDataFormats(DataFormatsDefinition dataFormats) {
        this.dataFormats = dataFormats;
    }

    public TransformersDefinition getTransformers() {
        return transformers;
    }

    public void setTransformers(TransformersDefinition transformers) {
        this.transformers = transformers;
    }

    public ValidatorsDefinition getValidators() {
        return validators;
    }

    public void setValidators(ValidatorsDefinition validators) {
        this.validators = validators;
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

    public List<ThreadPoolProfileDefinition> getThreadPoolProfiles() {
        return threadPoolProfiles;
    }

    public void setThreadPoolProfiles(List<ThreadPoolProfileDefinition> threadPoolProfiles) {
        this.threadPoolProfiles = threadPoolProfiles;
    }

    public String getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    public List<CamelProxyFactoryDefinition> getProxies() {
        return proxies;
    }

    public void setProxies(List<CamelProxyFactoryDefinition> proxies) {
        this.proxies = proxies;
    }

    public List<CamelServiceExporterDefinition> getExports() {
        return exports;
    }

    public void setExports(List<CamelServiceExporterDefinition> exports) {
        this.exports = exports;
    }

    public boolean isImplicitId() {
        return implicitId;
    }

    public void setImplicitId(boolean implicitId) {
        this.implicitId = implicitId;
    }

    public List<ErrorHandlerDefinition> getErrorHandlers() {
        return errorHandlers;
    }

    public void setErrorHandlers(List<ErrorHandlerDefinition> errorHandlers) {
        this.errorHandlers = errorHandlers;
    }
}
