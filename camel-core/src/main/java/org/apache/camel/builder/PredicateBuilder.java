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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.util.ExpressionToPredicateAdapter;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A helper class for working with predicates
 *
 * @version 
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
        return ExpressionToPredicateAdapter.toPredicate(expression);
    }

    /**
     * A helper method to return the logical not of the given predicate
     */
    public static Predicate not(final Predicate predicate) {
        notNull(predicate, "predicate");
        return new Predicate() {
            public boolean matches(Exchange exchange) {
                return !predicate.matches(exchange);
            }

            @Override
            public String toString() {
                return "not (" + predicate + ")";
            }
        };
    }

    /**
     * A helper method to combine multiple predicates by a logical AND
     */
    public static Predicate and(final Predicate left, final Predicate right) {
        notNull(left, "left");
        notNull(right, "right");
        return new Predicate() {
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
     * A helper method to combine two predicates by a logical OR.
     * If you want to combine multiple predicates see {@link #in(Predicate...)}
     */
    public static Predicate or(final Predicate left, final Predicate right) {
        notNull(left, "left");
        notNull(right, "right");
        return new Predicate() {
            public boolean matches(Exchange exchange) {
                return left.matches(exchange) || right.matches(exchange);
            }

            @Override
            public String toString() {
                return "(" + left + ") or (" + right + ")";
            }
        };
    }

    /**
     * Concat the given predicates into a single predicate, which matches
     * if at least one predicates matches.
     *
     * @param predicates predicates
     * @return a single predicate containing all the predicates
     */
    public static Predicate or(List<Predicate> predicates) {
        Predicate answer = null;
        for (Predicate predicate : predicates) {
            if (answer == null) {
                answer = predicate;
            } else {
                answer = or(answer, predicate);
            }
        }
        return answer;
    }

    /**
     * Concat the given predicates into a single predicate, which matches
     * if at least one predicates matches.
     *
     * @param predicates predicates
     * @return a single predicate containing all the predicates
     */
    public static Predicate or(Predicate... predicates) {
        return or(Arrays.asList(predicates));
    }

    /**
     * A helper method to return true if any of the predicates matches.
     */
    public static Predicate in(final Predicate... predicates) {
        notNull(predicates, "predicates");

        return new Predicate() {
            public boolean matches(Exchange exchange) {
                for (Predicate in : predicates) {
                    if (in.matches(exchange)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return "in (" + Arrays.asList(predicates) + ")";
            }
        };
    }

    /**
     * A helper method to return true if any of the predicates matches.
     */
    public static Predicate in(List<Predicate> predicates) {
        return in(predicates.toArray(new Predicate[0]));
    }

    public static Predicate isEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.typeCoerceEquals(exchange.getContext().getTypeConverter(), leftValue, rightValue);
            }

            protected String getOperationText() {
                return "==";
            }
        };
    }

    public static Predicate isEqualToIgnoreCase(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.typeCoerceEquals(exchange.getContext().getTypeConverter(), leftValue, rightValue, true);
            }

            protected String getOperationText() {
                return "=~";
            }
        };
    }

    public static Predicate isNotEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return false;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return true;
                }

                return ObjectHelper.typeCoerceNotEquals(exchange.getContext().getTypeConverter(), leftValue, rightValue);
            }

            protected String getOperationText() {
                return "!=";
            }
        };
    }

    public static Predicate isLessThan(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue, rightValue) < 0;
            }

            protected String getOperationText() {
                return "<";
            }
        };
    }

    public static Predicate isLessThanOrEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue, rightValue) <= 0;
            }

            protected String getOperationText() {
                return "<=";
            }
        };
    }

    public static Predicate isGreaterThan(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return false;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue, rightValue) > 0;
            }

            protected String getOperationText() {
                return ">";
            }
        };
    }

    public static Predicate isGreaterThanOrEqualTo(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.typeCoerceCompare(exchange.getContext().getTypeConverter(), leftValue, rightValue) >= 0;
            }

            protected String getOperationText() {
                return ">=";
            }
        };
    }

    public static Predicate contains(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.contains(leftValue, rightValue);
            }

            protected String getOperationText() {
                return "contains";
            }
        };
    }
    
    public static Predicate containsIgnoreCase(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }

                return ObjectHelper.containsIgnoreCase(leftValue, rightValue);
            }

            protected String getOperationText() {
                return "~~";
            }
        };
    }

    public static Predicate isNull(final Expression expression) {
        return new BinaryPredicateSupport(expression, ExpressionBuilder.constantExpression(null)) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null) {
                    // the left operator is null so its true
                    return true;
                } 

                return ObjectHelper.typeCoerceEquals(exchange.getContext().getTypeConverter(), leftValue, rightValue);
            }

            protected String getOperationText() {
                // leave the operation text as "is not" as Camel will insert right and left expression around it
                // so it will be displayed as: XXX is null
                return "is";
            }
        };
    }

    public static Predicate isNotNull(final Expression expression) {
        return new BinaryPredicateSupport(expression, ExpressionBuilder.constantExpression(null)) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue != null) {
                    // the left operator is not null so its true
                    return true;
                }

                return ObjectHelper.typeCoerceNotEquals(exchange.getContext().getTypeConverter(), leftValue, rightValue);
            }

            protected String getOperationText() {
                // leave the operation text as "is not" as Camel will insert right and left expression around it
                // so it will be displayed as: XXX is not null
                return "is not";
            }
        };
    }

    public static Predicate isInstanceOf(final Expression expression, final Class<?> type) {
        notNull(expression, "expression");
        notNull(type, "type");

        return new Predicate() {
            public boolean matches(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                return type.isInstance(value);
            }

            @Override
            public String toString() {
                return expression + " instanceof " + type.getCanonicalName();
            }
        };
    }

    public static Predicate startsWith(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }
                String leftStr = exchange.getContext().getTypeConverter().convertTo(String.class, leftValue);
                String rightStr = exchange.getContext().getTypeConverter().convertTo(String.class, rightValue);
                if (leftStr != null && rightStr != null) {
                    return leftStr.startsWith(rightStr);
                } else {
                    return false;
                }
            }

            protected String getOperationText() {
                return "startsWith";
            }
        };
    }

    public static Predicate endsWith(final Expression left, final Expression right) {
        return new BinaryPredicateSupport(left, right) {

            protected boolean matches(Exchange exchange, Object leftValue, Object rightValue) {
                if (leftValue == null && rightValue == null) {
                    // they are equal
                    return true;
                } else if (leftValue == null || rightValue == null) {
                    // only one of them is null so they are not equal
                    return false;
                }
                String leftStr = exchange.getContext().getTypeConverter().convertTo(String.class, leftValue);
                String rightStr = exchange.getContext().getTypeConverter().convertTo(String.class, rightValue);
                if (leftStr != null && rightStr != null) {
                    return leftStr.endsWith(rightStr);
                } else {
                    return false;
                }
            }

            protected String getOperationText() {
                return "endsWith";
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

        return new Predicate() {
            public boolean matches(Exchange exchange) {
                String value = expression.evaluate(exchange, String.class);
                if (value != null) {
                    Matcher matcher = pattern.matcher(value);
                    return matcher.matches();
                }
                return false;
            }

            @Override
            public String toString() {
                return expression + ".matches('" + pattern + "')";
            }
        };
    }

    /**
     * Concat the given predicates into a single predicate, which
     * only matches if all the predicates matches.
     *
     * @param predicates predicates
     * @return a single predicate containing all the predicates
     */
    public static Predicate and(List<Predicate> predicates) {
        Predicate answer = null;
        for (Predicate predicate : predicates) {
            if (answer == null) {
                answer = predicate;
            } else {
                answer = and(answer, predicate);
            }
        }
        return answer;
    }

    /**
     * Concat the given predicates into a single predicate, which only matches
     * if all the predicates matches.
     *
     * @param predicates predicates
     * @return a single predicate containing all the predicates
     */
    public static Predicate and(Predicate... predicates) {
        return and(Arrays.asList(predicates));
    }

    /**
     * A constant predicate.
     *
     * @param answer the constant matches
     * @return a predicate that always returns the given answer.
     */
    public static Predicate constant(final boolean answer) {
        return new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return answer;
            }

            @Override
            public String toString() {
                return "" + answer;
            }
        };
    }
}
