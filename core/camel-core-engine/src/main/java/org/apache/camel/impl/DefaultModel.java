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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteFilters;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;

public class DefaultModel implements Model {

    private final CamelContext camelContext;

    private final List<RouteDefinition> routeDefinitions = new ArrayList<>();
    private final List<RestDefinition> restDefinitions = new ArrayList<>();
    private Map<String, DataFormatDefinition> dataFormats = new HashMap<>();
    private List<TransformerDefinition> transformers = new ArrayList<>();
    private List<ValidatorDefinition> validators = new ArrayList<>();
    private Map<String, ServiceCallConfigurationDefinition> serviceCallConfigurations = new ConcurrentHashMap<>();
    private Map<String, HystrixConfigurationDefinition> hystrixConfigurations = new ConcurrentHashMap<>();
    private Map<String, Resilience4jConfigurationDefinition> resilience4jConfigurations = new ConcurrentHashMap<>();
    private Function<RouteDefinition, Boolean> routeFilter;

    public DefaultModel(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public synchronized void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (routeDefinitions == null || routeDefinitions.isEmpty()) {
            return;
        }
        List<RouteDefinition> list = new ArrayList<>();
        routeDefinitions.forEach(r -> {
            if (routeFilter == null || routeFilter.apply(r)) {
                list.add(r);
            }
        });

        removeRouteDefinitions(list);
        this.routeDefinitions.addAll(list);
        if (shouldStartRoutes()) {
            getCamelContext().adapt(ModelCamelContext.class).startRouteDefinitions(list);
        }
    }

    @Override
    public void addRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        addRouteDefinitions(Collections.singletonList(routeDefinition));
    }

    @Override
    public synchronized void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        for (RouteDefinition routeDefinition : routeDefinitions) {
            removeRouteDefinition(routeDefinition);
        }
    }

    @Override
    public synchronized void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception {
        RouteDefinition toBeRemoved = routeDefinition;
        String id = routeDefinition.getId();
        if (id != null) {
            // remove existing route
            camelContext.getRouteController().stopRoute(id);
            camelContext.removeRoute(id);
            toBeRemoved = getRouteDefinition(id);
        }
        this.routeDefinitions.remove(toBeRemoved);
    }

    @Override
    public synchronized List<RouteDefinition> getRouteDefinitions() {
        return routeDefinitions;
    }

    @Override
    public synchronized RouteDefinition getRouteDefinition(String id) {
        for (RouteDefinition route : routeDefinitions) {
            if (route.idOrCreate(camelContext.adapt(ExtendedCamelContext.class).getNodeIdFactory()).equals(id)) {
                return route;
            }
        }
        return null;
    }

    @Override
    public synchronized List<RestDefinition> getRestDefinitions() {
        return restDefinitions;
    }

    @Override
    public synchronized void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception {
        if (restDefinitions == null || restDefinitions.isEmpty()) {
            return;
        }

        this.restDefinitions.addAll(restDefinitions);
        if (addToRoutes) {
            // rests are also routes so need to add them there too
            for (final RestDefinition restDefinition : restDefinitions) {
                List<RouteDefinition> routeDefinitions = restDefinition.asRouteDefinition(camelContext);
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
    public Resilience4jConfigurationDefinition getResilience4jConfiguration(String id) {
        if (id == null) {
            id = "";
        }

        return resilience4jConfigurations.get(id);
    }

    @Override
    public void setResilience4jConfiguration(Resilience4jConfigurationDefinition configuration) {
        resilience4jConfigurations.put("", configuration);
    }

    @Override
    public void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> configurations) {
        if (configurations != null) {
            for (Resilience4jConfigurationDefinition configuration : configurations) {
                resilience4jConfigurations.put(configuration.getId(), configuration);
            }
        }
    }

    @Override
    public void addResilience4jConfiguration(String id, Resilience4jConfigurationDefinition configuration) {
        resilience4jConfigurations.put(id, configuration);
    }

    @Override
    public DataFormatDefinition resolveDataFormatDefinition(String name) {
        // lookup type and create the data format from it
        DataFormatDefinition type = lookup(camelContext, name, DataFormatDefinition.class);
        if (type == null && getDataFormats() != null) {
            type = getDataFormats().get(name);
        }
        return type;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ProcessorDefinition<?> getProcessorDefinition(String id) {
        for (RouteDefinition route : getRouteDefinitions()) {
            Iterator<ProcessorDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
            while (it.hasNext()) {
                ProcessorDefinition<?> proc = it.next();
                if (id.equals(proc.getId())) {
                    return proc;
                }
            }
        }
        return null;
    }

    @Override
    public <T extends ProcessorDefinition<T>> T getProcessorDefinition(String id, Class<T> type) {
        ProcessorDefinition<?> answer = getProcessorDefinition(id);
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

    @Override
    public void setRouteFilterPattern(String include, String exclude) {
        setRouteFilter(RouteFilters.filterByPattern(include, exclude));
    }

    @Override
    public Function<RouteDefinition, Boolean> getRouteFilter() {
        return routeFilter;
    }

    @Override
    public void setRouteFilter(Function<RouteDefinition, Boolean> routeFilter) {
        this.routeFilter = routeFilter;
    }

    /**
     * Should we start newly added routes?
     */
    protected boolean shouldStartRoutes() {
        return camelContext.isStarted() && !camelContext.isStarting();
    }

    protected static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookupByNameAndType(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
    }

}
