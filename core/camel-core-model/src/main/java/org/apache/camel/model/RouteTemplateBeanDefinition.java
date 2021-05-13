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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.RouteTemplateContext;
import org.apache.camel.spi.Metadata;

/**
 * A route template bean (local bean)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templateBean")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateBeanDefinition {
    @XmlTransient
    private RouteTemplateDefinition parent;
    @XmlAttribute(required = true)
    private String name;
    @XmlAttribute(required = true)
    private String type;
    @XmlElement(name = "property")
    private List<PropertyDefinition> properties;
    @XmlElement
    private RouteTemplateScriptDefinition script;
    // special for java-dsl to allow using lambda style
    @XmlTransient
    private Class<?> beanClass;
    @XmlTransient
    private RouteTemplateContext.BeanSupplier<Object> beanSupplier;

    public RouteTemplateBeanDefinition() {
    }

    public RouteTemplateBeanDefinition(String name) {
        this.name = name;
    }

    public RouteTemplateBeanDefinition(RouteTemplateDefinition parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    /**
     * Bean name
     */
    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public void setBeanType(Class<?> beanType) {
        this.beanClass = beanType;
    }

    public String getType() {
        return type;
    }

    /**
     * What type to use for creating the bean. Can be one of: #class,#type,bean,groovy,joor,language,mvel,ognl.
     *
     * #class or #type then the bean is created via the fully qualified classname, such as #class:com.foo.MyBean
     *
     * The others are scripting languages that gives more power to create the bean with an inlined code in the script
     * section, such as using groovy.
     */
    public void setType(String type) {
        this.type = type;
    }

    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * Optional properties to set on the created local bean
     */
    public void setProperties(List<PropertyDefinition> properties) {
        this.properties = properties;
    }

    public RouteTemplateContext.BeanSupplier<Object> getBeanSupplier() {
        return beanSupplier;
    }

    /**
     * Bean supplier that uses lambda style to create the local bean
     */
    public void setBeanSupplier(RouteTemplateContext.BeanSupplier<Object> beanSupplier) {
        this.beanSupplier = beanSupplier;
    }

    public RouteTemplateScriptDefinition getScript() {
        return script;
    }

    /**
     * The script to execute that creates the bean when using scripting languages.
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     */
    public void setScript(RouteTemplateScriptDefinition script) {
        this.script = script;
    }

    /**
     * The script to execute that creates the bean when using scripting languages.
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     */
    public void setScript(String script) {
        this.script = new RouteTemplateScriptDefinition();
        this.script.setScript(script);
    }

    // fluent builders
    // ----------------------------------------------------

    /**
     * Creates the bean from the given class type
     *
     * @param type the type of the class to create as bean
     */
    public RouteTemplateDefinition beanClass(Class<?> type) {
        setType("#class:" + type.getName());
        return parent;
    }

    /**
     * Lookup in the registry for bean instances of the given type, and if there is a single instance of the given type,
     * then that bean will be used as the local bean (danger this bean is shared)
     *
     * @param type the type of the class to lookup in the registry
     */
    public RouteTemplateDefinition beanType(Class<?> type) {
        setType("#type:" + type.getName());
        return parent;
    }

    /**
     * Calls a method on a bean for creating the local template bean
     *
     * @param type the bean class to call
     */
    public RouteTemplateDefinition bean(Class<?> type) {
        return bean(type, null);
    }

    /**
     * Calls a method on a bean for creating the local template bean
     *
     * @param type   the bean class to call
     * @param method the name of the method to call
     */
    public RouteTemplateDefinition bean(Class<?> type, String method) {
        setType("bean");
        if (method != null) {
            setScript(type.getName() + "?method=" + method);
        } else {
            setScript(type.getName());
        }
        return parent;
    }

    /**
     * Calls a groovy script for creating the local template bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public RouteTemplateDefinition groovy(String script) {
        setType("groovy");
        setScript(script);
        return parent;
    }

    /**
     * Calls joor script (Java source that is runtime compiled to Java bytecode) for creating the local template bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public RouteTemplateDefinition joor(String script) {
        setType("joor");
        setScript(script);
        return parent;
    }

    /**
     * Calls a custom language for creating the local template bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param language the language
     * @param script   the script
     */
    public RouteTemplateDefinition language(String language, String script) {
        setType(language);
        setScript(script);
        return parent;
    }

    /**
     * What type to use for creating the bean. Can be one of: #class,#type,bean,groovy,joor,language,mvel,ognl.
     *
     * #class or #type then the bean is created via the fully qualified classname, such as #class:com.foo.MyBean
     *
     * The others are scripting languages that gives more power to create the bean with an inlined code in the script
     * section, such as using groovy.
     */
    public RouteTemplateBeanDefinition type(String type) {
        if (!type.startsWith("#type:") && !type.startsWith("#class:")) {
            type = "#class:" + type;
        }
        setType(type);
        return this;
    }

    /**
     * Creates the bean from the given class type
     *
     * @param type the type of the class to create as bean
     */
    public RouteTemplateBeanDefinition type(Class<?> type) {
        beanClass(type);
        return this;
    }

    /**
     * Calls a MvEL script for creating the local template bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public RouteTemplateDefinition mvel(String script) {
        setType("mvel");
        setScript(script);
        return parent;
    }

    /**
     * Calls a OGNL script for creating the local template bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public RouteTemplateDefinition ognl(String script) {
        setType("ognl");
        setScript(script);
        return parent;
    }

    /**
     * Sets a property to set on the created local bean
     *
     * @param key   the property name
     * @param value the property value
     */
    public RouteTemplateBeanDefinition property(String key, String value) {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        properties.add(new PropertyDefinition(key, value));
        return this;
    }

    /**
     * Sets properties to set on the created local bean
     */
    public RouteTemplateBeanDefinition properties(Map<String, String> properties) {
        if (this.properties == null) {
            this.properties = new ArrayList<>();
        }
        properties.forEach((k, v) -> this.properties.add(new PropertyDefinition(k, v)));
        return this;
    }

    public RouteTemplateDefinition end() {
        return parent;
    }

}
