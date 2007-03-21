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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A helper class for working with predicates
 *
 * @version $Revision: 520261 $
 */
public class PredicateBuilder {
    /**
     * A helper method to combine multiple predicates by a logical AND
     */
    public static <E extends Exchange> Predicate<E> and(final Predicate<E> left, final Predicate<E> right) {
        notNull(left, "left");
        notNull(right, "right");
        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                return left.evaluate(exchange) && right.evaluate(exchange);
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
    public static <E extends Exchange> Predicate<E> or(final Predicate<E> left, final Predicate<E> right) {
        notNull(left, "left");
        notNull(right, "right");
        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                return left.evaluate(exchange) || right.evaluate(exchange);
            }

            @Override
            public String toString() {
                return "(" + left + ") or (" + right + ")";
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isEqualTo(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return ObjectHelper.equals(value1, value2);
            }

            @Override
            public String toString() {
                return left + " == " + right;
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isNotEqualTo(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return !ObjectHelper.equals(value1, value2);
            }

            @Override
            public String toString() {
                return left + " != " + right;
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isLessThan(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return ObjectHelper.compare(value1, value2) < 0;
            }

            @Override
            public String toString() {
                return left + " < " + right;
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isLessThanOrEqualTo(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return ObjectHelper.compare(value1, value2) <= 0;
            }

            @Override
            public String toString() {
                return left + " <= " + right;
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isGreaterThan(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return ObjectHelper.compare(value1, value2) > 0;
            }

            @Override
            public String toString() {
                return left + " > " + right;
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isGreaterThanOrEqualTo(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return ObjectHelper.compare(value1, value2) >= 0;
            }

            @Override
            public String toString() {
                return left + " >= " + right;
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isInstanceOf(final Expression<E> expression, final Class type) {
        notNull(expression, "expression");
        notNull(type, "type");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value = expression.evaluate(exchange);
                return type.isInstance(value);
            }

            @Override
            public String toString() {
                return expression + " instanceof " + type.getName();
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isNull(final Expression<E> expression) {
        notNull(expression, "expression");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value = expression.evaluate(exchange);
                return value == null;
            }

            @Override
            public String toString() {
                return expression + " == null";
            }
        };
    }

    public static <E extends Exchange> Predicate<E> isNotNull(final Expression<E> expression) {
        notNull(expression, "expression");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value = expression.evaluate(exchange);
                return value != null;
            }

            @Override
            public String toString() {
                return expression + " != null";
            }
        };
    }

    public static <E extends Exchange> Predicate<E> contains(final Expression<E> left, final Expression<E> right) {
        notNull(left, "left");
        notNull(right, "right");

        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                Object value1 = left.evaluate(exchange);
                Object value2 = right.evaluate(exchange);
                return ObjectHelper.contains(value1, value2);
            }

            @Override
            public String toString() {
                return left + ".contains(" + right + ")";
            }
        };
    }
}
