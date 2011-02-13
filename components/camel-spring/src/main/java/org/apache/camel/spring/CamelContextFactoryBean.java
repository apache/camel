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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
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
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A Spring {@link FactoryBean} to create and initialize a
 * {@link SpringCamelContext} and install routes either explicitly configured in
 * Spring XML or found by searching the classpath for Java classes which extend
 * {@link RouteBuilder} using the nested {@link #setPackages(String[])}.
 *
 * @version $Revision$
 */
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
public class CamelContextFactoryBean extends AbstractCamelContextFactoryBean<SpringCamelContext>
        implements FactoryBean, InitializingBean, DisposableBean, ApplicationContextAware, ApplicationListener {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextFactoryBean.class);

    @XmlAttribute(name = "depends-on", required = false)
    private String dependsOn;
    @XmlAttribute(required = false)
    private String trace;
    @XmlAttribute(required = false)
    private String streamCache;
    @XmlAttribute(required = false)
    private String delayer;
    @XmlAttribute(required = false)
    private String handleFault;
    @XmlAttribute(required = false)
    private String errorHandlerRef;
    @XmlAttribute(required = false)
    private String autoStartup;
    @XmlAttribute(required = false)
    private String useMDCLogging;
    @XmlAttribute(required = false)
    private ShutdownRoute shutdownRoute;
    @XmlAttribute(required = false)
    private ShutdownRunningTask shutdownRunningTask;
    @XmlAttribute(required = false)
    private Boolean lazyLoadTypeConverters;
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
            @XmlElement(name = "beanPostProcessor", type = CamelBeanPostProcessor.class, required = false),
            @XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class, required = false),
            @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class, required = false),
            @XmlElement(name = "proxy", type = CamelProxyFactoryDefinition.class, required = false),
            @XmlElement(name = "export", type = CamelServiceExporterDefinition.class, required = false),
            @XmlElement(name = "errorHandler", type = ErrorHandlerDefinition.class, required = false)})
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
    @XmlElement(name = "route", required = false)
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
    public Class getObjectType() {
        return SpringCamelContext.class;
    }
    
    protected <S> S getBeanForType(Class<S> clazz) {
        S bean = null;
        String[] names = getApplicationContext().getBeanNamesForType(clazz, true, true);
        if (names.length == 1) {
            bean = (S) getApplicationContext().getBean(names[0], clazz);
        }
        if (bean == null) {
            ApplicationContext parentContext = getApplicationContext().getParent();
            if (parentContext != null) {
                names = parentContext.getBeanNamesForType(clazz, true, true);
                if (names.length == 1) {
                    bean = (S) parentContext.getBean(names[0], clazz);
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
    protected void findRouteBuildersByContextScan(PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception {
        ContextScanRouteBuilderFinder finder = new ContextScanRouteBuilderFinder(getContext(), filter);
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

    protected void initCustomRegistry(SpringCamelContext context) {
        Registry registry = getBeanForType(Registry.class);
        if (registry != null) {
            LOG.info("Using custom Registry: " + registry);
            context.setRegistry(registry);
        }
    }

    public void onApplicationEvent(ApplicationEvent event) {
        // From Spring 3.0.1, The BeanFactory applicationEventListener 
        // and Bean's applicationEventListener will be called,
        // So we just delegate the onApplicationEvent call here.

        SpringCamelContext context = getContext(false);
        if (context != null) {
            // let the spring camel context handle the events
            context.onApplicationEvent(event);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Publishing spring-event: " + event);
            }

            if (event instanceof ContextRefreshedEvent) {
                // now lets start the CamelContext so that all its possible
                // dependencies are initialized
                try {
                    LOG.debug("Starting the context now!");
                    getContext().start();
                } catch (Exception e) {
                    throw wrapRuntimeCamelException(e);
                }
            }
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalArgumentException("No applicationContext has been injected!");
        }
        return applicationContext;
    }

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

    protected SpringCamelContext newCamelContext() {
        return new SpringCamelContext(getApplicationContext());
    }

    public SpringCamelContext getContext(boolean create) {
        if (context == null && create) {
            context = createContext();
        }
        return context;
    }

    public void setContext(SpringCamelContext context) {
        this.context = context;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    public List<CamelEndpointFactoryBean> getEndpoints() {
        return endpoints;
    }

    public List<CamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies() {
        return redeliveryPolicies;
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

    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
    }

    public void setCamelJMXAgent(CamelJMXAgentDefinition agent) {
        camelJMXAgent = agent;
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

    public Boolean getLazyLoadTypeConverters() {
        return lazyLoadTypeConverters;
    }

    public void setLazyLoadTypeConverters(Boolean lazyLoadTypeConverters) {
        this.lazyLoadTypeConverters = lazyLoadTypeConverters;
    }

    public CamelJMXAgentDefinition getCamelJMXAgent() {
        return camelJMXAgent;
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

    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    /**
     * Sets the name of the error handler object used to default the error handling strategy
     *
     * @param errorHandlerRef the Spring bean ref of the error handler
     */
    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
    }

    public void setDataFormats(DataFormatsDefinition dataFormats) {
        this.dataFormats = dataFormats;
    }

    public DataFormatsDefinition getDataFormats() {
        return dataFormats;
    }

    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
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
    
    public boolean isImplicitId() {
        return implicitId;
    }
    
    public void setImplicitId(boolean flag) {
        implicitId = flag;
    }

}
