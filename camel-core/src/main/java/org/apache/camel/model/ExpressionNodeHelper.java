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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.SimpleBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.XPathExpression;

/**
 * Helper for {@link ExpressionNode}
 */
public final class ExpressionNodeHelper {

    private ExpressionNodeHelper() {
    }

    /**
     * Determines which {@link ExpressionDefinition} describes the given expression best possible.
     * <p/>
     * This implementation will use types such as {@link SimpleExpression}, {@link XPathExpression} etc.
     * if the given expression is detect as such a type.
     *
     * @param expression the expression
     * @return a definition which describes the expression
     */
    public static ExpressionDefinition toExpressionDefinition(Expression expression) {
        if (expression instanceof SimpleBuilder) {
            SimpleBuilder builder = (SimpleBuilder) expression;
            // we keep the original expression by using the constructor that accepts an expression
            SimpleExpression answer = new SimpleExpression(builder);
            answer.setExpression(builder.getText());
            answer.setResultType(builder.getResultType());
            return answer;
        } else if (expression instanceof XPathBuilder) {
            XPathBuilder builder = (XPathBuilder) expression;
            // we keep the original expression by using the constructor that accepts an expression
            XPathExpression answer = new XPathExpression(builder);
            answer.setExpression(builder.getText());
            answer.setResultType(builder.getResultType());
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
     * Determines which {@link ExpressionDefinition} describes the given predicate best possible.
     * <p/>
     * This implementation will use types such as {@link SimpleExpression}, {@link XPathExpression} etc.
     * if the given predicate is detect as such a type.
     *
     * @param predicate the predicate
     * @return a definition which describes the predicate
     */
    public static ExpressionDefinition toExpressionDefinition(Predicate predicate) {
        if (predicate instanceof SimpleBuilder) {
            SimpleBuilder builder = (SimpleBuilder) predicate;
            // we keep the original expression by using the constructor that accepts an expression
            SimpleExpression answer = new SimpleExpression(builder);
            answer.setExpression(builder.getText());
            return answer;
        } else if (predicate instanceof XPathBuilder) {
            XPathBuilder builder = (XPathBuilder) predicate;
            // we keep the original expression by using the constructor that accepts an expression
            XPathExpression answer = new XPathExpression(builder);
            answer.setExpression(builder.getText());
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
