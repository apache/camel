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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.spi.Metadata;

/**
 * A route template bean (local bean)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "templateBean")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateBeanDefinition {
    @XmlAttribute(required = true)
    String name;
    @XmlAttribute
    private String beanType;
    @XmlTransient
    private Class<?> beanClass;
    @XmlTransient
    private RouteTemplateContext.BeanSupplier<Object> beanSupplier;
    @XmlElement(name = "beanExpression")
    private ExpressionSubElementDefinition beanExpression;

    public RouteTemplateBeanDefinition() {
    }

    public RouteTemplateBeanDefinition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Bean name
     */
    public void setName(String name) {
        this.name = name;
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

    public RouteTemplateContext.BeanSupplier<Object> getBeanSupplier() {
        return beanSupplier;
    }

    /**
     * Bean supplier that uses lambda style to create the local bean
     */
    public void setBeanSupplier(RouteTemplateContext.BeanSupplier<Object> beanSupplier) {
        this.beanSupplier = beanSupplier;
    }

    public ExpressionSubElementDefinition getBeanExpression() {
        return beanExpression;
    }

    /**
     * Bean supplier that uses Camel expression to create the local bean. For example to use groovy, joor, or other
     * languages as scripting some code that creates the bean
     */
    public void setBeanExpression(ExpressionSubElementDefinition beanExpression) {
        this.beanExpression = beanExpression;
    }

    /**
     * Bean supplier that uses Camel expression to create the local bean. For example to use groovy, joor, or other
     * languages as scripting some code that creates the bean
     */
    public void setBeanExpression(Expression expression) {
        this.beanExpression = new ExpressionSubElementDefinition(ExpressionNodeHelper.toExpressionDefinition(expression));
    }

}
