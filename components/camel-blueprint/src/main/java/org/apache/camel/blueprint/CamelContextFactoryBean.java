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
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.core.xml.CamelPropertyPlaceholderDefinition;
import org.apache.camel.core.xml.CamelProxyFactoryDefinition;
import org.apache.camel.core.xml.CamelServiceExporterDefinition;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.config.PropertiesDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.spi.PackageScanFilter;
import org.osgi.framework.BundleContext;
import org.osgi.service.blueprint.container.BlueprintContainer;

/**
 * A bean to create and initialize a {@link BlueprintCamelContext}
 * and install routes either explicitly configured in
 * Blueprint XML or found by searching the classpath for Java classes which extend
 * {@link RouteBuilder} using the nested {@link #setPackages(String[])}.
 *
 * @version $Revision$
 */
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends AbstractCamelContextFactoryBean<BlueprintCamelContext> {

    @XmlAttribute(name = "depends-on", required = false)
    private String dependsOn;
    @XmlAttribute(required = false)
    private String trace;
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
    private ShutdownRoute shutdownRoute;
    @XmlAttribute(required = false)
    private ShutdownRunningTask shutdownRunningTask;
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
    @XmlElements({
//        @XmlElement(name = "beanPostProcessor", type = CamelBeanPostProcessor.class, required = false),
        @XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class, required = false),
        @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class, required = false),
        @XmlElement(name = "proxy", type = CamelProxyFactoryDefinition.class, required = false),
        @XmlElement(name = "export", type = CamelServiceExporterDefinition.class, required = false) //,
//        @XmlElement(name = "errorHandler", type = ErrorHandlerDefinition.class, required = false)
    })
    private List beans;
    @XmlElement(name = "routeBuilder", required = false)
    private List<RouteBuilderDefinition> builderRefs = new ArrayList<RouteBuilderDefinition>();
    @XmlElement(name = "routeContextRef", required = false)
    private List<RouteContextRefDefinition> routeRefs = new ArrayList<RouteContextRefDefinition>();
    @XmlElement(name = "threadPoolProfile", required = false)
    private List<ThreadPoolProfileDefinition> threadPoolProfiles;
    @XmlElement(name = "threadPool", required = false)
    private List<CamelThreadPoolFactoryBean> threadPools;
    @XmlElement(name = "endpoint", required = false)
    private List<CamelEndpointFactoryBean> endpoints;
    @XmlElement(name = "dataFormats", required = false)
    private DataFormatsDefinition dataFormats;
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
    @XmlElement(name = "route", required = false)
    private List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
    @XmlTransient
    private BlueprintCamelContext context;
    @XmlTransient
    private ClassLoader contextClassLoaderOnStart;
//    @XmlTransient
//    private ApplicationContext applicationContext;
//    @XmlTransient
//    private BeanPostProcessor beanPostProcessor;
    @XmlTransient
    private BlueprintContainer blueprintContainer;
    @XmlTransient
    private BundleContext bundleContext;
    @XmlTransient
    private boolean implicitId;


    public Class getObjectType() {
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
    }

    @Override
    protected <S> S getBeanForType(Class<S> clazz) {
        return null;
    }

    @Override
    protected void initBeanPostProcessor(BlueprintCamelContext context) {
    }

    @Override
    protected void postProcessBeforeInit(RouteBuilder builder) {
    }

    @Override
    protected void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception {
    }

    @Override
    protected void findRouteBuildersByContextScan(PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception {
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

    public List getBeans() {
        return beans;
    }

    public void setBeans(List beans) {
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

    public ClassLoader getContextClassLoaderOnStart() {
        return contextClassLoaderOnStart;
    }
    
    public boolean isImplicitId() {
        return implicitId;
    }
    
    public void setImplicitId(boolean flag) {
        implicitId = flag;
    }

}
