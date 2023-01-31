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
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PredicateExceptionFactory;

/**
 * Validates a message based on an expression
 */
@Metadata(label = "eip,transformation")
@AsPredicate
@XmlRootElement(name = "validate")
@XmlAccessorType(XmlAccessType.FIELD)
public class ValidateDefinition extends ExpressionNode {

    @XmlTransient
    private PredicateExceptionFactory factory;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.spi.PredicateExceptionFactory")
    private String predicateExceptionFactory;

    public ValidateDefinition() {
    }

    public ValidateDefinition(Expression expression) {
        super(expression);
    }

    public ValidateDefinition(Predicate predicate) {
        super(predicate);
    }

    @Override
    public String toString() {
        return "Validate[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "validate";
    }

    @Override
    public String getLabel() {
        return "validate[" + getExpression() + "]";
    }

    /**
     * Expression to use for validation as a predicate. The expression should return either <tt>true</tt> or
     * <tt>false</tt>. If returning <tt>false</tt> the message is invalid and an exception is thrown.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public PredicateExceptionFactory getFactory() {
        return factory;
    }

    public String getPredicateExceptionFactory() {
        return predicateExceptionFactory;
    }

    /**
     * The bean id of custom PredicateExceptionFactory to use for creating the exception when the validation fails.
     *
     * By default, Camel will throw PredicateValidationException. By using a custom factory you can control which
     * exception to throw instead.
     */
    public void setPredicateExceptionFactory(String predicateExceptionFactory) {
        this.predicateExceptionFactory = predicateExceptionFactory;
    }

    /**
     * The custom PredicateExceptionFactory to use for creating the exception when the validation fails.
     *
     * By default, Camel will throw PredicateValidationException. By using a custom factory you can control which
     * exception to throw instead.
     */
    public ValidateDefinition predicateExceptionFactory(PredicateExceptionFactory factory) {
        this.factory = factory;
        return this;
    }

    /**
     * The bean id of the custom PredicateExceptionFactory to use for creating the exception when the validation fails.
     *
     * By default, Camel will throw PredicateValidationException. By using a custom factory you can control which
     * exception to throw instead.
     */
    public ValidateDefinition predicateExceptionFactory(String ref) {
        this.predicateExceptionFactory = ref;
        return this;
    }

}
