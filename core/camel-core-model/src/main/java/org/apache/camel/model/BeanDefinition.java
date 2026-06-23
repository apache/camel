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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.BeanScope;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslArg;
import org.apache.camel.util.ObjectHelper;

/**
 * Calls a Java bean
 */
@Metadata(label = "eip,endpoint")
@XmlRootElement(name = "bean")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanDefinition extends NoOutputDefinition<BeanDefinition> {

    @XmlTransient
    private Class<?> beanClass;
    @XmlTransient
    private Object bean;

    @XmlAttribute
    @DslArg(position = 0)
    @Metadata(description = "A reference to an existing bean to use, which is looked up from the registry.")
    private String ref;
    @XmlAttribute
    @DslArg(position = 1)
    @Metadata(description = "The method name on the bean to invoke.")
    private String method;
    @XmlAttribute
    @Metadata(description = "The class name (fully qualified) of the bean to use.")
    private String beanType;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "Singleton", enums = "Singleton,Request,Prototype",
              description = "Scope of bean. When using singleton scope (default) the bean is created or looked up only once and reused for the lifetime of the endpoint.")
    private String scope;

    public BeanDefinition() {
    }

    protected BeanDefinition(BeanDefinition source) {
        super(source);
        this.beanClass = source.beanClass;
        this.bean = source.bean;
        this.ref = source.ref;
        this.method = source.method;
        this.beanType = source.beanType;
        this.scope = source.scope;
    }

    public BeanDefinition(String ref) {
        this.ref = ref;
    }

    public BeanDefinition(String ref, String method) {
        this.ref = ref;
        this.method = method;
    }

    @Override
    public BeanDefinition copyDefinition() {
        return new BeanDefinition(this);
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
            return ObjectHelper.className(bean);
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

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setBean(Object bean) {
        this.bean = bean;
    }

    public Object getBean() {
        return bean;
    }

    public String getBeanType() {
        return beanType;
    }

    public void setBeanType(String beanType) {
        this.beanType = beanType;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public void setBeanType(Class<?> beanType) {
        this.beanClass = beanType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope.name();
    }

}
