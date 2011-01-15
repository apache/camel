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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.RegistryBean;
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
    @XmlAttribute(required = false)
    private Class parameterType;

    public MethodCallExpression() {
    }

    public MethodCallExpression(String beanName) {
        this(beanName, null);
    }

    public MethodCallExpression(String beanName, String method) {
        this(beanName, method, null);
    }
    
    public MethodCallExpression(String beanName, String method, Class parameterType) {
        super(beanName);
        this.method = method;
        this.parameterType = parameterType;
    }
    
    public MethodCallExpression(Object instance) {
        this(instance, null);
    }

    public MethodCallExpression(Object instance, String method) {
        this(instance, method, null);
    }
    
    public MethodCallExpression(Object instance, String method, Class parameterType) {
        super(instance.getClass().getName());
        this.instance = instance;
        this.method = method;
        this.parameterType = parameterType;
    }

    public MethodCallExpression(Class<?> type) {
        this(type, null);
    }
    
    public MethodCallExpression(Class<?> type, String method) {
        this(type, method, null);
    }
    
    public MethodCallExpression(Class<?> type, String method, Class parameterType) {
        super(type.toString());
        this.beanType = type;
        this.method = method;
        this.parameterType = parameterType;
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
    
    public Class getParameterType() {
        return parameterType;
    }

    public void setParameterType(Class parameterType) {
        this.parameterType = parameterType;
    }
    
    @Override
    public Expression createExpression(CamelContext camelContext) {
        if (beanType != null) {
            instance = ObjectHelper.newInstance(beanType);
            return new BeanExpression(instance, getMethod(), parameterType);
        } else if (instance != null) {
            return new BeanExpression(instance, getMethod(), parameterType);
        } else {
            String ref = beanName();
            // if its a ref then check that the ref exists
            BeanHolder holder = new RegistryBean(camelContext, ref);
            // get the bean which will check that it exists
            instance = holder.getBean();
            // only validate when it was a ref for a bean, so we can eager check
            // this on startup of Camel
            validateHasMethod(camelContext, instance, getMethod(), parameterType);
            return new BeanExpression(ref, getMethod(), parameterType);
        }
    }

    @Override
    public Predicate createPredicate(CamelContext camelContext) {
        return (BeanExpression) createExpression(camelContext);
    }

    /**
     * Validates the given bean has the method
     *
     * @param context  camel context
     * @param bean     the bean instance
     * @param method   the method, can be <tt>null</tt> if no method name provided
     * @throws org.apache.camel.RuntimeCamelException is thrown if bean does not have the method
     */
    @SuppressWarnings("rawtypes")
    protected void validateHasMethod(CamelContext context, Object bean, String method, Class parameterType) {
        if (method == null) {
            return;
        }

        BeanInfo info = new BeanInfo(context, bean.getClass());
        List<Class> parameterTypes = new ArrayList<Class>();
        if (parameterType != null) {
            parameterTypes.add(parameterType);            
        }
        if (!info.hasMethod(method, parameterTypes)) {
            throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method, parameterTypes));
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
        return "bean{" + beanName() + (method != null ? ", method=" + method : "") + (parameterType != null ? ", parameterTypes=" + parameterType : "") + "}";
    }
}