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
package org.apache.camel.model.validator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.validator.ProcessorValidator;
import org.apache.camel.model.ExpressionNodeHelper;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.validation.PredicateValidatingProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Validator;

/**
 * Represents a predicate {@link Validator} which leverages expression or predicates to
 * perform content validation. A {@link ProcessorValidator} will be created internally
 * with a {@link PredicateValidatingProcessor} which validates the message according to specified expression/predicates.
 * 
 * {@see ValidatorDefinition}
 * {@see Validator}
 */
@Metadata(label = "validation")
@XmlType(name = "predicateValidator")
@XmlAccessorType(XmlAccessType.FIELD)
public class PredicateValidatorDefinition extends ValidatorDefinition {

    @XmlElementRef
    private ExpressionDefinition expression;

    @Override
    protected Validator doCreateValidator(CamelContext context) throws Exception {
        Predicate pred = getExpression().createPredicate(context);
        PredicateValidatingProcessor processor = new PredicateValidatingProcessor(pred);
        return new ProcessorValidator(context)
            .setProcessor(processor)
            .setType(getType());
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        // favour using the helper to set the expression as it can unwrap some unwanted builders when using Java DSL
        if (expression instanceof Expression) {
            this.expression = ExpressionNodeHelper.toExpressionDefinition((Expression) expression);
        } else if (expression instanceof Predicate) {
            this.expression = ExpressionNodeHelper.toExpressionDefinition((Predicate) expression);
        } else {
            this.expression = expression;
        }
    }

}

