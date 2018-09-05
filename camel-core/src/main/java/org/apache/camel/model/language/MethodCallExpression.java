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
import org.apache.camel.component.bean.ConstantBeanHolder;
import org.apache.camel.component.bean.ConstantStaticTypeBeanHolder;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;

/**
 * To use a Java bean (aka method call) in Camel expressions or predicates.
 *
 * @version
 */
@Metadata(firstVersion = "1.3.0", label = "language,core,java", title = "Bean method")
@XmlRootElement(name = "method")
@XmlAccessorType(XmlAccessType.FIELD)
public class MethodCallExpression extends ExpressionDefinition {
    @XmlAttribute
    @Deprecated
    private String bean;
    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String method;
    @XmlAttribute(name = "beanType")
    private String beanTypeName;
    @XmlTransient
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
        super(ObjectHelper.className(instance));
        // must use setter as they have special logic
        setInstance(instance);
        setMethod(method);
    }

    public MethodCallExpression(Class<?> type) {
        this(type, null);
    }

    public MethodCallExpression(Class<?> type, String method) {
        super(type.getName());
        this.beanType = type;
        this.method = method;
    }

    public String getLanguage() {
        return "bean";
    }

    public String getBean() {
        return bean;
    }

    /**
     * Either a reference or a class name of the bean to use
     */
    public void setBean(String bean) {
        this.bean = bean;
    }

    public String getRef() {
        return ref;
    }

    /**
     * Reference to bean to lookup in the registry
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getMethod() {
        return method;
    }

    /**
     * Name of method to call
     */
    public void setMethod(String method) {
        this.method = method;
    }

    public Class<?> getBeanType() {
        return beanType;
    }

    public void setBeanType(Class<?> beanType) {
        this.beanType = beanType;
        this.instance = null;
    }

    public String getBeanTypeName() {
        return beanTypeName;
    }

    /**
     * Class name of the bean to use
     */
    public void setBeanTypeName(String beanTypeName) {
        this.beanTypeName = beanTypeName;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        // people may by mistake pass in a class type as the instance
        if (instance instanceof Class) {
            this.beanType = (Class<?>) instance;
            this.instance = null;
        } else {
            this.beanType = null;
            this.instance = instance;
        }
    }

    @Override
    public Expression createExpression(CamelContext camelContext) {
        Expression answer;

        if (beanType == null && beanTypeName != null) {
            try {
                beanType = camelContext.getClassResolver().resolveMandatoryClass(beanTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        BeanHolder holder;
        if (beanType != null) {
            // create a bean if there is a default public no-arg constructor
            if (ObjectHelper.hasDefaultPublicNoArgConstructor(beanType)) {
                instance = camelContext.getInjector().newInstance(beanType);
                holder = new ConstantBeanHolder(instance, camelContext);
            } else {
                holder = new ConstantStaticTypeBeanHolder(beanType, camelContext);
            }
        } else if (instance != null) {
            holder = new ConstantBeanHolder(instance, camelContext);
        } else {
            String ref = beanName();
            // if its a ref then check that the ref exists
            BeanHolder regHolder = new RegistryBean(camelContext, ref);
            // get the bean which will check that it exists
            instance = regHolder.getBean();
            holder = new ConstantBeanHolder(instance, camelContext);
        }

        // create answer using the holder
        answer = new BeanExpression(holder, getMethod());

        // and do sanity check that if a method name was given, that it exists
        validateHasMethod(camelContext, instance, beanType, getMethod());
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
     * @param type     the bean type
     * @param method   the method, can be <tt>null</tt> if no method name provided
     * @throws org.apache.camel.RuntimeCamelException is thrown if bean does not have the method
     */
    protected void validateHasMethod(CamelContext context, Object bean, Class<?> type, String method) {
        if (method == null) {
            return;
        }

        if (bean == null && type == null) {
            throw new IllegalArgumentException("Either bean or type should be provided on " + this);
        }

        // do not try to validate ognl methods
        if (OgnlHelper.isValidOgnlExpression(method)) {
            return;
        }

        // if invalid OGNL then fail
        if (OgnlHelper.isInvalidValidOgnlExpression(method)) {
            ExpressionIllegalSyntaxException cause = new ExpressionIllegalSyntaxException(method);
            throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(bean != null ? bean : type, method, cause));
        }

        if (bean != null) {
            BeanInfo info = new BeanInfo(context, bean.getClass());
            if (!info.hasMethod(method)) {
                throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method));
            }
        } else {
            BeanInfo info = new BeanInfo(context, type);
            // must be a static method as we do not have a bean instance to invoke
            if (!info.hasStaticMethod(method)) {
                throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(null, type, method, true));
            }
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
        boolean isRef = bean != null || ref != null;
        return "bean[" + (isRef ? "ref:" : "") + beanName() + (method != null ? " method:" + method : "") + "]";
    }
}