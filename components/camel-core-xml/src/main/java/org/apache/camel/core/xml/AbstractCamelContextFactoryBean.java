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
package org.apache.camel.core.xml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
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
import org.apache.camel.core.xml.scan.PatternBasedPackageScanFilter;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.management.DefaultManagementLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.config.PropertiesDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A factory to create and initialize a
 * {@link CamelContext} and install routes either explicitly configured
 * or found by searching the classpath for Java classes which extend
 * {@link org.apache.camel.builder.RouteBuilder}.
 *
 * @version $Revision: 938746 $
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelContextFactoryBean<T extends CamelContext> extends IdentifiedType implements RouteContainer {
    private static final Log LOG = LogFactory.getLog(AbstractCamelContextFactoryBean.class);

    @XmlTransient
    private List<RoutesBuilder> builders = new ArrayList<RoutesBuilder>();
    @XmlTransient
    private ClassLoader contextClassLoaderOnStart;

    public AbstractCamelContextFactoryBean() {
        // Lets keep track of the class loader for when we actually do start things up
        contextClassLoaderOnStart = Thread.currentThread().getContextClassLoader();
    }

    public Object getObject() throws Exception {
        return getContext();
    }

    public Class getObjectType() {
        return CamelContext.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public ClassLoader getContextClassLoaderOnStart() {
        return contextClassLoaderOnStart;
    }

    public void afterPropertiesSet() throws Exception {
        if (ObjectHelper.isEmpty(getId())) {
            throw new IllegalArgumentException("Id must be set");
        }

        if (getProperties() != null) {
            getContext().setProperties(getProperties().asMap());
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
        ProcessorFactory processorFactory = getBeanForType(ProcessorFactory.class);
        if (processorFactory != null) {
            LOG.info("Using custom ProcessorFactory: " + processorFactory);
            getContext().setProcessorFactory(processorFactory);
        }

        Debugger debugger = getBeanForType(Debugger.class);
        if (debugger != null) {
            LOG.info("Using custom Debugger: " + debugger);
            getContext().setDebugger(debugger);
        }

        // set the custom registry if defined
        initCustomRegistry(getContext());

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
        initBeanPostProcessor(getContext());

        initCamelContext(getContext());

        // must init route refs before we prepare the routes below
        initRouteRefs();

        // do special preparation for some concepts such as interceptors and policies
        // this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
        // using route builders. So we have here a little custom code to fix the JAXB gaps
        for (RouteDefinition route : getRoutes()) {

            // at first init the parent
            RouteDefinitionHelper.initParent(route);

            // abstracts is the cross cutting concerns
            List<ProcessorDefinition> abstracts = new ArrayList<ProcessorDefinition>();

            // upper is the cross cutting concerns such as interceptors, error handlers etc
            List<ProcessorDefinition> upper = new ArrayList<ProcessorDefinition>();

            // lower is the regular route
            List<ProcessorDefinition> lower = new ArrayList<ProcessorDefinition>();

            RouteDefinitionHelper.prepareRouteForInit(route, abstracts, lower);

            // interceptors should be first for the cross cutting concerns
            initInterceptors(route, upper);
            // then on completion
            initOnCompletions(abstracts, upper);
            // then transactions
            initTransacted(abstracts, lower);
            // then on exception
            initOnExceptions(abstracts, upper);

            // rebuild route as upper + lower
            route.clearOutput();
            route.getOutputs().addAll(lower);
            route.getOutputs().addAll(0, upper);

            // mark as custom prepared
            route.customPrepared();
        }

        if (getDataFormats() != null) {
            getContext().setDataFormats(getDataFormats().asMap());
        }

        // lets force any lazy creation
        getContext().addRouteDefinitions(getRoutes());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found JAXB created routes: " + getRoutes());
        }
        findRouteBuilders();
        installRoutes();
    }

    protected abstract void initCustomRegistry(T context);

    private void initOnExceptions(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> upper) {
        // add global on exceptions if any
        List<OnExceptionDefinition> onExceptions = getOnExceptions();
        if (onExceptions != null && !onExceptions.isEmpty()) {
            // init the parent
            for (OnExceptionDefinition global : onExceptions) {
                RouteDefinitionHelper.initParent(global);
            }
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
            // init the parent
            RouteDefinitionHelper.initParent(intercept);
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
                // init the parent
                RouteDefinitionHelper.initParent(intercept);
                // add as first output so intercept is handled before the actual route and that gives
                // us the needed head start to init and be able to intercept all the remaining processing steps
                upper.add(0, intercept);
            }
        }

        // configure intercept send to endpoint
        for (InterceptSendToEndpointDefinition intercept : getInterceptSendToEndpoints()) {
            intercept.afterPropertiesSet();
            // init the parent
            RouteDefinitionHelper.initParent(intercept);
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
            // init the parent
            for (OnCompletionDefinition global : completions) {
                RouteDefinitionHelper.initParent(global);
            }
        }

        // are there any completions to init at all?
        if (completions.isEmpty()) {
            return;
        }

        upper.addAll(completions);
    }

    private void initTransacted(List<ProcessorDefinition> abstracts, List<ProcessorDefinition> lower) {
        TransactedDefinition transacted = null;

        // add to correct type
        for (ProcessorDefinition type : abstracts) {
            if (type instanceof TransactedDefinition) {
                if (transacted == null) {
                    transacted = (TransactedDefinition) type;
                } else {
                    throw new IllegalArgumentException("The route can only have one transacted defined");
                }
            }
        }

        if (transacted != null) {
            // the outputs should be moved to the transacted policy
            transacted.getOutputs().addAll(lower);
            // and add it as the single output
            lower.clear();
            lower.add(transacted);
        }
    }

    private void initJMXAgent() throws Exception {
        CamelJMXAgentDefinition camelJMXAgent = getCamelJMXAgent();
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

    private void initRouteRefs() throws Exception {
        // add route refs to existing routes
        if (getRouteRefs() != null) {
            for (RouteContextRefDefinition ref : getRouteRefs()) {
                List<RouteDefinition> defs = ref.lookupRoutes(getContext());
                for (RouteDefinition def : defs) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Adding route from " + ref + " -> " + def);
                    }
                    // add in top as they are most likely to be common/shared
                    // which you may want to start first
                    getRoutes().add(0, def);
                }
            }
        }
    }

    protected abstract <S> S getBeanForType(Class<S> clazz);

    public void destroy() throws Exception {
        getContext().stop();
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
    public T getContext() {
        return getContext(true);
    }

    public abstract T getContext(boolean create);

    public abstract List<RouteDefinition> getRoutes();

    public abstract List<InterceptDefinition> getIntercepts();

    public abstract List<InterceptFromDefinition> getInterceptFroms();

    public abstract List<InterceptSendToEndpointDefinition> getInterceptSendToEndpoints();

    public abstract PropertiesDefinition getProperties();

    public abstract String[] getPackages();

    public abstract PackageScanDefinition getPackageScan();

    public abstract void setPackageScan(PackageScanDefinition packageScan);

    public abstract ContextScanDefinition getContextScan();

    public abstract void setContextScan(ContextScanDefinition contextScan);

    public abstract CamelPropertyPlaceholderDefinition getCamelPropertyPlaceholder();

    public abstract String getTrace();

    public abstract String getStreamCache();

    public abstract String getDelayer();

    public abstract String getHandleFault();

    public abstract String getAutoStartup();

    public abstract CamelJMXAgentDefinition getCamelJMXAgent();

    public abstract List<RouteBuilderDefinition> getBuilderRefs();

    public abstract List<RouteContextRefDefinition> getRouteRefs();

    public abstract String getErrorHandlerRef();

    public abstract DataFormatsDefinition getDataFormats();

    public abstract List<OnExceptionDefinition> getOnExceptions();

    public abstract List<OnCompletionDefinition> getOnCompletions();

    public abstract ShutdownRoute getShutdownRoute();

    public abstract ShutdownRunningTask getShutdownRunningTask();

    public abstract List<ThreadPoolProfileDefinition> getThreadPoolProfiles();

    public abstract String getDependsOn();

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Initializes the context
     *
     * @param ctx the context
     * @throws Exception is thrown if error occurred
     */
    protected void initCamelContext(T ctx) throws Exception {
        if (getStreamCache() != null) {
            ctx.setStreamCaching(parseBoolean(getStreamCache()));
        }
        if (getTrace() != null) {
            ctx.setTracing(parseBoolean(getTrace()));
        }
        if (getDelayer() != null) {
            ctx.setDelayer(parseLong(getDelayer()));
        }
        if (getHandleFault() != null) {
            ctx.setHandleFault(parseBoolean(getHandleFault()));
        }
        if (getErrorHandlerRef() != null) {
            ctx.setErrorHandlerBuilder(new ErrorHandlerBuilderRef(getErrorHandlerRef()));
        }
        if (getAutoStartup() != null) {
            ctx.setAutoStartup(parseBoolean(getAutoStartup()));
        }
        if (getShutdownRoute() != null) {
            ctx.setShutdownRoute(getShutdownRoute());
        }
        if (getShutdownRunningTask() != null) {
            ctx.setShutdownRunningTask(getShutdownRunningTask());
        }
    }

    private void initThreadPoolProfiles(T context) {
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
        if (getThreadPoolProfiles() != null && !getThreadPoolProfiles().isEmpty()) {
            for (ThreadPoolProfileDefinition profile : getThreadPoolProfiles()) {
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

    protected abstract void initBeanPostProcessor(T context);

    /**
     * Strategy to install all available routes into the context
     */
    protected void installRoutes() throws Exception {
        List<RouteBuilder> builders = new ArrayList<RouteBuilder>();

        // lets add route builders added from references
        if (getBuilderRefs() != null) {
            for (RouteBuilderDefinition builderRef : getBuilderRefs()) {
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
            // Inject the annotated resource
            postProcessBeforeInit(builder);
            getContext().addRoutes(builder);
        }
    }

    protected abstract void postProcessBeforeInit(RouteBuilder builder);

    /**
     * Strategy method to try find {@link org.apache.camel.builder.RouteBuilder} instances on the classpath
     */
    protected void findRouteBuilders() throws Exception {
        // package scan
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

            String[] normalized = normalizePackages(getContext(), packageScanDef.getPackages());
            findRouteBuildersByPackageScan(normalized, filter, builders);
        }

        // context scan
        ContextScanDefinition contextScanDef = getContextScan();
        if (contextScanDef != null) {
            // use package scan filter
            PatternBasedPackageScanFilter filter = new PatternBasedPackageScanFilter();
            // support property placeholders in include and exclude
            for (String include : contextScanDef.getIncludes()) {
                include = getContext().resolvePropertyPlaceholders(include);
                filter.addIncludePattern(include);
            }
            for (String exclude : contextScanDef.getExcludes()) {
                exclude = getContext().resolvePropertyPlaceholders(exclude);
                filter.addExcludePattern(exclude);
            }
            findRouteBuildersByContextScan(filter, builders);
        }
    }

    protected abstract void findRouteBuildersByPackageScan(String[] packages, PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception;

    protected abstract void findRouteBuildersByContextScan(PackageScanFilter filter, List<RoutesBuilder> builders) throws Exception;

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

    private String[] normalizePackages(T context, List<String> unnormalized) throws Exception {
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