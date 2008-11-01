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
package org.apache.camel.builder;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.compare;
import static org.apache.camel.util.ObjectHelper.notNull;


/**
 * A helper class for working with predicates
 *
 * @version $Revision$
 */
public final class PredicateBuilder {

    /**
     * Utility classes should not have a public constructor.
     */
    private PredicateBuilder() {
    }

    /**
     * Converts the given expression into an {@link Predicate}
     */
    public static Predicate toPredicate(final Expression expression) {
        return new PredicateSupport() {
            public boolean matches(Exchange exchange) {
                Object value = expression.evaluate(exchange);
                return ObjectHelper.evaluateValuePredicate(value);
            }

            @Override
            public String toString() {
                return expression.toString();
            }
        };
    }

    /**
     * A helper method to return the logical not of the given predicate
     */
    public static Predicate not(final Predicate predicate) {
        notNull(predicate, "predicate");
        return new PredicateSupport() {
            public boolean matches(Exchange exchange) {
                return !predicate.matches(exchange);
            }

            @Override
            public String toString() {
                return "not " + predicate;
            }
        };
    }

    /**
     * A helper method to combine multiple predicates by a logical AND
     */
    public static Predicate and(final Predicate left, final Predicate right) {
        notNull(left, "left");
        notNull(right, "right");
        return new PredicateSupport() {
            public boolean matches(Exchange exchange) {
                return left.matches(exchange) && right.matches(exchange);
            }

            @Override
            public String toString() {
                return "(" + left + ") and (" + right + ")";
            }
        };
    }

    /**
     * A helper method to combine multiple predicates by a logical OR
     */
    public static Predicate or(final Predicate left, final Predicate right) {
        notNull(left, "left");
        notNull(right, "right");
        return new PredicateSupport() {
            public boolean matches(Exchange exchange) {
                return left.matches(exchange) || right.matches(exchange);
            }

            @Override
            public String toString() {
                return "(" + left + ") or (" + right + ")";
            }
        };
    }

    public static Predicate isEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return ObjectHelper.equal(leftValue, rightValue);
            }

            protected String getOperationText() {
                return "==";
            }
        };
    }

    public static Predicate isNotEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return !ObjectHelper.equal(leftValue, rightValue);
            }

            protected String getOperationText() {
                return "!=";
            }
        };
    }

    public static Predicate isLessThan(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return compare(leftValue, rightValue) < 0;
            }

            protected String getOperationText() {
                return "<";
            }
        };
    }

    public static Predicate isLessThanOrEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return compare(leftValue, rightValue) <= 0;
            }

            protected String getOperationText() {
                return "<=";
            }
        };
    }

    public static Predicate isGreaterThan(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return compare(leftValue, rightValue) > 0;
            }

            protected String getOperationText() {
                return ">";
            }
        };
    }

    public static Predicate isGreaterThanOrEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return compare(leftValue, rightValue) >= 0;
            }

            protected String getOperationText() {
                return ">=";
            }
        };
    }

    public static Predicate contains(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                return ObjectHelper.contains(leftValue, rightValue);
            }

            protected String getOperationText() {
                return "contains";
            }
        };
    }

    public static Predicate isNull(final Expression expression) {
        return isEqualTo(expression, ExpressionBuilder.constantExpression(null));
    }

    public static Predicate isNotNull(final Expression expression) {
        return isNotEqualTo(expression, ExpressionBuilder.constantExpression(null));
    }

    public static Predicate isInstanceOf(final Expression expression, final Class<?> type) {
        notNull(expression, "expression");
        notNull(type, "type");

        return new PredicateSupport() {
            public boolean matches(Exchange exchange) {
                Object value = expression.evaluate(exchange);
                return type.isInstance(value);
            }

            @Override
            public String toString() {
                return expression + " instanceof " + type.getName();
            }

            @Override
            protected String assertionFailureMessage(Exchange exchange) {
                return super.assertionFailureMessage(exchange)
                    + " for <" + expression.evaluate(exchange) + ">";
            }
        };
    }

    /**
     * Returns a predicate which is true if the expression matches the given
     * regular expression
     *
     * @param expression the expression to evaluate
     * @param regex the regular expression to match against
     * @return a new predicate
     */
    public static Predicate regex(final Expression expression, final String regex) {
        return regex(expression, Pattern.compile(regex));
    }

    /**
     * Returns a predicate which is true if the expression matches the given
     * regular expression
     *
     * @param expression the expression to evaluate
     * @param pattern the regular expression to match against
     * @return a new predicate
     */
    public static Predicate regex(final Expression expression, final Pattern pattern) {
        notNull(expression, "expression");
        notNull(pattern, "pattern");

        return new PredicateSupport() {
            public boolean matches(Exchange exchange) {
                Object value = expression.evaluate(exchange);
                if (value != null) {
                    Matcher matcher = pattern.matcher(value.toString());
                    return matcher.matches();
                }
                return false;
            }

            @Override
            public String toString() {
                return expression + ".matches(" + pattern + ")";
            }

            @Override
            protected String assertionFailureMessage(Exchange exchange) {
                return super.assertionFailureMessage(exchange) + " for <" + expression.evaluate(exchange)
                       + ">";
            }

        };
    }
}
