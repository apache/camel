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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;

/**
 * Represents an expression sub element
 */
@XmlRootElement(name = "expression") // must be named expression
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionSubElementDefinition {
    @XmlElementRef
    private ExpressionDefinition expressionType;

    public ExpressionSubElementDefinition() {
    }

    public ExpressionSubElementDefinition(Expression expression) {
        this.expressionType = new ExpressionDefinition(expression);
    }

    public ExpressionSubElementDefinition(Predicate predicate) {
        this.expressionType = new ExpressionDefinition(predicate);
    }

    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

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
