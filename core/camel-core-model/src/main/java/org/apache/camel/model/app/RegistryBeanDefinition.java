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
 * A Pojo representing simplified "bean" element to declare registry beans using any DSL. This is not the same as "bean
 * processor".
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
    @XmlElement(name = "constructors")
    @XmlJavaTypeAdapter(BeanConstructorsAdapter.class)
    private Map<Integer, Object> constructors;
    @XmlElement(name = "properties")
    @XmlJavaTypeAdapter(BeanPropertiesAdapter.class)
    private Map<String, Object> properties;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInitMethod() {
        return initMethod;
    }

    public void setInitMethod(String initMethod) {
        this.initMethod = initMethod;
    }

    public String getDestroyMethod() {
        return destroyMethod;
    }

    public void setDestroyMethod(String destroyMethod) {
        this.destroyMethod = destroyMethod;
    }

    public String getFactoryMethod() {
        return factoryMethod;
    }

    public void setFactoryMethod(String factoryMethod) {
        this.factoryMethod = factoryMethod;
    }

    public String getFactoryBean() {
        return factoryBean;
    }

    public void setFactoryBean(String factoryBean) {
        this.factoryBean = factoryBean;
    }

    public Map<Integer, Object> getConstructors() {
        return constructors;
    }

    public void setConstructors(Map<Integer, Object> constructors) {
        this.constructors = constructors;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
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
