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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.ValueHolder;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.impl.engine.RouteService;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.impl.engine.ValidatorKey;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public class DefaultCamelContext extends SimpleCamelContext implements ModelCamelContext {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContext.class);

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
        setRegistry(registry);
    }

    public DefaultCamelContext(boolean init) {
        super(init);
    }

    @Override
    public void disposeModel() {
        LOG.debug("Disposing Model on CamelContext");
        model = null;
    }

    @Override
    public void addModelLifecycleStrategy(ModelLifecycleStrategy modelLifecycleStrategy) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addModelLifecycleStrategy(modelLifecycleStrategy);
    }

    @Override
    public List<ModelLifecycleStrategy> getModelLifecycleStrategies() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getModelLifecycleStrategies();
    }

    @Override
    public List<RouteDefinition> getRouteDefinitions() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getRouteDefinitions();
    }

    @Override
    public RouteDefinition getRouteDefinition(String id) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getRouteDefinition(id);
    }

    @Override
    public void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addRouteDefinitions(routeDefinitions);
    }

    @Override
    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addRouteDefinition(routeDefinition);
    }

    @Override
    public void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.removeRouteDefinitions(routeDefinitions);
    }

    @Override
    public void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.removeRouteDefinition(routeDefinition);
    }

    @Override
    public List<RouteTemplateDefinition> getRouteTemplateDefinitions() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getRouteTemplateDefinitions();
    }

    @Override
    public RouteTemplateDefinition getRouteTemplateDefinition(String id) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getRouteTemplateDefinition(id);
    }

    @Override
    public void addRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addRouteTemplateDefinitions(routeTemplateDefinitions);
    }

    @Override
    public void addRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addRouteTemplateDefinition(routeTemplateDefinition);
    }

    @Override
    public void removeRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.removeRouteTemplateDefinitions(routeTemplateDefinitions);
    }

    @Override
    public void removeRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.removeRouteTemplateDefinition(routeTemplateDefinition);
    }

    @Override
    public void addRouteTemplateDefinitionConverter(String templateIdPattern, RouteTemplateDefinition.Converter converter) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addRouteTemplateDefinitionConverter(templateIdPattern, converter);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters)
            throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.addRouteFromTemplate(routeId, routeTemplateId, parameters);
    }

    @Override
    public List<RestDefinition> getRestDefinitions() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getRestDefinitions();
    }

    @Override
    public void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addRestDefinitions(restDefinitions, addToRoutes);
    }

    @Override
    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setDataFormats(dataFormats);
    }

    @Override
    public Map<String, DataFormatDefinition> getDataFormats() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getDataFormats();
    }

    @Override
    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.resolveDataFormatDefinition(name);
    }

    @Override
    public ProcessorDefinition<?> getProcessorDefinition(String id) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getProcessorDefinition(id);
    }

    @Override
    public <T extends ProcessorDefinition<T>> T getProcessorDefinition(String id, Class<T> type) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getProcessorDefinition(id, type);
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setValidators(validators);
    }

    @Override
    public HystrixConfigurationDefinition getHystrixConfiguration(String id) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getHystrixConfiguration(id);
    }

    @Override
    public void setHystrixConfiguration(HystrixConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setHystrixConfiguration(configuration);
    }

    @Override
    public void setHystrixConfigurations(List<HystrixConfigurationDefinition> configurations) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setHystrixConfigurations(configurations);
    }

    @Override
    public void addHystrixConfiguration(String id, HystrixConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addHystrixConfiguration(id, configuration);
    }

    @Override
    public Resilience4jConfigurationDefinition getResilience4jConfiguration(String id) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getResilience4jConfiguration(id);
    }

    @Override
    public void setResilience4jConfiguration(Resilience4jConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setResilience4jConfiguration(configuration);
    }

    @Override
    public void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> configurations) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setResilience4jConfigurations(configurations);
    }

    @Override
    public void addResilience4jConfiguration(String id, Resilience4jConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addResilience4jConfiguration(id, configuration);
    }

    @Override
    public FaultToleranceConfigurationDefinition getFaultToleranceConfiguration(String id) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getFaultToleranceConfiguration(id);
    }

    @Override
    public void setFaultToleranceConfiguration(FaultToleranceConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setFaultToleranceConfiguration(configuration);
    }

    @Override
    public void setFaultToleranceConfigurations(List<FaultToleranceConfigurationDefinition> configurations) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setFaultToleranceConfigurations(configurations);
    }

    @Override
    public void addFaultToleranceConfiguration(String id, FaultToleranceConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addFaultToleranceConfiguration(id, configuration);
    }

    @Override
    public List<ValidatorDefinition> getValidators() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getValidators();
    }

    @Override
    public void setTransformers(List<TransformerDefinition> transformers) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setTransformers(transformers);
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getTransformers();
    }

    @Override
    public ServiceCallConfigurationDefinition getServiceCallConfiguration(String serviceName) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getServiceCallConfiguration(serviceName);
    }

    @Override
    public void setServiceCallConfiguration(ServiceCallConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setServiceCallConfiguration(configuration);
    }

    @Override
    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> configurations) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setServiceCallConfigurations(configurations);
    }

    @Override
    public void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.addServiceCallConfiguration(serviceName, configuration);
    }

    @Override
    public void setRouteFilterPattern(String include, String exclude) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setRouteFilterPattern(include, exclude);
    }

    @Override
    public void setRouteFilter(Function<RouteDefinition, Boolean> filter) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setRouteFilter(filter);
    }

    @Override
    public Function<RouteDefinition, Boolean> getRouteFilter() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getRouteFilter();
    }

    @Override
    public ModelReifierFactory getModelReifierFactory() {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getModelReifierFactory();
    }

    @Override
    public void setModelReifierFactory(ModelReifierFactory modelReifierFactory) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.setModelReifierFactory(modelReifierFactory);
    }

    @Override
    protected void doStartStandardServices() {
        super.doStartStandardServices();
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
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        List<RouteDefinition> routeDefinitions = model.getRouteDefinitions();
        if (routeDefinitions != null) {
            startRouteDefinitions(routeDefinitions);
        }
    }

    public void startRouteDefinitions(List<RouteDefinition> routeDefinitions) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }

        // indicate we are staring the route using this thread so
        // we are able to query this if needed
        boolean alreadyStartingRoutes = isStartingRoutes();
        if (!alreadyStartingRoutes) {
            setStartingRoutes(true);
        }

        PropertiesComponent pc = getCamelContextReference().getPropertiesComponent();
        try {
            RouteDefinitionHelper.forceAssignIds(getCamelContextReference(), routeDefinitions);
            for (RouteDefinition routeDefinition : routeDefinitions) {
                // assign ids to the routes and validate that the id's is all unique
                String duplicate = RouteDefinitionHelper.validateUniqueIds(routeDefinition, routeDefinitions);
                if (duplicate != null) {
                    throw new FailedToStartRouteException(
                            routeDefinition.getId(),
                            "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
                }

                // if the route definition was created via a route template then we need to prepare its parameters when the route is being created and started
                if (routeDefinition.isTemplate() != null && routeDefinition.isTemplate()
                        && routeDefinition.getTemplateParameters() != null) {
                    Properties prop = new Properties();
                    prop.putAll(routeDefinition.getTemplateParameters());
                    pc.setLocalProperties(prop);

                    // need to reset auto assigned ids, so there is no clash when creating routes
                    ProcessorDefinitionHelper.resetAllAutoAssignedNodeIds(routeDefinition);
                }

                // must ensure route is prepared, before we can start it
                if (!routeDefinition.isPrepared()) {
                    RouteDefinitionHelper.prepareRoute(getCamelContextReference(), routeDefinition);
                    routeDefinition.markPrepared();
                }

                Route route = model.getModelReifierFactory().createRoute(this, routeDefinition);
                RouteService routeService = new RouteService(route);
                startRouteService(routeService, true);

                // clear local after the route is created via the reifier
                pc.setLocalProperties(null);
            }
        } finally {
            if (!alreadyStartingRoutes) {
                setStartingRoutes(false);
            }
            pc.setLocalProperties(null);
        }
    }

    @Override
    protected ExecutorServiceManager createExecutorServiceManager() {
        return new DefaultExecutorServiceManager(this);
    }

    @Override
    public Processor createErrorHandler(Route route, Processor processor) throws Exception {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getModelReifierFactory().createErrorHandler(route, processor);
    }

    @Override
    public Expression createExpression(ExpressionDefinition definition) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getModelReifierFactory().createExpression(this, definition);
    }

    @Override
    public Predicate createPredicate(ExpressionDefinition definition) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        return model.getModelReifierFactory().createPredicate(this, definition);
    }

    @Override
    public RouteDefinition adviceWith(RouteDefinition definition, AdviceWithRouteBuilder builder) throws Exception {
        return AdviceWith.adviceWith(definition, this, builder);
    }

    @Override
    public void registerValidator(ValidatorDefinition def) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.getValidators().add(def);
        Validator validator = model.getModelReifierFactory().createValidator(this, def);
        getValidatorRegistry().put(createValidatorKey(def), validator);
    }

    private static ValueHolder<String> createValidatorKey(ValidatorDefinition def) {
        return new ValidatorKey(new DataType(def.getType()));
    }

    @Override
    public void registerTransformer(TransformerDefinition def) {
        if (model == null && isLightweight()) {
            throw new IllegalStateException("Access to model not supported in lightweight mode");
        }
        model.getTransformers().add(def);
        Transformer transformer = model.getModelReifierFactory().createTransformer(this, def);
        getTransformerRegistry().put(createTransformerKey(def), transformer);
    }

    private static ValueHolder<String> createTransformerKey(TransformerDefinition def) {
        return ObjectHelper.isNotEmpty(def.getScheme())
                ? new TransformerKey(def.getScheme())
                : new TransformerKey(new DataType(def.getFromType()), new DataType(def.getToType()));
    }
}
