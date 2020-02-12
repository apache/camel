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
 * To use a Java bean (aka method call) in Camel expressions or predicates.
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
        super((String)null); // we dont use @XmlValue but the attributes instead
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
        super((String)null); // we dont use @XmlValue but the attributes instead
        // must use setter as they have special logic
        setInstance(instance);
        setMethod(method);
    }

    public MethodCallExpression(Class<?> type) {
        this(type, null);
    }

    public MethodCallExpression(Class<?> type, String method) {
        super((String)null); // we dont use @XmlValue but the attributes instead
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

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        // people may by mistake pass in a class type as the instance
        if (instance instanceof Class) {
            this.beanType = (Class<?>)instance;
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
