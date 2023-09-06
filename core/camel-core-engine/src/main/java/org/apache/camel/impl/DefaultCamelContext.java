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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.StartupStep;
import org.apache.camel.ValueHolder;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.impl.engine.RouteService;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.impl.engine.ValidatorKey;
import org.apache.camel.impl.scan.AssignableToPackageScanFilter;
import org.apache.camel.impl.scan.InvertingPackageScanFilter;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.LocalBeanRepositoryAware;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.Validator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.LocalBeanRegistry;
import org.apache.camel.support.SimpleUuidGenerator;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.concurrent.NamedThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public class DefaultCamelContext extends SimpleCamelContext implements ModelCamelContext {

    // global options that can be set on CamelContext as part of concurrent testing
    // which means options should be isolated via thread-locals and not a static instance
    // use a HashMap to store only JDK classes in the thread-local so there will not be any Camel classes leaking
    private static final ThreadLocal<Map<String, Object>> OPTIONS = new NamedThreadLocal<>("CamelContextOptions", HashMap::new);
    private static final String OPTION_NO_START = "OptionNoStart";
    private static final String OPTION_DISABLE_JMX = "OptionDisableJMX";
    private static final String OPTION_EXCLUDE_ROUTES = "OptionExcludeRoutes";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContext.class);
    private static final UuidGenerator UUID = new SimpleUuidGenerator();

    private Model model = new DefaultModel(this);

    /**
     * Creates the {@link ModelCamelContext} using {@link org.apache.camel.support.DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public DefaultCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link CamelContext} using the given {@link BeanRepository} as first-choice repository, and the
     * {@link org.apache.camel.support.SimpleRegistry} as fallback, via the {@link DefaultRegistry} implementation.
     *
     * @param repository the bean repository.
     */
    public DefaultCamelContext(BeanRepository repository) {
        this(new DefaultRegistry(repository));
    }

    /**
     * Creates the {@link ModelCamelContext} using the given registry
     *
     * @param registry the registry
     */
    public DefaultCamelContext(Registry registry) {
        this();
        getCamelContextExtension().setRegistry(registry);
    }

    public DefaultCamelContext(boolean init) {
        super(init);
        if (isDisableJmx()) {
            disableJMX();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        OPTIONS.remove();
    }

    @Override
    protected void doDumpRoutes() {
        DumpRoutesStrategy strategy = CamelContextHelper.findSingleByType(this, DumpRoutesStrategy.class);
        if (strategy == null) {
            strategy = getCamelContextExtension().getContextPlugin(DumpRoutesStrategy.class);
        }
        if (strategy != null) {
            strategy.dumpRoutes(getDumpRoutes());
        }
    }

    public static void setNoStart(boolean b) {
        getOptions().put(OPTION_NO_START, b);
    }

    public static boolean isNoStart() {
        return (Boolean) getOptions().getOrDefault(OPTION_NO_START, Boolean.FALSE);
    }

    public static void setDisableJmx(boolean b) {
        getOptions().put(OPTION_DISABLE_JMX, b);
    }

    public static boolean isDisableJmx() {
        return (Boolean) getOptions().getOrDefault(OPTION_DISABLE_JMX, Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED));
    }

    @Override
    public String getTestExcludeRoutes() {
        return getExcludeRoutes();
    }

    public static String getExcludeRoutes() {
        return (String) getOptions().get(OPTION_EXCLUDE_ROUTES);
    }

    public static void setExcludeRoutes(String s) {
        getOptions().put(OPTION_EXCLUDE_ROUTES, s);
    }

    public static void clearOptions() {
        OPTIONS.get().clear();
    }

    private static Map<String, Object> getOptions() {
        return OPTIONS.get();
    }

    @Override
    public void start() {
        // for example from unit testing we want to start Camel later (manually)
        if (isNoStart()) {
            LOG.trace("Ignoring start() as NO_START is true");
            return;
        }

        if (!isStarted() && !isStarting()) {
            StopWatch watch = new StopWatch();
            super.start();
            LOG.debug("start() took {} millis", watch.taken());
        } else {
            // ignore as Camel is already started
            LOG.trace("Ignoring start() as Camel is already started");
        }
    }

    @Override
    protected PackageScanClassResolver createPackageScanClassResolver() {
        PackageScanClassResolver resolver = super.createPackageScanClassResolver();
        String excluded = getExcludeRoutes();
        if (ObjectHelper.isNotEmpty(excluded)) {
            Set<Class<?>> excludedClasses = new HashSet<>();
            for (String str : excluded.split(",")) {
                excludedClasses.add(getClassResolver().resolveClass(str));
            }
            resolver.addFilter(new InvertingPackageScanFilter(new AssignableToPackageScanFilter(excludedClasses)));
        }
        return resolver;
    }

    @Override
    public void disposeModel() {
        LOG.debug("Disposing Model on CamelContext");
        model = null;
    }

    @Override
    public void addModelLifecycleStrategy(ModelLifecycleStrategy modelLifecycleStrategy) {
        model.addModelLifecycleStrategy(modelLifecycleStrategy);
    }

    @Override
    public List<ModelLifecycleStrategy> getModelLifecycleStrategies() {
        return model.getModelLifecycleStrategies();
    }

    @Override
    public void addRouteConfiguration(RouteConfigurationDefinition routesConfiguration) {
        model.addRouteConfiguration(routesConfiguration);
    }

    @Override
    public void addRouteConfigurations(List<RouteConfigurationDefinition> routesConfigurations) {
        model.addRouteConfigurations(routesConfigurations);
    }

    @Override
    public List<RouteConfigurationDefinition> getRouteConfigurationDefinitions() {
        return model.getRouteConfigurationDefinitions();
    }

    @Override
    public RouteConfigurationDefinition getRouteConfigurationDefinition(String id) {
        return model.getRouteConfigurationDefinition(id);
    }

    @Override
    public void removeRouteConfiguration(RouteConfigurationDefinition routeConfigurationDefinition) throws Exception {
        model.removeRouteConfiguration(routeConfigurationDefinition);
    }

    @Override
    public List<RouteDefinition> getRouteDefinitions() {
        return model.getRouteDefinitions();
    }

    @Override
    public RouteDefinition getRouteDefinition(String id) {
        return model.getRouteDefinition(id);
    }

    @Override
    public void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        model.addRouteDefinitions(routeDefinitions);
    }

    @Override
    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        model.addRouteDefinition(routeDefinition);
    }

    @Override
    public void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (!isLockModel()) {
            model.removeRouteDefinitions(routeDefinitions);
        }
    }

    @Override
    public void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        if (!isLockModel()) {
            model.removeRouteDefinition(routeDefinition);
        }
    }

    @Override
    public List<RouteTemplateDefinition> getRouteTemplateDefinitions() {
        return model.getRouteTemplateDefinitions();
    }

    @Override
    public RouteTemplateDefinition getRouteTemplateDefinition(String id) {
        return model.getRouteTemplateDefinition(id);
    }

    @Override
    public void addRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        model.addRouteTemplateDefinitions(routeTemplateDefinitions);
    }

    @Override
    public void addRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {

        model.addRouteTemplateDefinition(routeTemplateDefinition);
    }

    @Override
    public void removeRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {

        if (!isLockModel()) {
            model.removeRouteTemplateDefinitions(routeTemplateDefinitions);
        }
    }

    @Override
    public void removeRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        if (!isLockModel()) {
            model.removeRouteTemplateDefinition(routeTemplateDefinition);
        }
    }

    @Override
    public void removeRouteTemplateDefinitions(String pattern) throws Exception {
        if (!isLockModel()) {
            model.removeRouteTemplateDefinitions(pattern);
        }
    }

    @Override
    public void addRouteTemplateDefinitionConverter(String templateIdPattern, RouteTemplateDefinition.Converter converter) {

        model.addRouteTemplateDefinitionConverter(templateIdPattern, converter);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters)
            throws Exception {

        return model.addRouteFromTemplate(routeId, routeTemplateId, parameters);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, String prefixId, Map<String, Object> parameters)
            throws Exception {

        return model.addRouteFromTemplate(routeId, routeTemplateId, prefixId, parameters);
    }

    @Override
    public String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId, RouteTemplateContext routeTemplateContext)
            throws Exception {

        return model.addRouteFromTemplate(routeId, routeTemplateId, prefixId, routeTemplateContext);
    }

    @Override
    public void addRouteFromTemplatedRoute(TemplatedRouteDefinition templatedRouteDefinition)
            throws Exception {

        model.addRouteFromTemplatedRoute(templatedRouteDefinition);
    }

    @Override
    public void removeRouteTemplates(String pattern) throws Exception {

        if (!isLockModel()) {
            model.removeRouteTemplateDefinitions(pattern);
        }
    }

    @Override
    public List<RestDefinition> getRestDefinitions() {
        return model.getRestDefinitions();
    }

    @Override
    public void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception {
        model.addRestDefinitions(restDefinitions, addToRoutes);
    }

    @Override
    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        model.setDataFormats(dataFormats);
    }

    @Override
    public Map<String, DataFormatDefinition> getDataFormats() {
        return model.getDataFormats();
    }

    @Override
    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        return model.resolveDataFormatDefinition(name);
    }

    @Override
    public ProcessorDefinition<?> getProcessorDefinition(String id) {
        return model.getProcessorDefinition(id);
    }

    @Override
    public <T extends ProcessorDefinition<T>> T getProcessorDefinition(String id, Class<T> type) {
        return model.getProcessorDefinition(id, type);
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        model.setValidators(validators);
    }

    @Override
    public Resilience4jConfigurationDefinition getResilience4jConfiguration(String id) {
        return model.getResilience4jConfiguration(id);
    }

    @Override
    public void setResilience4jConfiguration(Resilience4jConfigurationDefinition configuration) {
        model.setResilience4jConfiguration(configuration);
    }

    @Override
    public void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> configurations) {
        model.setResilience4jConfigurations(configurations);
    }

    @Override
    public void addResilience4jConfiguration(String id, Resilience4jConfigurationDefinition configuration) {
        model.addResilience4jConfiguration(id, configuration);
    }

    @Override
    public FaultToleranceConfigurationDefinition getFaultToleranceConfiguration(String id) {
        return model.getFaultToleranceConfiguration(id);
    }

    @Override
    public void setFaultToleranceConfiguration(FaultToleranceConfigurationDefinition configuration) {
        model.setFaultToleranceConfiguration(configuration);
    }

    @Override
    public void setFaultToleranceConfigurations(List<FaultToleranceConfigurationDefinition> configurations) {
        model.setFaultToleranceConfigurations(configurations);
    }

    @Override
    public void addFaultToleranceConfiguration(String id, FaultToleranceConfigurationDefinition configuration) {
        model.addFaultToleranceConfiguration(id, configuration);
    }

    @Override
    public List<ValidatorDefinition> getValidators() {
        return model.getValidators();
    }

    @Override
    public void setTransformers(List<TransformerDefinition> transformers) {
        model.setTransformers(transformers);
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return model.getTransformers();
    }

    @Override
    public ServiceCallConfigurationDefinition getServiceCallConfiguration(String serviceName) {
        return model.getServiceCallConfiguration(serviceName);
    }

    @Override
    public void setServiceCallConfiguration(ServiceCallConfigurationDefinition configuration) {
        model.setServiceCallConfiguration(configuration);
    }

    @Override
    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> configurations) {
        model.setServiceCallConfigurations(configurations);
    }

    @Override
    public void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration) {
        model.addServiceCallConfiguration(serviceName, configuration);
    }

    @Override
    public void setRouteFilterPattern(String include, String exclude) {
        model.setRouteFilterPattern(include, exclude);
    }

    @Override
    public void setRouteFilter(Function<RouteDefinition, Boolean> filter) {
        model.setRouteFilter(filter);
    }

    @Override
    public Function<RouteDefinition, Boolean> getRouteFilter() {
        return model.getRouteFilter();
    }

    @Override
    public void addRegistryBean(RegistryBeanDefinition bean) {
        model.addRegistryBean(bean);
    }

    @Override
    public List<RegistryBeanDefinition> getRegistryBeans() {
        return model.getRegistryBeans();
    }

    @Override
    public ModelReifierFactory getModelReifierFactory() {
        return model.getModelReifierFactory();
    }

    @Override
    public void setModelReifierFactory(ModelReifierFactory modelReifierFactory) {
        model.setModelReifierFactory(modelReifierFactory);
    }

    @Override
    protected void bindDataFormats() throws Exception {
        // eager lookup data formats and bind to registry so the dataformats can
        // be looked up and used
        if (model != null) {
            for (Map.Entry<String, DataFormatDefinition> e : model.getDataFormats().entrySet()) {
                String id = e.getKey();
                DataFormatDefinition def = e.getValue();
                LOG.debug("Creating Dataformat with id: {} and definition: {}", id, def);
                DataFormat df = model.getModelReifierFactory().createDataFormat(this, def);
                addService(df, true);
                getRegistry().bind(id, df);
            }
        }
    }

    @Override
    protected synchronized void shutdownRouteService(RouteService routeService) throws Exception {
        if (model != null) {
            RouteDefinition rd = model.getRouteDefinition(routeService.getId());
            if (rd != null) {
                model.getRouteDefinitions().remove(rd);
            }
        }
        super.shutdownRouteService(routeService);
    }

    @Override
    protected boolean isStreamCachingInUse() throws Exception {
        boolean streamCachingInUse = super.isStreamCachingInUse();
        if (!streamCachingInUse) {
            for (RouteDefinition route : model.getRouteDefinitions()) {
                Boolean routeCache = CamelContextHelper.parseBoolean(this, route.getStreamCache());
                if (routeCache != null && routeCache) {
                    streamCachingInUse = true;
                    break;
                }
            }
        }
        return streamCachingInUse;
    }

    @Override
    public void startRouteDefinitions() throws Exception {
        List<RouteDefinition> routeDefinitions = model.getRouteDefinitions();
        if (routeDefinitions != null) {
            // defensive copy of routes to be started as kamelets
            // can add route definitions from existing routes
            List<RouteDefinition> toBeStarted = new ArrayList<>(routeDefinitions);
            startRouteDefinitions(toBeStarted);
        }
    }

    @Override
    public void removeRouteDefinitionsFromTemplate() throws Exception {
        List<RouteDefinition> toBeRemoved = new ArrayList<>();
        for (RouteDefinition rd : model.getRouteDefinitions()) {
            if (rd.isTemplate() != null && rd.isTemplate()) {
                toBeRemoved.add(rd);
            }
        }
        removeRouteDefinitions(toBeRemoved);
    }

    public void startRouteDefinitions(List<RouteDefinition> routeDefinitions) throws Exception {
        // indicate we are staring the route using this thread so
        // we are able to query this if needed
        boolean alreadyStartingRoutes = isStartingRoutes();
        if (!alreadyStartingRoutes) {
            setStartingRoutes(true);
        }

        PropertiesComponent pc = getCamelContextReference().getPropertiesComponent();
        // route templates supports binding beans that are local for the template only
        // in this local mode then we need to check for side-effects (see further)
        LocalBeanRepositoryAware localBeans = null;
        if (getCamelContextReference().getRegistry() instanceof LocalBeanRepositoryAware) {
            localBeans = (LocalBeanRepositoryAware) getCamelContextReference().getRegistry();
        }
        try {
            RouteDefinitionHelper.forceAssignIds(getCamelContextReference(), routeDefinitions);
            List<RouteDefinition> routeDefinitionsToRemove = null;
            for (RouteDefinition routeDefinition : routeDefinitions) {
                // assign ids to the routes and validate that the id's is all unique
                String duplicate = RouteDefinitionHelper.validateUniqueIds(routeDefinition, routeDefinitions,
                        routeDefinition.getNodePrefixId());
                if (duplicate != null) {
                    throw new FailedToStartRouteException(
                            routeDefinition.getId(),
                            "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
                }

                // if the route definition was created via a route template then we need to prepare its parameters when the route is being created and started
                if (routeDefinition.isTemplate() != null && routeDefinition.isTemplate()
                        && routeDefinition.getTemplateParameters() != null) {

                    // apply configurer if any present
                    if (routeDefinition.getRouteTemplateContext().getConfigurer() != null) {
                        routeDefinition.getRouteTemplateContext().getConfigurer()
                                .accept(routeDefinition.getRouteTemplateContext());
                    }

                    // copy parameters/bean repository to not cause side effect
                    Map<Object, Object> params = new HashMap<>(routeDefinition.getTemplateParameters());
                    LocalBeanRegistry bbr
                            = (LocalBeanRegistry) routeDefinition.getRouteTemplateContext().getLocalBeanRepository();
                    LocalBeanRegistry bbrCopy = new LocalBeanRegistry();

                    // make all bean in the bean repository use unique keys (need to add uuid counter)
                    // so when the route template is used again to create another route, then there is
                    // no side-effect from previously used values that Camel may use in its endpoint
                    // registry and elsewhere
                    if (bbr != null && !bbr.isEmpty()) {
                        for (Map.Entry<Object, Object> param : params.entrySet()) {
                            Object value = param.getValue();
                            if (value instanceof String) {
                                String oldKey = (String) value;
                                boolean clash = bbr.keys().stream().anyMatch(k -> k.equals(oldKey));
                                if (clash) {
                                    String newKey = oldKey + "-" + UUID.generateUuid();
                                    LOG.debug(
                                            "Route: {} re-assigning local-bean id: {} to: {} to ensure ids are globally unique",
                                            routeDefinition.getId(), oldKey, newKey);
                                    bbrCopy.put(newKey, bbr.remove(oldKey));
                                    param.setValue(newKey);
                                }
                            }
                        }
                        // the remainder of the local beans must also have their ids made global unique
                        for (Map.Entry<String, Map<Class<?>, Object>> entry : bbr.entrySet()) {
                            String oldKey = entry.getKey();
                            String newKey = oldKey + "-" + UUID.generateUuid();
                            LOG.debug(
                                    "Route: {} re-assigning local-bean id: {} to: {} to ensure ids are globally unique",
                                    routeDefinition.getId(), oldKey, newKey);
                            bbrCopy.put(newKey, entry.getValue());
                            if (!params.containsKey(oldKey)) {
                                // if a bean was bound as local bean with a key and it was not defined as template parameter
                                // then store it as if it was a template parameter with same key=value which allows us
                                // to use this local bean in the route without any problem such as:
                                //   to("bean:{{myBean}}")
                                // and myBean is the local bean id.
                                params.put(oldKey, newKey);
                            }
                        }
                    }

                    OrderedLocationProperties prop = new OrderedLocationProperties();
                    if (routeDefinition.getTemplateDefaultParameters() != null) {
                        // need to keep track if a parameter is set as default value or end user configured value
                        params.forEach((k, v) -> {
                            Object dv = routeDefinition.getTemplateDefaultParameters().get(k);
                            prop.put(routeDefinition.getLocation(), k, v, dv);
                        });
                    } else {
                        prop.putAll(routeDefinition.getLocation(), params);
                    }
                    pc.setLocalProperties(prop);

                    // we need to shadow the bean registry on the CamelContext with the local beans from the route template context
                    if (localBeans != null && bbrCopy != null) {
                        localBeans.setLocalBeanRepository(bbrCopy);
                    }

                    // need to reset auto assigned ids, so there is no clash when creating routes
                    ProcessorDefinitionHelper.resetAllAutoAssignedNodeIds(routeDefinition);
                    // must re-init parent when created from a template
                    RouteDefinitionHelper.initParent(routeDefinition);
                }
                // Check if the route is included
                if (includedRoute(routeDefinition)) {
                    // must ensure route is prepared, before we can start it
                    if (!routeDefinition.isPrepared()) {
                        RouteDefinitionHelper.prepareRoute(getCamelContextReference(), routeDefinition);
                        routeDefinition.markPrepared();
                    }

                    StartupStepRecorder recorder
                            = getCamelContextReference().getCamelContextExtension().getStartupStepRecorder();
                    StartupStep step = recorder.beginStep(Route.class, routeDefinition.getRouteId(), "Create Route");
                    Route route = model.getModelReifierFactory().createRoute(this, routeDefinition);
                    recorder.endStep(step);

                    RouteService routeService = new RouteService(route);
                    startRouteService(routeService, true);
                } else {
                    // Add the definition to the list of definitions to remove as the route is excluded
                    if (routeDefinitionsToRemove == null) {
                        routeDefinitionsToRemove = new ArrayList<>(routeDefinitions.size());
                    }
                    routeDefinitionsToRemove.add(routeDefinition);
                }

                // clear local after the route is created via the reifier
                pc.setLocalProperties(null);
                if (localBeans != null) {
                    localBeans.setLocalBeanRepository(null);
                }
            }
            if (routeDefinitionsToRemove != null) {
                // Remove all the excluded routes
                model.removeRouteDefinitions(routeDefinitionsToRemove);
            }
        } finally {
            if (!alreadyStartingRoutes) {
                setStartingRoutes(false);
            }
            pc.setLocalProperties(null);
            if (localBeans != null) {
                localBeans.setLocalBeanRepository(null);
            }
        }
    }

    @Override
    protected ExecutorServiceManager createExecutorServiceManager() {
        return new DefaultExecutorServiceManager(this);
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        return model.getModelReifierFactory().createErrorHandler(route, processor);
    }

    @Override
    public Expression createExpression(ExpressionDefinition definition) {
        return model.getModelReifierFactory().createExpression(this, definition);
    }

    @Override
    public Predicate createPredicate(ExpressionDefinition definition) {
        return model.getModelReifierFactory().createPredicate(this, definition);
    }

    @Override
    public void registerValidator(ValidatorDefinition def) {
        model.getValidators().add(def);
        Validator validator = model.getModelReifierFactory().createValidator(this, def);
        getValidatorRegistry().put(createValidatorKey(def), validator);
    }

    private static ValueHolder<String> createValidatorKey(ValidatorDefinition def) {
        return new ValidatorKey(new DataType(def.getType()));
    }

    @Override
    public void registerTransformer(TransformerDefinition def) {
        model.getTransformers().add(def);
        Transformer transformer = model.getModelReifierFactory().createTransformer(this, def);
        getTransformerRegistry().put(createTransformerKey(def), transformer);
    }

    @Override
    protected boolean removeRoute(String routeId, LoggingLevel loggingLevel) throws Exception {
        // synchronize on model first to avoid deadlock with concurrent 'addRoutes' calls:
        synchronized (model) {
            synchronized (this) {
                boolean removed = super.removeRoute(routeId, loggingLevel);
                if (removed) {
                    // must also remove the route definition
                    RouteDefinition def = getRouteDefinition(routeId);
                    if (def != null) {
                        removeRouteDefinition(def);
                    }
                }
                return removed;
            }
        }
    }

    @Override
    public boolean removeRoute(String routeId) throws Exception {
        // synchronize on model first to avoid deadlock with concurrent 'addRoutes' calls:
        synchronized (model) {
            return super.removeRoute(routeId);
        }
    }

    /**
     * Indicates whether the route should be included according to the precondition.
     *
     * @param  definition the definition of the route to check.
     * @return            {@code true} if the route should be included, {@code false} otherwise.
     */
    private boolean includedRoute(RouteDefinition definition) {
        return PreconditionHelper.included(definition, this);
    }

    private static ValueHolder<String> createTransformerKey(TransformerDefinition def) {
        if (ObjectHelper.isNotEmpty(def.getScheme())) {
            return ObjectHelper.isNotEmpty(def.getName())
                    ? new TransformerKey(def.getScheme() + ":" + def.getName()) : new TransformerKey(def.getScheme());
        }
        if (ObjectHelper.isNotEmpty(def.getName())) {
            return new TransformerKey(def.getName());
        } else {
            return new TransformerKey(new DataType(def.getFromType()), new DataType(def.getToType()));
        }
    }

}
