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
import java.util.Map.Entry;
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
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.component.properties.PropertiesResolver;
import org.apache.camel.core.xml.scan.PatternBasedPackageScanFilter;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.management.DefaultManagementLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.ContextScanDefinition;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PackageScanDefinition;
import org.apache.camel.model.RouteBuilderDefinition;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.model.RouteContextRefDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.model.config.PropertiesDefinition;
import org.apache.camel.model.dataformat.DataFormatsDefinition;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementNamingStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory to create and initialize a
 * {@link CamelContext} and install routes either explicitly configured
 * or found by searching the classpath for Java classes which extend
 * {@link org.apache.camel.builder.RouteBuilder}.
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelContextFactoryBean<T extends CamelContext> extends IdentifiedType implements RouteContainer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCamelContextFactoryBean.class);

    /**
     * JVM system property to control lazy loading of type converters.
     */
    public static final String LAZY_LOAD_TYPE_CONVERTERS = "CamelLazyLoadTypeConverters";

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

        // set the type converter mode first
        if (getLazyLoadTypeConverters() != null) {
            getContext().setLazyLoadTypeConverters(getLazyLoadTypeConverters());
        } else if (System.getProperty(LAZY_LOAD_TYPE_CONVERTERS) != null) {
            // suppose a JVM property to control it so we can use that for example for unit testing
            // to speedup testing by enabling lazy loading of type converters
            String lazy = System.getProperty(LAZY_LOAD_TYPE_CONVERTERS);
            if ("true".equalsIgnoreCase(lazy)) {
                getContext().setLazyLoadTypeConverters(true);
            } else if ("false".equalsIgnoreCase(lazy)) {
                getContext().setLazyLoadTypeConverters(true);
            } else {
                throw new IllegalArgumentException("System property with key " + LAZY_LOAD_TYPE_CONVERTERS + " has unknown value: " + lazy);
            }
        }

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
        UuidGenerator uuidGenerator = getBeanForType(UuidGenerator.class);
        if (uuidGenerator != null) {
            LOG.info("Using custom UuidGenerator: " + uuidGenerator);
            getContext().setUuidGenerator(uuidGenerator);
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
        ManagementNamingStrategy managementNamingStrategy = getBeanForType(ManagementNamingStrategy.class);
        if (managementNamingStrategy != null) {
            LOG.info("Using custom ManagementNamingStrategy: " + managementNamingStrategy);
            getContext().getManagementStrategy().setManagementNamingStrategy(managementNamingStrategy);
        }
        EventFactory eventFactory = getBeanForType(EventFactory.class);
        if (eventFactory != null) {
            LOG.info("Using custom EventFactory: " + eventFactory);
            getContext().getManagementStrategy().setEventFactory(eventFactory);
        }
        // set the event notifier strategies if defined
        Map<String, EventNotifier> eventNotifiers = getContext().getRegistry().lookupByType(EventNotifier.class);
        if (eventNotifiers != null && !eventNotifiers.isEmpty()) {
            for (Entry<String, EventNotifier> entry : eventNotifiers.entrySet()) {
                EventNotifier notifier = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getManagementStrategy().getEventNotifiers().contains(notifier)) {
                    LOG.info("Using custom EventNotifier with id: " + entry.getKey() + " and implementation: " + notifier);
                    getContext().getManagementStrategy().addEventNotifier(notifier);
                }
            }
        }
        // set endpoint strategies if defined
        Map<String, EndpointStrategy> endpointStrategies = getContext().getRegistry().lookupByType(EndpointStrategy.class);
        if (endpointStrategies != null && !endpointStrategies.isEmpty()) {
            for (Entry<String, EndpointStrategy> entry : endpointStrategies.entrySet()) {
                EndpointStrategy strategy = entry.getValue();
                LOG.info("Using custom EndpointStrategy with id: " + entry.getKey() + " and implementation: " + strategy);
                getContext().addRegisterEndpointCallback(strategy);
            }
        }
        // shutdown
        ShutdownStrategy shutdownStrategy = getBeanForType(ShutdownStrategy.class);
        if (shutdownStrategy != null) {
            LOG.info("Using custom ShutdownStrategy: " + shutdownStrategy);
            getContext().setShutdownStrategy(shutdownStrategy);
        }
        // add global interceptors
        Map<String, InterceptStrategy> interceptStrategies = getContext().getRegistry().lookupByType(InterceptStrategy.class);
        if (interceptStrategies != null && !interceptStrategies.isEmpty()) {
            for (Entry<String, InterceptStrategy> entry : interceptStrategies.entrySet()) {
                InterceptStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getInterceptStrategies().contains(strategy)) {
                    LOG.info("Using custom InterceptStrategy with id: " + entry.getKey() + " and implementation: " + strategy);
                    getContext().addInterceptStrategy(strategy);
                }
            }
        }
        // set the lifecycle strategy if defined
        Map<String, LifecycleStrategy> lifecycleStrategies = getContext().getRegistry().lookupByType(LifecycleStrategy.class);
        if (lifecycleStrategies != null && !lifecycleStrategies.isEmpty()) {
            for (Entry<String, LifecycleStrategy> entry : lifecycleStrategies.entrySet()) {
                LifecycleStrategy strategy = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (!getContext().getLifecycleStrategies().contains(strategy)) {
                    LOG.info("Using custom LifecycleStrategy with id: " + entry.getKey() + " and implementation: " + strategy);
                    getContext().addLifecycleStrategy(strategy);
                }
            }
        }

        // set the default thread pool profile if defined
        initThreadPoolProfiles(getContext());

        // Set the application context and camelContext for the beanPostProcessor
        initBeanPostProcessor(getContext());

        // init camel context
        initCamelContext(getContext());

        // must init route refs before we prepare the routes below
        initRouteRefs();

        // do special preparation for some concepts such as interceptors and policies
        // this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
        // using route builders. So we have here a little custom code to fix the JAXB gaps
        prepareRoutes();

        // and add the routes
        getContext().addRouteDefinitions(getRoutes());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found JAXB created routes: " + getRoutes());
        }
        findRouteBuilders();
        installRoutes();
    }

    /**
     * Do special preparation for some concepts such as interceptors and policies
     * this is needed as JAXB does not build exactly the same model definition as Spring DSL would do
     * using route builders. So we have here a little custom code to fix the JAXB gaps
     */
    private void prepareRoutes() {
        for (RouteDefinition route : getRoutes()) {
            // leverage logic from route definition helper to prepare the route
            RouteDefinitionHelper.prepareRoute(getContext(), route, getOnExceptions(), getIntercepts(), getInterceptFroms(),
                    getInterceptSendToEndpoints(), getOnCompletions());

            // mark the route as prepared now
            route.markPrepared();
        }
    }

    protected abstract void initCustomRegistry(T context);

    protected void initJMXAgent() throws Exception {
        CamelJMXAgentDefinition camelJMXAgent = getCamelJMXAgent();

        boolean disabled = false;
        if (camelJMXAgent != null) {
            disabled = CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getDisabled());
        }

        if (disabled) {
            LOG.info("JMXAgent disabled");
            // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
            getContext().getLifecycleStrategies().clear();
            // no need to add a lifecycle strategy as we do not need one as JMX is disabled
            getContext().setManagementStrategy(new DefaultManagementStrategy());
        } else if (camelJMXAgent != null) {
            LOG.info("JMXAgent enabled: " + camelJMXAgent);
            DefaultManagementAgent agent = new DefaultManagementAgent(getContext());
            agent.setConnectorPort(CamelContextHelper.parseInteger(getContext(), camelJMXAgent.getConnectorPort()));
            agent.setCreateConnector(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getCreateConnector()));
            agent.setMBeanObjectDomainName(CamelContextHelper.parseText(getContext(), camelJMXAgent.getMbeanObjectDomainName()));
            agent.setMBeanServerDefaultDomain(CamelContextHelper.parseText(getContext(), camelJMXAgent.getMbeanServerDefaultDomain()));
            agent.setRegistryPort(CamelContextHelper.parseInteger(getContext(), camelJMXAgent.getRegistryPort()));
            agent.setServiceUrlPath(CamelContextHelper.parseText(getContext(), camelJMXAgent.getServiceUrlPath()));
            agent.setUsePlatformMBeanServer(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getUsePlatformMBeanServer()));
            agent.setOnlyRegisterProcessorWithCustomId(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getOnlyRegisterProcessorWithCustomId()));
            agent.setRegisterAlways(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getRegisterAlways()));
            agent.setRegisterNewRoutes(CamelContextHelper.parseBoolean(getContext(), camelJMXAgent.getRegisterNewRoutes()));

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

    protected void initPropertyPlaceholder() throws Exception {
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

            // if using a custom parser
            if (ObjectHelper.isNotEmpty(def.getPropertiesParserRef())) {
                PropertiesParser parser = CamelContextHelper.mandatoryLookup(getContext(), def.getPropertiesParserRef(),
                                                                             PropertiesParser.class);
                pc.setPropertiesParser(parser);
            }

            // register the properties component
            getContext().addComponent("properties", pc);
        }
    }

    protected void initRouteRefs() throws Exception {
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

    // Properties
    // -------------------------------------------------------------------------
    public T getContext() {
        return getContext(true);
    }

    public abstract T getContext(boolean create);

    public abstract List<RouteDefinition> getRoutes();

    public abstract List<? extends AbstractCamelEndpointFactoryBean> getEndpoints();

    public abstract List<? extends AbstractCamelRedeliveryPolicyFactoryBean> getRedeliveryPolicies();

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

    public abstract String getUseMDCLogging();

    public abstract String getUseBreadcrumb();

    public abstract Boolean getLazyLoadTypeConverters();

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
            ctx.setStreamCaching(CamelContextHelper.parseBoolean(getContext(), getStreamCache()));
        }
        if (getTrace() != null) {
            ctx.setTracing(CamelContextHelper.parseBoolean(getContext(), getTrace()));
        }
        if (getDelayer() != null) {
            ctx.setDelayer(CamelContextHelper.parseLong(getContext(), getDelayer()));
        }
        if (getHandleFault() != null) {
            ctx.setHandleFault(CamelContextHelper.parseBoolean(getContext(), getHandleFault()));
        }
        if (getErrorHandlerRef() != null) {
            ctx.setErrorHandlerBuilder(new ErrorHandlerBuilderRef(getErrorHandlerRef()));
        }
        if (getAutoStartup() != null) {
            ctx.setAutoStartup(CamelContextHelper.parseBoolean(getContext(), getAutoStartup()));
        }
        if (getUseMDCLogging() != null) {
            ctx.setUseMDCLogging(CamelContextHelper.parseBoolean(getContext(), getUseMDCLogging()));
        }
        if (getUseBreadcrumb() != null) {
            ctx.setUseBreadcrumb(CamelContextHelper.parseBoolean(getContext(), getUseBreadcrumb()));
        }
        if (getShutdownRoute() != null) {
            ctx.setShutdownRoute(getShutdownRoute());
        }
        if (getShutdownRunningTask() != null) {
            ctx.setShutdownRunningTask(getShutdownRunningTask());
        }
        if (getDataFormats() != null) {
            ctx.setDataFormats(getDataFormats().asMap());
        }
    }

    protected void initThreadPoolProfiles(T context) throws Exception {
        Set<String> defaultIds = new HashSet<String>();

        // lookup and use custom profiles from the registry
        Map<String, ThreadPoolProfile> profiles = context.getRegistry().lookupByType(ThreadPoolProfile.class);
        if (profiles != null && !profiles.isEmpty()) {
            for (Entry<String, ThreadPoolProfile> entry : profiles.entrySet()) {
                ThreadPoolProfile profile = entry.getValue();
                // do not add if already added, for instance a tracer that is also an InterceptStrategy class
                if (profile.isDefaultProfile()) {
                    LOG.info("Using custom default ThreadPoolProfile with id: " + entry.getKey() + " and implementation: " + profile);
                    context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(profile);
                    defaultIds.add(entry.getKey());
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
                    context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(profile.asThreadPoolProfile(context));
                    defaultIds.add(profile.getId());
                } else {
                    context.getExecutorServiceStrategy().registerThreadPoolProfile(profile.asThreadPoolProfile(context));
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
                LOG.trace("Using package: {} to scan for RouteBuilder classes", name);
                packages.add(name);
            }
        }
        return packages.toArray(new String[packages.size()]);
    }

}
