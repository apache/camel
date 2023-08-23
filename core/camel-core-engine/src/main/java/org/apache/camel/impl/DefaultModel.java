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
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.FailedToCreateRouteFromTemplateException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.DefaultRouteTemplateContext;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.FromDefinition;
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
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.TemplatedRouteBeanDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.TemplatedRouteParameterDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.RouteTemplateHelper;
import org.apache.camel.support.ScriptHelper;
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
    // XML and YAML DSL allows to declare beans in the DSL
    private List<RegistryBeanDefinition> beans = new ArrayList<>();
    private final Map<String, ServiceCallConfigurationDefinition> serviceCallConfigurations = new ConcurrentHashMap<>();
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
        // Ensure that the route configuration should be included
        if (routesConfiguration == null || !includedRouteConfiguration(routesConfiguration)) {
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
    public synchronized RouteConfigurationDefinition getRouteConfigurationDefinition(String id) {
        for (RouteConfigurationDefinition def : routesConfigurations) {
            if (def.idOrCreate(camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class)).equals(id)) {
                return def;
            }
        }
        // you can have a global route configuration that has no ID assigned
        return routesConfigurations.stream().filter(c -> c.getId() == null).findFirst().orElse(null);
    }

    @Override
    public void removeRouteConfiguration(RouteConfigurationDefinition routeConfigurationDefinition) throws Exception {
        RouteConfigurationDefinition toBeRemoved = getRouteConfigurationDefinition(routeConfigurationDefinition.getId());
        this.routesConfigurations.remove(toBeRemoved);
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

        // special if rest-dsl is inlining routes
        if (camelContext.getRestConfiguration().isInlineRoutes()) {
            List<RouteDefinition> allRoutes = new ArrayList<>();
            allRoutes.addAll(list);
            allRoutes.addAll(this.routeDefinitions);

            List<RouteDefinition> toBeRemoved = new ArrayList<>();
            Map<String, RouteDefinition> directs = new HashMap<>();
            for (RouteDefinition r : allRoutes) {
                // does the route start with direct, which is candidate for rest-dsl
                FromDefinition from = r.getInput();
                if (from != null) {
                    String uri = from.getEndpointUri();
                    if (uri != null && uri.startsWith("direct:")) {
                        directs.put(uri, r);
                    }
                }
            }
            for (RouteDefinition r : allRoutes) {
                // loop all rest routes
                FromDefinition from = r.getInput();
                if (from != null) {
                    String uri = from.getEndpointUri();
                    if (uri != null && uri.startsWith("rest:")) {
                        ProcessorDefinition<?> def = r.getOutputs().get(0);
                        if (def instanceof ToDefinition) {
                            ToDefinition to = (ToDefinition) def;
                            String toUri = to.getEndpointUri();
                            RouteDefinition toBeInlined = directs.get(toUri);
                            if (toBeInlined != null) {
                                toBeRemoved.add(toBeInlined);
                                // inline by replacing the outputs
                                r.getOutputs().clear();
                                r.getOutputs().addAll(toBeInlined.getOutputs());
                            }
                        }
                    }
                }
            }
            // remove all the routes that was inlined
            list.removeAll(toBeRemoved);
            this.routeDefinitions.removeAll(toBeRemoved);
        }

        for (RouteDefinition r : list) {
            for (ModelLifecycleStrategy s : modelLifecycleStrategies) {
                s.onAddRouteDefinition(r);
            }
            this.routeDefinitions.add(r);
        }

        if (shouldStartRoutes()) {
            ((ModelCamelContext) getCamelContext()).startRouteDefinitions(list);
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
            if (route.idOrCreate(camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class)).equals(id)) {
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
            if (route.idOrCreate(camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class)).equals(id)) {
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
        return addRouteFromTemplate(routeId, routeTemplateId, null, rtc);
    }

    @Override
    public String addRouteFromTemplate(String routeId, String routeTemplateId, String prefixId, Map<String, Object> parameters)
            throws Exception {
        RouteTemplateContext rtc = new DefaultRouteTemplateContext(camelContext);
        if (parameters != null) {
            parameters.forEach(rtc::setParameter);
        }
        return addRouteFromTemplate(routeId, routeTemplateId, prefixId, rtc);
    }

    public String addRouteFromTemplate(String routeId, String routeTemplateId, RouteTemplateContext routeTemplateContext)
            throws Exception {
        return addRouteFromTemplate(routeId, routeTemplateId, null, routeTemplateContext);
    }

    @Override
    public String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId,
            RouteTemplateContext routeTemplateContext)
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
                        = CamelContextHelper.findSingleByType(getCamelContext(), RouteTemplateLoaderListener.class);
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
        final Map<String, Object> propDefaultValues = new HashMap<>();
        // include default values first from the template (and validate that we have inputs for all required parameters)
        if (target.getTemplateParameters() != null) {
            StringJoiner templatesBuilder = new StringJoiner(", ");

            for (RouteTemplateParameterDefinition temp : target.getTemplateParameters()) {
                if (temp.getDefaultValue() != null) {
                    addProperty(prop, temp.getName(), temp.getDefaultValue());
                    addProperty(propDefaultValues, temp.getName(), temp.getDefaultValue());
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
        if (prefixId != null) {
            def.setNodePrefixId(prefixId);
        }
        def.setTemplateParameters(prop);
        def.setTemplateDefaultParameters(propDefaultValues);
        def.setRouteTemplateContext(routeTemplateContext);

        // setup local beans
        if (target.getTemplateBeans() != null) {
            addTemplateBeans(routeTemplateContext, target);
        }

        if (target.getConfigurer() != null) {
            routeTemplateContext.setConfigurer(target.getConfigurer());
        }

        // assign ids to the routes and validate that the id's are all unique
        String duplicate = RouteDefinitionHelper.validateUniqueIds(def, routeDefinitions, prefixId);
        if (duplicate != null) {
            throw new FailedToCreateRouteFromTemplateException(
                    routeId, routeTemplateId,
                    "duplicate id detected: " + duplicate + ". Please correct ids to be unique among all your routes.");
        }

        // must use route collection to prepare the created route to
        // ensure its created correctly from the route template
        RoutesDefinition routeCollection = new RoutesDefinition();
        routeCollection.setCamelContext(camelContext);
        routeCollection.setRoutes(getRouteDefinitions());
        routeCollection.prepareRoute(def);

        // add route and return the id it was assigned
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

    private static void addTemplateBeans(RouteTemplateContext routeTemplateContext, RouteTemplateDefinition target)
            throws Exception {
        for (RouteTemplateBeanDefinition b : target.getTemplateBeans()) {
            bind(b, routeTemplateContext);
        }
    }

    /**
     * Binds the bean factory to the repository (if possible).
     *
     * @param  beanFactory          the bean factory to bind.
     * @param  routeTemplateContext the context into which the bean factory should be bound.
     * @throws Exception            if an error occurs while trying to bind the bean factory
     */
    private static void bind(BeanFactoryDefinition<?, ?> beanFactory, RouteTemplateContext routeTemplateContext)
            throws Exception {
        final Map<String, Object> props = new HashMap<>();
        if (beanFactory.getProperties() != null) {
            props.putAll(beanFactory.getProperties());
        }
        if (beanFactory.getPropertyDefinitions() != null) {
            beanFactory.getPropertyDefinitions().forEach(p -> props.put(p.getKey(), p.getValue()));
        }
        if (beanFactory.getBeanSupplier() != null) {
            if (props.isEmpty()) {
                // bean class is optional for supplier
                if (beanFactory.getBeanClass() != null) {
                    routeTemplateContext.bind(beanFactory.getName(), beanFactory.getBeanClass(), beanFactory.getBeanSupplier());
                } else {
                    routeTemplateContext.bind(beanFactory.getName(), beanFactory.getBeanSupplier());
                }
            }
        } else if (beanFactory.getScript() != null) {
            final String script = beanFactory.getScript();
            final CamelContext camelContext = routeTemplateContext.getCamelContext();
            final Language lan = camelContext.resolveLanguage(beanFactory.getType());
            final Class<?> clazz;
            if (beanFactory.getBeanType() != null) {
                clazz = camelContext.getClassResolver().resolveMandatoryClass(beanFactory.getBeanType());
            } else {
                if (beanFactory.getBeanClass() != null) {
                    clazz = beanFactory.getBeanClass();
                } else {
                    clazz = Object.class;
                }
            }
            final ScriptingLanguage slan = lan instanceof ScriptingLanguage ? (ScriptingLanguage) lan : null;
            if (slan != null) {
                // scripting language should be evaluated with route template context as binding
                // and memorize so the script is only evaluated once and the local bean is the same
                // if a route template refers to the local bean multiple times
                routeTemplateContext.bind(beanFactory.getName(), clazz, Suppliers.memorize(() -> {
                    Map<String, Object> bindings = new HashMap<>();
                    // use rtx as the short-hand name, as context would imply its CamelContext
                    bindings.put("rtc", routeTemplateContext);
                    Object local = slan.evaluate(script, bindings, clazz);
                    if (!props.isEmpty()) {
                        PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                    }
                    return local;
                }));
            } else {
                // exchange based languages needs a dummy exchange to be evaluated
                // and memorize so the script is only evaluated once and the local bean is the same
                // if a route template refers to the local bean multiple times
                routeTemplateContext.bind(beanFactory.getName(), clazz, Suppliers.memorize(() -> {
                    ExchangeFactory ef = camelContext.getCamelContextExtension().getExchangeFactory();
                    Exchange dummy = ef.create(false);
                    try {
                        String text = ScriptHelper.resolveOptionalExternalScript(camelContext, dummy, script);
                        if (text != null) {
                            Expression exp = lan.createExpression(text);
                            Object local = exp.evaluate(dummy, clazz);
                            if (!props.isEmpty()) {
                                PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
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
        } else if (beanFactory.getBeanClass() != null
                || beanFactory.getType() != null && beanFactory.getType().startsWith("#class:")) {
            // if there is a factory method then the class/bean should be created in a different way
            String className = null;
            String factoryMethod = null;
            String parameters = null;
            if (beanFactory.getType() != null) {
                className = beanFactory.getType().substring(7);
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
                final CamelContext camelContext = routeTemplateContext.getCamelContext();
                final Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(className);
                final String fqn = className;
                final String fm = factoryMethod;
                final String fp = parameters;
                routeTemplateContext.bind(beanFactory.getName(), Object.class, Suppliers.memorize(() -> {
                    // resolve placeholders in parameters
                    String params = camelContext.resolvePropertyPlaceholders(fp);
                    try {
                        Object local;
                        if (fm != null) {
                            if (fp != null) {
                                // special to support factory method parameters
                                local = PropertyBindingSupport.newInstanceFactoryParameters(camelContext, clazz, fm, params);
                            } else {
                                local = camelContext.getInjector().newInstance(clazz, fm);
                            }
                            if (local == null) {
                                throw new IllegalStateException(
                                        "Cannot create bean instance using factory method: " + fqn + "#" + fm);
                            }
                        } else {
                            // special to support constructor parameters
                            local = PropertyBindingSupport.newInstanceConstructorParameters(camelContext, clazz, params);
                        }
                        if (!props.isEmpty()) {
                            PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                        }
                        return local;
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "Cannot create bean: " + beanFactory.getType());
                    }
                }));
            } else {
                final CamelContext camelContext = routeTemplateContext.getCamelContext();
                Class<?> clazz = beanFactory.getBeanClass() != null
                        ? beanFactory.getBeanClass() : camelContext.getClassResolver().resolveMandatoryClass(className);
                // we only have the bean class so we use that to create a new bean via the injector
                // and memorize so the bean is only created once and the local bean is the same
                // if a route template refers to the local bean multiple times
                routeTemplateContext.bind(beanFactory.getName(), clazz,
                        Suppliers.memorize(() -> {
                            Object local = camelContext.getInjector().newInstance(clazz);
                            if (!props.isEmpty()) {
                                PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                            }
                            return local;
                        }));
            }
        } else if (beanFactory.getType() != null && beanFactory.getType().startsWith("#type:")) {
            final CamelContext camelContext = routeTemplateContext.getCamelContext();
            Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(beanFactory.getType().substring(6));
            Set<?> found = camelContext.getRegistry().findByType(clazz);
            if (found == null || found.isEmpty()) {
                throw new NoSuchBeanException(null, clazz.getName());
            } else if (found.size() > 1) {
                throw new NoSuchBeanException(
                        "Found " + found.size() + " beans of type: " + clazz + ". Only one bean expected.");
            } else {
                // do not set properties when using #type as it uses an existing shared bean
                routeTemplateContext.bind(beanFactory.getName(), clazz, found.iterator().next());
            }
        } else {
            // invalid syntax for the local bean, so lets report an exception
            throw new IllegalArgumentException(
                    "Route template local bean: " + beanFactory.getName() + " has invalid type syntax: " + beanFactory.getType()
                                               + ". To refer to a class then prefix the value with #class such as: #class:fullyQualifiedClassName");
        }
    }

    @Override
    public void addRouteFromTemplatedRoute(TemplatedRouteDefinition templatedRouteDefinition)
            throws Exception {
        ObjectHelper.notNull(templatedRouteDefinition, "templatedRouteDefinition");

        final RouteTemplateContext routeTemplateContext = new DefaultRouteTemplateContext(camelContext);
        // Load the parameters into the context
        final List<TemplatedRouteParameterDefinition> parameters = templatedRouteDefinition.getParameters();
        if (parameters != null) {
            for (TemplatedRouteParameterDefinition parameterDefinition : parameters) {
                routeTemplateContext.setParameter(parameterDefinition.getName(), parameterDefinition.getValue());
            }
        }
        // Bind the beans into the context
        final List<TemplatedRouteBeanDefinition> beans = templatedRouteDefinition.getBeans();
        if (beans != null) {
            for (TemplatedRouteBeanDefinition beanDefinition : beans) {
                bind(beanDefinition, routeTemplateContext);
            }
        }
        // Add the route
        addRouteFromTemplate(templatedRouteDefinition.getRouteId(), templatedRouteDefinition.getRouteTemplateRef(),
                templatedRouteDefinition.getPrefixId(), routeTemplateContext);
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

    @Override
    public void addRegistryBean(RegistryBeanDefinition bean) {
        // remove exiting bean with same name to update
        beans.removeIf(b -> bean.getName().equals(b.getName()));
        beans.add(bean);
    }

    @Override
    public List<RegistryBeanDefinition> getRegistryBeans() {
        return beans;
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

    /**
     * Indicates whether the route configuration should be included according to the precondition.
     *
     * @param  definition the definition of the route configuration to check.
     * @return            {@code true} if the route configuration should be included, {@code false} otherwise.
     */
    private boolean includedRouteConfiguration(RouteConfigurationDefinition definition) {
        return PreconditionHelper.included(definition, camelContext);
    }
}
