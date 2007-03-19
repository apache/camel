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
package org.apache.camel;

/**
 * A helper class for working with predicates
 *
 * @version $Revision$
 */
public class Predicates {

    public static void notNull(Object value, String name) {
        if (value == null) {
            throw new NullPointerException("No " + name + " specified");
        }
    }

    /**
     * A helper method to combine multiple predicates by a logical AND
     */
    public static <E> Predicate<E> and(final Predicate<E> left, final Predicate<E> right) {
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
    public static <E> Predicate<E> or(final Predicate<E> left, final Predicate<E> right) {
        notNull(left, "left");
        notNull(right, "right");
        return new Predicate<E>() {
            public boolean evaluate(E exchange) {
                return left.evaluate(exchange) || right.evaluate(exchange);
            }
        };
    }


}
