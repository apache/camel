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

import org.apache.camel.CamelException;
import org.apache.camel.Routes;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultLifecycleStrategy;
import org.apache.camel.management.DefaultInstrumentationAgent;
import org.apache.camel.management.InstrumentationLifecycleStrategy;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.config.PropertiesDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.processor.interceptor.Debugger;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
public class CamelContextFactoryBean extends IdentifiedType implements RouteContainer, FactoryBean, InitializingBean, DisposableBean, ApplicationContextAware, ApplicationListener {
    private static final Log LOG = LogFactory.getLog(CamelContextFactoryBean.class);

    @XmlAttribute(required = false)
    private Boolean trace;
    @XmlAttribute(required = false)
    private Boolean streamCache = Boolean.TRUE;
    @XmlAttribute(required = false)
    private Long delay;
    @XmlAttribute(required = false)
    private Boolean handleFault;
    @XmlAttribute(required = false)
    private String errorHandlerRef;
    @XmlAttribute(required = false)
    private Boolean shouldStartContext = Boolean.TRUE;
    @XmlElement(name = "properties", required = false)
    private PropertiesDefinition properties;
    @XmlElement(name = "package", required = false)
    private String[] packages = {};
    @XmlElement(name = "jmxAgent", type = CamelJMXAgentDefinition.class, required = false)
    private CamelJMXAgentDefinition camelJMXAgent;    
    @XmlElements({
        @XmlElement(name = "beanPostProcessor", type = CamelBeanPostProcessor.class, required = false),
        @XmlElement(name = "template", type = CamelProducerTemplateFactoryBean.class, required = false),
        @XmlElement(name = "consumerTemplate", type = CamelConsumerTemplateFactoryBean.class, required = false),
        @XmlElement(name = "proxy", type = CamelProxyFactoryDefinition.class, required = false),
        @XmlElement(name = "export", type = CamelServiceExporterDefinition.class, required = false)})
    private List beans;    
    @XmlElement(name = "routeBuilder", required = false)
    private List<RouteBuilderDefinition> builderRefs = new ArrayList<RouteBuilderDefinition>();
    @XmlElement(name = "endpoint", required = false)
    private List<CamelEndpointFactoryBean> endpoints;
    @XmlElement(name = "dataFormats", required = false)
    private DataFormatsDefinition dataFormats;
    @XmlElement(name = "onException", required = false)
    private List<OnExceptionDefinition> onExceptions = new ArrayList<OnExceptionDefinition>();
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
    private RouteBuilder routeBuilder;
    @XmlTransient
    private List<Routes> additionalBuilders = new ArrayList<Routes>();
    @XmlTransient
    private ApplicationContext applicationContext;
    @XmlTransient
    private ClassLoader contextClassLoaderOnStart;
    @XmlTransient
    private BeanPostProcessor beanPostProcessor;

    public CamelContextFactoryBean() {
        // Lets keep track of the class loader for when we actually do start things up
        contextClassLoaderOnStart = Thread.currentThread().getContextClassLoader();
    }

    public Object getObject() throws Exception {
        return getContext();
    }

    public Class getObjectType() {
        return SpringCamelContext.class;
    }

    public boolean isSingleton() {
        return true;
    }
    
    public ClassLoader getContextClassLoaderOnStart() {
        return contextClassLoaderOnStart;
    }
    
    public List<Routes> getAdditionalBuilders() {
        return additionalBuilders;
    }

    public void afterPropertiesSet() throws Exception {
        if (properties != null) {
            getContext().setProperties(properties.asMap());
        }
        // set the resolvers first
        PackageScanClassResolver packageResolver = getBeanForType(PackageScanClassResolver.class);
        if (packageResolver != null) {
            getContext().setPackageScanClassResolver(packageResolver);
        }
        ClassResolver classResolver = getBeanForType(ClassResolver.class);
        if (classResolver != null) {
            getContext().setClassResolver(classResolver);
        }
        FactoryFinderResolver factoryFinderResolver = getBeanForType(FactoryFinderResolver.class);
        if (factoryFinderResolver != null) {
            getContext().setFactoryFinderResolver(factoryFinderResolver);
        }

        Debugger debugger = getBeanForType(Debugger.class);
        if (debugger != null) {
            getContext().addInterceptStrategy(debugger);
        }

        Tracer tracer = getBeanForType(Tracer.class);
        if (tracer != null) {
            // use formatter if there is a TraceFormatter bean defined
            TraceFormatter formatter = getBeanForType(TraceFormatter.class);
            if (formatter != null) {
                tracer.setFormatter(formatter);
            }
            getContext().addInterceptStrategy(tracer);
        }

        HandleFault handleFault = getBeanForType(HandleFault.class);
        if (handleFault != null) {
            getContext().addInterceptStrategy(handleFault);
        }

        Delayer delayer = getBeanForType(Delayer.class);
        if (delayer != null) {
            getContext().addInterceptStrategy(delayer);
        }

        // set the lifecycle strategy if defined
        LifecycleStrategy lifecycleStrategy = getBeanForType(LifecycleStrategy.class);
        if (lifecycleStrategy != null) {
            getContext().setLifecycleStrategy(lifecycleStrategy);
        }

        // set the strategy if defined
        Registry registry = getBeanForType(Registry.class);
        if (registry != null) {
            getContext().setRegistry(registry);
        }

        // Set the application context and camelContext for the beanPostProcessor
        if (beanPostProcessor != null) {
            if (beanPostProcessor instanceof ApplicationContextAware) {
                ((ApplicationContextAware)beanPostProcessor).setApplicationContext(applicationContext);
            }
            if (beanPostProcessor instanceof CamelBeanPostProcessor) {
                ((CamelBeanPostProcessor)beanPostProcessor).setCamelContext(getContext());
            }
        }

        // do special preparation for some concepts such as interceptors and policies
        // this is needed as JAXB does not build excaclty the same model definition as Spring DSL would do
        // using route builders. So we have here a little custom code to fix the JAXB gaps
        for (RouteDefinition route : routes) {
            initOnExceptions(route);
            initInterceptors(route);
            initPolicies(route);
        }

        if (dataFormats != null) {
            getContext().setDataFormats(dataFormats.asMap());
        } 
        
        // lets force any lazy creation
        getContext().addRouteDefinitions(routes);

        // setup JMX agent
        initJMXAgent();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found JAXB created routes: " + getRoutes());
        }
        findRouteBuilders();
        installRoutes();
    }

    private void initOnExceptions(RouteDefinition route) {
        List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();
        List<ProcessorDefinition<?>> exceptionHandlers = new ArrayList<ProcessorDefinition<?>>();

        // add global on exceptions if any
        if (onExceptions != null && !onExceptions.isEmpty()) {
            // on exceptions must be added at top, so the route flow is correct as
            // on exceptions should be the first outputs
            route.getOutputs().addAll(0, onExceptions);
        }

        for (ProcessorDefinition output : route.getOutputs()) {
            // split into on exception and regular outputs
            if (output instanceof OnExceptionDefinition) {
                exceptionHandlers.add(output);
            } else {
                outputs.add(output);
            }
        }

        // clearing the outputs
        route.clearOutput();

        // add exception handlers as top children
        route.getOutputs().addAll(exceptionHandlers);

        // and the remaining outputs
        route.getOutputs().addAll(outputs);
    }

    private void initInterceptors(RouteDefinition route) {

        // configure intercept
        for (InterceptDefinition intercept : getIntercepts()) {
            intercept.afterPropertiesSet();
            // add as first output so intercept is handled before the acutal route and that gives
            // us the needed head start to init and be able to intercept all the remaining processing steps
            route.getOutputs().add(0, intercept);
        }

        // configure intercept from
        for (InterceptFromDefinition intercept : getInterceptFroms()) {

            // should we only apply interceptor for a given endpoint uri
            boolean match = true;
            if (intercept.getUri() != null) {
                match = false;
                for (FromDefinition input : route.getInputs()) {
                    if (input.getUri().equals(intercept.getUri())) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                // add as first output so intercept is handled before the acutal route and that gives
                // us the needed head start to init and be able to intercept all the remaining processing steps
                route.getOutputs().add(0, intercept);
            }
        }

        // configure intercept send to endpoint
        for (InterceptSendToEndpointDefinition intercept : getInterceptSendToEndpoints()) {
            // special intercept for intercepting sending to an endpoint
            // init interceptor by letting it proxy the real endpoint

            // add the interceptor but we must do some pre configuration beforehand
            intercept.afterPropertiesSet();

            // replace proceed with the rest of the route
            try {
                intercept.proxyEndpoint(getContext());
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
            route.addOutput(intercept);
        }

    }

    private void initPolicies(RouteDefinition route) {
        // setup the policies as JAXB yet again have not created a correct model for us
        List<ProcessorDefinition> types = route.getOutputs();
        // we need to types as transacted cannot extend policy due JAXB limitations
        PolicyDefinition policy = null;
        TransactedDefinition transacted = null;
        for (ProcessorDefinition type : types) {
            if (type instanceof PolicyDefinition) {
                policy = (PolicyDefinition) type;
            } else if (type instanceof TransactedDefinition) {
                transacted = (TransactedDefinition) type;
            } else if (policy != null) {
                // the outputs should be moved to the policy
                policy.addOutput(type);
            } else if (transacted != null) {
                // the outputs should be moved to the transacted policy
                transacted.addOutput(type);
            }
        }
        // did we find a policy if so replace it as the only output on the route
        if (policy != null) {
            route.clearOutput();
            route.addOutput(policy);
        } else if (transacted != null) {
            route.clearOutput();
            route.addOutput(transacted);
        }
    }

    private void initJMXAgent() throws Exception {
        if (camelJMXAgent != null && camelJMXAgent.isDisabled()) {
            LOG.info("JMXAgent disabled");
            getContext().setLifecycleStrategy(new DefaultLifecycleStrategy());
        } else if (camelJMXAgent != null) {
            DefaultInstrumentationAgent agent = new DefaultInstrumentationAgent();
            agent.setConnectorPort(camelJMXAgent.getConnectorPort());
            agent.setCreateConnector(camelJMXAgent.isCreateConnector());
            agent.setMBeanObjectDomainName(camelJMXAgent.getMbeanObjectDomainName());
            agent.setMBeanServerDefaultDomain(camelJMXAgent.getMbeanServerDefaultDomain());
            agent.setRegistryPort(camelJMXAgent.getRegistryPort());
            agent.setServiceUrlPath(camelJMXAgent.getServiceUrlPath());
            agent.setUsePlatformMBeanServer(camelJMXAgent.isUsePlatformMBeanServer());
            agent.setOnlyRegisterProcessorWithCustomId(camelJMXAgent.getOnlyRegisterProcessorWithCustomId());

            LOG.info("JMXAgent enabled: " + camelJMXAgent);
            getContext().setLifecycleStrategy(new InstrumentationLifecycleStrategy(agent));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getBeanForType(Class<T> clazz) {
        T bean = null;
        String[] names = getApplicationContext().getBeanNamesForType(clazz, true, true);
        if (names.length == 1) {
            bean = (T) getApplicationContext().getBean(names[0], clazz);
        }
        if (bean == null) {
            ApplicationContext parentContext = getApplicationContext().getParent();
            if (parentContext != null) {
                names = parentContext.getBeanNamesForType(clazz, true, true);
                if (names.length == 1) {
                    bean = (T) parentContext.getBean(names[0], clazz);
                }
            }
        }
        return bean;

    }

    public void destroy() throws Exception {
        getContext().stop();
    }

    public void onApplicationEvent(ApplicationEvent event) {
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

    // Properties
    // -------------------------------------------------------------------------
    public SpringCamelContext getContext() throws Exception {
        if (context == null) {
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

    public RouteBuilder getRouteBuilder() {
        return routeBuilder;
    }

    /**
     * Set a single {@link RouteBuilder} to be used to create the default routes
     * on startup
     */
    public void setRouteBuilder(RouteBuilder routeBuilder) {
        this.routeBuilder = routeBuilder;
    }

    /**
     * Set a collection of {@link RouteBuilder} instances to be used to create
     * the default routes on startup
     */
    public void setRouteBuilders(RouteBuilder[] builders) {
        for (RouteBuilder builder : builders) {
            additionalBuilders.add(builder);
        }
    }

    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalArgumentException("No applicationContext has been injected!");
        }
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
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
     * extend {@link RouteBuilder} to be auto-wired up to the
     * {@link SpringCamelContext} as a route. Note that classes are excluded if
     * they are specifically configured in the spring.xml
     *
     * @param packages the package names which are recursively searched
     */
    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public void setBeanPostProcessor(BeanPostProcessor postProcessor) {
        this.beanPostProcessor = postProcessor;
    }

    public BeanPostProcessor getBeanPostProcessor() {
        return beanPostProcessor;
    }

    public void setCamelJMXAgent(CamelJMXAgentDefinition agent) {
        camelJMXAgent = agent;
    }

    public Boolean getTrace() {
        return trace;
    }

    public void setTrace(Boolean trace) {
        this.trace = trace;
    }

    public Boolean getStreamCache() {
        return streamCache;
    }

    public void setStreamCache(Boolean streamCache) {
        this.streamCache = streamCache;
    }

    public Long getDelay() {
        return delay;
    }

    public void setDelay(Long delay) {
        this.delay = delay;
    }

    public Boolean getHandleFault() {
        return handleFault;
    }

    public void setHandleFault(Boolean handleFault) {
        this.handleFault = handleFault;
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

    public Boolean getShouldStartContext() {
        return shouldStartContext;
    }

    public void setShouldStartContext(Boolean shouldStartContext) {
        this.shouldStartContext = shouldStartContext;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Create the context
     */
    protected SpringCamelContext createContext() {
        SpringCamelContext ctx = new SpringCamelContext(getApplicationContext());
        ctx.setName(getId());
        if (streamCache != null) {
            ctx.setStreamCaching(streamCache);
        }
        if (trace != null) {
            ctx.setTrace(trace);
        }
        if (delay != null) {
            ctx.setDelay(delay);
        }
        if (handleFault != null) {
            ctx.setHandleFault(handleFault);
        }
        if (errorHandlerRef != null) {
            ErrorHandlerBuilder errorHandlerBuilder = (ErrorHandlerBuilder) getApplicationContext().getBean(errorHandlerRef, ErrorHandlerBuilder.class);
            if (errorHandlerBuilder == null) {
                throw new IllegalArgumentException("Could not find bean: " + errorHandlerRef);
            }
            ctx.setErrorHandlerBuilder(errorHandlerBuilder);
        }

        if (shouldStartContext != null) {
            ctx.setShouldStartContext(shouldStartContext);
        }

        return ctx;
    }

    /**
     * Strategy to install all available routes into the context
     */
    @SuppressWarnings("unchecked")
    protected void installRoutes() throws Exception {
        List<RouteBuilder> builders = new ArrayList<RouteBuilder>();

        if (routeBuilder != null) {
            builders.add(routeBuilder);
        }

        // lets add route builders added from references
        if (builderRefs != null) {
            for (RouteBuilderDefinition builderRef : builderRefs) {
                RouteBuilder builder = builderRef.createRouteBuilder(getContext());
                if (builder != null) {
                    builders.add(builder);
                } else {
                    // support to get the route here
                    Routes routes = builderRef.createRoutes(getContext());
                    if (routes != null) {
                        additionalBuilders.add(routes);
                    } else {
                        // Throw the exception that we can't find any build here
                        throw new CamelException("Can't find any routes info with this RouteBuilderDefinition " + builderRef);
                    }
                }
                
            }
        }

        // install already configured routes
        for (Routes routeBuilder : additionalBuilders) {
            getContext().addRoutes(routeBuilder);
        }

        // install builders
        for (RouteBuilder builder : builders) {
            if (beanPostProcessor != null) {
                // Inject the annotated resource
                beanPostProcessor.postProcessBeforeInitialization(builder, builder.toString());
            }
            getContext().addRoutes(builder);
        }
    }

    /**
     * Strategy method to try find {@link RouteBuilder} instances on the classpath
     */
    protected void findRouteBuilders() throws Exception {
        List<String> packages = new ArrayList<String>();

        if (getPackages() != null && getPackages().length > 0) {

            // normalize packages as end user can have inserted spaces or \n or the likes
            for (String name : getPackages()) {
                name = ObjectHelper.normalizeClassName(name);
                if (ObjectHelper.isNotEmpty(name)) {
                    packages.add(name);
                }
            }
            String[] normalized = packages.toArray(new String[packages.size()]);

            RouteBuilderFinder finder = new RouteBuilderFinder(getContext(), normalized, getContextClassLoaderOnStart(),
                    getBeanPostProcessor(), getContext().getPackageScanClassResolver());
            finder.appendBuilders(getAdditionalBuilders());
        }
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
}
