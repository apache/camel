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
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;

/**
 * For expressions and predicates using the
 * <a href="http://camel.apache.org/bean-language.html">bean language</a>
 *
 * @version 
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
        this(beanName, null);
    }

    public MethodCallExpression(String beanName, String method) {
        super(beanName);
        this.method = method;
    }
    
    public MethodCallExpression(Object instance) {
        this(instance, null);
    }
    
    public MethodCallExpression(Object instance, String method) {
        super(instance.getClass().getName());
        this.instance = instance;
        this.method = method;
    }

    public MethodCallExpression(Class<?> type) {
        this(type, null);
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
        Expression answer;
        if (beanType != null) {
            instance = ObjectHelper.newInstance(beanType);
            answer = new BeanExpression(instance, getMethod());
        } else if (instance != null) {
            answer = new BeanExpression(instance, getMethod());
        } else {
            String ref = beanName();
            // if its a ref then check that the ref exists
            BeanHolder holder = new RegistryBean(camelContext, ref);
            // get the bean which will check that it exists
            instance = holder.getBean();
            answer = new BeanExpression(ref, getMethod());
        }

        validateHasMethod(camelContext, instance, getMethod());
        return answer;
    }

    @Override
    public Predicate createPredicate(CamelContext camelContext) {
        return (BeanExpression) createExpression(camelContext);
    }

    /**
     * Validates the given bean has the method.
     * <p/>
     * This implementation will skip trying to validate OGNL method name expressions.
     *
     * @param context  camel context
     * @param bean     the bean instance
     * @param method   the method, can be <tt>null</tt> if no method name provided
     * @throws org.apache.camel.RuntimeCamelException is thrown if bean does not have the method
     */
    protected void validateHasMethod(CamelContext context, Object bean, String method) {
        if (method == null) {
            return;
        }

        // do not try to validate ognl methods
        if (OgnlHelper.isValidOgnlExpression(method)) {
            return;
        }

        // if invalid OGNL then fail
        if (OgnlHelper.isInvalidValidOgnlExpression(method)) {
            ExpressionIllegalSyntaxException cause = new ExpressionIllegalSyntaxException(method);
            throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(bean, method, cause));
        }

        BeanInfo info = new BeanInfo(context, bean.getClass());
        if (!info.hasMethod(method)) {
            throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method));
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