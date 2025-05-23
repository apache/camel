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
package org.apache.camel.model.validator;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.ExpressionNodeHelper;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To use a predicate to perform validation on the route level.
 */
@Metadata(label = "validation")
@XmlRootElement(name = "predicateValidator")
@XmlAccessorType(XmlAccessType.FIELD)
public class PredicateValidatorDefinition extends ValidatorDefinition {

    @XmlElementRef
    private ExpressionDefinition expression;

    public PredicateValidatorDefinition() {
    }

    protected PredicateValidatorDefinition(PredicateValidatorDefinition source) {
        super(source);
        this.expression = source.expression != null ? source.expression.copyDefinition() : null;
    }

    @Override
    public ValidatorDefinition copyDefinition() {
        return new PredicateValidatorDefinition(this);
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        // favour using the helper to set the expression as it can unwrap some
        // unwanted builders when using Java DSL
        this.expression = ExpressionNodeHelper.toExpressionDefinition((Expression) expression);
    }

}
