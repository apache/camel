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

import org.apache.camel.Processor;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;bean/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "bean")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanDefinition extends OutputDefinition<BeanDefinition> {
    @XmlAttribute(required = false)
    private String ref;
    @XmlAttribute(required = false)
    private String method;
    @XmlAttribute(required = false)
    private Class<?> beanType;
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
        return "Bean[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "bean";
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

    public Class<?> getBeanType() {
        return beanType;
    }

    public void setBeanType(Class<?> beanType) {
        this.beanType = beanType;
    }
    
    // Fluent API
    //-------------------------------------------------------------------------
    /**
     * Sets the ref String on camel bean
     *
     * @param ref  the bean's id in the registry
     * @return the builder
     */
    public BeanDefinition ref(String ref) {
        setRef(ref);
        return this;
    }
    
    /**
     * Sets the calling method name of camel bean
     *
     * @param method  the bean's method name which wants camel to call
     * @return the builder
     */
    public BeanDefinition method(String method) {
        setMethod(method);
        return this;
    }
    
    /**
     * Sets the bean's instance that camel to call
     *
     * @param bean the instance of the bean
     * @return the builder
     */
    public BeanDefinition bean(Object bean) {
        setBean(bean);
        return this;
    }
    
    /**
     * Sets the Class of the bean that camel will instantiation it for calling
     *
     * @param beanType the Class of the bean
     * @return the builder
     */
    public BeanDefinition beanType(Class<?> beanType) {
        setBean(beanType);
        return this;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        BeanProcessor answer;
        if (ObjectHelper.isNotEmpty(ref)) {
            answer = new BeanProcessor(new RegistryBean(routeContext.getCamelContext(), ref));
        } else {
            if (bean == null) {
                ObjectHelper.notNull(beanType, "bean, ref or beanType", this);
                bean = CamelContextHelper.newInstance(routeContext.getCamelContext(), beanType);
            }
            ObjectHelper.notNull(bean, "bean", this);

            // validate the bean type is not from java so you by mistake think its a reference
            // to a bean name but the String is being invoke instead
            if (bean instanceof String) {
                throw new IllegalArgumentException("The bean instance is a java.lang.String type: " + bean
                    + ". We suppose you want to refer to a bean instance by its id instead. Please use beanRef.");
            }
            answer = new BeanProcessor(bean, routeContext.getCamelContext());
        }
        if (method != null) {
            answer.setMethod(method);
        }
        return answer;
    }

    @Override
    public String getLabel() {
        if (ref != null) {
            String methodText = "";
            if (method != null) {
                methodText = " method: " + method;
            }
            return "ref:" + ref + methodText;
        } else if (bean != null) {
            return bean.toString();
        } else if (beanType != null) {
            return beanType.getName();
        } else {
            return "";
        }
    }
}
