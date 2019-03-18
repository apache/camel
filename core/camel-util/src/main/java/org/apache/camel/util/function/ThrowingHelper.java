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

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.util.ObjectHelper;

public final class ThrowingHelper {
    private ThrowingHelper() {
    }

    public static <V, T extends Throwable> Supplier<V> wrapAsSupplier(ThrowingSupplier<V, T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    public static <I, T extends Throwable> Consumer<I> wrapAsConsumer(ThrowingConsumer<I, T> consumer) {
        return in -> {
            try {
                consumer.accept(in);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    public static <I1, I2, T extends Throwable> BiConsumer<I1, I2> wrapAsBiConsumer(ThrowingBiConsumer<I1, I2, T> consumer) {
        return (i1, i2) -> {
            try {
                consumer.accept(i1, i2);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    public static <I, R, T extends Throwable> Function<I, R> wrapAsFunction(ThrowingFunction<I, R, T> function) {
        return in -> {
            try {
                return function.apply(in);

            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string, an empty collection or a map  and transform it using the given function.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @param function  the function to be executed against value if not empty
     */
    public static <I, R, T extends Throwable> Optional<R> applyIfNotEmpty(I value, ThrowingFunction<I, R, T> function) throws T {
        if (ObjectHelper.isNotEmpty(value)) {
            return Optional.ofNullable(function.apply(value));
        }

        return Optional.empty();
    }

    /**
     * Tests whether the value is <b>not</b> <tt>null</tt>, an empty string, an empty collection or a map and transform it using the given function.
     *
     * @param value  the value, if its a String it will be tested for text length as well
     * @param consumer  the function to be executed against value if not empty
     * @param orElse  the supplier to use to retrieve a result if the given value is empty
     */
    public static <I, R, T extends Throwable> R applyIfNotEmpty(I value, ThrowingFunction<I, R, T> consumer, Supplier<R> orElse) throws T {
        if (ObjectHelper.isNotEmpty(value)) {
            return consumer.apply(value);
        }

        return orElse.get();
    }
}
