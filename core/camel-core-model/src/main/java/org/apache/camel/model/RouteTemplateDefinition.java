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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.Endpoint;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * Defines a route template (parameterized routes)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routeTemplate")
@XmlType(propOrder = { "templateParameters", "templateBeans", "route" })
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateDefinition extends OptionalIdentifiedDefinition<RouteTemplateDefinition> implements ResourceAware {

    @XmlTransient
    private Consumer<RouteTemplateContext> configurer;

    @XmlElement(name = "templateParameter")
    @Metadata(description = "Adds a template parameter the route template uses")
    private List<RouteTemplateParameterDefinition> templateParameters;
    @XmlElement(name = "templateBean")
    @Metadata(description = "Adds a local bean the route template uses")
    private List<RouteTemplateBeanDefinition> templateBeans;
    @XmlElement(name = "route", required = true)
    private RouteDefinition route = new RouteDefinition();
    @XmlTransient
    private Resource resource;

    public List<RouteTemplateParameterDefinition> getTemplateParameters() {
        return templateParameters;
    }

    public void setTemplateParameters(List<RouteTemplateParameterDefinition> templateParameters) {
        this.templateParameters = templateParameters;
    }

    public List<RouteTemplateBeanDefinition> getTemplateBeans() {
        return templateBeans;
    }

    public void setTemplateBeans(List<RouteTemplateBeanDefinition> templateBeans) {
        this.templateBeans = templateBeans;
    }

    public RouteDefinition getRoute() {
        return route;
    }

    public void setRoute(RouteDefinition route) {
        this.route = route;
    }

    public void setConfigurer(Consumer<RouteTemplateContext> configurer) {
        this.configurer = configurer;
    }

    public Consumer<RouteTemplateContext> getConfigurer() {
        return configurer;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Creates an input to the route
     *
     * @param  uri the from uri
     * @return     the builder
     */
    public RouteDefinition from(@AsEndpointUri String uri) {
        return route.from(uri);
    }

    /**
     * Creates an input to the route
     *
     * @param  endpoint the from endpoint
     * @return          the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        return route.from(endpoint);
    }

    /**
     * Creates an input to the route
     *
     * @param  endpoint the from endpoint
     * @return          the builder
     */
    public RouteDefinition from(EndpointConsumerBuilder endpoint) {
        return route.from(endpoint);
    }

    /**
     * To define the route in the template
     */
    public RouteDefinition route() {
        return route;
    }

    @Override
    public RouteTemplateDefinition description(String description) {
        setDescription(description);
        return this;
    }

    /**
     * Adds a required parameter the route template uses
     *
     * @param name the name of the parameter
     */
    public RouteTemplateDefinition templateParameter(String name) {
        addTemplateParameter(name, null);
        return this;
    }

    /**
     * Adds an optional parameter the route template uses
     *
     * @param name the name of the parameter
     */
    public RouteTemplateDefinition templateOptionalParameter(String name) {
        addTemplateOptionalParameter(name, null);
        return this;
    }

    /**
     * Adds an optional parameter the route template uses
     *
     * @param name        the name of the parameter
     * @param description the description of the parameter
     */
    public RouteTemplateDefinition templateOptionalParameter(String name, String description) {
        addTemplateOptionalParameter(name, description);
        return this;
    }

    /**
     * Adds a parameter (will use default value if not provided) the route template uses
     *
     * @param name         the name of the parameter
     * @param defaultValue default value of the parameter
     */
    public RouteTemplateDefinition templateParameter(String name, String defaultValue) {
        addTemplateParameter(name, defaultValue);
        return this;
    }

    /**
     * Adds a parameter (will use default value if not provided) the route template uses
     *
     * @param name         the name of the parameter
     * @param defaultValue default value of the parameter
     * @param description  the description of the parameter
     */
    public RouteTemplateDefinition templateParameter(String name, String defaultValue, String description) {
        addTemplateParameter(name, defaultValue, description);
        return this;
    }

    /**
     * Adds the parameters the route template uses.
     *
     * The keys in the map is the parameter names, and the values are optional default value. If a parameter has no
     * default value then the parameter is required.
     *
     * @param parameters the parameters (only name and default values)
     */
    public RouteTemplateDefinition templateParameters(Map<String, String> parameters) {
        parameters.forEach(this::addTemplateParameter);
        return this;
    }

    /**
     * Adds a local bean the route template uses
     *
     * @param name the name of the bean
     * @param type the type of the bean to associate the binding
     */
    public RouteTemplateDefinition templateBean(String name, Class<?> type) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setName(name);
        def.setBeanType(type);
        templateBeans.add(def);
        return this;
    }

    /**
     * Adds a local bean the route template uses
     *
     * @param name the name of the bean
     * @param bean the bean, or reference to bean (#class or #type), or a supplier for the bean
     */
    @SuppressWarnings("unchecked")
    public RouteTemplateDefinition templateBean(String name, Object bean) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setName(name);
        if (bean instanceof RouteTemplateContext.BeanSupplier) {
            def.setBeanSupplier((RouteTemplateContext.BeanSupplier<Object>) bean);
        } else if (bean instanceof Supplier) {
            def.setBeanSupplier(ctx -> ((Supplier<?>) bean).get());
        } else if (bean instanceof String) {
            // its a string type
            def.setType((String) bean);
        } else {
            def.setBeanSupplier(ctx -> bean);
        }
        templateBeans.add(def);
        return this;
    }

    /**
     * Adds a local bean the route template uses
     *
     * @param name the name of the bean
     * @param bean the supplier for the bean
     */
    public RouteTemplateDefinition templateBean(String name, Supplier<Object> bean) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setName(name);
        def.setBeanSupplier(ctx -> ((Supplier<?>) bean).get());
        templateBeans.add(def);
        return this;
    }

    /**
     * Adds a local bean the route template uses
     *
     * @param name the name of the bean
     * @param type the type of the bean to associate the binding
     * @param bean a supplier for the bean
     */
    public RouteTemplateDefinition templateBean(String name, Class<?> type, RouteTemplateContext.BeanSupplier<Object> bean) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setName(name);
        def.setBeanType(type);
        def.setBeanSupplier(bean);
        templateBeans.add(def);
        return this;
    }

    /**
     * Adds a local bean the route template uses
     *
     * @param name     the name of the bean
     * @param language the language to use
     * @param script   the script to use for creating the local bean
     */
    public RouteTemplateDefinition templateBean(String name, String language, String script) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setName(name);
        def.setScriptLanguage(language);
        def.setScript(script);
        templateBeans.add(def);
        return this;
    }

    /**
     * Adds a local bean the route template uses
     *
     * @param name     the name of the bean
     * @param type     the type of the bean to associate the binding
     * @param language the language to use
     * @param script   the script to use for creating the local bean
     */
    public RouteTemplateDefinition templateBean(String name, Class<?> type, String language, String script) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setName(name);
        def.setBeanType(type);
        def.setScriptLanguage(language);
        def.setScript(script);
        templateBeans.add(def);
        return this;
    }

    /**
     * Adds a local bean the route template uses (via fluent builder)
     *
     * @param  name the name of the bean
     * @return      fluent builder to choose which language and script to use for creating the bean
     */
    public RouteTemplateBeanDefinition templateBean(String name) {
        if (templateBeans == null) {
            templateBeans = new ArrayList<>();
        }
        RouteTemplateBeanDefinition def = new RouteTemplateBeanDefinition();
        def.setParent(this);
        def.setName(name);
        templateBeans.add(def);
        return def;
    }

    /**
     * Sets a configurer which allows to do configuration while the route template is being used to create a route. This
     * gives control over the creating process, such as binding local beans and doing other kind of customization.
     *
     * @param configurer the configurer with callback to invoke with the given route template context
     */
    public RouteTemplateDefinition configure(Consumer<RouteTemplateContext> configurer) {
        this.configurer = configurer;
        return this;
    }

    @Override
    public String getShortName() {
        return "routeTemplate";
    }

    @Override
    public String getLabel() {
        return "RouteTemplate[" + route.getInput().getLabel() + "]";
    }

    private void addTemplateParameter(String name, String defaultValue) {
        addTemplateParameter(name, defaultValue, null);
    }

    private void addTemplateParameter(String name, String defaultValue, String description) {
        if (this.templateParameters == null) {
            this.templateParameters = new ArrayList<>();
        }
        this.templateParameters.add(new RouteTemplateParameterDefinition(name, defaultValue, description));
    }

    private void addTemplateOptionalParameter(String name, String description) {
        if (this.templateParameters == null) {
            this.templateParameters = new ArrayList<>();
        }
        RouteTemplateParameterDefinition def = new RouteTemplateParameterDefinition(name, null, description);
        def.setRequired(false);
        this.templateParameters.add(def);
    }

    /**
     * Creates a copy of this template as a {@link RouteDefinition} which can be used to add as a new route.
     */
    public RouteDefinition asRouteDefinition() {
        RouteDefinition copy = new RouteDefinition();

        // must set these first in this order
        copy.setErrorHandlerRef(route.getErrorHandlerRef());
        if (route.isErrorHandlerFactorySet()) {
            // only set factory if not already set
            copy.setErrorHandlerFactory(route.getErrorHandlerFactory());
        }

        // and then copy over the rest
        // (do not copy id as it is used for route template id)
        copy.setAutoStartup(route.getAutoStartup());
        copy.setDelayer(route.getDelayer());
        copy.setGroup(route.getGroup());
        copy.setInheritErrorHandler(route.isInheritErrorHandler());
        copy.setInput(route.getInput());
        copy.setInputType(route.getInputType());
        copy.setLogMask(route.getLogMask());
        copy.setMessageHistory(route.getMessageHistory());
        copy.setOutputType(route.getOutputType());
        copy.setOutputs(route.getOutputs());
        copy.setRoutePolicies(route.getRoutePolicies());
        copy.setRoutePolicyRef(route.getRoutePolicyRef());
        copy.setRouteProperties(route.getRouteProperties());
        copy.setShutdownRoute(route.getShutdownRoute());
        copy.setShutdownRunningTask(route.getShutdownRunningTask());
        copy.setStartupOrder(route.getStartupOrder());
        copy.setStreamCache(route.getStreamCache());
        copy.setTemplate(true);
        copy.setTrace(route.getTrace());
        if (route.getDescription() != null) {
            copy.setDescription(route.getDescription());
        } else {
            copy.setDescription(getDescription());
        }
        copy.setPrecondition(route.getPrecondition());
        copy.setRouteConfigurationId(route.getRouteConfigurationId());
        return copy;
    }

    @FunctionalInterface
    public interface Converter {

        /**
         * Default implementation that uses {@link #asRouteDefinition()} to convert a {@link RouteTemplateDefinition} to
         * a {@link RouteDefinition}
         */
        Converter DEFAULT_CONVERTER = new Converter() {
            @Override
            public RouteDefinition apply(RouteTemplateDefinition in, Map<String, Object> parameters) throws Exception {
                return in.asRouteDefinition();
            }
        };

        /**
         * Convert a {@link RouteTemplateDefinition} to a {@link RouteDefinition}.
         *
         * @param  in         the {@link RouteTemplateDefinition} to convert
         * @param  parameters parameters that are given to the {@link Model#addRouteFromTemplate(String, String, Map)}.
         *                    Implementors are free to add or remove additional parameter.
         * @return            the generated {@link RouteDefinition}
         */
        RouteDefinition apply(RouteTemplateDefinition in, Map<String, Object> parameters) throws Exception;
    }
}
