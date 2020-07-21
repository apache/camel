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
package org.apache.camel.spring;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.Metadata;
import org.springframework.beans.factory.FactoryBean;

/**
 * Configuration of routes using XML
 */
@Metadata(label = "spring,configuration,routing")
@XmlRootElement(name = "routeContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelRouteContextFactoryBean extends IdentifiedType implements FactoryBean<List<RouteDefinition>> {

    @XmlElement(name = "route", required = true)  @Metadata(description = "Routes")
    private List<RouteDefinition> routes = new ArrayList<>();

    @Override
    public List<RouteDefinition> getObject() throws Exception {
        return routes;
    }

    @Override
    public Class<?> getObjectType() {
        return routes.getClass();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }
    
}
