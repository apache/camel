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
package org.apache.camel.spring.xml;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.spi.Metadata;
import org.springframework.beans.factory.FactoryBean;

/**
 * Configuration of route configurations using XML
 */
@Metadata(label = "spring,configuration,routing")
@XmlRootElement(name = "routeConfigurationContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelRouteConfigurationContextFactoryBean extends IdentifiedType
        implements FactoryBean<List<RouteConfigurationDefinition>> {

    @XmlElement(name = "routeConfiguration", required = true)
    @Metadata(description = "Route Configurations")
    private List<RouteConfigurationDefinition> routeConfigurations = new ArrayList<>();

    @Override
    public List<RouteConfigurationDefinition> getObject() throws Exception {
        return routeConfigurations;
    }

    @Override
    public Class<?> getObjectType() {
        return routeConfigurations.getClass();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public List<RouteConfigurationDefinition> getRouteConfigurations() {
        return routeConfigurations;
    }

    public void setRouteConfigurations(List<RouteConfigurationDefinition> routeConfigurations) {
        this.routeConfigurations = routeConfigurations;
    }
}
