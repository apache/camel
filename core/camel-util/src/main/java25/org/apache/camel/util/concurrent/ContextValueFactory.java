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
package org.apache.camel.util.concurrent;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating {@link ContextValue} instances.
 * <p>
 * This Java 25+ version uses ScopedValue when virtual threads are enabled, otherwise falls back to ThreadLocal.
 */
class ContextValueFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ContextValueFactory.class);

    // Use lazy holder pattern to avoid resolving ThreadType before configuration is loaded
    private static final class ScopedValueHolder {
        static final boolean USE_SCOPED_VALUES = shouldUseScopedValues();

        static {
            if (useScopedValues()) {
                LOG.info("ContextValue will use ScopedValue for virtual thread optimization");
            } else {
                LOG.debug("ContextValue will use ThreadLocal");
            }
        }

        private static boolean shouldUseScopedValues() {
            return ThreadType.current() == ThreadType.VIRTUAL;
        }
    }

    private static boolean useScopedValues() {
        return ScopedValueHolder.USE_SCOPED_VALUES;
    }

    /**
     * Creates a new context value with the given name.
     * <p>
     * Uses ScopedValue when virtual threads are enabled, otherwise ThreadLocal.
     */
    static <T> ContextValue<T> newInstance(String name) {
        if (useScopedValues()) {
            return new ScopedValueContextValue<>(name);
        }
        return new ThreadLocalContextValue<>(name);
    }

    /**
     * Creates a new ThreadLocal-based context value with the given name.
     * <p>
     * This always uses ThreadLocal, regardless of virtual thread settings.
     */
    static <T> ContextValue<T> newThreadLocal(String name) {
        return new ThreadLocalContextValue<>(name);
    }

    /**
     * Creates a new ThreadLocal-based context value with the given name and initial value supplier.
     * <p>
     * This always uses ThreadLocal, regardless of virtual thread settings.
     */
    static <T> ContextValue<T> newThreadLocal(String name, Supplier<T> supplier) {
        return new ThreadLocalContextValue<>(name, supplier);
    }

    /**
     * Executes the given operation with the context value bound to the specified value.
     */
    static <T, R> R where(ContextValue<T> key, T value, Supplier<R> operation) {
        if (key instanceof ScopedValueContextValue<T> svKey) {
            return ScopedValue.where(svKey.scopedValue, value).call(operation::get);
        } else if (key instanceof ThreadLocalContextValue<T> tlKey) {
            T oldValue = tlKey.get();
            try {
                tlKey.set(value);
                return operation.get();
            } finally {
                if (oldValue != null) {
                    tlKey.set(oldValue);
                } else {
                    tlKey.remove();
                }
            }
        }
        throw new IllegalArgumentException("Unsupported ContextValue type: " + key.getClass());
    }

    /**
     * Executes the given operation with the context value bound to the specified value.
     */
    static <T> void where(ContextValue<T> key, T value, Runnable operation) {
        if (key instanceof ScopedValueContextValue<T> svKey) {
            // In JDK 25+, ScopedValue.where() returns a Carrier that has run() method
            ScopedValue.where(svKey.scopedValue, value).run(operation);
        } else {
            where(key, value, () -> {
                operation.run();
                return null;
            });
        }
    }

    /**
     * ScopedValue-based implementation of ContextValue (JDK 25+).
     */
    static class ScopedValueContextValue<T> implements ContextValue<T> {
        private final String name;
        final ScopedValue<T> scopedValue;

        ScopedValueContextValue(String name) {
            this.name = name;
            this.scopedValue = ScopedValue.newInstance();
        }

        @Override
        public T get() {
            return scopedValue.get();
        }

        @Override
        public T orElse(T defaultValue) {
            return scopedValue.orElse(defaultValue);
        }

        @Override
        public boolean isBound() {
            return scopedValue.isBound();
        }

        @Override
        public void set(T value) {
            throw new UnsupportedOperationException(
                    "ScopedValue is immutable. Use ContextValue.where() to bind values.");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException(
                    "ScopedValue is immutable. Values are automatically unbound when leaving the scope.");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return "ContextValue[" + name + ",ScopedValue]";
        }
    }

}
