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
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.Metadata;

/**
 * A series of templated routes
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templatedRoutes")
@XmlAccessorType(XmlAccessType.FIELD)
public class TemplatedRoutesDefinition extends OptionalIdentifiedDefinition<TemplatedRoutesDefinition>
        implements TemplatedRouteContainer, CamelContextAware {
    @XmlElementRef
    private List<TemplatedRouteDefinition> templatedRoutes = new ArrayList<>();
    @XmlTransient
    private CamelContext camelContext;

    public TemplatedRoutesDefinition() {
    }

    @Override
    public String toString() {
        return "TemplatedRoutes: " + templatedRoutes;
    }

    @Override
    public String getShortName() {
        return "templatedRoutes";
    }

    @Override
    public String getLabel() {
        return "TemplatedRoutes " + getId();
    }

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public List<TemplatedRouteDefinition> getTemplatedRoutes() {
        return templatedRoutes;
    }

    /**
     * The templated routes
     */
    @Override
    public void setTemplatedRoutes(List<TemplatedRouteDefinition> templatedRoutes) {
        this.templatedRoutes = templatedRoutes;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Creates a templated route
     *
     * @param routeTemplateId the id of the route template
     */
    public TemplatedRouteDefinition templatedRoute(String routeTemplateId) {
        TemplatedRouteDefinition templatedRoute = new TemplatedRouteDefinition();
        templatedRoute.routeTemplateRef(routeTemplateId);
        return templatedRoute(templatedRoute);
    }

    /**
     * Adds the {@link TemplatedRouteDefinition}
     */
    public TemplatedRouteDefinition templatedRoute(TemplatedRouteDefinition template) {
        getTemplatedRoutes().add(template);
        return template;
    }

}
