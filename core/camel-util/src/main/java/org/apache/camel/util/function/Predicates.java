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
package org.apache.camel.util.function;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Predicate helpers, inspired by http://minborgsjavapot.blogspot.it/2016/03/put-your-java-8-method-references-to.html
 *
 */
public final class Predicates {
    private Predicates() {
    }

    /**
     * Wrap a predicate, useful for method references.
     */
    public static <T> Predicate<T> of(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "Predicate must be specified");

        return predicate;
    }


    /**
     * Negates a predicate, useful for method references.
     * 
     * <pre>
     *     Stream.of("A", "", "B")
     *         .filter(Predicates.negate(String::isEmpty))
     *         .count();
     * </pre>
     */
    public static <T> Predicate<T> negate(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "Predicate must be specified");

        return predicate.negate();
    }
}
