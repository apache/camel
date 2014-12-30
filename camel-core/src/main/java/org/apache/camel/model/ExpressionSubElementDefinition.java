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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an expression sub element
 */
@XmlRootElement(name = "expression") // must be named expression
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionSubElementDefinition {
    @XmlElementRef
    private ExpressionDefinition expressionType;
    @XmlTransient
    private Expression expression;
    @XmlTransient
    private Predicate predicate;

    public ExpressionSubElementDefinition() {
    }

    public ExpressionSubElementDefinition(Expression expression) {
        this.expression = expression;
    }

    public ExpressionSubElementDefinition(Predicate predicate) {
        this.predicate = predicate;
    }   
    
    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    public Expression getExpression() {
        return expression;
    }   
    
    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public Predicate getPredicate() {
        return predicate;
    }    
    
    public Expression createExpression(RouteContext routeContext) {
        ExpressionDefinition expressionType = getExpressionType();
        if (expressionType != null && expression == null) {
            expression = expressionType.createExpression(routeContext);
        }
        return expression;
    }
    
    public Predicate createPredicate(RouteContext routeContext) {
        ExpressionDefinition expressionType = getExpressionType();
        if (expressionType != null && getPredicate() == null) {
            setPredicate(expressionType.createPredicate(routeContext));
        }
        return getPredicate();
    }

    @Override
    public String toString() {
        if (expression != null) {
            return expression.toString();
        } else if (expressionType != null) {
            return expressionType.toString();
        } else if (predicate != null) {
            return predicate.toString();
        }
        return super.toString();
    }
}

