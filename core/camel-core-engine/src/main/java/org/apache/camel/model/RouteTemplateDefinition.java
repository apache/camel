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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;

/**
 * Defines a route template (parameterized routes)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routeTemplate")
@XmlType(propOrder = { "templateParameters", "route" })
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateDefinition extends OptionalIdentifiedDefinition {

    @XmlElement(name = "templateParameter")
    private List<RouteTemplateParameterDefinition> templateParameters;
    @XmlElement(name = "route", required = true)
    private RouteDefinition route = new RouteDefinition();

    public List<RouteTemplateParameterDefinition> getTemplateParameters() {
        return templateParameters;
    }

    public void setTemplateParameters(List<RouteTemplateParameterDefinition> templateParameters) {
        this.templateParameters = templateParameters;
    }

    public RouteDefinition getRoute() {
        return route;
    }

    public void setRoute(RouteDefinition route) {
        this.route = route;
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
    public RouteTemplateDefinition description(String text) {
        DescriptionDefinition def = new DescriptionDefinition();
        def.setText(text);
        setDescription(def);
        return this;
    }

    /**
     * Adds a parameter the route template uses.
     *
     * @param name the name of the parameter
     */
    public RouteTemplateDefinition templateParameter(String name) {
        addTemplateParameter(name, null);
        return this;
    }

    /**
     * Adds a parameter the route template uses.
     *
     * @param name         the name of the parameter
     * @param defaultValue default value of the parameter
     */
    public RouteTemplateDefinition templateParameter(String name, String defaultValue) {
        addTemplateParameter(name, defaultValue);
        return this;
    }

    /**
     * Adds a parameter the route template uses.
     *
     * @param name         the name of the parameter
     * @param defaultValue default value of the parameter
     */
    public RouteTemplateDefinition templateParameter(String name, String defaultValue, String description) {
        addTemplateParameter(name, defaultValue, description);
        return this;
    }

    /**
     * Adds the parameters the route template uses.
     *
     * @param parameters the parameters (only name and default values)
     */
    public RouteTemplateDefinition templateParameters(Map<String, String> parameters) {
        parameters.forEach(this::addTemplateParameter);
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

    /**
     * Adds a parameter the route template uses.
     */
    private void addTemplateParameter(String name, String defaultValue) {
        addTemplateParameter(name, defaultValue, null);
    }

    /**
     * Adds a parameter the route template uses.
     */
    private void addTemplateParameter(String name, String defaultValue, String description) {
        if (this.templateParameters == null) {
            this.templateParameters = new ArrayList<>();
        }
        this.templateParameters.add(new RouteTemplateParameterDefinition(name, defaultValue, description));
    }

    /**
     * Creates a copy of this template as a {@link RouteDefinition} which can be used to add as a new route.
     */
    public RouteDefinition asRouteDefinition() {
        RouteDefinition copy = new RouteDefinition();

        // do not copy id as it is used for route template id
        copy.setInheritErrorHandler(route.isInheritErrorHandler());
        copy.setGroup(route.getGroup());
        copy.setStreamCache(route.getStreamCache());
        copy.setTrace(route.getTrace());
        copy.setMessageHistory(route.getMessageHistory());
        copy.setLogMask(route.getLogMask());
        copy.setDelayer(route.getDelayer());
        copy.setStartupOrder(route.getStartupOrder());
        copy.setRoutePolicies(route.getRoutePolicies());
        copy.setRoutePolicyRef(route.getRoutePolicyRef());
        copy.setShutdownRoute(route.getShutdownRoute());
        copy.setShutdownRunningTask(route.getShutdownRunningTask());
        copy.setErrorHandlerRef(route.getErrorHandlerRef());
        copy.setErrorHandlerFactory(route.getErrorHandlerFactory());
        copy.setInputType(route.getInputType());
        copy.setOutputType(route.getOutputType());
        copy.setRouteProperties(route.getRouteProperties());
        copy.setTemplate(true);
        copy.setInput(route.getInput());
        copy.setOutputs(route.getOutputs());
        if (route.getDescription() != null) {
            copy.setDescription(route.getDescription());
        } else {
            copy.setDescription(getDescription());
        }

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
         * @deprecated use {@link #apply(RouteTemplateDefinition, Map)}
         */
        @Deprecated
        default RouteDefinition apply(RouteTemplateDefinition in) throws Exception {
            return apply(in, Collections.emptyMap());
        }

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
