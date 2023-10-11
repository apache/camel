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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.RouteTemplateContext;
import org.apache.camel.model.app.BeanPropertiesAdapter;
import org.apache.camel.spi.Metadata;

/**
 * Base class for nodes that define a bean factory.
 *
 * @param <T> the type of the bean factory.
 * @param <P> the type of the parent node.
 */
@Metadata(label = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class BeanFactoryDefinition<
        T extends BeanFactoryDefinition<T, P>, P> {

    @XmlTransient
    private P parent;
    // special for java-dsl to allow using lambda style
    @XmlTransient
    private Class<?> beanClass;
    @XmlTransient
    private RouteTemplateContext.BeanSupplier<Object> beanSupplier;

    @XmlAttribute(required = true)
    private String name;
    @XmlAttribute(required = true)
    private String type;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String scriptLanguage;
    @XmlElement(name = "property")
    private List<PropertyDefinition> propertyDefinitions;
    @XmlElement(name = "properties")
    @XmlJavaTypeAdapter(BeanPropertiesAdapter.class)
    private Map<String, Object> properties;
    @XmlElement(name = "script")
    @Metadata(label = "advanced")
    private String script;

    void setParent(P parent) {
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

    public String getType() {
        return type;
    }

    /**
     * What type to use for creating the bean (FQN classname). Can be prefixed with: #class or #type
     *
     * #class or #type then the bean is created via the fully qualified classname, such as #class:com.foo.MyBean
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * To set the type (fully qualified class name) to use for creating the bean.
     */
    public void setBeanType(Class<?> beanType) {
        this.beanClass = beanType;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Optional properties to set on the created local bean
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public List<PropertyDefinition> getPropertyDefinitions() {
        return propertyDefinitions;
    }

    /**
     * Optional properties to set on the created local bean
     */
    public void setPropertyDefinitions(List<PropertyDefinition> propertyDefinitions) {
        this.propertyDefinitions = propertyDefinitions;
    }

    public void addProperty(PropertyDefinition property) {
        if (propertyDefinitions == null) {
            propertyDefinitions = new LinkedList<>();
        }
        propertyDefinitions.add(property);
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

    public String getScriptLanguage() {
        return scriptLanguage;
    }

    /**
     * The script language to use when using inlined script for creating the bean, such as groovy, java, javascript etc.
     */
    public void setScriptLanguage(String scriptLanguage) {
        this.scriptLanguage = scriptLanguage;
    }

    /**
     * The script to execute that creates the bean when using scripting languages.
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     */
    public void setScript(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }

    // fluent builders
    // ----------------------------------------------------

    /**
     * What type to use for creating the bean. Can be one of: #class or #type
     *
     * #class or #type then the bean is created via the fully qualified classname, such as #class:com.foo.MyBean
     */
    @SuppressWarnings("unchecked")
    public T type(String prefix, Class<?> type) {
        if (prefix.startsWith("#type") || prefix.startsWith("#class")) {
            if (!prefix.endsWith(":")) {
                prefix = prefix + ":";
            }
            setType(prefix + type.getName());
        }
        setBeanType(type);
        return (T) this;
    }

    /**
     * What type to use for creating the bean. Can be one of: #class or #type
     *
     * #class or #type then the bean is created via the fully qualified classname, such as #class:com.foo.MyBean
     */
    @SuppressWarnings("unchecked")
    public T type(String type) {
        if (!type.startsWith("#")) {
            // use #class as default
            type = "#class:" + type;
        }
        setType(type);
        return (T) this;
    }

    /**
     * Creates the bean from the given class type
     *
     * @param type the type of the class to create as bean
     */
    @SuppressWarnings("unchecked")
    public T typeClass(Class<?> type) {
        setType("#class:" + type.getName());
        return (T) this;
    }

    /**
     * Creates the bean from the given class type
     *
     * @param type the type of the class to create as bean
     */
    @SuppressWarnings("unchecked")
    public T typeClass(String type) {
        setType("#class:" + type);
        return (T) this;
    }

    /**
     * To set the type (fully qualified class name) to use for creating the bean.
     *
     * @param type the fully qualified type of the returned bean
     */
    @SuppressWarnings("unchecked")
    public T beanType(Class<?> type) {
        setBeanType(type);
        return (T) this;
    }

    /**
     * Calls a method on a bean for creating the local bean
     *
     * @param type the bean class to call
     */
    public P bean(Class<?> type) {
        return bean(type, null);
    }

    /**
     * Calls a method on a bean for creating the local bean
     *
     * @param type   the bean class to call
     * @param method the name of the method to call
     */
    public P bean(Class<?> type, String method) {
        setScriptLanguage("bean");
        setBeanType(type);
        if (method != null) {
            setScript(type.getName() + "?method=" + method);
        } else {
            setScript(type.getName());
        }
        return parent;
    }

    /**
     * Calls a groovy script for creating the local bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public P groovy(String script) {
        setScriptLanguage("groovy");
        setScript(script);
        return parent;
    }

    /**
     * Calls joor script (Java source that is runtime compiled to Java bytecode) for creating the local bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public P joor(String script) {
        setScriptLanguage("joor");
        setScript(script);
        return parent;
    }

    /**
     * Calls java (Java source that is runtime compiled to Java bytecode) for creating the local bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public P java(String script) {
        return joor(script);
    }

    /**
     * Calls a custom language for creating the local bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param language the language
     * @param script   the script
     */
    public P language(String language, String script) {
        setScriptLanguage(language);
        setScript(script);
        return parent;
    }

    /**
     * Calls a MvEL script for creating the local bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public P mvel(String script) {
        setScriptLanguage("mvel");
        setScript(script);
        return parent;
    }

    /**
     * Calls a OGNL script for creating the local bean
     *
     * If the script use the prefix <tt>resource:</tt> such as <tt>resource:classpath:com/foo/myscript.groovy</tt>,
     * <tt>resource:file:/var/myscript.groovy</tt>, then its loaded from the external resource.
     *
     * @param script the script
     */
    public P ognl(String script) {
        setScriptLanguage("ognl");
        setScript(script);
        return parent;
    }

    /**
     * Sets a property to set on the created local bean
     *
     * @param key   the property name
     * @param value the property value
     */
    @SuppressWarnings("unchecked")
    public T property(String key, String value) {
        if (propertyDefinitions == null) {
            propertyDefinitions = new LinkedList<>();
        }
        propertyDefinitions.add(new PropertyDefinition(key, value));
        return (T) this;
    }

    /**
     * Sets properties to set on the created local bean
     */
    @SuppressWarnings("unchecked")
    public T properties(Map<String, Object> properties) {
        this.properties = properties;
        return (T) this;
    }

    public P end() {
        return parent;
    }

}
