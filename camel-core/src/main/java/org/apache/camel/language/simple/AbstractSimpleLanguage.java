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
package org.apache.camel.language.simple;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.spi.Language;

/**
 * Abstract base class for Simple languages.
 */
public abstract class AbstractSimpleLanguage implements Language {
    
    public Predicate<Exchange> createPredicate(String expression) {
        return PredicateBuilder.toPredicate(createExpression(expression));
    }

    public Expression<Exchange> createExpression(String expression) {
        if (expression.indexOf("${") >= 0) {
            return createComplexExpression(expression);
        }
        return createSimpleExpression(expression);
    }

    protected Expression<Exchange> createComplexExpression(String expression) {
        List<Expression> results = new ArrayList<Expression>();

        int pivot = 0;
        int size = expression.length();
        while (pivot < size) {
            int idx = expression.indexOf("${", pivot);
            if (idx < 0) {
                results.add(createConstantExpression(expression, pivot, size));
                break;
            } else {
                if (pivot < idx) {
                    results.add(createConstantExpression(expression, pivot, idx));
                }
                pivot = idx + 2;
                int endIdx = expression.indexOf("}", pivot);
                if (endIdx < 0) {
                    throw new IllegalArgumentException("Expecting } but found end of string for simple expression: " + expression);
                }
                String simpleText = expression.substring(pivot, endIdx);

                Expression simpleExpression = createSimpleExpression(simpleText);
                results.add(simpleExpression);
                pivot = endIdx + 1;
            }
        }
        return ExpressionBuilder.concatExpression(results, expression);
    }

    protected Expression createConstantExpression(String expression, int start, int end) {
        return ExpressionBuilder.constantExpression(expression.substring(start, end));
    }

    /**
     * Creates the simple expression based on the extracted content from the ${ } place holders
     *
     * @param expression  the content between ${ and }
     * @return the expression
     */
    protected abstract <E extends Exchange> Expression<Exchange> createSimpleExpression(String expression);

    protected String ifStartsWithReturnRemainder(String prefix, String text) {
        if (text.startsWith(prefix)) {
            String remainder = text.substring(prefix.length());
            if (remainder.length() > 0) {
                return remainder;
            }
        }
        return null;
    }
}
