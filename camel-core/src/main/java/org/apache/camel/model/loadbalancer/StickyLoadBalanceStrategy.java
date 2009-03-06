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
package org.apache.camel.model.loadbalancer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;

/**
 * Represents an XML &lt;sticky/&gt; element
 */
@XmlRootElement(name = "sticky")
@XmlAccessorType(XmlAccessType.FIELD)
public class StickyLoadBalanceStrategy extends LoadBalancerDefinition {
    @XmlElement(required = true, name = "expression", type = ExpressionDefinition.class)
    private ExpressionDefinition expressionType;
    @XmlElement(required = false, name = "loadBalancer", type = LoadBalancerDefinition.class)
    private LoadBalancerDefinition loadBalancerType;

    public StickyLoadBalanceStrategy() {
        super("org.apache.camel.processor.loadbalancer.StickyLoadBalancer");
    }

    public StickyLoadBalanceStrategy(ExpressionDefinition expressionType) {
        this();
        this.expressionType = expressionType;
    }

    public StickyLoadBalanceStrategy(ExpressionDefinition expressionType, LoadBalancerDefinition loadBalancerType) {
        this();
        this.expressionType = expressionType;
        this.loadBalancerType = loadBalancerType;
    }

    public void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

    public void setLoadBalancerType(LoadBalancerDefinition loadBalancerType) {
        this.loadBalancerType = loadBalancerType;
    }

    public LoadBalancerDefinition getLoadBalancerType() {
        return loadBalancerType;
    }

    @Override
    protected void configureLoadBalancer(LoadBalancer loadBalancer) {
        ExpressionDefinition expression = getExpressionType();
        if (expression != null) {
            setProperty(loadBalancer, "correlationExpression", expression);
        }
        LoadBalancerDefinition type = getLoadBalancerType();
        if (type != null) {
            setProperty(loadBalancer, "loadBalancer", type);
        }
    }

    @Override
    public String toString() {
        return "StickyLoadBalanceStrategy[" + expressionType + ", " + loadBalancerType + "]";
    }
}
