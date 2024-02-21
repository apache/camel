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
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.w3c.dom.Element;

import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.ExternalSchemaElement;

/**
 * Container for beans, routes, and more.
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "beans")
@XmlType(propOrder = {
        "componentScanning",
        "beans",
        "springBeans",
        "blueprintBeans",
        "restConfigurations",
        "rests",
        "routeConfigurations",
        "routeTemplates",
        "templatedRoutes",
        "routes"
})
@XmlAccessorType(XmlAccessType.FIELD)
public class BeansDefinition {

    // This class is not meant to be used with Camel Java DSL, but it's needed to generate XML Schema and MX parser methods

    @XmlElement(name = "component-scan")
    private List<ComponentScanDefinition> componentScanning = new ArrayList<>();

    // this is a place for <bean> element definition, without conflicting with <bean> elements referring
    // to "bean processors"

    @XmlElement(name = "bean")
    private List<RegistryBeanDefinition> beans = new ArrayList<>();

    // this is the only way I found to generate usable Schema without imports, while allowing elements
    // from different namespaces
    @ExternalSchemaElement(names = { "beans", "bean", "alias" },
                           namespace = "http://www.springframework.org/schema/beans",
                           documentElement = "beans")
    @XmlAnyElement
    private List<Element> springBeans = new ArrayList<>();
    @ExternalSchemaElement(names = { "bean" },
                           namespace = "http://www.osgi.org/xmlns/blueprint/v1.0.0",
                           documentElement = "blueprint")
    @XmlAnyElement
    private List<Element> blueprintBeans = new ArrayList<>();

    // the order comes from <camelContext> (org.apache.camel.spring.xml.CamelContextFactoryBean)
    // to make things less confusing, as it's not easy to simply tell JAXB to use <xsd:choice maxOccurs="unbounded">
    // over a set of unrelated elements

    // initially we'll be supporting only these elements which are parsed by
    // org.apache.camel.dsl.xml.io.XmlRoutesBuilderLoader in camel-xml-io-dsl

    @XmlElement(name = "restConfiguration")
    private List<RestConfigurationDefinition> restConfigurations = new ArrayList<>();
    @XmlElement(name = "rest")
    private List<RestDefinition> rests = new ArrayList<>();
    @XmlElement(name = "routeConfiguration")
    private List<RouteConfigurationDefinition> routeConfigurations = new ArrayList<>();
    @XmlElement(name = "routeTemplate")
    private List<RouteTemplateDefinition> routeTemplates = new ArrayList<>();
    @XmlElement(name = "templatedRoute")
    private List<TemplatedRouteDefinition> templatedRoutes = new ArrayList<>();
    @XmlElement(name = "route")
    private List<RouteDefinition> routes = new ArrayList<>();

    public List<ComponentScanDefinition> getComponentScanning() {
        return componentScanning;
    }

    /**
     * Component scanning that can auto-discover Camel route builders from the classpath.
     */
    public void setComponentScanning(List<ComponentScanDefinition> componentScanning) {
        this.componentScanning = componentScanning;
    }

    public List<RegistryBeanDefinition> getBeans() {
        return beans;
    }

    /**
     * List of bean
     */
    public void setBeans(List<RegistryBeanDefinition> beans) {
        this.beans = beans;
    }

    public List<Element> getSpringBeans() {
        return springBeans;
    }

    /**
     * Spring XML beans
     */
    public void setSpringBeans(List<Element> springBeans) {
        this.springBeans = springBeans;
    }

    public List<Element> getBlueprintBeans() {
        return blueprintBeans;
    }

    /**
     * Blueprint XML beans
     */
    public void setBlueprintBeans(List<Element> blueprintBeans) {
        this.blueprintBeans = blueprintBeans;
    }

    public List<RestConfigurationDefinition> getRestConfigurations() {
        return restConfigurations;
    }

    /**
     * Camel Rest DSL Configuration
     */
    public void setRestConfigurations(List<RestConfigurationDefinition> restConfigs) {
        this.restConfigurations = restConfigs;
    }

    public List<RestDefinition> getRests() {
        return rests;
    }

    /**
     * Camel Rest DSL
     */
    public void setRests(List<RestDefinition> rests) {
        this.rests = rests;
    }

    public List<RouteConfigurationDefinition> getRouteConfigurations() {
        return routeConfigurations;
    }

    /**
     * Camel route configurations
     */
    public void setRouteConfigurations(List<RouteConfigurationDefinition> routeConfigurations) {
        this.routeConfigurations = routeConfigurations;
    }

    public List<RouteTemplateDefinition> getRouteTemplates() {
        return routeTemplates;
    }

    /**
     * Camel route templates
     */
    public void setRouteTemplates(List<RouteTemplateDefinition> routeTemplates) {
        this.routeTemplates = routeTemplates;
    }

    public List<TemplatedRouteDefinition> getTemplatedRoutes() {
        return templatedRoutes;
    }

    /**
     * Camel routes to be created from template
     */
    public void setTemplatedRoutes(List<TemplatedRouteDefinition> templatedRoutes) {
        this.templatedRoutes = templatedRoutes;
    }

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    /**
     * Camel routes
     */
    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

}
