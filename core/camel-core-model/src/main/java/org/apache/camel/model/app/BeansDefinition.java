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
package org.apache.camel.model.app;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Metadata;

/**
 * A groupping POJO (and related XML root element) that's historically associated with "entire application"
 * (or its distinguished fragment). "beans" root element to define "the application" comes from Spring
 * Framework and it can be treated as de-facto standard.
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "beans")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeansDefinition {

    /**
     * Component scanning definition(s). But unlike package/packageScan/contextScan,
     * we're not scanning only for org.apache.camel.builder.RouteBuilder.
     */
    @XmlElement(name = "component-scan")
    private final List<ComponentScanDefinition> componentScanning = new ArrayList<>();

    // this is a place for <bean> element definition, but there's already org.apache.camel.model.BeanDefinition
    // model. However it is for "bean processor", not "bean definition".
    // also, it'd be nice to support all that Spring's <bean> can support (constructor args, maps/lists/constants)
    // for now let's stick to package scanning for JSR330 annotations and SupplierRegistry

    // the order comes from <camelContext> (org.apache.camel.spring.xml.CamelContextFactoryBean)
    // to make things less confusing, as it's not easy to simply tell JAXB to use <xsd:choice maxOccurs="unbounded">
    // over a set of unrelated elements

    // initially we'll be supporting only these elements which are parsed by
    // org.apache.camel.dsl.xml.io.XmlRoutesBuilderLoader in camel-xml-io-dsl

    @XmlElement(name = "rest")
    private final List<RestDefinition> rests = new ArrayList<>();
    @XmlElement(name = "routeConfiguration")
    private final List<RouteConfigurationDefinition> routeConfigurations = new ArrayList<>();
    @XmlElement(name = "routeTemplate")
    private final List<RouteTemplateDefinition> routeTemplates = new ArrayList<>();
    @XmlElement(name = "templatedRoute")
    private final List<TemplatedRouteDefinition> templatedRoutes = new ArrayList<>();
    @XmlElement(name = "route")
    private final List<RouteDefinition> routes = new ArrayList<>();

    public List<ComponentScanDefinition> getComponentScanning() {
        return componentScanning;
    }

    public List<RestDefinition> getRests() {
        return rests;
    }

    public List<RouteConfigurationDefinition> getRouteConfigurations() {
        return routeConfigurations;
    }

    public List<RouteTemplateDefinition> getRouteTemplates() {
        return routeTemplates;
    }

    public List<TemplatedRouteDefinition> getTemplatedRoutes() {
        return templatedRoutes;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

}
