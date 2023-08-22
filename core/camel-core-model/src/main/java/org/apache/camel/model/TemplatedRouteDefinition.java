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
import java.util.function.Supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * Defines a templated route (a route built from a route template)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templatedRoute")
@XmlType(propOrder = { "parameters", "beans" })
@XmlAccessorType(XmlAccessType.FIELD)
public class TemplatedRouteDefinition implements CamelContextAware, ResourceAware {

    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private Resource resource;

    @XmlAttribute(required = true)
    private String routeTemplateRef;
    @XmlAttribute
    private String routeId;
    @XmlAttribute
    private String prefixId;
    @XmlElement(name = "parameter")
    @Metadata(description = "Adds an input parameter of the template to build the route")
    private List<TemplatedRouteParameterDefinition> parameters;
    @XmlElement(name = "bean")
    @Metadata(description = "Adds a local bean as input of the template to build the route")
    private List<TemplatedRouteBeanDefinition> beans;

    public String getRouteTemplateRef() {
        return routeTemplateRef;
    }

    public void setRouteTemplateRef(String routeTemplateRef) {
        this.routeTemplateRef = routeTemplateRef;
    }

    public List<TemplatedRouteParameterDefinition> getParameters() {
        return parameters;
    }

    public void setParameters(List<TemplatedRouteParameterDefinition> parameters) {
        this.parameters = parameters;
    }

    public List<TemplatedRouteBeanDefinition> getBeans() {
        return beans;
    }

    public void setBeans(List<TemplatedRouteBeanDefinition> beans) {
        this.beans = beans;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getPrefixId() {
        return prefixId;
    }

    public void setPrefixId(String prefixId) {
        this.prefixId = prefixId;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
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
     * Adds an input parameter to build the route from the route template.
     *
     * @param name  the name of the parameter
     * @param value the value of the parameter
     */
    public TemplatedRouteDefinition parameter(String name, String value) {
        addParameter(name, value);
        return this;
    }

    /**
     * Adds the input parameters to build the route from the route template.
     *
     * @param parameters the parameters
     */
    public TemplatedRouteDefinition parameters(Map<String, String> parameters) {
        parameters.forEach(this::addParameter);
        return this;
    }

    /**
     * Adds a local bean as input of the route template.
     *
     * @param name the name of the bean
     * @param type the type of the bean to associate the binding
     */
    public TemplatedRouteDefinition bean(String name, Class<?> type) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
        def.setName(name);
        def.setBeanType(type);
        beans.add(def);
        return this;
    }

    /**
     * Adds a local bean as input of the route template.
     *
     * @param name the name of the bean
     * @param bean the bean, or reference to bean (#class or #type), or a supplier for the bean
     */
    @SuppressWarnings("unchecked")
    public TemplatedRouteDefinition bean(String name, Object bean) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
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
        beans.add(def);
        return this;
    }

    /**
     * Adds a local bean as input of the route template.
     *
     * @param name the name of the bean
     * @param bean the supplier for the bean
     */
    public TemplatedRouteDefinition bean(String name, Supplier<Object> bean) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
        def.setName(name);
        def.setBeanSupplier(ctx -> ((Supplier<?>) bean).get());
        beans.add(def);
        return this;
    }

    /**
     * Adds a local bean as input of the route template.
     *
     * @param name the name of the bean
     * @param type the type of the bean to associate the binding
     * @param bean a supplier for the bean
     */
    public TemplatedRouteDefinition bean(String name, Class<?> type, RouteTemplateContext.BeanSupplier<Object> bean) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
        def.setName(name);
        def.setBeanType(type);
        def.setBeanSupplier(bean);
        beans.add(def);
        return this;
    }

    /**
     * Adds a local bean as input of the route template.
     *
     * @param name     the name of the bean
     * @param language the language to use
     * @param script   the script to use for creating the local bean
     */
    public TemplatedRouteDefinition bean(String name, String language, String script) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
        def.setName(name);
        def.setType(language);
        def.setScript(script);
        beans.add(def);
        return this;
    }

    /**
     * Adds a local bean as input of the route template.
     *
     * @param name     the name of the bean
     * @param type     the type of the bean to associate the binding
     * @param language the language to use
     * @param script   the script to use for creating the local bean
     */
    public TemplatedRouteDefinition bean(String name, Class<?> type, String language, String script) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
        def.setName(name);
        def.setBeanType(type);
        def.setType(language);
        def.setScript(script);
        beans.add(def);
        return this;
    }

    /**
     * Adds a local bean as input of the route template. (via fluent builder)
     *
     * @param  name the name of the bean
     * @return      fluent builder to choose which language and script to use for creating the bean
     */
    public TemplatedRouteBeanDefinition bean(String name) {
        if (beans == null) {
            beans = new ArrayList<>();
        }
        TemplatedRouteBeanDefinition def = new TemplatedRouteBeanDefinition();
        def.setParent(this);
        def.setName(name);
        beans.add(def);
        return def;
    }

    /**
     * Sets a prefix to use for all node ids (not route id).
     *
     * @param id the prefix id
     */
    public TemplatedRouteDefinition prefixId(String id) {
        setPrefixId(id);
        return this;
    }

    /**
     * Sets the id of the route built from the route template.
     *
     * @param id the id the generated route
     */
    public TemplatedRouteDefinition routeId(String id) {
        setRouteId(id);
        return this;
    }

    /**
     * Sets the id of the route template to use to build the route.
     *
     * @param ref the id of the route template
     */
    public TemplatedRouteDefinition routeTemplateRef(String ref) {
        setRouteTemplateRef(ref);
        return this;
    }

    private void addParameter(String name, String value) {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(new TemplatedRouteParameterDefinition(name, value));
    }
}
