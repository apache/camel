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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesResolver;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.management.DefaultManagementLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.config.PropertiesDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
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
    private SpringCamelContext context;
    @XmlTransient
    private List<RoutesBuilder> builders = new ArrayList<RoutesBuilder>();
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
    
    public void afterPropertiesSet() throws Exception {
        if (properties != null) {
            getContext().setProperties(properties.asMap());
        }

        // set the resolvers first
        PackageScanClassResolver packageResolver = getBeanForType(PackageScanClassResolver.class);
        if (packageResolver != null) {
            LOG.info("Using custom PackageScanClassResolver: " + packageResolver);
            getContext().setPackageScanClassResolver(packageResolver);
        }
        ClassResolver classResolver = getBeanForType(ClassResolver.class);
        if (classResolver != null) {
            LOG.info("Using custom ClassResolver: " + classResolver);
            getContext().setClassResolver(classResolver);
        }
        FactoryFinderResolver factoryFinderResolver = getBeanForType(FactoryFinderResolver.class);
        if (factoryFinderResolver != null) {
            LOG.info("Using custom FactoryFinderResolver: " + factoryFinderResolver);
            getContext().setFactoryFinderResolver(factoryFinderResolver);
        }
        ExecutorServiceStrategy executorServiceStrategy = getBeanForType(ExecutorServiceStrategy.class);
        if (executorServiceStrategy != null) {
            LOG.info("Using custom ExecutorServiceStrategy: " + executorServiceStrategy);
            getContext().setExecutorServiceStrategy(executorServiceStrategy);
        }

        // set the custom registry if defined
        Registry registry = getBeanForType(Registry.class);
        if (registry != null) {
            LOG.info("Using custom Registry: " + registry);
            getContext().setRegistry(registry);
        }

        // setup property placeholder so we got it as early as possible
        initPropertyPlaceholder();

        // setup JMX agent at first
        initJMXAgent();

        Tracer tracer = getBeanForType(Tracer.class);
        if (tracer != null) {
            // use formatter if there is a TraceFormatter bean defined
            TraceFormatter formatter = getBeanForType(TraceFormatter.class);
            if (formatter != null) {
                tracer.setFormatter(formatter);
            }
            LOG.info("Using custom Tracer: " + tracer);
            getContext().addInterceptStrategy(tracer);
        }

        HandleFault handleFault = getBeanForType(HandleFault.class);
        if (handleFault != null) {
            LOG.info("Using custom HandleFault: " + handleFault);
            getContext().addInterceptStrategy(handleFault);
        }

        Delayer delayer = getBeanForType(Delayer.class);
        if (delayer != null) {
            LOG.info("Using custom Delayer: " + delayer);
            getContext().addInterceptStrategy(delayer);
        }

        InflightRepository inflightRepository = getBeanForType(InflightRepository.class);
        if (delayer != null) {
            LOG.info("Using custom InflightRepository: " + inflightRepository);
            getContext().setInflightRepository(inflightRepository);
        }

        ManagementStrategy managementStrategy = getBeanForType(ManagementStrategy.class);
        if (managementStrategy != null) {
            LOG.info("Using custom ManagementStrategy: " + managementStrategy);
            getContext().setManagementStrategy(managementStrategy);
        }

        EventFactory eventFactory = getBeanForType(EventFactory.class);
        if (eventFactory != null) {
            LOG.info("Using custom EventFactory: " + eventFactory);
            getContext().getManagementStrategy().setEventFactory(eventFactory);
        }

        // set the event notifier strategies if defined
        Map<String, EventNotifier> eventNotifiers = getContext().getRegistry().lookupByType(EventNotifier.class);
        if (eventNotifiers != null && !eventNotifiers.isEmpty()) {
            for (String id : eventNotifiers.keySet()) {
                EventNotifier notifier = eventNotifiers.get(id);
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getManagementStrategy().getEventNotifiers().contains(notifier)) {
                    LOG.info("Using custom EventNotifier with id: " + id + " and implementation: " + notifier);
                    getContext().getManagementStrategy().addEventNotifier(notifier);
                }
            }
        }

        ShutdownStrategy shutdownStrategy = getBeanForType(ShutdownStrategy.class);
        if (shutdownStrategy != null) {
            LOG.info("Using custom ShutdownStrategy: " + shutdownStrategy);
            getContext().setShutdownStrategy(shutdownStrategy);
        }

        // add global interceptors
        Map<String, InterceptStrategy> interceptStrategies = getContext().getRegistry().lookupByType(InterceptStrategy.class);
        if (interceptStrategies != null && !interceptStrategies.isEmpty()) {
            for (String id : interceptStrategies.keySet()) {
                InterceptStrategy strategy = interceptStrategies.get(id);
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getInterceptStrategies().contains(strategy)) {
                    LOG.info("Using custom InterceptStrategy with id: " + id + " and implementation: " + strategy);
                    getContext().addInterceptStrategy(strategy);
                }
            }
        }

        // set the lifecycle strategy if defined
        Map<String, LifecycleStrategy> lifecycleStrategies = getContext().getRegistry().lookupByType(LifecycleStrategy.class);
        if (lifecycleStrategies != null && !lifecycleStrategies.isEmpty()) {
            for (String id : lifecycleStrategies.keySet()) {
                LifecycleStrategy strategy = lifecycleStrategies.get(id);
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getLifecycleStrategies().contains(strategy)) {
                    LOG.info("Using custom LifecycleStrategy with id: " + id + " and implementation: " + strategy);
                    getContext().addLifecycleStrategy(strategy);
                }
            }
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(getContext());

        // Set the application context and camelContext for the beanPostProcessor
        if (beanPostProcessor != null) {
            if (beanPostProcessor instanceof ApplicationContextAware) {
                ((ApplicationContextAware)beanPostProcessor).setApplicationContext(applicationContext);
            }
            if (beanPostProcessor instanceof CamelBeanPostProcessor) {
                ((CamelBeanPostProcessor)beanPostProcessor).setCamelContext(getContext());
            }
        }

        initSpringCamelContext(getContext());

        // do special preparation for some concepts such as interceptors and policies
        // this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
        // using route builders. So we have here a little custom code to fix the JAXB gaps
        for (RouteDefinition route : routes) {

            // abstracts is the cross cutting concerns
            List<ProcessorDefinition> abstracts = new ArrayList<ProcessorDefinition>();

            // upper is the cross cutting concerns such as interceptors, error handlers etc
            List<ProcessorDefinition> upper = new ArrayList<ProcessorDefinition>();

            // lower is the regular route
            List<ProcessorDefinition> lower = new ArrayList<ProcessorDefinition>();

            prepareRouteForInit(route, abstracts, lower);

            // toAsync should fix up itself at first
            initToAsync(lower);

            // interceptors should be first for the cross cutting concerns
            initInterceptors(route, upper);
            // then on completion
            initOnCompletions(abstracts, upper);
            // then polices
            initPolicies(abstracts, lower);
            // then on exception
            initOnExceptions(abstracts, upper);

            // rebuild route as upper + lower
            route.clearOutput();
            route.getOutputs().addAll(upper);
            route.getOutputs().addAll(lower);

            // configure parents
            initParent(route);
        }

        if (dataFormats != null) {
            getContext().setDataFormats(dataFormats.asMap());
        } 
        
        // lets force any lazy creation
        getContext().addRouteDefinitions(routes);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found JAXB created routes: " + getRoutes());
        }
        findRouteBuilders();
        installRoutes();
    }

    private void prepareRouteForInit(RouteDefinition route, List<ProcessorDefinition> abstracts,
                                     List<ProcessorDefinition> lower) {
        // filter the route into abstracts and lower
        for (ProcessorDefinition output : route.getOutputs()) {
            if (output.isAbstract()) {
                abstracts.add(output);
            } else {
                lower.add(output);
            }
        }
    }

    private void initParent(RouteDefinition route) {
        for (ProcessorDefinition output : route.getOutputs()) {
            output.setParent(route);
            if (output.getOutputs() != null) {
                // recursive the outputs
                initParent(output);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initParent(ProcessorDefinition parent) {
        List<ProcessorDefinition> children = parent.getOutputs();
        for (ProcessorDefinition child : children) {
            child.setParent(parent);
            if (child.getOutputs() != null) {
                // recursive the children
                initParent(child);
            }
        }
    }

    private void initToAsync(List<ProcessorDefinition> lower) {
        List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
        ToDefinition toAsync = null;

        for (ProcessorDefinition output : lower) {
            if (toAsync != null) {
                // add this output on toAsync
                toAsync.getOutputs().add(output);
            } else {
                // regular outputs
                outputs.add(output);
            }

            if (output instanceof ToDefinition) {
                ToDefinition to = (ToDefinition) output;
                if (to.isAsync() != null && to.isAsync()) {
                    // new current to async
                    toAsync = to;
                }
            }
        }

        // rebuild outputs
        lower.clear();
        lower.addAll(outputs);
    }

    private void initOnExceptions(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> upper) {

        // add global on exceptions if any
        if (onExceptions != null && !onExceptions.isEmpty()) {
            abstracts.addAll(onExceptions);
        }

        // now add onExceptions to the route
        for (ProcessorDefinition output : abstracts) {
            if (output instanceof OnExceptionDefinition) {
                // on exceptions must be added at top, so the route flow is correct as
                // on exceptions should be the first outputs
                upper.add(0, output);
            }
        }
    }

    private void initInterceptors(RouteDefinition route, List<ProcessorDefinition> upper) {

        // configure intercept
        for (InterceptDefinition intercept : getIntercepts()) {
            intercept.afterPropertiesSet();
            // add as first output so intercept is handled before the actual route and that gives
            // us the needed head start to init and be able to intercept all the remaining processing steps
            upper.add(0, intercept);
        }

        // configure intercept from
        for (InterceptFromDefinition intercept : getInterceptFroms()) {

            // should we only apply interceptor for a given endpoint uri
            boolean match = true;
            if (intercept.getUri() != null) {
                match = false;
                for (FromDefinition input : route.getInputs()) {
                    if (EndpointHelper.matchEndpoint(input.getUri(), intercept.getUri())) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                intercept.afterPropertiesSet();
                // add as first output so intercept is handled before the actual route and that gives
                // us the needed head start to init and be able to intercept all the remaining processing steps
                upper.add(0, intercept);
            }
        }

        // configure intercept send to endpoint
        for (InterceptSendToEndpointDefinition intercept : getInterceptSendToEndpoints()) {
            intercept.afterPropertiesSet();
            // add as first output so intercept is handled before the actual route and that gives
            // us the needed head start to init and be able to intercept all the remaining processing steps
            upper.add(0, intercept);
        }

    }

    private void initOnCompletions(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> upper) {
        List<OnCompletionDefinition> completions = new ArrayList<OnCompletionDefinition>();

        // find the route scoped onCompletions
        for (ProcessorDefinition out : abstracts) {
            if (out instanceof OnCompletionDefinition) {
                completions.add((OnCompletionDefinition) out);
            }
        }

        // only add global onCompletion if there are no route already
        if (completions.isEmpty()) {
            completions = getOnCompletions();
        }

        // are there any completions to init at all?
        if (completions.isEmpty()) {
            return;
        }

        upper.addAll(completions);
    }

    private void initPolicies(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> lower) {

        // we need two types as transacted cannot extend policy due JAXB limitations
        PolicyDefinition policy = null;
        TransactedDefinition transacted = null;

        // add to correct type
        for (ProcessorDefinition type : abstracts) {
            if (type instanceof PolicyDefinition) {
                policy = (PolicyDefinition) type;
            } else if (type instanceof TransactedDefinition) {
                transacted = (TransactedDefinition) type;
            }
        }

        if (policy != null) {
            // the outputs should be moved to the policy
            policy.getOutputs().addAll(lower);
            // and add it as the single output
            lower.clear();
            lower.add(policy);
        } else if (transacted != null) {
            // the outputs should be moved to the transacted policy
            transacted.getOutputs().addAll(lower);
            // and add it as the single output
            lower.clear();
            lower.add(transacted);
        }
    }

    private void initJMXAgent() throws Exception {
        if (camelJMXAgent != null && camelJMXAgent.isAgentDisabled()) {
            LOG.info("JMXAgent disabled");
            // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
            getContext().getLifecycleStrategies().clear();
            // no need to add a lifecycle strategy as we do not need one as JMX is disabled
            getContext().setManagementStrategy(new DefaultManagementStrategy());
        } else if (camelJMXAgent != null) {
            LOG.info("JMXAgent enabled: " + camelJMXAgent);
            DefaultManagementAgent agent = new DefaultManagementAgent(getContext());
            agent.setConnectorPort(parseInteger(camelJMXAgent.getConnectorPort()));
            agent.setCreateConnector(parseBoolean(camelJMXAgent.getCreateConnector()));
            agent.setMBeanObjectDomainName(parseText(camelJMXAgent.getMbeanObjectDomainName()));
            agent.setMBeanServerDefaultDomain(parseText(camelJMXAgent.getMbeanServerDefaultDomain()));
            agent.setRegistryPort(parseInteger(camelJMXAgent.getRegistryPort()));
            agent.setServiceUrlPath(parseText(camelJMXAgent.getServiceUrlPath()));
            agent.setUsePlatformMBeanServer(parseBoolean(camelJMXAgent.getUsePlatformMBeanServer()));
            agent.setOnlyRegisterProcessorWithCustomId(parseBoolean(camelJMXAgent.getOnlyRegisterProcessorWithCustomId()));

            ManagementStrategy managementStrategy = new ManagedManagementStrategy(agent);
            getContext().setManagementStrategy(managementStrategy);

            // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
            getContext().getLifecycleStrategies().clear();
            getContext().addLifecycleStrategy(new DefaultManagementLifecycleStrategy(getContext()));
            // set additional configuration from camelJMXAgent
            boolean onlyId = agent.getOnlyRegisterProcessorWithCustomId() != null && agent.getOnlyRegisterProcessorWithCustomId();
            getContext().getManagementStrategy().onlyManageProcessorWithCustomId(onlyId);
            getContext().getManagementStrategy().setStatisticsLevel(camelJMXAgent.getStatisticsLevel());
        }
    }

    private void initPropertyPlaceholder() throws Exception {
        if (getCamelPropertyPlaceholder() != null) {
            CamelPropertyPlaceholderDefinition def = getCamelPropertyPlaceholder();

            PropertiesComponent pc = new PropertiesComponent();
            pc.setLocation(def.getLocation());

            // if using a custom resolver
            if (ObjectHelper.isNotEmpty(def.getPropertiesResolverRef())) {
                PropertiesResolver resolver = CamelContextHelper.mandatoryLookup(getContext(), def.getPropertiesResolverRef(),
                                                                                 PropertiesResolver.class);
                pc.setPropertiesResolver(resolver);
            }

            // register the properties component
            getContext().addComponent("properties", pc);
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
        // From Spring 3.0.1, The BeanFactory applicationEventListener 
        // and Bean's applicationEventListener will be called,
        // So we just delegate the onApplicationEvent call here.
        
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

    private String parseText(String text) throws Exception {
        // ensure we support property placeholders
        return getContext().resolvePropertyPlaceholders(text);
    }

    private Integer parseInteger(String text) throws Exception {
        // ensure we support property placeholders
        String s = getContext().resolvePropertyPlaceholders(text);
        if (s != null) {
            try {
                return new Integer(s);
            } catch (NumberFormatException e) {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as an Integer.", e);
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as an Integer.", e);
                }
            }
        }
        return null;
    }

    private Long parseLong(String text) throws Exception {
        // ensure we support property placeholders
        String s = getContext().resolvePropertyPlaceholders(text);
        if (s != null) {
            try {
                return new Long(s);
            } catch (NumberFormatException e) {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as a Long.", e);
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as a Long.", e);
                }
            }
        }
        return null;
    }

    private Boolean parseBoolean(String text) throws Exception {
        // ensure we support property placeholders
        String s = getContext().resolvePropertyPlaceholders(text);
        if (s != null) {
            s = s.trim().toLowerCase();
            if (s.equals("true") || s.equals("false")) {
                return new Boolean(s);
            } else {
                if (s.equals(text)) {
                    throw new IllegalArgumentException("Error parsing [" + s + "] as a Boolean.");
                } else {
                    throw new IllegalArgumentException("Error parsing [" + s + "] from property " + text + " as a Boolean.");
                }
            }
        }
        return null;
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
     * e.g. {@link RouteBuilder} implementations
     * 
     * @param packageScan the package scan
     */
    public void setPackageScan(PackageScanDefinition packageScan) {
        this.packageScan = packageScan;
    }

    public CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder() {
        return camelPropertyPlaceholder;
    }

    public void setCamelPropertyPlaceholder(CamelPropertyPlaceholderDefinition camelPropertyPlaceholder) {
        this.camelPropertyPlaceholder = camelPropertyPlaceholder;
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
     * Initializes the context
     * 
     * @param ctx the context
     * @throws Exception is thrown if error occurred
     */
    protected void initSpringCamelContext(SpringCamelContext ctx) throws Exception {
        if (streamCache != null) {
            ctx.setStreamCaching(parseBoolean(getStreamCache()));
        }
        if (trace != null) {
            ctx.setTracing(parseBoolean(getTrace()));
        }
        if (delayer != null) {
            ctx.setDelayer(parseLong(getDelayer()));
        }
        if (handleFault != null) {
            ctx.setHandleFault(parseBoolean(getHandleFault()));
        }
        if (errorHandlerRef != null) {
            ctx.setErrorHandlerBuilder(new ErrorHandlerBuilderRef(getErrorHandlerRef()));
        }
        if (autoStartup != null) {
            ctx.setAutoStartup(parseBoolean(getAutoStartup()));
        }
        if (shutdownRoute != null) {
            ctx.setShutdownRoute(getShutdownRoute());
        }
        if (shutdownRunningTask != null) {
            ctx.setShutdownRunningTask(getShutdownRunningTask());
        }
    }
    
    protected SpringCamelContext newCamelContext() {
        return new SpringCamelContext(getApplicationContext());
    }

    private void initThreadPoolProfiles(CamelContext context) {
        Set<String> defaultIds = new HashSet<String>();

        // lookup and use custom profiles from the registry
        Map<String, ThreadPoolProfile> profiles = context.getRegistry().lookupByType(ThreadPoolProfile.class);
        if (profiles != null && !profiles.isEmpty()) {
            for (String id : profiles.keySet()) {
                ThreadPoolProfile profile = profiles.get(id);
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (profile.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: " + id + " and implementation: " + profile);
                    context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(profile);
                    defaultIds.add(id);
                } else {
                    context.getExecutorServiceStrategy().registerThreadPoolProfile(profile);
                }
            }
        }

        // use custom profiles defined in the CamelContext
        if (threadPoolProfiles != null && !threadPoolProfiles.isEmpty()) {
            for (ThreadPoolProfileDefinition profile : threadPoolProfiles) {
                if (profile.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: " + profile.getId() + " and implementation: " + profile);
                    context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(profile);
                    defaultIds.add(profile.getId());
                } else {
                    context.getExecutorServiceStrategy().registerThreadPoolProfile(profile);
                }
            }
        }

        // validate at most one is defined
        if (defaultIds.size() > 1) {
            throw new IllegalArgumentException("Only exactly one default ThreadPoolProfile is allowed, was " + defaultIds.size() + " ids: " + defaultIds);
        }
    }

    /**
     * Strategy to install all available routes into the context
     */
    @SuppressWarnings("unchecked")
    protected void installRoutes() throws Exception {
        List<RouteBuilder> builders = new ArrayList<RouteBuilder>();

        // lets add route builders added from references
        if (builderRefs != null) {
            for (RouteBuilderDefinition builderRef : builderRefs) {
                RouteBuilder builder = builderRef.createRouteBuilder(getContext());
                if (builder != null) {
                    builders.add(builder);
                } else {
                    // support to get the route here
                    RoutesBuilder routes = builderRef.createRoutes(getContext());
                    if (routes != null) {
                        this.builders.add(routes);
                    } else {
                        // Throw the exception that we can't find any build here
                        throw new CamelException("Cannot find any routes with this RouteBuilder reference: " + builderRef);
                    }
                }
            }
        }

        // install already configured routes
        for (RoutesBuilder routeBuilder : this.builders) {
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
        PackageScanClassResolver resolver = getContext().getPackageScanClassResolver();
        addPackageElementContentsToScanDefinition();

        PackageScanDefinition packageScanDef = getPackageScan();
        if (packageScanDef != null && packageScanDef.getPackages().size() > 0) {
            // use package scan filter
            PatternBasedPackageScanFilter filter = new PatternBasedPackageScanFilter();
            // support property placeholders in include and exclude
            for (String include : packageScanDef.getIncludes()) {
                include = getContext().resolvePropertyPlaceholders(include);
                filter.addIncludePattern(include);
            }
            for (String exclude : packageScanDef.getExcludes()) {
                exclude = getContext().resolvePropertyPlaceholders(exclude);
                filter.addExcludePattern(exclude);
            }
            resolver.addFilter(filter);

            String[] normalized = normalizePackages(getContext(), packageScanDef.getPackages());
            RouteBuilderFinder finder = new RouteBuilderFinder(getContext(), normalized, getContextClassLoaderOnStart(),
                    getBeanPostProcessor(), getContext().getPackageScanClassResolver());
            finder.appendBuilders(builders);
        }
    }

    private void addPackageElementContentsToScanDefinition() {
        PackageScanDefinition packageScanDef = getPackageScan();

        if (getPackages() != null && getPackages().length > 0) {
            if (packageScanDef == null) {
                packageScanDef = new PackageScanDefinition();
                setPackageScan(packageScanDef);
            }

            for (String pkg : getPackages()) {
                packageScanDef.getPackages().add(pkg);
            }
        }
    }

    private String[] normalizePackages(CamelContext context, List<String> unnormalized) throws Exception {
        List<String> packages = new ArrayList<String>();
        for (String name : unnormalized) {
            // it may use property placeholders
            name = context.resolvePropertyPlaceholders(name);
            name = ObjectHelper.normalizeClassName(name);
            if (ObjectHelper.isNotEmpty(name)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Using package: " + name + " to scan for RouteBuilder classes");
                }
                packages.add(name);
            }
        }
        return packages.toArray(new String[packages.size()]);
    }

}
