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
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.language.simple.SimpleLanguageOperator.*;

/**
 * Abstract base class for Simple languages.
 */
public abstract class SimpleLanguageSupport implements Language, IsSingleton {

    // this is a regex for a given group in a simple expression that uses operators
    protected static final String OPERATOR_REGEX =
        "\\$\\{(\\S+)\\}\\s+(==|>|>=|<|<=|!=|contains|not contains|regex|not regex|in|not in|is|not is|range|not range)\\s+('.*?'|\\S+)";

    // this is the operator reg ex pattern used to match if a given expression is operator based or not
    protected static final Pattern OPERATOR_PATTERN = Pattern.compile(OPERATOR_REGEX);

    // two specialized pattern for matching/finding multiple expressions combined using multiple and|or operators
    protected static final Pattern ANDOR_PATTERN = Pattern.compile("\\s+(and|or)\\s+" + OPERATOR_REGEX);
    protected static final Pattern START_ANDOR_PATTERN = Pattern.compile("^" + OPERATOR_REGEX + "\\s+(and|or)\\s+.*$");

    // this is special for the range operator where you define the range as from..to (where from and to are numbers)
    protected static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(\\.\\.)(\\d+)$");
    protected final Log log = LogFactory.getLog(getClass());

    /**
     * A holder class to hold an operator and the expression.
     * <p/>
     * This is used for expression with multiple expressions grouped using and/or operators
     */
    private final class ExpressionGroup extends KeyValueHolder<SimpleLanguageOperator, Expression> {

        public ExpressionGroup(SimpleLanguageOperator key, Expression value) {
            super(key, value);
        }

        @Override
        public String toString() {
            return getKey() + " " + getValue();
        }
    }

    public Predicate createPredicate(String expression) {
        return PredicateBuilder.toPredicate(createExpression(expression));
    }

    public Expression createExpression(String expression) {
        Matcher matcher = OPERATOR_PATTERN.matcher(expression);
        Matcher startMatcher = START_ANDOR_PATTERN.matcher(expression);
        if (matcher.matches() || startMatcher.matches()) {
            if (log.isDebugEnabled()) {
                log.debug("Expression is evaluated as simple (with operator) expression: " + expression);
            }
            return createOperatorExpression(matcher, startMatcher, expression);
        } else if (expression.indexOf("${") >= 0) {
            if (log.isDebugEnabled()) {
                log.debug("Expression is evaluated as simple (strict) expression: " + expression);
            }
            return createComplexConcatExpression(expression);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Expression is evaluated as simple (non strict) expression: " + expression);
            }
            return createSimpleExpression(expression, false);
        }
    }

    private Expression createOperatorExpression(Matcher matcher, Matcher startMatcher, String expression) {
        Expression answer = null;

        if (startMatcher.matches()) {
            answer = doCreateOperatorExpression(expression, startMatcher.group(1), startMatcher.group(2), startMatcher.group(3));
        } else if (matcher.matches()) {
            answer = doCreateOperatorExpression(expression, matcher.group(1), matcher.group(2), matcher.group(3));
        }

        // append any additional operators
        answer = appendAdditionalOperatorExpressions(answer, expression);

        return answer;
    }

    private Expression appendAdditionalOperatorExpressions(final Expression answer, final String expression) {
        Matcher matcher = ANDOR_PATTERN.matcher(expression);

        // now go through the and/or and append those sub expressions
        final List<ExpressionGroup> expressions = new ArrayList<ExpressionGroup>();
        while (matcher.find()) {
            dumpMatcher(matcher);

            // we only support AND/OR operator between expression groups
            final SimpleLanguageOperator operator = asOperator(matcher.group(1));
            if (operator != AND && operator != OR) {
                throw new IllegalArgumentException("Syntax error in expression: " + expression
                    + ". Expected operator as either and/or but was: " + operator);
            }
            final Expression exp = doCreateOperatorExpression(expression, matcher.group(2), matcher.group(3), matcher.group(4));

            // add this group
            expressions.add(new ExpressionGroup(operator, exp));
        }

        // return the expression that evaluates the entire expression with multiple groups
        return new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                boolean matches = PredicateBuilder.toPredicate(answer).matches(exchange);
                for (ExpressionGroup group : expressions) {
                    boolean result = PredicateBuilder.toPredicate(group.getValue()).matches(exchange);
                    if (group.getKey() == AND) {
                        matches &= result;
                    } else {
                        matches |= result;
                    }
                }
                return exchange.getContext().getTypeConverter().convertTo(type, matches);
            }

            @Override
            public String toString() {
                StringBuilder msg = new StringBuilder(answer.toString());
                for (ExpressionGroup group : expressions) {
                    msg.append(" ");
                    msg.append(group.getKey());
                    msg.append(" ");
                    msg.append(group.getValue());
                }
                return msg.toString();
            }
        };
    }

    private void dumpMatcher(Matcher matcher) {
        if (log.isTraceEnabled()) {
            log.trace("Matcher start: " + matcher.start());
            log.trace("Matcher end: " + matcher.end());
            log.trace("Matcher group: " + matcher.group());
            log.trace("Matcher group count: " + matcher.groupCount());
            for (int i = 0; i < matcher.groupCount() + 1; i++) {
                String group = matcher.group(i);
                log.trace("Matcher group #" + i + ": " + group);
            }
        }
    }

    private Expression doCreateOperatorExpression(final String expression, final String leftText,
                                                  final String operatorText, final String rightText) {
        // left value is always a simple expression
        final Expression left = createSimpleExpression(leftText, true);
        final SimpleLanguageOperator operator = asOperator(operatorText);

        // the right hand side expression can either be a constant expression with or without enclosing ' '
        // or another simple expression using ${ } placeholders
        final Expression right;
        final Boolean isNull;
        // special null handling
        if ("null".equals(rightText) || "'null'".equals(rightText)) {
            isNull = Boolean.TRUE;
            right = createSimpleOrConstantExpression(null);
        } else {
            isNull = Boolean.FALSE;
            right = createSimpleOrConstantExpression(rightText);
        }

        return new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                Predicate predicate = null;

                if (operator == EQ && isNull) {
                    // special for EQ null
                    predicate = PredicateBuilder.isNull(left);
                } else if (operator == NOT && isNull) {
                    // special for not EQ null
                    predicate = PredicateBuilder.isNotNull(left);
                } else if (operator == EQ) {
                    predicate = PredicateBuilder.isEqualTo(left, right);
                } else if (operator == GT) {
                    predicate = PredicateBuilder.isGreaterThan(left, right);
                } else if (operator == GTE) {
                    predicate = PredicateBuilder.isGreaterThanOrEqualTo(left, right);
                } else if (operator == LT) {
                    predicate = PredicateBuilder.isLessThan(left, right);
                } else if (operator == LTE) {
                    predicate = PredicateBuilder.isLessThanOrEqualTo(left, right);
                } else if (operator == NOT) {
                    predicate = PredicateBuilder.isNotEqualTo(left, right);
                } else if (operator == CONTAINS || operator == NOT_CONTAINS) {
                    predicate = PredicateBuilder.contains(left, right);
                    if (operator == NOT_CONTAINS) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                } else if (operator == REGEX || operator == NOT_REGEX) {
                    // reg ex should use String pattern, so we evaluate the right hand side as a String
                    predicate = PredicateBuilder.regex(left, right.evaluate(exchange, String.class));
                    if (operator == NOT_REGEX) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                } else if (operator == IN || operator == NOT_IN) {
                    // okay the in operator is a bit more complex as we need to build a list of values
                    // from the right handside expression.
                    // each element on the right handside must be separated by comma (default for create iterator)
                    Iterator<Object> it = ObjectHelper.createIterator(right.evaluate(exchange, Object.class));
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
                } else if (operator == IS || operator == NOT_IS) {
                    String name = right.evaluate(exchange, String.class);
                    Class<?> rightType = exchange.getContext().getClassResolver().resolveClass(name);
                    if (rightType == null) {
                        throw new IllegalArgumentException("Syntax error in " + operatorText + " operator: " + expression
                                + " cannot find class with name: " + name);
                    }
                    predicate = PredicateBuilder.isInstanceOf(left, rightType);
                    if (operator == NOT_IS) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                } else if (operator == RANGE || operator == NOT_RANGE) {
                    String range = right.evaluate(exchange, String.class);
                    Matcher matcher = RANGE_PATTERN.matcher(range);
                    if (matcher.matches()) {
                        // wrap as constant expression for the from and to values
                        Expression from = ExpressionBuilder.constantExpression(matcher.group(1));
                        Expression to = ExpressionBuilder.constantExpression(matcher.group(3));

                        // build a compound predicate for the range
                        predicate = PredicateBuilder.isGreaterThanOrEqualTo(left, from);
                        predicate = PredicateBuilder.and(predicate, PredicateBuilder.isLessThanOrEqualTo(left, to));
                    } else {
                        throw new IllegalArgumentException("Syntax error in " + operatorText + " operator: " + expression
                                + " is not valid. Valid syntax:from..to(where from and to are numbers).");
                    }
                    if (operator == NOT_RANGE) {
                        predicate = PredicateBuilder.not(predicate);
                    }
                }

                if (predicate == null) {
                    throw new IllegalArgumentException("Unsupported operator: " + operatorText + " for expression: " + expression);
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
                int endIdx = expression.indexOf('}', pivot);
                if (endIdx < 0) {
                    throw new IllegalArgumentException("Expecting } but found end of string for simple expression: " + expression);
                }
                String simpleText = expression.substring(pivot, endIdx);

                Expression simpleExpression = createSimpleExpression(simpleText, true);
                results.add(simpleExpression);
                pivot = endIdx + 1;
            }
        }

        // only concat if there is more than one expression
        if (results.size() > 1) {
            return ExpressionBuilder.concatExpression(results, expression);
        } else if (results.size() == 1) {
            return results.get(0);
        } else {
            return null;
        }
    }

    protected Expression createSimpleOrConstantExpression(String text) {
        if (text != null) {
            String simple = ObjectHelper.between(text, "${", "}");
            if (simple != null) {
                return createSimpleExpression(simple, true);
            }

            simple = ObjectHelper.between(text, "'", "'");
            if (simple != null) {
                return createConstantExpression(simple);
            }
        }

        return createConstantExpression(text);
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
     * @param strict whether it is strict mode or not, if strict it will throw a
     * {@link org.apache.camel.ExpressionIllegalSyntaxException} if the expression was not known.
     * Set to <tt>false</tt> to support constant expressions
     * @return the expression
     */
    protected abstract Expression createSimpleExpression(String expression, boolean strict);

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
