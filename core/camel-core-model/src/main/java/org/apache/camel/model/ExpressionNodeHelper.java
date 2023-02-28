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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.spi.ExpressionResultTypeAware;

/**
 * Helper for {@link ExpressionNode}
 */
public final class ExpressionNodeHelper {

    private ExpressionNodeHelper() {
    }

    /**
     * Determines which {@link ExpressionDefinition} describes the given expression in the best possible way.
     * <p/>
     * This implementation will use types such as {@link SimpleExpression}, {@link XPathExpression} etc. if the given
     * expression is detected as such a type.
     *
     * @param  expression the expression
     * @return            a definition which describes the expression
     */
    public static ExpressionDefinition toExpressionDefinition(Expression expression) {
        if (expression instanceof ExpressionResultTypeAware
                && expression.getClass().getName().equals("org.apache.camel.language.xpath.XPathBuilder")) {
            ExpressionResultTypeAware aware = (ExpressionResultTypeAware) expression;
            // we keep the original expression by using the constructor that
            // accepts an expression
            XPathExpression answer = new XPathExpression(expression);
            answer.setExpression(aware.getExpressionText());
            answer.setResultType(answer.getResultType());
            return answer;
        } else if (expression instanceof ValueBuilder) {
            // ValueBuilder wraps the actual expression so unwrap
            ValueBuilder builder = (ValueBuilder) expression;
            expression = builder.getExpression();
        }

        if (expression instanceof ExpressionDefinition) {
            return (ExpressionDefinition) expression;
        }
        return new ExpressionDefinition(expression);
    }

    /**
     * Determines which {@link ExpressionDefinition} describes the given predicate in the best possible way.
     * <p/>
     * This implementation will use types such as {@link SimpleExpression}, {@link XPathExpression} etc. if the given
     * predicate is detected as such a type.
     *
     * @param  predicate the predicate
     * @return           a definition which describes the predicate
     */
    public static ExpressionDefinition toExpressionDefinition(Predicate predicate) {
        if (predicate instanceof ExpressionResultTypeAware
                && predicate.getClass().getName().equals("org.apache.camel.language.xpath.XPathBuilder")) {
            ExpressionResultTypeAware aware = (ExpressionResultTypeAware) predicate;
            Expression expression = (Expression) predicate;
            // we keep the original expression by using the constructor that
            // accepts an expression
            XPathExpression answer = new XPathExpression(expression);
            answer.setExpression(aware.getExpressionText());
            answer.setResultType(answer.getResultType());
            return answer;
        } else if (predicate instanceof ValueBuilder) {
            // ValueBuilder wraps the actual predicate so unwrap
            ValueBuilder builder = (ValueBuilder) predicate;
            Expression expression = builder.getExpression();
            if (expression instanceof Predicate) {
                predicate = (Predicate) expression;
            }
        }

        if (predicate instanceof ExpressionDefinition) {
            return (ExpressionDefinition) predicate;
        }
        return new ExpressionDefinition(predicate);
    }
}
