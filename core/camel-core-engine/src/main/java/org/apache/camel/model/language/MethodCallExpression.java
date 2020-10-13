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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * Call a method of the specified Java bean passing the Exchange, Body or specific headers to it.
 */
@Metadata(firstVersion = "1.3.0", label = "language,core,java", title = "Bean method")
@XmlRootElement(name = "method")
@XmlAccessorType(XmlAccessType.FIELD)
public class MethodCallExpression extends ExpressionDefinition {
    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String method;
    @XmlAttribute(name = "beanType")
    private String beanTypeName;
    @XmlAttribute
    @Metadata(defaultValue = "Singleton", enums = "Singleton,Request,Prototype")
    private String scope;
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
        super(""); // we dont use @XmlValue but the attributes instead
        if (beanName != null && beanName.startsWith("ref:")) {
            beanName = beanName.substring(4);
        } else if (beanName != null && beanName.startsWith("bean:")) {
            beanName = beanName.substring(5);
        }
        setRef(beanName);
        setMethod(method);
    }

    public MethodCallExpression(Object instance) {
        this(instance, null);
    }

    public MethodCallExpression(Object instance, String method) {
        super(""); // we dont use @XmlValue but the attributes instead
        // must use setter as they have special logic
        setInstance(instance);
        setMethod(method);
    }

    public MethodCallExpression(Class<?> type) {
        this(type, null);
    }

    public MethodCallExpression(Class<?> type, String method) {
        super(""); // we dont use @XmlValue but the attributes instead
        setBeanType(type);
        setBeanTypeName(type.getName());
        setMethod(method);
    }

    @Override
    public String getLanguage() {
        return "bean";
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

    public String getScope() {
        return scope;
    }

    /**
     * Scope of bean.
     *
     * When using singleton scope (default) the bean is created or looked up only once and reused for the lifetime of
     * the endpoint. The bean should be thread-safe in case concurrent threads is calling the bean at the same time.
     * When using request scope the bean is created or looked up once per request (exchange). This can be used if you
     * want to store state on a bean while processing a request and you want to call the same bean instance multiple
     * times while processing the request. The bean does not have to be thread-safe as the instance is only called from
     * the same request. When using prototype scope, then the bean will be looked up or created per call. However in
     * case of lookup then this is delegated to the bean registry such as Spring or CDI (if in use), which depends on
     * their configuration can act as either singleton or prototype scope. so when using prototype scope then this
     * depends on the bean registry implementation.
     */
    public void setScope(String scope) {
        this.scope = scope;
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

    private String beanName() {
        if (ref != null) {
            return ref;
        } else if (instance != null) {
            return ObjectHelper.className(instance);
        }
        return getExpression();
    }

    @Override
    public String toString() {
        boolean isRef = ref != null;
        return "bean[" + (isRef ? "ref:" : "") + beanName() + (method != null ? " method:" + method : "") + "]";
    }

}
