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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Represents an expression sub element
 */
@Metadata(description = "Represents an expression sub-element that can be embedded within other EIP definitions")
@XmlRootElement(name = "expression") // must be named expression
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionSubElementDefinition implements HasExpressionType {

    @XmlElementRef
    @Metadata(description = "Expression to evaluate.")
    private ExpressionDefinition expressionType;

    public ExpressionSubElementDefinition() {
    }

    public ExpressionSubElementDefinition(ExpressionSubElementDefinition source) {
        this.expressionType = source.expressionType != null ? source.expressionType.copyDefinition() : null;
    }

    public ExpressionSubElementDefinition(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    public ExpressionSubElementDefinition(Expression expression) {
        // unwrap a builder such as simple("...") to its expression definition, so a copy (each route created from a
        // route template) gets its own expression rather than sharing one that is created only once
        this.expressionType = ExpressionNodeHelper.toExpressionDefinition(expression);
    }

    public ExpressionSubElementDefinition(Predicate predicate) {
        this.expressionType = ExpressionNodeHelper.toExpressionDefinition(predicate);
    }

    public ExpressionSubElementDefinition copyDefinition() {
        return new ExpressionSubElementDefinition(this);
    }

    @Override
    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

    @Override
    public void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    @Override
    public String toString() {
        if (expressionType != null) {
            return expressionType.toString();
        }
        return super.toString();
    }
}
