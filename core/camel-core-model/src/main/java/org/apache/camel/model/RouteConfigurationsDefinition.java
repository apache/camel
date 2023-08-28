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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * A series of route configurations
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routeConfigurations")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteConfigurationsDefinition extends OptionalIdentifiedDefinition<RouteConfigurationsDefinition>
        implements RouteConfigurationContainer, ResourceAware {

    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private Resource resource;

    @XmlElementRef
    private List<RouteConfigurationDefinition> routeConfigurations = new ArrayList<>();

    public RouteConfigurationsDefinition() {
    }

    @Override
    public String getShortName() {
        return "routeConfigurations";
    }

    @Override
    public String getLabel() {
        return "RouteConfigurations " + getId();
    }

    @Override
    public String toString() {
        return "RouteConfigurations";
    }

    public List<RouteConfigurationDefinition> getRouteConfigurations() {
        return routeConfigurations;
    }

    public void setRouteConfigurations(List<RouteConfigurationDefinition> routeConfigurations) {
        this.routeConfigurations = routeConfigurations;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Creates a new route configuration
     *
     * @return the builder
     */
    public RouteConfigurationDefinition routeConfiguration() {
        RouteConfigurationDefinition config = createRouteConfiguration(null);
        getRouteConfigurations().add(config);
        return config;
    }

    /**
     * Creates a new route configuration
     *
     * @return the builder
     */
    public RouteConfigurationDefinition routeConfiguration(String id) {
        RouteConfigurationDefinition config = createRouteConfiguration(id);
        getRouteConfigurations().add(config);
        return config;
    }

    /**
     * Adds the route configuration
     *
     * @return the builder
     */
    public RouteConfigurationDefinition routeConfiguration(RouteConfigurationDefinition config) {
        getRouteConfigurations().add(config);
        return config;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected RouteConfigurationDefinition createRouteConfiguration(String id) {
        RouteConfigurationDefinition config = new RouteConfigurationDefinition();
        if (id != null) {
            config.setId(id);
        }
        if (resource != null) {
            config.setResource(resource);
        }
        CamelContextAware.trySetCamelContext(config, camelContext);
        return config;
    }

}
