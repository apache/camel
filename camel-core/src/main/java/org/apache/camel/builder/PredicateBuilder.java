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
        };
    }
}
