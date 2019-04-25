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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilderSupport;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.reifier.dataformat.DataFormatReifier;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.CamelContextHelper;

/**
 * Represents the context used to configure routes and the policies to use.
 */
public abstract class AbstractModelCamelContext extends AbstractCamelContext implements ModelCamelContext, CatalogCamelContext {

    private final List<RouteDefinition> routeDefinitions = new ArrayList<>();
    private final List<RestDefinition> restDefinitions = new ArrayList<>();
    private Map<String, DataFormatDefinition> dataFormats = new HashMap<>();
    private List<TransformerDefinition> transformers = new ArrayList<>();
    private List<ValidatorDefinition> validators = new ArrayList<>();
    private Map<String, ServiceCallConfigurationDefinition> serviceCallConfigurations = new ConcurrentHashMap<>();
    private Map<String, HystrixConfigurationDefinition> hystrixConfigurations = new ConcurrentHashMap<>();

    /**
     * Creates the {@link CamelContext} using
     * {@link org.apache.camel.support.DefaultRegistry} as registry.
     * <p/>
     * Use one of the other constructors to force use an explicit registry.
     */
    public AbstractModelCamelContext() {
        this(true);
    }

    /**
     * Creates the {@link CamelContext} using the given registry
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

    public void addRouteDefinitions(InputStream is) throws Exception {
        RoutesDefinition def = ModelHelper.loadRoutesDefinition(this, is);
        if (def != null) {
            addRouteDefinitions(def.getRoutes());
        }
    }

    public synchronized void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (routeDefinitions == null || routeDefinitions.isEmpty()) {
            return;
        }
        removeRouteDefinitions(routeDefinitions);
        this.routeDefinitions.addAll(routeDefinitions);
        if (shouldStartRoutes()) {
            startRouteDefinitions(routeDefinitions);
        }
    }

    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        addRouteDefinitions(Collections.singletonList(routeDefinition));
    }

    public synchronized void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
    }

    public synchronized void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        RouteDefinition toBeRemoved = routeDefinition;
        String id = routeDefinition.getId();
        if (id != null) {
            // remove existing route
            stopRoute(id);
            removeRoute(id);
            toBeRemoved = getRouteDefinition(id);
        }
        this.routeDefinitions.remove(toBeRemoved);
    }

    public synchronized List<RouteDefinition> getRouteDefinitions() {
        return routeDefinitions;
    }

    public synchronized RouteDefinition getRouteDefinition(String id) {
        for (RouteDefinition route : routeDefinitions) {
            if (route.idOrCreate(getNodeIdFactory()).equals(id)) {
                return route;
            }
        }
        return null;
    }

    public synchronized List<RestDefinition> getRestDefinitions() {
        return restDefinitions;
    }

    public void addRestDefinitions(InputStream is, boolean addToRoutes) throws Exception {
        RestsDefinition rests = ModelHelper.loadRestsDefinition(this, is);
        if (rests != null) {
            addRestDefinitions(rests.getRests(), addToRoutes);
        }
    }

    public synchronized void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception {
        if (restDefinitions == null || restDefinitions.isEmpty()) {
            return;
        }

        this.restDefinitions.addAll(restDefinitions);
        if (addToRoutes) {
            // rests are also routes so need to add them there too
            for (final RestDefinition restDefinition : restDefinitions) {
                List<RouteDefinition> routeDefinitions = restDefinition.asRouteDefinition(this);
                addRouteDefinitions(routeDefinitions);
            }
        }
    }

    @Override
    public ServiceCallConfigurationDefinition getServiceCallConfiguration(String serviceName) {
        if (serviceName == null) {
            serviceName = "";
        }

        return serviceCallConfigurations.get(serviceName);
    }

    @Override
    public void setServiceCallConfiguration(ServiceCallConfigurationDefinition configuration) {
        serviceCallConfigurations.put("", configuration);
    }

    @Override
    public void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> configurations) {
        if (configurations != null) {
            for (ServiceCallConfigurationDefinition configuration : configurations) {
                serviceCallConfigurations.put(configuration.getId(), configuration);
            }
        }
    }

    @Override
    public void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration) {
        serviceCallConfigurations.put(serviceName, configuration);
    }

    @Override
    public HystrixConfigurationDefinition getHystrixConfiguration(String id) {
        if (id == null) {
            id = "";
        }

        return hystrixConfigurations.get(id);
    }

    @Override
    public void setHystrixConfiguration(HystrixConfigurationDefinition configuration) {
        hystrixConfigurations.put("", configuration);
    }

    @Override
    public void setHystrixConfigurations(List<HystrixConfigurationDefinition> configurations) {
        if (configurations != null) {
            for (HystrixConfigurationDefinition configuration : configurations) {
                hystrixConfigurations.put(configuration.getId(), configuration);
            }
        }
    }

    @Override
    public void addHystrixConfiguration(String id, HystrixConfigurationDefinition configuration) {
        hystrixConfigurations.put(id, configuration);
    }

    @Override
    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        // lookup type and create the data format from it
        DataFormatDefinition type = lookup(this, name, DataFormatDefinition.class);
        if (type == null && getDataFormats() != null) {
            type = getDataFormats().get(name);
        }
        return type;
    }

    @Override
    public ProcessorDefinition getProcessorDefinition(String id) {
        for (RouteDefinition route : getRouteDefinitions()) {
            Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
            while (it.hasNext()) {
                ProcessorDefinition proc = it.next();
                if (id.equals(proc.getId())) {
                    return proc;
                }
            }
        }
        return null;
    }

    @Override
    public <T extends ProcessorDefinition> T getProcessorDefinition(String id, Class<T> type) {
        ProcessorDefinition answer = getProcessorDefinition(id);
        if (answer != null) {
            return type.cast(answer);
        }
        return null;
    }

    @Override
    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        this.dataFormats = dataFormats;
    }

    @Override
    public Map<String, DataFormatDefinition> getDataFormats() {
        return dataFormats;
    }

    @Override
    public void setTransformers(List<TransformerDefinition> transformers) {
        this.transformers = transformers;
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return transformers;
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        this.validators = validators;
    }

    @Override
    public List<ValidatorDefinition> getValidators() {
        return validators;
    }

    protected abstract HealthCheckRegistry createHealthCheckRegistry();

    protected abstract RuntimeCamelCatalog createRuntimeCamelCatalog();

    protected void doStartStandardServices() {
        super.doStartStandardServices();
        getExtension(RuntimeCamelCatalog.class);
    }

    protected void doStartEagerServices() {
        getExtension(HealthCheckRegistry.class);
        super.doStartEagerServices();
    }

    protected void startRouteDefinitions(Collection<RouteDefinition> list) throws Exception {
        if (list != null) {
            for (RouteDefinition route : list) {
                startRoute(route);
            }
        }
    }

    protected void bindDataFormats() throws Exception {
        // eager lookup data formats and bind to registry so the dataformats can
        // be looked up and used
        for (Map.Entry<String, DataFormatDefinition> e : dataFormats.entrySet()) {
            String id = e.getKey();
            DataFormatDefinition def = e.getValue();
            log.debug("Creating Dataformat with id: {} and definition: {}", id, def);
            DataFormat df = DataFormatReifier.reifier(def).createDataFormat(this);
            addService(df, true);
            getRegistry().bind(id, df);
        }
    }

    public void startRoute(RouteDefinition routeDefinition) throws Exception {
        // assign ids to the routes and validate that the id's is all unique
        RouteDefinitionHelper.forceAssignIds(this, routeDefinitions);
        String duplicate = RouteDefinitionHelper.validateUniqueIds(routeDefinition, routeDefinitions);
        if (duplicate != null) {
            throw new FailedToStartRouteException(routeDefinition.getId(), "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
        }

        // indicate we are staring the route using this thread so
        // we are able to query this if needed
        setStartingRoutes(true);
        try {
            // must ensure route is prepared, before we can start it
            routeDefinition.prepare(this);

            Route route = new RouteReifier(routeDefinition).addRoutes(this);
            RouteService routeService = new RouteService(this, routeDefinition, route.getRouteContext(), route);
            startRouteService(routeService, true);
        } finally {
            // we are done staring routes
            setStartingRoutes(false);
        }
    }

    protected synchronized void shutdownRouteService(RouteService routeService) throws Exception {
        // remove the route from ErrorHandlerBuilder if possible
        if (getErrorHandlerFactory() instanceof ErrorHandlerBuilderSupport) {
            ErrorHandlerBuilderSupport builder = (ErrorHandlerBuilderSupport)getErrorHandlerFactory();
            builder.removeOnExceptionList(routeService.getId());
        }
        routeDefinitions.removeIf(route -> route == routeService.getRouteDefinition());
        super.shutdownRouteService(routeService);
    }

    protected boolean isStreamCachingInUse() throws Exception {
        boolean streamCachingInUse = super.isStreamCachingInUse();
        if (!streamCachingInUse) {
            for (RouteDefinition route : routeDefinitions) {
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
    protected void startRouteDefinitions() throws Exception {
        startRouteDefinitions(routeDefinitions);
    }
}
