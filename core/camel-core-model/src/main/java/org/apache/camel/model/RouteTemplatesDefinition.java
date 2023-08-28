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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * A series of route templates
 */
@Metadata(label = "routeTemplates")
@XmlRootElement(name = "routeTemplates")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplatesDefinition extends OptionalIdentifiedDefinition<RouteTemplatesDefinition>
        implements RouteTemplateContainer, CamelContextAware, ResourceAware {

    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ErrorHandlerFactory errorHandlerFactory;
    @XmlTransient
    private Resource resource;

    @XmlElementRef
    private List<RouteTemplateDefinition> routeTemplates = new ArrayList<>();

    public RouteTemplatesDefinition() {
    }

    @Override
    public String toString() {
        return "RouteTemplates: " + routeTemplates;
    }

    @Override
    public String getShortName() {
        return "routeTemplates";
    }

    @Override
    public String getLabel() {
        return "RouteTemplate " + getId();
    }

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public List<RouteTemplateDefinition> getRouteTemplates() {
        return routeTemplates;
    }

    /**
     * The route templates
     */
    public void setRouteTemplates(List<RouteTemplateDefinition> routeTemplates) {
        this.routeTemplates = routeTemplates;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ErrorHandlerFactory getErrorHandlerFactory() {
        return errorHandlerFactory;
    }

    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerFactory = errorHandlerFactory;
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
     * Creates a route template
     *
     * @param id the id of the route template
     */
    public RouteTemplateDefinition routeTemplate(String id) {
        RouteTemplateDefinition routeTemplate = createRouteTemplate();
        routeTemplate.id(id);
        return routeTemplate(routeTemplate);
    }

    /**
     * Adds the {@link RouteTemplatesDefinition}
     */
    public RouteTemplateDefinition routeTemplate(RouteTemplateDefinition template) {
        getRouteTemplates().add(template);
        return template;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected RouteTemplateDefinition createRouteTemplate() {
        RouteTemplateDefinition template = new RouteTemplateDefinition();
        ErrorHandlerFactory handler = getErrorHandlerFactory();
        if (handler != null) {
            template.getRoute().setErrorHandlerFactoryIfNull(handler);
        }
        if (resource != null) {
            template.setResource(resource);
        }
        return template;
    }

}
