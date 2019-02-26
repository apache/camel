/**
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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Calls a java bean
 */
@Metadata(label = "eip,endpoint")
@XmlRootElement(name = "bean")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanDefinition extends NoOutputDefinition<BeanDefinition> {
    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String method;
    @XmlAttribute
    private String beanType;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean cache;
    @XmlTransient
    private Class<?> beanClass;
    @XmlTransient
    private Object bean;

    public BeanDefinition() {
    }

    public BeanDefinition(String ref) {
        this.ref = ref;
    }

    public BeanDefinition(String ref, String method) {
        this.ref = ref;
        this.method = method;
    }

    @Override
    public String toString() {
        return "Bean[" + description() + "]";
    }
    
    public String description() {
        if (ref != null) {
            String methodText = "";
            if (method != null) {
                methodText = " method:" + method;
            }
            return "ref:" + ref + methodText;
        } else if (bean != null) {
            return bean.toString();
        } else if (beanClass != null) {
            return beanClass.getName();
        } else if (beanType != null) {
            return beanType;
        } else {
            return "";
        }
    }
    
    @Override
    public String getShortName() {
        return "bean";
    }

    @Override
    public String getLabel() {
        return "bean[" + description() + "]";
    }

    public String getRef() {
        return ref;
    }

    /**
     * Sets a reference to a bean to use
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getMethod() {
        return method;
    }

    /**
     * Sets the method name on the bean to use
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * Sets an instance of the bean to use
     */
    public void setBean(Object bean) {
        this.bean = bean;
    }

    public Object getBean() {
        return bean;
    }

    public String getBeanType() {
        return beanType;
    }

    /**
     * Sets the Class of the bean
     */
    public void setBeanType(String beanType) {
        this.beanType = beanType;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    /**
     * Sets the Class of the bean
     */
    public void setBeanType(Class<?> beanType) {
        this.beanClass = beanType;
    }

    public Boolean getCache() {
        return cache;
    }

    /**
     * Caches the bean lookup, to avoid lookup up bean on every usage.
     */
    public void setCache(Boolean cache) {
        this.cache = cache;
    }

    // Fluent API
    //-------------------------------------------------------------------------

}
