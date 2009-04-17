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
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.IsSingleton;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.language.simple.SimpleLangaugeOperator.*;

/**
 * Abstract base class for Simple languages.
 */
public abstract class SimpleLanguageSupport implements Language, IsSingleton {

    protected static final Pattern PATTERN = Pattern.compile(
            "^\\$\\{(.+)\\}\\s+(==|>|>=|<|<=|!=|contains|not contains|regex|not regex|in|not in)\\s+(.+)$");
    protected final Log log = LogFactory.getLog(getClass());

    public Predicate createPredicate(String expression) {
        return PredicateBuilder.toPredicate(createExpression(expression));
    }

    public Expression createExpression(String expression) {
        Matcher matcher = PATTERN.matcher(expression);
        if (matcher.matches()) {
            if (log.isDebugEnabled()) {
                log.debug("Expression is evaluated as operator expression: " + expression);
            }
            return createOperatorExpression(matcher, expression);
        } else if (expression.indexOf("${") >= 0) {
            if (log.isDebugEnabled()) {
                log.debug("Expression is evaluated as complex expression: " + expression);
            }
            return createComplexConcatExpression(expression);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Expression is evaluated as simple expression: " + expression);
            }
            return createSimpleExpression(expression);
        }
    }

    private Expression createOperatorExpression(final Matcher matcher, final String expression) {
        final Expression left = createSimpleExpression(matcher.group(1));
        final SimpleLangaugeOperator operator = asOperator(matcher.group(2));

        // the right hand side expression can either be a constant expression wiht ' '
        // or another simple expression using ${ } placeholders
        String text = matcher.group(3);

        final Expression right;
        final Expression rightConverted;
        // special null handling
        if ("null".equals(text)) {
            right = createConstantExpression(null);
            rightConverted = right;
        } else {
            // text can either be a constant enclosed by ' ' or another expression using ${ } placeholders
            String constant = ObjectHelper.between(text, "'", "'");
            if (constant == null) {
                // if no ' ' around then fallback to the text itself
                constant = text;
            }
            String simple = ObjectHelper.between(text, "${", "}");

            right = simple != null ? createSimpleExpression(simple) : createConstantExpression(constant);
            // to support numeric comparions using > and < operators we must convert the right hand side
            // to the same type as the left
            rightConverted = ExpressionBuilder.convertToExpression(right, left);
        }

        return new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Predicate predicate = null;
                if (operator == EQ) {
                    predicate = PredicateBuilder.isEqualTo(left, rightConverted);
                } else if (operator == GT) {
                    predicate = PredicateBuilder.isGreaterThan(left, rightConverted);
                } else if (operator == GTE) {
                    predicate = PredicateBuilder.isGreaterThanOrEqualTo(left, rightConverted);
                } else if (operator == LT) {
                    predicate = PredicateBuilder.isLessThan(left, rightConverted);
                } else if (operator == LTE) {
                    predicate = PredicateBuilder.isLessThanOrEqualTo(left, rightConverted);
                } else if (operator == NOT) {
                    predicate = PredicateBuilder.isNotEqualTo(left, rightConverted);
                } else if (operator == CONTAINS || operator == NOT_CONTAINS) {
                    predicate = PredicateBuilder.contains(left, rightConverted);
                    if (operator == NOT_CONTAINS) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                } else if (operator == REGEX || operator == NOT_REGEX) {
                    // reg ex should use String pattern, so we evalute the right hand side as a String
                    predicate = PredicateBuilder.regex(left, right.evaluate(exchange, String.class));
                    if (operator == NOT_REGEX) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                } else if (operator == IN || operator == NOT_IN) {
                    // okay the in operator is a bit more complex as we need to build a list of values
                    // from the right handside expression.
                    // each element on the right handside must be separated by comma (default for create iterator)
                    Iterator it = ObjectHelper.createIterator(right.evaluate(exchange, Object.class));
                    List<Object> values = new ArrayList<Object>();
                    while (it.hasNext()) {
                        values.add(it.next());
                    }
                    // then reuse value builder to create the in predicate with the list of values
                    ValueBuilder vb = new ValueBuilder(left);
                    predicate = vb.in(values.toArray());
                    if (operator == NOT_IN) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                }

                if (predicate == null) {
                    throw new IllegalArgumentException("Unsupported operator: " + operator + " for expression: " + expression);
                }

                boolean matches = predicate.matches(exchange);
                return exchange.getContext().getTypeConverter().convertTo(type, matches);
            }

            @Override
            public String toString() {
                return left + " " + operator + " " + right;
            }
        };
    }

    protected Expression createComplexConcatExpression(String expression) {
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

    protected Expression createConstantExpression(String expression) {
        return ExpressionBuilder.constantExpression(expression);
    }

    /**
     * Creates the simple expression based on the extracted content from the ${ } place holders
     *
     * @param expression  the content between ${ and }
     * @return the expression
     */
    protected abstract Expression createSimpleExpression(String expression);

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
