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
package org.apache.camel.model.loadbalancer;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.ExpressionNodeHelper;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Sticky load balancing using an expression to calculate a correlation key to perform the sticky load balancing.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "stickyLoadBalancer")
@XmlAccessorType(XmlAccessType.FIELD)
public class StickyLoadBalancerDefinition extends LoadBalancerDefinition {

    @XmlElement(name = "correlationExpression", required = true)
    private ExpressionSubElementDefinition correlationExpression;

    public StickyLoadBalancerDefinition() {
    }

    public ExpressionSubElementDefinition getCorrelationExpression() {
        return correlationExpression;
    }

    /**
     * The correlation expression to use to calculate the correlation key
     */
    public void setCorrelationExpression(ExpressionSubElementDefinition correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public void setCorrelationExpression(Expression expression) {
        ExpressionDefinition def = ExpressionNodeHelper.toExpressionDefinition(expression);
        this.correlationExpression = new ExpressionSubElementDefinition();
        this.correlationExpression.setExpressionType(def);
    }

    @Override
    public String toString() {
        return "StickyLoadBalancer[" + correlationExpression + "]";
    }
}
