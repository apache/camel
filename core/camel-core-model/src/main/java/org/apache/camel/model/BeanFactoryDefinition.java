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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.RouteTemplateContext;
import org.apache.camel.model.app.BeanConstructorsAdapter;
import org.apache.camel.model.app.BeanPropertiesAdapter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * Define custom beans that can be used in your Camel routes and in general.
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "beanFactory")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanFactoryDefinition<P> implements ResourceAware {

    @XmlTransient
    private Resource resource;

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
    private String initMethod;

    @XmlAttribute
    private String destroyMethod;

    @XmlAttribute
    private String factoryMethod;

    @XmlAttribute
    private String factoryBean;

    @XmlAttribute
    private String builderClass;

    @XmlAttribute
    @Metadata(defaultValue = "build")
    private String builderMethod;

    @XmlAttribute
    @Metadata(label = "advanced")
    private String scriptLanguage;

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "advanced", defaultValue = "true")
    private String scriptPropertyPlaceholders;

    @XmlElement(name = "constructors")
    @XmlJavaTypeAdapter(BeanConstructorsAdapter.class)
    private Map<Integer, Object> constructors;

    @XmlElement(name = "properties")
    @XmlJavaTypeAdapter(BeanPropertiesAdapter.class)
    private Map<String, Object> properties;

    @XmlElement(name = "script")
    @Metadata(label = "advanced")
    private String script;

    public void setParent(P parent) {
        this.parent = parent;
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

    public RouteTemplateContext.BeanSupplier<Object> getBeanSupplier() {
        return beanSupplier;
    }

    /**
     * Bean supplier that uses lambda style to create the local bean
     */
    public void setBeanSupplier(RouteTemplateContext.BeanSupplier<Object> beanSupplier) {
        this.beanSupplier = beanSupplier;
    }

    public String getName() {
        return name;
    }

    /**
     * The name of the bean (bean id)
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    /**
     * The class name (fully qualified) of the bean
     */
    public void setType(String type) {
        this.type = type;
    }

    public String getInitMethod() {
        return initMethod;
    }

    /**
     * The name of the custom initialization method to invoke after setting bean properties. The method must have no
     * arguments, but may throw any exception.
     */
    public void setInitMethod(String initMethod) {
        this.initMethod = initMethod;
    }

    public String getDestroyMethod() {
        return destroyMethod;
    }

    /**
     * The name of the custom destroy method to invoke on bean shutdown, such as when Camel is shutting down. The method
     * must have no arguments, but may throw any exception.
     */
    public void setDestroyMethod(String destroyMethod) {
        this.destroyMethod = destroyMethod;
    }

    public String getFactoryMethod() {
        return factoryMethod;
    }

    /**
     * Name of method to invoke when creating the bean via a factory bean.
     */
    public void setFactoryMethod(String factoryMethod) {
        this.factoryMethod = factoryMethod;
    }

    public String getFactoryBean() {
        return factoryBean;
    }

    /**
     * Name of factory bean (bean id) to use for creating the bean.
     */
    public void setFactoryBean(String factoryBean) {
        this.factoryBean = factoryBean;
    }

    public String getBuilderClass() {
        return builderClass;
    }

    /**
     * Fully qualified class name of builder class to use for creating and configuring the bean. The builder will use
     * the properties values to configure the bean.
     */
    public void setBuilderClass(String builderClass) {
        this.builderClass = builderClass;
    }

    public String getBuilderMethod() {
        return builderMethod;
    }

    /**
     * Name of method when using builder class. This method is invoked after configuring to create the actual bean. This
     * method is often named build (used by default).
     */
    public void setBuilderMethod(String builderMethod) {
        this.builderMethod = builderMethod;
    }

    public Map<Integer, Object> getConstructors() {
        return constructors;
    }

    /**
     * Optional constructor arguments for creating the bean. Arguments correspond to specific index of the constructor
     * argument list, starting from zero.
     */
    public void setConstructors(Map<Integer, Object> constructors) {
        this.constructors = constructors;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Optional properties to set on the created bean.
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
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

    public String getScriptPropertyPlaceholders() {
        return scriptPropertyPlaceholders;
    }

    /**
     * Whether the script should support using Camel property placeholder syntax {{ }}.
     */
    public void setScriptPropertyPlaceholders(String scriptPropertyPlaceholders) {
        this.scriptPropertyPlaceholders = scriptPropertyPlaceholders;
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
    public BeanFactoryDefinition<P> type(String prefix, Class<?> type) {
        if (prefix.startsWith("#type") || prefix.startsWith("#class")) {
            if (!prefix.endsWith(":")) {
                prefix = prefix + ":";
            }
            setType(prefix + type.getName());
        }
        setBeanType(type);
        return this;
    }

    /**
     * What type to use for creating the bean. Can be one of: #class or #type
     *
     * #class or #type then the bean is created via the fully qualified classname, such as #class:com.foo.MyBean
     */
    public BeanFactoryDefinition<P> type(String type) {
        if (!type.startsWith("#")) {
            // use #class as default
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
    public BeanFactoryDefinition<P> typeClass(Class<?> type) {
        setType("#class:" + type.getName());
        return this;
    }

    /**
     * Creates the bean from the given class type
     *
     * @param type the type of the class to create as bean
     */
    public BeanFactoryDefinition<P> typeClass(String type) {
        setType("#class:" + type);
        return this;
    }

    /**
     * To set the type (fully qualified class name) to use for creating the bean.
     *
     * @param type the fully qualified type of the returned bean
     */
    public BeanFactoryDefinition<P> beanType(Class<?> type) {
        setBeanType(type);
        return this;
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
     * The name of the custom initialization method to invoke after setting bean properties. The method must have no
     * arguments, but may throw any exception.
     */
    public BeanFactoryDefinition<P> initMethod(String initMethod) {
        setInitMethod(initMethod);
        return this;
    }

    /**
     * The name of the custom destroy method to invoke on bean shutdown, such as when Camel is shutting down. The method
     * must have no arguments, but may throw any exception.
     */
    public BeanFactoryDefinition<P> destroyMethod(String destroyMethod) {
        setDestroyMethod(destroyMethod);
        return this;
    }

    /**
     * Name of method to invoke when creating the bean via a factory bean.
     */
    public BeanFactoryDefinition<P> factoryMethod(String factoryMethod) {
        setFactoryMethod(factoryMethod);
        return this;
    }

    /**
     * Name of factory bean (bean id) to use for creating the bean.
     */
    public BeanFactoryDefinition<P> factoryBean(String factoryBean) {
        setFactoryBean(factoryBean);
        return this;
    }

    /**
     * Fully qualified class name of builder class to use for creating and configuring the bean. The builder will use
     * the properties values to configure the bean.
     */
    public BeanFactoryDefinition<P> builderClass(String builderClass) {
        setBuilderClass(builderClass);
        return this;
    }

    /**
     * Name of method when using builder class. This method is invoked after configuring to create the actual bean. This
     * method is often named build (used by default).
     */
    public BeanFactoryDefinition<P> builderMethod(String builderMethod) {
        setBuilderMethod(builderMethod);
        return this;
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
     * Sets a constructor for creating the bean. Arguments correspond to specific index of the constructor argument
     * list, starting from zero.
     *
     * @param index the constructor index (starting from zero)
     * @param value the constructor value
     */
    public BeanFactoryDefinition<P> constructor(Integer index, String value) {
        if (constructors == null) {
            constructors = new LinkedHashMap<>();
        }
        constructors.put(index, value);
        return this;
    }

    /**
     * Optional constructor arguments for creating the bean. Arguments correspond to specific index of the constructor
     * argument list, starting from zero.
     */
    public BeanFactoryDefinition<P> constructors(Map<Integer, Object> constructors) {
        this.constructors = constructors;
        return this;
    }

    /**
     * Sets a property to set on the created local bean
     *
     * @param key   the property name
     * @param value the property value
     */
    public BeanFactoryDefinition<P> property(String key, String value) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.put(key, value);
        return this;
    }

    /**
     * Sets properties to set on the created local bean
     */
    public BeanFactoryDefinition<P> properties(Map<String, Object> properties) {
        this.properties = properties;
        return this;
    }

    /**
     * Whether the script should support using Camel property placeholder syntax {{ }}.
     */
    public BeanFactoryDefinition<P> scriptPropertyPlaceholders(boolean scriptPropertyPlaceholders) {
        this.scriptPropertyPlaceholders = scriptPropertyPlaceholders ? "true" : "false";
        return this;
    }

    /**
     * Whether the script should support using Camel property placeholder syntax {{ }}.
     */
    public BeanFactoryDefinition<P> scriptPropertyPlaceholders(String scriptPropertyPlaceholders) {
        this.scriptPropertyPlaceholders = scriptPropertyPlaceholders;
        return this;
    }

    public P end() {
        return parent;
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public void setResource(Resource resource) {
        this.resource = resource;
    }
}
