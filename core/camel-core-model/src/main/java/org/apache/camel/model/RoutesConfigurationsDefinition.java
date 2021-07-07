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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;

/**
 * A series of global configuration for Camel routes
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routesConfigurations")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutesConfigurationsDefinition extends OptionalIdentifiedDefinition<RoutesDefinition> {

    @XmlElementRef
    private List<RoutesConfigurationDefinition> routesConfigurations = new ArrayList<>();
    @XmlTransient
    private CamelContext camelContext;

    public RoutesConfigurationsDefinition() {
    }

    @Override
    public String toString() {
        return "RoutesConfigurations: " + getId();
    }

    @Override
    public String getShortName() {
        return "routesConfigurations";
    }

    @Override
    public String getLabel() {
        return "RoutesConfigurations " + getId();
    }

    public List<RoutesConfigurationDefinition> getRoutesConfigurations() {
        return routesConfigurations;
    }

    public void setRoutesConfigurations(List<RoutesConfigurationDefinition> routesConfigurations) {
        this.routesConfigurations = routesConfigurations;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
