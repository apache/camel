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
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.ConstantBeanHolder;
import org.apache.camel.component.bean.ConstantStaticTypeBeanHolder;
import org.apache.camel.component.bean.ConstantTypeBeanHolder;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Calls a java bean
 *
 * @version 
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
    @XmlAttribute
    @Deprecated
    private Boolean multiParameterArray;
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

    public String getBeanType() {
        return beanType;
    }

    /**
     * Sets the Class of the bean
     */
    public void setBeanType(String beanType) {
        this.beanType = beanType;
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
    
    public Boolean getMultiParameterArray() {
        return multiParameterArray;
    }

    /**
     * Whether the message body is an array type.
     *
     * @deprecated is to be replaced with a better solution in Camel 3.0
     */
    @Deprecated
    public void setMultiParameterArray(Boolean multiParameterArray) {
        this.multiParameterArray = multiParameterArray;
    }

    // Fluent API
    //-------------------------------------------------------------------------
    /**
     * Sets a reference to a bean to use
     *
     * @param ref  the bean's id in the registry
     * @return the builder
     * @deprecated not in use, will be removed in next Camel release
     */
    @Deprecated
    public BeanDefinition ref(String ref) {
        setRef(ref);
        return this;
    }
    
    /**
     * Sets the method name on the bean to use
     *
     * @param method  the bean's method name which wants camel to call
     * @return the builder
     * @deprecated not in use, will be removed in next Camel release
     */
    @Deprecated
    public BeanDefinition method(String method) {
        setMethod(method);
        return this;
    }
    
    /**
     * Sets an instance of the bean to use
     *
     * @param bean the instance of the bean
     * @return the builder
     * @deprecated not in use, will be removed in next Camel release
     */
    @Deprecated
    public BeanDefinition bean(Object bean) {
        setBean(bean);
        return this;
    }
    
    /**
     * Sets the Class of the bean
     *
     * @param beanType the Class of the bean
     * @return the builder
     * @deprecated not in use, will be removed in next Camel release
     */
    @Deprecated
    public BeanDefinition beanType(Class<?> beanType) {
        setBeanType(beanType);
        return this;
    }

    /**
     * Caches the bean lookup, to avoid lookup up bean on every usage.
     *
     * @return the builder
     */
    @Deprecated
    public BeanDefinition cache() {
        setCache(true);
        return this;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        BeanProcessor answer;
        Class<?> clazz = bean != null ? bean.getClass() : null;
        BeanHolder beanHolder;

        if (ObjectHelper.isNotEmpty(ref)) {
            // lets cache by default
            if (isCacheBean()) {
                // cache the registry lookup which avoids repeat lookup in the registry
                beanHolder = new RegistryBean(routeContext.getCamelContext(), ref).createCacheHolder();
                // bean holder will check if the bean exists
                bean = beanHolder.getBean();
            } else {
                // we do not cache so we invoke on-demand
                beanHolder = new RegistryBean(routeContext.getCamelContext(), ref);
            }
            answer = new BeanProcessor(beanHolder);
        } else {
            if (bean == null) {
                
                if (beanType == null && beanClass == null) {
                    throw new IllegalArgumentException("bean, ref or beanType must be provided");
                }

                // the clazz is either from beanType or beanClass
                if (beanType != null) {
                    try {
                        clazz = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(beanType);
                    } catch (ClassNotFoundException e) {
                        throw ObjectHelper.wrapRuntimeCamelException(e);
                    }
                } else {
                    clazz = beanClass;
                }

                // attempt to create bean using injector which supports auto-wiring
                if (isCacheBean() && routeContext.getCamelContext().getInjector().supportsAutoWiring()) {
                    try {
                        log.debug("Attempting to create new bean instance from class: {} via auto-wiring enabled", clazz);
                        bean = CamelContextHelper.newInstance(routeContext.getCamelContext(), clazz);
                    } catch (Throwable e) {
                        log.debug("Error creating new bean instance from class: " + clazz + ". This exception is ignored", e);
                    }
                }

                // create a bean if there is a default public no-arg constructor
                if (bean == null && isCacheBean() && ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                    log.debug("Class has default no-arg constructor so creating a new bean instance: {}", clazz);
                    bean = CamelContextHelper.newInstance(routeContext.getCamelContext(), clazz);
                    ObjectHelper.notNull(bean, "bean", this);
                }
            }

            // validate the bean type is not from java so you by mistake think its a reference
            // to a bean name but the String is being invoke instead
            if (bean instanceof String) {
                throw new IllegalArgumentException("The bean instance is a java.lang.String type: " + bean
                    + ". We suppose you want to refer to a bean instance by its id instead. Please use ref.");
            }

            // the holder should either be bean or type based
            if (bean != null) {
                beanHolder = new ConstantBeanHolder(bean, routeContext.getCamelContext());
            } else {
                if (isCacheBean() && ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                    // we can only cache if we can create an instance of the bean, and for that we need a public constructor
                    beanHolder = new ConstantTypeBeanHolder(clazz, routeContext.getCamelContext()).createCacheHolder();
                } else {
                    if (ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                        beanHolder = new ConstantTypeBeanHolder(clazz, routeContext.getCamelContext());
                    } else {
                        // this is only for invoking static methods on the bean
                        beanHolder = new ConstantStaticTypeBeanHolder(clazz, routeContext.getCamelContext());
                    }
                }
            }
            answer = new BeanProcessor(beanHolder);
        }
        
        // check for multiParameterArray setting
        if (multiParameterArray != null) {
            answer.setMultiParameterArray(multiParameterArray);
        }

        // check for method exists
        if (method != null) {
            answer.setMethod(method);

            // check there is a method with the given name, and leverage BeanInfo for that
            // which we only do if we are caching the bean as otherwise we will create a bean instance for this check
            // which we only want to do if we cache the bean
            if (isCacheBean()) {
                BeanInfo beanInfo = beanHolder.getBeanInfo();
                if (bean != null) {
                    // there is a bean instance, so check for any methods
                    if (!beanInfo.hasMethod(method)) {
                        throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method));
                    }
                } else if (clazz != null) {
                    // there is no bean instance, so check for static methods only
                    if (!beanInfo.hasStaticMethod(method)) {
                        throw ObjectHelper.wrapRuntimeCamelException(new MethodNotFoundException(null, clazz, method, true));
                    }
                }
            }
        }

        return answer;
    }

    private boolean isCacheBean() {
        return cache == null || cache;
    }

}
