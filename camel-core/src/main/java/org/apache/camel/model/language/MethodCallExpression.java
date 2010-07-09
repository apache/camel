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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Predicate;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.util.ObjectHelper;

/**
 * For expressions and predicates using the
 * <a href="http://camel.apache.org/bean-language.html">bean language</a>
 *
 * @version $Revision$
 */
@XmlRootElement(name = "method")
@XmlAccessorType(XmlAccessType.FIELD)
public class MethodCallExpression extends ExpressionDefinition {
    @XmlAttribute(required = false)
    @Deprecated
    private String bean;
    @XmlAttribute(required = false)
    private String ref;
    @XmlAttribute(required = false)
    private String method;
    @XmlAttribute(required = false)
    private Class<?> beanType;
    @XmlTransient
    private Object instance;

    public MethodCallExpression() {
    }

    public MethodCallExpression(String beanName) {
        super(beanName);
    }

    public MethodCallExpression(String beanName, String method) {
        super(beanName);
        this.method = method;
    }
    
    public MethodCallExpression(Object instance) {
        super(instance.getClass().getName());
        this.instance = instance;
    }

    public MethodCallExpression(Object instance, String method) {
        super(instance.getClass().getName());
        this.instance = instance;
        this.method = method;
    }

    public MethodCallExpression(Class<?> type) {
        super(type.toString());
        this.beanType = type;        
    }
    
    public MethodCallExpression(Class<?> type, String method) {
        super(type.toString());
        this.beanType = type;
        this.method = method;
    }

    public String getLanguage() {
        return "bean";
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
    
    @Override
    public Expression createExpression(CamelContext camelContext) {
        if (beanType != null) {            
            return new BeanExpression(ObjectHelper.newInstance(beanType), getMethod());
        } else if (instance != null) {
            return new BeanExpression(instance, getMethod());
        } else {
            String ref = beanName();
            // if its a ref then check that the ref exists
            if (camelContext.getRegistry().lookup(ref) == null) {
                throw new NoSuchBeanException(ref);
            }
            return new BeanExpression(ref, getMethod());
        }
    }

    @Override
    public Predicate createPredicate(CamelContext camelContext) {
        if (beanType != null) {
            return new BeanExpression(ObjectHelper.newInstance(beanType), getMethod());
        } else if (instance != null) {
            return new BeanExpression(instance, getMethod());
        } else {
            String ref = beanName();
            // if its a ref then check that the ref exists
            if (camelContext.getRegistry().lookup(ref) == null) {
                throw new NoSuchBeanException(ref);
            }
            return new BeanExpression(ref, getMethod());
        }
    }

    protected String beanName() {
        if (bean != null) {
            return bean;
        } else if (ref != null) {
            return ref;
        } else if (instance != null) {
            return ObjectHelper.className(instance);
        }
        return getExpression();
    }

    @Override
    public String toString() {
        return "bean{" + beanName() + (method != null ? ", method=" + method : "") + "}";
    }
}
