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
package org.apache.camel.support;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;

/**
 * Base class for {@link Language} implementations that support a result type and different sources of input data.
 */
public abstract class SingleInputTypedLanguageSupport extends TypedLanguageSupport {

    @Override
    public Predicate createPredicate(String expression) {
        return createPredicate(expression, null);
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    /**
     * Whether using result type is supported
     */
    protected boolean supportResultType() {
        return true;
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        if (expression != null && isStaticResource(expression)) {
            expression = loadResource(expression);
        }

        Class<?> type = property(Class.class, properties, 0, null);
        String source = property(String.class, properties, 1, null);
        Expression input = ExpressionBuilder.singleInputExpression(source);
        if (getCamelContext() != null) {
            input.init(getCamelContext());
        }
        if (type == null || type == Object.class || !supportResultType()) {
            return createExpression(input, expression, properties);
        }
        return ExpressionBuilder.convertToExpression(createExpression(input, expression, properties), type);
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        if (expression != null && isStaticResource(expression)) {
            expression = loadResource(expression);
        }

        Class<?> type = property(Class.class, properties, 0, null);
        String source = property(String.class, properties, 1, null);
        Expression input = ExpressionBuilder.singleInputExpression(source);
        if (getCamelContext() != null) {
            input.init(getCamelContext());
        }
        return createPredicate(input, expression, properties);
    }

    /**
     * Creates an expression based on the input with properties.
     *
     * @param  source     the expression allowing to retrieve the input data of the main expression.
     * @param  expression the main expression to evaluate.
     * @param  properties configuration properties (optimized as object array with hardcoded positions for properties)
     * @return            the created expression
     */
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a predicate based on the input with properties.
     *
     * @param  source     the expression allowing to retrieve the input data of the main expression.
     * @param  expression the main expression to evaluate as predicate.
     * @param  properties configuration properties (optimized as object array with hardcoded positions for properties)
     * @return            the created predicate
     */
    public Predicate createPredicate(Expression source, String expression, Object[] properties) {
        return PredicateBuilder.toPredicate(createExpression(source, expression, properties));
    }
}
