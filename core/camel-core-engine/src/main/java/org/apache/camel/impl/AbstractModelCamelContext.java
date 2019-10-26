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
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.engine.AbstractCamelContext;
import org.apache.camel.impl.engine.BaseRouteService;
import org.apache.camel.impl.engine.DefaultTransformerRegistry;
import org.apache.camel.impl.engine.DefaultValidatorRegistry;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.reifier.dataformat.DataFormatReifier;
import org.apache.camel.reifier.transformer.TransformerReifier;
import org.apache.camel.reifier.validator.ValidatorReifier;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public abstract class AbstractModelCamelContext extends AbstractCamelContext implements ModelCamelContext, CatalogCamelContext {

    private final Model model = new DefaultModel(this);

    /**
     * Creates the {@link ModelCamelContext} using
     * {@link org.apache.camel.support.DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public AbstractModelCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link ModelCamelContext} using the given registry
     *
     * @param registry the registry
     */
    public AbstractModelCamelContext(Registry registry) {
        this();
        setRegistry(registry);
    }

    public AbstractModelCamelContext(boolean init) {
        super(false);

        setDefaultExtension(HealthCheckRegistry.class, this::createHealthCheckRegistry);
        setDefaultExtension(RuntimeCamelCatalog.class, this::createRuntimeCamelCatalog);

        if (init) {
            init();
        }
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
        model.removeRouteDefinitions(routeDefinitions);
    }

    @Override
    public void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        model.removeRouteDefinition(routeDefinition);
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
    public ProcessorDefinition getProcessorDefinition(String id) {
        return model.getProcessorDefinition(id);
    }

    @Override
    public <T extends ProcessorDefinition> T getProcessorDefinition(String id, Class<T> type) {
        return model.getProcessorDefinition(id, type);
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        model.setValidators(validators);
    }

    @Override
    public HystrixConfigurationDefinition getHystrixConfiguration(String id) {
        return model.getHystrixConfiguration(id);
    }

    @Override
    public void setHystrixConfiguration(HystrixConfigurationDefinition configuration) {
        model.setHystrixConfiguration(configuration);
    }

    @Override
    public void setHystrixConfigurations(List<HystrixConfigurationDefinition> configurations) {
        model.setHystrixConfigurations(configurations);
    }

    @Override
    public void addHystrixConfiguration(String id, HystrixConfigurationDefinition configuration) {
        model.addHystrixConfiguration(id, configuration);
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
    protected ValidatorRegistry<ValidatorKey> createValidatorRegistry() throws Exception {
        DefaultValidatorRegistry registry = new DefaultValidatorRegistry(this);
        for (ValidatorDefinition def : getValidators()) {
            Validator validator = ValidatorReifier.reifier(def).createValidator(this);
            registry.put(createKey(def), doAddService(validator));
        }
        return registry;
    }

    private ValidatorKey createKey(ValidatorDefinition def) {
        return new ValidatorKey(new DataType(def.getType()));
    }

    @Override
    protected TransformerRegistry<TransformerKey> createTransformerRegistry() throws Exception {
        DefaultTransformerRegistry registry = new DefaultTransformerRegistry(this);
        for (TransformerDefinition def : getTransformers()) {
            Transformer transformer = TransformerReifier.reifier(def).createTransformer(this);
            registry.put(createKey(def), doAddService(transformer));
        }
        return registry;
    }

    private TransformerKey createKey(TransformerDefinition def) {
        return ObjectHelper.isNotEmpty(def.getScheme()) ? new TransformerKey(def.getScheme()) : new TransformerKey(new DataType(def.getFromType()), new DataType(def.getToType()));
    }

    protected abstract HealthCheckRegistry createHealthCheckRegistry();

    protected abstract RuntimeCamelCatalog createRuntimeCamelCatalog();

    @Override
    protected void doStartStandardServices() {
        super.doStartStandardServices();
        getExtension(RuntimeCamelCatalog.class);
    }

    @Override
    protected void doStartEagerServices() {
        getExtension(HealthCheckRegistry.class);
        super.doStartEagerServices();
    }

    @Override
    protected void bindDataFormats() throws Exception {
        // eager lookup data formats and bind to registry so the dataformats can
        // be looked up and used
        for (Map.Entry<String, DataFormatDefinition> e : model.getDataFormats().entrySet()) {
            String id = e.getKey();
            DataFormatDefinition def = e.getValue();
            log.debug("Creating Dataformat with id: {} and definition: {}", id, def);
            DataFormat df = DataFormatReifier.reifier(def).createDataFormat(this);
            addService(df, true);
            getRegistry().bind(id, df);
        }
    }

    @Override
    protected synchronized void shutdownRouteService(BaseRouteService routeService) throws Exception {
        if (routeService instanceof RouteService) {
            model.getRouteDefinitions().remove(((RouteService)routeService).getRouteDefinition());
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
        model.startRouteDefinitions();
    }

    @Override
    public AsyncProcessor createMulticast(Collection<Processor> processors, ExecutorService executor, boolean shutdownExecutorService) {
        return new MulticastProcessor(this, processors, null, true, executor, shutdownExecutorService, false, false, 0, null, false, false);
    }

}
