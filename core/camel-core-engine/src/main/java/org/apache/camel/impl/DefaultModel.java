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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateRouteFromTemplateException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.DefaultRouteTemplateContext;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RouteFilters;
import org.apache.camel.model.RouteTemplateBeanDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateParameterDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.RouteTemplateHelper;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;

public class DefaultModel implements Model {

    private final CamelContext camelContext;

    private ModelReifierFactory modelReifierFactory = new DefaultModelReifierFactory();
    private final List<ModelLifecycleStrategy> modelLifecycleStrategies = new ArrayList<>();
    private final List<RouteConfigurationDefinition> routesConfigurations = new ArrayList<>();
    private final List<RouteDefinition> routeDefinitions = new ArrayList<>();
    private final List<RouteTemplateDefinition> routeTemplateDefinitions = new ArrayList<>();
    private final List<RestDefinition> restDefinitions = new ArrayList<>();
    private final Map<String, RouteTemplateDefinition.Converter> routeTemplateConverters = new ConcurrentHashMap<>();
    private Map<String, DataFormatDefinition> dataFormats = new HashMap<>();
    private List<TransformerDefinition> transformers = new ArrayList<>();
    private List<ValidatorDefinition> validators = new ArrayList<>();
    private final Map<String, ServiceCallConfigurationDefinition> serviceCallConfigurations = new ConcurrentHashMap<>();
    private final Map<String, HystrixConfigurationDefinition> hystrixConfigurations = new ConcurrentHashMap<>();
    private final Map<String, Resilience4jConfigurationDefinition> resilience4jConfigurations = new ConcurrentHashMap<>();
    private final Map<String, FaultToleranceConfigurationDefinition> faultToleranceConfigurations = new ConcurrentHashMap<>();
    private Function<RouteDefinition, Boolean> routeFilter;

    public DefaultModel(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void addModelLifecycleStrategy(ModelLifecycleStrategy modelLifecycleStrategy) {
        // avoid adding double which can happen with spring xml on spring boot
        if (!this.modelLifecycleStrategies.contains(modelLifecycleStrategy)) {
            this.modelLifecycleStrategies.add(modelLifecycleStrategy);
        }
    }

    @Override
    public List<ModelLifecycleStrategy> getModelLifecycleStrategies() {
        return modelLifecycleStrategies;
    }

    @Override
    public void addRouteConfiguration(RouteConfigurationDefinition routesConfiguration) {
        if (routesConfiguration == null) {
            return;
        }
        // only add if not already exists (route-loader may let Java DSL add route configuration twice
        // because it extends RouteBuilder as base class)
        if (!this.routesConfigurations.contains(routesConfiguration)) {
            // check that there is no id clash
            if (routesConfiguration.getId() != null) {
                boolean clash = this.routesConfigurations.stream()
                        .anyMatch(r -> ObjectHelper.equal(r.getId(), routesConfiguration.getId()));
                if (clash) {
                    throw new IllegalArgumentException(
                            "Route configuration already exists with id: " + routesConfiguration.getId());
                }
            }
            this.routesConfigurations.add(routesConfiguration);
        }
    }

    @Override
    public void addRouteConfigurations(List<RouteConfigurationDefinition> routesConfigurations) {
        if (routesConfigurations == null || routesConfigurations.isEmpty()) {
            return;
        }
        // only add if not already exists (route-loader may let Java DSL add route configuration twice
        // because it extends RouteBuilder as base class)
        for (RouteConfigurationDefinition rc : routesConfigurations) {
            addRouteConfiguration(rc);
        }
    }

    @Override
    public List<RouteConfigurationDefinition> getRouteConfigurationDefinitions() {
        return routesConfigurations;
    }

    @Override
    public synchronized void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception {
        if (routeDefinitions == null || routeDefinitions.isEmpty()) {
            return;
        }

        List<RouteDefinition> list;
        if (routeFilter == null) {
            list = new ArrayList<>(routeDefinitions);
        } else {
            list = new ArrayList<>();
            for (RouteDefinition r : routeDefinitions) {
                if (routeFilter.apply(r)) {
                    list.add(r);
                }
            }
        }

        removeRouteDefinitions(list);

        for (RouteDefinition r : list) {
            for (ModelLifecycleStrategy s : modelLifecycleStrategies) {
                s.onAddRouteDefinition(r);
            }
            this.routeDefinitions.add(r);
        }

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
        for (ModelLifecycleStrategy s : modelLifecycleStrategies) {
            s.onRemoveRouteDefinition(toBeRemoved);
        }
        this.routeDefinitions.remove(toBeRemoved);
    }

    @Override
    public synchronized void removeRouteTemplateDefinitions(String pattern) throws Exception {
        for (RouteTemplateDefinition def : new ArrayList<>(routeTemplateDefinitions)) {
            if (PatternHelper.matchPattern(def.getId(), pattern)) {
                removeRouteTemplateDefinition(def);
            }
        }
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
    public List<RouteTemplateDefinition> getRouteTemplateDefinitions() {
        return routeTemplateDefinitions;
    }

    @Override
    public RouteTemplateDefinition getRouteTemplateDefinition(String id) {
        for (RouteTemplateDefinition route : routeTemplateDefinitions) {
            if (route.idOrCreate(camelContext.adapt(ExtendedCamelContext.class).getNodeIdFactory()).equals(id)) {
                return route;
            }
        }
        return null;
    }

    @Override
    public void addRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        if (routeTemplateDefinitions == null || routeTemplateDefinitions.isEmpty()) {
            return;
        }

        for (RouteTemplateDefinition r : routeTemplateDefinitions) {
            for (ModelLifecycleStrategy s : modelLifecycleStrategies) {
                s.onAddRouteTemplateDefinition(r);
            }
            this.routeTemplateDefinitions.add(r);
        }
    }

    @Override
    public void addRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        addRouteTemplateDefinitions(Collections.singletonList(routeTemplateDefinition));
    }

    @Override
    public void removeRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception {
        for (RouteTemplateDefinition r : routeTemplateDefinitions) {
            removeRouteTemplateDefinition(r);
        }
    }

    @Override
    public void removeRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception {
        for (ModelLifecycleStrategy s : modelLifecycleStrategies) {
            s.onRemoveRouteTemplateDefinition(routeTemplateDefinition);
        }
        routeTemplateDefinitions.remove(routeTemplateDefinition);
    }

    @Override
    public void addRouteTemplateDefinitionConverter(String templateIdPattern, RouteTemplateDefinition.Converter converter) {
        routeTemplateConverters.put(templateIdPattern, converter);
    }

    @Override
    @Deprecated
    public String addRouteFromTemplate(final String routeId, final String routeTemplateId, final Map<String, Object> parameters)
            throws Exception {
        RouteTemplateContext rtc = new DefaultRouteTemplateContext(camelContext);
        if (parameters != null) {
            parameters.forEach(rtc::setParameter);
        }
        return addRouteFromTemplate(routeId, routeTemplateId, rtc);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, RouteTemplateContext routeTemplateContext)
            throws Exception {
        RouteTemplateDefinition target = null;
        for (RouteTemplateDefinition def : routeTemplateDefinitions) {
            if (routeTemplateId.equals(def.getId())) {
                target = def;
                break;
            }
        }
        if (target == null) {
            // if the route template has a location parameter, then try to load route templates from the location
            // and look up again
            Object location = routeTemplateContext.getParameters().get(RouteTemplateParameterSource.LOCATION);
            if (location != null) {
                RouteTemplateLoaderListener listener
                        = CamelContextHelper.findByType(getCamelContext(), RouteTemplateLoaderListener.class);
                RouteTemplateHelper.loadRouteTemplateFromLocation(getCamelContext(), listener, routeTemplateId,
                        location.toString());
            }
            for (RouteTemplateDefinition def : routeTemplateDefinitions) {
                if (routeTemplateId.equals(def.getId())) {
                    target = def;
                    break;
                }
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Cannot find RouteTemplate with id " + routeTemplateId);
        }

        // support both camelCase and kebab-case keys
        final Map<String, Object> prop = new HashMap<>();
        // include default values first from the template (and validate that we have inputs for all required parameters)
        if (target.getTemplateParameters() != null) {
            StringJoiner templatesBuilder = new StringJoiner(", ");

            for (RouteTemplateParameterDefinition temp : target.getTemplateParameters()) {
                if (temp.getDefaultValue() != null) {
                    addProperty(prop, temp.getName(), temp.getDefaultValue());
                } else {
                    if (temp.isRequired() && !routeTemplateContext.hasParameter(temp.getName())) {
                        // this is a required parameter which is missing
                        templatesBuilder.add(temp.getName());
                    }
                }
            }
            if (templatesBuilder.length() > 0) {
                throw new IllegalArgumentException(
                        "Route template " + routeTemplateId + " the following mandatory parameters must be provided: "
                                                   + templatesBuilder);
            }
        }

        // then override with user parameters part 1
        if (routeTemplateContext.getParameters() != null) {
            routeTemplateContext.getParameters().forEach((k, v) -> addProperty(prop, k, v));
        }
        // route template context should include default template parameters from the target route template
        // so it has all parameters available
        if (target.getTemplateParameters() != null) {
            for (RouteTemplateParameterDefinition temp : target.getTemplateParameters()) {
                if (!routeTemplateContext.hasParameter(temp.getName()) && temp.getDefaultValue() != null) {
                    routeTemplateContext.setParameter(temp.getName(), temp.getDefaultValue());
                }
            }
        }

        RouteTemplateDefinition.Converter converter = RouteTemplateDefinition.Converter.DEFAULT_CONVERTER;

        for (Map.Entry<String, RouteTemplateDefinition.Converter> entry : routeTemplateConverters.entrySet()) {
            final String key = entry.getKey();
            final String templateId = target.getId();

            if ("*".equals(key) || templateId.equals(key)) {
                converter = entry.getValue();
                break;
            } else if (AntPathMatcher.INSTANCE.match(key, templateId)) {
                converter = entry.getValue();
                break;
            } else if (templateId.matches(key)) {
                converter = entry.getValue();
                break;
            }
        }

        RouteDefinition def = converter.apply(target, prop);
        if (routeId != null) {
            def.setId(routeId);
        }
        def.setTemplateParameters(prop);
        def.setRouteTemplateContext(routeTemplateContext);

        // setup local beans
        if (target.getTemplateBeans() != null) {
            addTemplateBeans(routeTemplateContext, target);
        }

        if (target.getConfigurer() != null) {
            routeTemplateContext.setConfigurer(target.getConfigurer());
        }

        // assign ids to the routes and validate that the id's are all unique
        String duplicate = RouteDefinitionHelper.validateUniqueIds(def, routeDefinitions);
        if (duplicate != null) {
            throw new FailedToCreateRouteFromTemplateException(
                    routeId, routeTemplateId,
                    "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
        }
        addRouteDefinition(def);
        return def.getId();
    }

    private static void addProperty(Map<String, Object> prop, String key, Object value) {
        prop.put(key, value);
        // support also camelCase and kebab-case because route templates (kamelets)
        // can be defined using different key styles
        key = StringHelper.dashToCamelCase(key);
        prop.put(key, value);
        key = StringHelper.camelCaseToDash(key);
        prop.put(key, value);
    }

    private void addTemplateBeans(RouteTemplateContext routeTemplateContext, RouteTemplateDefinition target) throws Exception {
        for (RouteTemplateBeanDefinition b : target.getTemplateBeans()) {
            final Map<String, Object> props = new HashMap<>();
            if (b.getProperties() != null) {
                b.getProperties().forEach(p -> props.put(p.getKey(), p.getValue()));
            }
            if (b.getBeanSupplier() != null) {
                if (props.isEmpty()) {
                    // bean class is optional for supplier
                    if (b.getBeanClass() != null) {
                        routeTemplateContext.bind(b.getName(), b.getBeanClass(), b.getBeanSupplier());
                    } else {
                        routeTemplateContext.bind(b.getName(), b.getBeanSupplier());
                    }
                }
            } else if (b.getScript() != null) {
                final String script = b.getScript().getScript();
                final Language lan = camelContext.resolveLanguage(b.getType());
                final Class<?> clazz = b.getBeanType() != null
                        ? camelContext.getClassResolver().resolveMandatoryClass(b.getBeanType())
                        : b.getBeanClass() != null ? b.getBeanClass() : Object.class;
                final ScriptingLanguage slan = lan instanceof ScriptingLanguage ? (ScriptingLanguage) lan : null;
                if (slan != null) {
                    // scripting language should be evaluated with route template context as binding
                    // and memorize so the script is only evaluated once and the local bean is the same
                    // if a route template refers to the local bean multiple times
                    routeTemplateContext.bind(b.getName(), clazz, Suppliers.memorize(() -> {
                        Map<String, Object> bindings = new HashMap<>();
                        // use rtx as the short-hand name, as context would imply its CamelContext
                        bindings.put("rtc", routeTemplateContext);
                        Object local = slan.evaluate(script, bindings, clazz);
                        if (!props.isEmpty()) {
                            setPropertiesOnTarget(camelContext, local, props);
                        }
                        return local;
                    }));
                } else {
                    // exchange based languages needs a dummy exchange to be evaluated
                    // and memorize so the script is only evaluated once and the local bean is the same
                    // if a route template refers to the local bean multiple times
                    routeTemplateContext.bind(b.getName(), clazz, Suppliers.memorize(() -> {
                        ExchangeFactory ef = camelContext.adapt(ExtendedCamelContext.class).getExchangeFactory();
                        Exchange dummy = ef.create(false);
                        try {
                            String text = ScriptHelper.resolveOptionalExternalScript(camelContext, dummy, script);
                            if (text != null) {
                                Expression exp = lan.createExpression(text);
                                Object local = exp.evaluate(dummy, clazz);
                                if (!props.isEmpty()) {
                                    setPropertiesOnTarget(camelContext, local, props);
                                }
                                return local;
                            } else {
                                return null;
                            }
                        } finally {
                            ef.release(dummy);
                        }
                    }));
                }
            } else if (b.getBeanClass() != null || b.getType() != null && b.getType().startsWith("#class:")) {
                // if there is a factory method then the class/bean should be created in a different way
                String className = null;
                String factoryMethod = null;
                String parameters = null;
                if (b.getType() != null) {
                    className = b.getType().substring(7);
                    if (className.endsWith(")") && className.indexOf('(') != -1) {
                        parameters = StringHelper.after(className, "(");
                        parameters = parameters.substring(0, parameters.length() - 1); // clip last )
                        className = StringHelper.before(className, "(");
                    }
                    if (className != null && className.indexOf('#') != -1) {
                        factoryMethod = StringHelper.after(className, "#");
                        className = StringHelper.before(className, "#");
                    }
                }
                if (className != null && (factoryMethod != null || parameters != null)) {
                    final Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(className);
                    final String fqn = className;
                    final String fm = factoryMethod;
                    final String fp = parameters;
                    routeTemplateContext.bind(b.getName(), Object.class, Suppliers.memorize(() -> {
                        try {
                            Object local;
                            if (fm != null) {
                                if (fp != null) {
                                    // special to support factory method parameters
                                    local = PropertyBindingSupport.newInstanceFactoryParameters(camelContext, clazz, fm, fp);
                                } else {
                                    local = camelContext.getInjector().newInstance(clazz, fm);
                                }
                                if (local == null) {
                                    throw new IllegalStateException(
                                            "Cannot create bean instance using factory method: " + fqn + "#" + fm);
                                }
                            } else {
                                // special to support constructor parameters
                                local = PropertyBindingSupport.newInstanceConstructorParameters(camelContext, clazz, fp);
                            }
                            if (!props.isEmpty()) {
                                setPropertiesOnTarget(camelContext, local, props);
                            }
                            return local;
                        } catch (Exception e) {
                            throw new IllegalStateException(
                                    "Cannot create bean: " + b.getType());
                        }
                    }));
                } else {
                    Class<?> clazz = b.getBeanClass() != null
                            ? b.getBeanClass() : camelContext.getClassResolver().resolveMandatoryClass(className);
                    // we only have the bean class so we use that to create a new bean via the injector
                    // and memorize so the bean is only created once and the local bean is the same
                    // if a route template refers to the local bean multiple times
                    routeTemplateContext.bind(b.getName(), clazz,
                            Suppliers.memorize(() -> {
                                Object local = camelContext.getInjector().newInstance(clazz);
                                if (!props.isEmpty()) {
                                    setPropertiesOnTarget(camelContext, local, props);
                                }
                                return local;
                            }));
                }
            } else if (b.getType() != null && b.getType().startsWith("#type:")) {
                Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(b.getType().substring(6));
                Set<?> found = getCamelContext().getRegistry().findByType(clazz);
                if (found == null || found.isEmpty()) {
                    throw new NoSuchBeanException(null, clazz.getName());
                } else if (found.size() > 1) {
                    throw new NoSuchBeanException(
                            "Found " + found.size() + " beans of type: " + clazz + ". Only one bean expected.");
                } else {
                    // do not set properties when using #type as it uses an existing shared bean
                    routeTemplateContext.bind(b.getName(), clazz, found.iterator().next());
                }
            } else {
                // invalid syntax for the local bean, so lets report an exception
                throw new IllegalArgumentException(
                        "Route template local bean: " + b.getName() + " has invalid type syntax: " + b.getType()
                                                   + ". To refer to a class then prefix the value with #class such as: #class:fullyQualifiedClassName");
            }
        }
    }

    @Override
    public synchronized List<RestDefinition> getRestDefinitions() {
        return restDefinitions;
    }

    @Override
    public synchronized void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes)
            throws Exception {
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
    public FaultToleranceConfigurationDefinition getFaultToleranceConfiguration(String id) {
        if (id == null) {
            id = "";
        }

        return faultToleranceConfigurations.get(id);
    }

    @Override
    public void setFaultToleranceConfiguration(FaultToleranceConfigurationDefinition configuration) {
        faultToleranceConfigurations.put("", configuration);
    }

    @Override
    public void setFaultToleranceConfigurations(List<FaultToleranceConfigurationDefinition> configurations) {
        if (configurations != null) {
            for (FaultToleranceConfigurationDefinition configuration : configurations) {
                faultToleranceConfigurations.put(configuration.getId(), configuration);
            }
        }
    }

    @Override
    public void addFaultToleranceConfiguration(String id, FaultToleranceConfigurationDefinition configuration) {
        faultToleranceConfigurations.put(id, configuration);
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
            Collection<ProcessorDefinition> col
                    = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class);
            for (ProcessorDefinition proc : col) {
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
    public Map<String, DataFormatDefinition> getDataFormats() {
        return dataFormats;
    }

    @Override
    public void setDataFormats(Map<String, DataFormatDefinition> dataFormats) {
        this.dataFormats = dataFormats;
    }

    @Override
    public List<TransformerDefinition> getTransformers() {
        return transformers;
    }

    @Override
    public void setTransformers(List<TransformerDefinition> transformers) {
        this.transformers = transformers;
    }

    @Override
    public List<ValidatorDefinition> getValidators() {
        return validators;
    }

    @Override
    public void setValidators(List<ValidatorDefinition> validators) {
        this.validators = validators;
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

    @Override
    public ModelReifierFactory getModelReifierFactory() {
        return modelReifierFactory;
    }

    @Override
    public void setModelReifierFactory(ModelReifierFactory modelReifierFactory) {
        this.modelReifierFactory = modelReifierFactory;
    }

    /**
     * Should we start newly added routes?
     */
    protected boolean shouldStartRoutes() {
        return camelContext.isStarted() && !camelContext.isStarting();
    }

    private static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookupByNameAndType(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
    }

    private static void setPropertiesOnTarget(CamelContext context, Object target, Map<String, Object> properties) {
        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");

        if (target instanceof CamelContext) {
            throw new UnsupportedOperationException("Configuring the Camel Context is not supported");
        }

        PropertyConfigurer configurer = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = ((Component) target).getComponentPropertyConfigurer();
        }

        if (configurer == null) {
            // see if there is a configurer for it
            configurer = context.adapt(ExtendedCamelContext.class)
                    .getConfigurerResolver()
                    .resolvePropertyConfigurer(target.getClass().getSimpleName(), context);
        }

        try {
            PropertyBindingSupport.build()
                    .withMandatory(true)
                    .withRemoveParameters(false)
                    .withConfigurer(configurer)
                    .withIgnoreCase(true)
                    .withFlattenProperties(true)
                    .bind(context, target, properties);
        } catch (PropertyBindingException e) {
            String key = e.getOptionKey();
            if (key == null) {
                String prefix = e.getOptionPrefix();
                if (prefix != null && !prefix.endsWith(".")) {
                    prefix = "." + prefix;
                }

                key = prefix != null
                        ? prefix + "." + e.getPropertyName()
                        : e.getPropertyName();
            }

            // enrich the error with more precise details with option prefix and key
            throw new PropertyBindingException(
                    e.getTarget(),
                    e.getPropertyName(),
                    e.getValue(),
                    null,
                    key,
                    e.getCause());
        }
    }

}
