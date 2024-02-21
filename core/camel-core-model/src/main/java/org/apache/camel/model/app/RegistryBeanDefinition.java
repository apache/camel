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

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * Define custom beans that can be used in your Camel routes and in general.
 */
@Metadata(label = "configuration")
@XmlType
@XmlAccessorType(XmlAccessType.FIELD)
public class RegistryBeanDefinition implements ResourceAware {

    @XmlTransient
    private Resource resource;

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
    @XmlElement(name = "constructors")
    @XmlJavaTypeAdapter(BeanConstructorsAdapter.class)
    private Map<Integer, Object> constructors;
    @XmlElement(name = "properties")
    @XmlJavaTypeAdapter(BeanPropertiesAdapter.class)
    private Map<String, Object> properties;
    @XmlElement(name = "script")
    @Metadata(label = "advanced")
    private String script;

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

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public void setResource(Resource resource) {
        this.resource = resource;
    }
}
